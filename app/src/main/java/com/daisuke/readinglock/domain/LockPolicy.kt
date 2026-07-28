package com.daisuke.readinglock.domain

import com.daisuke.readinglock.data.Source

/**
 * 計測対象の読書アプリ。同じサービスでも配布経路でパッケージ名が違うため集合で持つ。
 * DMM は Google Play 版と直販(book.dmm.com)版で別パッケージ。
 */
object ReadingApps {
    val KINDLE = setOf("com.amazon.kindle")
    val DMM = setOf("com.dmm.app.dmmbooks_google_play", "com.dmm.app.bookviewer")

    fun packagesFor(source: Source): Set<String> = when (source) {
        Source.KINDLE -> KINDLE
        Source.DMM -> DMM
        Source.PAPER -> emptySet()
    }
}

/** 表示条件: 前面アプリ ∈ BlockedApp(enabled) AND 本日未解禁 AND ホワイトリスト外。§5.1 */
object LockPolicy {

    fun shouldLock(
        foregroundPackage: String?,
        blockedPackages: Set<String>,
        unlocked: Boolean,
        whitelist: Set<String>,
    ): Boolean {
        if (foregroundPackage == null || unlocked) return false
        if (foregroundPackage in whitelist) return false
        return foregroundPackage in blockedPackages
    }

    /**
     * 解禁後にブロック対象アプリを使いすぎたら再ロックする。
     * usedSeconds は今回の1秒を加算した後の値を渡す。limitMinutes <= 0 は無制限。
     */
    fun shouldRelock(
        unlocked: Boolean,
        foregroundInBlocked: Boolean,
        usedSeconds: Int,
        limitMinutes: Int,
    ): Boolean = unlocked && foregroundInBlocked && limitMinutes > 0 && usedSeconds >= limitMinutes * 60
}
