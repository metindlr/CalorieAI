package com.calorieai.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    private val slots = listOf("breakfast" to (8 * 60 + 30), "lunch" to (13 * 60), "water" to (16 * 60), "dinner" to (19 * 60 + 30))

    fun schedule(ctx: Context) {
        slots.forEach { (tag, mins) ->
            val now = LocalDateTime.now()
            var t = now.toLocalDate().atTime(mins / 60, mins % 60)
            if (!t.isAfter(now)) t = t.plusDays(1)
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, t).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("tag" to tag)).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("rem_$tag", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }

    fun cancel(ctx: Context) = slots.forEach { WorkManager.getInstance(ctx).cancelUniqueWork("rem_${it.first}") }
}

class ReminderWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result {
        val c = LocaleHelper.wrap(applicationContext)     // bildirim metni seçili dilde
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val tag = inputData.getString("tag") ?: "water"
        val text = c.getString(when (tag) {
            "breakfast" -> R.string.rem_breakfast
            "lunch" -> R.string.rem_lunch
            "dinner" -> R.string.rem_dinner
            else -> R.string.rem_water
        })
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", c.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, "reminders")
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(c.getString(R.string.app_name))
            .setContentText(text).setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(tag.hashCode(), n)
        return Result.success()
    }
}
