package com.example.planyourtrip.data.sync

import kotlinx.coroutines.flow.StateFlow

/** Whether the device has a working internet connection. [AndroidNetworkMonitor] in the app, a fake in tests. */
interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}
