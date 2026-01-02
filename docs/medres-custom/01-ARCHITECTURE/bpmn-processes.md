# Process Diagrams - MEDRES ODK Collect

> Last Updated: 2025-12-28
>
> **BPMN 2.0 Diagrams**: True BPMN 2.0 diagrams with proper notation are available in `bpmn/` folder.
>
> This document provides both Mermaid diagrams (for quick reference) and references to generated BPMN images.

## BPMN Diagrams (True BPMN 2.0 Notation)

Professional BPMN 2.0 diagrams with proper notation are located in `bpmn/output/`:

- **Login Authentication** - [login-authentication.png](bpmn/output/login-authentication.png) | [Source](bpmn/login-authentication.bpmn)
- **PIN Security** - [pin-security.png](bpmn/output/pin-security.png) | [Source](bpmn/pin-security.bpmn)
- **Token Lifecycle** - [token-lifecycle.png](bpmn/output/token-lifecycle.png) | [Source](bpmn/token-lifecycle.bpmn)
- **Data Isolation & Logout** - [data-isolation-logout.png](bpmn/output/data-isolation-logout.png) | [Source](bpmn/data-isolation-logout.bpmn)
- **Multi-User Persistence** - [multiuser-persistence.png](bpmn/output/multiuser-persistence.png) | [Source](bpmn/multiuser-persistence.bpmn)
- **QR Workflow** - [qr-workflow.png](bpmn/output/qr-workflow.png) | [Source](bpmn/qr-workflow.bpmn)

**To regenerate diagrams**: `cd bpmn && ./render.sh`

---

## Mermaid Diagrams (Quick Reference)

For quick reference, Mermaid diagrams are provided below. These render in GitHub and other Mermaid-compatible viewers.

### 1. Authentication State Machine
 
 > [!NOTE]
 > For the **Telemetry 401 Interceptor Flow**, please refer to the specific sequence diagram in [Authentication Architecture](authentication.md#2-401-interceptor-flow-medres-apis-only).
 
 ```mermaid
stateDiagram-v2
    [*] --> LOGGED_OUT

    LOGGED_OUT --> LOGGED_IN: User logs in successfully

    state LOGGED_IN {
        [*] --> ACTIVE
        ACTIVE --> GRACE_PERIOD: Token expires

        state GRACE_PERIOD {
            [*] --> CHECKING
            CHECKING --> OFFLINE: Server unreachable
            CHECKING --> SOFT_EXPIRY: Server reachable

            OFFLINE --> CHECKING: Periodic check
            SOFT_EXPIRY --> RE_AUTHENTICATED: User re-enters password
            SOFT_EXPIRY --> OFFLINE: User cancels
        }

        GRACE_PERIOD --> LOGGED_OUT: Hard deadline (>6 hours)
    }

    LOGGED_IN --> LOGGED_OUT: User logs out
    LOGGED_IN --> LOGGED_OUT: 3 failed PIN attempts
```

---

## 2. Complete Login Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User
    participant UI as MedresLoginActivity
    participant Auth as MedresAuthManager
    participant API as Backend API
    participant PIN as PinManager
    participant ODK as ODK Core

    User->>UI: Enter username/password
    UI->>Auth: login(projectId, username, password)
    Auth->>API: POST /projects/{id}/app-users/login

    alt Valid Credentials
        API-->>Auth: {token, expiresAt, user}
        Auth->>Auth: Save to SharedPreferences
        Auth-->>UI: Success

        Auth->>PIN: isPinSet()
        alt PIN Not Set
            PIN-->>Auth: false
            UI->>User: Show SetupPinActivity
            User->>UI: Create 4-digit PIN
            UI->>PIN: savePIN(pin)
            PIN->>PIN: Hash with PBKDF2
        else PIN Set
            PIN-->>Auth: true
        end

        Auth->>ODK: Set current project
        UI->>User: Proceed to app
    else Invalid Credentials
        API-->>Auth: 401 Unauthorized
        Auth-->>UI: Error
        UI->>User: Show error message
    end
```

---

## 3. PIN Security Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User
    participant AppLock as MedresAppLock
    participant Auth as MedresAuthManager
    participant PIN as PinManager
    participant UI as PinEntryActivity

    Note over AppLock: App comes to foreground
    AppLock->>Auth: getCurrentAuthState()
    Auth-->>AppLock: LOGGED_IN

    AppLock->>PIN: isPinSet()
    PIN-->>AppLock: true

    AppLock->>UI: startActivity(PinEntryActivity)
    UI->>User: Show PIN screen

    User->>UI: Enter PIN
    UI->>PIN: validatePin(pin)

    alt Correct PIN
        PIN-->>UI: true
        UI->>PIN: resetAttempts()
        UI->>Auth: checkSoftExpiry()

        alt No soft expiry
            Auth-->>UI: false
            UI->>User: Allow access
        else Soft expiry active
            Auth-->>UI: true
            UI->>User: Show re-auth dialog
            User->>UI: Enter password
            UI->>Auth: reAuthenticate()
            Auth-->>UI: Success
            UI->>User: Allow access
        end
    else Incorrect PIN
        PIN-->>UI: false
        UI->>PIN: incrementAttempts()

        PIN->>PIN: attempts++

        alt attempts < 3
            UI->>User: Shake / Error
        else 3rd attempt
            UI->>Auth: logoutDueToFailedPin()
            Auth->>Auth: Clear all data
            Auth->>PIN: clearPin()
            UI->>User: Go to login screen
        end
    end
```

---

## 4. Token Injection Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    participant App as ODK Collect App
    participant Client as OkHttpOpenRosaServerClient
    participant Provider as TokenProvider
    participant Prefs as SharedPreferences
    participant Server as Central Backend

    Note over App: User downloads forms
    App->>Client: GET /formList

    Note over Client: Interceptor chain
    Client->>Provider: getToken()
    Provider->>Prefs: getString("auth_token_1")
    Prefs-->>Provider: "eyJhbGciOiJIUzI1..."
    Provider-->>Client: Token

    Note over Client: Add header
    Client->>Client: Authorization: Bearer eyJhbGci...

    Client->>Server: GET /formList<br/>Authorization: Bearer ...

    alt Token Valid
        Server-->>Client: 200 OK + Form List
        Client-->>App: Forms
    else Token Expired
        Server-->>Client: 401 Unauthorized
        Client-->>App: Error
        App->>App: Trigger soft expiry flow
    end
```

---

## 5. Data Isolation & Logout (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User
    participant Auth as MedresAuthManager
    participant Cleaner as ProjectCleaner
    participant Projects as ProjectsRepository
    participant Forms as FormsDataService
    participant ODK as ODK Core

    User->>Auth: logout()

    Auth->>Auth: Clear auth_token_*
    Auth->>Auth: Clear user_data_*
    Auth->>Auth: Clear expires_at_*
    Auth->>Auth: Clear pin_hash/salt

    Auth->>Cleaner: cleanup(projectId)

    Cleaner->>Projects: requireCurrentProject()
    Projects-->>Cleaner: project (with UUID)

    Cleaner->>Cleaner: withContext(Dispatchers.IO)

    Cleaner->>ODK: RESET_FORMS
    ODK->>ODK: Delete from DB
    ODK->>ODK: Delete files

    Cleaner->>ODK: RESET_CACHE
    ODK->>ODK: Clear cache

    Note over Cleaner: Instances NOT deleted

    Cleaner->>Forms: refresh(projectId)
    Forms->>Forms: Invalidate LiveData

    Auth->>User: Go to login screen
```

---

## 6. Grace Period Flow (State Diagram)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: Token valid

    ACTIVE --> CHECK_EXPIRY: Time passes

    CHECK_EXPIRY --> ACTIVE: Token still valid
    CHECK_EXPIRY --> EXPIRED: Token expired

    EXPIRED --> CHECK_GRACE: Within 6 hours?

    CHECK_GRACE --> HARD_LOGOUT: > 6 hours
    CHECK_GRACE --> CHECK_SERVER: <= 6 hours

    CHECK_SERVER --> OFFLINE_WORK: Server unreachable
    CHECK_SERVER --> PROMPT_USER: Server reachable

    PROMPT_USER --> REFRESH: User enters password
    PROMPT_USER --> OFFLINE_WORK: User cancels

    REFRESH --> ACTIVE: New token received
    OFFLINE_WORK --> CHECK_SERVER: Periodic check

    HARD_LOGOUT --> [*]
```

---

## 7. Multi-User Persistence (Flowchart)

```mermaid
flowchart TD
    Start([QR Scan or Config]) --> Extract[Extract Project ID]
    Extract --> Check{Project exists?}

    Check -->|Yes| GetUUID[Get ODK UUID]
    Check -->|No| Create[Create New Project]

    GetUUID --> Switch[Switch to Existing]
    Create --> Setup[Setup New Project]

    Switch --> Preserve[Preserve Forms/Instances]
    Setup --> Init[Initialize Empty Data]

    Preserve --> Login[User Login]
    Init --> Login

    Login --> End([Complete])
```

---

## 8. QR Code Configuration (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User
    participant QR as QRCodeScanner
    participant ODK as ODK Projects
    participant Auth as MedresAuthManager

    User->>QR: Scan QR code
    QR->>QR: Decode & decompress

    QR->>ODK: Import settings

    alt MEDRES Auth Enabled
        QR->>QR: Check if config changed
        QR->>Auth: Was server_url or username changed?

        alt Changed
            Auth->>Auth: Clear auth tokens
            Auth->>User: Show "Please login again"
        else No change
            Auth->>Auth: Keep existing tokens
        end

        QR->>User: Return to Login Screen
        User->>User: Must login to access
    else Standard ODK
        QR->>User: Navigate to MainMenu
        User->>User: Access granted
    end
```

---

## Diagram Types Used

| Diagram Type | Purpose | Usage |
|--------------|---------|-------|
| **State Diagram** | Show states and transitions | Authentication lifecycle, grace periods |
| **Sequence Diagram** | Show component interactions | Login, PIN, token injection, logout |
| **Flowchart** | Show general process flow | Multi-user persistence, QR config |

---

## Related Documentation

- [MEDRES vs. Standard Boundaries](medres_vs_standard_boundary.md) - Architecture boundaries
- [Collect Telemetry](Collect_telemetry.md) - Telemetry system design
- [Authentication System](authentication.md) - Detailed auth documentation
- [PIN Security Feature](../05-FEATURES/pin-security.md) - PIN implementation
- [Data Isolation](data-isolation.md) - Storage and cleanup
