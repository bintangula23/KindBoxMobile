package com.example.kindboxmobile

import android.app.Application
import androidx.room.Room
import com.cloudinary.android.MediaManager
import com.example.kindboxmobile.data.AppDatabase
import com.example.kindboxmobile.data.DonationRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MyApplication : Application() {

    // ******************************************************************************
    // ** PERINGATAN KEAMANAN KRITIS **
    // API Secret tidak boleh disimpan di aplikasi klien. Ubah ini jika ke produksi!
    // ******************************************************************************
    private val CLOUDINARY_CLOUD_NAME = "du0khgjtj"
    private val CLOUDINARY_API_KEY = "749337114895541" // API Key Anda
    private val CLOUDINARY_API_SECRET = "6aOeSWHBL7OusNK1SP7Q0H9ghbs" // API Secret Anda

    private val database by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "kindbox_db").build()
    }

    private val donationDao by lazy { database.donationDao() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    val donationRepository by lazy {
        DonationRepository(donationDao, firestore, storage)
    }

    override fun onCreate() {
        super.onCreate()

        // Konfigurasi Cloudinary untuk Signed Uploads (Implisit)
        // Kehadiran api_key dan api_secret yang membuat Signed Upload aktif.
        val config = HashMap<String, String>()
        config["cloud_name"] = CLOUDINARY_CLOUD_NAME
        config["api_key"] = CLOUDINARY_API_KEY
        config["api_secret"] = CLOUDINARY_API_SECRET

        MediaManager.init(this, config)
    }
}