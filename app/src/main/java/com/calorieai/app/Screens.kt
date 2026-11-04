package com.calorieai.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────── BUGÜN ─────────────────────────
@Composable
fun HomeScreen(vm: MainVM, onScan: () -> Unit) {
    val ctx = LocalContext.current
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val water by vm.water.collectAsState()
    val streak by vm.streak.collectAsState()
    val recents by vm.recents.collectAsState()
    val eaten = meals.sumOf { it.calories }
    val scored = meals.filter { it.healthScore > 0 }
    val dayScore = if (scored.isEmpty()) 0 else
        (scored.sumOf { it.healthScore.toLong() * it.calories.coerceAtLeast(1) }.toDouble() / scored.sumOf { it.calories.coerceAtLeast(1).toLong() }).roundToInt()
    val greeting = stringResource(when (LocalTime.now().hour) { in 5..11 -> R.string.greet_morning; in 12..17 -> R.string.greet_day; else -> R.string.greet_evening })

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        LocalDate.now().let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.FULL, LOC)}, ${it.dayOfWeek.getDisplayName(TextStyle.FULL, LOC)}" },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(greeting, style = MaterialTheme.typography.headlineMedium)
                }
                if (streak > 0) Pill(stringResource(R.string.streak_days, streak), Brand.fire)
            }
        }
        item { CalorieHero(eaten, goal) }
        item {
            AppCard {
                SectionTitle(stringResource(R.string.macros_title), if (dayScore > 0) stringResource(R.string.health_score_fmt, dayScore) else null)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroBar(stringResource(R.string.protein), Brand.protein, meals.sumOf { it.protein }, goal * 0.25 / 4)
                    MacroBar(stringResource(R.string.carbs), Brand.carbs, meals.sumOf { it.carbs }, goal * 0.45 / 4)
                    MacroBar(stringResource(R.string.fat), Brand.fat, meals.sumOf { it.fat }, goal * 0.30 / 9)
                }
            }
        }
        if (recents.isNotEmpty()) item {
            Column {
                SectionTitle(stringResource(R.string.quick_add), stringResource(R.string.recent_foods))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.id }) { m ->
                        Surface(
                            onClick = { vm.quickAdd(m); Toast.makeText(ctx, ctx.getString(R.string.added_fmt, m.name), Toast.LENGTH_SHORT).show() },
                            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 150.dp)) {
                                Text(m.name, maxLines = 1, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("+ ${m.calories} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        item { WaterCard(water, vm::addWater) }
        item { FastingCard(vm) }
        if (meals.isEmpty()) {
            item {
                AppCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍽️", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.empty_body), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        GradientButton(stringResource(R.string.scan_food), Icons.Default.CameraAlt, onClick = onScan)
                    }
                }
            }
        } else MEAL_TYPES.forEach { type ->
            val list = meals.filter { mealRes(it.mealType) == mealRes(type) }
            if (list.isNotEmpty()) {
                item(key = "h_$type") { SectionTitle(mealLabel(type), "${list.sumOf { it.calories }} kcal") }
                items(list, key = { it.id }) { m -> MealRow(m) { vm.delete(m) } }
            }
        }
    }
}

@Composable
fun WaterCard(ml: Int, onAdd: (Int) -> Unit) {
    val target = 2500
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💧", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.water), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.water_fmt, ml, target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onAdd(-250) }) { Icon(Icons.Default.Remove, "−250") }
            FilledTonalButton(onClick = { onAdd(250) }) { Text("+250") }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = { onAdd(500) }) { Text("+500") }
        }
        Spacer(Modifier.height(12.dp))
        ProgressBar(ml / target.toFloat(), Brand.water)
    }
}

@Composable
fun MealRow(m: Meal, onDelete: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.photoPath != null) AsyncImage(
                File(m.photoPath), null, Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop,
            ) else Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("🍴", style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.healthScore > 0) Text("●", color = scoreColor(m.healthScore), fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(
                    if (m.grams > 0) stringResource(R.string.meal_macros, m.grams, m.protein.roundToInt(), m.carbs.roundToInt(), m.fat.roundToInt())
                    else stringResource(R.string.meal_macros_short, m.protein.roundToInt(), m.carbs.roundToInt(), m.fat.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${m.calories}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, stringResource(R.string.delete_cd), tint = MaterialTheme.colorScheme.outline) }
        }
    }
}

/** Grafik canvas'ları RTL'de aynalanmaz; etiketlerle hizalı kalsın diye içeriği LTR'ye sabitler. */
@Composable
private fun Ltr(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)

// ───────────────────────── İLERLEME ─────────────────────────
@Composable
fun StatsScreen(vm: MainVM) {
    val week by vm.week.collectAsState()
    val goal by vm.goal.collectAsState()
    val weights by vm.weights.collectAsState()
    val goalWeight by vm.goalWeight.collectAsState()
    val goalStart by vm.goalStart.collectAsState()
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val totals = days.map { d -> week.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == d }.sumOf { it.calories } }
    val active = totals.filter { it > 0 }
    var input by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableIntStateOf(1) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var showCard by rememberSaveable { mutableStateOf(false) }
    val gw = goalWeight

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            AppCard {
                SectionTitle(stringResource(R.string.last_7_days), stringResource(R.string.target_kcal_fmt, goal))
                Spacer(Modifier.height(12.dp))
                Ltr { BarChart(totals, days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, LOC) }, goal) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showCard = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share_week_card))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(if (active.isEmpty()) "–" else "${active.average().roundToInt()}", stringResource(R.string.avg_daily_kcal), Modifier.weight(1f))
                StatTile("${totals.count { it in 1..goal }}", stringResource(R.string.days_on_target), Modifier.weight(1f))
                StatTile("${week.size}", stringResource(R.string.total_meals), Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                SectionTitle(stringResource(R.string.weight_tracking))
                val last = weights.lastOrNull()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(last?.let { "%.1f".format(LOC, it.kg) } ?: "–", style = MaterialTheme.typography.displayMedium)
                    Text(" kg", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (weights.size >= 2) {
                        val d = weights.last().kg - weights.first().kg
                        Pill("${if (d > 0) "+" else ""}${"%.1f".format(LOC, d)} kg", if (d <= 0) MaterialTheme.colorScheme.primary else Brand.carbs)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.range_7, R.string.range_30, R.string.range_90, R.string.range_all).forEachIndexed { i, l ->
                        FilterChip(range == i, { range = i }, label = { Text(stringResource(l)) }, colors = appChipColors())
                    }
                }
                val n = listOf(7, 30, 90, 0)[range]
                val shown = if (n == 0) weights else weights.filter { !LocalDate.parse(it.day).isBefore(LocalDate.now().minusDays(n - 1L)) }
                Spacer(Modifier.height(8.dp))
                if (shown.size >= 2) Ltr { WeightChart(shown) }
                else Text(stringResource(R.string.chart_need_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        input, { input = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '\u066B' }.take(5) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.weight_input_label)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Button(onClick = {
                        val kg = input.asciiNumber().toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
                        if (kg == null) Toast.makeText(ctx, ctx.getString(R.string.invalid_weight), Toast.LENGTH_SHORT).show()
                        else { vm.addWeight(kg); input = "" }
                    }, Modifier.height(56.dp)) { Text(stringResource(R.string.save)) }
                }
            }
        }
        // Hedef kilo + tahmini varış
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 " + stringResource(R.string.goal_weight), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showGoal = true }) { Text(stringResource(if (gw == null) R.string.set_goal_weight else R.string.edit)) }
                }
                if (gw == null) {
                    Text(stringResource(R.string.goal_weight_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val current = weights.lastOrNull()?.kg
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(LOC, gw), style = MaterialTheme.typography.headlineMedium)
                        Text(" kg", Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (current != null) Text(
                            stringResource(R.string.kg_to_go, "%.1f".format(LOC, abs(current - gw))),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val progress = if (current != null && abs(goalStart - gw) > 0.05) ((goalStart - current) / (goalStart - gw)).toFloat() else 0f
                    ProgressBar(progress, MaterialTheme.colorScheme.primary, height = 10.dp)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            forecastText(forecast(weights, gw)), Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (weights.isNotEmpty()) item {
            AppCard {
                SectionTitle(stringResource(R.string.daily_records), stringResource(R.string.last_n_days, minOf(7, weights.size)))
                Spacer(Modifier.height(4.dp))
                weights.takeLast(7).reversed().forEach { e ->
                    val i = weights.indexOf(e)
                    val diff = weights.getOrNull(i - 1)?.let { e.kg - it.kg }
                    val date = LocalDate.parse(e.day)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, LOC)} ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, LOC)}",
                            Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        diff?.let {
                            Text(
                                "${if (it > 0) "+" else ""}${"%.1f".format(LOC, it)}", style = MaterialTheme.typography.bodySmall,
                                color = if (it <= 0) MaterialTheme.colorScheme.primary else Brand.carbs,
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text("${"%.1f".format(LOC, e.kg)} kg", fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.deleteWeight(e.day) }) { Icon(Icons.Default.Close, stringResource(R.string.delete_cd), tint = MaterialTheme.colorScheme.outline) }
                    }
                }
            }
        }
        item { BadgesCard(vm) }
    }

    if (showGoal) GoalWeightDialog(
        initial = gw, onSave = { vm.setGoalWeight(it); showGoal = false },
        onClear = if (gw != null) ({ vm.clearGoalWeight(); showGoal = false }) else null, onDismiss = { showGoal = false },
    )
    if (showCard) {
        val bmp = remember { ShareCard.render(ctx, vm.weekSummary(), goal) }
        AlertDialog(
            onDismissRequest = { showCard = false },
            text = { Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(16.dp))) },
            confirmButton = { TextButton(onClick = { ShareCard.share(ctx, bmp) }) { Text(stringResource(R.string.share)) } },
            dismissButton = { TextButton(onClick = { showCard = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun forecastText(f: Forecast): String = when (f) {
    Forecast.NeedData -> stringResource(R.string.fc_need_data)
    Forecast.Reached -> stringResource(R.string.fc_reached)
    Forecast.WrongWay -> stringResource(R.string.fc_wrong)
    Forecast.Slow -> stringResource(R.string.fc_slow)
    is Forecast.Eta -> {
        val d = f.date
        val ds = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, LOC)} ${d.year}"
        stringResource(R.string.fc_eta, ds, "%.2f".format(LOC, abs(f.perWeek))) +
            if (abs(f.perWeek) > 1.0) "\n" + stringResource(R.string.fc_fast) else ""
    }
}

@Composable
private fun GoalWeightDialog(initial: Double?, onSave: (Double) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    var t by rememberSaveable { mutableStateOf(initial?.let { "%.1f".format(Locale.US, it) } ?: "") }
    val value = t.asciiNumber().toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_weight_dialog_title)) },
        text = {
            OutlinedTextField(
                t, { t = it.filter { c -> c.isDigit() || c == ',' || c == '.' || c == '\u066B' }.take(5) }, singleLine = true,
                label = { Text(stringResource(R.string.goal_weight_hint)) }, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.remove_goal)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun BarChart(values: List<Int>, labels: List<String>, goal: Int) {
    val max = maxOf(goal, values.maxOrNull() ?: 0).toFloat() * 1.15f
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * v / max
            val c = if (v > goal) over else bar
            drawRoundRect(
                Brush.verticalGradient(listOf(c, c.copy(alpha = 0.55f)), startY = size.height - h, endY = size.height),
                Offset(i * w + w * 0.18f, size.height - h), Size(w * 0.64f, h), CornerRadius(16f),
            )
        }
        val gy = size.height * (1 - goal / max)
        drawLine(line, Offset(0f, gy), Offset(size.width, gy), 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Günlere göre kilo grafiği: x ekseni gerçek tarih (boş günler aralık olarak görünür), y ekseni kg. */
@Composable
fun WeightChart(entries: List<WeightEntry>) {
    val col = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surf = MaterialTheme.colorScheme.surface
    val mut = MaterialTheme.colorScheme.onSurfaceVariant
    val days = entries.map { LocalDate.parse(it.day) }
    val d0 = days.first().toEpochDay()
    val span = (days.last().toEpochDay() - d0).coerceAtLeast(1).toFloat()
    val lo = entries.minOf { it.kg } - 0.5
    val hi = entries.maxOf { it.kg } + 0.5
    fun label(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, LOC)}"
    Row(Modifier.fillMaxWidth().height(130.dp)) {
        Column(Modifier.fillMaxHeight().width(38.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(hi, (hi + lo) / 2, lo).forEach { Text("%.1f".format(LOC, it), style = MaterialTheme.typography.labelSmall, color = mut) }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val pts = entries.mapIndexed { i, e ->
                Offset((days[i].toEpochDay() - d0) / span * size.width, (size.height * (1 - (e.kg - lo) / (hi - lo))).toFloat())
            }
            listOf(0f, 0.5f, 1f).forEach { drawLine(grid, Offset(0f, size.height * it), Offset(size.width, size.height * it), 2f) }
            val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val area = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
            drawPath(area, Brush.verticalGradient(listOf(col.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, col, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            pts.forEach { drawCircle(col, 8f, it); drawCircle(surf, 3.5f, it) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 38.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label(days.first()), style = MaterialTheme.typography.labelSmall, color = mut)
        Text(label(days.last()), style = MaterialTheme.typography.labelSmall, color = mut)
    }
}

// ───────────────────────── KOÇ ─────────────────────────
@Composable
fun CoachScreen(vm: MainVM, act: Activity) {
    val ctx = LocalContext.current
    val premium by vm.premium.collectAsState()
    val st = vm.coach
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.coach_title), style = MaterialTheme.typography.headlineMedium) }
        item { ChefCard(vm, act) }
        item {
            AppCard {
                Text(stringResource(R.string.weekly_analysis), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.weekly_analysis_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                GradientButton(stringResource(if (st is CoachState.Text) R.string.prepare_again else R.string.prepare_analysis), Icons.Default.AutoAwesome) { vm.askCoach() }
                if (!premium) Text(stringResource(R.string.coach_free_note), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (st) {
                    is CoachState.Loading -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.coach_loading))
                    }
                    is CoachState.Text -> Surface(Modifier.padding(top = 16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(st.text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    is CoachState.Locked -> Text(stringResource(R.string.coach_locked), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
                    is CoachState.Error -> Text(st.msg, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
                    else -> {}
                }
            }
        }
        item {
            AppCard {
                Text(stringResource(R.string.share_report), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.share_report_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    if (!premium) {
                        Toast.makeText(ctx, ctx.getString(R.string.premium_only_toast), Toast.LENGTH_SHORT).show()
                        (ctx.applicationContext as CalorieApp).billing.purchase(act)
                    } else {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.report_subject))
                            putExtra(Intent.EXTRA_TEXT, vm.csv())
                        }
                        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.share_report)))
                    }
                }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if (premium) R.string.share_csv else R.string.share_csv_premium))
                }
            }
        }
        item { PremiumCard(act) }
    }
}

// ───────────────────────── PROFİL ─────────────────────────
@Composable
fun ProfileScreen(vm: MainVM, act: Activity) {
    val p by vm.profile.collectAsState()
    val goal by vm.goal.collectAsState()
    val remind by vm.remind.collectAsState()
    val mode by vm.themeMode.collectAsState()
    val ctx = LocalContext.current
    var draft by remember(p) { mutableStateOf(p) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.setReminders(ok) }
    val langSel = LocaleHelper.LANGS.indexOfFirst { it.first == LocaleHelper.saved(ctx) } + 1   // 0 = sistem

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            AppCard {
                SectionTitle(stringResource(R.string.body_goal_title))
                Spacer(Modifier.height(12.dp))
                ProfileFields(draft) { draft = it }
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.suggested_goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${draft.targetCalories()} kcal", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(stringResource(R.string.current_goal_fmt, goal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GradientButton(stringResource(R.string.calc_save)) {
                    vm.saveProfile(draft); Toast.makeText(ctx, ctx.getString(R.string.goal_updated), Toast.LENGTH_SHORT).show()
                }
            }
        }
        item {
            AppCard {
                SectionTitle(stringResource(R.string.prefs_title))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔 " + stringResource(R.string.reminders), Modifier.weight(1f))
                    Switch(remind, { on ->
                        if (!on) vm.setReminders(false)
                        else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.setReminders(true)
                    })
                }
                Spacer(Modifier.height(12.dp))
                ChipRow(stringResource(R.string.theme), listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)), mode) { vm.setTheme(it) }
                Spacer(Modifier.height(12.dp))
                ChipRow(stringResource(R.string.language), listOf(stringResource(R.string.theme_system)) + LocaleHelper.LANGS.map { it.second }, langSel) {
                    LocaleHelper.set(act, if (it == 0) "" else LocaleHelper.LANGS[it - 1].first)
                }
            }
        }
        item { PremiumCard(act) }
        item { Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}


// ───────────────────────── ARALIKLI ORUÇ ─────────────────────────
@Composable
fun FastingCard(vm: MainVM) {
    val start by vm.fastStart.collectAsState()
    val hours by vm.fastHours.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (start > 0) { now = System.currentTimeMillis(); delay(30_000) } }
    val running = start > 0
    val elapsedMin = if (running) ((now - start) / 60_000).coerceAtLeast(0) else 0L
    val goalMin = hours * 60L
    val left = (goalMin - elapsedMin).coerceAtLeast(0)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱️", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fast_title), style = MaterialTheme.typography.titleMedium)
                if (running) Text(
                    stringResource(R.string.fast_time_fmt, elapsedMin / 60, elapsedMin % 60),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running) Pill(
                if (left == 0L) stringResource(R.string.fast_done) else stringResource(R.string.fast_left_fmt, "${left / 60}:${"%02d".format(left % 60)}"),
                MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (!running) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 16, 18).forEach { h ->
                    FilterChip(hours == h, { vm.setFastHours(h) }, label = { Text(stringResource(R.string.fast_hours_fmt, h)) }, colors = appChipColors())
                }
            }
            Text(stringResource(R.string.fast_hint), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            GradientButton(stringResource(R.string.fast_start)) { vm.startFast() }
        } else {
            ProgressBar(elapsedMin / goalMin.toFloat(), MaterialTheme.colorScheme.primary, height = 10.dp)
            Text(
                stringResource(when { elapsedMin < 8 * 60 -> R.string.fast_stage1; elapsedMin < 16 * 60 -> R.string.fast_stage2; else -> R.string.fast_stage3 }),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.endFast() }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.fast_end)) }
        }
    }
}

// ───────────────────────── BAŞARIMLAR ─────────────────────────
@Composable
private fun BadgesCard(vm: MainVM) {
    val meals by vm.mealCount.collectAsState()
    val best by vm.bestStreak.collectAsState()
    val weights by vm.weights.collectAsState()
    val fasts by vm.fastsDone.collectAsState()
    val gw by vm.goalWeight.collectAsState()
    val target = gw
    val reached = target != null && forecast(weights, target) == Forecast.Reached
    val badges = listOf(
        R.string.badge_first to (meals >= 1), R.string.badge_meals10 to (meals >= 10), R.string.badge_meals100 to (meals >= 100),
        R.string.badge_streak3 to (best >= 3), R.string.badge_streak7 to (best >= 7), R.string.badge_streak30 to (best >= 30),
        R.string.badge_weight7 to (weights.size >= 7), R.string.badge_goal to reached, R.string.badge_fast to (fasts >= 1),
    )
    AppCard {
        SectionTitle(stringResource(R.string.badges_title), stringResource(R.string.badges_count_fmt, badges.count { it.second }, badges.size))
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            badges.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, ok) ->
                        Surface(
                            Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                stringResource(label), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (ok) FontWeight.Bold else FontWeight.Normal,
                                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ───────────────────────── AI ŞEF ─────────────────────────
@Composable
fun ChefCard(vm: MainVM, act: Activity) {
    val ctx = LocalContext.current
    val left by vm.chefLeft.collectAsState()
    val premium by vm.premium.collectAsState()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("fridge") }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending?.let { vm.askChef(mode, it) }
    }
    fun shoot(m: String) {
        mode = m
        val f = newPhotoFile(ctx); pending = f.path
        cam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
    }
    AppCard {
        Text("👨‍🍳 " + stringResource(R.string.chef_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.chef_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        GradientButton(stringResource(R.string.chef_next), Icons.Default.Restaurant) { vm.askChef("next") }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(Icons.Default.Kitchen, stringResource(R.string.chef_fridge), Modifier.weight(1f)) { shoot("fridge") }
            ActionTile(Icons.Default.MenuBook, stringResource(R.string.chef_menu), Modifier.weight(1f)) { shoot("menu") }
        }
        if (!premium) Text(
            stringResource(R.string.chef_free_note, left.coerceAtLeast(0)), Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val st = vm.chef) {
            is ChefState.Loading -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.chef_loading))
            }
            is ChefState.Locked -> Text(stringResource(R.string.chef_limit), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
            is ChefState.None -> Text(stringResource(R.string.chef_none), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            is ChefState.Error -> Text(st.msg, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error)
            is ChefState.Done -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                st.list.forEach { s ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Pill("${s.calories} kcal", MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                stringResource(R.string.meal_macros_short, s.protein.roundToInt(), s.carbs.roundToInt(), s.fat.roundToInt()),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (s.reason.isNotBlank()) Text(s.reason, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                            if (s.prep.isNotBlank()) Text(s.prep, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                vm.addSuggestion(s); Toast.makeText(ctx, ctx.getString(R.string.chef_added), Toast.LENGTH_SHORT).show()
                            }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.chef_add)) }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
