import 'package:flutter/material.dart';
import 'package:gap/gap.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/utils/phone_formatter.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/background_painters.dart';
import '../../widgets/avatars.dart';
import '../../core/providers/providers.dart';

import '../../core/models/user.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  final User? otherUser;
  const ProfileScreen({super.key, this.otherUser});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  late TextEditingController _nameController;
  late TextEditingController _phoneController;
  bool _isEditing = false;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _phoneController = TextEditingController();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  void _initializeControllers(User user) {
    if (_nameController.text.isEmpty) _nameController.text = user.name;
    if (_phoneController.text.isEmpty) _phoneController.text = user.phoneNumber;
  }

  Future<void> _saveChanges() async {
    final user = ref.read(currentUserProvider).value;
    if (user == null) return;

    setState(() => _isLoading = true);

    try {
      // Bangladeshi Phone Number Validation
      final phone = _phoneController.text.trim();

      if (!PhoneFormatter.isValid(phone)) {
        ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                      content: Text(
                          'Please enter a valid Bangladeshi number (+8801xxxxxxxxx)')),
        );
        setState(() => _isLoading = false);
        return;
      }

      final authService = ref.read(authServiceProvider);

      // Update Name
      if (_nameController.text.trim() != user.name) {
        await authService.updateUserProfile(
            displayName: _nameController.text.trim());
      }

      // Update Phone
      if (phone != user.phoneNumber) {
        await authService.updatePhoneNumber(PhoneFormatter.format(phone));
      }

      // Refresh user data
      ref.refresh(currentUserProvider);

      setState(() {
        _isEditing = false;
        _isLoading = false;
      });

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Profile updated successfully!')),
        );
      }
    } catch (e) {
      if (mounted) {
        final errorMessage = e.toString();
        String displayError;
        if (errorMessage.contains('already registered')) {
          displayError = 'This phone number is already registered to another account';
        } else {
          displayError = 'Error updating profile: $e';
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(displayError)),
        );
      }
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    // If otherUser is provided, use it. Otherwise watch current user.
    final user = widget.otherUser ?? ref.watch(currentUserProvider).value;
    final isMe = widget.otherUser == null;

    if (user == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    // Initialize controllers with current data if not editing yet
    if (!_isEditing) {
      _nameController.text = user.name;
      _phoneController.text = user.phoneNumber;
    }

    return Scaffold(
      backgroundColor:
          const Color(0xFFF0F4F8), // Matches the light grey background
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: Padding(
          padding: const EdgeInsets.only(left: 16, top: 8, bottom: 8),
          child: CircleAvatar(
            backgroundColor: Colors.white.withOpacity(0.2),
            child: IconButton(
              icon:
                  Icon(PhosphorIcons.arrowLeft(), color: AppColors.textPrimary),
              onPressed: () => Navigator.pop(context),
            ),
          ),
        ),
        centerTitle: true,
        title: Text(
          "Profile",
          style: GoogleFonts.inter(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.bold,
            fontSize: 18,
          ),
        ),
        actions: [
          if (isMe)
            Padding(
              padding: const EdgeInsets.only(right: 16, top: 8, bottom: 8),
              child: CircleAvatar(
                backgroundColor: _isEditing
                    ? AppColors.primary
                    : Colors.white.withOpacity(0.2),
                child: IconButton(
                  icon: Icon(
                      _isEditing
                          ? PhosphorIcons.x()
                          : PhosphorIcons.pencilSimple(),
                      color: _isEditing ? Colors.white : AppColors.textPrimary),
                  onPressed: () {
                    setState(() {
                      _isEditing = !_isEditing;
                      // Reset fields if cancelling
                      if (!_isEditing) {
                        _nameController.text = user.name;
                        _phoneController.text = user.phoneNumber;
                      }
                    });
                  },
                ),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          // Header with Gradient and Profile Image
          SizedBox(
            height: 280,
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                // Gradient Background
                Container(
                  height: 220,
                  width: double.infinity,
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Color(0xFFE0E7FF), // Very light blue top
                        Color(0xFF2E5C9E), // Blue bottom
                      ],
                      stops: [0.0, 1.0],
                    ),
                  ),
                  child: Stack(
                    children: [
                      Positioned.fill(
                        child: CustomPaint(painter: SmoothWavesPainter()),
                      ),
                      Positioned.fill(
                        child: CustomPaint(painter: StarSparklesPainter()),
                      ),
                    ],
                  ),
                ),

                // Profile Image
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Center(
                    child: Stack(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(4),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.1),
                                blurRadius: 10,
                                offset: const Offset(0, 4),
                              ),
                            ],
                          ),
                          child: CircleAvatar(
                            radius: 64,
                            backgroundColor: Colors.white,
                            child: UserAvatar(
                              user: user,
                              radius: 60,
                            ),
                          ),
                        ),

                        // Camera Icon (Only show if editing? Or simply keep it)
                        if (_isEditing)
                          Positioned(
                            bottom: 0,
                            right: 0,
                            child: Container(
                              height: 36,
                              width: 36,
                              decoration: BoxDecoration(
                                color: const Color(0xFF1ECAD3), // Teal accent
                                shape: BoxShape.circle,
                                border:
                                    Border.all(color: Colors.white, width: 2),
                              ),
                              child: Icon(
                                PhosphorIcons.camera(PhosphorIconsStyle.fill),
                                color: Colors.white,
                                size: 18,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildLabel('Full Name'),
                  _buildInputContainer(
                    child: TextFormField(
                      controller: _nameController,
                      readOnly: !_isEditing,
                      style: GoogleFonts.inter(
                          fontWeight: FontWeight.w600,
                          color: AppColors.textPrimary),
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: EdgeInsets.zero,
                      ),
                    ),
                  ),
                  const Gap(16),
                  _buildLabel('Phone Number'),
                  _buildInputContainer(
                    child: Row(
                      children: [
                        // Flag placeholder
                        Image.network(
                          'https://flagcdn.com/w40/bd.png', // Corrected to BD flag
                          width: 24,
                          errorBuilder: (c, e, s) => const Icon(Icons.flag),
                        ),
                        const Gap(12),
                        Expanded(
                          child: TextFormField(
                            controller: _phoneController,
                            readOnly: !_isEditing,
                            keyboardType: TextInputType.phone,
                            inputFormatters: [
                              FilteringTextInputFormatter.allow(RegExp(r'[0-9+]')),
                              PhoneFormatter(),
                            ],
                            style: GoogleFonts.inter(
                                fontWeight: FontWeight.w600,
                                color: AppColors.textPrimary),
                            decoration: const InputDecoration(
                                border: InputBorder.none,
                                isDense: true,
                                contentPadding: EdgeInsets.zero,
                                hintText: "+8801xxxxxxxxx"),
                          ),
                        ),
                        if (_isEditing)
                          Icon(PhosphorIcons.pencilSimple(),
                              size: 16, color: AppColors.textSecondary),
                      ],
                    ),
                    onTap: () {},
                  ),
                  const Gap(16),
                  _buildLabel('Email (optional)'),
                  _buildInputContainer(
                    child: TextFormField(
                      initialValue: user.email,
                      readOnly: true, // Email is managed by Google Auth usually
                      style: GoogleFonts.inter(color: AppColors.textPrimary),
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        isDense: true,
                        contentPadding: EdgeInsets.zero,
                      ),
                    ),
                  ),
                  const Gap(16),
                  _buildLabel('Default Currency'),
                  _buildInputContainer(
                    child: Row(
                      children: [
                        Text(
                          "BDT",
                          style: GoogleFonts.inter(
                              fontWeight: FontWeight.bold,
                              color: AppColors.textPrimary),
                        ),
                        const Spacer(),
                        Text(
                          "Bangladeshi Taka",
                          style:
                              GoogleFonts.inter(color: AppColors.textSecondary),
                        ),
                        const Gap(8),
                        Icon(PhosphorIcons.caretRight(),
                            size: 16, color: AppColors.textSecondary),
                      ],
                    ),
                    onTap: () {},
                  ),
                  const Gap(16),
                  _buildLabel('Default Split Method'),
                  _buildInputContainer(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          "Equal",
                          style: GoogleFonts.inter(
                              fontWeight: FontWeight.w500,
                              color: AppColors.textPrimary),
                        ),
                        Icon(PhosphorIcons.caretRight(),
                            size: 16, color: AppColors.textSecondary),
                      ],
                    ),
                    onTap: () {},
                  ),
                  const Gap(32),
                  if (_isEditing)
                    Container(
                      width: double.infinity,
                      height: 56,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [
                            Color(0xFF3E5C8A), // Indigo
                            Color(0xFF1ECAD3), // Teal
                          ],
                        ),
                        borderRadius: BorderRadius.circular(30),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF3E5C8A).withOpacity(0.3),
                            blurRadius: 10,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: Material(
                        color: Colors.transparent,
                        child: InkWell(
                          onTap: _isLoading ? null : _saveChanges,
                          borderRadius: BorderRadius.circular(30),
                          child: Center(
                            child: _isLoading
                                ? const CircularProgressIndicator(
                                    color: Colors.white)
                                : Text(
                                    "Save Changes",
                                    style: GoogleFonts.inter(
                                      color: Colors.white,
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                          ),
                        ),
                      ),
                    ),
                  const Gap(16),

                  // Logout Button
                  if (isMe)
                    Center(
                      child: TextButton.icon(
                        onPressed: () async {
                          // Confirm dialog
                          final confirm = await showDialog<bool>(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: const Text('Sign Out'),
                              content: const Text(
                                  'Are you sure you want to sign out?'),
                              actions: [
                                TextButton(
                                  onPressed: () =>
                                      Navigator.pop(context, false),
                                  child: const Text('Cancel'),
                                ),
                                TextButton(
                                  onPressed: () => Navigator.pop(context, true),
                                  child: const Text('Sign Out',
                                      style: TextStyle(color: Colors.red)),
                                ),
                              ],
                            ),
                          );

                          if (confirm == true) {
                            await ref.read(authServiceProvider).signOut();
                            if (mounted) {
                              Navigator.pop(context); // Close profile screen
                            }
                          }
                        },
                        icon: Icon(PhosphorIcons.signOut(), color: Colors.red),
                        label: const Text(
                          "Sign Out",
                          style: TextStyle(
                            color: Colors.red,
                            fontWeight: FontWeight.w600,
                            fontSize: 16,
                          ),
                        ),
                        style: TextButton.styleFrom(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 24, vertical: 12),
                        ),
                      ),
                    ),

                  const Gap(32),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLabel(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 4),
      child: Text(
        text,
        style: GoogleFonts.inter(
          color: AppColors.textSecondary,
          fontSize: 14,
        ),
      ),
    );
  }

  Widget _buildInputContainer({required Widget child, VoidCallback? onTap}) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border:
              Border.all(color: Colors.white), // Invisible border for sizing
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.02),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: child,
      ),
    );
  }
}
