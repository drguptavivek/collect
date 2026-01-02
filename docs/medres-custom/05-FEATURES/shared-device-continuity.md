# Shared-Device Continuity & Isolation

MEDRES ODK Collect is optimized for field environments where multiple health workers may share a single mobile device. Our policy ensures **High Continuity** for data while maintaining **Strict Isolation** for security credentials.

---

## 1. Data Preservation Policy
Unlike standard ODK Collect, which may clear forms on project reset, the MEDRES fork implements a selective wipe.
- **Preserved Data**: All "Filled", "Sent", and "Draft" forms are **retained** during logout and re-authentication.
- **Continuity**: This allows Worker A to start a form, log out, and Worker B to log in later to find the same form in the "Drafts" or "Sent" folder.
- **Shared Context**: This is critical for longitudinal studies where multiple workers contribute to the same data pool on one device.
- **References**: `collect-7lh`, `MedresAuthManager#logout`.

## 2. Security Isolation (The "Selective Wipe")
While form data persists, security metadata is strictly user-scoped.
- **PIN Clearing**: On any login event, the system checks if the newly authenticated User ID is different from the previous session. If a **user change** is detected, the local 4-digit PIN is **manually cleared**.
- **Token Purge**: Tokens, session hashes, and the grace-period state are stored in `EncryptedSharedPreferences` and are force-cleared on every logout.
- **Outcome**: Worker B cannot use Worker A's PIN to unlock the app, even if they are working on the same project.
- **References**: `collect-3yt`, `MedresAuthManager#login`.

## 3. Session Invalidation Handling
- **Multi-Device Logout**: If a user logs in on a new device, the MEDRES server may invalidate previous sessions.
- **Client Response**: The app detects the `INVALID_SESSION` or `401 Unauthorized` response during any sync event.
- **Hard Logout**: Triggers an immediate local session clear, following the selective wipe ritual (clearing PIN/tokens but preserving forms).
- **References**: `collect-gu8`, `collect-1h6`.

## 4. Auto-Save for Hard Logout
- **Protection**: Before a session is hard-expired or cleared due to security failures, the app attempts to **auto-save** the current form draft.
- **Outcome**: Minimizes data loss for users who are caught in a hard-expiry window.

---

## Technical Reference
- **Manager**: `MedresAuthManager.clearSession(...)`
- **Cleaner**: `ProjectCleaner` (Selective reset targeting only tokens/hashes)
