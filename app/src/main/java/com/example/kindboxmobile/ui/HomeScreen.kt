package com.example.kindboxmobile.ui

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kindboxmobile.AddDonationActivity
import com.example.kindboxmobile.MainActivity
import com.example.kindboxmobile.ProfileActivity
import com.example.kindboxmobile.R
import com.example.kindboxmobile.data.DonationEntity

// Extension function to get color from resources
@Composable
private fun colorResource(id: Int): Color {
    return Color(LocalContext.current.resources.getColor(id, LocalContext.current.theme))
}

// === 1. Komponen Utama Layar Beranda ===

@Composable
fun HomeScreen(
    filteredDonations: List<DonationEntity>,
    userName: String?,
    userLocation: String?,
    userPhotoUrl: String?,
    onItemClick: (DonationEntity) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onRefresh: () -> Unit,
    currentCategoryFilter: String,
    onCategoryFilterChange: (String) -> Unit
) {
    Scaffold(
        bottomBar = { HomeBottomBar(onRefresh) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->

        // Menggunakan Box untuk layering Header/Search dan Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colorResource(id = R.color.white))
        ) {
            // Konten Header (Area Hijau)
            HomeHeader(userName = userName, userLocation = userLocation, userPhotoUrl = userPhotoUrl)

            // Container untuk Search dan Grid (Grid akan start lebih tinggi)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 85.dp) // <<< PERBAIKAN UTAMA LAYOUT: Menggeser semua konten ke atas
            ) {
                // Search Bar + Filter
                SearchFilterRow(
                    onSearchSubmit = onSearchSubmit,
                    onClear = { onSearchSubmit("") },
                    currentCategoryFilter = currentCategoryFilter,
                    onCategoryFilterChange = onCategoryFilterChange
                )

                // Grid Donasi (Ini akan berada tepat di bawah SearchFilterRow, menghilangkan kotak putih)
                DonationContentGrid(
                    donations = filteredDonations,
                    onItemClick = onItemClick
                )
            }
        }
    }
}


// === 2. Komponen Header (Fix: Tinggi Header, Foto Profil) ===

@Composable
fun HomeHeader(userName: String?, userLocation: String?, userPhotoUrl: String?) {
    val context = LocalContext.current
    val kindboxPrimary = colorResource(id = R.color.kindbox_primary)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(kindboxPrimary)
            .padding(top = 15.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Greeting & Profile Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hi, ${userName ?: "Pengguna"}!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )

                // PERBAIKAN: Ganti Icon dengan Foto Profil
                Card(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { context.startActivity(Intent(context, ProfileActivity::class.java)) }
                ) {
                    AsyncImage(
                        model = userPhotoUrl.takeIf { !it.isNullOrEmpty() } ?: R.mipmap.ic_launcher_round,
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Lokasi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_location_pin),
                    contentDescription = "Lokasi",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(1.dp))
                Text(
                    text = "Berada di ${userLocation ?: "Lokasi belum diatur"}",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


// === 3. Komponen Search Bar dan Filter (Fix: Layout & Filter Aktif) ===

@Composable
fun SearchFilterRow(
    onSearchSubmit: (String) -> Unit,
    onClear: () -> Unit,
    currentCategoryFilter: String,
    onCategoryFilterChange: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val view = LocalView.current

    // State untuk Dropdown Menu
    var expanded by remember { mutableStateOf(false) }
    val categories = stringArrayResource(id = R.array.donation_categories_filter)

    val whiteColor = colorResource(id = R.color.white)
    val kindboxLightGray = colorResource(id = R.color.kindbox_light_gray)

    Row(
        // PERBAIKAN: Layout rapi karena padding top ditangani di Column container
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TextField (Search)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
            },
            placeholder = { Text("Cari di KindBox") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF666666)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onClear()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = Color(0xFF666666))
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = whiteColor,
                unfocusedContainerColor = whiteColor,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchSubmit(searchQuery)
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Tombol Filter & Dropdown
        Box {
            IconButton(
                onClick = { expanded = true }, // <<< Mengaktifkan dropdown
                modifier = Modifier
                    .size(45.dp)
                    .background(kindboxLightGray, RoundedCornerShape(8.dp))
            ) {
                Icon(painterResource(id = android.R.drawable.ic_menu_sort_by_size), contentDescription = "Filter", tint = Color(0xFF666666))
            }

            // Dropdown Menu untuk Filter Kategori
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category,
                            fontWeight = if (category == currentCategoryFilter) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onCategoryFilterChange(category) // Panggil callback filter
                            expanded = false
                        },
                        // Tunjukkan item yang sedang terpilih
                        trailingIcon = {
                            if (category == currentCategoryFilter) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = colorResource(id = R.color.kindbox_primary))
                            }
                        }
                    )
                }
            }
        }
    }
}


// === 4. Komponen Grid Konten Donasi (Fix: Padding Grid) ===

@Composable
fun DonationContentGrid(
    donations: List<DonationEntity>,
    onItemClick: (DonationEntity) -> Unit
) {
    // LazyVerticalGrid akan mengisi sisa ruang setelah SearchBox
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {

        // Warning Banner (Sesuai logic di activity_main.xml)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.kindbox_warning_bg)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📢 Perhatian! 📢",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Platform ini bukan tempat jual beli. Harap gunakan sesuai tujuan komunitas. Dilarang melakukan transaksi keuangan dalam platform ini. Tetap bijak dan aman dalam berinteraksi!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = Color.DarkGray
                    )
                }
            }
        }

        // Teks "Rekomendasi Untukmu"
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Rekomendasi Untukmu",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }

        if (donations.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Belum ada donasi yang ditampilkan.",
                    modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        } else {
            // Item Donasi
            items(donations, key = { it.id }) { donation ->
                DonationCard(donation = donation, onClick = onItemClick)
            }
        }
    }
}


// === 5. Komponen Kartu Donasi (Tetap Sama) ===

@Composable
fun DonationCard(
    donation: DonationEntity,
    onClick: (DonationEntity) -> Unit
) {
    // Hitung Sisa Stok
    val remainingStock = donation.quantity - donation.interestedCount
    val displayStock = if (remainingStock < 0) 0 else remainingStock

    val kindboxDarkGreen = colorResource(id = R.color.kindbox_dark_green)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(donation) },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.bg_card_green_outline)),
        border = BorderStroke(1.5.dp, colorResource(id = R.color.kindbox_light_green))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Gambar Donasi
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = donation.imageUrl.ifEmpty { R.drawable.ic_launcher_foreground },
                    contentDescription = donation.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nama Donasi
            Text(
                text = donation.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Quantity/Stock
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Quantity",
                    modifier = Modifier.size(14.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Stok: $displayStock / ${donation.quantity}",
                    fontSize = 11.sp,
                    color = Color.Black
                )
            }

            // Lokasi
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                    contentDescription = "Location",
                    modifier = Modifier.size(14.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = donation.location,
                    fontSize = 11.sp,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tombol "Lihat Detail"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(kindboxDarkGreen)
                    .clickable { onClick(donation) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lihat Detail",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// === 6. Komponen Bottom Bar (Tetap Sama) ===

@Composable
fun HomeBottomBar(onHomeClick: () -> Unit) {
    val context = LocalContext.current
    val kindboxPrimary = colorResource(id = R.color.kindbox_primary)
    val whiteColor = colorResource(id = R.color.white)

    NavigationBar(
        containerColor = kindboxPrimary,
        modifier = Modifier.height(60.dp)
    ) {
        // Navigasi Item
        data class NavItem(val icon: Int, val destination: Class<*>)
        val items = listOf(
            NavItem(R.drawable.ic_home, MainActivity::class.java),
            NavItem(R.drawable.ic_plus, AddDonationActivity::class.java),
            NavItem(R.drawable.ic_person, ProfileActivity::class.java)
        )

        items.forEachIndexed { index, item ->
            val isHome = index == 0
            val isAdd = index == 1
            val iconSize = if (isAdd) 33.dp else 30.dp

            NavigationBarItem(
                selected = isHome,
                onClick = {
                    if (isHome) {
                        onHomeClick()
                    } else {
                        context.startActivity(Intent(context, item.destination))
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = null,
                        tint = whiteColor,
                        modifier = Modifier
                            .padding(if (isAdd) 0.dp else 0.dp)
                            .size(iconSize)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = kindboxPrimary,
                    selectedIconColor = whiteColor,
                    unselectedIconColor = whiteColor,
                )
            )
        }
    }
}