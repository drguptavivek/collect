# QR CODES

## Standard Managed QR Codes in usual upstream ODK CEntral

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


## MEDRES Project Cnmfiguration QR Codes
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

## Draft QR Codes - Smae in MEDRES and upstream
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



