# Security Policy

## APK Signature Verification

To ensure you are using an authentic version of MEDRES ODK Collect, you can verify the APK's signature fingerprint. This prevents installation of tampered or malicious versions.

### Production Certificate Fingerprint (SHA-256)
All official release builds from this repository are signed with a certificate having the following fingerprint:

```text
75:AE:98:D1:72:E5:BF:EE:70:ED:A1:BC:77:E2:72:E4:DE:0D:73:D0:51:8F:0C:41:D6:FF:1A:71:B8:5C:28:DE
```

### How to Verify
If you have the Android SDK installed, you can verify any APK file by running:

```bash
apksigner verify --print-certs path/to/medres-collect.apk
```

The `SHA-256 digest` in the output MUST match the fingerprint listed above. If it does not match, **do not install the app** and report it immediately.

## Reporting a Vulnerability

If you discover a security vulnerability, please report it via the [Vulnerability Disclosure Policy](https://getodk.org/vdp).
