package com.cifiko.stoperica

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class StopericaApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable database persistence (moved from MainActivity)
        Firebase.database.setPersistenceEnabled(true)

        // Keep data synced
        Firebase.database.reference.keepSynced(true)

        // Initialize other Firebase services if needed
        // Firebase.analytics
        // Firebase.crashlytics
    }
}