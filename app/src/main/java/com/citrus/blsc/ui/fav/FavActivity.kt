package com.citrus.blsc.ui.fav

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.citrus.blsc.OnFavItemActionListener
import com.citrus.blsc.R
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.databinding.ActivityFavouriteBinding
import com.citrus.blsc.ui.main.MainActivity
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import com.citrus.blsc.utils.UIAnimationHelper
import com.citrus.blsc.utils.VibrationHelper


class FavActivity : AppCompatActivity() {

    private lateinit var viewModel: FavViewModel
    private lateinit var recyclerViewAdapter: FavAdapter
    private lateinit var binding: ActivityFavouriteBinding

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[FavViewModel::class.java]

        recyclerViewAdapter = FavAdapter(mutableListOf(), object : OnFavItemActionListener {
            override fun saveFavItemToPrefs() {
                viewModel.saveFavItems(recyclerViewAdapter.favItems)
            }

            override fun removeFavItem(favItem: FavItem) {
                viewModel.removeFavItem(favItem)
                viewModel.getMacs(recyclerViewAdapter, binding.favTv)
            }
        }, this)

        binding.favRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@FavActivity)
            adapter = recyclerViewAdapter
        }


        viewModel.favItems.observe(this) { items ->
            recyclerViewAdapter.favItems = items.toMutableList()
            // Проверяем и удаляем дубликаты при загрузке данных
            recyclerViewAdapter.removeDuplicates()
            recyclerViewAdapter.notifyDataSetChanged()

            viewModel.getMacs(recyclerViewAdapter, binding.favTv)

        }
        val comingDeviceName = intent.getStringExtra("name")
        val comingMacAddress = intent.getStringExtra("macAddress")
        val comingRssi = ""
        val comingArea = ""
        val vibrateLong = VibrationHelper.DEFAULT_VIBRATION
        if (comingDeviceName != null && comingMacAddress != null) {
            val safeVibrateLong = vibrateLong ?: VibrationHelper.DEFAULT_VIBRATION

            viewModel.addFavItem(
                FavItem(
                    comingDeviceName,
                    comingMacAddress,
                    comingRssi,
                    comingArea,
                    safeVibrateLong
                )
            )
        }


        binding.favPlusBtn.setOnClickListener {
            UIAnimationHelper.animateButtonPress(binding.favPlusBtn)
            val dialog = AlertDialog.Builder(this)
            val view = LayoutInflater.from(this).inflate(R.layout.add_device_dialog, null)
            dialog.setView(view)
            val nameEditText = view.findViewById<EditText>(R.id.device_name_edit_text)
            val macEditText = view.findViewById<EditText>(R.id.mac_address_edit_text)
            val areaEditText = view.findViewById<EditText>(R.id.area_edit_text)
            val vibrationSpinner = view.findViewById<Spinner>(R.id.vibration_duration_spinner)

            val vibrationOptions = arrayOf(
                getString(R.string.vibration_short),
                getString(R.string.vibration_medium),
                getString(R.string.vibration_long),
                getString(R.string.vibration_custom)
            )
            val vibrationValues = arrayOf(
                VibrationHelper.VIBRATION_SHORT,
                VibrationHelper.VIBRATION_MEDIUM,
                VibrationHelper.VIBRATION_LONG,
                VibrationHelper.VIBRATION_CUSTOM
            )

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, vibrationOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            vibrationSpinner.adapter = adapter
            vibrationSpinner.setSelection(1) // По умолчанию средняя

            dialog.setPositiveButton("Добавить") { _, _ ->
                val deviceName = nameEditText.text.toString()
                val macAddress = macEditText.text.toString()
                val rssi = ""
                val area = areaEditText.text.toString()
                val selectedVibrationIndex = vibrationSpinner.selectedItemPosition
                val vibrateLong = vibrationValues[selectedVibrationIndex]

                // Проверяем, не существует ли уже элемент с таким MAC-адресом
                if (recyclerViewAdapter.hasMacAddress(macAddress)) {
                    AlertDialog.Builder(this)
                        .setTitle("Дубликат")
                        .setMessage("Устройство с MAC-адресом $macAddress уже существует в списке избранных.")
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                } else {
                    // Убедимся что vibrateLong не null
                    val safeVibrateLong = vibrateLong ?: VibrationHelper.DEFAULT_VIBRATION
                    viewModel.addFavItem(FavItem(deviceName, macAddress, rssi, area, safeVibrateLong))
                }
            }

            dialog.setNegativeButton("Отмена") { _, _ -> }
            dialog.show()
        }

        binding.favClearBtn.setOnClickListener {
            UIAnimationHelper.animateShake(binding.favClearBtn)
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Очистить список избранных устройств")
            alertDialog.setMessage("Вы уверены, что хотите очистить список избранных устройств?")
            alertDialog.setPositiveButton("Очистить") { _, _ ->
                viewModel.clearFavItems()
                viewModel.clearAllFavHistory()
            }
            alertDialog.setNegativeButton("Отмена") { _, _ -> }
            alertDialog.show()
        }

        binding.favToSeacrhBtn.setOnClickListener {
            UIAnimationHelper.animateButtonPress(binding.favToSeacrhBtn)
            val intent = Intent(this, MainActivity::class.java)
            val favouriteMacs = binding.favTv.text.toString()
            val lines = favouriteMacs.split("\n")
            val arrayList = ArrayList<String>()
            arrayList.addAll(lines)
            intent.putStringArrayListExtra("macs", arrayList)

            val vibrationMap =
                recyclerViewAdapter.favItems.associate { it.macAddress to it.vibrateLong }
            intent.putExtra("vibrations", vibrationMap as HashMap<String, String>)

            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            AnimationHelper.startActivityWithAnimation(this, intent)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AnimationHelper.finishActivityWithAnimation(this)
    }
}