package com.ozel.telefon

import com.ozel.telefon.ui.DetailState
import com.ozel.telefon.ui.SearchMode
import androidx.activity.compose.BackHandler
import com.ozel.telefon.ui.LocalBottomBarInset
import com.ozel.telefon.ui.LocalBackdrop
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.CompositionLocalProvider
import com.ozel.telefon.ui.F7

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import com.ozel.telefon.ui.RecentContextMenu
import com.ozel.telefon.ui.DetailBundle
import com.ozel.telefon.ui.MenuTarget
import com.ozel.telefon.ui.loadDetailBundle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ozel.telefon.ui.ContactsScreen
import com.ozel.telefon.ui.ContactDetailScreen
import com.ozel.telefon.ui.DetailTarget
import com.ozel.telefon.ui.DialerScreen
import com.ozel.telefon.ui.GlassTabBar
import com.ozel.telefon.ui.ListData
import com.ozel.telefon.ui.RecentsScreen
import com.ozel.telefon.ui.VoicemailScreen
import com.ozel.telefon.ui.theme.IOS
import com.ozel.telefon.ui.theme.OzelTelefonTheme

class MainActivity : ComponentActivity() {

    private var initialNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNumber = numberFrom(intent)
        enableEdgeToEdge()
        setContent {
            OzelTelefonTheme {
                AppRoot(initialNumber)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val n = numberFrom(intent)
        if (n.isNotBlank()) initialNumber = n
    }

    private fun numberFrom(intent: Intent?): String =
        intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart ?: ""
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG
    )
    if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
    return list.toTypedArray()
}

private fun coreGranted(context: Context): Boolean =
    listOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG
    ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

private fun isDefaultDialer(context: Context): Boolean =
    context.getSystemService(TelecomManager::class.java).defaultDialerPackage == context.packageName

/** Samsung / OneUI gibi agresif pil yönetimleri servisi öldürüp aramaları kaçırmasın diye. */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private val tabLabels = listOf("Aramalar", "Kişiler", "Klavye")
private val tabIcons = listOf(F7.ClockFill, F7.PersonCropCircleFill, F7.CircleGrid3x3Fill)

private const val KEYPAD_TAB = 2
private const val RECENTS_TAB = 0

@Composable
fun AppRoot(initialNumber: String) {
    val context = LocalContext.current
    val resume = rememberResumeCount()

    var hasPerms by remember { mutableStateOf(coreGranted(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPerms = coreGranted(context) }
    LaunchedEffect(Unit) {
        if (!hasPerms) permLauncher.launch(requiredPermissions())
    }

    // Varsayılan telefon uygulaması mıyız? Ayarlardan dönünce de yeniden kontrol edilir.
    var isDefault by remember { mutableStateOf(isDefaultDialer(context)) }
    LaunchedEffect(resume) { isDefault = isDefaultDialer(context) }

    fun openDefaultAppsSettings() {
        Toast.makeText(
            context,
            "\"Telefon uygulaması\" → Özel Telefon'u seç",
            Toast.LENGTH_LONG
        ).show()
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultDialer(context)
        // Sistem penceresi çıkmadıysa / reddedildiyse ayarlara yönlendir.
        if (!isDefault) openDefaultAppsSettings()
    }

    // Pil optimizasyonundan muaf mıyız? Yalnız varsayılan uygulama olduğumuzda anlamlı;
    // aksi halde sistem zaten aramaları kendi dialer'ına yönlendirir.
    var ignoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LaunchedEffect(resume) { ignoringBattery = isIgnoringBatteryOptimizations(context) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ignoringBattery = isIgnoringBatteryOptimizations(context) }
    fun requestIgnoreBatteryOptimizations() {
        try {
            batteryLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                // bazı OEM'lerde bu ayar sayfası da yok; sessizce geç
            }
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(RECENTS_TAB) }
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotBlank()) tab = KEYPAD_TAB
    }

    // remember: her yeniden çizimde yeni lambda oluşup liste satırlarını gereksiz tetiklemesin.
    val call: (String) -> Unit = remember(context) { { n: String -> placeCall(context, n) } }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }

    // iOS "push" geçişi: kart sağdan kayar, alttaki ekran hafifçe sola çekilip kararır.
    var shownDetail by remember { mutableStateOf<DetailTarget?>(null) }
    var preloaded by remember { mutableStateOf<DetailBundle?>(null) }
    val pushProgress = remember { Animatable(0f) }
    LaunchedEffect(detail) {
        val d = detail
        if (d != null) {
            // Kart içeriği önce hazırlanır, sonra kayar: kayarken içerik zıplamaz.
            preloaded = if (d.number.isBlank()) null else loadDetailBundle(context, d.number)
            shownDetail = d
            pushProgress.animateTo(1f, tween(360, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)))
        } else if (shownDetail != null) {
            pushProgress.animateTo(0f, tween(300, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)))
            shownDetail = null
        }
    }

    // Uzun basma menüsü: arka plan bulanıklaşır.
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    val blurRadius by animateDpAsState(
        targetValue = if (menuTarget != null) 22.dp else 0.dp,
        animationSpec = tween(220),
        label = "blur"
    )
    val tabBarHeight = 76.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Listeler bir kez önden yüklenir; uygulama öne gelince arka planda tazelenir.
    LaunchedEffect(hasPerms, resume) {
        if (hasPerms) ListData.refresh(context)
    }
    // Arama kaydı / rehber değişince (arama bitti, kişi eklendi...) listeler hemen tazelenir.
    DisposableEffect(hasPerms) {
        if (!hasPerms) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val recentsTask = Runnable { ListData.refresh(context, recents = true, contacts = false) }
        val contactsTask = Runnable {
            clearContactCaches()
            ListData.refresh(context)
        }
        val callObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(recentsTask)
                handler.postDelayed(recentsTask, 300)
            }
        }
        val contactObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(contactsTask)
                handler.postDelayed(contactsTask, 800)
            }
        }
        val cr = context.contentResolver
        try {
            cr.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callObserver)
            cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactObserver)
        } catch (e: Exception) {
            // izin yoksa sessizce geç; resume'da yine tazelenir
        }
        onDispose {
            cr.unregisterContentObserver(callObserver)
            cr.unregisterContentObserver(contactObserver)
            handler.removeCallbacksAndMessages(null)
        }
    }
    // Kişi kartı kapanınca (düzenleme/silme sonrası) listeleri tazele.
    var detailWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(detail) {
        if (detail != null) {
            detailWasOpen = true
        } else if (detailWasOpen) {
            detailWasOpen = false
            if (hasPerms) ListData.refresh(context)
        }
    }

    // Kaydırma konumu sekme değişince kaybolmasın.
    val recentsState = rememberLazyListState()
    val contactsState = rememberLazyListState()
    val tabScope = rememberCoroutineScope()
    val backdropLayer = rememberGraphicsLayer()
    BackHandler(enabled = SearchMode.active) { SearchMode.close() }

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().blur(blurRadius)) {
    // Arka plan katmanı: liste + kişi kartı birlikte kaydedilir; alt çubuk ikisinin de bulanığını gösterir.
    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                backdropLayer.record { this@drawWithContent.drawContent() }
                drawLayer(backdropLayer)
            }
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {}
    ) { padding ->
      // İçerik alt çubuğun ALTINDAN da geçer (liste çubuğun arkasında kayar); arkası bulanık görünür.
      CompositionLocalProvider(
          LocalBackdrop provides backdropLayer,
          LocalBottomBarInset provides tabBarHeight
      ) {
      Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .graphicsLayer { translationX = -pushProgress.value * size.width * 0.3f }
        ) {
            if (!isDefault) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IOS.Blue)
                        .clickable {
                            val rm = context.getSystemService(RoleManager::class.java)
                            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                                !rm.isRoleHeld(RoleManager.ROLE_DIALER)
                            ) {
                                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                            } else {
                                openDefaultAppsSettings()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Varsayılan telefon uygulaması yap",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "Arama ekranı ancak o zaman bu uygulamanın tasarımıyla açılır.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            } else if (!ignoringBattery) {
                // Varsayılan uygulama olduk; şimdi Samsung'un pil yönetimi servisi öldürmesin
                // diye muafiyet isteniyor, yoksa gelen aramalar arka planda kaçabilir.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IOS.Blue)
                        .clickable { requestIgnoreBatteryOptimizations() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Arka planda çalışmaya izin ver",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "Pil tasarrufu kapatılmazsa gelen aramalar gecikebilir ya da kaçabilir.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
            // iOS: alt sekmeye basınca içerik ANINDA değişir, çapraz solma yok.
            // Crossfade iki ekranın metnini üst üste bindirip "hayalet" görüntü veriyordu;
            // gerçek iPhone'da sekme geçişinde böyle bir animasyon zaten yok.
            when (tab) {
                0 -> RecentsScreen(
                    hasPerms,
                    recentsState,
                    call,
                    onInfo = { detail = DetailTarget(it) },
                    onMenu = { menuTarget = it }
                )
                1 -> ContactsScreen(
                    hasPerms,
                    contactsState,
                    call,
                    onOpen = { detail = DetailTarget(it) },
                    onAdd = { detail = DetailTarget("", startEditing = true) }
                )
                else -> DialerScreen(initialNumber, call)
            }
        }
      }
      }
    }
    val shown = shownDetail
    if (shown != null) {
        // alttaki ekranın kararması
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = pushProgress.value * 0.2f }
                .background(Color.Black)
        )
        key(shown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - pushProgress.value) * size.width }
            ) {
                ContactDetailScreen(
                    target = shown,
                    tabBarHeight = tabBarHeight,
                    initial = preloaded,
                    onClose = { detail = null },
                    onCall = { n ->
                        detail = null
                        call(n)
                    }
                )
            }
        }
    }
    }
    // Alt çubuk kişi kartının da üstünde durur (iOS gibi); kişi düzenlenirken gizlenir.
    if (!(shownDetail != null && DetailState.editing)) {
        CompositionLocalProvider(LocalBackdrop provides backdropLayer) {
            GlassTabBar(
                labels = tabLabels,
                icons = tabIcons,
                selected = tab,
                onSelect = { i ->
                    SearchMode.close()
                    if (tab == i && detail == null) {
                        when (i) {
                            0 -> tabScope.launch { recentsState.animateScrollToItem(0) }
                            1 -> tabScope.launch { contactsState.animateScrollToItem(0) }
                        }
                    }
                    tab = i
                    detail = null
                },
                onSearch = {
                    // Klavye sekmesinde arama rehberi arar; diğerlerinde bulunulan listeyi süzer.
                    if (tab == 2) tab = 1
                    detail = null
                    SearchMode.open()
                },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            )
        }
    }
    }

    RecentContextMenu(
        target = menuTarget,
        onDismiss = { menuTarget = null },
        onMessage = { n ->
            menuTarget = null
            sendSms(context, n)
        },
        onCall = { n ->
            menuTarget = null
            call(n)
        },
        onVideo = {
            menuTarget = null
            Toast.makeText(context, "Görüntülü arama bu uygulamada yok", Toast.LENGTH_SHORT).show()
        }
    )
    }
}
