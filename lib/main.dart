import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/firebase/firebase_config.dart';
import 'core/theme/app_theme.dart';
import 'screens/onboarding/splash_screen.dart';
import 'screens/onboarding/welcome_screen.dart';
import 'screens/onboarding/phone_number_screen.dart';
import 'screens/home/home_screen.dart';
import 'screens/auth/biometric_gate.dart';
import 'core/providers/providers.dart';

// Global provider for onboarding completion status
final onboardingCompleteProvider = FutureProvider<bool>((ref) async {
  final prefs = await SharedPreferences.getInstance();
  return prefs.getBool('onboarding_complete') ?? false;
});

void main() async {
  // Ensure Flutter is initialized
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Firebase
  try {
    await FirebaseConfig.initialize();
  } catch (e) {
    print('Firebase initialization error: $e');
    // Continue anyway to allow app to run in development without Firebase config
  }

  // Run app with Riverpod
  runApp(const ProviderScope(child: PayChatApp()));
}

class PayChatApp extends ConsumerWidget {
  const PayChatApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Watch onboarding completion status
    final onboardingComplete = ref.watch(onboardingCompleteProvider);
    
    // Watch currentUserProvider for auth state
    final userAsync = ref.watch(currentUserProvider);

    return MaterialApp(
      title: 'PayChat',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: onboardingComplete.when(
        data: (isComplete) {
          if (!isComplete) {
            // First time user - show splash with permissions
            return const SplashScreen();
          }
          
          // Onboarding complete - check auth state
          return userAsync.when(
            data: (user) {
              if (user == null) {
                // Not logged in
                return const WelcomeScreen();
              } else if (user.phoneNumber.isEmpty) {
                // Logged in but no phone number -> Force setup
                return const PhoneNumberScreen();
              } else {
                // Logged in and setup complete
                return const BiometricGate(
                  child: HomeScreen(),
                );
              }
            },
            loading: () => const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            ),
            error: (e, stack) => Scaffold(
              body: Center(child: Text('Error: $e')),
            ),
          );
        },
        loading: () => const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
        error: (e, stack) => Scaffold(
          body: Center(child: Text('Error: $e')),
        ),
      ),
    );
  }
}
