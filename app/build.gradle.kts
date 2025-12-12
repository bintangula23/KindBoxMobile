plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    id("com.google.gms.google-services") version "4.4.0"
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.kindboxmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kindboxmobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    // Android Core - Using versions from your libs.versions.toml for consistency
    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1") // Keep as is, or manage in TOML
    implementation("com.google.android.material:material:1.10.0") // Keep as is, or manage in TOML

    // --- FIX STARTS HERE: IMPLEMENT JETPACK COMPOSE ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose) // You had this in TOML but not here

    // Lifecycle, ViewModel, and Activity
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Room Database (Local Cache)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Firebase (Backend Serverless)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Location (Maps)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Image Loading (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Test dependencies (from your libs.versions.toml)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    implementation("com.cloudinary:cloudinary-android:2.5.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // Sesuaikan versinya jika perlu
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0") // Penting untuk Activity Compose

    // Compose Lifecycle (untuk observe LiveData dengan aman)
    implementation("androidx.compose.runtime:runtime-livedata:1.6.8")

    // Coil - Image Loading untuk Compose (untuk memuat gambar Donasi)
    implementation("io.coil-kt:coil-compose:2.6.0")
}