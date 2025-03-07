package com.citrus.blsc.ui.fav

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.OnFavItemActionListener
import com.citrus.blsc.R
import com.citrus.blsc.ui.map.MapsActivity

class FavAdapter(
    var favItems: MutableList<FavItem>,
    private val listener: OnFavItemActionListener,
    private val context: Context
) : RecyclerView.Adapter<FavAdapter.ViewHolder>() {




    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val favItem = favItems[position]
        holder.nameTextView.text = favItem.name
        holder.macAddressTextView.text = favItem.macAddress

        holder.itemView.setOnLongClickListener {
            val dialog = AlertDialog.Builder(holder.itemView.context)
            dialog.setTitle("Выберите действие")
            dialog.setItems(arrayOf("Удалить", "Посмотреть на карте")) { _, which ->
                when (which) {
                    0 -> {
                        listener.removeFavItem(favItem)
                        notifyItemRemoved(position)
                    }
                    1 -> {
                        val intent = Intent(context, MapsActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        val macAddress = holder.macAddressTextView.text.toString()
                        intent.putExtra("mac", macAddress)
                        context.startActivity(intent)
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.fav_name_text_view)
        val macAddressTextView: TextView = itemView.findViewById(R.id.fav_mac_address_text_view)
    }
}