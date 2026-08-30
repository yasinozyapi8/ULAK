package com.ulak.tv.data.xtream

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores one Xtream profile locally. Password is encrypted with Android Keystore.
 * v0.1.6 uses synchronous commit + immediate verification so the UI never claims
 * a profile was saved when persistence actually failed.
 */
class XtreamProfileStore(private val context: Context) {
    data class SavedProfile(
        val name: String,
        val server: String,
        val username: String,
        val password: String
    )

    private val prefs = context.getSharedPreferences("ulak_xtream_profile", Context.MODE_PRIVATE)
    private val alias = "ulak_xtream_password_key"

    fun save(name: String, server: String, username: String, password: String): Result<SavedProfile> = runCatching {
        require(server.isNotBlank()) { "Sunucu URL boş olamaz." }
        require(username.isNotBlank()) { "Kullanıcı adı boş olamaz." }
        require(password.isNotBlank()) { "Parola boş olamaz." }

        val normalizedName = name.ifBlank { "Ev IPTV" }
        val normalizedServer = server.trim()
        val normalizedUsername = username.trim()
        val encrypted = encrypt(password)

        val committed = prefs.edit()
            .putString("name", normalizedName)
            .putString("server", normalizedServer)
            .putString("username", normalizedUsername)
            .putString("password_cipher", encrypted.first)
            .putString("password_iv", encrypted.second)
            .commit()
        check(committed) { "Profil cihaz depolamasına yazılamadı." }

        val verified = load() ?: error("Profil yazıldı ancak doğrulanamadı.")
        check(verified.server == normalizedServer && verified.username == normalizedUsername && verified.password == password) {
            "Kaydedilen profil doğrulaması başarısız oldu."
        }
        verified
    }

    fun load(): SavedProfile? {
        val server = prefs.getString("server", null)?.takeIf { it.isNotBlank() } ?: return null
        val username = prefs.getString("username", null)?.takeIf { it.isNotBlank() } ?: return null
        val cipher = prefs.getString("password_cipher", null) ?: return null
        val iv = prefs.getString("password_iv", null) ?: return null
        val password = runCatching { decrypt(cipher, iv) }.getOrNull() ?: return null
        return SavedProfile(
            name = prefs.getString("name", "Ev IPTV") ?: "Ev IPTV",
            server = server,
            username = username,
            password = password
        )
    }

    fun clear(): Boolean = prefs.edit().clear().commit()

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(value: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP) to
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
    }

    private fun decrypt(value: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val clear = cipher.doFinal(Base64.decode(value, Base64.NO_WRAP))
        return String(clear, StandardCharsets.UTF_8)
    }
}
