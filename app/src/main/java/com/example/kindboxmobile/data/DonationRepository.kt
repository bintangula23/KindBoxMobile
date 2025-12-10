package com.example.kindboxmobile.data

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
// HAPUS: import com.cloudinary.android.signed
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
                // Upload Baru: Menggunakan Signed Upload
                downloadUrl = uploadToCloudinary(imageUri, "barang_$id")
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

    // FITUR UPDATE DONASI
    suspend fun updateDonation(
        id: String,
        title: String, desc: String, imageUri: Uri?, oldImageUrl: String,
        location: String, quantity: Int, category: String, condition: String, whatsapp: String
    ) {
        withContext(Dispatchers.IO) {
            var downloadUrl = oldImageUrl

            // Perbaikan: Jika ada image baru, lakukan Signed Upload menggunakan public_id yang sama untuk OVERWRITE.
            if (imageUri != null) {
                downloadUrl = uploadToCloudinary(imageUri, "barang_$id")
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
            refreshDonations()
        }
    }

    // Fungsi Helper Cloudinary: Menggunakan Signed Upload (Versi Java Standar)
    private suspend fun uploadToCloudinary(uri: Uri, publicId: String?): String = suspendCoroutine { continuation ->

        // FIX: Menggunakan versi yang tidak memanggil .signed() secara eksplisit
        val request = MediaManager.get().upload(uri)

        if (publicId != null) {
            request.option("public_id", publicId)
            request.option("overwrite", true) // Aktifkan overwrite karena ini Signed Upload
            request.option("signature", null) // Tambahkan signature null untuk memastikan Signed Upload
        }

        request.callback(object : UploadCallback {
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