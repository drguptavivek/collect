 # When do ODK COllect forms get updated

When the ODK setting is set to **"Exactly match server"** (match_exactly), the local form library updates in these scenarios:
- Periodic Background Sync: Automatically triggered by a background worker (WorkManager). The timing is determined by the "Form update check" setting (e.g., every 15 minutes, 1 hour, etc.).
- Manual Sync: When a user manually taps the Refresh icon (or uses pull-to-refresh) in the "Fill Blank Form" screen.
On Setting Change: Immediately when the update mode is switched to "Exactly match server" or when the Server URL is modified.
App Startup: Background tasks are rescheduled upon app launch, though they follow their periodic interval rather than necessarily running immediately.

Important Note: Simply opening the "Fill Blank Form" screen triggers a local disk refresh (syncing the database with XML files on the device), but it does not automatically trigger a server sync.