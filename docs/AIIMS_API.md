# AIIMS / VG App-User Auth API
This in brief describes the ODK Central API customziations that are deisgend to work hand in hand with the ODK Collect customizations donme in this reporsitory

## Overview
Short-lived, password-based authentication for Collect-style app users tied to projects. Tokens are bearer-only (no cookies) and expire based on `vg_app_user_session_ttl_days` (default 3 days) stored in `vg_settings`.

### Frontend Quick Reference
- **No long-lived tokens** are ever returned from create/list endpoints; only `/login` returns a short-lived bearer token.
- Listings always include `token: null`; use `/login` to obtain a token for data submission.
- Session cap and TTL are enforced server-side; a new login trims older sessions beyond the cap.
- All app-user requests must include `Authorization: Bearer <short-token>` (never cookies).
- Common error codes: `400` validation, `401` auth failure/expired token, `403` lack of project role or closed form, `404` not found/out-of-project.

### Password Policy
- Minimum 10 characters
- At least one uppercase, one lowercase, one digit, one special (`~!@#$%^&*()_+-=,.`)
- Rejects anything that does not meet all criteria

---

## App User Endpoints

### 1. Login for short-lived token
**POST /projects/:projectId/app-users/login**

- **Auth**: Anonymous.
- **Request (JSON)**:
  ```json
  { "username": "collect-user", "password": "GoodPass!1X" }
  ```
- **Response** — HTTP 200, application/json:
  ```json
  { "token": "abcd1234...tokenchars...", "projectId": 1, "expiresAt": "2025-12-19T16:00:00.000Z" }
  ```
  `projectId` comes from the linked `field_keys` row (app users are always project-scoped). `expiresAt` is the ISO timestamp of the short-lived bearer token.
- **Failure**: HTTP 401.2 `authenticationFailed`.
- **Lockout**: 5 failed attempts in 5 minutes per `username+IP` → 10-minute lock.

### 2. Create app user
**POST /projects/:projectId/app-users**

- **Auth**: Admin/manager on the project.
- **Request (JSON)**:
  ```json
  { "username": "collect-user", "password": "GoodPass!1X", "fullName": "Collect User", "phone": "+15551234567", "active": true }
  ```
- **Response** — HTTP 200:
  ```json
  { "id": 12, "createdAt": "...", "displayName": "Collect User", "token": null, "projectId": 1, "active": true }
  ```

### 3. List app users
**GET /projects/:projectId/app-users**

- **Auth**: Admin/manager on the project.
- **Response** — HTTP 200:
  ```json
  [
    {
      "id": 12,
      "projectId": 1,
      "displayName": "Collect User",
      "username": "collect-user",
      "token": null
    }
  ]
  ```

### 4. Update app user
**PATCH /projects/:projectId/app-users/:id**

- **Auth**: Admin/manager on the project.
- **Request (JSON)**:
  ```json
  { "fullName": "New Name", "phone": "+15557654321" }
  ```
- **Response** — HTTP 200.

### 5. Change password (self)
**POST /projects/:projectId/app-users/:id/password/change**

- **Auth**: App user bearer token.
- **Request (JSON)**:
  ```json
  { "oldPassword": "GoodPass!1X", "newPassword": "NewPass!2Y" }
  ```

### 6. Reset password (admin)
**POST /projects/:projectId/app-users/:id/password/reset**

- **Auth**: Admin/manager on the project.
- **Request (JSON)**:
  ```json
  { "newPassword": "ResetPass!3Z" }
  ```

### 7. Revoke sessions
- **Self**: `POST /projects/:projectId/app-users/:id/revoke`
- **Admin**: `POST /projects/:projectId/app-users/:id/revoke-admin`

### 8. Activate/Deactivate
**POST /projects/:projectId/app-users/:id/active**

- **Auth**: Admin/manager on the project.
- **Request (JSON)**: `{ "active": false }`
- **Effect**: Setting `false` terminates all sessions immediately.

---

## System Configuration Endpoints
Used by the Admin Settings UI.

### 1. Get Settings
**GET /system/settings**

- **Access**: Requires `config.read` permission (Admin).
- **Response**:
  ```json
  {
    "vg_app_user_session_ttl_days": "3",
    "vg_app_user_session_cap": "3"
  }
  ```

### 2. Update Settings
**PUT /system/settings**

- **Access**: Requires `config.set` permission (Admin).
- **Request**:
  ```json
  {
    "vg_app_user_session_ttl_days": "7",
    "vg_app_user_session_cap": "5"
  }
  ```

---

## Backend Schema Reference

### 1. `vg_field_key_auth` Table
Stores credentials for app users. One-to-one with `field_keys`.
- `actorId`: PK, FK to `field_keys.actorId`.
- `vg_username`: Unique, lowercase, trimmed.
- `vg_password_hash`: Bcrypt hash.
- `vg_active`: Boolean (default true).

### 2. `vg_settings` Table
Key-value store for global configuration.
- `vg_app_user_session_ttl_days`: Default 3.
- `vg_app_user_session_cap`: Default 3.
