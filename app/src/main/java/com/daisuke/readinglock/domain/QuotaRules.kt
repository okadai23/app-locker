package com.daisuke.readinglock.domain

/** ノルマ変更: 延長は即時、短縮は翌日4:00 反映。§3 */
object QuotaRules {

    /** @return (即時適用するノルマ, 翌日反映の pending 値 or null) */
    fun request(current: Int, pending: Int?, requested: Int): Pair<Int, Int?> = when {
        requested >= current -> requested to null // 延長は即時、pending もキャンセル
        else -> current to requested              // 短縮は保留
    }

    fun applyPending(current: Int, pending: Int?): Int = pending ?: current
}
