import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart'; // Added missing import
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/theme/app_colors.dart';
import '../../core/providers/providers.dart'; // Added missing providers import for authServiceProvider
import '../../widgets/buttons.dart';
import '../../widgets/background_painters.dart';

class WelcomeScreen extends ConsumerStatefulWidget {
  const WelcomeScreen({super.key});

  @override
  ConsumerState<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends ConsumerState<WelcomeScreen> {
  bool _isLoading = false;

  Future<void> _handleGoogleSignIn() async {
    setState(() => _isLoading = true);
    try {
      final authService = ref.read(authServiceProvider);
      // This will trigger the stream in main.dart to redirect to HomeScreen
      await authService.signInWithGoogle();

      // FORCE REFRESH: Ensure we fetch the newly created user doc
      // The stream might have triggered too early (before doc creation)
      ref.refresh(currentUserProvider);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Sign in failed: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // 1. Deep Blue Gradient Background
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0xFF2E5C9E), // Lighter Blue top
                  Color(0xFF1B3B6F), // Mid Blue
                  Color(0xFF0F2445), // Dark Blue bottom
                ],
                stops: [0.0, 0.5, 1.0],
              ),
            ),
          ),

          // 2. Subtle Waves (Background)
          Positioned.fill(
            child: CustomPaint(painter: SmoothWavesPainter()),
          ),

          // 3. Sparkles
          Positioned.fill(
            child: CustomPaint(painter: StarSparklesPainter()),
          ),

          // 4. Content
          SafeArea(
            child: Padding(
              padding:
                  const EdgeInsets.symmetric(horizontal: 32.0, vertical: 48),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Spacer(),

                  // Text
                  Text(
                    "Welcome to",
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          color: Colors.white.withOpacity(0.9),
                          fontWeight: FontWeight.w400,
                          fontSize: 24,
                        ),
                  )
                      .animate()
                      .fadeIn(duration: 600.ms)
                      .slideY(begin: -0.2, end: 0),

                  const Gap(8),

                  Text(
                    "PayChat",
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.displayMedium?.copyWith(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                          fontSize: 42,
                          letterSpacing: -0.5,
                        ),
                  ).animate().fadeIn(delay: 200.ms).slideY(begin: -0.2, end: 0),

                  const Gap(48),

                  // Center Illustration (Wallet)
                  SizedBox(
                    height: 160,
                    width: 160,
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        // Glow
                        Container(
                          width: 100,
                          height: 100,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: Colors.blue.withOpacity(0.4),
                                blurRadius: 40,
                                spreadRadius: 10,
                              ),
                            ],
                          ),
                        ),
                        // Wallet Icon
                        Icon(
                          PhosphorIcons.wallet(PhosphorIconsStyle.fill),
                          size: 120,
                          color: Colors.white,
                        )
                            .animate()
                            .scale(duration: 800.ms, curve: Curves.elasticOut),

                        // Decorative Card popping out
                        Positioned(
                          top: 30,
                          child: Container(
                            width: 60,
                            height: 40,
                            decoration: BoxDecoration(
                              color: AppColors.accent,
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ).animate().slideY(
                              begin: 0.5,
                              end: -0.2,
                              delay: 600.ms,
                              duration: 600.ms),
                        ),
                        // Re-draw wallet on top to cover part of card
                        Icon(
                          PhosphorIcons.wallet(PhosphorIconsStyle.fill),
                          size: 120,
                          color: const Color(0xFF4A7DFF),
                        ),
                        // Detail on Wallet
                        Icon(
                          PhosphorIcons.wallet(PhosphorIconsStyle.regular),
                          size: 120,
                          color: Colors.white.withOpacity(0.3),
                        ),
                      ],
                    ),
                  ),

                  const Spacer(),

                  Text(
                    "Sign in to continue",
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: Colors.white.withOpacity(0.8),
                          fontSize: 16,
                        ),
                  ).animate().fadeIn(delay: 800.ms),

                  const Gap(24),

                  // Button
                  _isLoading
                      ? const CircularProgressIndicator(color: Colors.white)
                      : GoogleButton(
                          onTap: _handleGoogleSignIn,
                        )
                          .animate()
                          .fadeIn(delay: 1000.ms)
                          .slideY(begin: 0.5, end: 0),

                  const Gap(24),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
