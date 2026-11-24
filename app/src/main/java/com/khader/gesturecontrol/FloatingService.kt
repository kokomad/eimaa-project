package com.khader.gesturecontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.khader.gesturecontrol.GestureEngine.Gesture
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        @Volatile
        var running = false
        const val ACTION_STOP = "com.khader.gesturecontrol.STOP"
        private const val CH = "gc_channel"
        private const val ROWS = 7
    }

    private lateinit var wm: WindowManager
    private lateinit var engine: GestureEngine
    private lateinit var btnLp: WindowManager.LayoutParams
    private var btn: TextView? = null
    private var active = false

    private var launcherView: LinearLayout? = null
    private var launcherRows: List<TextView> = emptyList()
    private var apps: List<Pair<String, String>> = emptyList()
    private var sel = 0

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> engine.reload() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        engine = GestureEngine(this) { handle(it) }
        startForeground(1, buildNotification())
        addButton()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        engine.stop()
        closeLauncher()
        try { btn?.let { wm.removeView(it) } } catch (_: Exception) {}
        running = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "التحكم بالإيماءات", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("إيماءة تعمل")
            .setContentText("اضغط الزر العائم لتفعيل/إيقاف الإيماءات")
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat), "إيقاف", stop
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    // ---------------- الزر العائم ----------------

    private fun addButton() {
        val size = dp(56)
        val t = TextView(this).apply {
            text = "✋"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        btn = t
        styleButton()
        btnLp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = Prefs.int(this@FloatingService, Prefs.POSX, 0)
            y = Prefs.int(this@FloatingService, Prefs.POSY, dp(220))
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        t.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = btnLp.x; sy = btnLp.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx
                        val dy = e.rawY - ty
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            btnLp.x = (sx + dx).toInt()
                            btnLp.y = (sy + dy).toInt()
                            wm.updateViewLayout(v, btnLp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            toggle()
                        } else {
                            val sw = resources.displayMetrics.widthPixels
                            btnLp.x = if (e.rawX < sw / 2f) 0 else sw - btnLp.width
                            wm.updateViewLayout(v, btnLp)
                            Prefs.setInt(this@FloatingService, Prefs.POSX, btnLp.x)
                            Prefs.setInt(this@FloatingService, Prefs.POSY, btnLp.y)
                        }
                    }
                }
                return true
            }
        })
        wm.addView(t, btnLp)
    }

    private fun styleButton() {
        btn?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (active) "#10B981" else "#475569"))
            setStroke(dp(2), Color.WHITE)
        }
        btn?.alpha = if (active) 1f else 0.75f
    }

    private fun toggle() {
        if (!active) {
            if (GestureAccessibilityService.instance == null) {
                Toast.makeText(this, "فعّل خدمة «إيماءة» من إعدادات إمكانية الوصول أولاً", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            active = true
            engine.start()
        } else {
            active = false
            engine.stop()
            closeLauncher()
        }
        styleButton()
        buzz(if (active) 70 else 30)
    }

    // ---------------- معالجة الإيماءات ----------------

    private fun handle(g: Gesture) {
        val svc = GestureAccessibilityService.instance ?: return
        if (launcherView != null) {
            launcherGesture(g)
            return
        }
        val c = this
        when (g) {
            Gesture.TILT_AWAY -> if (Prefs.bool(c, Prefs.SCROLL, true)) { svc.scroll(true); buzz() }
            Gesture.TILT_TOWARD -> if (Prefs.bool(c, Prefs.SCROLL, true)) { svc.scroll(false); buzz() }
            Gesture.ROLL_RIGHT -> if (Prefs.bool(c, Prefs.VOLUME, true)) { svc.volume(true); buzz() }
            Gesture.ROLL_LEFT -> if (Prefs.bool(c, Prefs.VOLUME, true)) { svc.volume(false); buzz() }
            Gesture.WAVE1 -> if (Prefs.bool(c, Prefs.MEDIA, true)) { svc.playPause(); buzz() }
            Gesture.WAVE2 -> if (Prefs.bool(c, Prefs.MEDIA, true)) { svc.back(); buzz() }
            Gesture.WAVE3 -> if (Prefs.bool(c, Prefs.MEDIA, true)) { svc.home(); buzz() }
            Gesture.HOLD -> if (Prefs.bool(c, Prefs.LAUNCHER, true)) { openLauncher(); buzz(80) }
            Gesture.SHAKE2 -> { svc.home(); buzz() }
        }
    }

    // ---------------- قائمة التطبيقات ----------------

    @Suppress("DEPRECATION")
    private fun openLauncher() {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = pm.queryIntentActivities(i, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first }
        val favs = Prefs.favs(this)
        apps = if (favs.isNotEmpty()) all.filter { it.second in favs } else all
        if (apps.isEmpty()) return
        sel = 0

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0E1420"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#22D3EE"))
            }
        }
        box.addView(TextView(this).apply {
            text = "اختر تطبيقاً"
            textSize = 16f
            setTextColor(Color.parseColor("#22D3EE"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        val rows = (0 until ROWS).map {
            TextView(this).apply {
                textSize = 18f
                minWidth = dp(240)
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
        }
        rows.forEach { box.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        box.addView(TextView(this).apply {
            text = "أمِل للتنقل • لفّة يمين: فتح • لفّة يسار: إغلاق"
            textSize = 12f
            setTextColor(Color.parseColor("#93A4BD"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        launcherRows = rows
        launcherView = box
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(box, lp)
        renderLauncher()
    }

    private fun renderLauncher() {
        val start = (sel - 3).coerceIn(0, maxOf(0, apps.size - ROWS))
        launcherRows.forEachIndexed { i, r ->
            val idx = start + i
            if (idx < apps.size) {
                r.visibility = View.VISIBLE
                r.text = apps[idx].first
                val on = idx == sel
                r.setTextColor(if (on) Color.parseColor("#06222B") else Color.WHITE)
                r.background = if (on) GradientDrawable().apply {
                    setColor(Color.parseColor("#22D3EE"))
                    cornerRadius = dp(12).toFloat()
                } else null
            } else {
                r.visibility = View.GONE
            }
        }
    }

    private fun launcherGesture(g: Gesture) {
        when (g) {
            Gesture.TILT_AWAY -> { sel = minOf(apps.size - 1, sel + 1); renderLauncher(); buzz(20) }
            Gesture.TILT_TOWARD -> { sel = maxOf(0, sel - 1); renderLauncher(); buzz(20) }
            Gesture.ROLL_RIGHT, Gesture.WAVE1 -> launchSelected()
            Gesture.ROLL_LEFT, Gesture.HOLD, Gesture.WAVE2 -> { closeLauncher(); buzz() }
            else -> {}
        }
    }

    private fun launchSelected() {
        val pkg = apps.getOrNull(sel)?.second
        closeLauncher()
        if (pkg == null) return
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        buzz(70)
    }

    private fun closeLauncher() {
        launcherView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        launcherView = null
    }
}
