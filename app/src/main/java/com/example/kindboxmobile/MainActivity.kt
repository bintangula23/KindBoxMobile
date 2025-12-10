package com.example.kindboxmobile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.databinding.ActivityMainBinding
import com.example.kindboxmobile.ui.DonationAdapter
import com.example.kindboxmobile.ui.MainViewModel
import com.example.kindboxmobile.ui.ViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: DonationAdapter

    // List untuk menyimpan data asli (Barang Orang Lain)
    private var otherUsersDonations: List<DonationEntity> = listOf()

    // Variabel filter
    private var currentSearchQuery: String = ""
    private var currentCategoryFilter: String = "Semua Kategori" // Default filter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchUserData()
        setupRecyclerView()
        setupViewModel()
        setupSearch()
        setupFilterButton()
        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        fetchUserData()
        if (::viewModel.isInitialized) {
            viewModel.refreshData()
        }
    }

    private fun fetchUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "User KindBox"
                    val location = document.getString("location") ?: "Lokasi belum diatur"
                    binding.tvGreeting.text = "Hi, $name!"
                    binding.tvHeaderLocation.text = "Berada di $location"
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = DonationAdapter { donation ->
            val intent = Intent(this, DetailDonationActivity::class.java)
            intent.putExtra("EXTRA_DONATION", donation)
            startActivity(intent)
        }

        binding.rvDonations.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            this.adapter = this@MainActivity.adapter
        }
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val repository = DonationRepository(
            database.donationDao(),
            FirebaseFirestore.getInstance(),
            FirebaseStorage.getInstance()
        )
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.donations.observe(this) { listBarang ->
            val myUserId = FirebaseAuth.getInstance().currentUser?.uid

            // Hanya ambil barang yang BUKAN milik saya
            otherUsersDonations = listBarang.filter { it.userId != myUserId }

            // Terapkan filter gabungan
            applyFilters()
        }
        viewModel.refreshData()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString() // Update query
                applyFilters() // Terapkan filter gabungan
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener {
            showCategoryFilterDialog()
        }
    }

    private fun showCategoryFilterDialog() {
        val categories = resources.getStringArray(R.array.donation_categories_filter)

        // Temukan index kategori yang saat ini dipilih
        val checkedItem = categories.indexOf(currentCategoryFilter).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Filter Berdasarkan Kategori")
            .setSingleChoiceItems(categories, checkedItem) { dialog, which ->
                currentCategoryFilter = categories[which]
                dialog.dismiss()
                applyFilters()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun applyFilters() {
        var filteredList = otherUsersDonations

        // 1. Filter berdasarkan Kategori
        if (currentCategoryFilter != "Semua Kategori") {
            filteredList = filteredList.filter { it.category == currentCategoryFilter }
        }

        // 2. Filter berdasarkan Search Query
        if (currentSearchQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.title.contains(currentSearchQuery, ignoreCase = true) ||
                        it.description.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        updateList(filteredList)
    }

    private fun updateList(list: List<DonationEntity>) {
        if (list.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvDonations.visibility = View.GONE

            // BARU: Logika pesan kosong
            if (currentCategoryFilter != "Semua Kategori") {
                // Pesan khusus untuk filter kategori
                binding.tvEmptyState.text = getString(R.string.empty_donations_filtered, currentCategoryFilter)
            } else if (currentSearchQuery.isNotEmpty()) {
                // Pesan khusus untuk filter search text
                binding.tvEmptyState.text = "Tidak ada barang yang cocok dengan \"$currentSearchQuery\"."
            }
            else {
                // Pesan default (tidak ada barang sama sekali)
                binding.tvEmptyState.text = getString(R.string.belum_ada_donasi)
            }
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvDonations.visibility = View.VISIBLE
            adapter.submitList(list)
        }
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener {
            viewModel.refreshData()
            binding.rvDonations.smoothScrollToPosition(0)
        }
        binding.navAdd.setOnClickListener {
            startActivity(Intent(this, AddDonationActivity::class.java))
        }
        binding.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}