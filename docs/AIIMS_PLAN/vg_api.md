# VG App-User Auth API

Short-lived, password-based auth for Collect-style app users tied to projects. Tokens are bearer-only (no cookies) and expire based on `vg_app_user_session_ttl_days` (default 3 days) stored in `vg_settings`.

## Frontend quick reference
- No long-lived tokens are ever returned from create/list endpoints; only `/login` returns a short-lived bearer token.
- Listings always include `token: null`; use `/login` to obtain a token for data submission.
- Session cap and TTL are enforced server-side; a new login trims older sessions beyond the cap.
- All app-user requests must include `Authorization: Bearer <short-token>` (never cookies).
- Common error codes: `400` validation, `401` auth failure/expired token, `403` lack of project role or closed form, `404` not found/out-of-project.

## Password policy
- Minimum 10 characters
- At least one uppercase, one lowercase, one digit, one special (`~!@#$%^&*()_+-=,.`)
- Rejects anything that does not meet all criteria

## Endpoints

### Create app user
**POST /projects/:projectId/app-users**

- Auth: Admin/manager on the project.
- Request (JSON):
  ```json
  { "username": "collect-user", "password": "GoodPass!1X", "fullName": "Collect User", "phone": "+15551234567", "active": true }
  ```
- Response — HTTP 200, application/json (app-user record; no long-lived token is minted):
  ```json
  { "id": 12, "createdAt": "2025-12-16T16:00:00.000Z", "updatedAt": null, "displayName": "Collect User", "token": null, "projectId": 1, "active": true }
  ```

### Login for short-lived token
**POST /projects/:projectId/app-users/login**

- Auth: Anonymous.
- Request (JSON):
  ```json
  { "username": "collect-user", "password": "GoodPass!1X" }
  ```
- Response — HTTP 200, application/json:
  ```json
  { "token": "abcd1234...tokenchars...", "projectId": 1, "expiresAt": "2025-12-19T16:00:00.000Z" }
  ```
  `projectId` comes from the linked `field_keys` row (app users are always project-scoped). `expiresAt` is the ISO timestamp of the short-lived bearer token and is set from `vg_app_user_session_ttl_days`. Cookies ignored.
- Failure: HTTP 401.2 `authenticationFailed` (generic message).
- Lockout: 5 failed attempts in 5 minutes per `username+IP` → 10-minute lock. Attempts are logged in `vg_app_user_login_attempts` with success/failure.

### List app users
**GET /projects/:projectId/app-users**

- Auth: Admin/manager on the project.
- Response — HTTP 200, application/json:
  ```json
  [
    {
      "id": 12,
      "projectId": 1,
      "displayName": "Collect User",
      "createdAt": "2025-12-16T16:00:00.000Z",
      "updatedAt": null,
      "token": null
    }
  ]
  ```
  `token` is always `null` in listings; use `/login` for a short-lived token. Extended metadata (`X-Extended-Metadata: true`) also returns `createdBy` and `lastUsed`.

### Update app user (name/phone)
**PATCH /projects/:projectId/app-users/:id**

- Auth: Admin/manager on the project.
- Request (JSON): both fields optional; omit to leave unchanged.
  ```json
  { "fullName": "New Name", "phone": "+15557654321" }
  ```
  `fullName` must be a non-empty string; `phone` is trimmed, optional, and capped at 25 characters. `username` is immutable once created.
- Response — HTTP 200, application/json (no token is ever returned):
  ```json
  { "id": 12, "projectId": 1, "displayName": "New Name", "phone": "+15557654321", "active": true, "username": "collect-user", "token": null }
  ```

### Change password (self)
**POST /projects/:projectId/app-users/:id/password/change**

- Auth: App user bearer token.
- Request (JSON):
  ```json
  { "oldPassword": "GoodPass!1X", "newPassword": "NewPass!2Y" }
  ```
- Response — HTTP 200, application/json:
  ```json
  { "success": true }
  ```
  All sessions for that actor are terminated.

### Reset password (admin)
**POST /projects/:projectId/app-users/:id/password/reset**

- Auth: Admin/manager on the project.
- Request (JSON):
  ```json
  { "newPassword": "ResetPass!3Z" }
  ```
- Response — HTTP 200, application/json:
  ```json
  { "success": true }
  ```
  All sessions for that actor are terminated.

### Revoke own sessions
**POST /projects/:projectId/app-users/:id/revoke**

- Auth: App user bearer token (self).
- Response — HTTP 200, application/json:
  ```json
  { "success": true }
  ```

### Revoke sessions (admin)
**POST /projects/:projectId/app-users/:id/revoke-admin**

- Auth: Admin/manager on the project.
- Response — HTTP 200, application/json:
  ```json
  { "success": true }
  ```

### Activate/deactivate
**POST /projects/:projectId/app-users/:id/active**

- Auth: Admin/manager on the project.
- Request (JSON):
  ```json
  { "active": false }
  ```
- Response — HTTP 200, application/json:
  ```json
  { "success": true }
  ```
  Setting `active: false` deactivates and terminates sessions; deactivated users cannot log in or be authenticated.

## Expected behavior
- Sessions remain valid only while `vg_field_key_auth.vg_active` is true and until `expiresAt` (no sliding refresh).
- Project linkage is enforced: the login response carries the app user's `projectId`, and existing sessions are only usable while the related `field_key` stays active for that project.
- All auth checks use bearer headers; cookies are ignored on these routes to minimize CSRF surface.
- Audit events for creation, password operations, revocation, activation, and login success/failure should be emitted with vg-prefixed action identifiers.
- Listings never expose active short tokens; frontends must explicitly call `/login` to obtain one for submissions.

## Configuration
- Session TTL is driven by the DB setting `vg_app_user_session_ttl_days` stored in `vg_settings`; default seed is `3` days (see migration `plan/sql/vg_app_user_auth.sql`). If the setting is absent, the backend falls back to 3 days.
- Session cap is driven by DB setting `vg_app_user_session_cap` in `vg_settings`; default seed is `3` active sessions per app user. Each login trims older sessions beyond the cap.
