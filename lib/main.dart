import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/firebase/firebase_config.dart';
import 'core/theme/app_theme.dart';
import 'screens/onboarding/welcome_screen.dart';
import 'screens/onboarding/phone_number_screen.dart';
import 'screens/home/home_screen.dart'; // Ensure HomeScreen is imported
import 'core/providers/providers.dart'; // Ensure providers are imported

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
    // Watch currentUserProvider instead of just auth state
    // This ensures we have the full user profile (incl. phone number) before decision
    final userAsync = ref.watch(currentUserProvider);

    return MaterialApp(
      title: 'PayChat',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: userAsync.when(
        data: (user) {
          if (user == null) {
            // Not logged in (or profile not found yet)

            // Check if actually authenticated with Firebase but profile missing (Race condition)
            final firebaseUser = ref.read(firebaseAuthStateProvider).value;
            if (firebaseUser != null) {
              return const Scaffold(
                body: Center(
                  child: CircularProgressIndicator(),
                ),
              );
            }

            return const WelcomeScreen();
          } else if (user.phoneNumber.isEmpty) {
            // Logged in but no phone number -> Force setup
            return const PhoneNumberScreen();
          } else {
            // Logged in and setup complete
            return const HomeScreen();
          }
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
