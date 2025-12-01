package com.citrus.blsc.ui.fav

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.OnFavItemActionListener
import com.citrus.blsc.R
import com.citrus.blsc.ui.map.OfflineMapViewerActivity
import com.citrus.blsc.utils.UIAnimationHelper
import com.citrus.blsc.utils.VibrationHelper

class FavAdapter(
    var favItems: MutableList<FavItem>,
    private val listener: OnFavItemActionListener,
    private val context: Context
) : RecyclerView.Adapter<FavAdapter.ViewHolder>() {

    companion object {
        private const val ACTION_DELETE = 0
        private const val ACTION_MAP = 1
        private const val ACTION_EDIT = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        UIAnimationHelper.animateCardAppear(holder.itemView, (position * 100).toLong())

        val favItem = favItems[position]
        holder.nameTextView.text = favItem.name
        holder.macAddressTextView.text = favItem.macAddress

        if (favItem.area == null) {
            holder.areaTextView.text = "Unknown"
        } else {
            holder.areaTextView.text = favItem.area
        }


        val vibrationText = VibrationHelper.getVibrationDisplayName(context, favItem.vibrateLong)
        holder.vibrationTextView.text = vibrationText

        holder.buttonInfo.setOnClickListener {
            UIAnimationHelper.animateButtonPress(holder.buttonInfo)
            val dialog = AlertDialog.Builder(holder.itemView.context)
            dialog.setTitle("Выберите действие")
            dialog.setItems(arrayOf("Удалить", "Посмотреть на карте", "Изменить")) { _, which ->
                when (which) {
                    ACTION_DELETE -> {
                        listener.removeFavItem(favItem)
                        notifyItemRemoved(position)
                    }

                    ACTION_MAP -> {
                        val intent = Intent(context, OfflineMapViewerActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        val macAddress = holder.macAddressTextView.text.toString()
                        intent.putExtra("mac", macAddress)
                        context.startActivity(intent)
                    }

                    ACTION_EDIT -> {
                        val dialog = AlertDialog.Builder(holder.itemView.context)
                        val view = LayoutInflater.from(holder.itemView.context)
                            .inflate(R.layout.add_device_dialog, null)
                        dialog.setView(view)

                        val nameEditText =
                            view.findViewById<android.widget.EditText>(R.id.device_name_edit_text)
                        val macEditText =
                            view.findViewById<android.widget.EditText>(R.id.mac_address_edit_text)
                        val areaEditText =
                            view.findViewById<android.widget.EditText>(R.id.area_edit_text)
                        val vibrationSpinner =
                            view.findViewById<Spinner>(R.id.vibration_duration_spinner)


                        val vibrationOptions = arrayOf(
                            context.getString(R.string.vibration_short),
                            context.getString(R.string.vibration_medium),
                            context.getString(R.string.vibration_long),
                            context.getString(R.string.vibration_custom)
                        )
                        val vibrationValues = arrayOf(
                            VibrationHelper.VIBRATION_SHORT,
                            VibrationHelper.VIBRATION_MEDIUM,
                            VibrationHelper.VIBRATION_LONG,
                            VibrationHelper.VIBRATION_CUSTOM
                        )

                        val adapter = ArrayAdapter(
                            holder.itemView.context,
                            android.R.layout.simple_spinner_item,
                            vibrationOptions
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        vibrationSpinner.adapter = adapter


                        nameEditText.setText(favItem.name)
                        macEditText.setText(favItem.macAddress)
                        areaEditText.setText(favItem.area ?: "")


                        val currentVibrationIndex = vibrationValues.indexOf(favItem.vibrateLong)
                        vibrationSpinner.setSelection(if (currentVibrationIndex >= 0) currentVibrationIndex else 1) // По умолчанию средняя

                        dialog.setPositiveButton("Сохранить") { _, _ ->
                            val updatedName = nameEditText.text.toString()
                            val updatedMac = macEditText.text.toString()
                            val updatedArea = areaEditText.text.toString()
                            val selectedVibrationIndex = vibrationSpinner.selectedItemPosition
                            val updatedVibration = vibrationValues[selectedVibrationIndex]

                            val updatedItem = FavItem(
                                updatedName,
                                updatedMac,
                                favItem.rssi,
                                updatedArea,
                                updatedVibration
                            )

                            favItems[position] = updatedItem
                            notifyItemChanged(position)

                            listener.saveFavItemToPrefs()
                        }

                        dialog.setNegativeButton("Отмена") { _, _ -> }
                        dialog.show()
                    }
                }
            }
            dialog.show()
            true
        }
    }

    override fun getItemCount(): Int {
        return favItems.size
    }

    fun extractMacAddresses(): List<String> {
        return favItems.map { it.macAddress }
    }

    fun removeDuplicates() {
        val uniqueItems = mutableListOf<FavItem>()
        val seenMacAddresses = mutableSetOf<String>()

        for (item in favItems) {
            if (!seenMacAddresses.contains(item.macAddress)) {
                seenMacAddresses.add(item.macAddress)
                uniqueItems.add(item)
            }
        }

        val removedCount = favItems.size - uniqueItems.size
        if (removedCount > 0) {
            favItems.clear()
            favItems.addAll(uniqueItems)
            notifyDataSetChanged()
        }
    }

    fun hasMacAddress(macAddress: String): Boolean {
        return favItems.any { it.macAddress == macAddress }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.fav_name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.fav_mac_address_text_view)
        val areaTextView: TextView = itemView.findViewById(R.id.textView_fav_area)
        val vibrationTextView: TextView = itemView.findViewById(R.id.fav_vibration_text_view)
        val buttonInfo: Button = itemView.findViewById(R.id.buttonFavInfo)
    }
}