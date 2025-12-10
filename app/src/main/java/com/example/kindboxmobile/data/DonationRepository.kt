package com.example.kindboxmobile.data

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class DonationRepository(
    private val donationDao: DonationDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    val allDonations: Flow<List<DonationEntity>> = donationDao.getAllDonations()

    suspend fun refreshDonations() = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("donations").get().await()
            val donations = snapshot.documents.map { doc ->
                val interestedList = doc.get("interestedUserIds") as? List<String> ?: emptyList()
                val interestedString = interestedList.joinToString(",")

                DonationEntity(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    title = doc.getString("title") ?: "Tanpa Judul",
                    description = doc.getString("description") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    location = doc.getString("location") ?: "Lokasi Tidak Tersedia",
                    quantity = (doc.get("quantity") as? Number)?.toInt() ?: 1,
                    interestedCount = (doc.get("interestedCount") as? Number)?.toInt() ?: 0,
                    category = doc.getString("category") ?: "Lainnya",
                    condition = doc.getString("condition") ?: "Layak Pakai",
                    whatsappNumber = doc.getString("whatsappNumber") ?: "",
                    interestedUsers = interestedString
                )
            }
            donationDao.insertAll(donations)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi Upload Donasi Baru
    suspend fun uploadDonation(
        title: String, desc: String, imageUri: Uri?, location: String,
        quantity: Int, category: String, condition: String, whatsapp: String, userId: String
    ) {
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            var downloadUrl = ""

            if (imageUri != null) {
                downloadUrl = uploadToCloudinary(imageUri, id)
            }

            val newDonation = DonationEntity(
                id = id, userId = userId, title = title, description = desc,
                imageUrl = downloadUrl, location = location, quantity = quantity,
                category = category, condition = condition, whatsappNumber = whatsapp,
                interestedUsers = ""
            )

            val firestoreData = mapOf(
                "id" to id, "userId" to userId, "title" to title, "description" to desc,
                "imageUrl" to downloadUrl, "location" to location, "quantity" to quantity,
                "category" to category, "condition" to condition, "whatsappNumber" to whatsapp,
                "interestedCount" to 0,
                "interestedUserIds" to listOf<String>()
            )

            firestore.collection("donations").document(id).set(firestoreData).await()
            donationDao.insert(newDonation)
        }
    }

    // --- FITUR BARU: Update Donasi ---
    suspend fun updateDonation(
        id: String,
        title: String, desc: String, imageUri: Uri?, oldImageUrl: String,
        location: String, quantity: Int, category: String, condition: String, whatsapp: String
    ) {
        withContext(Dispatchers.IO) {
            var downloadUrl = oldImageUrl

            // Jika user memilih foto baru, upload ke Cloudinary
            if (imageUri != null) {
                downloadUrl = uploadToCloudinary(imageUri, id)
            }

            // Update Firestore
            val updates = mapOf(
                "title" to title,
                "description" to desc,
                "imageUrl" to downloadUrl,
                "location" to location,
                "quantity" to quantity,
                "category" to category,
                "condition" to condition,
                "whatsappNumber" to whatsapp
            )

            firestore.collection("donations").document(id).update(updates).await()

            // Update Room (Lokal) agar langsung berubah tanpa refresh
            // Kita perlu mengambil data lama user ID dll, tapi karena update room parsial susah,
            // kita refresh saja atau biarkan sync berikutnya.
            // Opsi cepat: Trigger refresh
            refreshDonations()
        }
    }

    // Fungsi Helper Cloudinary
    private suspend fun uploadToCloudinary(uri: Uri, fileName: String): String = suspendCoroutine { continuation ->
        MediaManager.get().upload(uri).unsigned("kindbox").option("public_id", "barang_$fileName")
            .callback(object : UploadCallback {
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    continuation.resume(resultData?.get("secure_url").toString())
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    continuation.resumeWithException(Exception("Gagal Upload: ${error?.description}"))
                }
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }
}