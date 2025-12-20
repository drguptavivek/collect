# AIIMS Auth Module - Unit Test Strategy

This document outlines the testing strategy for the `aiims_auth_module`, including a detailed table of test cases, their rationale, and expected outcomes.

## Unit Test Table

| Component | Test Name | Rationale | Expectation | Type |
| :--- | :--- | :--- | :--- | :--- |
| **AiimsSecurityUtils** | `generateSalt_returnsNonEmptyString` | Salt must be generated for password hashing. | Returns a non-null, non-empty Base64 string. | Positive |
| | `hashPin_returnsConsistentHash` | Hashing must be deterministic given the same salt. | Same PIN + Same Salt == Same Hash. | Positive |
| | `hashPin_returnsDifferentHashForDifferentSalts` | Hashing must be randomized by salt. | Same PIN + Different Salt != Same Hash. | Negative |
| | `verifyPin_returnsTrueForCorrectPin` | Verification logic must pass valid credentials. | Valid PIN + Correct Hash/Salt returns `true`. | Positive |
| | `verifyPin_returnsFalseForIncorrectPin` | Verification logic must reject invalid credentials. | Wrong PIN + Correct Hash/Salt returns `false`. | Negative |
| | `encryptDecrypt_roundTripWorks` | Data must be recoverable after encryption. | Decrypted(Encrypted(Data)) == Data. | Positive |
| | `validatePinStrength_tooShort` | Security policy: PINs must be >= 4 chars. | "123" returns `TOO_SHORT`. | Negative |
| | `validatePinStrength_tooSimple` | Security policy: Repeated digits are unsafe. | "1111" returns `TOO_SIMPLE`. | Negative |
| | `validatePinStrength_valid` | Valid PINs must be accepted. | "1234" returns `WEAK` or better (not INVALID). | Positive |
| **AiimsAuthManager** | `login_success_storesTokenAndUpdatesState` | Login must persist headers and update memory. | Call client.login -> Success -> Save Prefs -> State=LOGGED_IN. | Positive |
| | `login_failure_returnsErrorAnd DoesNotChangeState` | Failed login shouldn't corrupt state. | Call client.login -> Error -> Prefs empty -> State=LOGGED_OUT. | Negative |
| | `login_withDifferentUser_clearsPin` | Security: Prevent PIN reuse across users. | If OldUser.id != NewUser.id -> Call PinManager.clearPin(). | Positive |
| | `login_withSameUser_keepsPin` | UX: Don't force PIN reset if re-logging in same user. | If OldUser.id == NewUser.id -> PinManager.clearPin() NOT called. | Positive |
| | `logout_revokesTokenAndClearsData` | Logout must contact server and wipe local data. | Call client.revoke -> Call ProjectCleaner.clear -> Remove Prefs -> State=LOGGED_OUT. | Positive |
| | `refreshState_restoresFromPrefs` | App restart must restore session. | Load Prefs -> If valid token -> State=LOGGED_IN. | Positive |
| | `refreshState_expiresIfServerReachable` | Security: If expired AND server responds -> Logout. | Expired + Ping Success -> State=LOGGED_OUT. | Negative |
| | `refreshState_allowsGraceIfServerUnreachable` | Usability: If expired AND server unavailable -> Allow Access. | Expired + Ping Fail -> State=LOGGED_IN. | Positive |
| **PinManager** | `savePin_storesPinAndResetsAttempts` | Saving a new PIN must clear any previous failure history. | Save "1234" -> Prefs has "1234", attempts=0. | Positive |
| | `verifyPin_success_resetsAttempts` | Successful login should clear "strikes" against the user. | Verify correct PIN -> Returns true, attempts=0. | Positive |
| | `verifyPin_failure_incrementsAttempts` | Failed login must be tracked to prevent brute force. | Verify wrong PIN -> Returns false, attempts=old+1. | Negative |
| | `isMaxAttemptsReached_returnsTrueAfterLimit` | Lockout threshold must be enforced. | After 3 failures -> returns true. | Negative |
| | `clearPin_removesAllData` | Logout/Reset must wipe security credentials. | Call clearPin -> isPinSet=false, attempts=0. | Positive |
| **TokenRevocation** | `markPending_savesToPrefs` | Must persist revocation requests when offline. | Call markPending -> Prefs count > 0. | Positive |
| | `processPending_doesNothingIfOffline` | No point retrying if network is down. | isOnline=false -> Verify revokeSession NOT called. | Negative |
| | `processPending_callsRevokeAndClearsOnSuccess` | Successful sync should remove pending item. | isOnline=true, revoke=Success -> Prefs count = 0. | Positive |
| | `processPending_keepsDataOnFailure` | Failed sync must retry later. | isOnline=true, revoke=Failure -> Prefs count > 0. | Negative |
