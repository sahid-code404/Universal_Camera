# GitHub OTA update system

## Scope

This repository is intended for GitHub/sideload distribution. The app checks the latest GitHub Release for `sahid-code404/Universal_Camera`.

## Release assets

Each production release must upload:

1. `Camera-<version>.apk`
2. `release-manifest.json`

Manifest schema:

```json
{
  "schema": 1,
  "versionCode": 2,
  "versionName": "0.1.1",
  "minSdk": 28,
  "apkAssetName": "Camera-0.1.1.apk",
  "sha256": "...",
  "signingCertSha256": "...",
  "changelog": "...",
  "mandatory": false
}
```

## Client verification

Before installer handoff the updater verifies:

- manifest schema
- `versionCode` is newer than installed version
- device SDK >= manifest `minSdk`
- APK file SHA-256 matches manifest
- manifest signing certificate hash, when supplied, matches the installed app signing certificate
- the APK remains under the app's private cache until installer handoff

Android's package installer also enforces signing-certificate continuity when replacing an installed package.

## Android limitation

A normal third-party Android app cannot silently self-install an APK. The user may need to:

- allow “Install unknown apps” for Camera
- confirm the Android package installer dialog

Do not attempt privileged/root/device-owner silent installation in the normal consumer build.

## Release signing

GitHub Actions expects these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Optional:
- `ANDROID_SIGNING_CERT_SHA256` — release certificate fingerprint for the OTA manifest

Never rotate the production signing key casually: an APK signed with a different key cannot update the existing package in place.

## Update cadence

The app performs a lightweight check on launch if the last check is older than 12 hours and offers a manual “Check for updates” action. It does not download an APK until the user accepts the update.
