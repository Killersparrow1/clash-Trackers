package com.killersparrow1.clashtrackers.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when the phone has a network that can reach the internet. False when there is none or it cannot be checked. */
object Connectivity {
    fun isOnline(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
}
