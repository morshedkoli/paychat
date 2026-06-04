import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import '../../core/theme/app_theme.dart';
import 'package:flutter/services.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/auth_providers.dart';

Widget _buildAvatar(AppUser? user) {
  final photoUrl = user?.photoUrl ?? '';
  final initial = (user?.displayName.isNotEmpty == true)
      ? user!.displayName[0].toUpperCase()
      : 'U';
  return Container(
    width: 80,
    height: 80,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      color: AppColors.bgSurface,
      border: Border.all(
        color: AppColors.primaryGreen.withValues(alpha: 0.5),
        width: 2.5,
      ),
      image: photoUrl.isNotEmpty
          ? DecorationImage(image: CachedNetworkImageProvider(photoUrl), fit: BoxFit.cover)
          : null,
    ),
    alignment: Alignment.center,
    child: photoUrl.isEmpty
        ? Text(
            initial,
            style: GoogleFonts.dmSans(
              color: AppColors.primaryGreen,
              fontSize: 28,
              fontWeight: FontWeight.w700,
            ),
          )
        : null,
  );
}

class QrDisplayScreen extends ConsumerWidget {
  const QrDisplayScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(themeControllerProvider);
    final sessionState = ref.watch(sessionStateProvider);
    final user = sessionState.user;

    final String qrData = 'paychat:user:${user?.uid ?? "demo_id_123"}';

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppTheme.secondaryAppBar('My QR Code'),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Container(
            padding: const EdgeInsets.all(32),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: AppColors.borderGlow, width: 1.5),
              boxShadow: AppColors.cardGlow,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Avatar
                _buildAvatar(user),
                const SizedBox(height: 16),
                Text(
                  user?.displayName ?? 'PayChat User',
                  style: GoogleFonts.dmSans(
                    color: AppColors.textPrimary,
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 0.8,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Scan this code to start a chat',
                  style: GoogleFonts.dmSans(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                  ),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                // QR code
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primaryGreen.withValues(alpha: 0.15),
                        blurRadius: 16,
                      ),
                    ],
                  ),
                  child: QrImageView(
                    data: qrData,
                    version: QrVersions.auto,
                    size: 200.0,
                    backgroundColor: Colors.white,
                    eyeStyle: const QrEyeStyle(
                      eyeShape: QrEyeShape.square,
                      color: Colors.black87,
                    ),
                    dataModuleStyle: const QrDataModuleStyle(
                      dataModuleShape: QrDataModuleShape.square,
                      color: Colors.black87,
                    ),
                  ),
                ),
                const SizedBox(height: 20),
                // Copyable User ID for manual entry
                GestureDetector(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: qrData));
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('QR ID copied to clipboard',
                            style: GoogleFonts.dmSans(
                                color: AppColors.textPrimary)),
                        backgroundColor: AppColors.bgSurface,
                        behavior: SnackBarBehavior.floating,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        duration: const Duration(seconds: 2),
                      ),
                    );
                  },
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 14, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColors.bgSurface,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.copy_rounded,
                            size: 14, color: AppColors.textSecondary),
                        const SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            user?.uid ?? '',
                            style: GoogleFonts.dmSans(
                              color: AppColors.textSecondary,
                              fontSize: 11,
                              fontWeight: FontWeight.w500,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
