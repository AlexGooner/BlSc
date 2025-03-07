package com.citrus.blsc.ui.fav

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.citrus.blsc.OnFavItemActionListener
import com.citrus.blsc.R
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.databinding.ActivityFavouriteBinding
import com.citrus.blsc.ui.main.MainActivity


class FavActivity : AppCompatActivity() {

    private lateinit var viewModel: FavViewModel
    private lateinit var recyclerViewAdapter: FavAdapter
    private lateinit var binding : ActivityFavouriteBinding

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[FavViewModel::class.java]

        recyclerViewAdapter = FavAdapter(mutableListOf(), object : OnFavItemActionListener {
            override fun saveFavItemToPrefs() {
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
            recyclerViewAdapter.notifyDataSetChanged()

            viewModel.getMacs(recyclerViewAdapter, binding.favTv)

        }
        val comingDeviceName = intent.getStringExtra("name")
        val comingMacAddress = intent.getStringExtra("macAddress")
        val comingRssi = ""
        if (comingDeviceName != null && comingMacAddress != null) {
            viewModel.addFavItem(FavItem(comingDeviceName, comingMacAddress, comingRssi))
        }


        binding.favPlusBtn.setOnClickListener {
            // Show dialog to add a new fav item
            val dialog = AlertDialog.Builder(this)
            val view = LayoutInflater.from(this).inflate(R.layout.add_device_dialog, null)
            dialog.setView(view)
            val nameEditText = view.findViewById<EditText>(R.id.device_name_edit_text)
            val macEditText = view.findViewById<EditText>(R.id.mac_address_edit_text)

            dialog.setPositiveButton("Добавить") { _, _ ->
                val deviceName = nameEditText.text.toString()
                val macAddress = macEditText.text.toString()
                val rssi = ""
                viewModel.addFavItem(FavItem(deviceName, macAddress, rssi))
            }

            dialog.setNegativeButton("Отмена") { _, _ -> }
            dialog.show()
        }

        binding.favClearBtn.setOnClickListener {
            val alertDialog = AlertDialog.Builder(this)
            alertDialog.setTitle("Очистить список избранных устройств")
            alertDialog.setMessage("Вы уверены, что хотите очистить список избранных устройств?")
            alertDialog.setPositiveButton("Очистить") { _, _ ->
                viewModel.clearFavItems()
            }
            alertDialog.setNegativeButton("Отмена") { _, _ -> }
            alertDialog.show()
        }

        binding.favToSeacrhBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            val favouriteMacs = binding.favTv.text.toString()
            val lines = favouriteMacs.split("\n")
            val arrayList = ArrayList<String>()
            arrayList.addAll(lines)
            intent.putStringArrayListExtra("macs", arrayList)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
}