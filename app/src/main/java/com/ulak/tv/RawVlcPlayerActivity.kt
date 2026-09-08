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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import coil.ImageLoader
import coil.request.ImageRequest
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * v0.3.5.2 RAW player.
 *
 * RAW kanallar LibVLC motorunda oynatılmaya devam eder, ancak oynatma ekranı
 * artık normal ULAK Media3 oynatıcısıyla aynı alt bilgi hiyerarşisini kullanır.
 * Eski ayrı RAW kartı tamamen kaldırılmıştır.
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
    private lateinit var liveText: TextView
    private lateinit var stabilityText: TextView
    private lateinit var streamTypeText: TextView
    private lateinit var groupText: TextView
    private lateinit var errorText: TextView
    private lateinit var technicalPanel: LinearLayout
    private lateinit var technicalStream: TextView
    private lateinit var technicalRoute: TextView
    private lateinit var technicalStatus: TextView
    private lateinit var okHint: TextView

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var overlayVisible = true
    private var technicalVisible = false
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
        val logoUrl = intent.getStringExtra(EXTRA_LOGO)

        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = horizontalGradientBackground(
                intArrayOf(0xF20B0D0F.toInt(), 0xE614171A.toInt(), 0xCC14171A.toInt()),
                22f,
                0xFF2A2F35.toInt()
            )
        }

        val logoBox = FrameLayout(this).apply {
            background = roundedBackground(0xFF101214.toInt(), 18f, 0xFF272B30.toInt())
        }
        val logoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
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
        if (!logoUrl.isNullOrBlank()) {
            logoBox.addView(
                logoView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            ImageLoader(this).enqueue(
                ImageRequest.Builder(this)
                    .data(logoUrl)
                    .target(
                        onSuccess = { drawable ->
                            initials.visibility = View.GONE
                            logoView.setImageDrawable(drawable)
                        },
                        onError = {
                            logoView.visibility = View.GONE
                            initials.visibility = View.VISIBLE
                        }
                    )
                    .build()
            )
        }
        overlay.addView(logoBox, LinearLayout.LayoutParams(dp(78), dp(78)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }
        info.addView(TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })

        val stateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        liveText = stateText("● Canlı yayın", 0xFF69E58B.toInt(), bold = true)
        stabilityText = stateText("Stabil", 0xFF69E58B.toInt())
        streamTypeText = stateText(streamType(urls[urlIndex]), 0xFFF4B400.toInt(), bold = true)
        stateRow.addView(liveText)
        stateRow.addView(dotText())
        stateRow.addView(stabilityText)
        stateRow.addView(dotText())
        stateRow.addView(streamTypeText)
        info.addView(stateRow)

        groupText = TextView(this).apply {
            text = group
            setTextColor(0xFF9AA0A6.toInt())
            textSize = 12f
            maxLines = 1
        }
        info.addView(groupText)

        errorText = TextView(this).apply {
            text = ""
            setTextColor(0xFFFF8A80.toInt())
            textSize = 10f
            maxLines = 1
            visibility = View.GONE
        }
        info.addView(errorText)
        overlay.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val hints = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        hints.addView(hintText("↑ Önceki", Color.WHITE, 11f))
        hints.addView(hintText("↓ Sonraki", Color.WHITE, 11f))
        okHint = hintText("OK Teknik bilgi", 0xFFF4B400.toInt(), 10f)
        hints.addView(okHint)
        overlay.addView(hints)

        bottomContainer.addView(
            overlay,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        technicalPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(0xF214171A.toInt(), 22f, 0xFF30353B.toInt())
            visibility = View.GONE
        }
        technicalPanel.addView(TextView(this).apply {
            text = "Teknik Bilgiler"
            setTextColor(0xFFF4B400.toInt())
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        technicalPanel.addView(technicalLine("Motor", "LibVLC"))
        technicalStream = technicalLine("Yayın", streamType(urls[urlIndex]))
        technicalPanel.addView(technicalStream)
        technicalPanel.addView(technicalLine("Ses", "VLC demux / decoder"))
        technicalRoute = technicalLine("Yol", "${urlIndex + 1}/${urls.size}")
        technicalPanel.addView(technicalRoute)
        technicalStatus = technicalLine("Durum", "Başlatılıyor")
        technicalPanel.addView(technicalStatus)

        bottomContainer.addView(
            technicalPanel,
            LinearLayout.LayoutParams(dp(330), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(18)
            }
        )

        root.addView(
            bottomContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(dp(28), dp(28), dp(28), dp(28))
            }
        )

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
                        MediaPlayer.Event.Opening -> updateState("● Kaynak açılıyor…", false, "Açılıyor")
                        MediaPlayer.Event.Buffering -> updateState("● Yükleniyor… ${event.buffering.toInt()}%", false, "Yükleniyor")
                        MediaPlayer.Event.Playing -> updatePlayingState()
                        MediaPlayer.Event.Vout -> updatePlayingState()
                        MediaPlayer.Event.EndReached -> tryNext("Yayın sona erdi")
                        MediaPlayer.Event.EncounteredError -> tryNext("Yayın hatası")
                    }
                }
            }
        }
    }

    private fun updatePlayingState() {
        liveText.text = "● Canlı yayın"
        liveText.setTextColor(0xFF69E58B.toInt())
        stabilityText.text = "Stabil"
        stabilityText.setTextColor(0xFF69E58B.toInt())
        streamTypeText.text = streamType(urls[urlIndex])
        technicalStream.text = "Yayın\n${streamType(urls[urlIndex])}"
        technicalRoute.text = "Yol\n${urlIndex + 1}/${urls.size}"
        technicalStatus.text = "Durum\nCanlı yayın"
        errorText.visibility = View.GONE
        showOverlay(true)
    }

    private fun updateState(value: String, stable: Boolean, technical: String) {
        liveText.text = value
        liveText.setTextColor(0xFFF4B400.toInt())
        stabilityText.text = if (stable) "Stabil" else "Kontrol ediliyor"
        stabilityText.setTextColor(if (stable) 0xFF69E58B.toInt() else 0xFFF4B400.toInt())
        streamTypeText.text = streamType(urls[urlIndex])
        technicalStream.text = "Yayın\n${streamType(urls[urlIndex])}"
        technicalRoute.text = "Yol\n${urlIndex + 1}/${urls.size}"
        technicalStatus.text = "Durum\n$technical"
        showOverlay(true)
    }

    private fun playCurrent() {
        val engine = libVlc ?: return
        val player = mediaPlayer ?: return
        if (urlIndex !in urls.indices) return

        runCatching { player.stop() }
        updateState("● Kaynak açılıyor…", false, "Başlatılıyor")
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
            errorText.text = "$reason • alternatif yayın deneniyor"
            errorText.visibility = View.VISIBLE
            urlIndex++
            playCurrent()
        } else {
            liveText.text = "● Yayın açılamadı"
            liveText.setTextColor(0xFFFF8A80.toInt())
            stabilityText.text = "Kontrol gerekli"
            stabilityText.setTextColor(0xFFFF8A80.toInt())
            technicalStatus.text = "Durum\nYayın açılamadı"
            errorText.text = reason
            errorText.visibility = View.VISIBLE
            showOverlay(true)
        }
    }

    private fun showOverlay(show: Boolean) {
        overlayVisible = show
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            technicalVisible = false
            technicalPanel.visibility = View.GONE
            okHint.text = "OK Teknik bilgi"
        }
    }

    private fun toggleTechnical() {
        if (!overlayVisible) {
            showOverlay(true)
            return
        }
        technicalVisible = !technicalVisible
        technicalPanel.visibility = if (technicalVisible) View.VISIBLE else View.GONE
        okHint.text = if (technicalVisible) "OK Detayları gizle" else "OK Teknik bilgi"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleTechnical()
                true
            }
            // RAW player ayrı process'te olduğu için burada mevcut kanalın alternatif
            // yayın yolları arasında geçiş yapıyoruz. Görsel hiyerarşi normal player ile aynı.
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (urlIndex + 1 < urls.size) {
                    urlIndex++
                    playCurrent()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (urlIndex > 0) {
                    urlIndex--
                    playCurrent()
                }
                true
            }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> {
                showOverlay(!overlayVisible)
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

    private fun streamType(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "HLS"
        url.contains(".ts", ignoreCase = true) -> "MPEG-TS"
        else -> "AUTO"
    }

    private fun stateText(value: String, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = 13f
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dotText(): TextView = TextView(this).apply {
        text = "  •  "
        setTextColor(0xFF9AA0A6.toInt())
        textSize = 13f
    }

    private fun hintText(value: String, color: Int, size: Float): TextView =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = size
            gravity = Gravity.END
        }

    private fun technicalLine(label: String, value: String): TextView =
        TextView(this).apply {
            text = "$label\n$value"
            setTextColor(Color.WHITE)
            textSize = 11f
            setLineSpacing(0f, 1.05f)
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun roundedBackground(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun horizontalGradientBackground(colors: IntArray, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
