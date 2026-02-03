import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:gap/gap.dart';
import '../../core/providers/providers.dart';
import '../../core/utils/phone_formatter.dart';
import '../../widgets/buttons.dart';
import '../../widgets/avatars.dart';
import '../../core/theme/app_colors.dart';
import '../home/home_screen.dart';
import '../auth/biometric_gate.dart';

class PhoneNumberScreen extends ConsumerStatefulWidget {
  const PhoneNumberScreen({super.key});

  @override
  ConsumerState<PhoneNumberScreen> createState() => _PhoneNumberScreenState();
}

class _PhoneNumberScreenState extends ConsumerState<PhoneNumberScreen> {
  final _phoneController = TextEditingController();
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    // Pre-fill phone number if available
    final user = ref.read(currentUserProvider).value;
    if (user != null && user.phoneNumber.isNotEmpty) {
      _phoneController.text = user.phoneNumber;
    }
  }

  @override
  void dispose() {
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _savePhoneNumber() async {
    final phone = _phoneController.text.trim();
    if (phone.isEmpty) {
      setState(() => _error = 'Please enter your phone number');
      return;
    }

    // Use PhoneFormatter for validation
    if (!PhoneFormatter.isValid(phone)) {
      setState(() =>
          _error = 'Please enter a valid Bangladeshi number (+8801xxxxxxxxx)');
      return;
    }

    // Ensure strictly formatted
    final formattedPhone = PhoneFormatter.format(phone);

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final authService = ref.read(authServiceProvider);
      await authService.updatePhoneNumber(formattedPhone);

      // Invalidate currentUserProvider to update state
      ref.invalidate(currentUserProvider);

      if (!mounted) return;

      // Explicitly navigate to HomeScreen instead of relying on stream
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => const BiometricGate(child: HomeScreen()),
        ),
        (route) => false,
      );
    } catch (e) {
      final errorMessage = e.toString();
      if (errorMessage.contains('already registered')) {
        setState(() => _error = 'This phone number is already registered to another account');
      } else {
        setState(() => _error = 'Failed to save: $e');
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
      backgroundColor: Colors.white,
      appBar: AppBar(
        title: const Text("Setup Profile"),
        centerTitle: true,
        backgroundColor: Colors.white,
        elevation: 0,
      ),
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Gap(20),
            // Profile Header
            if (ref.watch(currentUserProvider).value != null) ...[
              Center(
                child: UserAvatar(
                  user: ref.watch(currentUserProvider).value!,
                  radius: 40,
                ),
              ),
              const Gap(16),
              Text(
                "Hi, ${ref.watch(currentUserProvider).value!.name.split(' ').first}!",
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
                textAlign: TextAlign.center,
              ),
              const Gap(8),
            ],

            const Text(
              "One Last Step",
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: AppColors.textPrimary,
              ),
              textAlign: TextAlign.center,
            ),
            const Gap(8),
            const Text(
              "We need your phone number to identify you uniquely in the PayChat network.",
              style: TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
              textAlign: TextAlign.center,
            ),
            const Gap(40),

            // Input
            TextField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9+]')),
                PhoneFormatter(),
              ],
              decoration: InputDecoration(
                labelText: "Phone Number",
                hintText: "+8801xxxxxxxxx",
                errorText: _error,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                filled: true,
                fillColor: AppColors.backgroundLight,
              ),
            ),

            const Spacer(),

            // Save Button
            PrimaryButton(
              label: "Save & Continue",
              onTap: _savePhoneNumber,
              isLoading: _isLoading,
            ),
            const Gap(40),
          ],
        ),
      ),
    );
  }
}
