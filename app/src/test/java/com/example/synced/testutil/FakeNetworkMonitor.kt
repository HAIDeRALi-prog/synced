package com.example.synced.testutil

import com.example.synced.core.network.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline

    fun setOnline(value: Boolean) {
        _isOnline.value = value
    }
}
