package com.daisuke.readinglock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Source { KINDLE, DMM, PAPER }

enum class BookStatus { ACTIVE, ARCHIVED }

enum class SessionState { RUNNING, PAUSED, ENDED }

enum class PauseReason { OTHER_APP, PICKED_UP, MANUAL }

enum class EventType { DECLARE, START, AUTO_PAUSE, RESUME, END, FORCE_END_RESET, RESTORED }

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: Source,
    val status: BookStatus = BookStatus.ACTIVE,
    val lastReadAt: Long? = null,
    val createdAt: Long,
)

@Entity(tableName = "reading_sessions")
data class ReadingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long?,
    val source: Source,
    val logicalDate: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val effectiveSeconds: Int = 0,
    val state: SessionState = SessionState.RUNNING,
    val pausedReason: PauseReason? = null,
    val lastHeartbeatAt: Long,
)

@Entity(tableName = "daily_progress")
data class DailyProgress(
    @PrimaryKey val logicalDate: String,
    val totalSeconds: Int = 0,
    /** その日に適用されたノルマ。履歴の正確性のため凍結保存する */
    val quotaMinutes: Int,
    val achievedAt: Long? = null,
    /** 解禁後の使いすぎで再ロックした回数。1回ごとにノルマが1周分積み増しされる */
    val relockCount: Int = 0,
    /** 解禁後にブロック対象アプリを使った秒。再ロックのたびに 0 に戻る */
    val postAchieveUsedSeconds: Int = 0,
)

@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val enabled: Boolean = true,
    val addedAt: Long,
)

@Entity(tableName = "blocked_domains")
data class BlockedDomain(
    @PrimaryKey val domain: String,
    val addedAt: Long,
    /** 翌日4:00の削除予約。null なら有効 */
    val pendingDeleteAt: Long? = null,
)

@Entity(tableName = "session_events")
data class SessionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: EventType,
    val at: Long,
)
