package com.calorieai.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Ana ekran widget'ı: bugün kalan kalori. Dokununca uygulama açılır. */
class CalorieWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CalorieApp
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val eaten = app.db.meals().between(start, start + DAY - 1).first().sumOf { it.calories }
        val goal = app.prefs.int("goal", 2000).first()
        val white = ColorProvider(Color.White)
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF0B7A5A))).padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${kotlin.math.abs(goal - eaten)}", style = TextStyle(color = white, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                Text(LocaleHelper.wrap(context).getString(if (eaten <= goal) R.string.widget_left else R.string.widget_over), style = TextStyle(color = white, fontSize = 13.sp))
                Text("$eaten / $goal", style = TextStyle(color = white, fontSize = 11.sp))
            }
        }
    }
}

class CalorieWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalorieWidget()
}
