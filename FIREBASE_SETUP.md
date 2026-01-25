# Firebase Setup Instructions

The FlutterFire CLI had issues creating a new project, so we've created a manual configuration file. Here's how to complete the Firebase setup:

## Step 1: Get Firebase Configuration Values

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **ainews-f6d83** (or create a new one)
3. Click the gear icon ⚙️ > **Project Settings**
4. Scroll down to "Your apps" section

## Step 2: Add Android App

1. Click "Add app" > Select Android
2. **Android package name**: `com.paychat.app`
3. Click "Register app"
4. Download `google-services.json`
5. Place it in: `android/app/google-services.json`
6. Copy the config values and update `lib/core/firebase/firebase_options.dart`

Example Android config from Firebase Console:
```
{
  "project_info": {
    "project_number": "123456789",
    "project_id": "ainews-f6d83"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789:android:abc123...",
        "android_client_info": {
          "package_name": "com.paychat.app"
        }
      },
      "api_key": [
        {
          "current_key": "AIza..."  // <-- Use this for apiKey
        }
      ]
    }
  ]
}
```

## Step 3: Add iOS App (if needed)

1. Click "Add app" > Select iOS
2. **iOS bundle ID**: `com.paychat.app`
3. Click "Register app"
4. Download `GoogleService-Info.plist`
5. Add it to your iOS project in Xcode
6. Copy the config values and update `lib/core/firebase/firebase_options.dart`

## Step 4: Update firebase_options.dart

Open `lib/core/firebase/firebase_options.dart` and replace:

### For Android:
```dart
static const FirebaseOptions android = FirebaseOptions(
  apiKey: 'YOUR_ANDROID_API_KEY',        // From google-services.json
  appId: 'YOUR_ANDROID_APP_ID',          // mobilesdk_app_id
  messagingSenderId: 'YOUR_SENDER_ID',   // project_number
  projectId: 'ainews-f6d83',             // Already correct
  storageBucket: 'ainews-f6d83.appspot.com',
);
```

### For iOS:
```dart
static const FirebaseOptions ios = FirebaseOptions(
  apiKey: 'YOUR_IOS_API_KEY',            // From GoogleService-Info.plist
  appId: 'YOUR_IOS_APP_ID',              // GOOGLE_APP_ID
  messagingSenderId: 'YOUR_SENDER_ID',   // GCM_SENDER_ID
  projectId: 'ainews-f6d83',             // Already correct
  storageBucket: 'ainews-f6d83.appspot.com',
  iosBundleId: 'com.paychat.app',        // Already correct
);
```

## Step 5: Enable Firebase Services

In Firebase Console, enable these services:

1. **Authentication** > Sign-in method > Enable **Google**
2. **Firestore Database** > Create database (Production mode)
3. **Storage** > Get started
4. **Cloud Messaging** (automatically enabled)

## Step 6: Update Android Configuration

Add to `android/app/build.gradle` (if not already present):

```gradle
apply plugin: 'com.google.gms.google-services'
```

Add to `android/build.gradle` dependencies:

```gradle
dependencies {
    classpath 'com.google.gms:google-services:4.4.0'
}
```

## Step 7: Test the Configuration

Run the app:
```bash
flutter run
```

The app should initialize Firebase without errors. Check the console for:
```
Firebase initialized successfully
```

## Quick Start (If Using Existing Project)

If you already have a Firebase project set up for another app:

1. Use the same `google-services.json` and `GoogleService-Info.plist`
2. Just update the package name/bundle ID to match PayChat
3. Copy the config values to `firebase_options.dart`

## Need Help?

If you encounter issues:
- Check Firebase Console for correct project ID
- Verify google-services.json is in the correct location
- Ensure package names match exactly
- Run `flutter clean` and `flutter pub get`

---

**Note**: This manual setup is equivalent to what `flutterfire configure` does automatically. Once configured, everything will work the same way!
