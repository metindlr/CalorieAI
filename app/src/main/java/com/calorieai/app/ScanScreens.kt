package com.calorieai.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ScanScreen(vm: MainVM, act: Activity) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CalorieApp
    val left by vm.scansLeft.collectAsState()
    val premium by vm.premium.collectAsState()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var showText by rememberSaveable { mutableStateOf(false) }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { vm.analyze(it) }
    }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::analyzeUri) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(vm::analyzeText)
    }
    val startVoice = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.voice_prompt))
        try { voice.launch(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, ctx.getString(R.string.voice_unavailable), Toast.LENGTH_SHORT).show()
        }
    }
    val scanBarcode = {
        GmsBarcodeScanning.getClient(ctx).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(vm::lookupBarcode) }
            .addOnFailureListener { Toast.makeText(ctx, ctx.getString(R.string.barcode_failed), Toast.LENGTH_SHORT).show() }
        Unit
    }

    when (val s = vm.scan) {
        is ScanState.Idle, is ScanState.Failed -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.scan_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(
                if (premium) stringResource(R.string.premium_unlimited) else stringResource(R.string.credits_left_fmt, left.coerceAtLeast(0)),
                MaterialTheme.colorScheme.primary,
            )
            if (s is ScanState.Failed) AppCard { Text(s.msg, color = MaterialTheme.colorScheme.error) }
            if (left > 0) {
                GradientButton(stringResource(R.string.take_photo), Icons.Default.CameraAlt) {
                    val f = newPhotoFile(ctx); pending = f.path
                    cam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Default.PhotoLibrary, stringResource(R.string.gallery), Modifier.weight(1f)) {
                        gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ActionTile(Icons.Default.QrCodeScanner, stringResource(R.string.barcode), Modifier.weight(1f)) { scanBarcode() }
                    ActionTile(Icons.Default.Edit, stringResource(R.string.type_it), Modifier.weight(1f)) { showText = true }
                    ActionTile(Icons.Default.Mic, stringResource(R.string.voice), Modifier.weight(1f)) { startVoice() }
                }
            } else {
                AppCard {
                    Text(stringResource(R.string.limit_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.limit_body, REWARD_SCANS), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val shown = app.ads.showRewarded(act) { vm.addBonusScans() }
                            if (!shown) Toast.makeText(ctx, ctx.getString(R.string.ad_not_ready), Toast.LENGTH_SHORT).show()
                        },
                        Modifier.fillMaxWidth().height(52.dp),
                    ) { Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.watch_ad, REWARD_SCANS)) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { app.billing.purchase(act) }, Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.gold, contentColor = Color(0xFF2B1B00)),
                    ) { Text(stringResource(R.string.go_premium), fontWeight = FontWeight.Bold) }
                }
                ActionTile(Icons.Default.QrCodeScanner, stringResource(R.string.barcode_free), Modifier.fillMaxWidth()) { scanBarcode() }
            }
        }

        is ScanState.Loading -> Column(
            Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (s.path != null) AsyncImage(File(s.path), null, Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
            else Text("🔎", fontSize = 64.sp)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(s.label), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is ScanState.Done -> ResultView(
            s = s,
            correcting = vm.correcting, correctMsg = vm.correctMsg, correctionsLeft = MAX_CORRECTIONS - vm.corrections,
            onCorrect = vm::correct, onRemove = vm::removeItem,
            onSave = { mult, type ->
                vm.save(s, mult, type)
                if (!premium && ++vm.saves % 3 == 0) app.ads.showInterstitial(act)
            },
            onDiscard = { vm.reset(deleteFile = true) },
        )
    }

    if (showText) TextAddDialog(onDismiss = { showText = false }, onSubmit = { showText = false; vm.analyzeText(it) })
}

@Composable
private fun TextAddDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_dialog_title)) },
        text = {
            OutlinedTextField(
                text, { text = it.take(200) }, Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(16.dp),
                placeholder = { Text(stringResource(R.string.text_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text.trim()) }, enabled = text.trim().length >= 3) { Text(stringResource(R.string.analyze)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ResultView(
    s: ScanState.Done,
    correcting: Boolean, correctMsg: String?, correctionsLeft: Int,
    onCorrect: (String) -> Unit, onRemove: (Int) -> Unit,
    onSave: (Float, String) -> Unit, onDiscard: () -> Unit,
) {
    val a = s.a
    var mult by remember { mutableFloatStateOf(1f) }
    var type by remember { mutableStateOf(guessMealType()) }
    var txt by rememberSaveable { mutableStateOf("") }
    val title = a.dishName.ifBlank { a.items.first().name.ifBlank { stringResource(R.string.packaged_product) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (s.path != null) AsyncImage(File(s.path), null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroBrush).padding(20.dp)) {
            Column {
                Text(
                    (if (s.path == null) (if (s.source == "off") "📦 " else "✍️ ") else "") + title,
                    color = Color.White, style = MaterialTheme.typography.titleLarge,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(a.totalCal * mult).roundToInt()}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text(" kcal", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    if (s.source == "off") stringResource(R.string.source_off) else stringResource(R.string.confidence_fmt, (a.confidence * 100).roundToInt()),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (a.healthScore > 0) AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(scoreColor(a.healthScore).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${a.healthScore}", color = scoreColor(a.healthScore), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.health_score_fmt, a.healthScore), style = MaterialTheme.typography.titleMedium)
                    if (a.scoreReason.isNotBlank()) Text(a.scoreReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroStat(stringResource(R.string.protein), a.totalProtein * mult, Brand.protein, Modifier.weight(1f))
            MacroStat(stringResource(R.string.carbs_short), a.totalCarbs * mult, Brand.carbs, Modifier.weight(1f))
            MacroStat(stringResource(R.string.fat), a.totalFat * mult, Brand.fat, Modifier.weight(1f))
        }

        AppCard {
            SectionTitle(stringResource(R.string.portion), "×${"%.1f".format(LOC, mult)}")
            Slider(mult, { mult = it }, valueRange = 0.5f..2f, steps = 14)
            a.items.forEachIndexed { idx, it ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.name.ifBlank { title }, fontWeight = FontWeight.Medium)
                        Text("${(it.grams * mult).roundToInt()} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${(it.calories * mult).roundToInt()} kcal", fontWeight = FontWeight.SemiBold)
                    if (a.items.size > 1) IconButton(onClick = { onRemove(idx) }) {
                        Icon(Icons.Default.Close, stringResource(R.string.remove_item_cd), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        if (s.source == "off") Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(stringResource(R.string.off_note, a.items.first().grams), Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else AppCard {
            Text("✍️ " + stringResource(R.string.correct_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.correct_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    txt, { txt = it.take(160) }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp), maxLines = 2,
                    placeholder = { Text(stringResource(R.string.correct_example)) }, enabled = !correcting && correctionsLeft > 0,
                )
                Button(
                    onClick = { onCorrect(txt.trim()); txt = "" }, Modifier.height(56.dp),
                    enabled = txt.trim().length >= 3 && !correcting && correctionsLeft > 0,
                ) {
                    if (correcting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    else Text(stringResource(R.string.apply))
                }
            }
            Text(
                stringResource(R.string.corrections_left, correctionsLeft.coerceAtLeast(0)),
                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            correctMsg?.let { Text(it, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        ChipRow(stringResource(R.string.meal_type_label), MEAL_TYPES.map { mealLabel(it) }, MEAL_TYPES.indexOf(type)) { type = MEAL_TYPES[it] }

        if (a.healthTip.isNotBlank()) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("💡 ${a.healthTip}", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientButton(stringResource(R.string.save_log), Icons.Default.Check) { onSave(mult, type) }
        TextButton(onClick = onDiscard, Modifier.fillMaxWidth()) { Text(stringResource(R.string.discard)) }
    }
}

@Composable
private fun MacroStat(label: String, grams: Double, color: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${grams.roundToInt()} g", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
