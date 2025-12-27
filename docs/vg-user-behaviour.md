# AIIMS Collect User Behavior Documentation

This document describes the expected user-facing behavior of AIIMS Collect, particularly around multi-user scenarios on shared devices.

---

## Multi-User Project Persistence (Shared Device Behavior)

### Design Decision: **Option B - Clear Nothing on Logout**

When a user logs out of AIIMS Collect, the following data is **preserved** on the device:

| Data Type | Behavior on Logout | Rationale |
|-----------|-------------------|-----------|
| **Auth Token** | ✅ Cleared | Security - prevents unauthorized API access |
| **Local PIN** | ✅ Cleared | Security - forces next user to set own PIN |
| **Blank Forms** | ✅ **Preserved** | Efficiency - no re-download needed |
| **Saved Instances (Drafts)** | ✅ **Preserved** | Team visibility - shared device users can see drafts |
| **Submitted Instances** | ✅ **Preserved** | Audit trail - history remains on device |
| **Project Settings** | ✅ **Preserved** | Efficiency - URL/project config persists |
| **Cache** | ✅ **Preserved** | Efficiency - media cache persists |

### Implications

1. **Same Project, Different Users**: When User B logs into the same project that User A was using, User B **will see**:
   - All blank forms already downloaded
   - User A's saved drafts (if not submitted)
   - User A's submission history

2. **This is intended behavior** for AIIMS field deployments where:
   - Devices are shared among a trusted team
   - Multiple health workers may use the same tablet
   - Data continuity across shifts is desirable
   - Avoiding repeated form downloads saves bandwidth

3. **Security Consideration**: This design assumes organizational trust between users sharing a device. For scenarios requiring strict data isolation between users, a different approach would be needed.

### Project Switching via Find-or-Create

When a user manually configures a project (via settings icon) or scans a QR code:

1. System searches for an existing project with the same server URL
2. If found → **Switches to existing project** (data preserved)
3. If not found → Creates new project

This ensures multi-user data is never accidentally deleted when switching projects.

**Code Reference**: `AiimsLoginActivity.manualConfigureProject()` (lines 337-361)

---

## Session Lifecycle

### Login Flow
1. User enters credentials on AiimsLoginActivity
2. System authenticates against ODK Central backend
3. Token stored in secure preferences
4. User prompted to set local PIN (first login only)
5. Navigate to MainMenuActivity

### Logout Triggers
- Explicit logout via menu
- Token hard expiry (24h + 6h grace exceeded)
- 5 failed PIN attempts

### On Logout
- Auth token cleared
- Local PIN cleared  
- **All other data preserved** (forms, instances, settings)

---

## Related Documentation

- [AIIMS Architecture](AIIMS_ARCHITECTURE.md) - System design
- [Feature: Multi-User Persistence](FEAT_MULTIUSER_PERSISTENCE.md) - Implementation details
- [AIIMS Maintenance](AIIMS_MAINTENANCE.md) - Build and deployment
