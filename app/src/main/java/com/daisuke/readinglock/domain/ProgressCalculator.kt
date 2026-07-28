package com.daisuke.readinglock.domain

/** 内部は秒、UI 表示は分。§3 / §7 */
object ProgressCalculator {

    /** 経過表示は切り捨て(12分59秒 → 12分) */
    fun elapsedMinutes(totalSeconds: Int): Int = totalSeconds / 60

    /** 残りは切り上げ(過少申告しない)。達成済みは 0 */
    fun remainingMinutes(totalSeconds: Int, quotaMinutes: Int): Int {
        val remainingSeconds = quotaMinutes * 60 - totalSeconds
        if (remainingSeconds <= 0) return 0
        return (remainingSeconds + 59) / 60
    }

    fun achieved(totalSeconds: Int, quotaMinutes: Int): Boolean =
        quotaMinutes <= 0 || totalSeconds >= quotaMinutes * 60

    /** リング/プログレスバー用の 0f..1f */
    fun fraction(totalSeconds: Int, quotaMinutes: Int): Float {
        if (quotaMinutes <= 0) return 1f
        return (totalSeconds.toFloat() / (quotaMinutes * 60)).coerceIn(0f, 1f)
    }
}
