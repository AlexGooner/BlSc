package com.citrus.blsc.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.citrus.blsc.R

object ThemeHelper {

    private const val PREF_NAME = "theme_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM = "system"

    fun getThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
    }

    fun setThemeMode(context: Context, themeMode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, themeMode).apply()

        when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun applyTheme(context: Context) {
        val themeMode = getThemeMode(context)
        setThemeMode(context, themeMode)
    }
}

object AnimationHelper {

    // Убрали настройку типа анимации, оставляем только slide анимации по умолчанию

    fun startActivityWithAnimation(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    fun finishActivityWithAnimation(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    fun finishActivityWithSlideAnimation(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}