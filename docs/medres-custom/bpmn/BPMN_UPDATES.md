# BPMN Diagram Updates Required

The `login-authentication.bpmn` diagram needs to be updated to reflect the new **Intelligent QR Scanning** and **Demo Mode** flows.

## Required Changes

### 1. QR Scan Gateway
**Current Flow:**
- Scan QR -> Check Project -> Login

**New Flow:**
- **Scan QR** (MedresQrScannerActivity)
- **Decision Gateway**: "Is Draft Project?"
    - **YES** (URL contains `/draft` AND `/test/`):
        - Trigger `DEMO_MODE`
        - Bypass standard authentication
        - Show "Enter Demo Mode" rescue button
    - **NO** (Standard Medres Project):
        - Trigger `STANDARD_MODE`
        - Proceed to Username/Password Login (as per existing diagram)
    - **INVALID** (Standard ODK App User QR with `/key/`):
        - **Reject** / End Event (Error Toast)

### 2. Session Context
- **Demo Mode Session**:
    - Does NOT persist sensitive tokens (`auth_token`, `pin_hash` wiped).
    - Uses dummy user context (`demo_user`).
    - Restricted feature set (No Change PIN, No Remote Wipe).

## Diagrams to Update
- `login-authentication.bpmn`
- `data-isolation-logout.bpmn` (Ensure Demo Mode data is wiped on exit)
