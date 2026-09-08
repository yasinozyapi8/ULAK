package com.ulak.tv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ulak.tv.data.FavoritesStore
import com.ulak.tv.data.PlaybackRouteStore
import com.ulak.tv.data.favoriteKey
import com.ulak.tv.data.model.Channel
import com.ulak.tv.data.xtream.XtreamProfileStore
import com.ulak.tv.data.xtream.XtreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.ImageLoader
import coil.request.ImageRequest
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * v0.3.7 RAW player.
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
        const val EXTRA_STREAM_ID = "raw_stream_id"
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
    private lateinit var volumeHint: TextView
    private lateinit var favoriteHint: TextView
    private lateinit var nameText: TextView
    private lateinit var nowText: TextView
    private lateinit var nextText: TextView
    private lateinit var qualityText: TextView
    private lateinit var technicalVideo: TextView
    private lateinit var technicalFps: TextView
    private lateinit var technicalBuffer: TextView
    private lateinit var technicalEpg: TextView
    private lateinit var logoView: ImageView
    private lateinit var initialsView: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideOverlayRunnable = Runnable {
        if (!technicalVisible) showOverlay(false)
    }
    private val hideVolumeRunnable = Runnable {
        if (::volumeHint.isInitialized) volumeHint.visibility = View.GONE
    }

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var overlayVisible = true
    private var technicalVisible = false
    private var released = false
    private var retryCount = 0
    private var currentVolume = 100
    private var lastNonZeroVolume = 100
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var routeStore: PlaybackRouteStore
    private lateinit var profileStore: XtreamProfileStore
    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = -1
    private var previousChannelIndex = -1
    private var currentChannel: Channel? = null
    private var initialStreamId: Long = -1L
    private var centerDownAt = 0L
    private var centerLongTriggered = false
    private var bufferStartMs: Long? = null
    private var lastBufferMs: Long = 0L
    private var bufferWatchToken = 0
    private var epgNow: String? = null
    private var epgNext: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        favoritesStore = FavoritesStore(applicationContext)
        routeStore = PlaybackRouteStore(applicationContext)
        profileStore = XtreamProfileStore(applicationContext)
        initialStreamId = intent.getLongExtra(EXTRA_STREAM_ID, -1L)
        urls = intent.getStringArrayExtra(EXTRA_URLS)?.filter { it.isNotBlank() } ?: emptyList()
        if (urls.isEmpty()) {
            finish()
            return
        }

        currentVolume = getSharedPreferences("raw_player_prefs", MODE_PRIVATE).getInt("volume", 100).coerceIn(0, 100)
        if (currentVolume > 0) lastNonZeroVolume = currentVolume

        buildUi()
        startEngine()
        playCurrent()
        loadChannelSession()
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
        currentChannel = Channel(name = name, streamUrl = urls.first(), alternateStreamUrls = urls.drop(1), logoUrl = logoUrl, group = group, streamId = initialStreamId.takeIf { it > 0 })

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
        logoView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        initialsView = TextView(this).apply {
            text = name.take(2).uppercase()
            setTextColor(0xFFF4B400.toInt())
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        logoBox.addView(
            initialsView,
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
                            initialsView.visibility = View.GONE
                            logoView.visibility = View.VISIBLE
                            logoView.setImageDrawable(drawable)
                        },
                        onError = {
                            logoView.visibility = View.GONE
                            initialsView.visibility = View.VISIBLE
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
        nameText = TextView(this).apply {
            text = name
            setTextColor(Color.WHITE)
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        info.addView(nameText)

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
        nowText = TextView(this).apply {
            text = "Şimdi: EPG yükleniyor…"
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 1
        }
        nextText = TextView(this).apply {
            text = "Sırada: -"
            setTextColor(0xFF9AA0A6.toInt())
            textSize = 10f
            maxLines = 1
        }
        qualityText = TextView(this).apply {
            text = "Görüntü bilgisi bekleniyor"
            setTextColor(0xFF9AA0A6.toInt())
            textSize = 10f
            maxLines = 1
        }
        info.addView(nowText)
        info.addView(nextText)
        info.addView(qualityText)

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
        hints.addView(hintText("↑ Önceki kanal", Color.WHITE, 11f))
        hints.addView(hintText("↓ Sonraki kanal", Color.WHITE, 11f))
        hints.addView(hintText("← Ses -    Ses + →", Color.WHITE, 10f))
        okHint = hintText("OK Teknik bilgi • Uzun OK ★ Favori", 0xFFF4B400.toInt(), 10f)
        hints.addView(okHint)
        favoriteHint = hintText("⏮ Son kanal", 0xFF9AA0A6.toInt(), 9f)
        hints.addView(favoriteHint)
        volumeHint = hintText("Ses %$currentVolume", 0xFFF4B400.toInt(), 10f).apply {
            visibility = View.GONE
            setPadding(0, dp(4), 0, 0)
        }
        hints.addView(volumeHint)
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
        technicalVideo = technicalLine("Video", "Bekleniyor")
        technicalPanel.addView(technicalVideo)
        technicalFps = technicalLine("FPS", "-")
        technicalPanel.addView(technicalFps)
        technicalBuffer = technicalLine("Son buffer", "-")
        technicalPanel.addView(technicalBuffer)
        technicalEpg = technicalLine("EPG", "Yükleniyor")
        technicalPanel.addView(technicalEpg)
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
            player.volume = currentVolume
            player.setEventListener { event ->
                runOnUiThread {
                    when (event.type) {
                        MediaPlayer.Event.Opening -> updateState("● Kaynak açılıyor…", false, "Açılıyor")
                        MediaPlayer.Event.Buffering -> {
                            if (bufferStartMs == null) {
                                bufferStartMs = android.os.SystemClock.elapsedRealtime()
                                scheduleBufferWatch()
                            }
                            updateState("● Yükleniyor… ${event.buffering.toInt()}%", false, "Yükleniyor")
                        }
                        MediaPlayer.Event.Playing -> { updatePlayingState(); inspectVlcVideoInfo() }
                        MediaPlayer.Event.Vout -> { updatePlayingState(); inspectVlcVideoInfo() }
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
        retryCount = 0
        bufferStartMs?.let { lastBufferMs = android.os.SystemClock.elapsedRealtime() - it }
        bufferStartMs = null
        bufferWatchToken++
        technicalBuffer.text = "Son buffer\n" + if (lastBufferMs > 0) String.format("%.1f sn", lastBufferMs / 1000.0) else "-"
        currentChannel?.let { routeStore.markWorking(it, urls[urlIndex]) }
        showOverlay(true)
        scheduleOverlayHide()
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

    private fun playCurrent(preserveRetry: Boolean = false) {
        val engine = libVlc ?: return
        val player = mediaPlayer ?: return
        if (urlIndex !in urls.indices) return

        if (!preserveRetry) retryCount = 0
        bufferStartMs = null
        bufferWatchToken++
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
        if (retryCount < 1) {
            retryCount++
            errorText.text = "$reason • aynı kaynak yeniden deneniyor"
            errorText.visibility = View.VISIBLE
            playCurrent(preserveRetry = true)
            return
        }
        retryCount = 0
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

    private fun scheduleOverlayHide() {
        uiHandler.removeCallbacks(hideOverlayRunnable)
        if (!technicalVisible) uiHandler.postDelayed(hideOverlayRunnable, 4500L)
    }

    private fun showVolumeHint() {
        if (!::volumeHint.isInitialized) return
        volumeHint.text = if (currentVolume == 0) "Ses kapalı" else "Ses %$currentVolume"
        volumeHint.visibility = View.VISIBLE
        showOverlay(true)
        uiHandler.removeCallbacks(hideVolumeRunnable)
        uiHandler.postDelayed(hideVolumeRunnable, 1600L)
        scheduleOverlayHide()
    }

    private fun setPlayerVolume(value: Int) {
        currentVolume = value.coerceIn(0, 100)
        if (currentVolume > 0) lastNonZeroVolume = currentVolume
        runCatching { mediaPlayer?.volume = currentVolume }
        getSharedPreferences("raw_player_prefs", MODE_PRIVATE).edit().putInt("volume", currentVolume).apply()
        showVolumeHint()
    }

    private fun adjustVolume(delta: Int) {
        setPlayerVolume(currentVolume + delta)
    }

    private fun toggleMute() {
        if (currentVolume == 0) setPlayerVolume(lastNonZeroVolume.coerceAtLeast(10)) else {
            lastNonZeroVolume = currentVolume
            setPlayerVolume(0)
        }
    }

    private fun showOverlay(show: Boolean) {
        overlayVisible = show
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            technicalVisible = false
            technicalPanel.visibility = View.GONE
            okHint.text = "OK Teknik bilgi • Uzun OK ★ Favori"
        }
    }

    private fun toggleTechnical() {
        if (!overlayVisible) {
            showOverlay(true)
            return
        }
        technicalVisible = !technicalVisible
        technicalPanel.visibility = if (technicalVisible) View.VISIBLE else View.GONE
        okHint.text = if (technicalVisible) "OK Detayları gizle • Uzun OK ★ Favori" else "OK Teknik bilgi • Uzun OK ★ Favori"
        if (technicalVisible) uiHandler.removeCallbacks(hideOverlayRunnable) else scheduleOverlayHide()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                centerDownAt = android.os.SystemClock.elapsedRealtime()
                centerLongTriggered = false
                uiHandler.postDelayed({
                    if (centerDownAt > 0L && !centerLongTriggered) {
                        centerLongTriggered = true
                        toggleFavorite()
                    }
                }, 650L)
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                centerDownAt = 0L
                if (!centerLongTriggered) toggleTechnical()
                centerLongTriggered = false
                return true
            }
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (code) {
            KeyEvent.KEYCODE_BACK -> { finish(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP -> { adjustVolume(5); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(-5); true }
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> { toggleMute(); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { switchChannel(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchChannel(1); true }
            KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { goLastChannel(); true }
            KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_BOOKMARK -> { toggleFavorite(); true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> { showOverlay(!overlayVisible); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onStop() {
        runCatching { mediaPlayer?.stop() }
        super.onStop()
    }

    override fun onDestroy() {
        backgroundScope.cancel()
        releaseEngine()
        super.onDestroy()
    }

    private fun releaseEngine() {
        if (released) return
        released = true
        val player = mediaPlayer
        mediaPlayer = null
        uiHandler.removeCallbacksAndMessages(null)
        runCatching { player?.setEventListener(null) }
        runCatching { player?.stop() }
        runCatching { player?.detachViews() }
        runCatching { player?.release() }
        val engine = libVlc
        libVlc = null
        runCatching { engine?.release() }
    }

    private fun loadChannelSession() {
        val profile = profileStore.load() ?: run { loadEpg(); return }
        backgroundScope.launch {
            XtreamRepository.loginAndLoadLive(profile.server, profile.username, profile.password)
                .onSuccess { loaded ->
                    withContext(Dispatchers.Main) {
                        channels = loaded
                        currentChannelIndex = loaded.indexOfFirst { ch ->
                            (initialStreamId > 0 && ch.streamId == initialStreamId) || ch.streamUrl == urls.firstOrNull()
                        }.takeIf { it >= 0 } ?: 0
                        currentChannel = loaded.getOrNull(currentChannelIndex) ?: currentChannel
                        currentChannel?.let { updateChannelUi(it, keepPlaying = true) }
                        loadEpg()
                    }
                }
                .onFailure { withContext(Dispatchers.Main) { loadEpg() } }
        }
    }

    private fun orderedUrls(channel: Channel): List<String> {
        val base = (listOf(channel.streamUrl) + channel.alternateStreamUrls).distinct()
        val preferred = routeStore.preferred(channel)
        return if (!preferred.isNullOrBlank() && preferred in base) listOf(preferred) + base.filter { it != preferred } else base
    }

    private fun switchChannel(delta: Int) {
        if (channels.isEmpty()) {
            errorText.text = "Kanal listesi hazırlanıyor…"
            errorText.visibility = View.VISIBLE
            showOverlay(true)
            return
        }
        val target = (currentChannelIndex + delta).let {
            when { it < 0 -> channels.lastIndex; it > channels.lastIndex -> 0; else -> it }
        }
        tuneChannel(target)
    }

    private fun goLastChannel() {
        if (previousChannelIndex !in channels.indices || previousChannelIndex == currentChannelIndex) {
            errorText.text = "Önceki kanal yok"
            errorText.visibility = View.VISIBLE
            showOverlay(true)
            scheduleOverlayHide()
            return
        }
        tuneChannel(previousChannelIndex)
    }

    private fun tuneChannel(index: Int) {
        val ch = channels.getOrNull(index) ?: return
        if (index != currentChannelIndex) previousChannelIndex = currentChannelIndex
        currentChannelIndex = index
        currentChannel = ch
        urls = orderedUrls(ch)
        urlIndex = 0
        retryCount = 0
        updateChannelUi(ch, keepPlaying = false)
        loadEpg()
        playCurrent()
    }

    private fun updateChannelUi(channel: Channel, keepPlaying: Boolean) {
        nameText.text = channel.name
        groupText.text = channel.group ?: "Canlı TV"
        initialsView.text = channel.name.take(2).uppercase()
        initialsView.visibility = View.VISIBLE
        logoView.setImageDrawable(null)
        logoView.visibility = View.GONE
        channel.logoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            logoView.visibility = View.VISIBLE
            ImageLoader(this).enqueue(
                ImageRequest.Builder(this).data(url).target(
                    onSuccess = { drawable -> initialsView.visibility = View.GONE; logoView.setImageDrawable(drawable) },
                    onError = { logoView.visibility = View.GONE; initialsView.visibility = View.VISIBLE }
                ).build()
            )
        }
        streamTypeText.text = streamType(urls.getOrNull(urlIndex).orEmpty())
        technicalStream.text = "Yayın\n${streamType(urls.getOrNull(urlIndex).orEmpty())}"
        technicalRoute.text = "Yol\n${urlIndex + 1}/${urls.size.coerceAtLeast(1)}"
        epgNow = null
        epgNext = null
        nowText.text = "Şimdi: EPG yükleniyor…"
        nextText.text = "Sırada: -"
        updateFavoriteHint()
        showOverlay(true)
        if (!keepPlaying) scheduleOverlayHide()
    }

    private fun toggleFavorite() {
        val ch = currentChannel ?: return
        val nowFavorite = favoritesStore.toggle(ch)
        favoriteHint.text = if (nowFavorite) "★ Favorilere eklendi • ⏮ Son kanal" else "☆ Favorilerden çıkarıldı • ⏮ Son kanal"
        showOverlay(true)
        uiHandler.postDelayed({ updateFavoriteHint() }, 1600L)
        scheduleOverlayHide()
    }

    private fun updateFavoriteHint() {
        val favorite = currentChannel?.let { favoritesStore.isFavorite(it) } == true
        favoriteHint.text = (if (favorite) "★ Favoride" else "☆ Favori değil") + " • ⏮ Son kanal"
    }

    private fun loadEpg() {
        val ch = currentChannel ?: return
        val streamId = ch.streamId ?: return
        val profile = profileStore.load() ?: return
        backgroundScope.launch {
            XtreamRepository.loadShortEpg(profile.server, profile.username, profile.password, streamId)
                .onSuccess { entries ->
                    val nowSec = System.currentTimeMillis() / 1000L
                    val current = entries.firstOrNull { e ->
                        val start = e.startTimestamp ?: Long.MIN_VALUE
                        val stop = e.stopTimestamp ?: Long.MAX_VALUE
                        nowSec in start until stop
                    } ?: entries.firstOrNull()
                    val next = current?.let { c -> entries.dropWhile { it != c }.drop(1).firstOrNull() } ?: entries.drop(1).firstOrNull()
                    withContext(Dispatchers.Main) {
                        if (currentChannel?.streamId != streamId) return@withContext
                        epgNow = current?.title
                        epgNext = next?.title
                        nowText.text = "Şimdi: ${epgNow ?: "Bilgi yok"}"
                        nextText.text = "Sırada: ${epgNext ?: "Bilgi yok"}"
                        technicalEpg.text = "EPG\n${epgNow ?: "yok"} → ${epgNext ?: "yok"}"
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        if (currentChannel?.streamId == streamId) {
                            nowText.text = "Şimdi: EPG bilgisi yok"
                            nextText.text = "Sırada: -"
                            technicalEpg.text = "EPG\nyok"
                        }
                    }
                }
        }
    }

    private fun scheduleBufferWatch() {
        val token = ++bufferWatchToken
        uiHandler.postDelayed({
            if (token != bufferWatchToken || bufferStartMs == null || released) return@postDelayed
            if (retryCount < 1) {
                retryCount++
                errorText.text = "Buffer 12 sn aştı • yeniden bağlanıyor"
                errorText.visibility = View.VISIBLE
                playCurrent(preserveRetry = true)
            } else {
                tryNext("Buffer 12 sn aştı")
            }
        }, 12_000L)
    }

    private fun inspectVlcVideoInfo() {
        runCatching {
            val player = mediaPlayer ?: return
            val getMedia = player.javaClass.methods.firstOrNull { it.name == "getMedia" && it.parameterCount == 0 } ?: return
            val mediaObj = getMedia.invoke(player) ?: return
            val getTracks = mediaObj.javaClass.methods.firstOrNull { it.name == "getTracks" && it.parameterCount == 0 } ?: return
            val tracksObj = getTracks.invoke(mediaObj) ?: return
            val tracks = when (tracksObj) {
                is Array<*> -> tracksObj.toList()
                is Iterable<*> -> tracksObj.toList()
                else -> emptyList()
            }
            val video = tracks.firstOrNull { it?.javaClass?.simpleName?.contains("Video", true) == true } ?: return
            fun number(method: String): Number? = runCatching {
                video.javaClass.methods.firstOrNull { it.name.equals(method, true) && it.parameterCount == 0 }?.invoke(video) as? Number
            }.getOrNull()
            val width = number("getWidth")?.toInt() ?: 0
            val height = number("getHeight")?.toInt() ?: 0
            val num = number("getFrameRateNum")?.toFloat() ?: 0f
            val den = number("getFrameRateDen")?.toFloat() ?: 0f
            val fps = if (den > 0f) num / den else number("getFrameRate")?.toFloat() ?: 0f
            val res = if (width > 0 && height > 0) "${width}×${height}" else "-"
            val fpsText = if (fps > 0f) String.format("%.2f FPS", fps) else "-"
            qualityText.text = listOf(res, fpsText).filter { it != "-" }.joinToString(" • ").ifBlank { "Görüntü aktif" }
            technicalVideo.text = "Video\n$res"
            technicalFps.text = "FPS\n$fpsText"
        }
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
