import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart' show defaultTargetPlatform, kIsWeb, TargetPlatform;

class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      case TargetPlatform.iOS:
        return ios;
      case TargetPlatform.macOS:
        throw UnsupportedError(
          'DefaultFirebaseOptions has not been configured for macos.',
        );
      case TargetPlatform.windows:
        throw UnsupportedError(
          'DefaultFirebaseOptions has not been configured for windows.',
        );
      case TargetPlatform.linux:
        throw UnsupportedError(
          'DefaultFirebaseOptions has not been configured for linux.',
        );
      default:
        throw UnsupportedError(
          'DefaultFirebaseOptions are not supported for this platform.',
        );
    }
  }

  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyAxiOatusxGzcUDSCG9vrJk_dzaVZf3aJo',
    appId: '1:713973943888:web:REPLACE_WITH_YOUR_WEB_APP_ID',
    messagingSenderId: '713973943888',
    projectId: 'paychat-96225',
    authDomain: 'paychat-96225.firebaseapp.com',
    storageBucket: 'paychat-96225.firebasestorage.app',
  );

  static const FirebaseOptions android = FirebaseOptions(
    apiKey: 'AIzaSyAxiOatusxGzcUDSCG9vrJk_dzaVZf3aJo',
    appId: '1:713973943888:android:7b82054b5cdc4b29624b55',
    messagingSenderId: '713973943888',
    projectId: 'paychat-96225',
    storageBucket: 'paychat-96225.firebasestorage.app',
  );

  static const FirebaseOptions ios = FirebaseOptions(
    apiKey: 'AIzaSyDummy',
    appId: '1:123456789:ios:abc123def456',
    messagingSenderId: '123456789',
    projectId: 'your-project-id',
    storageBucket: 'your-project-id.appspot.com',
    iosBundleId: 'com.paychat.paychat',
  );
}
