package com.calorieai.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

val LOC: Locale get() = Locale.getDefault()

/** Çevrilebilir hata: kaynak id + argümanlar. */
class UiError(@StringRes val res: Int, vararg val args: Any) : Exception()

fun Throwable.uiMessage(ctx: Context): String =
    if (this is UiError) ctx.getString(res, *args) else ctx.getString(R.string.err_generic)

fun newPhotoFile(ctx: Context): File {
    val dir = File(ctx.filesDir, "meals").apply { mkdirs() }
    return File(dir, "${System.currentTimeMillis()}.jpg")
}

fun loadBitmap(path: String, max: Int = 1024): Bitmap {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var s = 1
    while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        ?: throw UiError(R.string.err_photo_read)
    val rot = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
    }
    val scale = minOf(1f, max / maxOf(bmp.width, bmp.height).toFloat())
    val m = Matrix().apply { postRotate(rot); postScale(scale, scale) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

// Öğün türleri DB'de dilden bağımsız anahtar olarak saklanır (eski Türkçe kayıtlar da eşlenir).
val MEAL_TYPES = listOf("breakfast", "lunch", "dinner", "snack")

fun guessMealType(): String = when (LocalTime.now().hour) {
    in 5..10 -> "breakfast"; in 11..15 -> "lunch"; in 17..22 -> "dinner"; else -> "snack"
}

@StringRes
fun mealRes(k: String): Int = when (k) {
    "breakfast", "Kahvaltı" -> R.string.meal_breakfast
    "lunch", "Öğle" -> R.string.meal_lunch
    "dinner", "Akşam" -> R.string.meal_dinner
    else -> R.string.meal_snack
}

@Composable
fun mealLabel(k: String): String = stringResource(mealRes(k))

// ───────── Hedef kiloya tahmini varış ─────────
sealed interface Forecast {
    data object NeedData : Forecast
    data object Reached : Forecast
    data object WrongWay : Forecast
    data object Slow : Forecast
    data class Eta(val date: LocalDate, val perWeek: Double) : Forecast
}

/** Son 28 günün kayıtlarına doğrusal regresyon uygular, eğim (kg/gün) ile hedefe kalan süreyi tahmin eder. */
fun forecast(entries: List<WeightEntry>, goalKg: Double, today: LocalDate = LocalDate.now()): Forecast {
    if (entries.isEmpty()) return Forecast.NeedData
    val current = entries.last().kg
    if (abs(current - goalKg) <= 0.3) return Forecast.Reached
    val recent = entries.filter { !LocalDate.parse(it.day).isBefore(today.minusDays(27)) }
    if (recent.size < 3) return Forecast.NeedData
    val xs = recent.map { LocalDate.parse(it.day).toEpochDay().toDouble() }
    val ys = recent.map { it.kg }
    if (xs.max() - xs.min() < 7) return Forecast.NeedData
    val mx = xs.average(); val my = ys.average()
    val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
    val need = goalKg - current
    if (abs(slope) < 0.005 || slope * need <= 0) return Forecast.WrongWay
    val days = need / slope
    if (days > 730) return Forecast.Slow
    return Forecast.Eta(today.plusDays(days.toLong()), slope * 7)
}

/** Arapça-Hint/diğer Unicode rakamları ve ondalık ayırıcıları (',' '٫') ASCII sayıya çevirir. Ayrıştırma (toDoubleOrNull) için. */
fun String.asciiNumber(): String = buildString {
    for (c in this@asciiNumber) when {
        Character.isDigit(c) -> append(Character.digit(c, 10))
        c == ',' || c == '\u066B' || c == '.' -> append('.')
    }
}
