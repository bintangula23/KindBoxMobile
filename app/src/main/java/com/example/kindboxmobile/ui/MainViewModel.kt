package com.example.kindboxmobile.ui

import androidx.lifecycle.*
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: DonationRepository) : ViewModel() {
    val donations: LiveData<List<DonationEntity>> = repository.allDonations.asLiveData()

    fun refreshData() {
        viewModelScope.launch { repository.refreshDonations() }
    }
}