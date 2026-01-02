package edu.aiims.medresodk.auth.utils

import kotlinx.coroutines.flow.Flow

interface MedresNetworkStateMonitor {
    /**
     * Emits true when the network becomes reachable (with Internet capability).
     * Emits false when the network is lost.
     */
    val isNetworkAvailable: Flow<Boolean>
}
