package com.citrus.blsc.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi

object VibrationHelper {

    const val VIBRATION_SHORT = "short"
    const val VIBRATION_MEDIUM = "medium"
    const val VIBRATION_LONG = "long"
    const val VIBRATION_CUSTOM = "custom"
    const val DEFAULT_VIBRATION = VIBRATION_MEDIUM

    @RequiresApi(Build.VERSION_CODES.S)
    fun vibrate(context: Context, vibrationType: String = DEFAULT_VIBRATION) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator

        // Используем безопасное значение по умолчанию
        val safeVibrationType = vibrationType ?: DEFAULT_VIBRATION

        val pattern = when (safeVibrationType) {
            VIBRATION_SHORT -> VibrationEffect.createWaveform(longArrayOf(0, 200, 100), -1)
            VIBRATION_MEDIUM -> VibrationEffect.createWaveform(longArrayOf(0, 300, 500, 600), -1)
            VIBRATION_LONG -> VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            VIBRATION_CUSTOM -> VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 100, 50, 100), -1)
            else -> VibrationEffect.createWaveform(longArrayOf(0, 300, 500, 600), -1)
        }
        vibrator.vibrate(pattern)
    }

    fun getVibrationDisplayName(context: Context, vibrationType: String?): String {
        // Обработка null значения
        val safeVibrationType = vibrationType ?: DEFAULT_VIBRATION

        return when (safeVibrationType) {
            VIBRATION_SHORT -> context.getString(com.citrus.blsc.R.string.vibration_short)
            VIBRATION_MEDIUM -> context.getString(com.citrus.blsc.R.string.vibration_medium)
            VIBRATION_LONG -> context.getString(com.citrus.blsc.R.string.vibration_long)
            VIBRATION_CUSTOM -> context.getString(com.citrus.blsc.R.string.vibration_custom)
            else -> context.getString(com.citrus.blsc.R.string.vibration_medium)
        }
    }
}
