package com.cifiko.stoperica

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (isNetworkAvailable(context)) {
            Log.d("NetworkChangeReceiver", "Network is available, retrying uploads")
            // Trigger session upload when network is available
            val mainActivity = context as? MainActivity
            mainActivity?.retryUploadSessions()
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}