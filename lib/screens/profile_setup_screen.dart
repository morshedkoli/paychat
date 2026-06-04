import 'dart:convert';
import 'dart:typed_data';

import 'package:mime/mime.dart';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:paychat/core/config.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/providers/theme_provider.dart';

class ProfileSetupScreen extends ConsumerStatefulWidget {
  final AppUser user;
  const ProfileSetupScreen({super.key, required this.user});

  @override
  ConsumerState<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends ConsumerState<ProfileSetupScreen> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _emailCtrl;
  late final TextEditingController _addressCtrl;

  Uint8List? _pickedBytes;
  bool _removePhoto = false;  // user explicitly removed their existing photo
  bool _isLoading = false;
  String _statusText = '';

  @override
  void initState() {
    super.initState();
    _nameCtrl    = TextEditingController(text: widget.user.fullName);
    _emailCtrl   = TextEditingController(text: widget.user.email);
    _addressCtrl = TextEditingController(text: widget.user.address);
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    _addressCtrl.dispose();
    super.dispose();
  }

  // ── Image picking ─────────────────────────────────────────────────────────────

  Future<void> _pickFrom(ImageSource source) async {
    final picker = ImagePicker();
    final file = await picker.pickImage(
      source: source,
      imageQuality: 80,
      maxWidth: 800,
      maxHeight: 800,
    );
    if (file == null) return;
    final bytes = await file.readAsBytes();
    setState(() {
      _pickedBytes = bytes;
      _removePhoto = false;
    });
  }

  void _showImageSourceSheet() {
    final hasPhoto = _pickedBytes != null ||
        (widget.user.photoUrl.isNotEmpty && !_removePhoto);

    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          border: Border.all(color: AppColors.borderSubtle),
        ),
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Handle
            Center(
              child: Container(
                width: 40, height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: AppColors.borderSubtle,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(
              'Profile Photo',
              style: GoogleFonts.dmSans(
                color: AppColors.textPrimary,
                fontSize: 16,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 16),
            _sheetOption(
              icon: Icons.photo_library_rounded,
              label: 'Choose from Gallery',
              onTap: () {
                Navigator.pop(context);
                _pickFrom(ImageSource.gallery);
              },
            ),
            const SizedBox(height: 8),
            _sheetOption(
              icon: Icons.camera_alt_rounded,
              label: 'Take a Photo',
              onTap: () {
                Navigator.pop(context);
                _pickFrom(ImageSource.camera);
              },
            ),
            if (hasPhoto) ...[
              const SizedBox(height: 8),
              _sheetOption(
                icon: Icons.delete_outline_rounded,
                label: 'Remove Photo',
                color: AppColors.errorColor,
                onTap: () {
                  Navigator.pop(context);
                  setState(() {
                    _pickedBytes = null;
                    _removePhoto = true;
                  });
                },
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _sheetOption({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    Color? color,
  }) {
    final c = color ?? AppColors.textPrimary;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.borderSubtle),
        ),
        child: Row(
          children: [
            Icon(icon, color: c, size: 20),
            const SizedBox(width: 14),
            Text(label,
                style: GoogleFonts.dmSans(
                    color: c, fontSize: 15, fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }

  // ── ImgBB upload ──────────────────────────────────────────────────────────────

  static const int _kMaxImageBytes = 5 * 1024 * 1024; // 5 MB

  Future<String?> _uploadToImgBB(Uint8List bytes) async {
    if (bytes.length > _kMaxImageBytes) return null;

    final header = bytes.length >= 12 ? bytes.sublist(0, 12) : bytes;
    final mimeType = lookupMimeType('', headerBytes: header);
    if (mimeType == null || !mimeType.startsWith('image/')) return null;

    final response = await http.post(
      Uri.parse('https://api.imgbb.com/1/upload'),
      body: {
        'key': AppConfig.imgbbApiKey,
        'image': base64Encode(bytes),
      },
    );
    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      return data['data']['display_url'] as String?;
    }
    return null;
  }

  // ── Save ──────────────────────────────────────────────────────────────────────

  Future<void> _save() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _snack('Please enter your name');
      return;
    }

    setState(() { _isLoading = true; _statusText = 'Saving…'; });

    try {
      String? photoUrl;

      if (_pickedBytes != null) {
        setState(() => _statusText = 'Uploading photo…');
        photoUrl = await _uploadToImgBB(_pickedBytes!);
        if (photoUrl == null) {
          _snack('Photo upload failed — profile saved without photo.');
        }
      } else if (_removePhoto) {
        photoUrl = '';           // signal to clear
      } else {
        photoUrl = null;         // keep existing
      }

      setState(() => _statusText = 'Saving profile…');

      final userRepo = ref.read(userRepositoryProvider);
      await userRepo.updateProfile(
        uid: widget.user.uid,
        fullName: name,
        email: _emailCtrl.text.trim(),
        address: _addressCtrl.text.trim(),
        photoUrl: _removePhoto ? '' : photoUrl,
      );

      await ref.read(sessionStateProvider.notifier).ensureProfile(
        uid: widget.user.uid,
        phoneNumber: widget.user.phoneNumber,
        displayName: name,
        photoUrl: photoUrl ?? widget.user.photoUrl,
        email: _emailCtrl.text.trim(),
      );

      if (mounted) {
        _snack('Profile updated!', isSuccess: true);
        if (Navigator.canPop(context)) Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) _snack('Error: $e');
    } finally {
      if (mounted) setState(() { _isLoading = false; _statusText = ''; });
    }
  }

  void _snack(String msg, {bool isSuccess = false}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg,
          style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
      backgroundColor: isSuccess ? AppColors.primaryGreen : AppColors.bgCard,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ));
  }

  // ── Build ─────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);

    final hasPhoto = _pickedBytes != null ||
        (widget.user.photoUrl.isNotEmpty && !_removePhoto);

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        title: Text(
          'Edit Profile',
          style: GoogleFonts.dmSans(
            fontSize: 18,
            fontWeight: FontWeight.w700,
            color: AppColors.textPrimary,
          ),
        ),
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.textPrimary,
        elevation: 0,
        centerTitle: true,
        iconTheme: IconThemeData(color: AppColors.primaryGreen),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // ── Avatar with camera overlay ────────────────────────────────
              Center(
                child: GestureDetector(
                  onTap: _isLoading ? null : _showImageSourceSheet,
                  child: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      // Avatar circle
                      Container(
                        width: 100,
                        height: 100,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: AppColors.bgSurface,
                          border: Border.all(
                            color: AppColors.primaryGreen.withValues(alpha: 0.5),
                            width: 2.5,
                          ),
                          image: _pickedBytes != null
                              ? DecorationImage(
                                  image: MemoryImage(_pickedBytes!),
                                  fit: BoxFit.cover,
                                )
                              : (!_removePhoto && widget.user.photoUrl.isNotEmpty)
                                  ? DecorationImage(
                                      image: CachedNetworkImageProvider(widget.user.photoUrl),
                                      fit: BoxFit.cover,
                                    )
                                  : null,
                        ),
                        child: !hasPhoto
                            ? Icon(Icons.person_rounded,
                                size: 44, color: AppColors.textMuted)
                            : null,
                      ),
                      // Camera badge
                      Positioned(
                        right: -2,
                        bottom: -2,
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: AppColors.primaryGreen,
                            shape: BoxShape.circle,
                            border: Border.all(
                                color: AppColors.bgPrimary, width: 2),
                          ),
                          child: const Icon(Icons.camera_alt_rounded,
                              size: 16, color: Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Center(
                child: Text(
                  'Tap to change photo',
                  style: GoogleFonts.dmSans(
                    fontSize: 12,
                    color: AppColors.textMuted,
                  ),
                ),
              ),
              const SizedBox(height: 32),

              // ── Fields ────────────────────────────────────────────────────
              _field(_nameCtrl, 'Full name *', Icons.person_outline_rounded),
              const SizedBox(height: 14),
              _field(_emailCtrl, 'Email (optional)', Icons.email_outlined,
                  keyboard: TextInputType.emailAddress),
              const SizedBox(height: 14),
              _field(_addressCtrl, 'Address (optional)',
                  Icons.location_on_outlined),
              const SizedBox(height: 40),

              // ── Save button ────────────────────────────────────────────────
              SizedBox(
                height: 52,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _save,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primaryGreen,
                    foregroundColor: Colors.white,
                    disabledBackgroundColor:
                        AppColors.primaryGreen.withValues(alpha: 0.5),
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16)),
                  ),
                  child: _isLoading
                      ? Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              _statusText,
                              style: GoogleFonts.dmSans(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white),
                            ),
                          ],
                        )
                      : Text(
                          'Save Profile',
                          style: GoogleFonts.dmSans(
                              fontSize: 15, fontWeight: FontWeight.w700),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _field(
    TextEditingController ctrl,
    String hint,
    IconData icon, {
    TextInputType keyboard = TextInputType.text,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: TextField(
        controller: ctrl,
        keyboardType: keyboard,
        style: GoogleFonts.dmSans(
            color: AppColors.textPrimary, fontSize: 15),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle:
              GoogleFonts.dmSans(color: AppColors.textMuted, fontSize: 15),
          prefixIcon:
              Icon(icon, color: AppColors.textSecondary, size: 20),
          border: InputBorder.none,
          filled: false,
          contentPadding: const EdgeInsets.symmetric(vertical: 16),
        ),
      ),
    );
  }
}
