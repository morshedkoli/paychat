import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_app_check/firebase_app_check.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/core/config.dart';
import 'package:paychat/firebase_options.dart';
import 'package:paychat/screens/app.dart';
import 'package:paychat/services/notification_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  // Initialize push notifications (registers background handler first).
  if (!kIsWeb) {
    await NotificationService.instance.initialize();
  }

  // App Check: only active in production builds.
  // Debug builds skip it so Firebase Phone Auth can use its own reCAPTCHA
  // fallback without needing a debug token registered in Firebase Console.
  if (!kDebugMode) {
    if (kIsWeb) {
      await FirebaseAppCheck.instance.activate(
        webProvider: ReCaptchaV3Provider(AppConfig.recaptchaSiteKey),
      );
    } else {
      await FirebaseAppCheck.instance.activate(
        androidProvider: AndroidProvider.playIntegrity,
      );
      await FirebaseAppCheck.instance.setTokenAutoRefreshEnabled(true);
    }
  }

  runApp(
    const ProviderScope(
      child: PayChatApp(),
    ),
  );
}
