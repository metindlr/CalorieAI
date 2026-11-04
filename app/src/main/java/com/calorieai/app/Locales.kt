package com.calorieai.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Uygulama içi dil seçimi: "" = sistem, "tr", "en". Seçim SharedPreferences'ta, MainActivity.attachBaseContext'te uygulanır. */
object LocaleHelper {
    /** Desteklenen diller: kod → kendi dilinde ad. Yeni dil eklemek için buraya + values-xx + locales_config. */
    val LANGS = listOf(
        "tr" to "Türkçe", "en" to "English", "es" to "Español", "pt" to "Português",
        "de" to "Deutsch", "fr" to "Français", "ar" to "العربية",
    )

    private const val FILE = "app"
    private const val KEY = "lang"

    fun saved(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun wrap(base: Context): Context {
        val l = saved(base)
        if (l.isEmpty()) {
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
            return base
        }
        val loc = Locale(l)
        Locale.setDefault(loc)
        val cfg = Configuration(base.resources.configuration).apply { setLocale(loc) }
        return base.createConfigurationContext(cfg)
    }

    /** Gemini ve metinler için etkin dil kodu (desteklenmiyorsa "en"). */
    fun effective(ctx: Context): String {
        val l = saved(ctx).ifEmpty { Resources.getSystem().configuration.locales[0].language }
        return if (LANGS.any { it.first == l }) l else "en"
    }

    fun set(act: Activity, lang: String) {
        act.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
        act.recreate()
    }
}
