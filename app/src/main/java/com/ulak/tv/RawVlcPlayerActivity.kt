package com.ulak.tv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * v0.3.5.1 RAW player.
 *
 * RAW streams stay in a dedicated process, but the lower information card now
 * follows the same ULAK visual hierarchy as the normal Media3 player.
 */
class RawVlcPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAME = "raw_name"
        const val EXTRA_GROUP = "raw_group"
        const val EXTRA_LOGO = "raw_logo"
        const val EXTRA_URLS = "raw_urls"
    }

    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var overlay: LinearLayout
    private lateinit var liveStateText: TextView
    private lateinit var routeText: TextView
    private lateinit var errorText: TextView

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
        root.addView(
            videoLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "RAW Kanal" }
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty().ifBlank { "Canlı TV" }

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBackground(0xF20B0D0F.toInt(), 22f, 0xFF2A2F35.toInt())
        }

        val logoBox = FrameLayout(this).apply {
            background = roundedBackground(0xFF101214.toInt(), 18f, 0xFF272B30.toInt())
        }
        val initials = TextView(this).apply {
            text = name.take(2).uppercase()
            setTextColor(0xFFF4B400.toInt())
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        logoBox.addView(
            initials,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        overlay.addView(logoBox, LinearLayout.LayoutParams(dp(78), dp(78)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }
        val title = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        liveStateText = TextView(this).apply {
            text = "● Canlı yayın   •   Stabil   •   RAW VLC"
            setTextColor(0xFF69E58B.toInt())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        }
        val groupText = TextView(this).apply {
            text = group
            setTextColor(0xFF9AA0A6.toInt())
            textSize = 12f
        }
        routeText = TextView(this).apply {
            text = "VLC tam TS demux + MPEG Audio • yol 1/${urls.size}"
            setTextColor(0xFF9AA0A6.toInt())
            textSize = 10f
        }
        errorText = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF8A80.toInt())
            textSize = 10f
            maxLines = 1
            visibility = View.GONE
        }
        info.addView(title)
        info.addView(liveStateText)
        info.addView(groupText)
        info.addView(routeText)
        info.addView(errorText)
        overlay.addView(
            info,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val hints = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        listOf(
            "← Önceki yol",
            "→ Sonraki yol",
            "OK Bilgiyi gizle"
        ).forEachIndexed { index, value ->
            hints.addView(TextView(this).apply {
                text = value
                setTextColor(if (index == 2) 0xFFF4B400.toInt() else Color.WHITE)
                textSize = if (index == 2) 10f else 11f
                gravity = Gravity.END
            })
        }
        overlay.addView(hints)

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
                        MediaPlayer.Event.Opening -> updateState("● Kaynak açılıyor…", 0xFFF4B400.toInt())
                        MediaPlayer.Event.Buffering -> updateState("● Yükleniyor… ${event.buffering.toInt()}%", 0xFFF4B400.toInt())
                        MediaPlayer.Event.Playing -> updatePlayingState()
                        MediaPlayer.Event.Vout -> updatePlayingState()
                        MediaPlayer.Event.EndReached -> tryNext("Yayın sona erdi")
                        MediaPlayer.Event.EncounteredError -> tryNext("VLC yayın hatası")
                    }
                }
            }
        }
    }

    private fun updatePlayingState() {
        liveStateText.text = "● Canlı yayın   •   Stabil   •   RAW VLC"
        liveStateText.setTextColor(0xFF69E58B.toInt())
        routeText.text = "VLC tam TS demux + MPEG Audio • yol ${urlIndex + 1}/${urls.size}"
        errorText.visibility = View.GONE
        showOverlay(true)
    }

    private fun updateState(value: String, color: Int) {
        liveStateText.text = value
        liveStateText.setTextColor(color)
        routeText.text = "RAW VLC • yol ${urlIndex + 1}/${urls.size}"
        showOverlay(true)
    }

    private fun playCurrent() {
        val engine = libVlc ?: return
        val player = mediaPlayer ?: return
        if (urlIndex !in urls.indices) return

        runCatching { player.stop() }
        updateState("● RAW VLC başlatılıyor…", 0xFFF4B400.toInt())
        val media = Media(engine, Uri.parse(urls[urlIndex])).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1000")
            addOption(":http-user-agent=VLC/3.0.18 LibVLC/3.0.18")
        }
        player.media = media
        media.release()
        player.play()
    }

    private fun tryNext(reason: String) {
        if (urlIndex + 1 < urls.size) {
            errorText.text = "$reason • alternatif yol deneniyor"
            errorText.visibility = View.VISIBLE
            urlIndex++
            playCurrent()
        } else {
            liveStateText.text = "● Yayın açılamadı"
            liveStateText.setTextColor(0xFFFF8A80.toInt())
            routeText.text = "RAW VLC • tüm ${urls.size} yayın yolu denendi"
            errorText.text = reason
            errorText.visibility = View.VISIBLE
            showOverlay(true)
        }
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

    private fun roundedBackground(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
