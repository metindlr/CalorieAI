package com.calorieai.app

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


object Brand {
    val protein = Color(0xFF3B82F6)
    val carbs = Color(0xFFF59E0B)
    val fat = Color(0xFFF472B6)
    val water = Color(0xFF38BDF8)
    val fire = Color(0xFFFF7043)
    val gold = Color(0xFFFFC857)
}

/** Sağlık skoru rengi: ≥70 yeşil, 40–69 amber, <40 kırmızı. */
fun scoreColor(score: Int): Color = when {
    score >= 70 -> Color(0xFF22C55E)
    score >= 40 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF0B7A5A), Color(0xFF0F5E73)))

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun ProgressBar(p: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val anim by animateFloatAsState(p.coerceIn(0f, 1f), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(color.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(left: String, right: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        right?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun GradientButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(HeroBrush).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(10.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CalorieHero(eaten: Int, goal: Int) {
    val over = eaten > goal
    val p by animateFloatAsState((eaten / goal.toFloat()).coerceIn(0f, 1f), label = "hero")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroBrush).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 14.dp.toPx()
                    val sz = Size(size.width - w, size.height - w)
                    val tl = Offset(w / 2, w / 2)
                    drawArc(Color.White.copy(alpha = 0.22f), -90f, 360f, false, tl, sz, style = Stroke(w))
                    drawArc(if (over) Color(0xFFFFB4AB) else Color.White, -90f, 360f * p, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${(p * 100).roundToInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(stringResource(if (over) R.string.hero_over else R.string.hero_remaining), color = Color.White.copy(alpha = 0.8f))
                Text("${abs(goal - eaten)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp)
                Text("kcal", color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.hero_eaten_goal, eaten, goal), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun MacroBar(label: String, color: Color, value: Double, target: Double) {
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("${value.roundToInt()} / ${target.roundToInt()} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar((value / target).toFloat(), color)
    }
}

@Composable
fun appChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun ChipRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, o -> FilterChip(selected == i, { onSelect(i) }, label = { Text(o) }, colors = appChipColors()) }
        }
    }
}

/** Yaş / boy / kilo / cinsiyet / aktivite / hedef formu (Onboarding ve Profil ortak kullanır). */
@Composable
fun ProfileFields(p: Profile, onChange: (Profile) -> Unit) {
    var age by remember { mutableStateOf(p.age.toString()) }
    var h by remember { mutableStateOf(p.height.toString()) }
    var w by remember { mutableStateOf(p.weight.toString()) }
    fun upd(a: String = age, hh: String = h, ww: String = w) {
        age = a; h = hh; w = ww
        onChange(p.copy(age = a.asciiNumber().toIntOrNull() ?: p.age, height = hh.asciiNumber().toIntOrNull() ?: p.height, weight = ww.asciiNumber().toIntOrNull() ?: p.weight))
    }
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(age, { upd(a = it.filter(Char::isDigit).take(3)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.age)) }, keyboardOptions = num, singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(h, { upd(hh = it.filter(Char::isDigit).take(3)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.height_cm)) }, keyboardOptions = num, singleLine = true, shape = RoundedCornerShape(16.dp))
            OutlinedTextField(w, { upd(ww = it.filter(Char::isDigit).take(3)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.weight_kg)) }, keyboardOptions = num, singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        ChipRow(stringResource(R.string.gender), listOf(stringResource(R.string.female), stringResource(R.string.male)), if (p.male) 1 else 0) { onChange(p.copy(male = it == 1)) }
        ChipRow(stringResource(R.string.activity), listOf(stringResource(R.string.act_sedentary), stringResource(R.string.act_light), stringResource(R.string.act_moderate), stringResource(R.string.act_high)), p.activity) { onChange(p.copy(activity = it)) }
        ChipRow(stringResource(R.string.goal), listOf(stringResource(R.string.goal_lose), stringResource(R.string.goal_maintain), stringResource(R.string.goal_gain)), p.goalType) { onChange(p.copy(goalType = it)) }
    }
}

/** Koyu, altın vurgulu premium kartı. */
@Composable
fun PremiumCard(act: Activity) {
    val app = act.applicationContext as CalorieApp
    val premium by app.billing.premium.collectAsState()
    val price by app.billing.price.collectAsState()
    val gold = Brand.gold
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B2A25), Color(0xFF0E1714)))).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (premium) {
                Text(stringResource(R.string.prem_active_title), color = gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.prem_active_body), color = Color.White.copy(alpha = 0.85f))
            } else {
                Text(stringResource(R.string.prem_title), color = gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.prem_features), color = Color.White.copy(alpha = 0.85f))
                Button(
                    onClick = { app.billing.purchase(act) }, Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF2B1B00)),
                ) { Text(price?.let { stringResource(R.string.prem_go_price, it) } ?: stringResource(R.string.prem_go), fontWeight = FontWeight.Bold) }
                TextButton(onClick = { app.billing.restore() }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.prem_restore), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}
