package com.daisuke.readinglock.domain

/**
 * 紙セッションの伏せ検知ステートマシン(純粋)。§4.2
 *
 * 伏せ(近接near+Z軸下向き)または画面OFF でカウント。持ち上げたら猶予秒後に PAUSE。
 * 猶予内に伏せ直せば PAUSE しない。PAUSE 後に伏せたら RESUME。
 */
class FaceDownDetector(private val graceMillis: Long) {

    enum class State { IDLE, COUNTING, GRACE, PAUSED }

    enum class Action { NONE, PAUSE, RESUME }

    var state: State = State.IDLE
        private set

    private var graceSince = 0L

    /** 実効秒を積算してよいか。猶予中は継続(10秒後に PAUSE) */
    val counting: Boolean get() = state == State.COUNTING || state == State.GRACE

    /** 現在の伏せ状態を流し込む。毎 tick とセンサー変化時に呼ぶ */
    fun update(faceDown: Boolean, now: Long): Action {
        if (faceDown) {
            val resumed = state == State.PAUSED
            state = State.COUNTING
            return if (resumed) Action.RESUME else Action.NONE
        }
        return when (state) {
            State.COUNTING -> {
                state = State.GRACE
                graceSince = now
                Action.NONE
            }
            State.GRACE -> if (now - graceSince >= graceMillis) {
                state = State.PAUSED
                Action.PAUSE
            } else {
                Action.NONE
            }
            // セッション開始後まだ一度も伏せていない / すでに PAUSE 済み
            State.IDLE, State.PAUSED -> Action.NONE
        }
    }
}
