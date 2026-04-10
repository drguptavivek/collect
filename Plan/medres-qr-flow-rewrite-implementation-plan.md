# MEDRES QR Flow Rewrite — Implementation Plan

> **Status: COMPLETE**
> Last updated: 2026-04-10

---

## Summary

Full replacement of ad-hoc URL substring QR handling with a type-safe, policy-aligned
architecture. All phases complete, verified on-device, committed to `medres-post-rc4-reapply`.

---

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | QR Classifier (typed parse results + security) | ✅ |
| 2 | Typed Staging Store | ✅ |
| 3 | Scanner Flow Branching | ✅ |
| 4 | Login Materialization (MEDRES project only) | ✅ |
| 5 | Security constraints (byte-accurate, ratio guard) | ✅ |
| 6 | Draft/Main Project Isolation | ✅ |
| 7 | Auth Settings scan path — **removed** (Option B) | ✅ |

**Build verification:**
```
./gradlew :medres-auth-module:testDebugUnitTest   ✅ (137 tasks)
./gradlew :collect_app:installMedresDebug          ✅ (on emulator)
Manual: full draft→main→draft cycle               ✅
```

---

## Architectural Decision — Scan QR removed from Auth Settings

### Problem (resolved)

Scanning a QR from Auth Settings while authenticated required:
1. Logout confirmation dialog
2. `authManager.logout()` async in a coroutine
3. Stage new QR
4. Navigate to `MedresLoginActivity` with `FLAG_ACTIVITY_CLEAR_TASK`
5. `refreshState()` emitting `DEMO_MODE` before login screen rendered
6. PIN screen intercepting before "Enter Demo Mode" was visible

Each step produced a new bug during this rewrite. The `isQrTransition` flag,
`setActiveProject(null)`, and the `DEMO_MODE` no-op observer were all band-aids.

### Decision: Remove "Scan New QR" from Auth Settings (Option B)

The correct flow is:
```
Auth Settings → [Logout] → Login screen (clean state) → [Scan QR]
```
One extra tap, zero race conditions, zero mode-transition machinery.

**Auth Settings is for managing an existing authenticated session** (token status,
PIN change, logs, logout) — not for QR onboarding.

### What was deleted

| Location | Removed |
|---|---|
| `activity_auth_settings.xml` | `scan_demo_qr_button` MaterialButton |
| `AuthSettingsActivity.kt` | `scanDemoQrButton`, click listener, `launchNativeQrScanner()` |
| `MedresQrScannerActivity.kt` | `EXTRA_LAUNCHED_FROM_AUTH_SETTINGS`, `maybeConfirmAndHandle()`, `getCurrentProjectContext()`, transition `AlertDialog`, `setActiveProject(null)`, `EXTRA_FROM_QR_TRANSITION` intent |
| `MedresLoginActivity.kt` | `isQrTransition` field, `EXTRA_FROM_QR_TRANSITION` companion, `DEMO_MODE` no-op branch, `isQrTransition` guard |

`routeAfterSuccessfulScan()` is now just `finish()`.

---

## Complete Flows

### Flow 1 — Fresh App: MEDRES Project QR → Login

```
MedresLoginActivity (no project configured)
  └─► "No project configured. Scan a QR code."
  └─► [Scan QR] → MedresQrScannerActivity
        └─► MedresQrParser → MedresProjectQr
              └─► stageMedresProject()
              └─► finish() → back to MedresLoginActivity

MedresLoginActivity.onResume() → detectCurrentProject()
  └─► StagedMedresProjectContext
  └─► Shows project config + [Username] [Password] [Login]

Login → AuthResult.Success
  └─► ensureMainProjectConfigured() → creates/finds ODK project
  └─► setProjectMapping(centralPid, odkUuid)
  └─► server_url = ".../v1/key/<TOKEN>/projects/<PID>"
  └─► navigateToPinSetup() → SetupPinActivity → MainMenuActivity
```

---

### Flow 2 — Fresh App: Draft QR → Demo Mode

```
MedresLoginActivity (no project configured)
  └─► [Scan QR] → MedresQrScannerActivity
        └─► MedresQrParser → DraftFormQr
              └─► stageDraftForm()
              └─► finish() → back to MedresLoginActivity

MedresLoginActivity.onResume() → detectCurrentProject()
  └─► StagedDraftFormContext
  └─► ensureDraftProjectConfigured()
        └─► Creates ODK project: name="[Draft] ...", server_url=<full draft URL>
        └─► Sets as CURRENT_PROJECT_ID
        └─► Does NOT call setProjectMapping() (never overwrites main mapping)
  └─► Shows "Demo Mode: [Draft] 1.School Information"
  └─► [Enter Demo Mode] → navigateToMain()
        └─► MainMenuActivity
              Fill Blank Form: draft forms only ✅
              Sent Forms: draft submissions only ✅
```

---

### Flow 3 — Standard ODK Managed QR → Rejected

```
[Scan QR] → MedresQrScannerActivity
  └─► MedresQrParser → StandardOdkManagedQr
        └─► Toast: "Standard ODK QR rejected. Please use a MEDRES Project QR."
        └─► isProcessing = false (scanner stays open, no state change)
```

---

### Flow 4 — Mode Switch: Main → Draft

```
User is logged in to main MEDRES project.
Auth Settings → [Logout] → MedresLoginActivity (clean state)
  └─► [Scan QR] → scan draft_form_medres_qr.png
        └─► DraftFormQr → stageDraftForm() → finish()

MedresLoginActivity.onResume() → detectCurrentProject()
  └─► StagedDraftFormContext → ensureDraftProjectConfigured()
  └─► [Enter Demo Mode] → MainMenuActivity (draft project)
        Fill Blank Form: ONLY draft forms ✅
        Sent Forms: ONLY draft submissions ✅
```

---

### Flow 5 — Mode Switch: Draft → Main

```
User is in Demo Mode.
Auth Settings → [Logout] → MedresLoginActivity (clean state)
  └─► [Scan QR] → scan app_user_medres_qr.png
        └─► MedresProjectQr → stageMedresProject() → finish()

MedresLoginActivity.onResume() → StagedMedresProjectContext
  └─► [Login] → SetupPinActivity → MainMenuActivity (main project)
        Fill Blank Form: ONLY main project forms ✅
        Sent Forms: ONLY main project submissions ✅
```

---

## Draft/Main Isolation — Three Guards

All three required to prevent draft data bleeding into main:

| Guard | Location | Prevents |
|-------|----------|---------|
| `isDraftLikeUrl(authBaseUrl)` | `MedresLoginActivity.attemptLogin()` | Draft-like `authBaseUrl` blocks entire main materialization branch |
| `isDraftProjectUrl` filter | `MedresLoginActivity.ensureMainProjectConfigured()` | Projects with `/test/` + `/draft` URL skipped when finding main ODK slot |
| No `setProjectMapping()` in draft path | `MedresLoginActivity.ensureDraftProjectConfigured()` | Draft UUID never overwrites `centralPid → mainOdkUuid` mapping |

---

## URL Transformation Rules

| QR Type | Scanned URL | Staged | After Auth |
|---------|------------|--------|------------|
| MEDRES Project | `.../v1/projects/<PID>` | `auth_url = .../v1` | `server_url = .../v1/key/<TOKEN>/projects/<PID>` |
| Draft | `.../v1/test/<T>/projects/<PID>/forms/<F>/draft` | `draft_url = <full URL>` | `server_url = <same full URL, never rewritten>` |
| ODK Managed | `.../v1/key/<T>/projects/<PID>` | — (rejected) | — |

---

## Files

### New Production Files

| File | Purpose |
|------|---------|
| `qr/MedresQrParseResult.kt` | Sealed hierarchy: `MedresProjectQr`, `DraftFormQr`, `StandardOdkManagedQr`, `InvalidQr` |
| `qr/MedresQrParser.kt` | Pure-logic classifier, strict regex URL matching, byte-accurate security guards |
| `qr/MedresStagedQrContext.kt` | `StagedMedresProjectContext` + `StagedDraftFormContext` |
| `qr/MedresQrStagingStore.kt` | Typed SharedPreferences staging, `SESSION_KEYS_TO_CLEAR` precise cleanup |
| `qr/MedresQrTransitionPolicy.kt` | Mode-transition conflict detection (kept for future use, no longer called from scanner) |

### Modified Production Files

| File | Change |
|------|--------|
| `MedresConstants.kt` | Staging keys, `KEY_AUTH_USERNAME_HINT` (separate from `KEY_USER_NAME`), `SESSION_KEYS_TO_CLEAR` |
| `MedresQrScannerActivity.kt` | Typed `when` dispatch; `routeAfterSuccessfulScan()` = `finish()` only |
| `MedresLoginActivity.kt` | Typed staging read; `ensureDraftProjectConfigured()`; `isDraftLikeUrl()` guard |
| `AuthSettingsActivity.kt` | Scan QR button and `launchNativeQrScanner()` removed |
| `activity_auth_settings.xml` | `scan_demo_qr_button` removed; `logout_button` constraint rewired |

### Test Files

| File | Coverage |
|------|---------|
| `MedresQrParserTest.kt` | 30+ tests: all 4 QR types, security limits, malformed URL rejection |
| `MedresQrStagingTest.kt` | Staging/read roundtrip, precise cleanup, `FakeSharedPreferences` |
| `MedresQrFlowContractTest.kt` | End-to-end parse→stage→read for all 3 QR types |
| `MedresQrTransitionPolicyTest.kt` | Mode-transition conflict detection |
| `ProjectConfigurationTest.kt` | Regression: draft project never selected as main ODK slot |

---

## Security Constraints

| Constraint | Limit | Where |
|---|---|---|
| Compressed payload | 4 096 bytes (UTF-8) | `MedresQrParser` before decompression |
| Decompressed payload | 16 384 bytes (UTF-8) | `MedresQrParser` after decompression |
| Compression ratio | 50× | `MedresQrParser` |
| General key allow-list | `ProjectKeys` | `MedresSettingsValidator` |
| Admin key allow-list | `ProtectedProjectKeys` | `MedresSettingsValidator` |
| Sensitive key exclusion | `server_url`, `username`, `password`, `protocol` | Materialization loop |

---

## Acceptance Criteria — All Met ✅

| # | Criterion |
|---|-----------|
| 1 | MEDRES project QR requires MEDRES login |
| 2 | Successful login produces tokenized `server_url` (`/key/<TOKEN>/projects/<PID>`) |
| 3 | Draft QR never requires login |
| 4 | Draft QR preserves full URL (never rewritten) |
| 5 | Draft QR never triggers `/key/<TOKEN>/...` transform |
| 6 | Draft `project.name` is display metadata only |
| 7 | Standard ODK managed QR rejected with no staging |
| 8 | Rescan cleanup is precise (session/staged keys only) |
| 9 | Byte-accurate size + compression-ratio security enforced |
| 10 | Tests built from real fixture QR image shapes (`local-testing/`) |
| 11 | Scanning from Auth Settings removed — no mode-transition race conditions possible |
| 12 | Draft data does not bleed into main project |

---

## Fixtures

Located in `local-testing/`:

| File | QR Type | Result |
|------|---------|--------|
| `app_user_medres_qr.png` | MEDRES Project | `MedresProjectQr`, PID=1 |
| `draft_form_medres_qr.png` | Draft/Demo | `DraftFormQr`, form=school_info |
| `standard_odk_draft_qr.png` | ODK Draft | `DraftFormQr` (shape decides, not domain) |
| `standard_odk_managed_qr.png` | ODK Managed | `StandardOdkManagedQr` → rejected |
