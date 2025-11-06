package com.citrus.blsc.ui.history

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.R
import com.citrus.blsc.data.model.SearchHistoryItem
import com.citrus.blsc.utils.UIAnimationHelper

class HistoryAdapter(
    private var historyItems: MutableList<SearchHistoryItem>,
    private val listener: OnHistoryItemActionListener,
    private val context: Context
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    interface OnHistoryItemActionListener {
        fun onAddToFavourites(item: SearchHistoryItem)
        fun onRemoveFromFavourites(item: SearchHistoryItem)
        fun onDeleteItem(item: SearchHistoryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        UIAnimationHelper.animateCardAppear(holder.itemView, (position * 50).toLong())

        val historyItem = historyItems[position]

        holder.deviceNameTextView.text =
            historyItem.deviceName.ifEmpty { context.getString(R.string.unknown_device) }
        holder.macAddressTextView.text = historyItem.macAddress
        holder.rssiTextView.text = "${historyItem.rssi} dBm"
        holder.timestampTextView.text = historyItem.getFormattedDate()

        holder.favouriteIcon.visibility = if (historyItem.isFavourite) View.VISIBLE else View.GONE


        if (historyItem.latitude != null && historyItem.longitude != null) {
            holder.locationTextView.text = "${String.format("%.4f", historyItem.latitude)}, ${
                String.format(
                    "%.4f",
                    historyItem.longitude
                )
            }"
        } else {
            holder.locationTextView.text = context.getString(R.string.coordinates_unavailable)
        }

        holder.deleteButton.setOnClickListener {
            UIAnimationHelper.animateShake(holder.deleteButton)
            listener.onDeleteItem(historyItem)
        }
    }

    override fun getItemCount(): Int = historyItems.size

    fun updateItems(newItems: List<SearchHistoryItem>) {
        historyItems = newItems as MutableList<SearchHistoryItem>
        notifyDataSetChanged()
    }


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.mac_address_text_view)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssi_text_view)
        val locationTextView: TextView = itemView.findViewById(R.id.location_text_view)
        val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text_view)
        val favouriteIcon: ImageView = itemView.findViewById(R.id.favourite_icon)
        val deleteButton: Button = itemView.findViewById(R.id.delete_button)
    }
}
