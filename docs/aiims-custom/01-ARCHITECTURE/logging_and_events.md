# AIIMS Collect Logging & Event Strategy

> Last Updated: 2025-12-31

## Overview
This document outlines the logging and event tracking strategy for the AIIMS Auth Module. The system is designed to provide visibility into authentication flows, network health, and security incidents while maintaining strict modularity and privacy.

---

## 1. Architecture

The logging architecture consists of two distinct layers:

### A. Local Analytics (`AiimsAppAnalytics`)
*   **Purpose**: Immediate, high-severity diagnostic logging.
*   **Implementation**: Standalone Kotlin Object (`AiimsAppAnalytics`).
*   **Destination**: Android Logcat (currently).
*   **Design**: Strictly isolated from ODK's core `Analytics` to prevent circular dependencies.
*   **Key Use Case**: Debugging login failures, clock manipulation, and network bursts.

### B. Server Telemetry (`TelemetryWorker`)
*   **Purpose**: Periodic reporting of device status, location, and aggregate metrics.
*   **Implementation**: `TelemetryWorker` + `AiimsAuthManager`.
*   **Destination**: Central Backend (`/telemetry` endpoint).
*   **Frequency**: Every **20 minutes** (Configured via `TELEMETRY_SYNC_INTERVAL_MINUTES`).

---

## 2. Event Catalogue

### Authentication Events
Tracked via `AiimsAppAnalytics`.

| Event Name | Trigger | Payload |
| :--- | :--- | :--- |
| `aiims_auth_login_attempt` | User taps "Login". | None |
| `aiims_auth_login_success` | Valid token received. | None |
| `aiims_auth_login_failed` | API returns non-200. | `reason` (e.g., "invalid_credentials_401") |
| `aiims_auth_token_expired` | Token validity check fails. | None |
| `aiims_auth_grace_period_started` | Offline grace period activated. | None |
| `aiims_auth_hard_logout` | Grace period exceeded (6h). | `reason` |
| `aiims_auth_reauth_prompt` | PIN/Biometric prompt shown. | None |

### Network Events
Tracked via `AiimsAppAnalytics`.

| Event Name | Trigger | Payload |
| :--- | :--- | :--- |
| `aiims_net_error` | Exception during API call. | `type` (timeout, dns, io) |
| `aiims_net_server_time_offset` | Clock synchronization. | `offset_ms` |

### Security Events
Tracked via `AiimsAppAnalytics`.

| Event Name | Trigger | Payload |
| :--- | :--- | :--- |
| `aiims_sec_clock_manipulation` | Time fraud detected. | None |
| `aiims_sec_manipulation_cleared` | Time corrected by user. | None |

---

## 3. Telemetry vs. Logging
It is important to distinguish between "Logging" and "Telemetry" to avoid confusion during server analysis.

| Feature | Logging (`AiimsAppAnalytics`) | Telemetry (`TelemetryRequest`) |
| :--- | :--- | :--- |
| **Content** | Granular events (clicks, errors). | Device status, Location, Aggregates. |
| **Transport** | Logcat (Local device buffer). | HTTP POST to Server. |
| **Persistence** | Ephemeral (lost on reboot/buffer cycle). | Durable (Room DB until ack). |
| **Audience** | Developers / QA (Debug). | Admins / Dashboard (Monitoring). |

> **Note on Duplication**:
> If the server sees "duplicate" login events, ensure you are not conflating the 20-minute Telemetry heartbeat (which may carry metadata) with the actual Login API call.
> - **Login API**: Hits `/sessions` (One time per login).
> - **Telemetry API**: Hits `/telemetry` (Periodic, includes device metadata).

---

## 4. Implementation Details

### `AiimsAppAnalytics`
A centralized helper that abstracts the logging implementation. Currently uses `android.util.Log` but can be swapped for a persistent logger (e.g., Firebase, Sentry) without changing call sites.

```kotlin
// Example Usage
AiimsAppAnalytics.logLoginFailed("rate_limited")
```

### Telemetry Frequency
Controlled by `AiimsConstants.TELEMETRY_SYNC_INTERVAL_MINUTES`.
*   **Current Setting**: 20 Minutes.
*   **Constraint**: Requires Network (`NetworkType.CONNECTED`).

---

## 5. Security & Privacy
*   **No PII**: Passwords and raw tokens are **never** logged.
*   **Aggregation**: Network errors are categorized (e.g., "dns_error") rather than logging raw stack traces to analytics.
