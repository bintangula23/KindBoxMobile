package com.example.kindboxmobile

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = HashMap<String, String>()
        config["cloud_name"] = "du0khgjtj"
        config["secure"] = "true"

        MediaManager.init(this, config)
    }
}