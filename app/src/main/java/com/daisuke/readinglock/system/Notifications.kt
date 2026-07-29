package com.daisuke.readinglock.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daisuke.readinglock.AppIntents
import com.daisuke.readinglock.R
import com.daisuke.readinglock.domain.TodayProgress

/** §7 常駐通知 + イベント通知 */
object Notifications {

    const val CHANNEL_RESIDENT = "resident"
    const val CHANNEL_EVENTS = "events"
    const val CHANNEL_WARNINGS = "warnings"
    const val ID_RESIDENT = 1
    private const val ID_EVENT = 2
    private const val ID_WARNING = 3

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESIDENT, "読書ロック常駐", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "読書ロック通知", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WARNINGS, "読書ロック警告", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun resident(context: Context, progress: TodayProgress): Notification {
        val quota = progress.effectiveQuotaMinutes
        val unlocked = progress.unlocked
        val title = if (unlocked) {
            "今日の読書 $quota/${quota}分 ✓ ─ ロック解除中"
        } else {
            "読書 ${progress.elapsedMinutes}/${quota}分 ─ ロック中"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RESIDENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(activity(context, AppIntents.home(context), 0))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setProgress(quota * 60, progress.totalSeconds.coerceAtMost(quota * 60), false)
            .setColor(if (unlocked) COLOR_ACHIEVED else COLOR_LOCKED)
            .setColorized(false)
        if (!unlocked) {
            builder.addAction(0, "読書を開始", activity(context, AppIntents.startFlow(context), 1))
        }
        return builder.build()
    }

    fun event(context: Context, title: String, text: String? = null) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(activity(context, AppIntents.home(context), 0))
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(ID_EVENT, notification)
        }
    }

    /** §9-3 / §9-4 の最優先警告。ホームバナーと対で出す */
    fun warning(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(activity(context, AppIntents.home(context), 0))
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(ID_WARNING, notification)
        }
    }

    private fun activity(context: Context, intent: android.content.Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val COLOR_LOCKED = 0xFF2E4057.toInt()
    private const val COLOR_ACHIEVED = 0xFF5A6B4A.toInt()
}
