package com.daisuke.readinglock.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.domain.LogicalDayResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 毎日 4:00 の日次リセット。§3 / §9-1 */
object ResetScheduler {

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = LogicalDayResolver.nextResetTime(System.currentTimeMillis())
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ResetReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                // 権限が無ければ不正確でも構わない(tick 側でも論理日変化を検知して自己修復する)
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }
}

class ResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                AppGraph.engine.runDailyReset()
                AppGraph.db.blockedDomains().purgeDue(System.currentTimeMillis())
                ResetScheduler.schedule(app)
                MonitorService.start(app)
            } finally {
                result.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        ResetScheduler.schedule(app)
        MonitorService.start(app)
    }
}
