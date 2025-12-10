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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

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

    // Fungsi Upload Donasi Baru (Tidak Berubah)
    suspend fun uploadDonation(
        title: String, desc: String, imageUri: Uri?, location: String,
        quantity: Int, category: String, condition: String, whatsapp: String, userId: String
    ) {
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            var downloadUrl = ""

            if (imageUri != null) {
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
                "interestedUserIds" to listOf<String>(),
                "verifiedRecipients" to listOf<String>(),
                "rejectedRecipients" to listOf<String>()
            )

            firestore.collection("donations").document(id).set(firestoreData).await()
            donationDao.insert(newDonation)
        }
    }

    // FITUR UPDATE DONASI (Tidak Berubah)
    suspend fun updateDonation(
        id: String,
        title: String, desc: String, imageUri: Uri?, oldImageUrl: String,
        location: String, quantity: Int, category: String, condition: String, whatsapp: String
    ) {
        withContext(Dispatchers.IO) {
            var downloadUrl = oldImageUrl

            if (imageUri != null) {
                downloadUrl = uploadToCloudinary(imageUri, "barang_$id")
            }

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

    // Fungsi Helper Cloudinary (Tidak Berubah)
    private suspend fun uploadToCloudinary(uri: Uri, publicId: String?): String = suspendCoroutine { continuation ->

        val request = MediaManager.get().upload(uri)

        if (publicId != null) {
            request.option("public_id", publicId)
            request.option("overwrite", true)
            request.option("signature", null)
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

    // BARU: Fungsi untuk memverifikasi peminat dan mengurangi stok menggunakan transaksi (REVISI FINAL)
    suspend fun verifyRecipientTransaction(
        donationId: String,
        recipientUserId: String,
        quantityRequested: Int
    ): String = withContext(Dispatchers.IO) {
        val donationRef = firestore.collection("donations").document(donationId)

        return@withContext try {
            firestore.runTransaction { transaction ->
                val donationSnapshot = transaction.get(donationRef)

                // 1. Validasi Stok KRITIS
                val currentQuantity = (donationSnapshot.get("quantity") as? Number)?.toInt() ?: 0

                // Cek Stok Habis
                if (currentQuantity <= 0) {
                    throw Exception("Stok barang sudah habis (0). Verifikasi dibatalkan.")
                }

                // Cek Stok Tidak Mencukupi
                if (currentQuantity < quantityRequested) {
                    throw Exception("Stok tidak mencukupi. Tersedia: $currentQuantity, Diminta: $quantityRequested. Verifikasi dibatalkan.")
                }

                // 2. Update Stok, Hapus dari PENDING, Tambah ke VERIFIED
                val newQuantity = currentQuantity - quantityRequested

                val updates = hashMapOf<String, Any>(
                    "quantity" to newQuantity, // PENTING: Pengurangan stok di sini
                    "interestedCount" to FieldValue.increment(-1),
                    // Pindah dari PENDING ke VERIFIED
                    "interestedUserIds" to FieldValue.arrayRemove(recipientUserId),
                    "verifiedRecipients" to FieldValue.arrayUnion(recipientUserId)
                )

                transaction.update(donationRef, updates)

                "Peminat berhasil diverifikasi. Stok telah berkurang menjadi $newQuantity."
            }.await()
        } catch (e: Exception) {
            e.message ?: "Gagal memverifikasi peminat."
        } finally {
            // Setelah transaksi, refresh data ke Room/UI
            refreshDonations()
        }
    }

    // BARU: Fungsi untuk menolak peminat (Tidak ada pengurangan stok)
    suspend fun rejectRecipient(
        donationId: String,
        recipientUserId: String
    ): String = withContext(Dispatchers.IO) {
        val donationRef = firestore.collection("donations").document(donationId)

        return@withContext try {
            firestore.runTransaction { transaction ->

                // Pindah dari PENDING ke REJECTED (Tanpa mengubah stok)
                val updates = hashMapOf<String, Any>(
                    "interestedCount" to FieldValue.increment(-1),
                    "interestedUserIds" to FieldValue.arrayRemove(recipientUserId),
                    "rejectedRecipients" to FieldValue.arrayUnion(recipientUserId)
                )
                transaction.update(donationRef, updates)

                "Peminat berhasil ditolak."
            }.await()
        } catch (e: Exception) {
            e.message ?: "Gagal menolak peminat."
        } finally {
            refreshDonations()
        }
    }
}