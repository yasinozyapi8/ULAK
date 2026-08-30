package com.ulak.tv

import android.os.Bundle
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import com.ulak.tv.data.m3u.M3uRepository
import com.ulak.tv.data.model.Channel
import com.ulak.tv.data.xtream.XtreamProfileStore
import com.ulak.tv.data.xtream.XtreamRepository
import com.ulak.tv.update.GitHubUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

private val Gold = Color(0xFFF4B400)
private val Bg = Color(0xFF050607)
private val Card = Color(0xFF15181C)
private val CardSelected = Color(0xFF24201A)
private val Muted = Color(0xFF9AA0A6)

private sealed interface Screen {
    data object Home : Screen
    data object AddProfile : Screen
    data object M3uLogin : Screen
    data object XtreamLogin : Screen
    data object Channels : Screen
    data object Update : Screen
    data class Player(val index: Int) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { UlakApp() } }
    }
}

@Composable
fun UlakApp() {
    val context = LocalContext.current
    val profileStore = remember { XtreamProfileStore(context.applicationContext) }
    var savedProfile by remember { mutableStateOf(profileStore.load()) }
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var channelBackScreen: Screen by remember { mutableStateOf(Screen.Home) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connectSavedProfile() {
        val p = savedProfile ?: return
        homeLoading = true
        homeError = null
        scope.launch {
            XtreamRepository.loginAndLoadLive(p.server, p.username, p.password)
                .onSuccess {
                    channels = it
                    channelBackScreen = Screen.Home
                    screen = Screen.Channels
                }
                .onFailure { homeError = it.message ?: "Kayıtlı profile bağlanılamadı." }
            homeLoading = false
        }
    }

    if (screen is Screen.Player) {
        PlayerScreen(channels = channels, initialIndex = (screen as Screen.Player).index, onBack = { screen = Screen.Channels })
        return
    }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1117), Bg, Color.Black)
                )
            )
            .padding(horizontal = 42.dp, vertical = 28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Header(savedProfile?.name)
            when (screen) {
                Screen.Home -> HomeScreen(
                    profile = savedProfile,
                    loading = homeLoading,
                    error = homeError,
                    onLiveTv = { if (savedProfile != null) connectSavedProfile() else screen = Screen.AddProfile },
                    onAddProfile = { screen = Screen.AddProfile },
                    onCheckUpdate = { screen = Screen.Update },
                    onDeleteProfile = {
                        profileStore.clear()
                        savedProfile = null
                        channels = emptyList()
                        homeError = null
                    }
                )
                Screen.AddProfile -> ProfileScreen(
                    onM3u = { screen = Screen.M3uLogin },
                    onXtream = { screen = Screen.XtreamLogin },
                    onBack = { screen = Screen.Home }
                )
                Screen.M3uLogin -> M3uLoginScreen(
                    onLoaded = {
                        channels = it
                        channelBackScreen = Screen.M3uLogin
                        screen = Screen.Channels
                    },
                    onBack = { screen = Screen.AddProfile }
                )
                Screen.XtreamLogin -> XtreamLoginScreen(
                    initial = savedProfile,
                    onLoaded = { profile, loaded ->
                        profileStore.save(profile.name, profile.server, profile.username, profile.password)
                            .onSuccess { persisted ->
                                savedProfile = persisted
                                homeError = null
                                channels = loaded
                                channelBackScreen = Screen.Home
                                screen = Screen.Channels
                            }
                            .onFailure {
                                homeError = "Profil kaydedilemedi: ${it.message ?: "bilinmeyen hata"}"
                                screen = Screen.Home
                            }
                    },
                    onBack = { screen = Screen.AddProfile }
                )
                Screen.Update -> UpdateScreen(onBack = { screen = Screen.Home })
                Screen.Channels -> ChannelBrowserScreen(
                    channels = channels,
                    onPlay = { channel ->
                        val index = channels.indexOfFirst { it.streamUrl == channel.streamUrl }
                        screen = Screen.Player(index.coerceAtLeast(0))
                    },
                    onBack = { screen = channelBackScreen }
                )
                is Screen.Player -> Unit
            }
        }
    }
}

@Composable
private fun Header(profileName: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("U▶", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Column {
                Text("ULAK", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Canlı TV • Film • Dizi", color = Gold, fontSize = 14.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(profileName ?: "Profil yok", color = if (profileName != null) Color.White else Muted, fontSize = 13.sp)
            Text("v0.2.1", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeScreen(
    profile: XtreamProfileStore.SavedProfile?,
    loading: Boolean,
    error: String?,
    onLiveTv: () -> Unit,
    onAddProfile: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDeleteProfile: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Yayını sana ulaştırır.", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (profile != null) "${profile.name} hazır. Canlı yayınlara tek tuşla bağlan." else "M3U veya Xtream profilini ekleyerek başla.",
                    color = Muted,
                    fontSize = 16.sp
                )
            }
            ProfileStatusCard(profile)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PremiumMenuCard("▶", "CANLI TV", if (profile != null) "Kanalları aç" else "Profil gerekli", true, onLiveTv)
            PremiumMenuCard("▣", "FİLMLER", "Yakında", false) {}
            PremiumMenuCard("▤", "DİZİLER", "Yakında", false) {}
            PremiumMenuCard("★", "FAVORİLER", "Yakında", false) {}
            PremiumMenuCard("↺", "SON İZLENENLER", "Yakında", false) {}
        }

        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), color = Gold)
            Text("Kayıtlı profile bağlanılıyor…", color = Gold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAddProfile) { Text(if (profile == null) "+ PROFİL EKLE" else "PROFİLİ DÜZENLE") }
            if (profile != null) Button(onClick = onDeleteProfile) { Text("PROFİLİ SİL") }
            Button(onClick = onCheckUpdate) { Text("GÜNCELLEME") }
        }
    }
}



@Composable
private fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")
    }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var release by remember { mutableStateOf<GitHubUpdateManager.ReleaseInfo?>(null) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var message by remember { mutableStateOf("GitHub Releases üzerinden yeni ULAK sürümlerini kontrol et.") }
    var error by remember { mutableStateOf<String?>(null) }

    fun check() {
        checking = true
        error = null
        release = null
        downloadedApk = null
        message = "GitHub kontrol ediliyor…"
        scope.launch {
            GitHubUpdateManager.checkLatest(currentVersion)
                .onSuccess { found ->
                    release = found
                    message = if (found == null) {
                        "ULAK v$currentVersion güncel."
                    } else {
                        "Yeni sürüm bulundu: v${found.version}"
                    }
                }
                .onFailure {
                    error = it.message ?: "Güncelleme kontrol edilemedi."
                    message = "Güncelleme kontrolü başarısız."
                }
            checking = false
        }
    }

    BackHandler(enabled = !downloading, onBack = onBack)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("ULAK Güncelleme", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Yüklü sürüm: v$currentVersion", color = Gold, fontSize = 15.sp)

        Column(
            Modifier.fillMaxWidth(0.72f)
                .background(Card, RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF30343A), RoundedCornerShape(18.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(message, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            release?.let { r ->
                Text(r.title, color = Gold, fontSize = 14.sp)
                if (r.notes.isNotBlank()) {
                    Text(r.notes.take(600), color = Muted, fontSize = 13.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Text("APK: ${r.apkName}", color = Muted, fontSize = 12.sp)
            }
            if (downloading) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Gold)
                Text("İndiriliyor… %$progress", color = Gold, fontSize = 14.sp)
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !checking && !downloading, onClick = { check() }) {
                Text(if (checking) "KONTROL EDİLİYOR…" else "GÜNCELLEMELERİ KONTROL ET")
            }
            release?.let { r ->
                Button(enabled = !checking && !downloading, onClick = {
                    downloading = true
                    progress = 0
                    error = null
                    scope.launch {
                        GitHubUpdateManager.downloadApk(context, r) { progress = it }
                            .onSuccess { file ->
                                downloadedApk = file
                                message = "APK hazır. Kurulum ekranını açabilirsin."
                                if (GitHubUpdateManager.canInstallPackages(context)) {
                                    runCatching { GitHubUpdateManager.launchInstaller(context, file) }
                                        .onFailure { error = it.message ?: "Kurulum ekranı açılamadı." }
                                } else {
                                    message = "ULAK için uygulama yükleme izni gerekli. İzni açıp geri dön ve KUR'u seç."
                                    GitHubUpdateManager.openInstallPermission(context)
                                }
                            }
                            .onFailure { error = it.message ?: "APK indirilemedi." }
                        downloading = false
                    }
                }) { Text("İNDİR VE GÜNCELLE") }
            }
            downloadedApk?.let { file ->
                Button(enabled = !downloading, onClick = {
                    if (GitHubUpdateManager.canInstallPackages(context)) {
                        runCatching { GitHubUpdateManager.launchInstaller(context, file) }
                            .onFailure { error = it.message ?: "Kurulum ekranı açılamadı." }
                    } else {
                        GitHubUpdateManager.openInstallPermission(context)
                    }
                }) { Text("KUR") }
            }
            Button(enabled = !downloading, onClick = onBack) { Text("GERİ") }
        }

        Text(
            "Kaynak: github.com/yasinozyapi8/ULAK • Releases/latest",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProfileStatusCard(profile: XtreamProfileStore.SavedProfile?) {
    Column(
        Modifier.width(320.dp).background(Card, RoundedCornerShape(18.dp)).border(1.dp, Color(0xFF30343A), RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("AKTİF PROFİL", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(profile?.name ?: "Profil eklenmedi", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(profile?.server ?: "Xtream / M3U", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (profile != null) "● Hazır" else "○ Bekliyor", color = if (profile != null) Color(0xFF81C784) else Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PremiumMenuCard(icon: String, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(205.dp).height(145.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) CardSelected else Card, RoundedCornerShape(18.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Gold else Color(0xFF2D3136), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(icon, color = if (enabled) Gold else Muted, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Column {
            Text(title, color = if (enabled) Color.White else Muted, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileScreen(onM3u: () -> Unit, onXtream: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Profil Ekle / Düzenle", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Yayın kaynağını seç.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Button(onClick = onM3u, modifier = Modifier.width(280.dp).height(96.dp)) { Column { Text("M3U / URL"); Text("Playlist bağlantısı", fontSize = 13.sp) } }
            Button(onClick = onXtream, modifier = Modifier.width(280.dp).height(96.dp)) { Column { Text("XTREAM CODES"); Text("Sunucu • kullanıcı • parola", fontSize = 13.sp) } }
        }
        Button(onClick = onBack) { Text("GERİ") }
    }
}

@Composable
private fun XtreamLoginScreen(
    initial: XtreamProfileStore.SavedProfile?,
    onLoaded: (XtreamProfileStore.SavedProfile, List<Channel>) -> Unit,
    onBack: () -> Unit
) {
    var profileName by remember { mutableStateOf(initial?.name ?: "Ev IPTV") }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = !loading, onBack = onBack)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Xtream Codes Girişi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Bilgiler bu cihazda kaydedilir; parola Android Keystore ile şifrelenir.", color = Muted)
            OutlinedTextField(profileName, { profileName = it }, label = { Text("Profil adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(server, { server = it; error = null }, label = { Text("Sunucu URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it; error = null }, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it; error = null }, label = { Text("Parola") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !loading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true; error = null
                        scope.launch {
                            XtreamRepository.loginAndLoadLive(server, username, password)
                                .onSuccess { onLoaded(XtreamProfileStore.SavedProfile(profileName.ifBlank { "Ev IPTV" }, server.trim(), username.trim(), password), it) }
                                .onFailure { error = it.message ?: "Xtream bağlantısı kurulamadı." }
                            loading = false
                        }
                    }
                ) { Text(if (loading) "BAĞLANIYOR…" else "GİRİŞ YAP VE KAYDET") }
                Button(onClick = onBack, enabled = !loading) { Text("GERİ") }
                if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = Gold)
            }
        }
        Column(
            Modifier.width(330.dp).background(Card, RoundedCornerShape(18.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ULAK PROFİL", color = Gold, fontWeight = FontWeight.Bold)
            Text("Bir kez giriş yaptıktan sonra aynı bilgileri tekrar yazmana gerek kalmaz.", color = Color.White, fontSize = 15.sp)
            Text("• Sunucu ve kullanıcı bilgisi yerelde saklanır\n• Parola şifreli tutulur\n• Açılışta kayıtlı profil hazır gelir", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun M3uLoginScreen(onLoaded: (List<Channel>) -> Unit, onBack: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("M3U / M3U8 URL", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Yetkili olduğun playlist bağlantısını gir.", color = Muted)
        OutlinedTextField(url, { url = it; error = null }, label = { Text("Playlist URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.72f))
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !loading && url.isNotBlank(), onClick = {
                loading = true; error = null
                scope.launch {
                    M3uRepository.load(url.trim()).onSuccess(onLoaded).onFailure { error = it.message ?: "Playlist yüklenemedi." }
                    loading = false
                }
            }) { Text(if (loading) "YÜKLENİYOR…" else "BAĞLAN") }
            Button(onClick = onBack, enabled = !loading) { Text("GERİ") }
            if (loading) CircularProgressIndicator(Modifier.size(30.dp), color = Gold)
        }
    }
}

@Composable
private fun ChannelBrowserScreen(channels: List<Channel>, onPlay: (Channel) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedGroup by remember(channels) { mutableStateOf("Tümü") }
    var selectedChannel by remember(channels) { mutableStateOf(channels.firstOrNull()) }
    val groupCounts = remember(channels) { channels.groupingBy { it.group ?: "Diğer" }.eachCount() }
    val groups = remember(channels) { listOf("Tümü") + channels.map { it.group ?: "Diğer" }.distinct().sorted() }
    val visibleChannels = remember(channels, selectedGroup) { if (selectedGroup == "Tümü") channels else channels.filter { (it.group ?: "Diğer") == selectedGroup } }
    LaunchedEffect(selectedGroup) { selectedChannel = visibleChannels.firstOrNull() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroChannelPanel(selectedChannel, visibleChannels.size)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LazyColumn(Modifier.width(205.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text("KATEGORİLER", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)) }
                items(groups) { group ->
                    val selected = group == selectedGroup
                    val count = if (group == "Tümü") channels.size else groupCounts[group] ?: 0
                    FocusRow(selected, { selectedGroup = group }, Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(group, color = if (selected) Gold else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(count.toString(), color = if (selected) Gold else Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("$selectedGroup  •  ${visibleChannels.size} kanal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleChannels, key = { it.streamUrl }) { channel ->
                        ChannelRow(channel, selectedChannel?.streamUrl == channel.streamUrl, { selectedChannel = channel }) { onPlay(channel) }
                    }
                }
            }
            ChannelInfoPanel(selectedChannel, Modifier.width(285.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun HeroChannelPanel(channel: Channel?, visibleCount: Int) {
    Row(
        Modifier.fillMaxWidth().height(135.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF101820), Color(0xFF111111), Color(0xFF1A1408))), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF2C3138), RoundedCornerShape(20.dp)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(Modifier.size(92.dp).background(Color(0xFF0B0D0F), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            if (!channel?.logoUrl.isNullOrBlank()) AsyncImage(channel?.logoUrl, "Kanal logosu", Modifier.fillMaxSize().padding(12.dp))
            else Text(channel?.name?.take(2)?.uppercase() ?: "TV", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(channel?.name ?: "Canlı TV", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel?.group ?: "$visibleCount kanal hazır", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("OK ile oynat • Yön tuşlarıyla kanal seç • BACK ile geri dön", color = Muted, fontSize = 12.sp)
        }
        Text("CANLI", color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.background(Gold, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun FocusRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.onFocusChanged { focused = it.isFocused }.background(if (selected || focused) CardSelected else Card, RoundedCornerShape(10.dp)).border(if (focused) 2.dp else 1.dp, if (focused) Gold else Color(0xFF292D32), RoundedCornerShape(10.dp)).clickable(onClick = onClick).focusable().padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
}

@Composable
private fun ChannelRow(channel: Channel, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .background(if (focused || selected) CardSelected else Card, RoundedCornerShape(12.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Gold else Color(0xFF292D32), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).focusable().padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(52.dp).background(Color(0xFF0C0E10), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (!channel.logoUrl.isNullOrBlank()) AsyncImage(channel.logoUrl, "${channel.name} logosu", Modifier.fillMaxSize().padding(6.dp))
            else Text(channel.name.take(2).uppercase(), color = Gold, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(channel.group ?: "Diğer", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        Text(if (focused) "▶" else streamType(channel.streamUrl), color = if (focused) Gold else Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ChannelInfoPanel(channel: Channel?, modifier: Modifier = Modifier) {
    Column(modifier.background(Card, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("KANAL BİLGİSİ", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (channel == null) { Text("Bir kanal seç.", color = Muted); return@Column }
        Box(Modifier.fillMaxWidth().height(125.dp).background(Color(0xFF0C0E10), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            if (!channel.logoUrl.isNullOrBlank()) AsyncImage(channel.logoUrl, "${channel.name} logosu", Modifier.fillMaxSize().padding(18.dp))
            else Text(channel.name.take(2).uppercase(), color = Gold, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        Text(channel.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        InfoLine("Kategori", channel.group ?: "Diğer")
        InfoLine("Yayın", streamType(channel.streamUrl))
        if (!channel.tvgId.isNullOrBlank()) InfoLine("EPG ID", channel.tvgId!!)
        Spacer(Modifier.weight(1f))
        Text("Gerçek EPG program bilgisi sonraki sürümde.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun streamType(url: String): String = when {
    url.contains(".m3u8", true) -> "HLS"
    url.contains(".ts", true) -> "MPEG-TS"
    else -> "AUTO"
}

private fun playbackCandidates(channel: Channel): List<String> =
    (listOf(channel.streamUrl) + channel.alternateStreamUrls).distinct()

private fun safeError(error: PlaybackException): String {
    var cause: Throwable? = error
    var httpCode: Int? = null
    var httpMessage: String? = null
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            httpCode = cause.responseCode
            httpMessage = when (cause.responseCode) {
                401 -> "Yetkilendirme reddedildi"
                403 -> "Sunucu erişimi reddetti"
                404 -> "Yayın adresi bulunamadı"
                in 500..599 -> "Yayın sunucusu geçici hata verdi"
                else -> "HTTP yanıt hatası"
            }
            break
        }
        cause = cause.cause
    }

    val rootCause = error.cause?.javaClass?.simpleName
    return buildString {
        append(error.errorCodeName)
        if (httpCode != null) append(" • HTTP ").append(httpCode).append(" • ").append(httpMessage)
        else if (!rootCause.isNullOrBlank()) append(" • ").append(rootCause)
        error.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it.take(120)) }
    }
}


@Composable
private fun TechnicalLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 205.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(channels: List<Channel>, initialIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    if (channels.isEmpty()) {
        BackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Kanal listesi boş.", color = Color.White)
        }
        return
    }

    var currentIndex by remember(channels, initialIndex) { mutableIntStateOf(initialIndex.coerceIn(channels.indices)) }
    val channel = channels[currentIndex]
    var candidates by remember { mutableStateOf(playbackCandidates(channel)) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var activeUrl by remember { mutableStateOf(candidates.first()) }
    var attemptLog by remember { mutableStateOf(emptyList<String>()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Yayın hazırlanıyor…") }
    var overlayVisible by remember { mutableStateOf(true) }
    var technicalVisible by remember { mutableStateOf(false) }
    var overlayToken by remember { mutableIntStateOf(0) }
    var audioInfo by remember { mutableStateOf("Ses: bekleniyor") }
    var videoInfo by remember { mutableStateOf("Video: bekleniyor") }
    var playbackGeneration by remember { mutableIntStateOf(0) }
    var useVlc by remember { mutableStateOf(false) }
    var vlcStatus by remember { mutableStateOf("VLC bekliyor") }
    val playerScope = rememberCoroutineScope()

    // Keep the default ExoPlayer renderer/buffer behavior, but use an HTTP
    // data source with TV-friendly request headers. Some IPTV servers reject
    // generic/empty user agents even when the account itself is valid.
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/120 Safari/537.36 ULAK/0.2.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )
        // Keep Media3's default extractor/renderer selection. v0.1.5 forced
        // MPEG-TS parsing globally, which caused a regression on streams that
        // previously rendered video correctly.
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    // Software-decoder capable fallback engine. It is activated only when
    // Media3 successfully gets audio but cannot create a video track/decoder.
    val libVlc = remember {
        LibVLC(
            context.applicationContext,
            arrayListOf("--network-caching=1500", "--clock-jitter=0", "--clock-synchro=0")
        )
    }
    val vlcPlayer = remember { VlcMediaPlayer(libVlc) }

    fun showOverlay() {
        overlayVisible = true
        overlayToken++
    }

    fun toggleTechnical() {
        technicalVisible = !technicalVisible
        overlayVisible = true
        overlayToken++
    }

    fun updateDiagnostics() {
        val af = player.audioFormat
        audioInfo = if (af != null) {
            val codec = af.sampleMimeType?.substringAfter('/')?.uppercase() ?: "?"
            val rate = if (af.sampleRate > 0) "${af.sampleRate / 1000.0} kHz" else "? kHz"
            val ch = if (af.channelCount > 0) "${af.channelCount} kanal" else "? kanal"
            "Ses: $codec • $rate • $ch"
        } else "Ses: algılanmadı"

        val vf = player.videoFormat
        videoInfo = if (vf != null) {
            val codec = vf.sampleMimeType?.substringAfter('/')?.uppercase() ?: "?"
            val size = if (vf.width > 0 && vf.height > 0) "${vf.width}×${vf.height}" else "çözünürlük ?"
            "Video: $codec • $size"
        } else "Video: algılanmadı"
    }

    fun playUrl(url: String) {
        playbackGeneration++
        if (useVlc) {
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.detachViews() }
        }
        useVlc = false
        vlcStatus = "VLC bekliyor"
        activeUrl = url
        playbackError = null
        status = "Yayın hazırlanıyor…"
        audioInfo = "Ses: bekleniyor"
        videoInfo = "Video: bekleniyor"
        player.stop()
        player.clearMediaItems()
        // Let Media3 sniff the actual stream/container. Some Xtream panels
        // return HLS/TS content through URLs whose extension does not describe
        // the response reliably.
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        showOverlay()
    }

    fun switchToVlc(url: String) {
        player.stop()
        player.clearMediaItems()
        activeUrl = url
        useVlc = true
        vlcStatus = "VLC video motoru başlatılıyor…"
        playbackError = null
        status = "VLC uyumluluk modu"
        videoInfo = "Video: VLC ile deneniyor"
        showOverlay()
    }

    fun tuneTo(index: Int) {
        val normalized = when {
            index < 0 -> channels.lastIndex
            index > channels.lastIndex -> 0
            else -> index
        }
        if (useVlc) {
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.detachViews() }
            useVlc = false
        }
        currentIndex = normalized
        candidates = playbackCandidates(channels[normalized])
        candidateIndex = 0
        attemptLog = emptyList()
        playUrl(candidates.first())
    }

    fun tryNextCandidate(reason: String): Boolean {
        attemptLog = attemptLog + "${streamType(activeUrl)}: $reason"
        val next = candidateIndex + 1
        return if (next < candidates.size) {
            candidateIndex = next
            status = "Alternatif yayın deneniyor (${next + 1}/${candidates.size})…"
            playUrl(candidates[next])
            true
        } else {
            false
        }
    }

    LaunchedEffect(overlayToken) {
        if (overlayVisible && !technicalVisible) {
            delay(3200)
            overlayVisible = false
        }
    }

    LaunchedEffect(Unit) {
        candidates = playbackCandidates(channel)
        candidateIndex = 0
        playUrl(candidates.first())
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "Yükleniyor…"
                    Player.STATE_READY -> "Canlı yayın"
                    Player.STATE_ENDED -> "Yayın sona erdi"
                    else -> status
                }
                if (playbackState == Player.STATE_READY) {
                    updateDiagnostics()
                    val generationAtReady = playbackGeneration
                    val candidateAtReady = candidateIndex
                    playerScope.launch {
                        delay(5000)
                        if (generationAtReady != playbackGeneration || candidateAtReady != candidateIndex) return@launch
                        updateDiagnostics()
                        val hasAudio = player.audioFormat != null
                        val hasVideo = player.videoFormat != null
                        if (hasAudio && !hasVideo) {
                            val reason = "Ses var, Media3 video track/decoder oluşturamadı (5 sn)"
                            attemptLog = attemptLog + "${streamType(activeUrl)}: $reason → VLC"
                            switchToVlc(activeUrl)
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                updateDiagnostics()
            }

            override fun onPlayerError(error: PlaybackException) {
                val detail = safeError(error)
                if (!tryNextCandidate(detail)) {
                    playbackError = detail
                    status = "Yayın açılamadı • ${candidates.size} yol denendi"
                    updateDiagnostics()
                    showOverlay()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.detachViews() }
            runCatching { vlcPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (useVlc) {
            key(activeUrl) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_BACK -> { onBack(); true }
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { tuneTo(currentIndex - 1); true }
                                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { tuneTo(currentIndex + 1); true }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggleTechnical(); true }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { showOverlay(); true }
                                else -> false
                            }
                        },
                    factory = { ctx ->
                        VLCVideoLayout(ctx).apply {
                            keepScreenOn = true
                            requestFocus()
                            runCatching { vlcPlayer.detachViews() }
                            vlcPlayer.attachViews(this, null, false, false)
                            vlcPlayer.setEventListener { event ->
                                when (event.type) {
                                    VlcMediaPlayer.Event.Playing -> {
                                        vlcStatus = "VLC oynatıyor"
                                        status = "Canlı yayın • VLC"
                                        videoInfo = "Video: VLC yazılım/uyumluluk motoru"
                                    }
                                    VlcMediaPlayer.Event.Vout -> {
                                        vlcStatus = "VLC video çıkışı aktif"
                                        videoInfo = "Video: VLC çıkışı aktif"
                                    }
                                    VlcMediaPlayer.Event.EncounteredError -> {
                                        vlcStatus = "VLC yayın hatası"
                                        playbackError = "Media3 görüntü üretemedi; VLC de bu akışı oynatamadı."
                                        status = "VLC yayın hatası"
                                        showOverlay()
                                    }
                                }
                            }
                            val media = Media(libVlc, Uri.parse(activeUrl)).apply {
                                setHWDecoderEnabled(true, false)
                                addOption(":network-caching=1500")
                                addOption(":http-user-agent=Mozilla/5.0 (Linux; Android TV) ULAK/0.2.0")
                            }
                            vlcPlayer.media = media
                            media.release()
                            vlcPlayer.play()
                        }
                    }
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BACK -> { onBack(); true }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { tuneTo(currentIndex - 1); true }
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { tuneTo(currentIndex + 1); true }
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { toggleTechnical(); true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { showOverlay(); true }
                            else -> false
                        }
                    },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        requestFocus()
                    }
                },
                update = { it.player = player }
            )
        }

        if (overlayVisible) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF20B0D0F), Color(0xE614171A), Color(0xCC14171A))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .border(1.dp, Color(0xFF2A2F35), RoundedCornerShape(22.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        Modifier
                            .size(78.dp)
                            .background(Color(0xFF101214), RoundedCornerShape(18.dp))
                            .border(1.dp, Color(0xFF272B30), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.logoUrl.isNullOrBlank()) {
                            AsyncImage(channel.logoUrl, "Kanal logosu", Modifier.fillMaxSize().padding(9.dp))
                        } else {
                            Text(channel.name.take(2).uppercase(), color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            channel.name,
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("● Canlı yayın", color = Color(0xFF69E58B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("•", color = Muted)
                            Text(if (playbackError == null) "Stabil" else "Kontrol ediliyor", color = if (playbackError == null) Color(0xFF69E58B) else Gold, fontSize = 13.sp)
                            Text("•", color = Muted)
                            Text(streamType(activeUrl), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(channel.group ?: "Canlı TV", color = Muted, fontSize = 12.sp)
                        if (candidateIndex > 0) {
                            Text("Uyumluluk modu • ${candidateIndex + 1}/${candidates.size}", color = Muted, fontSize = 10.sp)
                        }
                        playbackError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("↑ Önceki", color = Color.White, fontSize = 11.sp)
                        Text("↓ Sonraki", color = Color.White, fontSize = 11.sp)
                        Text(if (technicalVisible) "OK Detayları gizle" else "OK Teknik bilgi", color = Gold, fontSize = 10.sp)
                    }
                }

                if (technicalVisible) {
                    Column(
                        Modifier
                            .width(330.dp)
                            .background(Color(0xF214171A), RoundedCornerShape(22.dp))
                            .border(1.dp, Color(0xFF30353B), RoundedCornerShape(22.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Teknik Bilgiler", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        TechnicalLine("Video", videoInfo.removePrefix("Video: "))
                        TechnicalLine("Ses", audioInfo.removePrefix("Ses: "))
                        TechnicalLine("Yayın", streamType(activeUrl))
                        TechnicalLine("Motor", if (useVlc) "LibVLC" else "Media3 / ExoPlayer")
                        TechnicalLine("Durum", status)
                        TechnicalLine("Yol", "${candidateIndex + 1}/${candidates.size}")
                        if (attemptLog.isNotEmpty()) {
                            Text("Son tanılama", color = Muted, fontSize = 10.sp)
                            Text(attemptLog.takeLast(2).joinToString("\n"), color = Color(0xFFCFD3D8), fontSize = 9.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

