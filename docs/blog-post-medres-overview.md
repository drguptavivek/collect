# MEDRES ODK Collect: Complete Customization Overview

**GitHub Repository**: [drguptavivek/collect](https://github.com/drguptavivek/collect)  
**Last Updated**: January 28, 2026  
**Version**: v2025.3.3-MEDRES-RC4  
**Backend (ODK Central)**: Custom fork designed to work in tandem with MEDRES Collect


**SEO Meta Description**: MEDRES ODK Collect transforms standard ODK into a secure mobile data collection platform for field medical research. Features Bearer Token auth, PIN security, offline grace periods, and intelligent QR workflows for shared-device environments.

 
## Why MEDRES Exists

Field medical research in resource-constrained environments faces unique challenges that standard data collection tools don't address. Researchers work in remote areas with unreliable connectivity, share devices across multiple data collectors, and handle sensitive patient data that requires institutional-grade security. Standard ODK Collect, while excellent for general data collection, lacks the authentication controls, offline resilience, and shared-device workflows needed for clinical research.

**MEDRES solves this by transforming ODK Collect into a secure, field-ready research platform**. It replaces long-lived credentials with short-lived tokens (reducing exposure), adds mandatory PIN security (protecting shared devices), implements intelligent offline grace periods (enabling work during connectivity gaps), and provides comprehensive telemetry (tracking device health and security events). The result: research teams can deploy to 100+ field workers in hours, maintain strict data security even on shared tablets, and continue data collection through network outages—all while maintaining full audit trails for regulatory compliance.

**Key Differentiators**:
- ✅ **Bearer Token Authentication**: 3-day tokens vs. permanent credentials (87% reduction in exposure window)
- ✅ **Offline Grace Period**: 6-hour tolerance for network outages (vs. immediate lockout)
- ✅ **Shared Device Security**: PIN-per-user with 3-attempt wipe (vs. single-user assumption)
- ✅ **Intelligent QR Workflows**: Automatic detection of Production vs. Draft QRs (prevents data contamination)
- ✅ **Hardware-Backed Encryption**: Android Keystore with AES-256-GCM (vs. plaintext storage)
- ✅ **Comprehensive Telemetry**: 20-minute heartbeat with location, battery, security events (vs. no visibility)

---


## Introduction

MEDRES ODK Collect is a specialized fork of ODK Collect designed for field medical research in resource-constrained environments. This document explains the complete architecture and rationale behind the customization.

 

## The Four Core Modifications

MEDRES implements four major modifications to standard ODK Collect:

### 1. Custom Authentication: Bearer Token System

**Standard ODK**: Basic Auth (username/password in every HTTP request)

**MEDRES**: Bearer Token (short-lived JWT tokens)

**Why the Change**:
- **Short-lived tokens** (3-day default validity) improve security over long-lived credentials
- **Server-side revocation** enables immediate session termination across all devices
- **Offline grace period** (6 hours) allows field work to continue during connectivity gaps
- **Better audit trail** through token lifecycle tracking

**Implementation**:
- OkHttp interceptor (`AuthInterceptor`) injects Bearer token into all OpenRosa API requests
- Token format: `Authorization: Bearer <JWT>`
- Tokens stored in hardware-backed encrypted storage (`medres_auth_secure`)

**Token Lifecycle**:
1. **Login**: User provides credentials, server returns JWT token (3-day validity)
2. **Usage**: Token injected into every API request via interceptor
3. **Expiry Warning**: Multi-tier warnings at 8h, 3h, 1h, 30m, 15m, 3m before expiry
4. **Soft Expiry**: Token expired but server reachable → Re-authentication prompt
5. **Grace Period**: Token expired and server unreachable → 6-hour offline grace
6. **Hard Expiry**: Grace period exceeded → Forced logout

---

### 2. Local Networking: Emulator Development Support

**Feature**: Android Emulator resolves `central.local` to `10.0.2.2` with SSL bypass

**Why This Matters**:
- Enables local development without modifying emulator `/etc/hosts`
- Developers can test against local ODK Central instance
- Speeds up development iteration cycles

**Security**:
- **Debug builds only** - SSL bypass disabled in production
- Custom DNS resolver intercepts `central.local` lookups
- All other domains use standard DNS resolution

**Implementation**:
```kotlin
// Debug only
if (BuildConfig.DEBUG && hostname == "central.local") {
    return listOf(InetAddress.getByName("10.0.2.2"))
}
```

---

### 3. PIN Security: Device-Level Protection

**Feature**: Mandatory 4-digit PIN after successful server authentication

**Why PIN on Top of Auth**:
- **Shared device environments**: Multiple field researchers use same tablet
- **Physical security**: Protects against unauthorized device access
- **Offline protection**: Works even when server is unreachable
- **Brute force prevention**: 3 failed attempts = immediate session wipe

**Implementation**:
- **Hashing**: PBKDF2 with unique salt per installation
- **Storage**: Encrypted storage (`medres_auth_secure`) with hardware backing
- **Auto-lock**: App locks when minimized, requires PIN on resume
- **Attempt counter**: Tracks failed attempts, wipes after 3 failures

**PIN Workflow**:
1. User successfully authenticates with server
2. App prompts for 4-digit PIN setup
3. PIN hashed with PBKDF2 and stored in encrypted storage
4. On app resume: PIN entry required
5. Failed attempts increment counter
6. 3rd failed attempt triggers immediate wipe (token, PIN, user data cleared)

---

### 4. Project Persistence: Manual Configuration Fix

**Problem**: Standard ODK had bugs with manual server entry - settings wouldn't persist correctly

**Solution**: Maintain mapping between Central Project ID and ODK UUID

**Why This Matters**:
- **ODK uses UUIDs internally** for project identification
- **Central API uses numeric IDs** (e.g., project 42)
- **Critical for correct cleanup** when switching projects or logging out

**Implementation**:
- Preference key: `central_to_odk_$centralPid`
- Maps Central ID → ODK UUID
- Used during logout to target correct project for cleanup

---

## Key Design Decisions

### Decision 1: Forms Wiped, Instances Preserved

**What Happens on Logout**:
- ✅ **Blank forms deleted** (form definitions)
- ✅ **Filled instances preserved** (submitted and draft data)

**Rationale**:
1. **Prevents form confusion**: New user doesn't see previous user's project forms
2. **Shared device workflows**: User A fills form, User B uploads (common in field research)
3. **Bandwidth efficiency**: Instances are small metadata; forms can be large XML
4. **Data integrity**: Instances tied to form definitions via form ID/version

**Implementation**: `ProjectResetter` with `RESET_FORMS` flag only

---

### Decision 2: Grace Period for Offline Work

**Configuration**: 6-hour grace period after token expiry

**Rationale**:
- **Field connectivity is unreliable**: Researchers work in remote areas with spotty networks
- **Hard expiry would strand users**: Cannot complete data collection if locked out
- **Server unreachability check**: Prevents lockout when server is down (not user's fault)

**Implementation**:
```kotlin
GRACE_PERIOD_MS = 6 * 60 * 60 * 1000 // 6 hours
```

**Grace Period Logic**:
1. Token expires (3 days elapsed)
2. App checks server reachability via `HEAD /version.txt`
3. **Server reachable**: Show re-authentication prompt (soft expiry)
4. **Server unreachable**: Enter grace period (6 hours of continued work)
5. **Grace period expires**: Force logout (hard expiry)

---

### Decision 3: Network Reachability Architecture

**Problem**: Network connectivity doesn't guarantee server reachability

**Solution**: Multi-layer network monitoring

#### Layer 1: Network State Monitoring
- Uses Android's `ConnectivityManager.NetworkCallback`
- Verifies `NetworkCapabilities.NET_CAPABILITY_INTERNET`
- Filters out local-only connections (e.g., printer Wi-Fi)
- **Debouncing**: 30-second cooldown prevents thrashing during unstable networks

#### Layer 2: Server Reachability Checks
- **Endpoint**: `HEAD /version.txt` (lightweight, static resource)
- **Why HEAD?**: Minimizes data usage, checks responsiveness without downloading body
- **Caching**: Results cached for 2 minutes (reduces server load)
- **Timeouts**: 3-second connect timeout, 3-second read timeout

**Stability Criteria**:
- Any connection taking >3 seconds = Unreachable
- Effectively filters out:
  - Edge/2G networks with extreme latency
  - Congested Wi-Fi with high packet loss
  - Captive portal networks (internet capability check passed but traffic blocked)

#### Layer 3: Automatic Grace Period Re-evaluation
**Scenario**: User in grace period, network restored

**Workflow**:
1. `MedresNetworkStateMonitor` emits `true` (Internet available)
2. Condition: User currently in Grace Period
3. Action: `MedresAuthManager` triggers `refreshState()`
4. Verification: Calls `checkReachability()`
5. **Success**: Show re-authentication prompt
6. **Failure**: Continue in offline grace mode

**Security Property**: User cannot bypass re-auth by turning off data, waiting for expiry, then turning it back on. The moment connectivity is restored, re-check fires.

---

## Telemetry & Logging Architecture

MEDRES uses a dual-layer approach for visibility:

### Layer 1: Local Analytics (MedresAppAnalytics)

**Purpose**: Immediate, high-severity diagnostic logging

**Destination**: Android Logcat (currently)

**Design**: Strictly isolated from ODK's core Analytics (prevents circular dependencies)

**Key Events**:
- `medres_auth_login_attempt`
- `medres_auth_login_success`
- `medres_auth_login_failed`
- `medres_auth_token_expired`
- `medres_sec_clock_manipulation`
- `medres_net_error`

### Layer 2: Server Telemetry (TelemetryWorker)

**Purpose**: Periodic reporting of device status and location

**Destination**: Central Backend (`/telemetry` endpoint)

**Frequency**: Every 20 minutes

**Offline Handling**:
- Telemetry stored in Room database when offline
- `TelemetryWorker` (WorkManager) flushes queue when online
- Automatic retry with exponential backoff
- Idempotent upserts (client-generated stable ID prevents duplicates)

**401 Handling**:
- `AuthInterceptor` catches 401 from server
- Per Server API Spec: 401 = no record created on server
- Triggers blocking re-authentication flow
- Automatic retry after successful re-auth
- Queue integrity maintained if re-auth cancelled

**Response Status "invalidated"**:
- Server returns `200 OK` with `status: "invalidated"`
- Meaning: Telemetry recorded, but token near expiry (within 2-day grace)
- Client action: Remove from queue (prevent duplicates), proactively refresh session

---

## Data Persistence & Storage

MEDRES uses a three-layer storage approach:

### Layer 1: MEDRES Encrypted Storage (`medres_auth_secure`)

**Technology**: Android `EncryptedSharedPreferences` with AES256_GCM

**Encryption**:
- Values: AES-256-GCM
- Keys: AES-256-SIV
- Key storage: Android Keystore with hardware backing

**Encrypted Data**:
- `auth_token`: Bearer Token (JWT)
- `token_expiry`: Expiry timestamp (ms since epoch)
- `project_id`: Central Project ID for token validation
- `pin_hash`: PBKDF2 hash of 4-digit PIN
- `pin_salt`: Unique salt for hashing
- `pin_attempts`: Failed PIN attempt counter
- `last_pin_attempt`: Timestamp of last attempt
- `last_valid_wall_time`: Last known true wall time (clock manipulation detection)
- `api_url`: Base URL of Central server

### Layer 2: MEDRES Regular Storage (`medres_auth_prefs`)

**Technology**: Standard `SharedPreferences`

**Global State**:
- `is_authenticated`: Boolean authentication flag
- `last_auth_timestamp`: Last successful auth timestamp
- `active_project_id`: Currently active Central project ID
- `first_launch`: Initial setup flag

**Per-Project Metadata** (scoped with `$pid` suffix):
- `user_data_$pid`: JSON user details (ID, Username)
- `api_url_$pid`: Base URL for this project
- `project_name_$pid`: Cached project name for display
- `central_to_odk_$pid`: Maps Central ID → ODK UUID

### Layer 3: Standard ODK Storage Integration

**Meta Settings** (`meta`):
- `current_project_id`: MEDRES sets this to ODK UUID
- `metadata_installid`: Standard ODK Install ID (used as `deviceId` in MEDRES)

**Project Settings** (`general_prefs[UUID]`):
- `server_url`: Tokenized format: `<BaseURL>/key/<BearerToken>/projects/<PID>`
- `protocol`: Hardcoded to `odk_default`
- `project_name`: Auto-updated from Central metadata

---

## Security Cleanup Behavior

**When security event occurs** (logout, 3 failed PINs, hard expiry):

| Category | Item | Action | Rationale |
|----------|------|--------|-----------|
| **Security** | Auth Token (JWT) | ✅ Wiped | Prevents unauthorized API access |
| **Security** | Security PIN | ✅ Wiped | Forces next user to set own PIN |
| **Security** | Session State | ✅ Wiped | Clears `is_authenticated`, `active_project_id` |
| **Security** | Clock State | ✅ Wiped | Resets `last_valid_wall_time` (prevents replay) |
| **User Data** | User Profile | ✅ Wiped | Clears `user_data_$pid` (ID, Username) |
| **Project Data** | Blank Forms | ❌ Preserved | Bandwidth efficiency, shared team access |
| **Project Data** | Saved Instances | ❌ Preserved | Team visibility, shared device drafts |
| **Project Data** | Submitted History | ❌ Preserved | Local audit trail |
| **Configuration** | Project URL/Name | ❌ Preserved | Simplifies re-login for next user |
| **Configuration** | ODK Settings | ❌ Preserved | Maintains ODK core stability |

---

## QR Code Workflows

### QR Type Detection

MEDRES distinguishes between three QR types:

| QR Type | URL Pattern | Behavior | Use Case |
|---------|-------------|----------|----------|
| **MEDRES Project** | `/v1/projects/<ID>` (no `/key/`) | Login Mode | Production data collection |
| **Draft/Test** | `/draft` AND `/test/` | Demo Mode | Form testing |
| **Standard ODK** | `/key/<TOKEN>` | Rejected | Incompatible with MEDRES |
| **Legacy ODK** | Base64 encoded `/key/` | Rejected | Old ODK Central format |

### QR Security Validation (5 Layers)

#### Layer 1: Size Limits
- Compressed: Max 4KB
- Decompressed: Max 16KB
- Compression ratio: Max 4:1
- **Prevents**: Decompression bomb attacks

#### Layer 2: Key Validation
- General settings: Validated against `ProjectKeys`
- Admin settings: Validated against `ProtectedProjectKeys`
- Invalid keys: Logged and skipped
- **Prevents**: Injection of arbitrary preference keys

#### Layer 3: Type Safety
- Boolean values: Must be `true`/`false`
- String values: Must be valid strings
- Integer values: Must be valid integers
- Admin settings: Must be boolean only
- **Prevents**: Type confusion attacks

#### Layer 4: Sensitive Key Protection
**Blocked from QR override**:
- `server_url` (handled by login flow)
- `username` (handled by login flow)
- `password` (never in QR)
- `protocol` (enforced by app)
- **Prevents**: Credential theft via malicious QR

#### Layer 5: Demo Mode Validation
Draft QRs must contain:
- `/draft` in URL
- `/test/` in URL
- Both conditions required
- **Prevents**: Accidental production data in test mode

---

## Security Threat Model

| Threat | Mitigation |
|--------|------------|
| **Device theft** | Mandatory PIN (4-digit, 3 attempts = wipe) |
| **Token interception** | Short-lived (3 days), HTTPS only |
| **Offline brute force** | PIN attempt counter, local wipe |
| **QR bypass** | QR only configures, still requires login |
| **Session hijacking** | Server-side revocation endpoint |
| **Data leakage** | Forms wiped on logout, instances tied to projects |
| **Clock manipulation** | Server time sync, `last_valid_wall_time` tracking |
| **Decompression bombs** | 4KB compressed, 16KB decompressed, 4:1 ratio limits |
| **Settings injection** | Key validation against `ProjectKeys` whitelist |
| **Credential theft** | Sensitive keys blocked from QR override |

---

## Field Research Use Cases

### Use Case 1: Multi-Site Clinical Trial

**Scenario**: Diabetic retinopathy screening across 50 rural health centers in Tamil Nadu

**Requirements**:
- Each site has dedicated tablet
- Multiple data collectors per site (shared device)
- Intermittent connectivity (3G/4G)
- Form updates every 2 weeks (protocol refinements)

**MEDRES Solution**:
1. **Bearer Token Auth**: 3-day tokens reduce credential exposure
2. **PIN Security**: Each data collector sets own PIN, auto-lock on device handoff
3. **Offline Grace**: 6-hour grace period allows work during network outages
4. **QR Workflows**: Research coordinator tests form updates via Draft QRs, deploys via Project QRs
5. **Data Isolation**: Logout wipes forms but preserves instances (shared device drafts)

### Use Case 2: Longitudinal Cohort Study

**Scenario**: 5-year follow-up study tracking maternal health outcomes

**Requirements**:
- Same participants visited every 6 months
- Form versions change as study progresses
- Field workers need to review previous submissions
- Strict data security (PHI/PII)

**MEDRES Solution**:
1. **Token Lifecycle**: Short-lived tokens with server-side revocation
2. **Encrypted Storage**: Hardware-backed encryption for tokens and PINs
3. **Telemetry**: 20-minute heartbeat tracks device location and activity
4. **Project Persistence**: Manual configuration persists correctly (no re-entry)
5. **Form Preservation**: Instances preserved on logout (review previous visits)

### Use Case 3: Emergency Response Data Collection

**Scenario**: Rapid assessment during disease outbreak

**Requirements**:
- Deploy to 100+ field workers in 48 hours
- Unreliable connectivity (disaster zones)
- Frequent form updates (evolving protocols)
- Immediate data visibility for coordinators

**MEDRES Solution**:
1. **QR Onboarding**: Scan Project QR, enter credentials, set PIN (< 2 minutes)
2. **Offline First**: 6-hour grace period, telemetry queuing, offline form filling
3. **Draft QR Testing**: Coordinators test form updates before mass deployment
4. **Auto Project Name**: Immediate visual confirmation of correct deployment
5. **Network Monitoring**: Smart reachability checks, automatic re-auth on connectivity restore

---

## Version History

### RC4 (January 28, 2026)
- Intelligent QR type detection (Project vs Draft vs Standard ODK)
- Automatic project name updates
- 5-layer QR security validation
- 706 lines of test coverage

### RC3 (January 28, 2026)
- Release signing guide
- Trusted keystore configuration
- Android 10 minimum SDK (upgraded from Android 5)
- Target SDK 35 (Android 15)

### RC2 (January 2, 2026)
- Fixed URL tokenization regression
- Fixed manual entry `/v1` issue

### RC1 (January 1, 2026)
- Initial release candidate
- Bearer Token authentication
- PIN security system
- Offline telemetry
- Accessibility improvements (WCAG 2.1 Level AA)
- Performance optimizations (reachability caching, HEAD requests)

---

## Technical Specifications

**Current Version**: v2025.3.3-MEDRES-RC4  
**Version Code**: 5115  
**Min SDK**: 29 (Android 10)  
**Target SDK**: 35 (Android 15)  
**Compile SDK**: 36  
**Base ODK Version**: v2025.3.3 (November 1, 2025)

**APK Sizes**:
- Release: ~24MB
- Debug: ~57MB

**Tested On**: Android 14 (Samsung SM-E146B)

---

## Documentation

Comprehensive documentation available in `docs/medres-custom/`:

**Architecture** (15 documents):
- Authentication flows
- QR code workflows
- Network reachability
- Telemetry architecture
- Data persistence
- Security boundaries

**Operations** (8 documents):
- Deployment procedures
- Release signing
- Versioning strategy
- Maintenance workflows

**Development** (10 documents):
- Building guide
- Testing procedures
- Contributing guidelines
- Local development setup

**Testing** (6 documents):
- QA procedures
- Test coverage
- Accessibility testing
- Security testing

---

## Future Roadmap

### Short-term (RC5)
- Enhanced telemetry offline queuing
- Biometric authentication option (fingerprint/face unlock)
- Performance optimizations for low-end devices

### Medium-term
- Multi-language support (Hindi, Tamil, Telugu)
- Advanced analytics dashboard
- Custom form builder for research coordinators

### Long-term
- Integration with ABDM (Ayushman Bharat Digital Mission)
- Offline form submission with conflict resolution
- Machine learning-based data quality checks

---

## Backend (ODK Central) Customizations

MEDRES Collect requires a heavily modified ODK Central backend to support advanced authentication, telemetry mapping, and per-project security policies.

### Key Backend Features

#### 1. Bearer Token Authentication
- Issues short-lived JWT tokens (configurable validity, default 3 days)
- Server-side token revocation endpoint
- Per-project token validity configuration
- Grace period settings (default 6 hours)

#### 2. Telemetry System
- `/telemetry` endpoint receives device location, battery, security events
- Real-time geographic visualization of field workers
- Idempotent upserts based on `(appUserId, deviceId, event.id)`
- Response status `"invalidated"` for tokens near expiry

#### 3. App User Management
- Automated password generation for field workers
- Centralized project-access control
- Role-based permissions
- Bulk user provisioning

#### 4. Project-Specific Security Policies
- Configurable token validity per project
- Mandatory PIN rotation rules
- Session timeout policies
- Offline grace period settings

#### 5. Audit Logs & Monitoring
- Login history tracking
- Security event logs (failed PINs, clock manipulation)
- Device activity monitoring
- Session lifecycle tracking

### Backend Documentation

Comprehensive backend documentation available in the repository:
- **API Specification**: `docs/medres-custom/03-API/reference.md`
- **Screenshots**: `docs/medres-custom/ODK_Central_docs/screenshots/`
- **Setup Guide**: `docs/medres-custom/ODK_Central_docs/`

**Backend Screenshots**:
- App User Management with auto-generated passwords
- Project-specific security settings
- Telemetry table and live mapping
- Login history and audit trails

---

## Links & Resources

### GitHub Repository
- **Main Repository**: [drguptavivek/collect](https://github.com/drguptavivek/collect)
- **Branch**: `vg-work` (active development)

### Documentation
- **Architecture**: [docs/medres-custom/01-ARCHITECTURE/](https://github.com/drguptavivek/collect/tree/vg-work/docs/medres-custom/01-ARCHITECTURE)
- **API Reference**: [docs/medres-custom/03-API/reference.md](https://github.com/drguptavivek/collect/blob/vg-work/docs/medres-custom/03-API/reference.md)
- **Operations**: [docs/medres-custom/04-OPERATIONS/](https://github.com/drguptavivek/collect/tree/vg-work/docs/medres-custom/04-OPERATIONS)
- **Features**: [docs/medres-custom/05-FEATURES/](https://github.com/drguptavivek/collect/tree/vg-work/docs/medres-custom/05-FEATURES)

### Changelog & Releases
- **CHANGELOG**: [docs/CHANGELOG.md](https://github.com/drguptavivek/collect/blob/vg-work/docs/CHANGELOG.md)
- **Releases**: [GitHub Releases](https://github.com/drguptavivek/collect/releases)

### Backend
- **ODK Central Docs**: [docs/medres-custom/ODK_Central_docs/](https://github.com/drguptavivek/collect/tree/vg-work/docs/medres-custom/ODK_Central_docs)
- **Backend Screenshots**: [docs/medres-custom/ODK_Central_docs/screenshots/](https://github.com/drguptavivek/collect/tree/vg-work/docs/medres-custom/ODK_Central_docs/screenshots)

---

## Conclusion

MEDRES ODK Collect transforms standard ODK into a secure, field-ready platform for medical research in resource-constrained environments. The four core modifications (Bearer Token auth, local networking, PIN security, project persistence) work together to provide:

✅ **Security**: Hardware-backed encryption, short-lived tokens, brute-force protection  
✅ **Reliability**: Offline grace period, network monitoring, automatic re-auth  
✅ **Usability**: QR workflows, auto-lock, shared device support  
✅ **Flexibility**: Draft QR testing, project switching, manual configuration  

Every design decision prioritizes the needs of field researchers working in challenging environments with intermittent connectivity and shared devices.

---

## Contact & Support

**Questions?** 
- See `docs/medres-custom/` for complete technical documentation
- Open an issue on [GitHub](https://github.com/drguptavivek/collect/issues)
- Review the [CHANGELOG](https://github.com/drguptavivek/collect/blob/vg-work/docs/CHANGELOG.md) for version history

**Contributing**: See `docs/medres-custom/02-DEVELOPMENT/` for development setup and contribution guidelines.
