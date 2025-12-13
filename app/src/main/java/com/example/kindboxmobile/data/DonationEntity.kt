package com.example.kindboxmobile.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "donations")
data class DonationEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val title: String = "Tanpa Judul",
    val description: String = "",
    val imageUrl: String = "",
    val location: String = "Lokasi Tidak Tersedia",
    val quantity: Int = 1, // Sisa Stok Saat Ini (Ini yang akan berkurang)
    val originalQuantity: Int = 1, // Total Stok Awal (Ini yang tetap)
    val interestedCount: Int = 0, // Total Peminat (PENDING + VERIFIED + REJECTED)
    val category: String = "Lainnya",
    val condition: String = "Layak Pakai",
    val whatsappNumber: String = "",
    // BARU: Menyimpan ID user yang minat (contoh: "user1,user2,user3")
    val interestedUsers: String = ""
) : Parcelable