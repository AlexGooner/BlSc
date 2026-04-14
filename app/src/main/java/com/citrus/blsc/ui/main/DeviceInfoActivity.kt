package com.citrus.blsc.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.citrus.blsc.R
import com.citrus.blsc.data.model.SearchHistoryItem
import com.citrus.blsc.databinding.ActivityDeviceInfoBinding
import com.citrus.blsc.ui.fav.FavActivity
import com.citrus.blsc.ui.fav.FavViewModel
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import com.google.android.material.R as MaterialR
import com.google.android.material.snackbar.Snackbar

class DeviceInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceInfoBinding
    private var allCoordsExpanded = false
    private var weekCoordsExpanded = false

    private val macAddress: String by lazy {
        intent.getStringExtra(EXTRA_DEVICE_MAC)?.trim().orEmpty()
    }

    private val viewModel: DeviceInfoViewModel by viewModels {
        DeviceInfoViewModel.Factory(application, macAddress)
    }

    private val favViewModel: FavViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        if (macAddress.isEmpty()) {
            finish()
            return
        }

        binding = ActivityDeviceInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.headerAllCoords.setOnClickListener {
            allCoordsExpanded = !allCoordsExpanded
            updateExpandable(
                expanded = allCoordsExpanded,
                container = binding.containerAllCoords,
                chevron = binding.chevronAll,
            )
        }

        binding.headerWeekCoords.setOnClickListener {
            weekCoordsExpanded = !weekCoordsExpanded
            updateExpandable(
                expanded = weekCoordsExpanded,
                container = binding.containerWeekCoords,
                chevron = binding.chevronWeek,
            )
        }

        binding.nameValue.text =
            intent.getStringExtra(EXTRA_DEVICE_NAME)?.ifBlank { null }
                ?: getString(R.string.unknown_device)
        binding.macValue.text = macAddress
        binding.categoryValue.text =
            intent.getStringExtra(EXTRA_DEVICE_CATEGORY)?.ifBlank { null }
                ?: getString(R.string.unknown_device)

        binding.chevronAll.rotation = COLLAPSED_ROTATION
        binding.chevronWeek.rotation = COLLAPSED_ROTATION

        favViewModel.favItems.observe(this) { items ->
            applyFavouriteButtonState(
                items.orEmpty().any { it.macAddress == macAddress },
            )
        }

        binding.addToFavouritesButton.setOnClickListener {
            if (!binding.addToFavouritesButton.isEnabled) return@setOnClickListener
            val name = binding.nameValue.text.toString()
            val intent = Intent(this, FavActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra("name", name)
                putExtra("macAddress", macAddress)
            }
            AnimationHelper.startActivityWithAnimation(this, intent)
            finish()
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                DeviceInfoViewModel.DeviceInfoState.Loading -> {
                    binding.progress.isVisible = true
                    binding.scrollContent.isVisible = false
                }

                is DeviceInfoViewModel.DeviceInfoState.Ready -> {
                    binding.progress.isVisible = false
                    binding.scrollContent.isVisible = true
                    binding.totalCountValue.text = state.totalDetections.toString()
                    binding.weekCountValue.text = state.detectionsLast7Days.toString()
                    fillCoordinateContainer(binding.containerAllCoords, state.allDiscoveries)
                    fillCoordinateContainer(binding.containerWeekCoords, state.weekDiscoveries)
                }

                is DeviceInfoViewModel.DeviceInfoState.Error -> {
                    binding.progress.isVisible = false
                    binding.scrollContent.isVisible = true
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        favViewModel.reloadFromStorage()
    }

    private fun applyFavouriteButtonState(alreadyInFavourites: Boolean) {
        binding.addToFavouritesButton.isEnabled = !alreadyInFavourites
        binding.addToFavouritesButton.text = if (alreadyInFavourites) {
            getString(R.string.device_info_already_in_favourites)
        } else {
            getString(R.string.device_info_add_to_favourites_btn)
        }
    }

    private fun updateExpandable(
        expanded: Boolean,
        container: LinearLayout,
        chevron: View,
    ) {
        container.isVisible = expanded
        chevron.rotation = if (expanded) EXPANDED_ROTATION else COLLAPSED_ROTATION
    }

    private fun fillCoordinateContainer(container: LinearLayout, items: List<SearchHistoryItem>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(
                TextView(this).apply {
                    setText(R.string.device_info_no_records)
                    setPadding(0, 8, 0, 8)
                    TextViewCompat.setTextAppearance(
                        this,
                        MaterialR.style.TextAppearance_Material3_BodyMedium,
                    )
                }
            )
            return
        }
        val padV = resources.getDimensionPixelSize(R.dimen.device_info_coord_padding_vertical)
        for (item in items) {
            val line = TextView(this).apply {
                text = formatCoordinateLine(item)
                setPadding(0, padV, 0, padV)
                TextViewCompat.setTextAppearance(
                    this,
                    MaterialR.style.TextAppearance_Material3_BodyMedium,
                )
            }
            container.addView(line)
        }
    }

    private fun formatCoordinateLine(item: SearchHistoryItem): String {
        val whenStr = item.getFormattedDate()
        val coords = if (item.latitude != null && item.longitude != null) {
            "${item.latitude}, ${item.longitude}"
        } else {
            getString(R.string.coordinates_unavailable)
        }
        return getString(R.string.device_info_coord_line, whenStr, coords)
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_DEVICE_MAC = "extra_device_mac"
        const val EXTRA_DEVICE_CATEGORY = "extra_device_category"

        private const val COLLAPSED_ROTATION = -90f
        private const val EXPANDED_ROTATION = 0f
    }
}
