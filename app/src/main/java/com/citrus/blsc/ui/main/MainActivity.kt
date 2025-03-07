package com.citrus.blsc.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.citrus.blsc.data.database.AppDatabase
import com.citrus.blsc.data.database.DeviceCoordinate
import com.citrus.blsc.data.model.BluetoothDeviceInfo
import com.citrus.blsc.data.model.FavItem
import com.citrus.blsc.databinding.ActivityMainBinding
import com.citrus.blsc.ui.fav.FavActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayList


class MainActivity() : AppCompatActivity() {

    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.values.all { it }) {
            } else {
                Toast.makeText(this, "Permission is not granted", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: MainAdapter
    private var devices = mutableListOf<BluetoothDeviceInfo>()
    private var job: Job? = null
    private var favouriteMacs: List<String> = emptyList()
    private lateinit var db: AppDatabase

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        binding.lottieAnimation.isVisible = false

        setupRecyclerView()
        observeViewModel()
        check()

        binding.startBtn.setOnClickListener {
            binding.mainTextView.text = viewModel.getLocation(this).toString()

            job = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    viewModel.startScanning(
                        this@MainActivity,
                        binding.mainTextView.text.toString(),
                        binding.mapsTextView.text.toString(),
                        binding.mainTextView,
                        binding.mapsTextView

                    )
                    delay(15000)
                }
            }

            viewModel.startTimer(binding.timerTextView)
            binding.lottieAnimation.isVisible = true


        }

        viewModel.location.observe(this) { location ->
            binding.locationTextView.text = location
        }

        binding.stopBtn.setOnClickListener {
            job?.cancel()
            job = null
            viewModel.stopScanning(this)
            viewModel.stopTimer()
            binding.lottieAnimation.isVisible = false

        }

        binding.favImageView.setOnClickListener {
            val intent = Intent(this, FavActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }


        viewModel.lastDevice.observe(this) { device ->
            device.let {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                }
                if (it != null) {
                    binding.mainTextView.append(
                        "\n" + (it.device.name
                            ?: "unnamed") + "\n" + it.device.address + "\n"
                                + binding.locationTextView.text + "\n"
                                + viewModel.getCurrentTime() + "\n"
                                + it.rssi + " dBm"
                    )
                    binding.mapsTextView.append(
                        it.device.address + "\n" + binding.locationTextView.text + "\n"
                    )

                    db = AppDatabase.getDatabase(this)
                    val textFromMaps = binding.mapsTextView.text.toString()
                    val lines = textFromMaps.split("\n")
                    lifecycleScope.launch {
                        for (i in lines.indices step 2) {
                            if (i + 1 < lines.size) {
                                val mac = lines[i].trim()
                                val coordinates = lines[i + 1].trim().split(" ")
                                if (coordinates.size == 2) {
                                    val latitudeStr = coordinates[0].trim()
                                    val longitudeStr = coordinates[1].trim()// Проверяем, что координаты корректны
                                    val latitude = latitudeStr.toDoubleOrNull()
                                    val longitude = longitudeStr.toDoubleOrNull()
                                    if (latitude != null && longitude != null) {
                                        val deviceCoordinate = DeviceCoordinate(0, mac, latitude, longitude, viewModel.getCurrentTime())
                                        db.deviceCoordinateDao().insert(deviceCoordinate)
                                    } else {
                                        Log.e("MainActivity", "Invalid coordinates: $latitudeStr, $longitudeStr")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MainAdapter(devices, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {
        viewModel.devices.observe(this) { newDevices ->
            devices.clear()
            devices.addAll(newDevices)
            adapter.notifyDataSetChanged()
        }
    }

    private fun check() {
        favouriteMacs = intent.getStringArrayListExtra("macs") ?: emptyList()
        viewModel.favouriteMacs = favouriteMacs
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        check()
    }

    private fun checkPermissions(){
        val isAllGranted = REQUEST_PERMISSIONS.all { permission->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (isAllGranted){
            Toast.makeText(this, "Permissions is granted", Toast.LENGTH_SHORT).show()
        } else{
            launcher.launch(REQUEST_PERMISSIONS)
        }
    }


    companion object {
        private val REQUEST_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}

