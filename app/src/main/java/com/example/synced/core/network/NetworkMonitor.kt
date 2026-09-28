package com.example.synced.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over connectivity so ViewModels/tests never touch ConnectivityManager.
 * Emits true when the device has a network with internet capability, false otherwise.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

@Singleton
class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        // Track every active network rather than a single one: a phone can lose Wi-Fi
        // while cellular is still up, and we only go "offline" when all are gone.
        val activeNetworks = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                activeNetworks -= network
                trySend(activeNetworks.isNotEmpty())
            }
        }

        // Seed the flow with the connection state that already exists when we start
        // observing — onAvailable alone would miss networks connected before this point.
        connectivityManager.activeNetwork?.takeIf { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }?.let {
            activeNetworks += it
            trySend(true)
        } ?: trySend(false)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
