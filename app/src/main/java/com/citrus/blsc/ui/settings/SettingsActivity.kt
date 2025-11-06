package com.citrus.blsc.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.citrus.blsc.R
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var animationRadioGroup: RadioGroup
    private lateinit var backButton: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupThemeSelection()
        setupAnimationSelection()
        setupBackButton()
    }

    private fun initViews() {
        themeRadioGroup = findViewById(R.id.theme_radio_group)
        animationRadioGroup = findViewById(R.id.animation_radio_group)
        backButton = findViewById(R.id.back_button)
    }

    private fun setupThemeSelection() {
        val currentTheme = ThemeHelper.getThemeMode(this)

        when (currentTheme) {
            ThemeHelper.THEME_LIGHT -> findViewById<RadioButton>(R.id.radio_light).isChecked = true
            ThemeHelper.THEME_DARK -> findViewById<RadioButton>(R.id.radio_dark).isChecked = true
            ThemeHelper.THEME_SYSTEM -> findViewById<RadioButton>(R.id.radio_system).isChecked =
                true
        }

        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val themeMode = when (checkedId) {
                R.id.radio_light -> ThemeHelper.THEME_LIGHT
                R.id.radio_dark -> ThemeHelper.THEME_DARK
                R.id.radio_system -> ThemeHelper.THEME_SYSTEM
                else -> ThemeHelper.THEME_SYSTEM
            }
            ThemeHelper.setThemeMode(this, themeMode)
            recreate()
        }
    }

    private fun setupAnimationSelection() {
        val prefs = getSharedPreferences("animation_preferences", Context.MODE_PRIVATE)
        val currentAnimation = prefs.getString("animation_type", "slide") ?: "slide"

        when (currentAnimation) {
            "slide" -> findViewById<RadioButton>(R.id.radio_slide).isChecked = true
            "fade" -> findViewById<RadioButton>(R.id.radio_fade).isChecked = true
            "scale" -> findViewById<RadioButton>(R.id.radio_scale).isChecked = true
        }

        animationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val animationType = when (checkedId) {
                R.id.radio_slide -> "slide"
                R.id.radio_fade -> "fade"
                R.id.radio_scale -> "scale"
                else -> "slide"
            }
            prefs.edit().putString("animation_type", animationType).apply()
        }
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressed()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AnimationHelper.finishActivityWithSlideAnimation(this)
    }
}
