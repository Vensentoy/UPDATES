package com.revyu.app.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lifecycle-aware connectivity helper that tracks real internet access via [ConnectivityManager.registerDefaultNetworkCallback].
 *
 * Uses [NetworkCapabilities.NET_CAPABILITY_VALIDATED] rather than plain network association so captive portals
 * or unvalidated local networks are not falsely reported as online.
 */
class NetworkObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialOnlineState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateOnlineState()
        }

        override fun onLost(network: Network) {
            updateOnlineState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = isValidated
        }
    }

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
            _isOnline.value = checkInitialOnlineState()
        }
    }

    private fun checkInitialOnlineState(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateOnlineState() {
        val cm = connectivityManager
        if (cm == null) {
            _isOnline.value = false
            return
        }
        val activeNetwork = cm.activeNetwork
        if (activeNetwork == null) {
            _isOnline.value = false
            return
        }
        val caps = cm.getNetworkCapabilities(activeNetwork)
        _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
}
