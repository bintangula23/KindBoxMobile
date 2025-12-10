package com.example.kindboxmobile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
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

            // PERBAIKAN: Filter di awal, simpan ke variabel global 'otherUsersDonations'
            // Hanya ambil barang yang BUKAN milik saya
            otherUsersDonations = listBarang.filter { it.userId != myUserId }

            // Tampilkan data yang sudah difilter
            updateList(otherUsersDonations)
        }
        viewModel.refreshData()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterData(query: String) {
        // Filter dari 'otherUsersDonations', bukan dari list kosong atau list mentah
        val filteredList = if (query.isEmpty()) {
            otherUsersDonations
        } else {
            otherUsersDonations.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        updateList(filteredList)
    }

    private fun updateList(list: List<DonationEntity>) {
        if (list.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvDonations.visibility = View.GONE
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