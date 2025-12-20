# AIIMS ODK Collect: Architecture & Flows

This document visualizes the custom Authentication, Security, and Data Isolation flows implemented in the AIIMS fork of ODK Collect.

## 1. Authentication Lifecycle (State Machine)
The core logic resides in `AiimsAuthManager`. It handles JWT Bearer tokens, Expiry, and the 6-Hour Offline Grace Period.

```mermaid
stateDiagram-v2
    [*] --> LOGGED_OUT
    
    LOGGED_OUT --> LOGGED_IN : "User logs in (Credentials)"
    
    state LOGGED_IN {
        [*] --> Active : "Token Valid"
        Active --> GracePeriod : "Token Expired (Time > ExpiresAt)"
        
        state GracePeriod {
            [*] --> CheckReachability
            CheckReachability --> OfflineGrace : "Server Unreachable"
            CheckReachability --> SoftExpiry : "Server Reachable"
            
            OfflineGrace --> CheckReachability : "Periodic Refresh"
            
            SoftExpiry --> ReAuthenticated : "User Logs In"
            SoftExpiry --> OfflineGrace : "User Cancels (Work Offline)"
        }
        
    }
    
    GracePeriod --> LOGGED_OUT : "Hard Deadline (> 6 Hours)"
    LOGGED_IN --> LOGGED_OUT : "User Manually Logs Out"
    LOGGED_IN --> LOGGED_OUT : "3 Failed PIN Attempts (Wipe)"

    note right of GracePeriod
        Token is expired but user
        can still work offline.
        "Soft Expiry" prompts user
        if network is available.
    end note
```

## 2. Startup & PIN Security Flow
`AiimsAppLock` intercepts Activity lifecycles to enforce PIN security when the app comes to the foreground.

```mermaid
sequenceDiagram
    participant User
    participant AppLock as "AppLock (Lifecycle)"
    participant AuthManager
    participant PinManager
    participant Activity
    
    User->>Activity: Open App / Resume
    Activity->>AppLock: onActivityStarted()
    
    AppLock->>AuthManager: getCurrentAuthState()
    
    alt is LOGGED_OUT
        AppLock->>User: "No Action (Go to Login)"
    else is LOGGED_IN
        AppLock->>AuthManager: isSoftExpiry?
        alt Yes (Soft Expiry)
            AppLock->>Activity: "Start AiimsLoginActivity (Re-Auth Mode)"
            Activity-->>User: Show "Session Expired" Prompt
            
            opt User Cancels
               User->>Activity: Back / "Work Offline"
               Activity->>AuthManager: snoozeSoftExpiry()
               Activity-->>User: Resume Work
            end
        else No
            AppLock->>PinManager: isPinSet?
            alt Pin Set
                 AppLock->>Activity: Start PinEntryActivity
                 Activity-->>User: Show PIN Screen
                 User->>Activity: Enter PIN
                 
                 alt Correct
                     Activity-->>User: Proceed to App
                 else Incorrect
                     Activity-->>User: Shake / Error
                     opt 3 Failed Attempts
                         Activity->>AuthManager: logout()
                         AuthManager->>AuthManager: Clear Data & Token
                         Activity-->>User: Go to Login Screen
                     end
                 end
            else Pin Not Set
                 AppLock-->>User: "Proceed (Should force setup)"
            end
        end
    end
```

## 3. Data Stores & Isolation
We strictly isolate project data. When a user logs out (or session executes Hard Logout), we wipe sensitive data.

```mermaid
graph TD
    subgraph "SharedPreferences (Secure)"
        AuthPrefs[("aiims_auth_prefs")]
        MetaPrefs[("meta_prefs")]
        
        AuthPrefs -->|Contains| Token["JWT Token"]
        AuthPrefs -->|Contains| User["User Profile"]
        AuthPrefs -->|Contains| Expiry["Expiry Timestamp"]
    end
    
    subgraph "ODK Internal Storage"
        FormsDB[("Forms Database (SQLite)")]
        InstancesDB[("Instances Database (SQLite)")]
        FormFiles[("Form Files (XML/Media)")]
        InstanceFiles[("Instance XMLs")]
    end
    
    subgraph "Managers"
        AuthMgr[AiimsAuthManager]
        ProjCleaner[ProjectCleaner]
    end
    
    AuthMgr -- Read/Write --> AuthPrefs
    
    AuthMgr -- On Logout --> ProjCleaner
    
    ProjCleaner -- Wipes --> FormsDB
    ProjCleaner -- Wipes --> FormFiles
    
    note right of ProjCleaner
        "Blank Forms" are deleted to prevent new users from seeing previous project forms. Completed Instances are KEPT for sync."
    end note
```
