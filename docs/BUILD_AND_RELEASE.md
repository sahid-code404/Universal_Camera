# Build and release

## Local debug

```bash
./gradlew :app:assembleDebug
```

## Tests

```bash
./gradlew testDebugUnitTest lintDebug
```

## Signed release locally

Create `keystore.properties` from `keystore.properties.example` and point `storeFile` to your production keystore. Then:

```bash
./gradlew :app:assembleRelease
```

Never commit the keystore or real passwords.

## GitHub release

Push a semantic tag:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The release workflow derives `versionName` from the tag and uses the monotonically increasing GitHub workflow run number as `versionCode`, then builds the signed APK, computes SHA-256, creates `release-manifest.json`, and uploads both assets.
