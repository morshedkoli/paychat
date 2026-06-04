import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl_phone_field/intl_phone_field.dart';
import 'package:intl_phone_field/countries.dart' as intl_countries;
import 'package:firebase_auth/firebase_auth.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/auth_providers.dart';
import '../../providers/service_providers.dart';
import 'package:paychat/services/user_repository.dart';
import 'package:paychat/providers/theme_provider.dart';
class ChangeNumberScreen extends ConsumerStatefulWidget {
  const ChangeNumberScreen({super.key});

  @override
  ConsumerState<ChangeNumberScreen> createState() => _ChangeNumberScreenState();
}

class _ChangeNumberScreenState extends ConsumerState<ChangeNumberScreen> {
  String _phoneNumber = '';
  String? _verificationId;
  String? _errorMessage;
  bool _isLoading = false;
  bool _otpSent = false;

  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());

  @override
  void dispose() {
    for (var c in _otpControllers) {
      c.dispose();
    }
    for (var f in _otpFocusNodes) {
      f.dispose();
    }
    super.dispose();
  }

  Future<void> _sendCode() async {
    if (_phoneNumber.isEmpty) return;
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final authService = ref.read(authServiceProvider);

    try {
      await authService.verifyPhoneNumber(
        phoneNumber: _phoneNumber,
        onVerificationComplete: (PhoneAuthCredential credential) async {
          await _updatePhoneNumberWithCredential(credential);
        },
        onVerificationFailed: (FirebaseAuthException e) {
          if (mounted) {
            setState(() {
              _errorMessage = e.message ?? 'Verification failed';
              _isLoading = false;
            });
          }
        },
        onCodeSent: (String vId, int? resendToken) {
          if (mounted) {
            setState(() {
              _verificationId = vId;
              _otpSent = true;
              _isLoading = false;
            });
          }
        },
        onCodeAutoRetrievalTimeOut: (String vId) {
          _verificationId = vId;
        },
      );
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _verifyOtp() async {
    final otp = _otpControllers.map((c) => c.text).join();
    if (otp.length < 6 || _verificationId == null) return;

    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final authService = ref.read(authServiceProvider);
      final credential = authService.phoneAuthCredential(
        verificationId: _verificationId!,
        smsCode: otp,
      );
      await _updatePhoneNumberWithCredential(credential);
    } on FirebaseAuthException catch (e) {
      setState(() {
        _errorMessage = e.message ?? 'Invalid OTP code';
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'An error occurred updating the phone number';
        _isLoading = false;
      });
    }
  }

  Future<void> _updatePhoneNumberWithCredential(PhoneAuthCredential credential) async {
    try {
      final authService = ref.read(authServiceProvider);
      final userRepo = ref.read(userRepositoryProvider);
      final currentUser = ref.read(sessionStateProvider).user;

      await authService.updatePhoneNumber(credential);
      final newPhone = authService.currentUser?.phoneNumber ?? _phoneNumber;

      if (currentUser != null && newPhone.isNotEmpty) {
        await userRepo.ensureUserProfile(
          uid: currentUser.uid,
          phoneNumber: newPhone,
          displayName: currentUser.fullName,
          photoUrl: currentUser.photoUrl,
          email: currentUser.email,
        );
      }

      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Phone number updated successfully!',
              style: GoogleFonts.dmSans(color: AppColors.bgPrimary),
            ),
            backgroundColor: AppColors.accentGreen,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        );
        Navigator.pop(context);
      }
    } on FirebaseAuthException catch (e) {
      if (e.code == 'requires-recent-login') {
        setState(() {
          _errorMessage =
              'Security requirement: You must logout and re-authenticate to change your phone number.';
          _isLoading = false;
        });
      } else {
        setState(() {
          _errorMessage = e.message ?? 'Failed to update phone number';
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        title: Text(
          'Change Number',
          style: GoogleFonts.dmSans(
            color: AppColors.accentCyan,
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
          ),
        ),
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.accentCyan,
        iconTheme: IconThemeData(color: AppColors.accentCyan),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Enter your new phone number to receive a verification code.',
                textAlign: TextAlign.center,
                style: GoogleFonts.dmSans(
                  fontSize: 15,
                  color: AppColors.textSecondary,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 32),

              if (!_otpSent) ...[
                Container(
                  decoration: BoxDecoration(
                    color: AppColors.bgSurface,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                  child: IntlPhoneField(
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 17,
                    ),
                    dropdownTextStyle: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 15,
                    ),
                    decoration: InputDecoration(
                      hintText: 'New phone number',
                      hintStyle: GoogleFonts.dmSans(
                        color: AppColors.textMuted,
                      ),
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      filled: false,
                      contentPadding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    initialCountryCode: 'BD',
                    countries: intl_countries.countries
                        .where((c) => ['BD', 'US'].contains(c.code))
                        .toList(),
                    onChanged: (phone) {
                      _phoneNumber = UserRepository.normalizePhone(phone.completeNumber);
                    },
                  ),
                ),
                const SizedBox(height: 24),
                Container(
                  decoration: BoxDecoration(
                    gradient: _isLoading ? null : AppColors.gradientCyan,
                    color: _isLoading ? AppColors.bgSurface : null,
                    borderRadius: BorderRadius.circular(12),
                    boxShadow: _isLoading ? [] : AppColors.cyanGlow,
                  ),
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _sendCode,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          )
                        : Text(
                            'SEND PIN',
                            style: GoogleFonts.dmSans(
                              color: AppColors.bgPrimary,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.5,
                            ),
                          ),
                  ),
                ),
              ] else ...[
                Text(
                  'Code sent to $_phoneNumber',
                  textAlign: TextAlign.center,
                  style: GoogleFonts.dmSans(
                    fontWeight: FontWeight.w600,
                    color: AppColors.accentCyan,
                  ),
                ),
                const SizedBox(height: 24),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: List.generate(6, (i) => _buildOtpBox(i)),
                ),
                const SizedBox(height: 32),
                Container(
                  decoration: BoxDecoration(
                    gradient: _isLoading ? null : AppColors.gradientCyan,
                    color: _isLoading ? AppColors.bgSurface : null,
                    borderRadius: BorderRadius.circular(12),
                    boxShadow: _isLoading ? [] : AppColors.cyanGlow,
                  ),
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _verifyOtp,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              color: Colors.white,
                              strokeWidth: 2,
                            ),
                          )
                        : Text(
                            'VERIFY',
                            style: GoogleFonts.dmSans(
                              color: AppColors.bgPrimary,
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.5,
                            ),
                          ),
                  ),
                ),
              ],

              if (_errorMessage != null) ...[
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.accentAmber.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.accentAmber.withValues(alpha: 0.3)),
                  ),
                  child: Text(
                    _errorMessage!,
                    style: GoogleFonts.dmSans(
                      color: AppColors.accentAmber,
                      fontSize: 13,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildOtpBox(int index) {
    return SizedBox(
      width: 40,
      child: TextField(
        controller: _otpControllers[index],
        focusNode: _otpFocusNodes[index],
        textAlign: TextAlign.center,
        keyboardType: TextInputType.number,
        maxLength: 1,
        style: GoogleFonts.dmSans(
          fontSize: 24,
          color: AppColors.textPrimary,
          fontWeight: FontWeight.w700,
        ),
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        decoration: InputDecoration(
          counterText: '',
          enabledBorder: UnderlineInputBorder(
            borderSide: BorderSide(color: AppColors.borderSubtle, width: 2),
          ),
          focusedBorder: UnderlineInputBorder(
            borderSide: BorderSide(color: AppColors.accentCyan, width: 2),
          ),
        ),
        onChanged: (value) {
          if (value.isNotEmpty && index < 5) _otpFocusNodes[index + 1].requestFocus();
          if (value.isEmpty && index > 0) _otpFocusNodes[index - 1].requestFocus();
        },
      ),
    );
  }
}
