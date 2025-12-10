package com.example.kindboxmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import com.example.kindboxmobile.databinding.ActivityAddDonationBinding
import com.example.kindboxmobile.ui.AddDonationViewModel
import com.example.kindboxmobile.ui.Result
import com.example.kindboxmobile.ui.ViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddDonationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddDonationBinding
    private lateinit var viewModel: AddDonationViewModel
    private var imageUri: Uri? = null
    private var currentPhotoPath: String? = null

    // Variabel untuk Mode Edit
    private var isEditMode = false
    private var editDonationId: String? = null
    private var existingImageUrl: String = ""

    // Launchers
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivPreview.setImageURI(uri)
        }
    }
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) binding.ivPreview.setImageURI(imageUri)
    }
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startCamera() else Toast.makeText(this, "Butuh izin kamera", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddDonationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        val repo = DonationRepository(db.donationDao(), FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
        viewModel = ViewModelProvider(this, ViewModelFactory(repo))[AddDonationViewModel::class.java]

        setupSpinners()
        setupObservers()
        setupBottomNavigation()

        // --- CEK APAKAH INI MODE EDIT? (Menggunakan metode aman untuk Parcelable) ---
        val editData: DonationEntity? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_EDIT_DATA", DonationEntity::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_EDIT_DATA")
        }

        if (editData != null) {
            setupEditMode(editData)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnChooseImage.setOnClickListener { showImageSourceDialog() }
        binding.cbUseProfileLocation.setOnCheckedChangeListener { _, isChecked -> if (isChecked) fetchProfileLocation() }

        binding.btnKirim.setOnClickListener {
            if (isEditMode) updateData() else uploadData()
        }
    }

    private fun setupEditMode(data: DonationEntity) {
        isEditMode = true
        editDonationId = data.id
        existingImageUrl = data.imageUrl

        // Mengubah judul tombol
        binding.btnKirim.text = "Simpan Perubahan"

        // Isi Form dengan Data Lama
        binding.etTitle.setText(data.title)
        binding.etDesc.setText(data.description)
        binding.etLocation.setText(data.location)
        binding.etQuantity.setText(data.quantity.toString())
        binding.etWhatsApp.setText(data.whatsappNumber)

        // Set Spinner Category
        val catAdapter = binding.spCategory.adapter as ArrayAdapter<String>
        val catPosition = catAdapter.getPosition(data.category)
        if (catPosition >= 0) binding.spCategory.setSelection(catPosition)

        // Set Spinner Condition
        val condAdapter = binding.spCondition.adapter as ArrayAdapter<String>
        val condPosition = condAdapter.getPosition(data.condition)
        if (condPosition >= 0) binding.spCondition.setSelection(condPosition)

        // Tampilkan Gambar Lama (Glide)
        if (data.imageUrl.isNotEmpty()) {
            Glide.with(this).load(data.imageUrl).into(binding.ivPreview)
        }
    }

    // --- LOGIKA NAVIGASI BAWAH ---
    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
        }
    }

    private fun setupObservers() {
        viewModel.uploadStatus.observe(this) { result ->
            when (result.status) {
                Result.Status.LOADING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnKirim.isEnabled = false
                    binding.btnKirim.text = if (isEditMode) "Menyimpan..." else "Mengupload..."
                }
                Result.Status.SUCCESS -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.data, Toast.LENGTH_SHORT).show()

                    // Setelah selesai, kembali ke MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                    finish()
                }
                Result.Status.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnKirim.isEnabled = true
                    binding.btnKirim.text = if (isEditMode) "Simpan Perubahan" else "Kirim"
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun uploadData() {
        // Logika simpan baru
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val whatsapp = binding.etWhatsApp.text.toString().trim()
        val quantityStr = binding.etQuantity.text.toString().trim()
        val quantity = if (quantityStr.isNotEmpty()) quantityStr.toInt() else 1
        val category = binding.spCategory.selectedItem.toString()
        val condition = binding.spCondition.selectedItem.toString()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (title.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Judul dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        if (imageUri == null) {
            Toast.makeText(this, "Foto wajib dipilih", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentUser == null) {
            Toast.makeText(this, "Login dulu!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveDonation(title, desc, imageUri, location, quantity, category, condition, whatsapp, currentUser.uid)
    }

    private fun updateData() {
        // Logika Update data
        val title = binding.etTitle.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val whatsapp = binding.etWhatsApp.text.toString().trim()
        val quantityStr = binding.etQuantity.text.toString().trim()
        val quantity = if (quantityStr.isNotEmpty()) quantityStr.toInt() else 1
        val category = binding.spCategory.selectedItem.toString()
        val condition = binding.spCondition.selectedItem.toString()

        if (title.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Judul dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        if (editDonationId == null) {
            Toast.makeText(this, "ID Donasi tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageUri == null && existingImageUrl.isEmpty()) {
            Toast.makeText(this, "Foto wajib ada, silakan pilih foto", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateDonation(
            id = editDonationId!!,
            title = title,
            desc = desc,
            uri = imageUri, // Jika null, akan pakai existingImageUrl
            oldImageUrl = existingImageUrl,
            location = location,
            quantity = quantity,
            category = category,
            condition = condition,
            whatsapp = whatsapp
        )
    }

    private fun setupSpinners() {
        val categories = arrayOf("Pilih Kategori", "Pakaian", "Buku", "Elektronik", "Lainnya")
        binding.spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        val conditions = arrayOf("Pilih Kondisi", "Baru", "Bekas")
        binding.spCondition.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, conditions)
    }

    private fun fetchProfileLocation() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener {
                val loc = it.getString("location")
                if (!loc.isNullOrEmpty()) binding.etLocation.setText(loc)
            }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Kamera", "Galeri")
        AlertDialog.Builder(this).setTitle("Pilih Foto")
            .setItems(options) { _, which ->
                if (which == 0) checkCameraPermissionAndOpen() else galleryLauncher.launch("image/*")
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        try {
            val photoFile = createImageFile()
            val photoURI = FileProvider.getUriForFile(this, "${packageName}.provider", photoFile)
            imageUri = photoURI
            cameraLauncher.launch(photoURI)
        } catch (e: Exception) { Toast.makeText(this, "Error Kamera", Toast.LENGTH_SHORT).show() }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply { currentPhotoPath = absolutePath }
    }
}