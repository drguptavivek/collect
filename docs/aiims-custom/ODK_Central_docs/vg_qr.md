## 2. Managed QR Codes

**Purpose**: Secure app user enrollment with settings automation

**Encoded Data**:
```json
{
  "general": {
    "server_url": "https://central.example.com/v1/projects/1",
    "username": "app_user_id",
    "form_update_mode": "match_exactly",
    "automatic_update": true,
    "delete_send": false,
    "default_completed": false,
    "analytics": true,
    "metadata_username": "App User Display Name"
  },
  "admin": {
    "change_server": false,
    "admin_pw": "vg_custom"
  },
  "project": {
    "name": "Project Name",
    "project_id": "1"
  }
}
```