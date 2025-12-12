package com.example.kindboxmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.ui.HomeScreen
import com.example.kindboxmobile.ui.MainViewModel
import com.example.kindboxmobile.ui.ViewModelFactory
import com.example.kindboxmobile.ui.theme.KindBoxMobileTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale


class MainActivity : AppCompatActivity() {

    // Gunakan by viewModels
    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory(
            DonationRepository(
                AppDatabase.getDatabase(this).donationDao(),
                FirebaseFirestore.getInstance(),
                FirebaseStorage.getInstance()
            )
        )
    }

    // Data Lokasi
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val requestLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) getMyLastLocation() else Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            KindBoxMobileTheme {
                // Ambil data donasi yang difilter/search
                val filteredDonations by viewModel.filteredDonations.observeAsState(initial = emptyList())

                // Ambil data user
                val userName by viewModel.userName.observeAsState(initial = "User KindBox")
                val userLocation by viewModel.userLocation.observeAsState(initial = "Memuat lokasi...")
                val userPhotoUrl by viewModel.userPhotoUrl.observeAsState() // Ambil Photo URL

                // Ambil state filter kategori
                val currentCategoryFilter by viewModel.currentCategoryFilter.observeAsState(initial = "Semua Kategori")

                HomeScreen(
                    filteredDonations = filteredDonations,
                    userName = userName,
                    userLocation = userLocation,
                    userPhotoUrl = userPhotoUrl, // Passing Photo URL
                    onItemClick = { donation ->
                        val intent = Intent(this, DetailDonationActivity::class.java)
                        intent.putExtra("EXTRA_DONATION", donation)
                        startActivity(intent)
                    },
                    onSearchSubmit = { query ->
                        // Menggunakan setSearchQuery di ViewModel
                        viewModel.setSearchQuery(query)
                    },
                    currentCategoryFilter = currentCategoryFilter,
                    onCategoryFilterChange = { category ->
                        viewModel.setCategoryFilter(category)
                    },
                    onRefresh = { viewModel.refreshData() }
                )
            }
        }

        viewModel.refreshData()
        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        viewModel.fetchUserData()
    }

    // --- Logika Lokasi ---

    private fun checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            getMyLastLocation()
        }
    }

    private fun getMyLastLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    getAddressName(location.latitude, location.longitude)
                } else {
                    viewModel.setUserLocation("Lokasi tidak ditemukan. Coba nyalakan GPS.")
                }
            }
        }
    }

    private fun getAddressName(lat: Double, lon: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val locText = listOfNotNull(address.subLocality, address.locality, address.adminArea).joinToString(", ")
                        viewModel.setUserLocation(locText)
                    } else {
                        viewModel.setUserLocation("Alamat tidak dikenal")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val locText = listOfNotNull(address.subLocality, address.locality, address.adminArea).joinToString(", ")
                    viewModel.setUserLocation(locText)
                } else {
                    viewModel.setUserLocation("Alamat tidak dikenal")
                }
            }
        } catch (e: Exception) {
            viewModel.setUserLocation("Gagal mengambil alamat")
            e.printStackTrace()
        }
    }
}