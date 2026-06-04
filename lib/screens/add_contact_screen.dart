import 'package:flutter/material.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl_phone_field/intl_phone_field.dart';
import 'package:intl_phone_field/countries.dart' as intl_countries;
import '../core/theme/app_colors.dart';
import '../providers/auth_providers.dart';
import '../providers/service_providers.dart';
import '../services/user_repository.dart';
import 'chat_screen.dart';

class AddContactScreen extends ConsumerStatefulWidget {
  const AddContactScreen({super.key});

  @override
  ConsumerState<AddContactScreen> createState() => _AddContactScreenState();
}

class _AddContactScreenState extends ConsumerState<AddContactScreen> {
  final _formKey = GlobalKey<FormState>();
  String _name = '';
  String _phoneNumber = '';
  bool _isLoading = false;

  void _startChat() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);

    try {
      final session = ref.read(sessionStateProvider);
      final currentUser = session.user;
      if (currentUser == null) {
        _showError('Not logged in.');
        return;
      }

      final userRepo = ref.read(userRepositoryProvider);
      final chatRepo = ref.read(chatRepositoryProvider);

      final contactUser = await userRepo.findUserByPhone(_phoneNumber);

      if (contactUser != null && contactUser.uid == currentUser.uid) {
        _showError('You cannot start a chat with yourself.');
        return;
      }

      final String threadId;
      final String partnerName;

      if (contactUser != null) {
        partnerName = _name.trim().isNotEmpty ? _name.trim() : contactUser.fullName;
        threadId = await chatRepo.getOrCreateThread(
          currentUserId: currentUser.uid,
          currentUserDisplayName: currentUser.fullName,
          currentUserPhotoUrl: currentUser.photoUrl,
          contactUserId: contactUser.uid,
          contactName: partnerName,
          contactPhotoUrl: contactUser.photoUrl,
        );
      } else {
        partnerName = _name.trim().isNotEmpty ? _name.trim() : _phoneNumber;
        threadId = await chatRepo.getOrCreateGuestThread(
          currentUserId: currentUser.uid,
          currentUserDisplayName: currentUser.fullName,
          currentUserPhotoUrl: currentUser.photoUrl,
          guestPhone: _phoneNumber,
          guestName: partnerName,
        );
      }

      if (!mounted) return;
      Navigator.pop(context);
      Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ChatScreen(
            threadId: threadId,
            partnerName: partnerName,
            partnerInitials: partnerName.substring(0, 1).toUpperCase(),
            avatarColor: AppColors.accentCyan,
            partnerUserId: contactUser?.uid ?? 'guest_${UserRepository.normalizePhone(_phoneNumber).replaceAll(RegExp(r'[^\d+]'), '')}',
          ),
        ),
      );
    } catch (e) {
      _showError('Something went wrong: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          message,
          style: GoogleFonts.dmSans(color: AppColors.textPrimary),
        ),
        backgroundColor: AppColors.bgCard,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        title: Text(
          'New Chat',
          style: GoogleFonts.dmSans(
            color: AppColors.accentCyan,
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
          ),
        ),
        leading: IconButton(
          icon: Icon(Icons.arrow_back_rounded, color: AppColors.accentCyan),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            // Hero header with dark gradient
            Container(
              width: double.infinity,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [AppColors.bgSecondary, AppColors.bgCard],
                ),
              ),
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
              child: Column(
                children: [
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      gradient: AppColors.gradientCyan,
                      shape: BoxShape.circle,
                      boxShadow: AppColors.cyanGlow,
                    ),
                    child: const Icon(Icons.person_add_alt_1_rounded, size: 38, color: Colors.white),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    'Start a conversation',
                    style: GoogleFonts.dmSans(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                      letterSpacing: 0.8,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Enter contact details to begin chatting',
                    style: GoogleFonts.dmSans(
                      fontSize: 13,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),

            // Form card
            Transform.translate(
              offset: const Offset(0, -16),
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 16),
                padding: const EdgeInsets.fromLTRB(22, 26, 22, 26),
                decoration: BoxDecoration(
                  color: AppColors.bgCard,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: AppColors.borderGlow),
                  boxShadow: AppColors.cardGlow,
                ),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Label
                      Text(
                        'CONTACT INFORMATION',
                        style: GoogleFonts.dmSans(
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          color: AppColors.accentCyan,
                          letterSpacing: 1.5,
                        ),
                      ),
                      const SizedBox(height: 18),

                      // Name input
                      TextFormField(
                        style: GoogleFonts.dmSans(
                          color: AppColors.textPrimary,
                          fontSize: 15,
                        ),
                        decoration: InputDecoration(
                          labelText: 'Name (optional)',
                          labelStyle: GoogleFonts.dmSans(
                            color: AppColors.textSecondary,
                            fontSize: 14,
                          ),
                          prefixIcon: Icon(
                            Icons.person_outline_rounded,
                            color: AppColors.textSecondary,
                            size: 20,
                          ),
                          filled: true,
                          fillColor: AppColors.bgSurface,
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: AppColors.borderSubtle),
                          ),
                          enabledBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: AppColors.borderSubtle),
                          ),
                          focusedBorder: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                            borderSide: BorderSide(color: AppColors.accentCyan, width: 2),
                          ),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                        ),
                        onChanged: (val) => _name = val,
                      ),
                      const SizedBox(height: 16),

                      // Phone input
                      Container(
                        decoration: BoxDecoration(
                          color: AppColors.bgSurface,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppColors.borderSubtle),
                        ),
                        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                        child: IntlPhoneField(
                          style: GoogleFonts.dmSans(
                            color: AppColors.textPrimary,
                            fontSize: 15,
                          ),
                          dropdownTextStyle: GoogleFonts.dmSans(
                            color: AppColors.textPrimary,
                            fontSize: 15,
                          ),
                          decoration: InputDecoration(
                            labelText: 'Phone Number',
                            labelStyle: GoogleFonts.dmSans(
                              color: AppColors.textSecondary,
                              fontSize: 14,
                            ),
                            border: InputBorder.none,
                            enabledBorder: InputBorder.none,
                            focusedBorder: InputBorder.none,
                            filled: false,
                            contentPadding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
                          ),
                          initialCountryCode: 'BD',
                          countries: intl_countries.countries
                              .where((c) => ['BD', 'US'].contains(c.code))
                              .toList(),
                          validator: (val) {
                            if (val == null || val.completeNumber.isEmpty) {
                              return 'Please enter a valid phone number';
                            }
                            return null;
                          },
                          onChanged: (phone) {
                            // Use shared normalizer so the number matches what's stored
                            _phoneNumber = UserRepository.normalizePhone(phone.completeNumber);
                          },
                        ),
                      ),

                      const SizedBox(height: 28),

                      // Start chat button
                      Container(
                        decoration: BoxDecoration(
                          gradient: _isLoading ? null : AppColors.gradientCyan,
                          color: _isLoading ? AppColors.bgSurface : null,
                          borderRadius: BorderRadius.circular(12),
                          boxShadow: _isLoading ? [] : AppColors.cyanGlow,
                        ),
                        child: ElevatedButton(
                          onPressed: _isLoading ? null : _startChat,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.transparent,
                            shadowColor: Colors.transparent,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          ),
                          child: _isLoading
                              ? const SizedBox(
                                  width: 22,
                                  height: 22,
                                  child: CircularProgressIndicator(
                                    color: Colors.white,
                                    strokeWidth: 2.5,
                                  ),
                                )
                              : Text(
                                  'Start Chat',
                                  style: GoogleFonts.dmSans(
                                    color: AppColors.bgPrimary,
                                    fontSize: 15,
                                    fontWeight: FontWeight.w700,
                                    letterSpacing: 0.3,
                                  ),
                                ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
