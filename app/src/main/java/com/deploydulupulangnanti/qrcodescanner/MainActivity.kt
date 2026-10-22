package com.deploydulupulangnanti.qrcodescanner

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.deploydulupulangnanti.qrcodescanner.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private val Paper = Color(0xFFF8F6F2)
private val Ink = Color(0xFF202820)
private val Rust = Color(0xFFB94C2B)
private val Muted = Color(0xFF74796F)
private val Line = Color(0xFFE4E3DB)
private val LightPalette = lightColorScheme(
    primary = Rust, onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DBCD), onPrimaryContainer = Color(0xFF642A17),
    secondary = Ink, secondaryContainer = Color(0xFFE5EADC), onSecondaryContainer = Ink,
    tertiary = Color(0xFF65755E), tertiaryContainer = Color(0xFFE5EADC), onTertiaryContainer = Ink,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFEDEEE7), onSurfaceVariant = Color(0xFF596353),
    outline = Color(0xFF7E8779), outlineVariant = Line,
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFFFB398), onPrimary = Ink,
    primaryContainer = Color(0xFF71331E), onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFBBCDB5), secondaryContainer = Color(0xFF354532), onSecondaryContainer = Color(0xFFD7E6CD),
    tertiary = Color(0xFFBBCDB5), tertiaryContainer = Color(0xFF354532), onTertiaryContainer = Color(0xFFD7E6CD),
    background = Color(0xFF171D18), onBackground = Color(0xFFE3E9DE), surface = Color(0xFF171D18), onSurface = Color(0xFFE3E9DE),
    surfaceVariant = Color(0xFF283129), onSurfaceVariant = Color(0xFFBDC8B7), outline = Color(0xFF889580), outlineVariant = Color(0xFF424C3F),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val capture = BuildConfig.DEBUG && intent.getBooleanExtra("capture", false)
        val captureTab = if (capture) intent.getIntExtra("tab", 0) else 0
        val demoScene = if (capture) runCatching { assets.open("demo_scan.png").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() else null
        setContent {
            val model: ScannerModel = viewModel()
            SideEffect {
                androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !model.dark
                    isAppearanceLightNavigationBars = !model.dark
                }
            }
            LaunchedEffect(model.loaded) { if (capture && captureTab == 4 && model.loaded) model.result = model.records.firstOrNull() }
            MaterialTheme(colorScheme = if (model.dark) DarkPalette else LightPalette, typography = Typography()) {
                Surface(Modifier.fillMaxSize()) { ScannerApp(model, if (captureTab == 4) 2 else captureTab, demoScene) }
            }
        }
    }
}

private fun notify(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
private fun launchExternal(context: Context, intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { notify(context, "No compatible app is installed.") } }
private fun shareText(context: Context, value: String) = launchExternal(context, Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value) }, "Share content"))

@Composable
fun ScannerApp(model: ScannerModel, initialTab: Int = 0, demoScene: android.graphics.Bitmap? = null) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val factor = maxOf(info.size.width, info.size.height) / 1800f
                        if (factor > 1) decoder.setTargetSize((info.size.width / factor).toInt(), (info.size.height / factor).toInt())
                    } else {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                        val options = android.graphics.BitmapFactory.Options().apply { while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1800) inSampleSize *= 2 }
                        val decoded = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) } ?: error("Image unavailable")
                        val orientation = runCatching { context.contentResolver.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1) } ?: 1 }.getOrDefault(1)
                        val matrix = android.graphics.Matrix().apply { when (orientation) {
                            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                            7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
                        } }
                        if (orientation > 1) android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it != decoded) decoded.recycle() } else decoded
                    }
                    try { BarcodeEngine.decode(bitmap) } finally { bitmap.recycle() }
                }.getOrNull()
            }
            importing = false
            if (result != null) model.accept(result.text, result.barcodeFormat.name) else notify(context, "No readable code found. Try a sharper, closer image.")
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = model.csv()
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("Cannot open file") }.isSuccess }
                notify(context, if (ok) "History exported." else "Could not save the file.")
            }
        }
    }
    val nav = listOf("Scan" to Icons.Outlined.QrCodeScanner, "Create" to Icons.Outlined.AddCircleOutline, "History" to Icons.Outlined.History, "Settings" to Icons.Outlined.Tune)
    BackHandler(enabled = model.result != null || tab != 0) { if (model.result != null) model.result = null else tab = 0 }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 700.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(24.dp))
                nav.forEachIndexed { index, item -> NavigationRailItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }) }
            }
            Scaffold(bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    nav.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant)) }
                }
            }, modifier = Modifier.weight(1f)) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    if (!model.loaded) CircularProgressIndicator(Modifier.align(Alignment.Center)) else {
                        val contentModifier = Modifier.widthIn(max = if (tab == 0 && wide) 960.dp else 760.dp).fillMaxSize()
                        when (tab) {
                            0 -> ScanScreen(model, importing, { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentModifier, demoScene)
                            1 -> CreateScreen(model, contentModifier, demoScene != null)
                            2 -> HistoryScreen(model, { exportCsv.launch("qr-scanner-history.csv") }, contentModifier)
                            3 -> SettingsScreen(model, contentModifier)
                        }
                    }
                }
            }
        }
    }
    model.result?.let { record -> ResultSheet(record, model) }
}

@Composable
private fun PageHeading(kicker: String, title: String, subtitle: String, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
        }
        action?.invoke()
    }
}

@Composable
private fun ScanScreen(model: ScannerModel, importing: Boolean, pickImage: () -> Unit, modifier: Modifier, demoScene: android.graphics.Bitmap? = null) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    var torch by rememberSaveable { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageHeading("QR Scanner Pro", "A little scan.\nA lot of possibility.", "Point. Discover. Keep moving.")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text("  SCAN A QR OR BARCODE", letterSpacing = 1.6.sp, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" On device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          val previewHeight = (maxWidth / 1.03f).coerceIn(270.dp, 430.dp)
          val frameSize = minOf(maxWidth * .68f, previewHeight * .7f)
          Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(28.dp)).background(Color(0xFF252E27)), contentAlignment = Alignment.Center) {
            if (demoScene != null) Image(demoScene.asImageBitmap(), "Sample camera scene", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else if (permission && model.result == null && cameraError == null) CameraScanner(torch, { value, format ->
                if (model.haptics) (context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                model.accept(value, format)
            }, { if (it.contains("flashlight")) { notify(context, it); torch = false } else cameraError = it }, Modifier.fillMaxSize())
            ScanCorners(Modifier.size(frameSize))
            if (demoScene == null && (!permission || cameraError != null)) Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.QrCodeScanner, null, tint = Color(0xFFE4E9DF), modifier = Modifier.size(58.dp))
                Text(if (!permission) "Ready when you are" else "Camera unavailable", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 20.sp, modifier = Modifier.padding(top = 22.dp))
                Text(cameraError ?: "Allow camera access to scan.\nYour camera stays on your device.", color = Color(0xFFBEC8BB), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Button(onClick = { if (cameraError != null) cameraError = null else request.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 18.dp)) { Text(if (cameraError != null) "Try again" else "Enable camera") }
            }
            if ((permission || demoScene != null) && cameraError == null) Text("Keep the code inside the frame", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp))
          }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = pickImage, enabled = !importing, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (importing) "Reading…" else "From image") }
            OutlinedButton(onClick = { torch = !torch }, enabled = permission, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, "Flashlight"); Text(if (torch) " On" else " Off") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recently scanned", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("${model.records.count { !it.created }} saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val recent = model.records.filterNot { it.created }.take(2)
        if (recent.isEmpty()) Text("Your next discovery starts here. Scan a code to keep it close.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        recent.forEach { RecordRow(it) { model.result = it } }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ScanCorners(modifier: Modifier) {
    Canvas(modifier) {
        val line = 4.dp.toPx(); val length = size.width * .13f; val c = Color(0xFFE5E9DC)
        listOf(0f to 0f, size.width to 0f, 0f to size.height, size.width to size.height).forEach { (x, y) ->
            val dx = if (x == 0f) length else -length; val dy = if (y == 0f) length else -length
            drawLine(c, androidx.compose.ui.geometry.Offset(x, y + dy), androidx.compose.ui.geometry.Offset(x, y), line, StrokeCap.Round)
            drawLine(c, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx, y), line, StrokeCap.Round)
        }
    }
}

@Composable
private fun CreateScreen(model: ScannerModel, modifier: Modifier, capture: Boolean = false) {
    var type by rememberSaveable { mutableStateOf("Website") }
    var text by rememberSaveable { mutableStateOf(if (capture) "https://example.com/studio" else "") }
    var extra by rememberSaveable { mutableStateOf("") }
    var secured by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageHeading("Made to share", "Make it a QR.", "Small code. Endless ways to connect.")
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QrCode2, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 16.dp)) { Text("A shortcut to your world", fontWeight = FontWeight.SemiBold); Text("Create. Save. Share anywhere.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text("WHAT WOULD YOU LIKE TO SHARE?", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
        listOf(listOf("Website", "Text", "Wi-Fi"), listOf("Email", "Phone")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { name -> FilterChip(selected = type == name, onClick = { type = name; text = ""; extra = ""; error = null }, label = { Text(name) }, leadingIcon = if (type == name) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null) } }
        }
        OutlinedTextField(value = text, onValueChange = { text = it; error = null }, label = { Text(when(type) { "Wi-Fi" -> "Network name (SSID)"; "Email" -> "Email address"; "Phone" -> "Phone number"; "Text" -> "Your text"; else -> "Website address" }) }, placeholder = { Text(when(type) { "Website" -> "example.com"; "Wi-Fi" -> "Studio Guest"; "Email" -> "hello@example.com"; "Phone" -> "+1 555 0100"; else -> "Something worth sharing…" }) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), minLines = if (type == "Text") 4 else 1, singleLine = type != "Text", keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = when(type) { "Website" -> androidx.compose.ui.text.input.KeyboardType.Uri; "Email" -> androidx.compose.ui.text.input.KeyboardType.Email; "Phone" -> androidx.compose.ui.text.input.KeyboardType.Phone; else -> androidx.compose.ui.text.input.KeyboardType.Text }), shape = RoundedCornerShape(16.dp), isError = error != null)
        if (type == "Wi-Fi") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Password protected", Modifier.weight(1f)); Switch(secured, { secured = it }) }
            if (secured) OutlinedTextField(extra, { extra = it }, label = { Text("Network password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = {
            scope.launch {
                generating = true
                val payload = runCatching { CodeContent.create(type, text, extra, secured) }
                val content = payload.getOrNull()
                if (content != null) {
                    val valid = withContext(Dispatchers.Default) { runCatching { BarcodeEngine.qr(content, 512).recycle() }.isSuccess }
                    if (valid) model.accept(content, "QR_CODE", true) else error = "That is too much content for one QR. Try a shorter message."
                } else error = payload.exceptionOrNull()?.message
                generating = false
            }
        }, enabled = !generating && text.isNotBlank(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp)) { Icon(Icons.Outlined.QrCode2, null); Spacer(Modifier.width(10.dp)); Text(if (generating) "Creating…" else "Create QR code") }
        Row(Modifier.padding(top = 20.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp)); Text("  Generated on your device. Yours to keep.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HistoryScreen(model: ScannerModel, export: () -> Unit, modifier: Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    val records = model.records.filter { (!favorites || it.favorite) && (it.value.contains(search, true) || CodeContent.kind(it.value, it.format).label.contains(search, true)) }
    Column(modifier.padding(horizontal = 24.dp)) {
        PageHeading("Your collection", "Worth keeping.", "Every discovery, in one quiet place.") { IconButton(onClick = export, enabled = model.records.isNotEmpty()) { Icon(Icons.Outlined.FileDownload, "Export CSV") } }
        OutlinedTextField(search, { search = it }, placeholder = { Text("Search your codes") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
        Row(Modifier.padding(top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!favorites, { favorites = false }, label = { Text("All codes · ${model.records.size}") })
            FilterChip(favorites, { favorites = true }, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null, Modifier.size(16.dp)) })
        }
        if (records.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Bookmarks, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (search.isNotEmpty()) "No matching codes" else "A place for your discoveries", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
            Text(if (search.isNotEmpty()) "Try another word." else "Scan or create a code to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        LazyColumn { items(records, key = { it.id }) { record -> RecordRow(record) { model.result = record } } }
    }
}

private fun kindIcon(kind: ContentKind): ImageVector = when(kind) { ContentKind.LINK -> Icons.Outlined.Language; ContentKind.WIFI -> Icons.Outlined.Wifi; ContentKind.EMAIL -> Icons.Outlined.AlternateEmail; ContentKind.PHONE -> Icons.Outlined.Call; ContentKind.CONTACT -> Icons.Outlined.PersonOutline; ContentKind.PRODUCT -> Icons.Outlined.ViewWeek; else -> Icons.Outlined.Notes }

@Composable
private fun RecordRow(record: ScanRecord, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(kindIcon(CodeContent.kind(record.value, record.format)), null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(CodeContent.title(record.value, record.format), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("${CodeContent.kind(record.value, record.format).label}  ·  ${SimpleDateFormat("MMM d, HH:mm", Locale.ENGLISH).format(record.time)}${if(record.created) "  ·  Created" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(if (record.favorite) Icons.Outlined.Star else Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SettingsScreen(model: ScannerModel, modifier: Modifier) {
    val context = LocalContext.current
    var clear by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageHeading("Make yourself at home", "Just your style.", "A few thoughtful details. All yours.")
        Text("PREFERENCES", letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
        SettingToggle(Icons.Outlined.History, "Save history", "Keep up to 1,000 codes on this device", model.saveHistory) { model.setting("history", it) }
        SettingToggle(Icons.Outlined.Vibration, "Haptic feedback", "A little tap when a code is found", model.haptics) { model.setting("haptics", it) }
        SettingToggle(Icons.Outlined.DarkMode, "Dark appearance", "A softer view after hours", model.dark) { model.setting("dark", it) }
        Text("YOUR DATA", letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 30.dp, bottom = 12.dp))
        SettingLink(Icons.Outlined.DeleteOutline, "Clear all history", "Remove saved codes and favorites") { clear = true }
        SettingLink(Icons.Outlined.PrivacyTip, "Privacy policy", "Simple, transparent, on-device") { privacy = true }
        SettingLink(Icons.Outlined.MailOutline, "Get in touch", "We’re here to help") { launchExternal(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:seandyadryan2@gmail.com"))) }
        SettingLink(Icons.Outlined.Code, "Open source licenses", "Built with carefully chosen tools") { licenses = true }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(top = 28.dp, bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("Less friction. More discovery.", fontWeight = FontWeight.SemiBold); Text("QR Scanner Pro · ${BuildConfig.VERSION_NAME}\nNo ads. No account. No subscriptions.\nPT Deploy Dulu Pulang Nanti", style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear your collection?") }, text = { Text("All saved codes and favorites will be permanently deleted from this device.") }, confirmButton = { TextButton(onClick = { model.clear(); clear = false }) { Text("Delete all") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Keep history") } })
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Open source licenses") }, text = { Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) { Text(remember { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }, style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = { licenses = false }) { Text("Close") } })
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("Your privacy, by design") }, text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) { Text("QR Scanner Pro processes codes on your device. We do not operate a server for this app and do not collect or sell your scans, images, or personal data.\n\nCamera access is used only for scanning. Selected images are processed locally and are not uploaded. History is stored in app-private storage (up to 1,000 codes); disable saving or delete it at any time. Favorites are explicitly saved when you star a code. Android cloud backup is disabled.\n\nOpening links, sending email, exporting, or sharing sends the selected content to the app or destination you choose. Those services have their own privacy policies.\n\nNo advertising, analytics, accounts, or subscriptions.\n\nContact: seandyadryan2@gmail.com\nDeveloper: PT Deploy Dulu Pulang Nanti\nEffective: October 22, 2026.", style = MaterialTheme.typography.bodyMedium) } }, confirmButton = { TextButton(onClick = { privacy = false }) { Text("Got it") } })
}

@Composable
private fun SettingToggle(icon: ImageVector, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }; Switch(checked, onChange) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
@Composable
private fun SettingLink(icon: ImageVector, title: String, detail: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }; Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheet(record: ScanRecord, model: ScannerModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(record.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var qrFailed by remember(record.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val image = bitmap
        if (uri != null && image != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } ?: error("Cannot open file") }.isSuccess }
            notify(context, if (ok) "QR image saved." else "Could not save image.")
        }
    }
    LaunchedEffect(record.id) { if (record.format == "QR_CODE") { bitmap = withContext(Dispatchers.Default) { runCatching { BarcodeEngine.qr(record.value) }.getOrNull() }; qrFailed = bitmap == null } }
    ModalBottomSheet(onDismissRequest = { model.result = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(if(record.created) "READY TO SHARE" else "FOUND IT", color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelSmall); Text(CodeContent.kind(record.value, record.format).label, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
                IconButton(onClick = { model.favorite(record) }) { Icon(if(record.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, if(record.favorite) "Remove favorite" else "Save favorite", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { model.result = null }) { Icon(Icons.Outlined.Close, "Close result") }
            }
            bitmap?.let { Image(it.asImageBitmap(), "QR code for the content below", Modifier.padding(vertical = 18.dp).size(210.dp).clip(RoundedCornerShape(16.dp))) }
            if (qrFailed) Text("QR preview unavailable. The original content is available below.", style = MaterialTheme.typography.bodySmall)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer { Text(record.value, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(18.dp)) }
            }
            Text(record.format.replace('_', ' ') + if(record.created) " · Created on your device" else " · Check the content before opening", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
            val url = CodeContent.webUrl(record.value)
            if (url != null) Button(onClick = { launchExternal(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Text("  Open website") }
            if (CodeContent.kind(record.value) == ContentKind.PHONE) Button(onClick = { launchExternal(context, Intent(Intent.ACTION_DIAL, Uri.parse(record.value))) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open dialer") }
            if (CodeContent.kind(record.value) == ContentKind.EMAIL) Button(onClick = { launchExternal(context, Intent(Intent.ACTION_SENDTO, Uri.parse(record.value))) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Compose email") }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Scan result", record.value)); notify(context, "Copied to clipboard.") }, modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Text("  Copy") }
                OutlinedButton(onClick = { shareText(context, record.value) }, modifier = Modifier.weight(1f).height(50.dp)) { Icon(Icons.Outlined.Share, null, Modifier.size(16.dp)); Text("  Share") }
            }
            if (bitmap != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { save.launch("qr-code.png") }) { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)); Text("  Save PNG") }
                TextButton(enabled = !busy, onClick = {
                    val image = bitmap ?: return@TextButton
                    scope.launch {
                        busy = true
                        val uri = withContext(Dispatchers.IO) { runCatching {
                            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000 }?.forEach { it.delete() }
                            val file = File(dir, "qr-${System.currentTimeMillis()}.png")
                            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        }.getOrNull() }
                        busy = false
                        if (uri != null) launchExternal(context, Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("QR image", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share QR code")) else notify(context, "Could not share QR image.")
                    }
                }) { Text("Share QR image") }
            }
            if (model.records.any { it.id == record.id }) TextButton(onClick = { delete = true }) { Text("Delete from history", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this code?") }, text = { Text("This removes the saved code from your history.") }, confirmButton = { TextButton(onClick = { model.delete(record); delete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
}
