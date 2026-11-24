package com.khader.gesturecontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val cBg = Color.parseColor("#0E1420")
    private val cCard = Color.parseColor("#172033")
    private val cAccent = Color.parseColor("#22D3EE")
    private val cOk = Color.parseColor("#34D399")
    private val cBad = Color.parseColor("#F87171")
    private val cTxt = Color.parseColor("#E6EDF7")
    private val cSub = Color.parseColor("#93A4BD")

    private val perms = mutableListOf<Triple<TextView, String, () -> Boolean>>()
    private lateinit var startBtn: Button
    private lateinit var sensorsTv: TextView
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val scroll = ScrollView(this).apply { setBackgroundColor(cBg) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }
        scroll.addView(col)

        col.addView(text("✋ إيماءة", 30f, cAccent, true))
        col.addView(text("تحكّم بشاشة جوالك بالإيماءات دون لمسها", 14f, cSub))

        // الصلاحيات
        val pc = card()
        pc.addView(text("الصلاحيات المطلوبة", 17f, cTxt, true))
        addPerm(pc, "الظهور فوق التطبيقات (الزر العائم)",
            { Settings.canDrawOverlays(this) },
            { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        addPerm(pc, "خدمة إمكانية الوصول (تنفيذ التمرير والأوامر)",
            { accEnabled() },
            {
                Toast.makeText(this, "اختر «إيماءة» ثم فعّلها", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        if (Build.VERSION.SDK_INT >= 33) {
            addPerm(pc, "الإشعارات",
                { checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED },
                { requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1) })
        }
        addPerm(pc, "استثناء من توفير البطارية (ليعمل في الخلفية)",
            { (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) },
            { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })
        pc.addView(text(
            "ملاحظة: إن كانت خيارات إمكانية الوصول رمادية، افتح معلومات التطبيق ← ⋮ ← «السماح بالإعدادات المقيّدة» ثم أعد المحاولة.",
            12f, cSub
        ))
        col.addView(pc)

        // التشغيل
        startBtn = button("تشغيل الزر العائم", cAccent) { toggleService() }
        col.addView(startBtn)

        // الميزات
        val fc = card()
        fc.addView(text("الميزات", 17f, cTxt, true))
        fc.addView(switchRow("التمرير بالإمالة", "إمالة سريعة للجوال: أعلى/أسفل", Prefs.SCROLL, true))
        fc.addView(switchRow("التحكم بالصوت", "لفّة سريعة يمين/يسار", Prefs.VOLUME, true))
        fc.addView(switchRow("لوّحة اليد (مستشعر القرب)", "تشغيل/إيقاف الفيديو، رجوع، الرئيسية", Prefs.MEDIA, true))
        fc.addView(switchRow("قائمة التطبيقات", "تغطية مطوّلة لمستشعر القرب", Prefs.LAUNCHER, true))
        fc.addView(switchRow("هزّتان = الرئيسية", "هزّ الجوال مرتين بسرعة", Prefs.SHAKE, false))
        fc.addView(switchRow("اهتزاز تأكيدي", "اهتزاز خفيف عند كل أمر", Prefs.VIB, true))
        fc.addView(switchRow("عكس الاتجاهات", "إن كانت الحركة معكوسة على جوالك", Prefs.INVERT, false))
        fc.addView(text("الحساسية", 15f, cTxt).apply { setPadding(0, dp(12), 0, 0) })
        fc.addView(SeekBar(this).apply {
            max = 9
            progress = Prefs.int(this@MainActivity, Prefs.SENS, 5) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    Prefs.setInt(this@MainActivity, Prefs.SENS, p + 1)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        fc.addView(text("يسار = أصعب (أقل أخطاء)   |   يمين = أسهل", 11f, cSub))
        fc.addView(button("اختيار تطبيقاتي المفضلة للقائمة", cCard) { pickFavorites() }.apply {
            setTextColor(cAccent)
        })
        col.addView(fc)

        // الدليل
        val gc = card()
        gc.addView(text("كيف تتحكم؟", 17f, cTxt, true))
        gc.addView(text(
            "اضغط الزر العائم ✋ لتفعيل الإيماءات (يصبح أخضر) وأعد الضغط للإيقاف. اسحبه لتحريكه.\n\n" +
            "• إمالة أعلى الجوال بعيداً عنك بحركة سريعة ← تمرير للأسفل\n" +
            "• إمالته نحوك ← تمرير للأعلى\n" +
            "• لفّة سريعة لليمين ← رفع الصوت، لليسار ← خفض الصوت\n" +
            "• غطِّ مستشعر القرب (أعلى الشاشة قرب السماعة) بيدك مرة ← تشغيل/إيقاف الفيديو\n" +
            "• مرتان ← رجوع، ثلاث مرات ← الرئيسية\n" +
            "• تغطية مطوّلة (ثانية) ← قائمة التطبيقات: أمِل للتنقل، لفّة يمين للفتح، يسار للإغلاق",
            14f, cSub
        ).apply { setLineSpacing(0f, 1.2f) })
        col.addView(gc)

        // المستشعرات
        val sc = card()
        sc.addView(text("حالة المستشعرات", 17f, cTxt, true))
        sensorsTv = text("", 14f, cSub)
        sc.addView(sensorsTv)
        col.addView(sc)

        // الاعتماد
        val cc = card()
        cc.addView(text("تطوير: خضر أبو صالحه", 16f, cTxt, true))
        cc.addView(text("mankhader@gmail.com", 14f, cAccent).apply {
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:mankhader@gmail.com")))
            }
        })
        col.addView(cc)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        for ((t, title, check) in perms) {
            val ok = check()
            t.text = if (ok) "✅  $title" else "⚠️  $title — اضغط للمنح"
            t.setTextColor(if (ok) cOk else cBad)
        }
        startBtn.text = if (FloatingService.running) "إيقاف الزر العائم" else "تشغيل الزر العائم"
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        fun s(name: String, type: Int) =
            (if (sm.getDefaultSensor(type) != null) "✅ " else "❌ ") + name
        sensorsTv.text = listOf(
            s("الجيروسكوب (التمرير والصوت)", Sensor.TYPE_GYROSCOPE),
            s("مستشعر القرب (تلويح اليد)", Sensor.TYPE_PROXIMITY),
            s("التسارع الخطي (الهزّ)", Sensor.TYPE_LINEAR_ACCELERATION)
        ).joinToString("\n")
    }

    private fun accEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains(packageName) && s.contains("GestureAccessibilityService")
    }

    private fun toggleService() {
        if (FloatingService.running) {
            stopService(Intent(this, FloatingService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "امنح إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return
            }
            startForegroundService(Intent(this, FloatingService::class.java))
            if (!accEnabled()) {
                Toast.makeText(this, "تذكير: فعّل خدمة إمكانية الوصول ليعمل التحكم", Toast.LENGTH_LONG).show()
            }
        }
        ui.postDelayed({ refresh() }, 500)
    }

    @Suppress("DEPRECATION")
    private fun pickFavorites() {
        val pm = packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first }
        val favs = Prefs.favs(this)
        val checked = BooleanArray(list.size) { list[it].second in favs }
        AlertDialog.Builder(this)
            .setTitle("التطبيقات في القائمة (فارغ = الكل)")
            .setMultiChoiceItems(list.map { it.first }.toTypedArray(), checked) { _, idx, c -> checked[idx] = c }
            .setPositiveButton("حفظ") { _, _ ->
                Prefs.setFavs(this, list.indices.filter { checked[it] }.map { list[it].second }.toSet())
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- أدوات الواجهة ----------

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(cCard)
            cornerRadius = dp(18).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(Color.parseColor("#06222B"))
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
    }

    private fun addPerm(parent: LinearLayout, title: String, check: () -> Boolean, grant: () -> Unit) {
        val t = text("", 15f, cTxt).apply {
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { if (!check()) grant() }
        }
        parent.addView(t)
        perms.add(Triple(t, title, check))
    }

    private fun switchRow(title: String, desc: String, key: String, def: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tx.addView(text(title, 15f, cTxt))
        tx.addView(text(desc, 12f, cSub))
        row.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = Prefs.bool(this@MainActivity, key, def)
            setOnCheckedChangeListener { _, c -> Prefs.setBool(this@MainActivity, key, c) }
        })
        return row
    }
}
