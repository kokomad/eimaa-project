package com.khader.gesturecontrol

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Prefs {
    const val SCROLL = "scroll"
    const val VOLUME = "volume"
    const val MEDIA = "media"
    const val LAUNCHER = "launcher"
    const val SHAKE = "shake"
    const val VIB = "vib"
    const val INVERT = "invert"
    const val SENS = "sens"
    const val FAVS = "favs"
    const val POSX = "px"
    const val POSY = "py"

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("gc", Context.MODE_PRIVATE)
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun favs(c: Context): Set<String> = sp(c).getStringSet(FAVS, emptySet()) ?: emptySet()
    fun setFavs(c: Context, s: Set<String>) = sp(c).edit().putStringSet(FAVS, s).apply()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

@Suppress("DEPRECATION")
fun Context.buzz(ms: Long = 40) {
    if (!Prefs.bool(this, Prefs.VIB, true)) return
    val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}
