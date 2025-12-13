package com.example.kindboxmobile

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.databinding.ActivityDetailDonationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class untuk memodelkan data peminat secara visual
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

    // Variabel global untuk menyimpan dialog agar tidak double
    private var ratingDialog: AlertDialog? = null

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
                // Update local entity dengan data terbaru dari Firestore
                val interestedList = latestDoc.get("interestedUserIds") as? List<String> ?: emptyList()
                val verifiedList = latestDoc.get("verifiedRecipients") as? List<String> ?: emptyList()
                val rejectedList = latestDoc.get("rejectedRecipients") as? List<String> ?: emptyList()

                // Gabungkan semua user untuk menghitung total peminat (interestedCount)
                val allUsers = (interestedList + verifiedList + rejectedList).distinct()
                val totalInterestedCount = allUsers.size

                val remainingQuantity = (latestDoc.get("quantity") as? Number)?.toInt() ?: 1
                // Gunakan fallback jika originalQuantity tidak ada (untuk data lama)
                val originalQuantity = (latestDoc.get("originalQuantity") as? Number)?.toInt() ?: remainingQuantity

                donationItem = donationItem!!.copy(
                    quantity = remainingQuantity, // Sisa Stok
                    originalQuantity = originalQuantity, // Total Stok Awal
                    interestedUsers = allUsers.joinToString(","),
                    interestedCount = totalInterestedCount // Total Peminat
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

        // PERBAIKAN STOK SESUAI PERMINTAAN: Tampilkan Sisa Stok / Total Stok Awal
        binding.tvDetailQuantity.text = "Tersedia: ${item.quantity} / ${item.originalQuantity} Pcs"

        val peminat = item.interestedCount // Total Peminat
        val sisa = item.quantity // Sisa Stok

        binding.tvDetailPeminat.text = peminat.toString()
        // Ini adalah jumlah yang tersedia di Owner POV, yang merupakan Sisa Stok
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
            // --- TAMPILAN PEMINAT/PENCARI ---
            binding.layoutOwnerInfo.visibility = View.GONE
            binding.layoutOwnerButtons.visibility = View.GONE

            binding.layoutDonorInfo.visibility = View.VISIBLE
            binding.btnMinat.visibility = View.VISIBLE

            // Tampilkan Info Donor + Bintang Rating
            loadDonorInfo(item.userId)

            when (currentUserStatus) {
                "VERIFIED" -> {
                    binding.btnMinat.isEnabled = false
                    binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    binding.btnMinat.text = "Anda Telah Diverifikasi"

                    // Cek apakah user bisa memberi rating (Belum pernah rating sebelumnya)
                    checkAndShowRatingButton(item)
                }
                "REJECTED" -> {
                    binding.btnMinat.isEnabled = false
                    binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, R.color.kindbox_red))
                    binding.btnMinat.text = "Pengajuan Ditolak"
                }
                "PENDING" -> {
                    if (availableStock <= 0) {
                        binding.btnMinat.isEnabled = false
                        binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                        binding.btnMinat.text = getString(R.string.stock_zero_recipient_status)
                    } else {
                        checkIfInterested(item) { alreadyInterested ->
                            if (alreadyInterested) {
                                binding.btnMinat.isEnabled = false
                                binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                                binding.btnMinat.text = "Sudah Minat (Pending)"
                            } else {
                                binding.btnMinat.isEnabled = true
                                binding.btnMinat.text = "Saya Berminat"
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

    // --- LOGIKA RATING ---

    private fun checkAndShowRatingButton(item: DonationEntity) {
        val recipientId = currentUserId ?: return

        // Cek di Firestore apakah user ini sudah memberi rating untuk item ini
        db.collection("ratings")
            .whereEqualTo("donationId", item.id)
            .whereEqualTo("recipientId", recipientId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Jika belum rating, ubah tombol menjadi tombol "Beri Bintang"
                    binding.btnMinat.isEnabled = true
                    binding.btnMinat.text = "Beri Bintang ke Pemberi"
                    binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, R.color.kindbox_primary))

                    binding.btnMinat.setOnClickListener {
                        showRatingDialog(item)
                    }
                } else {
                    binding.btnMinat.text = "Terima kasih atas ratingnya!"
                }
            }
    }

    private fun showRatingDialog(item: DonationEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rating, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBarInput)

        ratingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Kirim") { dialog, _ ->
                val ratingValue = ratingBar.rating
                if (ratingValue > 0) {
                    submitRatingToFirestore(item, ratingValue)
                } else {
                    Toast.makeText(this, "Silakan pilih bintang", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun submitRatingToFirestore(item: DonationEntity, ratingValue: Float) {
        val ratingData = hashMapOf(
            "donorId" to item.userId,
            "recipientId" to currentUserId,
            "donationId" to item.id,
            "ratingValue" to ratingValue,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("ratings")
            .add(ratingData)
            .addOnSuccessListener {
                Toast.makeText(this, "Rating berhasil dikirim!", Toast.LENGTH_SHORT).show()
                binding.btnMinat.isEnabled = false
                binding.btnMinat.text = "Rating Terkirim"
                binding.btnMinat.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengirim rating", Toast.LENGTH_SHORT).show()
            }
    }

    // --- INFO DONOR + BINTANG ---
    private fun loadDonorInfo(userId: String) {
        // 1. Ambil Data User (Nama & Foto)
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                binding.tvDonorName.text = doc.getString("name") ?: "User KindBox"
                val photoUrl = doc.getString("photoUrl")
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this).load(photoUrl).into(binding.ivDonorProfile)
                }
            }
        }

        // 2. Ambil Rata-rata Rating User dari koleksi 'ratings'
        db.collection("ratings")
            .whereEqualTo("donorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    var totalStars = 0.0
                    for (doc in documents) {
                        totalStars += (doc.getDouble("ratingValue") ?: 0.0)
                    }
                    val avg = totalStars / documents.size()

                    binding.rbDonorRating.visibility = View.VISIBLE
                    binding.rbDonorRating.rating = avg.toFloat()

                } else {
                    binding.rbDonorRating.rating = 0f
                }
            }
    }

    private fun checkIfInterested(item: DonationEntity, callback: (Boolean) -> Unit) {
        val userId = currentUserId ?: return
        if (item.interestedUsers.contains(userId)) {
            callback(true)
            return
        }

        db.collection("donations").document(item.id).get().addOnSuccessListener { doc ->
            val list = doc.get("interestedUserIds") as? List<*>
            val verified = doc.get("verifiedRecipients") as? List<*>
            val rejected = doc.get("rejectedRecipients") as? List<*>

            val exists = (list?.contains(userId) == true) ||
                    (verified?.contains(userId) == true) ||
                    (rejected?.contains(userId) == true)
            callback(exists)
        }.addOnFailureListener {
            callback(false)
        }
    }

    private fun loadInterestedUsers(donationId: String, latestDoc: DocumentSnapshot?, availableStock: Int) {
        binding.listInterestedUsers.removeAllViews()

        val interestedList = latestDoc?.get("interestedUserIds") as? List<String> ?: emptyList()
        val verifiedList = latestDoc?.get("verifiedRecipients") as? List<String> ?: emptyList()
        val rejectedList = latestDoc?.get("rejectedRecipients") as? List<String> ?: emptyList()

        val allInterestedIds = (interestedList + verifiedList + rejectedList).distinct()

        val defaultChildCount = 2
        while (binding.layoutOwnerInfo.childCount > defaultChildCount) {
            binding.layoutOwnerInfo.removeViewAt(1)
        }

        if (allInterestedIds.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Belum ada peminat."
            tv.textSize = 12f
            tv.setTextColor(ContextCompat.getColor(this, R.color.kindbox_text_primary))
            tv.setPadding(8, 8, 8, 8)
            binding.listInterestedUsers.addView(tv)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val peminatList = mutableListOf<PeminatData>()

            for (uid in allInterestedIds) {
                try {
                    // 1. Ambil Profil User
                    val doc = db.collection("users").document(uid).get().await()

                    // 2. Ambil Rating Peminat (Hitung Rata-rata)
                    val ratingSnapshot = db.collection("ratings")
                        .whereEqualTo("donorId", uid)
                        .get()
                        .await()

                    var totalStars = 0.0
                    for (ratingDoc in ratingSnapshot) {
                        totalStars += (ratingDoc.getDouble("ratingValue") ?: 0.0)
                    }
                    val avgRating = if (!ratingSnapshot.isEmpty) totalStars / ratingSnapshot.size() else 0.0

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
                            averageRating = avgRating,
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

    private fun createInterestedUserView(donationId: String, peminat: PeminatData, availableStock: Int): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 0, 0, 16)
        card.layoutParams = cardParams

        // Warna Background Card
        val cardBackground = when (peminat.interestStatus) {
            "VERIFIED" -> R.drawable.bg_card_green_outline
            "REJECTED" -> R.drawable.bg_card_red_outline
            else -> R.drawable.bg_card_item
        }
        card.setBackgroundResource(cardBackground)
        card.setPadding(16, 16, 16, 16)

        // --- Konten Profil ---
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
        infoLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)

        val tvUsername = TextView(this)
        tvUsername.text = peminat.username
        tvUsername.setTextColor(ContextCompat.getColor(this, R.color.kindbox_text_color))
        tvUsername.typeface = android.graphics.Typeface.DEFAULT_BOLD
        tvUsername.textSize = 14f
        infoLayout.addView(tvUsername)

        val tvLocation = TextView(this)
        tvLocation.text = peminat.location
        tvLocation.textSize = 12f
        tvLocation.setTextColor(ContextCompat.getColor(this, R.color.black))
        infoLayout.addView(tvLocation)

        // --- RATING BAR DINAMIS UNTUK PEMINAT ---
        val ratingBar = RatingBar(this, null, android.R.attr.ratingBarStyleSmall)
        val ratingParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        ratingParams.topMargin = 4.dpToPx()
        ratingBar.layoutParams = ratingParams

        ratingBar.numStars = 5
        ratingBar.stepSize = 0.1f

        // Set Nilai Rating
        ratingBar.rating = peminat.averageRating.toFloat()

        // Set Warna Kuning (Pakai Setter untuk menghindari error 'val cannot be reassigned')
        ratingBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.kindbox_yellow)))
        ratingBar.setSecondaryProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.kindbox_yellow)))
        ratingBar.setProgressBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.darker_gray)))

        infoLayout.addView(ratingBar)
        // ---------------------------------------------

        contentLayout.addView(infoLayout)
        card.addView(contentLayout)

        // --- Status dan Tombol Aksi ---
        val statusText = TextView(this)
        statusText.setPadding(0, 16.dpToPx(), 0, 0)
        statusText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        statusText.textSize = 12f

        when (peminat.interestStatus) {
            "VERIFIED" -> {
                statusText.text = "Peminat ini telah diterima!"
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                card.addView(statusText)
            }
            "REJECTED" -> {
                statusText.text = "Peminat ini telah ditolak!"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.kindbox_red))
                card.addView(statusText)
            }
            "PENDING" -> {
                val isStockZero = availableStock <= 0

                if (isStockZero) {
                    val stockWarning = TextView(this)
                    stockWarning.text = getString(R.string.stock_zero_owner_warning)
                    stockWarning.textSize = 12f
                    stockWarning.setTextColor(ContextCompat.getColor(this, R.color.kindbox_red))
                    stockWarning.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    stockWarning.setPadding(0, 0, 0, 8.dpToPx())
                    card.addView(stockWarning)
                }

                statusText.text = getString(R.string.pending_interest_status, peminat.quantityRequested)
                statusText.setTextColor(ContextCompat.getColor(this, R.color.kindbox_text_color))
                card.addView(statusText)

                val actionLayout = LinearLayout(this)
                actionLayout.orientation = LinearLayout.HORIZONTAL
                actionLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dpToPx() }

                val buttonColorActive = ContextCompat.getColor(this, R.color.kindbox_dark_green)
                val buttonColorDisabled = ContextCompat.getColor(this, android.R.color.darker_gray)
                val rejectColorActive = ContextCompat.getColor(this, R.color.kindbox_red)

                // Tombol Verifikasi
                val btnVerify = Button(this)
                btnVerify.text = "Terima"
                btnVerify.textSize = 12f
                btnVerify.isEnabled = !isStockZero
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
                btnReject.isEnabled = !isStockZero
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

    // LOGIKA PERBAIKAN: Hanya update interestedUserIds di Firestore, dan update interestedCount di UI secara lokal.
    private fun incrementInterestedCount(donationId: String) {
        val userId = currentUserId ?: return
        val docRef = db.collection("donations").document(donationId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentList = snapshot.get("interestedUserIds") as? List<*> ?: emptyList<String>()
            val verifiedList = snapshot.get("verifiedRecipients") as? List<*> ?: emptyList<String>()
            val rejectedList = snapshot.get("rejectedRecipients") as? List<*> ?: emptyList<String>()

            // Cek apakah user sudah ada di salah satu list (PENDING/VERIFIED/REJECTED)
            val isAlreadyAssociated = currentList.contains(userId) || verifiedList.contains(userId) || rejectedList.contains(userId)

            if (!isAlreadyAssociated) {
                // HANYA TAMBAHKAN KE interestedUserIds (PENDING)
                transaction.update(docRef, "interestedUserIds", FieldValue.arrayUnion(userId))
            }
        }.addOnSuccessListener {
            Toast.makeText(this, "Minat berhasil dicatat!", Toast.LENGTH_SHORT).show()
            binding.btnMinat.isEnabled = false
            binding.btnMinat.text = "Sudah Minat (Pending)"

            // Update interestedCount di UI lokal dan entity lokal
            if (donationItem != null) {
                // Jumlah peminat total (interestedCount) bertambah 1 karena user ini baru
                val newCount = donationItem!!.interestedCount + 1
                binding.tvDetailPeminat.text = newCount.toString()

                // Update juga entity lokal untuk mencegah double-tap/re-check error
                donationItem = donationItem!!.copy(
                    interestedCount = newCount,
                    interestedUsers = if (donationItem!!.interestedUsers.isEmpty()) userId else "${donationItem!!.interestedUsers},$userId"
                )
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal mencatat minat", Toast.LENGTH_SHORT).show()
        }
    }
}