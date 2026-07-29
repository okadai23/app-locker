package com.daisuke.readinglock.domain

import com.daisuke.readinglock.data.AppDatabase
import com.daisuke.readinglock.data.DailyProgress
import com.daisuke.readinglock.data.EventType
import com.daisuke.readinglock.data.PauseReason
import com.daisuke.readinglock.data.ReadingSession
import com.daisuke.readinglock.data.SessionEvent
import com.daisuke.readinglock.data.SessionState
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TodayProgress(
    val logicalDate: String,
    val totalSeconds: Int,
    val quotaMinutes: Int,
    /** 解禁後の使いすぎで再ロックした回数。1回ごとにノルマが1周分積み増しされる */
    val relockCount: Int = 0,
) {
    /** 再ロックを織り込んだ、今のロック解除に必要なノルマ */
    val effectiveQuotaMinutes: Int get() = quotaMinutes * (relockCount + 1)

    /** その日の初回達成。ストリーク・履歴はこちらを見る(基準ノルマのまま) */
    val achieved: Boolean get() = ProgressCalculator.achieved(totalSeconds, quotaMinutes)

    /** 今ロックが解けているか。ロック判定と画面表示はこちら */
    val unlocked: Boolean get() = ProgressCalculator.achieved(totalSeconds, effectiveQuotaMinutes)

    val remainingMinutes: Int get() = ProgressCalculator.remainingMinutes(totalSeconds, effectiveQuotaMinutes)
    val elapsedMinutes: Int get() = ProgressCalculator.elapsedMinutes(totalSeconds)
}

/** tick 1回で起きた副作用。呼び出し側(サービス)が通知に使う */
data class TickResult(
    val autoPaused: Boolean = false,
    val autoResumed: Boolean = false,
    val achievedNow: Boolean = false,
    val relockedNow: Boolean = false,
) {
    companion object { val NONE = TickResult() }
}

/**
 * セッションの状態機械と実効秒の積算。§4 / §9-1 / §9-2
 * 実効秒は1秒粒度でメモリに積み、10秒ごとに永続化(ハートビート)する。
 */
class SessionEngine(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** 紙セッションを積算してよいか。FaceDownDetector が駆動する。§4.2 */
    @Volatile var paperCounting: Boolean = false

    private val _active = MutableStateFlow<ReadingSession?>(null)
    val active: StateFlow<ReadingSession?> = _active.asStateFlow()

    private val _today = MutableStateFlow(TodayProgress("", 0, 30))
    val today: StateFlow<TodayProgress> = _today.asStateFlow()

    /** 未計上区間の起点。null なら積算していない */
    private var accrualAnchor: Long? = null

    /** 未永続化の秒数(読書・解禁後利用のどちらも)。10秒でハートビート */
    private var unflushedSeconds = 0

    /** 解禁後にブロック対象アプリを使った秒。再ロックで 0 に戻る */
    private var postUnlockUsedSeconds = 0
    private var relockLimitMinutes = 30

    /** サービス起動時に呼ぶ。進捗の読み込みと未END セッションの復元。§9-2 */
    suspend fun refresh(): Boolean = mutex.withLock {
        loadToday()
        var restored = false
        for (session in db.sessions().openSessions()) {
            if (session.logicalDate != _today.value.logicalDate) {
                // ハートビートから 4:00 を跨いでいた → 前論理日で強制END
                forceEnd(session)
            } else {
                val paused = session.copy(state = SessionState.PAUSED, pausedReason = PauseReason.MANUAL)
                db.sessions().update(paused)
                logEvent(paused.id, EventType.RESTORED)
                _active.value = paused
                restored = true
            }
        }
        accrualAnchor = null
        restored
    }

    suspend fun start(bookId: Long?, source: Source): ReadingSession = mutex.withLock {
        flushLocked()
        _active.value?.let { endLocked(it, EventType.END) }
        loadToday()
        val t = now()
        val session = ReadingSession(
            bookId = bookId,
            source = source,
            logicalDate = _today.value.logicalDate,
            startedAt = t,
            state = SessionState.RUNNING,
            lastHeartbeatAt = t,
        )
        val id = db.sessions().insert(session)
        val stored = session.copy(id = id)
        if (bookId != null) logEvent(id, EventType.DECLARE)
        logEvent(id, EventType.START)
        _active.value = stored
        accrualAnchor = t
        stored
    }

    suspend fun pause(reason: PauseReason) = mutex.withLock {
        pauseLocked(reason)
    }

    suspend fun resume() = mutex.withLock {
        resumeLocked()
    }

    suspend fun end() = mutex.withLock {
        flushLocked()
        _active.value?.let { endLocked(it, EventType.END) }
        Unit
    }

    /**
     * 毎秒呼ぶ。前面パッケージに応じて積算・自動 PAUSE/RESUME を行う。
     * @param foregroundPackage UsageStats から取得した前面アプリ。不明なら null
     * @param selfPackage 自アプリ(中立: 積算も PAUSE もしない)
     * @param blockedPackages 有効なブロック対象。解禁後の利用時間の積算に使う
     */
    suspend fun tick(
        foregroundPackage: String?,
        selfPackage: String,
        blockedPackages: Set<String> = emptySet(),
    ): TickResult = mutex.withLock {
        if (LogicalDayResolver.logicalDate(now()) != _today.value.logicalDate) {
            // アラームが不発でも論理日が変わったら必ずリセットする
            resetLocked()
            return@withLock TickResult.NONE
        }

        val session = _active.value
        var result = TickResult.NONE

        if (session != null && session.source != Source.PAPER) {
            val targets = ReadingApps.packagesFor(session.source)
            val onTarget = foregroundPackage != null && foregroundPackage in targets
            val neutral = foregroundPackage == null || foregroundPackage == selfPackage
            when {
                session.state == SessionState.RUNNING && !onTarget && !neutral -> {
                    accrueLocked(counting = false)
                    pauseLocked(PauseReason.OTHER_APP)
                    result = result.copy(autoPaused = true)
                }
                session.state == SessionState.PAUSED &&
                    session.pausedReason == PauseReason.OTHER_APP && onTarget -> {
                    resumeLocked()
                    result = result.copy(autoResumed = true)
                }
            }
        }

        val current = _active.value
        val counting = current != null && current.state == SessionState.RUNNING && (
            if (current.source == Source.PAPER) {
                paperCounting
            } else {
                foregroundPackage != null && foregroundPackage in ReadingApps.packagesFor(current.source)
            }
            )
        val unlockedBefore = _today.value.unlocked
        accrueLocked(counting)
        if (!unlockedBefore && _today.value.unlocked) {
            flushLocked()
            result = result.copy(achievedNow = true)
        }

        // 解禁後にブロック対象アプリを使った時間を積む。上限に達したらノルマを1周分積み増して再ロック
        val unlocked = _today.value.unlocked
        val inBlocked = foregroundPackage != null && foregroundPackage in blockedPackages
        if (unlocked && inBlocked) {
            postUnlockUsedSeconds++
            unflushedSeconds++
        }
        if (LockPolicy.shouldRelock(unlocked, inBlocked, postUnlockUsedSeconds, relockLimitMinutes)) {
            _today.value = _today.value.copy(relockCount = _today.value.relockCount + 1)
            postUnlockUsedSeconds = 0
            flushLocked(force = true)
            result = result.copy(relockedNow = true)
        }

        if (unflushedSeconds >= HEARTBEAT_SECONDS) flushLocked()
        result
    }

    /** 4:00 リセット。開いているセッションを前論理日で強制END し、pending ノルマを適用する。§9-1 */
    suspend fun runDailyReset() = mutex.withLock { resetLocked() }

    /** 延長は即時、短縮は翌日反映。即時反映分は当日の凍結ノルマにも反映する。§3 */
    suspend fun setQuota(minutes: Int) = mutex.withLock {
        settings.requestQuota(minutes)
        val applied = settings.current().quotaMinutes
        val progress = _today.value
        if (applied != progress.quotaMinutes) {
            val row = db.dailyProgress().byDate(progress.logicalDate)
            if (row != null) {
                val achievedAt = if (ProgressCalculator.achieved(row.totalSeconds, applied)) {
                    row.achievedAt ?: now()
                } else {
                    null
                }
                db.dailyProgress().update(row.copy(quotaMinutes = applied, achievedAt = achievedAt))
            }
            _today.value = progress.copy(quotaMinutes = applied)
        }
    }

    /** 解禁後の利用上限。0 は無制限。即時反映 */
    suspend fun setRelockLimit(minutes: Int) = mutex.withLock {
        val applied = minutes.coerceAtLeast(0)
        settings.setRelockLimitMinutes(applied)
        relockLimitMinutes = applied
    }

    // --- 以下 mutex 保持前提 ---

    private suspend fun resetLocked() {
        flushLocked()
        for (session in db.sessions().openSessions()) forceEnd(session)
        _active.value = null
        accrualAnchor = null
        settings.applyPendingQuota()
        loadToday()
    }

    private suspend fun forceEnd(session: ReadingSession) {
        val ended = session.copy(
            state = SessionState.ENDED,
            endedAt = now(),
            pausedReason = null,
            lastHeartbeatAt = now(),
        )
        db.sessions().update(ended)
        logEvent(ended.id, EventType.FORCE_END_RESET)
        ended.bookId?.let { db.books().touch(it, ended.endedAt!!) }
    }

    private suspend fun endLocked(session: ReadingSession, type: EventType) {
        val t = now()
        val ended = session.copy(state = SessionState.ENDED, endedAt = t, pausedReason = null, lastHeartbeatAt = t)
        db.sessions().update(ended)
        logEvent(ended.id, type)
        ended.bookId?.let { db.books().touch(it, t) }
        _active.value = null
        accrualAnchor = null
    }

    private suspend fun pauseLocked(reason: PauseReason) {
        val session = _active.value ?: return
        if (session.state != SessionState.RUNNING) return
        val paused = session.copy(state = SessionState.PAUSED, pausedReason = reason, lastHeartbeatAt = now())
        db.sessions().update(paused)
        logEvent(paused.id, EventType.AUTO_PAUSE)
        _active.value = paused
        accrualAnchor = null
        flushLocked() // 一時停止時点で永続化しておく(次のハートビートを待たない)
    }

    private suspend fun resumeLocked() {
        val session = _active.value ?: return
        if (session.state != SessionState.PAUSED) return
        val running = session.copy(state = SessionState.RUNNING, pausedReason = null, lastHeartbeatAt = now())
        db.sessions().update(running)
        logEvent(running.id, EventType.RESUME)
        _active.value = running
        accrualAnchor = now()
    }

    /** 前回アンカーからの経過秒をメモリに積む。counting=false ならアンカーを進めるだけ */
    private fun accrueLocked(counting: Boolean) {
        val t = now()
        if (!counting) {
            accrualAnchor = null
            return
        }
        val anchor = accrualAnchor
        if (anchor == null) {
            accrualAnchor = t
            return
        }
        val seconds = ((t - anchor) / 1000L).toInt()
        if (seconds <= 0) return
        accrualAnchor = anchor + seconds * 1000L
        unflushedSeconds += seconds
        _active.value = _active.value?.let { it.copy(effectiveSeconds = it.effectiveSeconds + seconds) }
        _today.value = _today.value.copy(totalSeconds = _today.value.totalSeconds + seconds)
    }

    private suspend fun flushLocked(force: Boolean = false) {
        if (unflushedSeconds == 0 && !force) return
        val t = now()
        val progress = _today.value
        val row = db.dailyProgress().byDate(progress.logicalDate)
        if (row != null) {
            // achievedAt は基準ノルマの初回達成のまま(ストリーク・履歴を再ロックで壊さない)
            val achievedAt = if (progress.achieved) row.achievedAt ?: t else null
            db.dailyProgress().update(
                row.copy(
                    totalSeconds = progress.totalSeconds,
                    achievedAt = achievedAt,
                    relockCount = progress.relockCount,
                    postAchieveUsedSeconds = postUnlockUsedSeconds,
                ),
            )
        }
        _active.value?.let { db.sessions().update(it.copy(lastHeartbeatAt = t)) }
        unflushedSeconds = 0
    }

    private suspend fun loadToday() {
        val date = LogicalDayResolver.logicalDate(now())
        val current = settings.current()
        val quota = current.quotaMinutes
        relockLimitMinutes = current.relockLimitMinutes
        db.dailyProgress().insertIfAbsent(DailyProgress(logicalDate = date, quotaMinutes = quota))
        val row = db.dailyProgress().byDate(date)
        _today.value = TodayProgress(
            logicalDate = date,
            totalSeconds = row?.totalSeconds ?: 0,
            quotaMinutes = row?.quotaMinutes ?: quota,
            relockCount = row?.relockCount ?: 0,
        )
        postUnlockUsedSeconds = row?.postAchieveUsedSeconds ?: 0
        unflushedSeconds = 0
    }

    private suspend fun logEvent(sessionId: Long, type: EventType) {
        db.sessionEvents().insert(SessionEvent(sessionId = sessionId, type = type, at = now()))
    }

    private companion object {
        const val HEARTBEAT_SECONDS = 10
    }
}
