package com.daisuke.readinglock

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.daisuke.readinglock.data.AppDatabase
import com.daisuke.readinglock.data.SettingsRepository
import com.daisuke.readinglock.domain.SessionEngine
import com.daisuke.readinglock.system.Notifications
import com.daisuke.readinglock.system.ResetScheduler

/** DI フレームワークは使わない。手書きシングルトンで足りる */
object AppGraph {
    private lateinit var appContext: Context

    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val engine: SessionEngine by lazy { SessionEngine(db, settings) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/** オーバーレイ・通知からのディープリンク先。reading://home / reading://start */
object AppIntents {
    const val SCHEME = "reading"
    const val HOST_HOME = "home"
    const val HOST_START = "start"

    fun home(context: Context): Intent = link(context, HOST_HOME)

    fun startFlow(context: Context): Intent = link(context, HOST_START)

    private fun link(context: Context, host: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("$SCHEME://$host"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

class ReadingLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        Notifications.ensureChannels(this)
        ResetScheduler.schedule(this)
        // MonitorService の起動は Activity / BOOT_COMPLETED / アラームから。
        // バックグラウンドで起きたプロセスから FGS を上げると Android 12+ で落ちる
    }
}
