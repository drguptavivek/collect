# RPC School Survey API Documentation

**Version**: 1.3
**Last Updated**: December 15, 2024
**Base URL**: `http://localhost:5174/api` (Development)
**Authentication**: Device Token (Android) / Session (Web)

## Recent Updates (v1.3)

- **Enhanced User Payload**: Added `partnerName` field to both Login and Verify API responses for better user identification
- **Android Compatibility**: Android client supports both `partnerName` (camelCase) and `partner_name` (snake_case) field names
- **Consistent User Data**: User information now consistently includes partner name across all authentication endpoints

---

## 📚 Table of Contents

1. [Authentication APIs](#authentication-apis)
2. [Survey Management APIs](#survey-management-apis)
3. [School Management APIs](#school-management-apis)
4. [Sync APIs](#sync-apis)
5. [Device Management APIs](#device-management-apis)
6. [User Management APIs](#user-management-apis)
7. [Error Handling](#error-handling)
8. [Security & Rate Limiting](#security--rate-limiting)
9. [Field Naming Convention](#field-naming-convention)
10. [Testing & Development](#testing--development)

---

## 🔐 Authentication APIs

### POST `/api/auth/login`

Authenticates Android app users and issues device tokens.

**Purpose**: Primary authentication for Android app with device token generation
**Authentication**: None (public endpoint)
**Rate Limiting**: 15 attempts per 15 minutes per IP

#### Request Body
```json
{
  "email": "string",
  "password": "string",
  "deviceId": "string",
  "deviceInfo": "string"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | ✅ | User email address |
| password | string | ✅ | User password |
| deviceId | string | ✅ | Unique device identifier |
| deviceInfo | string | ✅ | Device description (model, OS, etc.) |

#### Response (200 OK)
```json
{
  "success": true,
  "user": {
    "id": "uuid",
    "email": "admin@example.com",
    "role": "national_admin|partner_manager|team_member|data_manager",
    "partnerId": "uuid|null",
    "partnerName": "Partner Organization Name|null",
    "name": "User Name"
  },
  "deviceToken": "jwt_token_string",
  "expiresAt": "2026-12-14T12:48:04.140Z",
  "requiresPinSetup": true,
  "message": "Login successful. Please set up your local PIN for offline access."
}
```

**Response Fields (user object)**:

| Field | Type | Description |
|-------|------|-------------|
| id | string | User UUID |
| email | string | User email address |
| role | string | User role: national_admin, partner_manager, team_member, or data_manager |
| partnerId | string | null | Partner UUID (for partner_manager and team_member roles) |
| partnerName | string | null | Partner organization name (null for national_admin and data_manager) |
| name | string | User's full name |

**Android Client Note**: The Android app uses `partner_name` (snake_case) as an alternate field name with `@SerializedName` annotation for backward compatibility.

#### Error Responses
- `400 Bad Request`: Missing required fields or invalid email format
- `401 Unauthorized`: Invalid credentials
- `500 Internal Server Error`: System error

---

### POST `/api/auth/verify`

Verifies device tokens for subsequent API calls.

**Purpose**: Validate device tokens for API access
**Authentication**: Device Token (Bearer token)

#### Headers
```
Authorization: Bearer <device_token>
```

#### Response (200 OK)
```json
{
  "valid": true,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "role": "partner_manager",
    "partnerId": "uuid",
    "partnerName": "Partner Organization Name"
  }
}
```

#### Error Responses
- `401 Unauthorized`: Invalid/expired token
- `401 Unauthorized`: Token requires re-authentication

---

### POST `/api/auth/refresh`

Refreshes device tokens before expiration.

**Purpose**: Extend device token lifecycle
**Authentication**: Device Token (Bearer token)

#### Request Body
```json
{
  "deviceId": "string"
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "deviceToken": "new_jwt_token",
  "expiresAt": "2026-12-14T12:48:04.140Z"
}
```

---

## 📋 Survey Management APIs

### POST `/api/surveys/submit`

Submits individual survey forms with immediate acknowledgment.

**Purpose**: Single survey submission from Android app
**Authentication**: Device Token (Bearer token)
**Rate Limiting**: 100 submissions per hour per device

#### Headers
```
Authorization: Bearer <device_token>
Content-Type: application/json
```

#### Request Body
```json
{
  "localId": "string",
  "surveyUniqueId": "101-201-5-A-001",
  "surveyDate": "2024-12-14",
  "districtId": "uuid",
  "areaType": "rural|urban",
  "schoolId": "uuid",
  "schoolType": "government|private|aided|other",
  "class": 5,
  "section": "A",
  "rollNo": "001",
  "studentName": "John Doe",
  "sex": "male|female",
  "age": 10,
  "consent": "yes|refused|absent",

  // Section B: Distance Vision
  "usesDistanceGlasses": true,
  "unaidedVaRightEye": "6/6",
  "unaidedVaLeftEye": "6/6",
  "presentingVaRightEye": "6/6",
  "presentingVaLeftEye": "6/6",
  "referredForRefraction": true,

  // Section C: Refraction Details (conditional - if referred for refraction)
  "sphericalPowerRight": -2.50,
  "sphericalPowerLeft": -2.25,
  "cylindricalPowerRight": -0.75,
  "cylindricalPowerLeft": -0.50,
  "axisRight": 90,
  "axisLeft": 85,
  "bcvaRightEye": "6/6",
  "bcvaLeftEye": "6/6",

  // Section D: Main Cause
  "causeRightEye": "refractive_error",
  "causeRightEyeOther": null,
  "causeLeftEye": "refractive_error",
  "causeLeftEyeOther": null,

  // Section E: Barriers (up to 2)
  "barrier1": "distance",
  "barrier2": "cost",

  // Section F: Follow-up Details (conditional - if uses glasses)
  "timeSinceLastCheckup": "6_months",
  "placeOfLastRefraction": "government_hospital",
  "costOfGlasses": "free",
  "usesSpectacleRegularly": true,
  "spectacleAlignmentCentering": true,
  "spectacleScratches": "none",
  "spectacleFrameIntegrity": "good",

  // Section G: Advice
  "spectaclesPrescribed": true,
  "referredToOphthalmologist": false,

  // Metadata
  "submittedAt": "2024-12-14T10:30:00Z"
}
```

#### Important: Field Naming Convention

**JavaScript/API Request**: Use `camelCase` for all field names in JSON requests
**Database Storage**: Fields are stored as `snake_case` in PostgreSQL database

**Mixed Pattern Fields**: Some fields use `snake_case` even in JavaScript due to Drizzle ORM mapping:
- `unaided_va_right_eye`, `unaided_va_left_eye`
- `presenting_va_right_eye`, `presenting_va_left_eye`
- `referredForRefraction` (camelCase)
- All other fields follow standard `camelCase` in requests

**Examples**:
```json
{
  "localId": "string",           // camelCase in JSON
  "studentName": "John Doe",     // camelCase in JSON
  "unaidedVaRightEye": "6/6",    // camelCase in JSON, maps to snake_case in DB
  "presentingVaRightEye": "6/6"  // camelCase in JSON, maps to snake_case in DB
}
```

#### Field Validation & Enum Values

**Section A (Basic Details)**: All required
- `areaType`: `"rural"` | `"urban"`
- `schoolType`: `"government"` | `"private"` | `"aided"` | `"other"`
- `sex`: `"male"` | `"female"`
- `consent`: `"yes"` | `"refused"` | `"absent"`

**Section B (Distance Vision)**: All required
- `usesDistanceGlasses`: `boolean`
- `presentingVaRightEye` & `presentingVaLeftEye`: `"6/6"`, `"6/9"`, `"6/12"`, `"6/18"`, `"6/24"`, `"6/36"`, `"6/60"`, `"3/60"`, `"1/60"`, `"HM"`, `"PL"`, `"NPL"`
- `unaidedVaRightEye` & `unaidedVaLeftEye`: Same as presenting VA (optional)

**Section C (Refraction Details)**: Optional, only if `referredForRefraction: true`
- `sphericalPowerRight/Left`: Decimal values (e.g., `-2.50`, `+1.75`)
- `cylindricalPowerRight/Left`: Decimal values (e.g., `-0.75`, `+0.50`)
- `axisRight/Left`: Integer values (0-180)
- `bcvaRightEye` & `bcvaLeftEye`: Same VA format as presenting VA

**Section D (Main Cause)**: Optional
- `causeRightEye` & `causeLeftEye`: `"refractive_error"`, `"cataract"`, `"corneal_opacity"`, `"glaucoma"`, `"retinal_disease"`, `"other"`

**Section E (Barriers)**: Optional (up to 2)
- `barrier1` & `barrier2`: `"cost"`, `"distance"`, `"awareness"`, `"fear"`, `"family_opposition"`, `"other"`

**Section F (Follow-up Details)**: Optional, conditional based on glasses usage
- `timeSinceLastCheckup`: `"never"`, `"1_year"`, `"2_years"`, `"3_years"`, `"4_years"`, `"5_years"`, `"6_months"`
- `placeOfLastRefraction`: `"government"` | `"private_ngo"`
- `costOfGlasses`: `"free"` | `"paid"`
- `spectacleScratches`: `"none"` | `"superficial_few"` | `"deep_multiple"`
- `spectacleFrameIntegrity`: `"not_broken"` | `"broken_taped_glued"`
- `usesSpectacleRegularly`, `spectacleAlignmentCentering`: `boolean`

**Section G (Advice)**: All required
- `spectaclesPrescribed`: `boolean`
- `referredToOphthalmologist`: `boolean`

**Metadata**: Optional
- `submittedAt`: ISO 8601 datetime (defaults to current time if not provided)

#### Response (201 Created)
```json
{
  "success": true,
  "surveyId": "uuid",
  "timestamp": "2024-12-14T10:30:00Z",
  "ack": "Form received and processed successfully"
}
```

#### Error Responses
- `400 Bad Request`: Missing required fields or invalid data
- `403 Forbidden`: School access denied (partner scoping)
- `409 Conflict`: Duplicate survey (surveyUniqueId already exists)
- `401 Unauthorized`: Invalid device token
- `500 Internal Server Error`: System error

---

### POST `/api/surveys/unique-id`

Validates survey unique ID format.

**Purpose**: Check if survey unique ID follows correct format
**Authentication**: Device Token (Bearer token)

#### Request Body
```json
{
  "surveyUniqueId": "101-201-5-A-001"
}
```

#### Response (200 OK)
```json
{
  "valid": true,
  "format": "district_code-school_code-class-section-roll_no"
}
```

---

## 🏫 School Management APIs

### GET `/api/schools/by-partner`

Retrieves schools for a specific partner.

**Purpose**: Get partner-scoped school list for Android app
**Authentication**: Device Token (Bearer token) OR Web Session
**Partner Scoping**: Enforced based on user role and partner

#### Headers
```
Authorization: Bearer <device_token> // OR web session cookie
```

#### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| partnerId | string | ❌ | Partner UUID (defaults to user's partner) |

#### Response (200 OK)
```json
{
  "success": true,
  "schools": [
    {
      "id": "uuid",
      "name": "Government Primary School",
      "code": "201",
      "districtId": "uuid",
      "districtName": "Central District",
      "address": "123 Main Street",
      "schoolType": "government",
      "areaType": "urban",
      "isActive": true
    }
  ]
}
```

#### Error Responses
- `400 Bad Request`: Partner ID required
- `403 Forbidden`: Access denied (partner scoping)
- `401 Unauthorized`: Authentication required

---

### GET `/api/schools/schools`

Internal API for school selection dropdowns.

**Purpose**: Filterable school list for web forms
**Authentication**: Web Session

#### Query Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| districtId | string | ❌ | Filter by district |
| partnerId | string | ❌ | Filter by partner |

---

## 🔄 Sync APIs

### POST `/api/sync/upload`

Bulk upload of encrypted survey forms with individual acknowledgments.

**Purpose**: Sync multiple offline submissions at once
**Authentication**: Device Token (Bearer token)
**Batch Size**: Maximum 100 forms per request
**Rate Limiting**: 10 bulk syncs per hour per device

#### Request Body
```json
{
  "forms": [
    {
      "localId": "local_form_id_1",
      "encryptedData": "base64_encrypted_survey_data",
      "checksum": "sha256_hash"
    }
  ],
  "encryptionKey": "encryption_key_for_data"
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "processed": 2,
  "results": [
    {
      "localId": "local_form_id_1",
      "success": true,
      "surveyId": "uuid",
      "timestamp": "2024-12-14T10:30:00Z",
      "ack": "Form received and processed successfully"
    },
    {
      "localId": "local_form_id_2",
      "success": false,
      "error": "Duplicate survey",
      "existingId": "uuid"
    }
  ]
}
```

#### Processing Details
- **Checksum Verification**: Ensures data integrity
- **Individual Processing**: Each form processed separately
- **Partial Success**: Some forms may succeed while others fail
- **Duplicate Detection**: Checks surveyUniqueId for duplicates
- **Partner Scoping**: Enforces school access permissions

---

### POST `/api/sync/status`

Check sync status and operations.

**Purpose**: Monitor sync operations and device status
**Authentication**: Device Token (Bearer token)

#### Request Body
```json
{
  "deviceId": "string",
  "deviceInfo": "string"
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "deviceStatus": "active",
  "lastSync": "2024-12-14T10:30:00Z",
  "pendingForms": 0,
  "serverTime": "2024-12-14T10:35:00Z"
}
```

---

## 📱 Device Management APIs

### GET `/api/device-tokens`

List device tokens for current user.

**Purpose**: Manage authenticated devices
**Authentication**: Web Session

#### Response (200 OK)
```json
{
  "success": true,
  "tokens": [
    {
      "id": "uuid",
      "deviceId": "android_device_001",
      "deviceInfo": "Samsung Galaxy S21",
      "lastUsed": "2024-12-14T10:30:00Z",
      "expiresAt": "2026-12-14T12:48:04.140Z",
      "isRevoked": false,
      "ipAddress": "192.168.1.100"
    }
  ]
}
```

---

### POST `/api/device-tokens/[id]/revoke`

Revoke a specific device token.

**Purpose**: Remove access for lost/stolen devices (also used by Android logout)
**Authentication**: Device Token (Bearer) preferred; if the token is expired, you can pass the raw device token as `[id]` and the server will still revoke it after signature verification.
**URL Parameters**: `id` - Device token UUID *or* the raw device token string (JWT)

#### Request (with valid bearer)
```
POST /api/device-tokens/{tokenId}/revoke
Authorization: Bearer <device_token>
```

#### Request (fallback when bearer is expired)
```
POST /api/device-tokens/{raw_device_token}/revoke
```

#### Response (200 OK)
```json
{
  "success": true,
  "message": "Device token revoked successfully"
}
```

---

## 👥 User Management APIs

### POST `/api/users/check-email`

Check email availability during user creation.

**Purpose**: Validate email uniqueness
**Authentication**: Web Session

#### Request Body
```json
{
  "email": "newuser@example.com"
}
```

#### Response (200 OK)
```json
{
  "available": true,
  "message": "Email is available"
}
```

---

## 📝 Field Naming Convention

### Overview
The RPC School Survey API uses a mixed naming convention pattern due to Drizzle ORM mappings:

### API Request Format (JavaScript JSON)
- **Use**: `camelCase` for all field names
- **Example**: `"studentName"`, `"areaType"`, `"usesDistanceGlasses"`

### Database Storage (PostgreSQL)
- **Use**: `snake_case` for all column names
- **Example**: `student_name`, `area_type`, `uses_distance_glasses`

### Mixed Pattern Fields
Some vision-related fields use `snake_case` even in the API due to Drizzle ORM schema definitions:

```javascript
// API Request (JSON)
{
  "unaidedVaRightEye": "6/6",        // Maps to unaided_va_right_eye
  "unaidedVaLeftEye": "6/9",         // Maps to unaided_va_left_eye
  "presentingVaRightEye": "6/6",     // Maps to presenting_va_right_eye
  "presentingVaLeftEye": "6/6",      // Maps to presenting_va_left_eye
  "referredForRefraction": true,     // Standard camelCase
  "sphericalPowerRight": -2.50,      // Standard camelCase
  "studentName": "John Doe",         // Standard camelCase
}
```

### Why This Pattern?
1. **Drizzle ORM**: The JavaScript schema defines field names, which may use snake_case for complex database relationships
2. **Consistency**: Most API fields follow camelCase convention for JavaScript developers
3. **Database Standards**: PostgreSQL columns follow snake_case naming convention

### Development Guidelines
1. **Always use camelCase** in your API requests unless specifically documented otherwise
2. **Don't worry about database column names** - the ORM handles the mapping automatically
3. **Follow the documented field names** exactly as shown in the API examples
4. **Mixed pattern fields are clearly documented** in the field validation section

---

### POST `/api/users/[id]/reset-credentials`

Reset user password and credentials.

**Purpose**: Password reset and account recovery
**Authentication**: Web Session
**Role Requirements**: national_admin, data_manager, or partner_manager (own users)

#### Response (200 OK)
```json
{
  "success": true,
  "message": "Credentials reset successfully",
  "temporaryPassword": "new_temp_password"
}
```

---

## 🧪 Testing & Development

### Survey Submission Examples

#### Example 1: Simple Survey (No Glasses)
```bash
curl -X POST http://localhost:5174/api/surveys/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <device_token>" \
  -d '{
    "localId": "test_local_001",
    "surveyUniqueId": "101-201-5-A-001",
    "surveyDate": "2024-12-14",
    "districtId": "<district_uuid>",
    "areaType": "urban",
    "schoolId": "<school_uuid>",
    "schoolType": "government",
    "class": 5,
    "section": "A",
    "rollNo": "001",
    "studentName": "Test Student",
    "sex": "male",
    "age": 10,
    "consent": "yes",
    "usesDistanceGlasses": false,
    "presentingVaRightEye": "6/6",
    "presentingVaLeftEye": "6/6",
    "referredForRefraction": false,
    "spectaclesPrescribed": false,
    "referredToOphthalmologist": false
  }'
```

#### Example 2: Complex Survey (With Glasses and Refraction)
```bash
curl -X POST http://localhost:5174/api/surveys/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <device_token>" \
  -d '{
    "localId": "test_local_002",
    "surveyUniqueId": "101-201-5-A-002",
    "surveyDate": "2024-12-14",
    "districtId": "<district_uuid>",
    "areaType": "rural",
    "schoolId": "<school_uuid>",
    "schoolType": "government",
    "class": 6,
    "section": "B",
    "rollNo": "025",
    "studentName": "Test Student With Glasses",
    "sex": "female",
    "age": 11,
    "consent": "yes",
    "usesDistanceGlasses": true,
    "unaidedVaRightEye": "6/12",
    "unaidedVaLeftEye": "6/9",
    "presentingVaRightEye": "6/6",
    "presentingVaLeftEye": "6/6",
    "referredForRefraction": true,
    "sphericalPowerRight": -2.50,
    "sphericalPowerLeft": -2.25,
    "cylindricalPowerRight": -0.75,
    "cylindricalPowerLeft": -0.50,
    "axisRight": 90,
    "axisLeft": 85,
    "bcvaRightEye": "6/6",
    "bcvaLeftEye": "6/6",
    "causeRightEye": "refractive_error",
    "causeLeftEye": "refractive_error",
    "barrier1": "cost",
    "barrier2": "distance",
    "timeSinceLastCheckup": "1_year",
    "placeOfLastRefraction": "government",
    "costOfGlasses": "free",
    "usesSpectacleRegularly": true,
    "spectacleAlignmentCentering": true,
    "spectacleScratches": "none",
    "spectacleFrameIntegrity": "not_broken",
    "spectaclesPrescribed": true,
    "referredToOphthalmologist": false
  }'
```

### Test Credentials
```json
{
  "partner_manager": {
    "email": "manager@example.com",
    "password": "password123",
    "role": "partner_manager"
  },
  "team_member": {
    "email": "team@example.com",
    "password": "password123",
    "role": "team_member"
  }
}
```

### Verified Working Features
- ✅ Single survey submission with all 7 sections
- ✅ Boolean field handling (true/false values)
- ✅ Conditional field validation (refraction details only when needed)
- ✅ Partner scoping and access control
- ✅ Survey unique ID validation
- ✅ Duplicate survey prevention
- ✅ Comprehensive audit logging
- ✅ Bulk sync operations (100 forms max per request)
- ✅ Device token authentication and refresh

---

## ❌ Error Handling

### Standard Error Response Format
```json
{
  "success": false,
  "error": "Error message",
  "code": "ERROR_CODE",
  "details": {
    "field": "additional_error_details"
  }
}
```

### HTTP Status Codes
| Status | Meaning | Description |
|--------|---------|-------------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request data |
| 401 | Unauthorized | Authentication required or failed |
| 403 | Forbidden | Access denied (insufficient permissions) |
| 409 | Conflict | Resource conflict (duplicates) |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### Common Error Codes
| Code | Description |
|------|-------------|
| `INVALID_CREDENTIALS` | Username or password incorrect |
| `TOKEN_EXPIRED` | Device token has expired |
| `ACCESS_DENIED` | User lacks required permissions |
| `DUPLICATE_RESOURCE` | Resource already exists |
| `VALIDATION_ERROR` | Request data validation failed |
| `RATE_LIMIT_EXCEEDED` | Too many requests |

---

## 🔒 Security & Rate Limiting

### Authentication Methods
1. **Device Token**: Long-lived JWT tokens for Android apps (1 year)
2. **Web Session**: HTTP-only cookies for web interface

### Rate Limiting
| Endpoint | Limit | Period | Scope |
|----------|-------|--------|-------|
| `/api/auth/login` | 15 | 15 minutes | Per IP |
| `/api/surveys/submit` | 100 | 1 hour | Per device |
| `/api/sync/upload` | 10 | 1 hour | Per device |
| General APIs | 1000 | 1 hour | Per user |

### Security Headers
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
```

### Data Validation
- **Server-side validation** for all inputs
- **SQL injection protection** via parameterized queries
- **XSS prevention** with input sanitization
- **CSRF protection** for web forms

### Audit Logging
All sensitive operations are logged with:
- User information
- IP addresses and user agents
- Timestamps
- Old/new data changes
- Success/failure status

---

## 🧪 Testing & Development

### Test Credentials
```json
{
  "admin": {
    "email": "admin@example.com",
    "password": "password123",
    "role": "national_admin"
  },
  "manager": {
    "email": "manager@example.com",
    "password": "password123",
    "role": "partner_manager"
  },
  "team": {
    "email": "team@example.com",
    "password": "password123",
    "role": "team_member"
  }
}
```
