package com.calorieai.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File

data class WeekSummary(
    val totals: List<Int>, val labels: List<String>, val avg: Int,
    val onTarget: Int, val streak: Int, val weightDelta: Double?, val rangeLabel: String,
)

/** Paylaşılabilir 1080×1350 haftalık özet kartı (Instagram/WhatsApp story-feed uyumlu). */
object ShareCard {
    fun render(ctx: Context, s: WeekSummary, goal: Int): Bitmap {
        val c0 = LocaleHelper.wrap(ctx)
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF0B7A5A.toInt(), 0xFF0F5E73.toInt(), Shader.TileMode.CLAMP)
        })
        fun p(size: Float, bold: Boolean = false, alpha: Int = 255, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; this.alpha = alpha; textSize = size; textAlign = align
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }

        cv.drawText(c0.getString(R.string.sc_title), 80f, 150f, p(68f, true))
        cv.drawText(s.rangeLabel, 80f, 212f, p(38f, alpha = 200))
        cv.drawText("${s.avg}", 80f, 470f, p(230f, true))
        cv.drawText(c0.getString(R.string.sc_avg), 80f, 536f, p(44f, alpha = 215))

        val max = maxOf(goal, s.totals.maxOrNull() ?: 0).toFloat() * 1.1f
        val left = 80f; val right = w - 80f; val top = 620f; val bottom = 980f
        val slot = (right - left) / 7f
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 240 }
        val dim = Paint(bar).apply { alpha = 70 }
        s.totals.forEachIndexed { i, v ->
            val bh = maxOf((bottom - top) * v / max, 10f)
            val x = left + i * slot + slot * 0.15f
            cv.drawRoundRect(RectF(x, bottom - bh, x + slot * 0.7f, bottom), 22f, 22f, if (v > 0) bar else dim)
        }
        val gy = bottom - (bottom - top) * goal / max
        cv.drawLine(left, gy, right, gy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 170; strokeWidth = 4f; style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
        })
        val lp = p(34f, alpha = 210, align = Paint.Align.CENTER)
        s.labels.forEachIndexed { i, l -> cv.drawText(l, left + i * slot + slot / 2, bottom + 52f, lp) }

        val gap = 24f
        val bw = (right - left - 2 * gap) / 3
        val stats = listOf(
            "${s.onTarget}/7" to c0.getString(R.string.sc_on_target),
            "🔥 ${s.streak}" to c0.getString(R.string.sc_streak),
            (s.weightDelta?.let { "%+.1f kg".format(LOC, it) } ?: "–") to c0.getString(R.string.sc_weight),
        )
        stats.forEachIndexed { i, (value, label) ->
            val x = left + i * (bw + gap)
            cv.drawRoundRect(RectF(x, 1075f, x + bw, 1235f), 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 38 })
            cv.drawText(value, x + bw / 2, 1150f, p(64f, true, align = Paint.Align.CENTER))
            cv.drawText(label, x + bw / 2, 1204f, p(30f, alpha = 215, align = Paint.Align.CENTER))
        }
        cv.drawText("CalorieAI", w / 2f, 1306f, p(44f, true, 225, Paint.Align.CENTER))
        return bmp
    }

    fun share(ctx: Context, bmp: Bitmap) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "calorieai_week.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, LocaleHelper.wrap(ctx).getString(R.string.share_week_card)))
    }
}
