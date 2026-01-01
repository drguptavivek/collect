package org.aiims.odk.auth.utils

import kotlinx.coroutines.flow.Flow

interface AiimsNetworkStateMonitor {
    /**
     * Emits true when the network becomes reachable (with Internet capability).
     * Emits false when the network is lost.
     */
    val isNetworkAvailable: Flow<Boolean>
}
