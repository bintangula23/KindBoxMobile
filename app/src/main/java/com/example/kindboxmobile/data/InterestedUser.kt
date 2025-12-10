package com.example.kindboxmobile.data

/**
 * Model data untuk merepresentasikan seorang peminat di detail donasi.
 * Data ini tidak disimpan di Room, melainkan langsung diambil dari Firestore
 * dengan me-resolve user ID dari daftar interestedUserIds.
 */
data class InterestedUser(
    val userId: String,
    val name: String,
    val username: String,
    val location: String,
    val photoUrl: String? = null,
    // CATATAN: Karena tidak ada tabel 'interests' di Firestore Anda,
    // kita akan menggunakan status minat 'implicit' dari daftar interestedUserIds.
    // Jika ada sistem interest yang lebih kompleks (seperti di PHP Anda),
    // struktur Firestore perlu disesuaikan (misal: sub-collection 'interests').
    // Untuk implementasi ini, kita HANYA akan menggunakan status:
    // "INTERESTED" (default dari daftar)
    // "VERIFIED" (akan menjadi penerima)
    // "REJECTED" (harus ditangani di struktur Firestore yang lebih kompleks)

    // KARENA ANDA TIDAK MENGUBAH DATA MODEL DI FIREBASE/ROOM:
    // Kita akan menggunakan pendekatan sederhana: semua yang ada di 'interestedUserIds' adalah "PENDING"
    // Status 'VERIFIED' dan 'REJECTED' harus disimpan di field tambahan di DonationEntity/Firestore,
    // atau di sub-collection baru.

    // SEMENTARA, kita asumsikan 'status' berikut untuk memfasilitasi logika verifikasi:
    val interestStatus: String = "PENDING", // PENDING, VERIFIED, REJECTED
    val quantityRequested: Int = 1 // Jumlah barang yang diminta peminat (asumsi 1 karena tidak ada form minat di AddDonationActivity.kt)
)