# MEDRES ODK Collect: Complete Customization Overview

**GitHub Repository**: [drguptavivek/collect](https://github.com/drguptavivek/collect)  
**Version Line**: `v2025.3.3-MEDRES-RC4` and later

## Why MEDRES Exists

Standard ODK Collect assumes relatively stable connectivity, simpler authentication, and mostly single-user device ownership. MEDRES changes that assumption set for field medical research:

- devices may be shared
- connectivity may be intermittent
- projects may need strict onboarding controls
- field teams need both production collection and isolated draft testing

MEDRES addresses those constraints by layering a dedicated authentication and security module around ODK Collect while keeping ODK’s core form engine intact.

## Core MEDRES Modifications

### 1. Bearer token authentication

MEDRES replaces basic-auth style assumptions with a token-based model:

- server authentication happens through the MEDRES auth layer
- OpenRosa requests use injected bearer tokens
- token validity and refresh behavior are controlled by MEDRES policy

This reduces credential exposure and enables better session control.

### 2. Local PIN security

After login, MEDRES requires local PIN protection:

- mandatory PIN setup
- app lock on resume/background return
- limited failed attempts before wipe
- sensitive state stored in encrypted preferences

That makes shared-device use safer even when the device is offline.

### 3. Shared-device continuity

MEDRES separates what must be wiped from what should persist:

- tokens, PIN state, and session context are security state and can be cleared
- project configuration and ODK data can remain to support continuity on shared devices

This is why logout and rescan behavior must be precise rather than broad preference wipes.

### 4. QR-driven onboarding and testing

MEDRES uses typed QR workflows rather than one generic “import settings” path.

#### MEDRES project QR

Shape:
- `/v1/projects/<ID>`

Behavior:
- stage project metadata and settings
- return user to login
- on successful login, materialize tokenized ODK project configuration

#### Draft QR

Shape:
- `/v1/test/<TOKEN>/projects/<ID>/forms/<FORM_ID>/draft`

Behavior:
- preserve the full draft URL
- skip username/password login
- enter **Draft Testing Mode**
- treat `project.name` as display-only draft form metadata

#### Standard ODK managed QR

Shape:
- `/v1/key/<TOKEN>/projects/<ID>`

Behavior:
- reject

This separation is a key MEDRES safeguard. It prevents a managed ODK QR or a draft QR from being mistaken for a production onboarding QR.

## Persistence Model

MEDRES uses two main preference spaces:

### `medres_auth_prefs`

For non-sensitive state and staged QR context:

- auth/project staging
- draft-testing staging
- project mappings
- UI state flags

### `medres_auth_secure`

For sensitive state:

- auth tokens
- token expiry
- PIN hash/salt
- security/clock validation state

MEDRES then materializes the final active project into standard ODK project preferences only when the flow is validated.

## Security Design

The MEDRES security model is based on explicit boundaries rather than hidden magic:

- auth is handled in the MEDRES auth module
- ODK core remains responsible for form engine and project/runtime behavior
- staged QR data is separated from materialized project configuration
- sensitive data is kept out of normal shared preferences

For QR handling specifically, protection comes from strict parsing and bounded input:

- compressed size limits
- decompressed size limits
- compression-ratio guard
- URL-shape validation
- typed staging contexts
- rejection of incompatible managed ODK QRs

## Operational Benefits

This architecture gives research programs a cleaner set of workflows:

- production onboarding is explicit and authenticated
- draft form testing is isolated
- shared tablets remain usable without weakening security boundaries
- project configuration is controlled rather than implicitly overwritten

## Further Reading

- [Architecture Overview](medres-custom/01-ARCHITECTURE/overview.md)
- [QR Codes Reference](medres-custom/01-ARCHITECTURE/qr-codes.md)
- [Saved Preferences](medres-custom/01-ARCHITECTURE/saved_preferences.md)
- [Shared Device Continuity](medres-custom/05-FEATURES/shared-device-continuity.md)
- [Security Scenarios](medres-custom/05-FEATURES/security-scenarios.md)
