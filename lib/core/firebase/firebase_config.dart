import 'package:firebase_core/firebase_core.dart';

import 'firebase_options.dart';

/// Firebase initialization service
class FirebaseConfig {
  static bool _initialized = false;

  /// Initialize Firebase
  /// Call this once in main() before runApp()
  static Future<void> initialize() async {
    if (_initialized) return;

    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );

    _initialized = true;
  }

  /// Check if Firebase is initialized
  static bool get isInitialized => _initialized;
}
