import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/providers/providers.dart';
import '../../core/theme/app_colors.dart';

/// Wraps the home screen and requires biometric auth if enabled
class BiometricGate extends ConsumerStatefulWidget {
  final Widget child;

  const BiometricGate({
    super.key,
    required this.child,
  });

  @override
  ConsumerState<BiometricGate> createState() => _BiometricGateState();
}

class _BiometricGateState extends ConsumerState<BiometricGate>
    with WidgetsBindingObserver {
  bool _isLocked = true; // Default to locked until we check
  bool _checking = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkBiometric();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Re-lock if app goes to background (paused/inactive)
    // This is optional but good for security apps
    if (state == AppLifecycleState.paused) {
      // We could set _isLocked = true here if we want strict security
      // For now, let's just keep it simple: lock on startup only,
      // or if user explicitly requested.
      // Actually, for a financial app, locking on resume is good practice.
      // Let's implement lock on resume if enabled.
      _checkIfShouldLock();
    }
  }

  Future<void> _checkIfShouldLock() async {
    final biometricService = ref.read(biometricServiceProvider);
    final enabled = await biometricService.isBiometricEnabled();
    if (enabled) {
      if (mounted) setState(() => _isLocked = true);
    }
  }

  Future<void> _checkBiometric() async {
    final biometricService = ref.read(biometricServiceProvider);
    final enabled = await biometricService.isBiometricEnabled();

    if (!enabled) {
      // Biometric not enabled, proceed
      if (mounted) {
        setState(() {
          _isLocked = false;
          _checking = false;
        });
      }
      return;
    }

    // Enabled, try to authenticate
    await _authenticate();
  }

  Future<void> _authenticate() async {
    final biometricService = ref.read(biometricServiceProvider);
    final authenticated = await biometricService.authenticate(
      reason: 'Unlock PayChat',
    );

    if (mounted) {
      setState(() {
        _isLocked = !authenticated;
        _checking = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_checking) {
      // Show loading or splash while checking preference
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (!_isLocked) {
      return widget.child;
    }

    // Locked Screen
    return Scaffold(
      backgroundColor: AppColors.backgroundLight,
      body: SafeArea(
        child: SizedBox(
          width: double.infinity,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Spacer(),
              Icon(
                PhosphorIcons.lockKey(PhosphorIconsStyle.fill),
                size: 80,
                color: AppColors.primary,
              ).animate().scale(duration: 400.ms, curve: Curves.elasticOut),
              const Gap(24),
              const Text(
                'PayChat Locked',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ),
              const Gap(8),
              const Text(
                'Unlock to access your ledger',
                style: TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary,
                ),
              ),
              const Spacer(),

              // Unlock Button (in case biometric prompt was dismissed)
              TextButton.icon(
                onPressed: _authenticate,
                icon: Icon(PhosphorIcons.fingerprint(), size: 28),
                label: const Text(
                  'Unlock',
                  style: TextStyle(fontSize: 18),
                ),
                style: TextButton.styleFrom(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                ),
              ),
              const Gap(48),
            ],
          ),
        ),
      ),
    );
  }
}
