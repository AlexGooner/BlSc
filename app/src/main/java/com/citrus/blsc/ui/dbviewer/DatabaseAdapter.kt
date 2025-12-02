package com.citrus.blsc.ui.dbviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.R

class DatabaseAdapter(private val data: List<Map<String, Any>>) :
    RecyclerView.Adapter<DatabaseAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_database_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = data[position]

        val rowText = StringBuilder()
        row.forEach { (key, value) ->
            rowText.append("$key: $value\n")
        }

        holder.tvRow.text = rowText.toString()
    }

    override fun getItemCount(): Int = data.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRow: TextView = itemView.findViewById(R.id.tvRow)
    }
}