package com.ozel.telefon

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ozel.telefon.ui.FavoritesScreen
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

@Composable
private fun TabIcon(index: Int, tint: Color, cutout: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val cx = w / 2f
        val cy = size.height / 2f
        when (index) {
            0 -> { // Hızlı Arama: dolu yıldız
                val path = Path()
                val outer = w * 0.52f
                val inner = outer * 0.46f
                val top = cy + w * 0.03f
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) outer else inner
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val x = cx + (rad * cos(ang)).toFloat()
                    val y = top + (rad * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, tint)
            }
            1 -> { // Son Aramalar: dolu saat
                drawCircle(tint, w * 0.46f, Offset(cx, cy))
                val sw = w * 0.085f
                drawLine(cutout, Offset(cx, cy), Offset(cx, cy - w * 0.25f), sw, StrokeCap.Round)
                drawLine(cutout, Offset(cx, cy), Offset(cx + w * 0.2f, cy), sw, StrokeCap.Round)
            }
            2 -> { // Kişiler: dolu daire içinde kişi
                val r = w * 0.46f
                drawCircle(tint, r, Offset(cx, cy))
                val clip = Path().apply { addOval(Rect(Offset(cx, cy), r)) }
                clipPath(clip) {
                    drawCircle(cutout, w * 0.17f, Offset(cx, cy - w * 0.1f))
                    drawCircle(cutout, w * 0.34f, Offset(cx, cy + w * 0.5f))
                }
            }
            3 -> { // Klavye: 3x3 + alttaki nokta
                val dot = w * 0.075f
                val xs = listOf(w * 0.22f, cx, w * 0.78f)
                val ys = listOf(w * 0.14f, w * 0.38f, w * 0.62f, w * 0.86f)
                for (row in 0 until 3) for (x in xs) drawCircle(tint, dot, Offset(x, ys[row]))
                drawCircle(tint, dot, Offset(cx, ys[3]))
            }
            else -> { // Sesli Mesaj: iki halka + alt çizgi
                val r = w * 0.2f
                val sw = w * 0.085f
                val y = w * 0.4f
                drawCircle(tint, r, Offset(w * 0.27f, y), style = Stroke(sw))
                drawCircle(tint, r, Offset(w * 0.73f, y), style = Stroke(sw))
                drawLine(tint, Offset(w * 0.27f, y + r), Offset(w * 0.73f, y + r), sw, StrokeCap.Round)
            }
        }
    }
}

private class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Hızlı Arama", Icons.Filled.Star),
    Tab("Son Aramalar", Icons.Filled.Schedule),
    Tab("Kişiler", Icons.Filled.AccountCircle),
    Tab("Klavye", Icons.Filled.Dialpad),
    Tab("Sesli Mesaj", Icons.Filled.Voicemail)
)

private const val KEYPAD_TAB = 3

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

    var tab by rememberSaveable { mutableIntStateOf(KEYPAD_TAB) }
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotBlank()) tab = KEYPAD_TAB
    }

    val call: (String) -> Unit = { placeCall(context, it) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .height(52.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        val selected = tab == i
                        val tint = if (selected) IOS.Blue else IOS.Gray
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { tab = i }
                                .padding(top = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            TabIcon(i, tint, MaterialTheme.colorScheme.surface)
                            Spacer(Modifier.height(1.dp))
                            Text(
                                t.label,
                                color = tint,
                                fontSize = 9.5.sp,
                                letterSpacing = (-0.1).sp,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
            }
            when (tab) {
                0 -> FavoritesScreen(hasPerms, call)
                1 -> RecentsScreen(hasPerms, call) { detail = DetailTarget(it) }
                2 -> ContactsScreen(
                    hasPerms,
                    call,
                    onOpen = { detail = DetailTarget(it) },
                    onAdd = { detail = DetailTarget("", startEditing = true) }
                )
                3 -> DialerScreen(initialNumber, call)
                else -> VoicemailScreen()
            }
        }
    }
    detail?.let { d ->
        ContactDetailScreen(
            target = d,
            onClose = { detail = null },
            onCall = { n ->
                detail = null
                call(n)
            }
        )
    }
    }
}

