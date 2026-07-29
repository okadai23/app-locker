package com.daisuke.readinglock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Book::class,
        ReadingSession::class,
        DailyProgress::class,
        BlockedApp::class,
        BlockedDomain::class,
        SessionEvent::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun sessions(): SessionDao
    abstract fun dailyProgress(): DailyProgressDao
    abstract fun blockedApps(): BlockedAppDao
    abstract fun blockedDomains(): BlockedDomainDao
    abstract fun sessionEvents(): SessionEventDao

    companion object {
        /** 解禁後の再ロック(利用上限)の追加。履歴は消さない */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_progress ADD COLUMN relockCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE daily_progress ADD COLUMN postAchieveUsedSeconds INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val SEED_APPS = listOf(
            "com.twitter.android" to "X",
            "com.facebook.katana" to "Facebook",
        )
        private val SEED_DOMAINS = listOf(
            "shonenjumpplus.com",
            "comic-days.com",
            "tonarinoyj.jp",
            "nicovideo.jp",
        )

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reading_lock.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val now = System.currentTimeMillis()
                        SEED_APPS.forEach { (pkg, name) ->
                            db.execSQL(
                                "INSERT INTO blocked_apps (packageName, appName, enabled, addedAt) VALUES (?, ?, 1, ?)",
                                arrayOf(pkg, name, now),
                            )
                        }
                        SEED_DOMAINS.forEach { domain ->
                            db.execSQL(
                                "INSERT INTO blocked_domains (domain, addedAt, pendingDeleteAt) VALUES (?, ?, NULL)",
                                arrayOf(domain, now),
                            )
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
