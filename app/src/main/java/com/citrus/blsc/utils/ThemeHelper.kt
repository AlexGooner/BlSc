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
    
    private const val PREF_NAME = "animation_preferences"
    private const val KEY_ANIMATION_TYPE = "animation_type"
    
    private fun getAnimationType(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ANIMATION_TYPE, "slide") ?: "slide"
    }
    
    fun startActivityWithAnimation(activity: Activity, intent: Intent) {
        val animationType = getAnimationType(activity)
        activity.startActivity(intent)
        
        when (animationType) {
            "slide" -> activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            "fade" -> activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            "scale" -> activity.overridePendingTransition(R.anim.scale_in, R.anim.scale_out)
            else -> activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
    
    fun finishActivityWithAnimation(activity: Activity) {
        val animationType = getAnimationType(activity)
        activity.finish()
        
        when (animationType) {
            "slide" -> activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            "fade" -> activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            "scale" -> activity.overridePendingTransition(R.anim.scale_in, R.anim.scale_out)
            else -> activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    
    fun finishActivityWithSlideAnimation(activity: Activity) {
        activity.finish()
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
