package com.citrus.blsc.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.citrus.blsc.R
import com.citrus.blsc.ui.dbviewer.DatabaseViewerActivity
import com.citrus.blsc.ui.map.OfflineMapActivity
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var backButton: Button
    private lateinit var mapsButton: Button
    private lateinit var btnDatabase: Button

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupThemeSelection()
        setupMapsButton()
        setupBackButton()
        setupDatabaseButton()
    }

    private fun initViews() {
        themeRadioGroup = findViewById(R.id.theme_radio_group)
        backButton = findViewById(R.id.back_button)
        mapsButton = findViewById(R.id.maps_button)
        btnDatabase = findViewById(R.id.btnDatabase)
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

    private fun setupDatabaseButton(){
        btnDatabase.setOnClickListener {
            val intent = Intent(this, DatabaseViewerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupMapsButton() {
        mapsButton.setOnClickListener {
            val intent = Intent(this, OfflineMapActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressed()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AnimationHelper.finishActivityWithAnimation(this)
    }
}
