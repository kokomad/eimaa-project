package com.khader.gesturecontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/** يحوّل قراءات المستشعرات (جيروسكوب، قرب، تسارع) إلى إيماءات. */
class GestureEngine(private val ctx: Context, private val onGesture: (Gesture) -> Unit) :
    SensorEventListener {

    enum class Gesture {
        TILT_AWAY, TILT_TOWARD, ROLL_RIGHT, ROLL_LEFT,
        WAVE1, WAVE2, WAVE3, HOLD, SHAKE2
    }

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val prox: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lin: Sensor? = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val h = Handler(Looper.getMainLooper())

    private var started = false
    private var thr = 3.7f
    private var invert = false
    private var lockUntil = 0L

    // proximity state
    private var nearAt = 0L
    private var taps = 0
    private var holdFired = false
    private val holdR = Runnable { holdFired = true; taps = 0; emit(Gesture.HOLD) }
    private val tapR = Runnable {
        val t = taps
        taps = 0
        emit(if (t == 1) Gesture.WAVE1 else if (t == 2) Gesture.WAVE2 else Gesture.WAVE3)
    }

    // shake state
    private var lastSpike = 0L
    private var spikes = 0

    fun start() {
        if (started) return
        started = true
        reload()
        gyro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        prox?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        started = false
        sm.unregisterListener(this)
        h.removeCallbacksAndMessages(null)
        taps = 0
    }

    fun reload() {
        thr = 6.0f - 0.45f * Prefs.int(ctx, Prefs.SENS, 5)
        invert = Prefs.bool(ctx, Prefs.INVERT, false)
        if (started) {
            lin?.let { sm.unregisterListener(this, it) }
            if (Prefs.bool(ctx, Prefs.SHAKE, false)) {
                lin?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            }
        }
    }

    private fun emit(g: Gesture) = onGesture(g)

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> onGyro(e.values[0], e.values[1])
            Sensor.TYPE_PROXIMITY -> onProx(e.values[0] < e.sensor.maximumRange)
            Sensor.TYPE_LINEAR_ACCELERATION -> onAccel(e.values[0], e.values[1], e.values[2])
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun onGyro(gx: Float, gy: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now < lockUntil) return
        val ax = abs(gx)
        val ay = abs(gy)
        val g: Gesture? = if (ax >= thr && ax > ay * 1.8f) {
            if ((gx < 0) != invert) Gesture.TILT_AWAY else Gesture.TILT_TOWARD
        } else if (ay >= thr && ay > ax * 1.8f) {
            if ((gy > 0) != invert) Gesture.ROLL_RIGHT else Gesture.ROLL_LEFT
        } else null
        if (g != null) {
            lockUntil = now + 850
            emit(g)
        }
    }

    private fun onProx(near: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (near) {
            nearAt = now
            holdFired = false
            h.removeCallbacks(tapR)
            h.postDelayed(holdR, 1000)
        } else {
            h.removeCallbacks(holdR)
            if (holdFired || nearAt == 0L) return
            if (now - nearAt < 800) {
                taps++
                h.removeCallbacks(tapR)
                h.postDelayed(tapR, 450)
            }
        }
    }

    private fun onAccel(x: Float, y: Float, z: Float) {
        val m = sqrt(x * x + y * y + z * z)
        if (m < 16f) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSpike < 120) return
        spikes = if (now - lastSpike < 800) spikes + 1 else 1
        lastSpike = now
        if (spikes >= 2) {
            spikes = 0
            lockUntil = now + 900
            emit(Gesture.SHAKE2)
        }
    }
}
