package com.daisuke.readinglock.system

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledApp(val packageName: String, val appName: String)

/** ブロック設定の「直近7日間の使用時間」。保存はせず表示のたびに取り直す(§6) */
object AppUsage {

    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun weekly(context: Context): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        return runCatching {
            manager.queryAndAggregateUsageStats(now - WEEK_MS, now)
                .mapValues { it.value.totalTimeInForeground }
                .filterValues { it > 0 }
        }.getOrDefault(emptyMap())
    }

    /** ランチャーから起動できるアプリ(自分自身は除く) */
    fun launchable(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            // MATCH_DEFAULT_ONLY だと CATEGORY_DEFAULT を持たないランチャーアプリが漏れる(実機で発覚)
            pm.queryIntentActivities(intent, 0)
                .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.appName }
        }.getOrDefault(emptyList())
    }
}
