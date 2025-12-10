package com.example.kindboxmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy // <-- IMPORT BARU
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.databinding.ActivityDetailDonationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DetailDonationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailDonationBinding
    private var donationItem: DonationEntity? = null
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailDonationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Ambil Data dari Intent
        @Suppress("DEPRECATION")
        donationItem = intent.getParcelableExtra("EXTRA_DONATION")

        if (donationItem == null) {
            Toast.makeText(this, "Data barang tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Setup Tampilan
        setupUI(donationItem!!)
        setupPOV(donationItem!!)
        setupBottomNav()

        // 3. Tombol Kembali
        binding.btnBackDetail.setOnClickListener { finish() }
    }

    private fun setupUI(item: DonationEntity) {
        binding.tvDetailTitle.text = item.title
        binding.tvDetailDescription.text = item.description
        binding.tvDetailCategory.text = item.category
        binding.tvDetailCondition.text = item.condition
        binding.tvDetailLocation.text = item.location

        // PERBAIKAN DI SINI (Ganti tvDetailTotal jadi tvDetailQuantity)
        binding.tvDetailQuantity.text = "${item.quantity} Pcs"

        // Peminat & Tersedia
        val peminat = item.interestedCount
        // Pastikan stok tidak minus
        val sisa = if (item.quantity - peminat < 0) 0 else item.quantity - peminat

        binding.tvDetailPeminat.text = peminat.toString()
        binding.tvDetailTersedia.text = sisa.toString()

        // Gambar
        if (item.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                // FIX: Bypass cache Glide agar gambar yang baru diupload muncul
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(binding.ivDetailImage)
        }
    }

    private fun setupPOV(item: DonationEntity) {
        val isOwner = currentUserId != null && currentUserId == item.userId

        if (isOwner) {
            // --- TAMPILAN PEMILIK ---
            binding.layoutDonorInfo.visibility = View.GONE
            binding.btnMinat.visibility = View.GONE

            binding.layoutOwnerInfo.visibility = View.VISIBLE
            binding.layoutOwnerButtons.visibility = View.VISIBLE

            binding.btnDelete.setOnClickListener { showDeleteDialog(item.id) }

            // --- LOGIKA EDIT BARANG (UPDATED) ---
            binding.btnEdit.setOnClickListener {
                val intent = Intent(this, AddDonationActivity::class.java)
                intent.putExtra("EXTRA_EDIT_DATA", item) // Kirim data barang ke form
                startActivity(intent)
            }

            loadInterestedUsers(item.interestedUsers)

        } else {
            // --- TAMPILAN PEMINAT ---
            binding.layoutOwnerInfo.visibility = View.GONE
            binding.layoutOwnerButtons.visibility = View.GONE

            binding.layoutDonorInfo.visibility = View.VISIBLE
            binding.btnMinat.visibility = View.VISIBLE

            loadDonorInfo(item.userId)

            // Cek apakah sudah pernah klik minat?
            checkIfInterested(item) { alreadyInterested ->
                if (alreadyInterested) {
                    binding.btnMinat.isEnabled = false
                    binding.btnMinat.text = "Sudah Minat"
                } else {
                    binding.btnMinat.setOnClickListener {
                        incrementInterestedCount(item.id)
                    }
                }
            }

            binding.btnWA.setOnClickListener {
                openWhatsApp(item.whatsappNumber, item.title)
            }
        }
    }

    // Cek status minat user saat ini
    private fun checkIfInterested(item: DonationEntity, callback: (Boolean) -> Unit) {
        val userId = currentUserId ?: return
        // Cek dari data lokal dulu (String dipisah koma)
        if (item.interestedUsers.contains(userId)) {
            callback(true)
        } else {
            // Cek ke server untuk kepastian (opsional)
            db.collection("donations").document(item.id).get().addOnSuccessListener { doc ->
                val list = doc.get("interestedUserIds") as? List<*>
                val exists = list?.contains(userId) == true
                callback(exists)
            }
        }
    }

    private fun loadDonorInfo(userId: String) {
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                binding.tvDonorName.text = doc.getString("name") ?: "User KindBox"
                val photoUrl = doc.getString("photoUrl")
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this).load(photoUrl).into(binding.ivDonorProfile)
                }
            }
        }
    }

    private fun loadInterestedUsers(userIdsString: String) {
        if (userIdsString.isEmpty()) return

        val ids = userIdsString.split(",").filter { it.isNotEmpty() }
        if (ids.isEmpty()) return

        binding.listInterestedUsers.removeAllViews()

        ids.forEach { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "User"

                val tv = TextView(this)
                tv.text = "• $name"
                tv.setPadding(8, 8, 8, 8)
                binding.listInterestedUsers.addView(tv)
            }
        }
    }

    private fun incrementInterestedCount(donationId: String) {
        val userId = currentUserId ?: return
        val docRef = db.collection("donations").document(donationId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            // Perbaikan Unchecked Cast dengan List<*>
            val currentList = snapshot.get("interestedUserIds") as? List<*> ?: emptyList<String>()

            if (!currentList.contains(userId)) {
                val currentCount = snapshot.getLong("interestedCount") ?: 0
                transaction.update(docRef, "interestedCount", currentCount + 1)
                transaction.update(docRef, "interestedUserIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Minat berhasil dicatat!", Toast.LENGTH_SHORT).show()
            binding.btnMinat.isEnabled = false
            binding.btnMinat.text = "Sudah Minat"

            // Update angka di UI secara manual (biar gak nunggu refresh)
            val currentPeminat = binding.tvDetailPeminat.text.toString().toIntOrNull() ?: 0
            binding.tvDetailPeminat.text = (currentPeminat + 1).toString()
        }
    }

    private fun openWhatsApp(number: String, itemName: String) {
        if (number.isEmpty()) {
            Toast.makeText(this, "Nomor WhatsApp tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        var formatted = number.trim()
        if (formatted.startsWith("0")) formatted = "62" + formatted.substring(1)

        val message = "Halo, saya berminat dengan *$itemName* di KindBox."
        val url = "https://api.whatsapp.com/send?phone=$formatted&text=${Uri.encode(message)}"

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteDialog(id: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Yakin ingin menghapus barang ini?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection("donations").document(id).delete().addOnSuccessListener {
                    Toast.makeText(this, "Terhapus", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setupBottomNav() {
        binding.navHome.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)); finish() }
        binding.navAdd.setOnClickListener { startActivity(Intent(this, AddDonationActivity::class.java)); finish() }
        binding.navProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)); finish() }
    }
}