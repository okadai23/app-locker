package com.daisuke.readinglock.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.daisuke.readinglock.domain.FaceDownDetector

/**
 * FaceDownDetector のセンサー配線。紙セッション中だけ購読する(電池対策)。§2 / §4.2
 * 画面OFF 中は伏せ扱いでカウントするため加速度は落とし、近接だけ低頻度で残す。
 */
class FaceDownMonitor(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val proximity = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile private var near = false
    @Volatile private var zDown = false
    @Volatile private var screenOn = true
    @Volatile private var detector: FaceDownDetector? = null

    val counting: Boolean get() = detector?.counting == true

    fun start(graceSeconds: Int) {
        if (detector != null) return
        near = false
        zDown = false
        detector = FaceDownDetector(graceSeconds * 1000L)
        register()
    }

    fun stop() {
        if (detector == null) return
        detector = null
        manager?.unregisterListener(this)
    }

    fun setScreenOn(on: Boolean) {
        if (screenOn == on) return
        screenOn = on
        if (detector != null) register()
    }

    /** 現在の伏せ状態を状態機械に流し込む。毎 tick 呼ぶ */
    fun update(now: Long = System.currentTimeMillis()): FaceDownDetector.Action =
        detector?.update(!screenOn || (near && zDown), now) ?: FaceDownDetector.Action.NONE

    private fun register() {
        val manager = manager ?: return
        manager.unregisterListener(this)
        proximity?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (screenOn) {
            accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            // 近接は 0(near)か maximumRange(far)を返す端末が多い。5cm を上限に見る
            Sensor.TYPE_PROXIMITY -> near = event.values[0] < minOf(event.sensor.maximumRange, NEAR_CM)
            Sensor.TYPE_ACCELEROMETER -> zDown = event.values[2] <= FACE_DOWN_Z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val NEAR_CM = 5f
        /** 伏せ = Z軸 ≈ −9.8。傾きの許容幅を持たせる */
        const val FACE_DOWN_Z = -8.0f
    }
}
