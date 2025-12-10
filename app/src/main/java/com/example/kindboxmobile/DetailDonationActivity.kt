package com.example.kindboxmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.databinding.ActivityDetailDonationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.kindboxmobile.data.AppDatabase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.DocumentSnapshot

// Data class untuk memodelkan data peminat secara visual (workaround)
data class PeminatData(
    val userId: String,
    val name: String,
    val username: String,
    val location: String,
    val photoUrl: String? = null,
    val averageRating: Double = 0.0,
    val interestStatus: String, // PENDING, VERIFIED, REJECTED
    val quantityRequested: Int = 1
)

class DetailDonationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailDonationBinding
    private var donationItem: DonationEntity? = null
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val db = FirebaseFirestore.getInstance()
    private lateinit var repository: DonationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailDonationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val roomDb = AppDatabase.getDatabase(this)
        repository = DonationRepository(
            roomDb.donationDao(),
            Firebase.firestore,
            FirebaseStorage.getInstance()
        )

        @Suppress("DEPRECATION")
        donationItem = intent.getParcelableExtra("EXTRA_DONATION")

        if (donationItem == null) {
            Toast.makeText(this, "Data barang tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 1. Ambil data donasi terbaru dari Firestore untuk status yang benar
        CoroutineScope(Dispatchers.Main).launch {
            val latestDoc = try {
                db.collection("donations").document(donationItem!!.id).get().await()
            } catch (e: Exception) {
                null
            }
            if (latestDoc != null && latestDoc.exists()) {
                donationItem = donationItem!!.copy(
                    quantity = (latestDoc.get("quantity") as? Number)?.toInt() ?: 1,
                    interestedUsers = (latestDoc.get("interestedUserIds") as? List<String> ?: emptyList()).joinToString(","),
                    interestedCount = (latestDoc.get("interestedCount") as? Number)?.toInt() ?: 0
                )
            }

            // 2. Setup UI dan POV setelah mendapatkan data terbaru
            setupUI(donationItem!!)
            setupPOV(donationItem!!, latestDoc)
        }

        binding.btnBackDetail.setOnClickListener { finish() }
        setupBottomNav()
    }

    private fun setupUI(item: DonationEntity) {
        binding.tvDetailTitle.text = item.title
        binding.tvDetailDescription.text = item.description
        binding.tvDetailCategory.text = item.category
        binding.tvDetailCondition.text = item.condition
        binding.tvDetailLocation.text = item.location

        binding.tvDetailQuantity.text = getString(R.string.quantity_pcs, item.quantity)

        val peminat = item.interestedCount
        val sisa = item.quantity

        binding.tvDetailPeminat.text = peminat.toString()
        binding.tvDetailTersedia.text = sisa.toString()

        if (item.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(binding.ivDetailImage)
        }
    }

    private fun setupPOV(item: DonationEntity, latestDoc: DocumentSnapshot?) {
        val isOwner = currentUserId != null && currentUserId == item.userId

        val verifiedList = latestDoc?.get("verifiedRecipients") as? List<String> ?: emptyList()
        val rejectedList = latestDoc?.get("rejectedRecipients") as? List<String> ?: emptyList()

        val currentUserStatus = when (currentUserId) {
            in verifiedList -> "VERIFIED"
            in rejectedList -> "REJECTED"
            else -> "PENDING"
        }

        val availableStock = item.quantity

        if (isOwner) {
            // --- TAMPILAN PEMILIK ---
            binding.layoutDonorInfo.visibility = View.GONE
            binding.btnMinat.visibility = View.GONE

            binding.layoutOwnerInfo.visibility = View.VISIBLE
            binding.layoutOwnerButtons.visibility = View.VISIBLE
            binding.tvDetailTersedia.text = availableStock.toString()

            binding.btnDelete.setOnClickListener { showDeleteDialog(item.id) }
            binding.btnEdit.setOnClickListener {
                val intent = Intent(this, AddDonationActivity::class.java)
                intent.putExtra("EXTRA_EDIT_DATA", item)
                startActivity(intent)
            }

            loadInterestedUsers(item.id, latestDoc, availableStock)

        } else {
            // --- TAMPILAN PEMINAT ---
            binding.layoutOwnerInfo.visibility = View.GONE
            binding.layoutOwnerButtons.visibility = View.GONE

            binding.layoutDonorInfo.visibility = View.VISIBLE
            binding.btnMinat.visibility = View.VISIBLE

            loadDonorInfo(item.userId)

            // Logika tombol Minat/Status
            when (currentUserStatus) {
                "VERIFIED" -> {
                    binding.btnMinat.isEnabled = false
                    binding.btnMinat.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                    binding.btnMinat.text = "Anda Telah Diverifikasi"
                }
                "REJECTED" -> {
                    binding.btnMinat.isEnabled = false
                    binding.btnMinat.setBackgroundColor(resources.getColor(R.color.kindbox_red, theme))
                    binding.btnMinat.text = "Pengajuan Ditolak"
                }
                "PENDING" -> {
                    // Cek jika stok habis sebelum diverifikasi
                    if (availableStock <= 0) {
                        binding.btnMinat.isEnabled = false
                        binding.btnMinat.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
                        binding.btnMinat.text = getString(R.string.stock_zero_recipient_status)
                    } else {
                        checkIfInterested(item) { alreadyInterested ->
                            if (alreadyInterested) {
                                binding.btnMinat.isEnabled = false
                                binding.btnMinat.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
                                binding.btnMinat.text = "Sudah Minat (Pending)"
                            } else {
                                binding.btnMinat.setOnClickListener {
                                    incrementInterestedCount(item.id)
                                }
                            }
                        }
                    }
                }
            }
            binding.btnWA.setOnClickListener {
                openWhatsApp(item.whatsappNumber, item.title)
            }
        }
    }

    private fun checkIfInterested(item: DonationEntity, callback: (Boolean) -> Unit) {
        val userId = currentUserId ?: return
        if (item.interestedUsers.contains(userId)) {
            callback(true)
        } else {
            db.collection("donations").document(item.id).get().addOnSuccessListener { doc ->
                val list = doc.get("interestedUserIds") as? List<*>
                val exists = list?.contains(userId) == true
                callback(exists)
            }
        }
    }

    // MODIFIED: loadInterestedUsers (Menghilangkan warning stok dari sini)
    private fun loadInterestedUsers(donationId: String, latestDoc: DocumentSnapshot?, availableStock: Int) {
        binding.listInterestedUsers.removeAllViews()

        val interestedList = latestDoc?.get("interestedUserIds") as? List<String> ?: emptyList()
        val verifiedList = latestDoc?.get("verifiedRecipients") as? List<String> ?: emptyList()
        val rejectedList = latestDoc?.get("rejectedRecipients") as? List<String> ?: emptyList()

        val allInterestedIds = (interestedList + verifiedList + rejectedList).distinct()

        // Hapus penambahan warning stok di sini (logika dipindahkan ke createInterestedUserView)
        // Pastikan tidak ada view tambahan selain yang ada di XML (TextView "Daftar Peminat:")
        val defaultChildCount = 2 // Header text view (0) dan listInterestedUsers (1)
        while (binding.layoutOwnerInfo.childCount > defaultChildCount) {
            binding.layoutOwnerInfo.removeViewAt(1)
        }

        if (allInterestedIds.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Belum ada peminat."
            tv.textSize = 12f
            tv.setTextColor(resources.getColor(R.color.kindbox_text_primary, theme))
            tv.setPadding(8, 8, 8, 8)
            binding.listInterestedUsers.addView(tv)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val peminatList = mutableListOf<PeminatData>()

            for (uid in allInterestedIds) {
                try {
                    val doc = db.collection("users").document(uid).get().await()

                    if (doc.exists()) {
                        val status = when (uid) {
                            in verifiedList -> "VERIFIED"
                            in rejectedList -> "REJECTED"
                            else -> "PENDING"
                        }

                        val peminat = PeminatData(
                            userId = uid,
                            name = doc.getString("name") ?: "User",
                            username = doc.getString("username") ?: "@user",
                            location = doc.getString("location") ?: "Lokasi Tidak Tersedia",
                            photoUrl = doc.getString("photoUrl"),
                            interestStatus = status,
                            quantityRequested = 1
                        )
                        peminatList.add(peminat)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            launch(Dispatchers.Main) {
                binding.listInterestedUsers.removeAllViews()

                val sortedList = peminatList.sortedWith(compareBy {
                    when (it.interestStatus) {
                        "PENDING" -> 1
                        "VERIFIED" -> 2
                        "REJECTED" -> 3
                        else -> 4
                    }
                })

                sortedList.forEach { p ->
                    binding.listInterestedUsers.addView(createInterestedUserView(donationId, p, availableStock))
                }
            }
        }
    }

    // MODIFIED: createInterestedUserView (Logika stok habis dipindahkan ke sini)
    private fun createInterestedUserView(donationId: String, peminat: PeminatData, availableStock: Int): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 0, 0, 16)
        card.layoutParams = cardParams

        // --- 1. Tentukan Drawable berdasarkan Status ---
        val cardBackground = when (peminat.interestStatus) {
            "VERIFIED" -> R.drawable.bg_card_green_outline
            "REJECTED" -> R.drawable.bg_card_red_outline // ASUMSI FILE INI ADA
            else -> R.drawable.bg_card_item
        }
        card.setBackgroundResource(cardBackground)
        card.setPadding(16, 16, 16, 16)

        // --- Konten Card (Profil, Nama, Lokasi, Rating) ---
        val contentLayout = LinearLayout(this)
        contentLayout.orientation = LinearLayout.HORIZONTAL
        contentLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentLayout.gravity = android.view.Gravity.CENTER_VERTICAL

        val ivProfile = ImageView(this)
        val imageParams = LinearLayout.LayoutParams(56.dpToPx(), 56.dpToPx())
        imageParams.setMargins(0, 0, 12.dpToPx(), 0)
        ivProfile.layoutParams = imageParams
        ivProfile.setImageResource(R.mipmap.ic_launcher_round)
        if (!peminat.photoUrl.isNullOrEmpty()) {
            Glide.with(this).load(peminat.photoUrl).circleCrop().into(ivProfile)
        }
        contentLayout.addView(ivProfile)

        val infoLayout = LinearLayout(this)
        infoLayout.orientation = LinearLayout.VERTICAL
        infoLayout.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        )

        val tvUsername = TextView(this)
        tvUsername.text = peminat.username
        tvUsername.setTextColor(resources.getColor(R.color.kindbox_text_color, theme))
        tvUsername.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tvUsername.textSize = 14f
        infoLayout.addView(tvUsername)

        val tvLocation = TextView(this)
        tvLocation.text = peminat.location
        tvLocation.textSize = 12f
        tvLocation.setTextColor(resources.getColor(R.color.black, theme))
        infoLayout.addView(tvLocation)

        val ratingLayout = LinearLayout(this)
        ratingLayout.orientation = LinearLayout.HORIZONTAL
        for (i in 1..5) {
            val star = ImageView(this)
            star.layoutParams = LinearLayout.LayoutParams(12.dpToPx(), 12.dpToPx())
            star.setImageResource(android.R.drawable.btn_star_big_on)
            star.setColorFilter(resources.getColor(android.R.color.holo_orange_light, theme))
            ratingLayout.addView(star)
        }
        infoLayout.addView(ratingLayout)


        contentLayout.addView(infoLayout)
        card.addView(contentLayout)

        // --- 2. Status dan Tombol Aksi ---
        val statusText = TextView(this)
        statusText.setPadding(0, 16.dpToPx(), 0, 0)
        statusText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        statusText.textSize = 12f

        when (peminat.interestStatus) {
            "VERIFIED" -> {
                statusText.text = "Peminat ini telah diverifikasi!"
                statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
                card.addView(statusText)
            }
            "REJECTED" -> {
                statusText.text = "Peminat ini telah ditolak."
                statusText.setTextColor(resources.getColor(R.color.kindbox_red, theme))
                card.addView(statusText)
            }
            "PENDING" -> {
                val isStockZero = availableStock <= 0

                // Tambahkan pesan stok habis di atas tombol jika stok 0
                if (isStockZero) {
                    val stockWarning = TextView(this)
                    stockWarning.text = getString(R.string.stock_zero_owner_warning)
                    stockWarning.textSize = 12f
                    stockWarning.setTextColor(resources.getColor(R.color.kindbox_red, theme))
                    stockWarning.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    stockWarning.setPadding(0, 0, 0, 8.dpToPx())
                    card.addView(stockWarning)
                }

                statusText.text = getString(R.string.pending_interest_status, peminat.quantityRequested)
                statusText.setTextColor(resources.getColor(R.color.kindbox_text_color, theme))
                card.addView(statusText)

                // Tambahkan tombol Aksi
                val actionLayout = LinearLayout(this)
                actionLayout.orientation = LinearLayout.HORIZONTAL
                actionLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpToPx() }

                // Tentukan warna tombol berdasarkan status stok
                val buttonColorActive = resources.getColor(R.color.kindbox_dark_green, theme)
                val buttonColorDisabled = resources.getColor(android.R.color.darker_gray, theme)
                val rejectColorActive = resources.getColor(R.color.kindbox_red, theme)

                // Tombol Verifikasi
                val btnVerify = Button(this)
                btnVerify.text = "Terima"
                btnVerify.textSize = 12f
                btnVerify.isEnabled = !isStockZero // Nonaktifkan jika stok 0
                btnVerify.setBackgroundColor(if (isStockZero) buttonColorDisabled else buttonColorActive)

                actionLayout.addView(btnVerify, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply { rightMargin = 8.dpToPx() })

                if (!isStockZero) {
                    btnVerify.setOnClickListener {
                        if (availableStock < peminat.quantityRequested) {
                            Toast.makeText(this, "Stok ($availableStock) tidak cukup.", Toast.LENGTH_LONG).show()
                        } else {
                            showVerificationDialog(donationId, peminat.userId, peminat.quantityRequested)
                        }
                    }
                }

                // Tombol Tolak
                val btnReject = Button(this)
                btnReject.text = "Tolak"
                btnReject.textSize = 12f
                btnReject.isEnabled = !isStockZero // Nonaktifkan jika stok 0
                btnReject.setBackgroundColor(if (isStockZero) buttonColorDisabled else rejectColorActive)

                actionLayout.addView(btnReject, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply { leftMargin = 8.dpToPx() })

                if (!isStockZero) {
                    btnReject.setOnClickListener {
                        showRejectionDialog(donationId, peminat.userId)
                    }
                }

                card.addView(actionLayout)
            }
        }

        return card
    }

    // ... (Fungsi showVerificationDialog, showRejectionDialog, incrementInterestedCount, dll. tidak diubah)

    private fun showVerificationDialog(donationId: String, recipientUserId: String, quantity: Int) {
        AlertDialog.Builder(this)
            .setTitle("Verifikasi Peminat")
            .setMessage("Yakin ingin memverifikasi peminat ini? Stok barang akan berkurang sebanyak $quantity.")
            .setPositiveButton("Ya, Verifikasi") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val result = repository.verifyRecipientTransaction(donationId, recipientUserId, quantity)
                    Toast.makeText(this@DetailDonationActivity, result, Toast.LENGTH_LONG).show()

                    if (result.startsWith("Peminat")) {
                        recreate()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showRejectionDialog(donationId: String, recipientUserId: String) {
        AlertDialog.Builder(this)
            .setTitle("Tolak Peminat")
            .setMessage("Yakin ingin menolak peminat ini?")
            .setPositiveButton("Ya, Tolak") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val result = repository.rejectRecipient(donationId, recipientUserId)
                    Toast.makeText(this@DetailDonationActivity, result, Toast.LENGTH_LONG).show()

                    if (result.startsWith("Peminat")) {
                        recreate()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
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

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun incrementInterestedCount(donationId: String) {
        val userId = currentUserId ?: return
        val docRef = db.collection("donations").document(donationId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
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

            val currentPeminat = binding.tvDetailPeminat.text.toString().toIntOrNull() ?: 0
            binding.tvDetailPeminat.text = (currentPeminat + 1).toString()
        }
    }
}