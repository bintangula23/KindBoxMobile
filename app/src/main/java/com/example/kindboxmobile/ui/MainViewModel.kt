package com.example.kindboxmobile.ui

import androidx.lifecycle.*
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.data.DonationRepository
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainViewModel(private val repository: DonationRepository) : ViewModel() {

    // Data Donasi
    val donations: LiveData<List<DonationEntity>> = repository.allDonations.asLiveData()

    // Data User untuk Compose Header
    private val _userName = MutableLiveData<String?>()
    val userName: LiveData<String?> = _userName

    private val _userLocation = MutableLiveData<String?>()
    val userLocation: LiveData<String?> = _userLocation

    // Data Foto Profil
    private val _userPhotoUrl = MutableLiveData<String?>()
    val userPhotoUrl: LiveData<String?> = _userPhotoUrl

    // State Filter Kategori saat ini (default: Semua Kategori)
    private val _currentCategoryFilter = MutableLiveData("Semua Kategori")
    val currentCategoryFilter: LiveData<String> = _currentCategoryFilter

    // Internal state untuk menyimpan search query
    private val _currentSearchQuery = MutableLiveData("")

    // Setter Publik untuk memperbarui Lokasi dari Activity
    fun setUserLocation(location: String) {
        _userLocation.value = location
    }

    // Setter Publik untuk memperbarui Filter Kategori dari Compose
    fun setCategoryFilter(category: String) {
        _currentCategoryFilter.value = category
        // Memicu filter ulang melalui MediatorLiveData
    }

    // Setter Publik untuk memperbarui Search Query dari Compose
    fun setSearchQuery(query: String) {
        _currentSearchQuery.value = query
        // Memicu filter ulang melalui MediatorLiveData
    }


    // Data untuk menampung list yang sudah difilter/search
    private val _filteredDonations = MediatorLiveData<List<DonationEntity>>()
    val filteredDonations: LiveData<List<DonationEntity>> = _filteredDonations

    init {
        fetchUserData()

        // Atur sumber data filteredDonations untuk mengamati perubahan di tiga LiveData
        _filteredDonations.addSource(donations) { allDonations ->
            filterDonations(allDonations, _currentCategoryFilter.value ?: "Semua Kategori", _currentSearchQuery.value ?: "")
        }
        _filteredDonations.addSource(_currentCategoryFilter) { category ->
            filterDonations(donations.value ?: emptyList(), category, _currentSearchQuery.value ?: "")
        }
        _filteredDonations.addSource(_currentSearchQuery) { query ->
            filterDonations(donations.value ?: emptyList(), _currentCategoryFilter.value ?: "Semua Kategori", query)
        }
    }

    fun refreshData() {
        viewModelScope.launch { repository.refreshDonations() }
    }

    // Fungsi utama filter (Dipanggil internal oleh MediatorLiveData)
    private fun filterDonations(
        allDonations: List<DonationEntity>,
        categoryFilter: String,
        searchQuery: String
    ) {
        val myUserId = FirebaseAuth.getInstance().currentUser?.uid

        // REVISI DI SINI:
        // Filter awal:
        // 1. Barang bukan punya user yang sedang login (it.userId != myUserId)
        // 2. Stok barang harus lebih dari 0 (it.quantity > 0)
        var filteredList = allDonations.filter {
            it.userId != myUserId && it.quantity > 0
        }

        val search = searchQuery.trim()

        // 1. Filter berdasarkan Kategori
        if (categoryFilter != "Semua Kategori") {
            filteredList = filteredList.filter { it.category == categoryFilter }
        }

        // 2. Filter berdasarkan Search Query
        if (search.isNotEmpty()) {
            filteredList = filteredList.filter {
                it.title.contains(search, ignoreCase = true) ||
                        it.description.contains(search, ignoreCase = true)
            }
        }

        _filteredDonations.value = filteredList
    }

    fun fetchUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        _userName.value = document.getString("name") ?: "User KindBox"
                        _userPhotoUrl.value = document.getString("photoUrl") // Ambil Photo URL

                        if (_userLocation.value.isNullOrEmpty() || _userLocation.value == "Memuat lokasi...") {
                            setUserLocation(document.getString("location") ?: "Lokasi belum diatur")
                        }
                    }
                }
        }
    }
}