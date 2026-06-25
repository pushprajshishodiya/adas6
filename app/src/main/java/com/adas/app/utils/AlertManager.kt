package com.adas.app.utils

import android.content.Context
import android.os.*
import com.adas.app.detection.WarningLevel

class AlertManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private var lastLevel = WarningLevel.SAFE
    private var lastTimeMs = 0L
    private val cooldowns = mapOf(WarningLevel.DANGER to 600L, WarningLevel.WARNING to 1200L, WarningLevel.CAUTION to 2500L)

    fun alert(level: WarningLevel) {
        if (level == WarningLevel.SAFE) return
        val now = System.currentTimeMillis()
        val cd = cooldowns[level] ?: Long.MAX_VALUE
        if (level == lastLevel && now - lastTimeMs < cd) return
        lastLevel = level; lastTimeMs = now
        val pattern = when(level) {
            WarningLevel.DANGER  -> longArrayOf(0,100,60,100,60,200)
            WarningLevel.WARNING -> longArrayOf(0,80,80,80)
            else                 -> longArrayOf(0,50)
        }
        val amps = when(level) {
            WarningLevel.DANGER  -> intArrayOf(0,255,0,255,0,255)
            WarningLevel.WARNING -> intArrayOf(0,200,0,200)
            else                 -> intArrayOf(0,140)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amps, -1))
        } else { @Suppress("DEPRECATION") vibrator.vibrate(pattern, -1) }
    }
    fun release() {}
}
