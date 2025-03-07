package com.citrus.blsc.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.citrus.blsc.R
import com.citrus.blsc.data.database.AppDatabase
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.citrus.blsc.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        with(googleMap.uiSettings) {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = true
        }
        db = AppDatabase.getDatabase(this)

        val macFromIntent = intent.getStringExtra("mac")

        lifecycleScope.launch {
            val coordinates = db.deviceCoordinateDao().getCoordinateByMac(macFromIntent ?: "")
            val count = db.deviceCoordinateDao().countCoordinatesByMac(macFromIntent ?: "")
            coordinates.forEach { coordinate ->
                val latLng = LatLng(coordinate!!.latitude, coordinate.longitude)
                val marker =
                    mMap.addMarker(MarkerOptions().position(latLng).title(coordinate.macAddress))
                marker?.snippet = coordinate.time
                Toast.makeText(this@MapsActivity, "Найдено записей: $count", Toast.LENGTH_SHORT)
                    .show()
            }
            if (coordinates.isNotEmpty()) {
                val firstCoordinate = coordinates.first()
                mMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            firstCoordinate!!.latitude,
                            firstCoordinate.longitude
                        ), 15f
                    )
                )
            } else {
                Toast.makeText(
                    this@MapsActivity,
                    "Координаты не найдены для MAC-адреса: $macFromIntent",
                    Toast.LENGTH_SHORT
                ).show()
            }

            Toast.makeText(this@MapsActivity, "Найдено записей: $count", Toast.LENGTH_SHORT).show()
        }

        getCurrentLocation()
    }


    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLocation = LatLng(it.latitude, it.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
            }
        }
    }

    companion object {
        private val REQUEST_PERMISSIONS: Array<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
    }

}