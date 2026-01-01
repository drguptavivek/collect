# Network & Reachability Architecture

This document details the network monitoring and server reachability logic used by the AIIMS Auth Module to manage online/offline states and grace period re-evaluation.

## 1. Network State Monitoring
We use a **reactive** approach to monitor network connectivity rather than polling.

### `AiimsNetworkStateMonitor`
The `AiimsNetworkStateMonitor` (and its implementation `AiimsNetworkStateMonitorImpl`) provides a `Flow<Boolean>` that emits real-time connectivity updates.

-   **Mechanism**: Uses Android's `ConnectivityManager.NetworkCallback`.
-   **Capability Check**: Verifies `NetworkCapabilities.NET_CAPABILITY_INTERNET`, filtering out local-only connections (e.g., printer Wi-Fi) that cannot reach the server.
-   **Debouncing**: The system enforces a **30-second cooldown** on re-checks to prevent "thrashing" during unstable network conditions (flapping connections).

## 2. Server Reachability Checks
Merely having an internet connection doesn't guarantee the AIIMS server is reachable. Verification is done via **Real HTTP Requests**.

### Implementation: `RealAuthClient.checkReachability()`
This function performs the definitive check for server availability.

-   **Endpoint**: `HEAD /version.txt` (or root `/` if unavailable).
    -   **Why HEAD?**: Minimizes data usage; checks responsiveness without downloading a body.
    -   **Why version.txt?**: A lightweight, static resource that bypasses complex application logic/DB queries for the fastest possible response.
-   **Caching**: Results are cached for **2 minutes** (`REACHABILITY_TTL_MS`) to reduce server load.

### Stability & "Good Connection" Criteria
We do not explicitly measure signal strength (dBm). Instead, **stability is enforced via strict timeouts**:

1.  **Connection Timeout**: **3,000ms (3 seconds)**.
2.  **Read Timeout**: **3,000ms (3 seconds)**.

**Implication**: Any connection that takes longer than 3 seconds to establish or respond is treated as **Unreachable**. This effectively filters out:
-   Edge/2G networks with extreme latency.
-   Congested Wi-Fi with high packet loss.
-   "Captive Portal" networks where the internet capability check passed but traffic is blocked.

## 3. Automatic Grace Period Re-evaluation (Scenario 59)
To prevent unauthorized offline access, the system automatically re-evaluates the user's session when the network is restored.

**Workflow:**
1.  **Trigger**: `AiimsNetworkStateMonitor` emits `true` (Internet available).
2.  **Condition**: User is currently in the **Grace Period** (Token Expired AND `checkReachability` failed previously).
3.  **Action**: `AiimsAuthManager` triggers `refreshState()`.
4.  **Verification**: `refreshState()` calls `checkReachability()`.
5.  **Outcome**:
    -   **Success**: `isSoftExpiry` becomes `true`. The UI shows the Re-authentication Prompt.
    -   **Failure**: `isSoftExpiry` remains `false`. User continues in Offline Grace mode.

This ensures that a user cannot bypass the re-auth prompt by simply turning off data, waiting for expiry, and then turning it back on. The moment actionable connectivity is restored, the re-check fires.
