# PayChat

PayChat is now a native Android app written in Kotlin with Jetpack Compose.

## Stack

- Kotlin + Jetpack Compose
- Firebase Authentication
- Cloud Firestore
- Firebase Storage

## Current App Flow

- Splash and session restore
- Phone number sign-in with OTP
- Profile setup and profile editing
- Chat inbox and conversation view
- Chat-linked transaction records
- Blocked-account handling

## Build

From the Android project directory:

```powershell
cd android
./gradlew.bat :app:assembleDebug
```

The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Release Build

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in your release keystore path, alias, and passwords.
3. Build the optimized release artifacts:

```powershell
cd android
./gradlew.bat :app:assembleRelease :app:bundleRelease
```

Outputs:

- Release APK: `android/app/build/outputs/apk/release/app-release.apk`
- Android App Bundle: `android/app/build/outputs/bundle/release/app-release.aab`

If no `keystore.properties` file is present, the local release build falls back to the debug signing key so the APK can still be installed for testing.
