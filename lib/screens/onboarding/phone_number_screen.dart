import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gap/gap.dart';
import '../../core/providers/providers.dart';
import '../../widgets/buttons.dart';
import '../../widgets/avatars.dart';
import '../../core/theme/app_colors.dart';

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

    // Bangladeshi Phone Number Validation
    // Matches: +8801xxxxxxxxx or 01xxxxxxxxx
    final bangladeshiNumberRegex = RegExp(r'^(?:\+88)?(01[3-9]\d{8})$');

    if (!bangladeshiNumberRegex.hasMatch(phone)) {
      setState(() =>
          _error = 'Please enter a valid Bangladeshi number (01xxxxxxxxx)');
      return;
    }

    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final authService = ref.read(authServiceProvider);
      await authService.updatePhoneNumber(phone);

      // Invalidate currentUserProvider to trigger re-fetch and navigation check
      ref.invalidate(currentUserProvider);
    } catch (e) {
      setState(() => _error = 'Failed to save: $e');
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
              decoration: InputDecoration(
                labelText: "Phone Number",
                hintText: "+1234567890",
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
            _isLoading
                ? const Center(child: CircularProgressIndicator())
                : PrimaryButton(
                    label: "Save & Continue",
                    onTap: _savePhoneNumber,
                  ),
            const Gap(40),
          ],
        ),
      ),
    );
  }
}
