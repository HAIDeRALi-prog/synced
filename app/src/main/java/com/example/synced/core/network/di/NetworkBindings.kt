package com.example.synced.core.network.di

import com.example.synced.core.network.ConnectivityManagerNetworkMonitor
import com.example.synced.core.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
}
