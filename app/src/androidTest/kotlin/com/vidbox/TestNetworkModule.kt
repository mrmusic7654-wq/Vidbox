package com.vidbox

import com.vidbox.data.di.NetworkBindings
import com.vidbox.domain.model.NetworkStatus
import com.vidbox.domain.repository.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkBindings::class])
object TestNetworkModule {
    @Provides @Singleton fun network(): NetworkMonitor = object : NetworkMonitor {
        // The test server is on loopback and does not need Google's internet-validation endpoint.
        override val status = MutableStateFlow(NetworkStatus(true, wifi = true, metered = false))
    }
}
