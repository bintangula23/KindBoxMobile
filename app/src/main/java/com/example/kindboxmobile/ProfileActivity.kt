package com.example.kindboxmobile

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
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

    // Variabel untuk menyimpan DATA ASLI (Sebelum difilter search)
    private var originalMyDonations: List<DonationEntity> = listOf()
    private var originalMyInterests: List<DonationEntity> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUserProfile()
        setupHistoryRecyclerView()
        setupSearchListeners() // Tambahkan setup search
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

            // Tampilkan awal (tanpa filter search)
            adapterMemberi.submitList(originalMyDonations)
            adapterMinat.submitList(originalMyInterests)
        }
        viewModel.refreshData()
    }

    private fun setupSearchListeners() {
        // Listener untuk Search Riwayat Memberi
        binding.etSearchMemberi.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMemberi(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener untuk Search Riwayat Minat
        binding.etSearchMinat.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMinat(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterMemberi(query: String) {
        val filteredList = if (query.isEmpty()) {
            originalMyDonations
        } else {
            originalMyDonations.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        adapterMemberi.submitList(filteredList)
    }

    private fun filterMinat(query: String) {
        val filteredList = if (query.isEmpty()) {
            originalMyInterests
        } else {
            originalMyInterests.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        adapterMinat.submitList(filteredList)
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