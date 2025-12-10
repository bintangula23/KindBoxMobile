package com.example.kindboxmobile.ui

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kindboxmobile.data.DonationRepository
import kotlinx.coroutines.launch

class AddDonationViewModel(private val repository: DonationRepository) : ViewModel() {

    private val _uploadStatus = MutableLiveData<Result<String>>()
    val uploadStatus: LiveData<Result<String>> = _uploadStatus

    // Parameter diperbarui (terima location string, quantity, dll)
    fun saveDonation(
        title: String,
        desc: String,
        uri: Uri?,
        location: String,
        quantity: Int,
        category: String,
        condition: String,
        whatsapp: String,
        userId: String
    ) {
        _uploadStatus.value = Result.loading()

        viewModelScope.launch {
            try {
                repository.uploadDonation(
                    title, desc, uri, location, quantity, category, condition, whatsapp, userId
                )
                _uploadStatus.value = Result.success("Berhasil Upload!")
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadStatus.value = Result.error("Gagal: ${e.message}")
            }
        }
    }
}

// Class Result helper (jika belum ada di file terpisah)
data class Result<out T>(val status: Status, val data: T?, val message: String?) {
    enum class Status { SUCCESS, ERROR, LOADING }
    companion object {
        fun <T> success(data: T): Result<T> = Result(Status.SUCCESS, data, null)
        fun <T> error(msg: String, data: T? = null): Result<T> = Result(Status.ERROR, data, msg)
        fun <T> loading(data: T? = null): Result<T> = Result(Status.LOADING, data, null)
    }
}