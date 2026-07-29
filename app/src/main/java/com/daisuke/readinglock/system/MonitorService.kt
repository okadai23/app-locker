package com.daisuke.readinglock.system

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daisuke.readinglock.AppGraph
import com.daisuke.readinglock.data.PauseReason
import com.daisuke.readinglock.data.SessionState
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.domain.FaceDownDetector
import com.daisuke.readinglock.domain.LockPolicy
import com.daisuke.readinglock.domain.ReadingApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 常駐監視。UsageStatsManager のポーリングで前面アプリを取り、
 * (a) ロックオーバーレイの出し入れ (b) セッションの自動 PAUSE/RESUME を駆動する。§2 / §4.1 / §5.1
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine get() = AppGraph.engine

    private lateinit var overlay: LockOverlayController
    private lateinit var timerOverlay: TimerOverlayController
    private lateinit var usageStats: UsageStatsManager
    private lateinit var whitelist: Set<String>
    private lateinit var faceDown: FaceDownMonitor
    private var paperWakeLock: PowerManager.WakeLock? = null

    @Volatile private var blockedPackages: Set<String> = emptySet()
    @Volatile private var screenOn = true
    private var lastEventQueryAt = 0L
    private var foregroundPackage: String? = null
    private var lastNotificationKey: String? = null
    private var lastPermissionCheckAt = 0L
    private var lastMissingPermissions: Set<String>? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            faceDown.setScreenOn(screenOn)
            if (!screenOn) scope.launch {
                withContext(Dispatchers.Main) { overlay.hide(); timerOverlay.hide() }
                // 前面 null = 中立。Kindle/DMM の積算を止め、紙は画面OFF=伏せ扱いで継続させる
                applyFaceDown()
                engine.tick(null, packageName, blockedPackages)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        startForegroundNow()

        overlay = LockOverlayController(this)
        timerOverlay = TimerOverlayController(this)
        usageStats = getSystemService(UsageStatsManager::class.java)
        whitelist = buildWhitelist()
        faceDown = FaceDownMonitor(this)
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        faceDown.setScreenOn(screenOn)
        lastEventQueryAt = System.currentTimeMillis() - LOOKBACK_MS

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        scope.launch {
            AppGraph.db.blockedApps().observeEnabledPackages().collectLatest { blockedPackages = it.toSet() }
        }
        scope.launch {
            // センサー購読は紙セッション中だけ。§2(電池対策)
            engine.active
                .map { it != null && it.source == Source.PAPER && it.state != SessionState.ENDED }
                .distinctUntilChanged()
                .collect { paper ->
                    if (paper) {
                        faceDown.start(AppGraph.settings.current().paperGraceSeconds)
                        acquirePaperWakeLock()
                    } else {
                        faceDown.stop()
                        engine.paperCounting = false
                        releasePaperWakeLock()
                    }
                }
        }
        scope.launch {
            if (engine.refresh()) {
                Notifications.event(this@MonitorService, "セッションを復元しました", "再開してください")
            }
            loop()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        faceDown.stop()
        releasePaperWakeLock()
        overlay.hide()
        timerOverlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 画面OFF 中の Doze で coroutine の delay が止まると紙セッションが一切カウントされないため、
     * 紙セッションが存在する間だけ CPU を起こしておく。PAUSED 中も伏せ直しの検知に tick が要る。
     * 電池の消費はセッション長(実測30分程度)で頭打ちになる。
     */
    private fun acquirePaperWakeLock() {
        if (paperWakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        paperWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "readinglock:paper").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releasePaperWakeLock() {
        paperWakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        paperWakeLock = null
    }

    private suspend fun loop() {
        while (true) {
            if (screenOn) {
                runCatching { step() }
                delay(POLL_INTERVAL_MS)
            } else {
                // 画面OFF中はポーリング不要。紙セッションの積算(実時間差分の一括加算)だけ粗い間隔で回す
                runCatching {
                    applyFaceDown()
                    engine.tick(null, packageName, blockedPackages)
                }
                delay(SCREEN_OFF_INTERVAL_MS)
            }
        }
    }

    private suspend fun step() {
        checkPermissions()
        applyFaceDown()
        foregroundPackage = latestForegroundPackage() ?: foregroundPackage
        val result = engine.tick(foregroundPackage, packageName, blockedPackages)

        if (result.autoPaused) {
            Notifications.event(this, "他のアプリの操作を検知しました", "読書セッションを一時停止しました")
        }
        val progress = engine.today.value
        if (result.achievedNow) {
            Notifications.event(this, "今日のノルマを達成しました", "ロックを解除しました")
        }
        if (result.relockedNow) {
            Notifications.event(
                this,
                "利用上限に達しました",
                "ブロックを再開しました。あと${progress.remainingMinutes}分の読書で再び解除できます",
            )
        }

        val shouldLock = LockPolicy.shouldLock(foregroundPackage, blockedPackages, progress.unlocked, whitelist)
        val session = engine.active.value
        // 読書アプリを開いている間だけ経過時間チップを出す。ロック画面とは同時に出さない
        val showTimer = !shouldLock && session != null &&
            session.state == SessionState.RUNNING &&
            session.source != Source.PAPER &&
            foregroundPackage != null &&
            foregroundPackage in ReadingApps.packagesFor(session.source)
        withContext(Dispatchers.Main) {
            if (shouldLock) {
                overlay.show(LockContent(appLabel(foregroundPackage!!), progress))
            } else if (overlay.isShowing) {
                overlay.hide()
            }
            if (showTimer) {
                timerOverlay.show(progress.elapsedMinutes, progress.effectiveQuotaMinutes)
            } else if (timerOverlay.isShowing) {
                timerOverlay.hide()
            }
        }

        val key = "${progress.elapsedMinutes}/${progress.effectiveQuotaMinutes}/${progress.unlocked}"
        if (key != lastNotificationKey) {
            lastNotificationKey = key
            startForegroundNow()
        }
    }

    /** 伏せ状態を状態機械に流し、PAUSE/RESUME を適用する。§4.2 */
    private suspend fun applyFaceDown() {
        when (faceDown.update()) {
            FaceDownDetector.Action.PAUSE -> {
                engine.pause(PauseReason.PICKED_UP)
                Notifications.event(this, "読書を一時停止しました", "スマホを伏せると再開します")
            }
            // 手動 PAUSE / 復元直後の PAUSE は自動再開しない
            FaceDownDetector.Action.RESUME ->
                if (engine.active.value?.pausedReason == PauseReason.PICKED_UP) engine.resume()
            FaceDownDetector.Action.NONE -> Unit
        }
        engine.paperCounting = faceDown.counting
    }

    /** 60秒ごとの権限セルフチェック。fail-closed にはしない。§9-3 */
    private suspend fun checkPermissions() {
        val now = System.currentTimeMillis()
        if (now - lastPermissionCheckAt < PERMISSION_CHECK_MS) return
        lastPermissionCheckAt = now
        val siteBlock = AppGraph.settings.current().siteBlockEnabled
        // 同意済みなのに VPN が落ちていれば起こす(プロセスキル・再インストール後の復帰)。
        // 他アプリに奪われた場合は prepare() が非 null になるため、ここでは奪い返さない(§9-4)。
        // ユーザーがサイトブロックを OFF にしている間は起こさない
        if (siteBlock && !DnsVpnService.isRunning && DnsVpnService.prepareIntent(this) == null) {
            DnsVpnService.start(this)
        }
        if (!siteBlock && DnsVpnService.isRunning) {
            DnsVpnService.stop(this)
        }
        val missing = Permissions.missing(this, vpnRequired = siteBlock)
        if (missing == lastMissingPermissions) return
        lastMissingPermissions = missing
        AppGraph.settings.setWarnings(missing)
        if (missing.isNotEmpty()) {
            Notifications.warning(
                this,
                "読書ロックの権限が不足しています",
                missing.joinToString("、") { Permissions.label(it) } + " を許可してください",
            )
        }
    }

    /** 直近クエリ以降の MOVE_TO_FOREGROUND の最後のパッケージ。権限が無ければ null */
    private fun latestForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = runCatching { usageStats.queryEvents(lastEventQueryAt, now) }.getOrNull() ?: return null
        lastEventQueryAt = now
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = event.packageName
        }
        return latest
    }

    private fun startForegroundNow() {
        val notification = Notifications.resident(this, AppGraph.engine.today.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_RESIDENT, notification, type)
    }

    /** 自アプリ・ランチャー・システムUI はロック対象外 */
    private fun buildWhitelist(): Set<String> {
        val launcher = packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            ?.activityInfo?.packageName
        return setOfNotNull(packageName, launcher, "com.android.systemui")
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
        // 10秒 = ハートビートの永続化粒度。これより粗いと画面OFF の紙読書が保存されない
        private const val SCREEN_OFF_INTERVAL_MS = 10_000L
        private const val LOOKBACK_MS = 60_000L
        private const val PERMISSION_CHECK_MS = 60_000L

        fun start(context: Context) {
            // バックグラウンド起動制限で弾かれることがある。落とすほどのものではない
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
            }
        }
    }
}
