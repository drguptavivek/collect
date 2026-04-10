# QR Codes Reference

## Quick Identification Guide

| Type | URL Pattern / content | Medres Scanner Behavior | Intended Use |
| :--- | :--- | :--- | :--- |
| **MEDRES Project** | `/v1/projects/<ID>` (No `/key/`) | **ACCEPTED** (Login) | Standard production login for hospital staff. |
| **Draft / Test** | `/v1/test/<TOKEN>/projects/<ID>/forms/<FORM_ID>/draft` | **ACCEPTED** (Draft Testing Mode) | Testing draft forms without affecting production onboarding. |
| **Standard ODK** | `/key/<TOKEN>` | **REJECTED** | **Do Not Use**. Prevents overwriting secure settings. |
| **Legacy ODK** | Base64 Encoded (decodes to `/key/`) | **REJECTED** | **Do Not Use**. Old ODK Central format. |

---

## Standard Managed QR Codes in usual upstream ODK Central


```json
{
  "general": {
    "server_url": "https://DOMAIN/v1/key/<TOKEN>/projects/<PROJECT>",
    "form_update_mode": "match_exactly",
    "autosend": "wifi_and_cellular"
  },
  "project": {
    "name": "Project XXX"
  },
  "admin": {}
}

```

## Legacy ODK Central QR Codes
Older ODK Central versions generate QRs that are **Base64 encoded** before ZLIB compression.
Structure after decoding is identical to Standard QR:
```json
{
  "general": {
    "server_url": "https://DOMAIN/v1/key/<TOKEN>/projects/<PROJECT>",
    "form_update_mode": "match_exactly",
    "autosend": "wifi_and_cellular"
  },
  "project": {
    "name": "Project XXX"
  },
  "admin": {}
}
```
**Behavior**: Rejected by Medres Scanner (contains `/key/`).


## MEDRES Project Configuration QR Codes
```json
{
  "general": {
    "server_url": "https://DOMAIN/v1/projects/<PROJECT_ID>",
    "username": "USER_NAME",
    "form_update_mode": "match_exactly",
    "automatic_update": true,
    "delete_send": false,
    "default_completed": false,
    "analytics": true,
    "metadata_username": "USER_NAME"
  },
  "project": {
    "name": "PROJECT_TITLE",
    "project_id": "PROJECT_ID"
  },
  "admin": {
    "change_server": false,
    "admin_pw": "XXXXXX"
  }
}
```

## Draft QR Codes
```json
{
    "general": {
        "server_url": "https://SERVER/v1/test/<TOKEN>/projects/<PROJECT_ID>/forms/<FORM_ID>/draft",
        "form_update_mode": "match_exactly",
        "autosend": "wifi_and_cellular"
    },
    "project": {
        "name": "[Draft] ODK Demo Form 1",
        "icon": "📝"
    },
    "admin": {}
}
```

Important semantics for draft QRs:
- `project.name` is the **draft form display label**, not the durable MEDRES project identity.
- `project.project_id` is usually absent in draft QRs and must be derived from the URL path if needed.
- The full draft URL is preserved and used directly for Draft Testing Mode.
- Draft metadata must not overwrite the real production project name/configuration.

## Domain Is Not the Classifier

The scanner classifies QRs by URL shape, not by server domain:
- A draft QR from a standard upstream ODK server is still accepted for Draft Testing Mode if it has the `/v1/test/.../forms/.../draft` shape.
- A managed ODK QR is rejected if it has the `/v1/key/<TOKEN>/projects/<ID>` shape, even if it comes from a MEDRES-related server.




---

## Security Protections

All QR codes (MEDRES, Demo, and Standard ODK) are subject to the following security validations:

### Size Limits
- **Compressed**: Max 4KB (prevents oversized QR codes)
- **Decompressed**: Max 16KB (prevents decompression bombs)
- **Ratio**: 4:1 maximum compression ratio

### Key Validation
- **General Settings**: Validated against `ProjectKeys` constants
- **Admin Settings**: Validated against `ProtectedProjectKeys.allKeys()`
- **Invalid Keys**: Logged and skipped (prevents injection attacks)

### Type Safety
- Boolean, String, Integer type checking
- Admin settings must be boolean only
- Non-boolean admin values rejected

### Sensitive Key Protection
Blocked from QR override:
- `server_url` (handled by login logic)
- `username` (handled by login logic)  
- `password` (never in QR)
- `protocol` (enforced by app)

### Attack Prevention
- ❌ Decompression bombs (4KB → gigabytes)
- ❌ Malicious key injection
- ❌ Standard ODK QR overwrites
- ❌ Credential theft via QR
- ❌ OOM/ANR crashes
