package com.calorieai.app

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** İlk açılış: dil seçimi + karşılama + kişisel hedef kurulumu. */
@Composable
fun OnboardingScreen(vm: MainVM) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    if (step == 0) {
        Column(Modifier.fillMaxSize().background(HeroBrush).systemBarsPadding().padding(28.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                LocaleHelper.LANGS.forEach { (code, name) ->
                    TextButton(onClick = { (ctx as? Activity)?.let { LocaleHelper.set(it, code) } }) { Text(name, color = Color.White) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("🥗", fontSize = 72.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.app_name), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.onb_tagline), color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
                Spacer(Modifier.height(28.dp))
                listOf(R.string.onb_f1, R.string.onb_f2, R.string.onb_f3, R.string.onb_f4)
                    .forEach { Text(stringResource(it), color = Color.White, modifier = Modifier.padding(vertical = 6.dp), fontSize = 16.sp) }
                Spacer(Modifier.height(36.dp))
                Button(
                    onClick = { step = 1 }, Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B7A5A)),
                ) { Text(stringResource(R.string.onb_start), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    } else {
        var draft by remember { mutableStateOf(Profile(30, 170, 70, true, 1, 1)) }
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.onb_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppCard { ProfileFields(draft) { draft = it } }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.onb_goal_title), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${draft.targetCalories()} kcal", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            GradientButton(stringResource(R.string.onb_ready)) { vm.completeOnboarding(draft) }
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
