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
import com.bumptech.glide.Glide
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.databinding.ActivityProfileBinding
import com.example.kindboxmobile.ui.DonationAdapter
import com.example.kindboxmobile.ui.MainViewModel
import com.example.kindboxmobile.ui.ViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapterMemberi: DonationAdapter
    private lateinit var adapterMinat: DonationAdapter
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Variabel untuk menyimpan DATA ASLI
    private var originalMyDonations: List<DonationEntity> = listOf()
    private var originalMyInterests: List<DonationEntity> = listOf()

    // Variabel filter
    private var searchMemberiQuery: String = ""
    private var categoryFilterMemberi: String = "Semua Kategori" // Default
    private var searchMinatQuery: String = ""
    private var categoryFilterMinat: String = "Semua Kategori" // Default


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUserProfile()
        setupHistoryRecyclerView()
        setupSearchListeners()
        setupFilterButtons()
        setupBottomNavigation()

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        setupUserProfile()
        viewModel.refreshData()
    }

    private fun setupUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        // 1. Ambil Data Profil Dasar
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "User KindBox"
                    val username = document.getString("username") ?: "@user"
                    val photoUrl = document.getString("photoUrl")

                    binding.tvName.text = name
                    binding.tvUsername.text = "@$username"

                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this).load(photoUrl).into(binding.ivProfile)
                    }
                }
            }

        // 2. Hitung Statistik (Level & Rating)
        loadUserStatistics(userId)
    }

    // Di dalam ProfileActivity.kt

    private fun loadUserStatistics(userId: String) {
        // 1. Ambil Statistik "Jumlah Memberi" langsung dari Profil User
        // Ini memastikan angka tidak hilang meskipun barang dihapus
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Ambil angka counter (default 0 jika belum ada)
                    val jumlahDonasi = (document.getLong("completedDonationCount") ?: 0).toInt()

                    // Hitung Level Kebaikan: (Jumlah / 5) + 1
                    val levelKebaikan = (jumlahDonasi / 5) + 1

                    // Update Tampilan
                    binding.tvLevelNumber.text = levelKebaikan.toString()
                    binding.tvGoodnessLevel.text = "Kamu telah memberikan barang sebanyak $jumlahDonasi kali. Level kebaikan kamu adalah Level $levelKebaikan."
                }
            }
            .addOnFailureListener {
                binding.tvGoodnessLevel.text = "Gagal memuat statistik."
            }

        // 2. Hitung Rata-Rata Rating (Tetap sama)
        firestore.collection("ratings")
            .whereEqualTo("donorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    var totalStars = 0.0
                    for (doc in documents) {
                        totalStars += (doc.getDouble("ratingValue") ?: 0.0)
                    }
                    val averageRating = totalStars / documents.size()
                    binding.rbProfileRating.rating = averageRating.toFloat()
                    binding.rbProfileRating.visibility = View.VISIBLE
                } else {
                    binding.rbProfileRating.rating = 0f
                }
            }
    }

    private fun setupHistoryRecyclerView() {
        // 1. Adapter Riwayat Memberi
        adapterMemberi = DonationAdapter { donation ->
            val intent = Intent(this, DetailDonationActivity::class.java)
            intent.putExtra("EXTRA_DONATION", donation)
            startActivity(intent)
        }
        binding.rvHistory.layoutManager = GridLayoutManager(this, 2)
        binding.rvHistory.isNestedScrollingEnabled = false
        binding.rvHistory.adapter = adapterMemberi

        // 2. Adapter Riwayat Minat
        adapterMinat = DonationAdapter { donation ->
            val intent = Intent(this, DetailDonationActivity::class.java)
            intent.putExtra("EXTRA_DONATION", donation)
            startActivity(intent)
        }
        binding.rvInterestedHistory.layoutManager = GridLayoutManager(this, 2)
        binding.rvInterestedHistory.isNestedScrollingEnabled = false
        binding.rvInterestedHistory.adapter = adapterMinat

        val db = AppDatabase.getDatabase(this)
        val repo = DonationRepository(
            db.donationDao(),
            FirebaseFirestore.getInstance(),
            FirebaseStorage.getInstance()
        )
        val factory = ViewModelFactory(repo)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        viewModel.donations.observe(this) { listBarang ->
            val myUserId = auth.currentUser?.uid

            // Simpan data ke variabel Original
            originalMyDonations = listBarang.filter { it.userId == myUserId }
            originalMyInterests = listBarang.filter { it.interestedUsers.contains(myUserId.toString()) }

            // Terapkan filter gabungan
            applyFilterMemberi()
            applyFilterMinat()
        }
        viewModel.refreshData()
    }

    private fun setupSearchListeners() {
        // Listener untuk Search Riwayat Memberi
        binding.etSearchMemberi.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchMemberiQuery = s.toString() // Update query
                applyFilterMemberi() // Terapkan filter gabungan
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener untuk Search Riwayat Minat
        binding.etSearchMinat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchMinatQuery = s.toString() // Update query
                applyFilterMinat() // Terapkan filter gabungan
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilterButtons() {
        binding.btnFilterMemberi.setOnClickListener {
            showCategoryFilterDialog(true)
        }
        binding.btnFilterMinat.setOnClickListener {
            showCategoryFilterDialog(false)
        }
    }

    private fun showCategoryFilterDialog(isMemberi: Boolean) {
        val categories = resources.getStringArray(R.array.donation_categories_filter)
        val currentFilter = if (isMemberi) categoryFilterMemberi else categoryFilterMinat

        val checkedItem = categories.indexOf(currentFilter).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Filter Kategori " + if (isMemberi) "Memberi" else "Minat")
            .setSingleChoiceItems(categories, checkedItem) { dialog, which ->
                val selectedCategory = categories[which]
                if (isMemberi) {
                    categoryFilterMemberi = selectedCategory
                    applyFilterMemberi()
                } else {
                    categoryFilterMinat = selectedCategory
                    applyFilterMinat()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }


    private fun applyFilterMemberi() {
        var filteredList = originalMyDonations

        if (categoryFilterMemberi != "Semua Kategori") {
            filteredList = filteredList.filter { it.category == categoryFilterMemberi }
        }

        if (searchMemberiQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.title.contains(searchMemberiQuery, ignoreCase = true) ||
                        it.description.contains(searchMemberiQuery, ignoreCase = true)
            }
        }

        adapterMemberi.submitList(filteredList)

        if (filteredList.isEmpty()) {
            val emptyMessage = if (categoryFilterMemberi != "Semua Kategori") {
                getString(R.string.empty_donations_filtered, categoryFilterMemberi)
            } else if (searchMemberiQuery.isNotEmpty()) {
                "Tidak ada barang yang cocok dengan \"$searchMemberiQuery\" di Riwayat Memberi."
            } else {
                "Belum ada donasi yang kamu berikan."
            }
            Toast.makeText(this, emptyMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyFilterMinat() {
        var filteredList = originalMyInterests

        if (categoryFilterMinat != "Semua Kategori") {
            filteredList = filteredList.filter { it.category == categoryFilterMinat }
        }

        if (searchMinatQuery.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.title.contains(searchMinatQuery, ignoreCase = true) ||
                        it.description.contains(searchMinatQuery, ignoreCase = true)
            }
        }

        adapterMinat.submitList(filteredList)

        if (filteredList.isEmpty()) {
            val emptyMessage = if (categoryFilterMinat != "Semua Kategori") {
                getString(R.string.empty_donations_filtered, categoryFilterMinat)
            } else if (searchMinatQuery.isNotEmpty()) {
                "Tidak ada barang yang cocok dengan \"$searchMinatQuery\" di Riwayat Minat."
            } else {
                "Belum ada barang yang kamu minati."
            }
            Toast.makeText(this, emptyMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.navAdd.setOnClickListener {
            startActivity(Intent(this, AddDonationActivity::class.java))
            finish()
        }
    }
}