package com.ulak.tv

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * v0.3.5 RAW player.
 *
 * RAW streams are deliberately hosted in a separate Android process. This keeps
 * the Compose/Media3 process isolated from native LibVLC failures and avoids the
 * attach/detach race we saw when VLC lived inside AndroidView.
 *
 * This is an original ULAK implementation inspired only by the observed
 * architecture (separate Exo/VLC engines); it does not copy XCIPTV source code.
 */
class RawVlcPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAME = "raw_name"
        const val EXTRA_GROUP = "raw_group"
        const val EXTRA_LOGO = "raw_logo"
        const val EXTRA_URLS = "raw_urls"
    }

    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var overlay: LinearLayout

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var overlayVisible = true
    private var released = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        urls = intent.getStringArrayExtra(EXTRA_URLS)?.filter { it.isNotBlank() } ?: emptyList()
        if (urls.isEmpty()) {
            finish()
            return
        }

        buildUi()
        startEngine()
        playCurrent()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        videoLayout = VLCVideoLayout(this).apply {
            keepScreenOn = true
            isFocusable = false
        }
        root.addView(videoLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(16))
            setBackgroundColor(0xD914171A.toInt())
        }

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "RAW Kanal" }
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty().ifBlank { "RAW" }

        val title = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        statusText = TextView(this).apply {
            text = "RAW VLC motoru hazırlanıyor…"
            setTextColor(0xFFF4B400.toInt())
            textSize = 14f
        }
        detailText = TextView(this).apply {
            text = "$group • VLC tam TS demux + codec zinciri\nBACK: geri • OK: bilgi • ←/→: alternatif yayın yolu"
            setTextColor(0xFFD0D4D8.toInt())
            textSize = 12f
        }

        overlay.addView(title)
        overlay.addView(statusText)
        overlay.addView(detailText)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            setMargins(dp(28), dp(28), dp(28), dp(28))
        }
        root.addView(overlay, lp)
        setContentView(root)
        root.requestFocus()
    }

    private fun startEngine() {
        if (libVlc != null) return

        // Keep the option set conservative. The previous crash happened while
        // attaching/detaching VLC from a Compose AndroidView, not because a
        // special codec option was required. VLC already includes MPEG audio
        // demux/decoding internally.
        libVlc = LibVLC(
            applicationContext,
            arrayListOf(
                "--network-caching=1000",
                "--clock-jitter=0",
                "--clock-synchro=0",
                "--audio-time-stretch"
            )
        )
        mediaPlayer = MediaPlayer(libVlc!!).also { player ->
            player.attachViews(videoLayout, null, false, false)
            player.setEventListener { event ->
                runOnUiThread {
                    when (event.type) {
                        MediaPlayer.Event.Opening -> updateStatus("Kaynak açılıyor…")
                        MediaPlayer.Event.Buffering -> updateStatus("Yükleniyor… ${event.buffering.toInt()}%")
                        MediaPlayer.Event.Playing -> updateStatus("Canlı yayın • RAW VLC • yol ${urlIndex + 1}/${urls.size}")
                        MediaPlayer.Event.Vout -> updateStatus("Canlı yayın • görüntü çıkışı aktif • yol ${urlIndex + 1}/${urls.size}")
                        MediaPlayer.Event.EndReached -> tryNext("Yayın sona erdi")
                        MediaPlayer.Event.EncounteredError -> tryNext("VLC yayın hatası")
                    }
                }
            }
        }
    }

    private fun playCurrent() {
        val engine = libVlc ?: return
        val player = mediaPlayer ?: return
        if (urlIndex !in urls.indices) return

        runCatching { player.stop() }
        val url = urls[urlIndex]
        updateStatus("RAW VLC başlatılıyor • yol ${urlIndex + 1}/${urls.size}")

        val media = Media(engine, Uri.parse(url)).apply {
            // Let VLC choose hardware video decode where safe, while keeping its
            // own demux/audio pipeline for MPEG-1/2 Audio.
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1000")
            addOption(":http-user-agent=VLC/3.0.18 LibVLC/3.0.18")
            addOption(":http-referrer=")
        }
        player.media = media
        media.release()
        player.play()
    }

    private fun tryNext(reason: String) {
        if (urlIndex + 1 < urls.size) {
            urlIndex++
            updateStatus("$reason • alternatif yol deneniyor")
            playCurrent()
        } else {
            updateStatus("$reason • tüm yollar denendi")
            detailText.text = "RAW VLC motoru tüm ${urls.size} yayın yolunu denedi. BACK ile kanal listesine dön."
            showOverlay(true)
        }
    }

    private fun updateStatus(value: String) {
        statusText.text = value
        showOverlay(true)
    }

    private fun showOverlay(show: Boolean) {
        overlayVisible = show
        overlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                showOverlay(!overlayVisible)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (urlIndex + 1 < urls.size) {
                    urlIndex++
                    playCurrent()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (urlIndex > 0) {
                    urlIndex--
                    playCurrent()
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onStop() {
        // A dedicated player Activity owns VLC completely. Stop playback before
        // the surface disappears to avoid native view races.
        runCatching { mediaPlayer?.stop() }
        super.onStop()
    }

    override fun onDestroy() {
        releaseEngine()
        super.onDestroy()
    }

    private fun releaseEngine() {
        if (released) return
        released = true
        val player = mediaPlayer
        mediaPlayer = null
        runCatching { player?.setEventListener(null) }
        runCatching { player?.stop() }
        runCatching { player?.detachViews() }
        runCatching { player?.release() }
        val engine = libVlc
        libVlc = null
        runCatching { engine?.release() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
