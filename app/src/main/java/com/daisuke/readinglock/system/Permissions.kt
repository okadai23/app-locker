package com.daisuke.readinglock.system

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** §9-3 権限の事後剥奪チェック。fail-closed にはせず、警告とホームバナーで再誘導する */
object Permissions {

    const val USAGE = "usage"
    const val OVERLAY = "overlay"
    const val NOTIFICATIONS = "notifications"
    const val VPN = "vpn"

    /** vpnRequired=false(サイトブロックをユーザーが OFF)なら VPN は欠落扱いにしない */
    fun missing(context: Context, vpnRequired: Boolean = true): Set<String> = buildSet {
        if (!hasUsageAccess(context)) add(USAGE)
        if (!Settings.canDrawOverlays(context)) add(OVERLAY)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) add(NOTIFICATIONS)
        if (vpnRequired && (DnsVpnService.prepareIntent(context) != null || !DnsVpnService.isRunning)) add(VPN)
    }

    fun label(code: String): String = when (code) {
        USAGE -> "使用状況アクセス(計測も止まります)"
        OVERLAY -> "他のアプリの上に表示"
        NOTIFICATIONS -> "通知"
        VPN -> "サイトブロック(VPN)"
        else -> code
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
