package com.citrus.blsc.utils

import android.content.Context

object ProximitySettingsHelper {

    private const val PREF_NAME = "proximity_preferences"
    private const val KEY_THRESHOLD = "proximity_threshold"

    const val DEFAULT_THRESHOLD = 10
    const val MIN_THRESHOLD = 2
    const val MAX_THRESHOLD = 100

    fun getThreshold(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
            .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    fun setThreshold(context: Context, threshold: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_THRESHOLD, threshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD))
            .apply()
    }
}
