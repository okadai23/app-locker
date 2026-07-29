package com.daisuke.readinglock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE status = 'ACTIVE' ORDER BY lastReadAt DESC, createdAt DESC")
    fun observeActive(): Flow<List<Book>>

    /** 本リスト画面。ACTIVE が先、次にアーカイブ済み */
    @Query("SELECT * FROM books ORDER BY status, lastReadAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: BookStatus)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE books SET lastReadAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ReadingSession): Long

    @Update
    suspend fun update(session: ReadingSession)

    @Query("SELECT * FROM reading_sessions WHERE state != 'ENDED' ORDER BY id")
    suspend fun openSessions(): List<ReadingSession>

    @Query("SELECT * FROM reading_sessions WHERE logicalDate = :date ORDER BY startedAt")
    fun observeByDate(date: String): Flow<List<ReadingSession>>
}

@Dao
interface DailyProgressDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: DailyProgress)

    @Update
    suspend fun update(progress: DailyProgress)

    @Query("SELECT * FROM daily_progress WHERE logicalDate = :date")
    suspend fun byDate(date: String): DailyProgress?

    @Query("SELECT logicalDate FROM daily_progress WHERE achievedAt IS NOT NULL")
    suspend fun achievedDates(): List<String>

    @Query("SELECT * FROM daily_progress ORDER BY logicalDate DESC")
    fun observeAll(): Flow<List<DailyProgress>>
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appName")
    fun observeAll(): Flow<List<BlockedApp>>

    @Query("SELECT packageName FROM blocked_apps WHERE enabled = 1")
    fun observeEnabledPackages(): Flow<List<String>>

    @Upsert
    suspend fun upsert(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface BlockedDomainDao {
    @Query("SELECT * FROM blocked_domains ORDER BY domain")
    fun observeAll(): Flow<List<BlockedDomain>>

    /** 有効なドメイン。削除予約済みでも予約時刻までは有効のまま */
    @Query("SELECT domain FROM blocked_domains WHERE pendingDeleteAt IS NULL OR pendingDeleteAt > :now")
    suspend fun activeDomains(now: Long): List<String>

    @Upsert
    suspend fun upsert(domain: BlockedDomain)

    @Query("UPDATE blocked_domains SET pendingDeleteAt = :at WHERE domain = :domain")
    suspend fun scheduleDelete(domain: String, at: Long)

    @Query("DELETE FROM blocked_domains WHERE pendingDeleteAt IS NOT NULL AND pendingDeleteAt <= :now")
    suspend fun purgeDue(now: Long)

    /** 削除予約の取り消し(誤タップの救済) */
    @Query("UPDATE blocked_domains SET pendingDeleteAt = NULL WHERE domain = :domain")
    suspend fun cancelDelete(domain: String)
}

@Dao
interface SessionEventDao {
    @Insert
    suspend fun insert(event: SessionEvent)

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY at")
    suspend fun bySession(sessionId: Long): List<SessionEvent>
}
