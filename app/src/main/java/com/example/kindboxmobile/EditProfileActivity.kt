package com.example.kindboxmobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.kindboxmobile.databinding.ActivityEditProfileBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var imageUri: Uri? = null
    private var currentPhotoPath: String? = null

    // --- LAUNCHERS ---
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) getMyLocation() else Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivProfile.setImageURI(uri)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            binding.ivProfile.setImageURI(imageUri)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startCamera() else Toast.makeText(this, "Izin kamera diperlukan", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()

        binding.btnBack.setOnClickListener { finish() }

        // Setup Ikon Pensil pada Text Field
        setupFieldEditing(binding.etName)
        setupFieldEditing(binding.etUsername)
        setupFieldEditing(binding.etLocation)
        setupFieldEditing(binding.etPassword)

        // Edit Foto Profil
        binding.ivEditPhoto.setOnClickListener {
            showImageSourceDialog()
        }

        // Deteksi Lokasi Otomatis (GPS)
        binding.btnDetectLocation.setOnClickListener {
            checkLocationPermission()
        }

        // Submit Data
        binding.btnSubmit.setOnClickListener {
            updateProfile()
        }

        // Hapus Akun
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    // --- FUNGSI UNTUK MENGAKTIFKAN EDIT TEXT SAAT KLIK PENSIL ---
    @SuppressLint("ClickableViewAccessibility")
    private fun setupFieldEditing(editText: EditText) {
        editText.setOnTouchListener { v, event ->
            val DRAWABLE_RIGHT = 2
            if (event.action == MotionEvent.ACTION_UP) {
                // Cek apakah klik berada di area ikon kanan (pensil)
                if (event.rawX >= (editText.right - editText.compoundDrawables[DRAWABLE_RIGHT].bounds.width() - 50)) { // + buffer

                    // Aktifkan Mode Edit
                    editText.isFocusableInTouchMode = true
                    editText.isFocusable = true
                    editText.requestFocus()

                    // Munculkan Keyboard
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

                    // Ubah ikon jadi 'check' atau hilangkan (opsional, di sini kita biarkan pensil)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser
        val userId = user?.uid ?: return

        binding.etEmail.setText(user.email)

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    binding.etName.setText(document.getString("name"))
                    binding.etUsername.setText(document.getString("username"))
                    binding.etLocation.setText(document.getString("location"))

                    // Load Foto Profil dengan Glide
                    val photoUrl = document.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this).load(photoUrl).into(binding.ivProfile)
                    }
                }
            }
    }

    // --- LOGIKA FOTO PROFIL ---
    private fun showImageSourceDialog() {
        val options = arrayOf("Ambil Foto (Kamera)", "Pilih dari Galeri")
        AlertDialog.Builder(this)
            .setTitle("Ganti Foto Profil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> galleryLauncher.launch("image/*")
                }
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
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                photoFile
            )
            imageUri = photoURI
            cameraLauncher.launch(photoURI)
        } catch (ex: Exception) {
            Toast.makeText(this, "Error kamera: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    // --- LOGIKA UPDATE DATA ---
    private fun updateProfile() {
        val userId = auth.currentUser?.uid ?: return
        val name = binding.etName.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Menyimpan..."

        // Cek apakah ada foto baru yang perlu diupload
        if (imageUri != null) {
            uploadProfileImage(userId) { photoUrl ->
                saveToFirestore(userId, photoUrl)
            }
        } else {
            saveToFirestore(userId, null)
        }
    }

    private fun uploadProfileImage(userId: String, onComplete: (String) -> Unit) {
        // UPLOAD KE CLOUDINARY
        MediaManager.get().upload(imageUri)
            .unsigned("kindbox") // Preset kamu
            .option("public_id", "profil_$userId") // Nama file unik per user
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    onComplete(url) // Kembalikan URL ke fungsi simpan
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Toast.makeText(this@EditProfileActivity, "Gagal upload foto: ${error?.description}", Toast.LENGTH_SHORT).show()
                    saveToFirestore(userId, null)
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun saveToFirestore(userId: String, photoUrl: String?) {
        val name = binding.etName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val location = binding.etLocation.text.toString().trim()
        val newPassword = binding.etPassword.text.toString().trim()

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "username" to username,
            "location" to location
        )

        if (photoUrl != null) {
            updates["photoUrl"] = photoUrl
        }

        firestore.collection("users").document(userId).set(updates, SetOptions.merge())
            .addOnSuccessListener {
                if (newPassword.isNotEmpty()) {
                    auth.currentUser?.updatePassword(newPassword)
                        ?.addOnCompleteListener {
                            Toast.makeText(this, "Profil & Password diperbarui!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                } else {
                    Toast.makeText(this, "Profil diperbarui!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal simpan data: ${it.message}", Toast.LENGTH_SHORT).show()
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "Submit"
            }
    }

    // --- LOGIKA LOKASI ---
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            getMyLocation()
        }
    }

    private fun getMyLocation() {
        Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val locText = listOfNotNull(address.subLocality, address.locality, address.adminArea).joinToString(", ")
                            binding.etLocation.setText(locText)
                        } else {
                            binding.etLocation.setText("${location.latitude}, ${location.longitude}")
                        }
                    } catch (e: Exception) {
                        binding.etLocation.setText("${location.latitude}, ${location.longitude}")
                    }
                } else {
                    Toast.makeText(this, "Lokasi tidak ditemukan. Coba nyalakan GPS.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    // --- LOGIKA HAPUS AKUN ---
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Akun")
            .setMessage("Yakin ingin menghapus akun permanen?")
            .setPositiveButton("Hapus") { _, _ -> deleteAccount() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        val userId = user?.uid ?: return

        firestore.collection("users").document(userId).delete()
            .addOnSuccessListener {
                user.delete().addOnCompleteListener {
                    if (it.isSuccessful) {
                        Toast.makeText(this, "Akun terhapus", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
    }
}