# Release Signing Guide

This guide explains how to generate a release signing certificate (keystore) and configure the app for production builds.

## 1. Generate a Keystore

To generate a "trusted" certificate for Android app signing, use the `keytool` command (included with the Java Development Kit).

Run this command in your terminal:

```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-alias
```

### Parameters explained:
- `-keystore my-release-key.jks`: The name of the file to create.
- `-alias my-alias`: A descriptive name for the key.
- `-keyalg RSA`: The encryption algorithm to use.
- `-keysize 2048`: The size of the key (2048 is standard).
- `-validity 10000`: How many days the certificate is valid (approx. 27 years).

> [!IMPORTANT]
> Keep your keystore file and passwords safe. If you lose them, you cannot update your app on the Play Store.

## 2. Configure the App

1. Create a file named `secrets.properties` in the root directory of the project.
2. Add the following lines to it, replacing the values with your own:

```properties
RELEASE_STORE_FILE=my-release-key.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

3. Ensure the `my-release-key.jks` file is also in the root directory (or update `RELEASE_STORE_FILE` with the path).

## 3. Build the Release APK

Once configured, you can build the release version of the MEDRES flavor:

```bash
./gradlew assembleMedresRelease
```

The signed APK will be generated in `collect_app/build/outputs/apk/medres/release/`.

## 4. How Devices Trust the App

Android uses the certificate in your keystore to identify you as the developer. Here is how trust is established:

### Digital Identity
The signature ensures that the APK has not been modified after it was signed. If someone tries to change the code, the signature becomes invalid, and Android will refuse to install or update the app.

### App Updates
Android only allows an app to be updated if the new APK is signed with the **exact same key** as the currently installed version. This prevents malicious actors from replacing your app with a different one.

### Distribution & Play Protect
- **Play Store Distribution**: If you upload to the Play Store, Google verifies your identity and uses **Google Play Protect** to scan the app for malware. This provides the highest level of trust for users.
- **Sideloading (Direct APK)**: If you distribute the APK directly (e.g., via a website) users will see a warning about "Installing from Unknown Sources". They must manually enable this setting to trust your app.
- **Enterprise/MDM**: For professional environments, you can use Mobile Device Management (MDM) tools to automatically trust and install your signed APK on employee devices.

## 5. Public Verification (Trust but Verify)

If you are distributing your APK outside of the Play Store, you should publish your **Certificate Fingerprints** (SHA-256) so users can verify them.

### How to get your Fingerprint
Run this command on your keystore:
```bash
keytool -list -v -keystore my-release-key.jks -alias my-alias
```
Look for the `SHA256:` line. It will look something like this:
`SHA256: 7B:E2:1A:...:55:BC`

### Where to host it?
- **GitHub README**: Listing the fingerprint in your repository's README or a dedicated SECURITY.md.
- **Official Website**: A "Downloads" page where you list the APK and its corresponding SHA-256 fingerprint.
- **App "About" Screen**: You can display the signing certificate fingerprint within the app itself so users can verify it after installation.

### How users verify
Technically savvy users can check the APK signature using:
```bash
apksigner verify --print-certs my-app.apk
```
By comparing the output with your public fingerprint, they can be 100% sure the file hasn't been tampered with.
