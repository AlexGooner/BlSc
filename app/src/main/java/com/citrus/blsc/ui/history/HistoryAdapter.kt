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
import java.util.Locale

class HistoryAdapter(
    historyItems: List<SearchHistoryItem>,
    private val listener: OnHistoryItemActionListener,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnHistoryItemActionListener {
        fun onAddToFavourites(item: SearchHistoryItem)
        fun onRemoveFromFavourites(item: SearchHistoryItem)
        fun onDeleteItem(item: SearchHistoryItem)
    }

    private var sourceItems: List<SearchHistoryItem> = historyItems.toList()
    private var groupByDetections = false
    private val expandedKeys = mutableSetOf<String>()
    private val groupedItems = HashMap<String, List<SearchHistoryItem>>()
    private val displayRows = ArrayList<HistoryRow>()

    private val density = context.resources.displayMetrics.density
    private val nestedMarginPx = (20 * density).toInt()
    private val defaultMarginPx = (4 * density).toInt()

    init {
        rebuildDisplay()
    }

    fun updateItems(newItems: List<SearchHistoryItem>) {
        sourceItems = newItems.toList()
        rebuildDisplay()
        notifyDataSetChanged()
    }

    fun setGroupByDetections(enabled: Boolean) {
        if (groupByDetections == enabled) return
        groupByDetections = enabled
        if (!enabled) expandedKeys.clear()
        rebuildDisplay()
        notifyDataSetChanged()
    }

    fun toggleGroup(key: String) {
        val index = displayRows.indexOfFirst { (it as? HistoryRow.Group)?.key == key }
        if (index == -1) return
        val group = displayRows[index] as HistoryRow.Group
        if (group.expanded) {
            val childCount = followingChildCount(index)
            expandedKeys.remove(key)
            displayRows[index] = group.copy(expanded = false)
            if (childCount > 0) {
                displayRows.subList(index + 1, index + 1 + childCount).clear()
            }
            notifyItemChanged(index, PAYLOAD_EXPANDED)
            if (childCount > 0) notifyItemRangeRemoved(index + 1, childCount)
        } else {
            val children = groupedItems[key].orEmpty()
            expandedKeys.add(key)
            displayRows[index] = group.copy(expanded = true)
            if (children.isNotEmpty()) {
                displayRows.addAll(index + 1, children.map { HistoryRow.Item(it, nested = true) })
            }
            notifyItemChanged(index, PAYLOAD_EXPANDED)
            if (children.isNotEmpty()) notifyItemRangeInserted(index + 1, children.size)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (displayRows[position]) {
            is HistoryRow.Group -> TYPE_GROUP
            is HistoryRow.Item -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP) {
            GroupViewHolder(inflater.inflate(R.layout.history_group_item, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.history_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = displayRows[position]) {
            is HistoryRow.Item -> bindItem(holder as ItemViewHolder, row, position)
            is HistoryRow.Group -> bindGroup(holder as GroupViewHolder, row)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val group = displayRows.getOrNull(position) as? HistoryRow.Group
        if (payloads.contains(PAYLOAD_EXPANDED) && holder is GroupViewHolder && group != null) {
            bindExpandedIndicator(holder, group)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = displayRows.size

    @SuppressLint("SetTextI18n")
    private fun bindItem(holder: ItemViewHolder, row: HistoryRow.Item, position: Int) {
        val historyItem = row.item
        val delay = if (row.nested) {
            ((position % 8) * 40).toLong()
        } else {
            (position * 50).toLong()
        }
        UIAnimationHelper.animateCardAppear(holder.itemView, delay)
        applyNesting(holder.itemView, row.nested)

        holder.deviceNameTextView.text = displayName(historyItem.deviceName)
        holder.macAddressTextView.text = historyItem.macAddress
        holder.deviceTypeTextView.text = historyItem.deviceType
        holder.rssiTextView.text = "${historyItem.rssi} dBm"
        holder.timestampTextView.text = historyItem.getFormattedDate()
        holder.favouriteIcon.visibility = if (historyItem.isFavourite) View.VISIBLE else View.GONE
        holder.locationTextView.text = formatLocation(historyItem)

        holder.deleteButton.setOnClickListener {
            UIAnimationHelper.animateShake(holder.deleteButton)
            listener.onDeleteItem(historyItem)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindGroup(holder: GroupViewHolder, group: HistoryRow.Group) {
        val latest = group.latest
        holder.deviceNameTextView.text = displayName(group.displayName)
        holder.macAddressTextView.text = latest.macAddress
        holder.deviceTypeTextView.text = latest.deviceType
        holder.detectionCountTextView.text = context.resources.getQuantityString(
            R.plurals.detection_count,
            group.count,
            group.count
        )
        holder.rssiTextView.text = "${latest.rssi} dBm"
        holder.locationTextView.text = formatLocation(latest)
        holder.timestampTextView.text = context.getString(
            R.string.last_detection,
            latest.getFormattedDate()
        )
        holder.favouriteIcon.visibility = if (group.hasFavourite) View.VISIBLE else View.GONE
        bindExpandedIndicator(holder, group)
        holder.itemView.setOnClickListener { toggleGroup(group.key) }
    }

    private fun bindExpandedIndicator(holder: GroupViewHolder, group: HistoryRow.Group) {
        holder.expandIcon.rotation = if (group.expanded) 180f else 0f
        holder.expandIcon.contentDescription = context.getString(
            if (group.expanded) R.string.collapse_detections_group else R.string.expand_detections_group
        )
    }

    private fun rebuildDisplay() {
        displayRows.clear()
        groupedItems.clear()
        if (!groupByDetections) {
            sourceItems.forEach { displayRows.add(HistoryRow.Item(it, nested = false)) }
            return
        }

        val buckets = sourceItems
            .groupBy { deviceKey(it) }
            .map { (key, items) -> DeviceBucket(key, items.sortedByDescending { it.timestamp }) }
            .sortedWith(
                compareByDescending<DeviceBucket> { it.items.size }
                    .thenByDescending { it.items.first().timestamp }
            )

        expandedKeys.retainAll(buckets.map { it.key }.toSet())

        for (bucket in buckets) {
            val latest = bucket.items.first()
            val expanded = expandedKeys.contains(bucket.key)
            displayRows.add(
                HistoryRow.Group(
                    key = bucket.key,
                    displayName = bucket.items.firstOrNull { it.deviceName.isNotBlank() }?.deviceName.orEmpty(),
                    count = bucket.items.size,
                    expanded = expanded,
                    latest = latest,
                    hasFavourite = bucket.items.any { it.isFavourite }
                )
            )
            groupedItems[bucket.key] = bucket.items
            if (expanded) {
                bucket.items.forEach { displayRows.add(HistoryRow.Item(it, nested = true)) }
            }
        }
    }

    private fun followingChildCount(headerIndex: Int): Int {
        var count = 0
        var index = headerIndex + 1
        while (index < displayRows.size) {
            val row = displayRows[index]
            if (row is HistoryRow.Item && row.nested) {
                count++
                index++
            } else {
                break
            }
        }
        return count
    }

    private fun deviceKey(item: SearchHistoryItem): String {
        val mac = item.macAddress.trim()
        return if (mac.isNotEmpty()) mac.uppercase(Locale.ROOT) else "id:${item.id}"
    }

    private fun displayName(name: String): String {
        return name.ifEmpty { context.getString(R.string.unknown_device) }
    }

    private fun formatLocation(item: SearchHistoryItem): String {
        val latitude = item.latitude
        val longitude = item.longitude
        return if (latitude != null && longitude != null) {
            "${String.format(Locale.getDefault(), "%.4f", latitude)}, ${
                String.format(Locale.getDefault(), "%.4f", longitude)
            }"
        } else {
            context.getString(R.string.coordinates_unavailable)
        }
    }

    private fun applyNesting(view: View, nested: Boolean) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.marginStart = if (nested) nestedMarginPx else defaultMarginPx
        view.layoutParams = params
    }

    private sealed class HistoryRow {
        data class Item(val item: SearchHistoryItem, val nested: Boolean) : HistoryRow()
        data class Group(
            val key: String,
            val displayName: String,
            val count: Int,
            val expanded: Boolean,
            val latest: SearchHistoryItem,
            val hasFavourite: Boolean
        ) : HistoryRow()
    }

    private data class DeviceBucket(
        val key: String,
        val items: List<SearchHistoryItem>
    )

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.mac_address_text_view)
        val deviceTypeTextView: TextView = itemView.findViewById(R.id.device_type_text_view)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssi_text_view)
        val locationTextView: TextView = itemView.findViewById(R.id.location_text_view)
        val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text_view)
        val favouriteIcon: ImageView = itemView.findViewById(R.id.favourite_icon)
        val deleteButton: Button = itemView.findViewById(R.id.delete_button)
    }

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceNameTextView: TextView = itemView.findViewById(R.id.device_name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.mac_address_text_view)
        val deviceTypeTextView: TextView = itemView.findViewById(R.id.device_type_text_view)
        val detectionCountTextView: TextView = itemView.findViewById(R.id.detection_count_text_view)
        val rssiTextView: TextView = itemView.findViewById(R.id.rssi_text_view)
        val locationTextView: TextView = itemView.findViewById(R.id.location_text_view)
        val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text_view)
        val favouriteIcon: ImageView = itemView.findViewById(R.id.favourite_icon)
        val expandIcon: ImageView = itemView.findViewById(R.id.expand_icon)
    }

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_GROUP = 1
        private const val PAYLOAD_EXPANDED = "expanded"
    }
}
