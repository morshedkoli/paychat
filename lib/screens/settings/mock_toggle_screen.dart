import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../core/theme/app_colors.dart';

class MockToggleScreen extends ConsumerStatefulWidget {
  final String title;
  final String description;
  final String toggleLabel;

  const MockToggleScreen({
    super.key,
    required this.title,
    required this.description,
    required this.toggleLabel,
  });

  @override
  ConsumerState<MockToggleScreen> createState() => _MockToggleScreenState();
}

class _MockToggleScreenState extends ConsumerState<MockToggleScreen> {
  bool _isEnabled = false;

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        title: Text(
          widget.title,
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
      body: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: AppColors.accentCyan.withValues(alpha: 0.08),
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.2)),
              ),
              child: Icon(Icons.security, size: 38, color: AppColors.accentCyan),
            ),
            const SizedBox(height: 24),
            Text(
              widget.description,
              style: GoogleFonts.dmSans(
                fontSize: 15,
                color: AppColors.textSecondary,
                height: 1.5,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            Container(
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: SwitchListTile(
                title: Text(
                  widget.toggleLabel,
                  style: GoogleFonts.dmSans(
                    color: AppColors.textPrimary,
                    fontSize: 15,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                activeThumbColor: AppColors.accentCyan,
                inactiveThumbColor: AppColors.textMuted,
                inactiveTrackColor: AppColors.bgSurface,
                value: _isEnabled,
                onChanged: (val) {
                  setState(() => _isEnabled = val);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        '${widget.title} ${_isEnabled ? "enabled" : "disabled"} successfully.',
                        style: GoogleFonts.dmSans(color: AppColors.textPrimary),
                      ),
                      backgroundColor: AppColors.bgCard,
                      behavior: SnackBarBehavior.floating,
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
