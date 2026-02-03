import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:local_auth/local_auth.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/background_painters.dart';
import 'welcome_screen.dart';

/// Animated splash screen with permission request slides
class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen>
    with TickerProviderStateMixin {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  bool _showSplash = true;
  bool _biometricsAvailable = false;

  late AnimationController _logoController;
  late AnimationController _textController;

  @override
  void initState() {
    super.initState();
    _logoController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _textController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );

    _startSplashAnimation();
    _checkBiometricsAvailability();
  }

  @override
  void dispose() {
    _pageController.dispose();
    _logoController.dispose();
    _textController.dispose();
    super.dispose();
  }

  Future<void> _checkBiometricsAvailability() async {
    try {
      final localAuth = LocalAuthentication();
      final available = await localAuth.canCheckBiometrics;
      final isDeviceSupported = await localAuth.isDeviceSupported();
      setState(() => _biometricsAvailable = available && isDeviceSupported);
    } catch (e) {
      setState(() => _biometricsAvailable = false);
    }
  }

  Future<void> _startSplashAnimation() async {
    // Start logo animation
    await Future.delayed(const Duration(milliseconds: 300));
    _logoController.forward();

    // Start text animation after logo
    await Future.delayed(const Duration(milliseconds: 600));
    _textController.forward();

    // Wait for animations to complete
    await Future.delayed(const Duration(milliseconds: 1500));

    if (mounted) {
      setState(() {
        _showSplash = false;
      });
    }
  }

  Future<void> _requestContactsPermission() async {
    await Permission.contacts.request();
    _nextPage();
  }

  Future<void> _requestNotificationPermission() async {
    try {
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
    } catch (e) {
      // Silently handle if FCM not available
    }
    _nextPage();
  }

  Future<void> _enableBiometrics() async {
    try {
      final localAuth = LocalAuthentication();
      final authenticated = await localAuth.authenticate(
        localizedReason: 'Enable biometric login for PayChat',
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
        ),
      );
      if (authenticated) {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setBool('biometric_enabled', true);
      }
    } catch (e) {
      // Handle error silently
    }
    _completeOnboarding();
  }

  void _nextPage() {
    if (_currentPage < _getTotalPages() - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 400),
        curve: Curves.easeInOut,
      );
    }
  }

  void _skipPage() {
    _nextPage();
  }

  int _getTotalPages() {
    int pages = 2; // Contacts + Notifications
    if (_biometricsAvailable) pages++;
    return pages;
  }

  Future<void> _completeOnboarding() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('onboarding_complete', true);

    if (mounted) {
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const WelcomeScreen()),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // Set status bar style
    SystemChrome.setSystemUIOverlayStyle(
      const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
      ),
    );

    return Scaffold(
      body: Stack(
        children: [
          // Gradient Background
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0xFF2E5C9E),
                  Color(0xFF1B3B6F),
                  Color(0xFF0F2445),
                ],
                stops: [0.0, 0.5, 1.0],
              ),
            ),
          ),

          // Background Effects
          Positioned.fill(
            child: CustomPaint(painter: SmoothWavesPainter()),
          ),
          Positioned.fill(
            child: CustomPaint(painter: StarSparklesPainter()),
          ),

          // Content
          if (_showSplash)
            _buildSplashContent()
          else
            _buildPermissionSlides(),
        ],
      ),
    );
  }

  Widget _buildSplashContent() {
    return SafeArea(
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Logo
            AnimatedBuilder(
              animation: _logoController,
              builder: (context, child) {
                return Transform.scale(
                  scale: Curves.elasticOut.transform(_logoController.value),
                  child: Opacity(
                    opacity: _logoController.value.clamp(0.0, 1.0),
                    child: Container(
                      width: 120,
                      height: 120,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(30),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.blue.withOpacity(0.4),
                            blurRadius: 40,
                            spreadRadius: 10,
                          ),
                        ],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(30),
                        child: Image.asset(
                          'assets/icons/app_icon.png',
                          fit: BoxFit.cover,
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),

            const Gap(32),

            // App Name
            AnimatedBuilder(
              animation: _textController,
              builder: (context, child) {
                return Opacity(
                  opacity: _textController.value,
                  child: Transform.translate(
                    offset: Offset(0, 20 * (1 - _textController.value)),
                    child: const Text(
                      'PayChat',
                      style: TextStyle(
                        fontSize: 42,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ),
                );
              },
            ),

            const Gap(12),

            // Tagline
            AnimatedBuilder(
              animation: _textController,
              builder: (context, child) {
                return Opacity(
                  opacity: (_textController.value - 0.3).clamp(0.0, 1.0) / 0.7,
                  child: Text(
                    'Chat. Track. Settle.',
                    style: TextStyle(
                      fontSize: 18,
                      color: Colors.white.withOpacity(0.8),
                      letterSpacing: 1,
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionSlides() {
    final slides = <Widget>[
      // Contacts Permission
      _PermissionSlide(
        icon: PhosphorIcons.addressBook(PhosphorIconsStyle.fill),
        title: 'Access Contacts',
        description:
            'Find friends who use PayChat and easily start conversations with your contacts.',
        buttonText: 'Allow Access',
        onAllow: _requestContactsPermission,
        onSkip: _skipPage,
        showSkip: true,
      ),

      // Notifications Permission
      _PermissionSlide(
        icon: PhosphorIcons.bell(PhosphorIconsStyle.fill),
        title: 'Stay Updated',
        description:
            'Get notified about new messages, transaction requests, and payment updates.',
        buttonText: 'Enable Notifications',
        onAllow: _requestNotificationPermission,
        onSkip: _skipPage,
        showSkip: true,
      ),

      // Biometrics Permission (if available)
      if (_biometricsAvailable)
        _PermissionSlide(
          icon: PhosphorIcons.fingerprint(PhosphorIconsStyle.fill),
          title: 'Secure Access',
          description:
              'Use fingerprint or face recognition for quick and secure app access.',
          buttonText: 'Enable Biometrics',
          onAllow: _enableBiometrics,
          onSkip: _completeOnboarding,
          showSkip: true,
          isLast: true,
        ),
    ];

    // If no biometrics, make notifications the last slide
    if (!_biometricsAvailable && slides.length >= 2) {
      slides[1] = _PermissionSlide(
        icon: PhosphorIcons.bell(PhosphorIconsStyle.fill),
        title: 'Stay Updated',
        description:
            'Get notified about new messages, transaction requests, and payment updates.',
        buttonText: 'Enable Notifications',
        onAllow: () async {
          await _requestNotificationPermission();
          _completeOnboarding();
        },
        onSkip: _completeOnboarding,
        showSkip: true,
        isLast: true,
      );
    }

    return Column(
      children: [
        Expanded(
          child: PageView(
            controller: _pageController,
            physics: const NeverScrollableScrollPhysics(),
            onPageChanged: (index) => setState(() => _currentPage = index),
            children: slides,
          ),
        ),

        // Page Indicator
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 32),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                slides.length,
                (index) => AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: _currentPage == index ? 24 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: _currentPage == index
                        ? Colors.white
                        : Colors.white.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _PermissionSlide extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final String buttonText;
  final VoidCallback onAllow;
  final VoidCallback? onSkip;
  final bool showSkip;
  final bool isLast;

  const _PermissionSlide({
    required this.icon,
    required this.title,
    required this.description,
    required this.buttonText,
    required this.onAllow,
    this.onSkip,
    this.showSkip = false,
    this.isLast = false,
  });

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Spacer(flex: 2),

            // Icon with glow
            Container(
              width: 140,
              height: 140,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white.withOpacity(0.1),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.accent.withOpacity(0.3),
                    blurRadius: 40,
                    spreadRadius: 10,
                  ),
                ],
              ),
              child: Icon(
                icon,
                size: 70,
                color: Colors.white,
              ),
            )
                .animate()
                .fadeIn(duration: 400.ms)
                .scale(begin: const Offset(0.8, 0.8), duration: 500.ms),

            const Gap(48),

            // Title
            Text(
              title,
              style: const TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
              textAlign: TextAlign.center,
            ).animate().fadeIn(delay: 200.ms).slideY(begin: 0.2),

            const Gap(16),

            // Description
            Text(
              description,
              style: TextStyle(
                fontSize: 16,
                color: Colors.white.withOpacity(0.8),
                height: 1.5,
              ),
              textAlign: TextAlign.center,
            ).animate().fadeIn(delay: 300.ms).slideY(begin: 0.2),

            const Spacer(flex: 2),

            // Allow Button
            SizedBox(
              width: double.infinity,
              height: 56,
              child: ElevatedButton(
                onPressed: onAllow,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: AppColors.primary,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                child: Text(
                  isLast ? 'Get Started' : buttonText,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ).animate().fadeIn(delay: 400.ms).slideY(begin: 0.2),

            const Gap(16),

            // Skip Button
            if (showSkip)
              TextButton(
                onPressed: onSkip,
                child: Text(
                  isLast ? 'Maybe Later' : 'Skip for now',
                  style: TextStyle(
                    color: Colors.white.withOpacity(0.7),
                    fontSize: 14,
                  ),
                ),
              ).animate().fadeIn(delay: 500.ms),

            const Gap(60),
          ],
        ),
      ),
    );
  }
}
