import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:google_fonts/google_fonts.dart';
import '../core/theme/app_colors.dart';

class SettingsOption {
  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  SettingsOption({
    required this.icon,
    required this.title,
    this.subtitle,
    required this.onTap,
  });
}

class SettingsDetailScreen extends ConsumerWidget {
  final String title;
  final List<SettingsOption> options;
  final Widget? headerWidget;

  const SettingsDetailScreen({
    super.key,
    required this.title,
    required this.options,
    this.headerWidget,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        title: Text(
          title,
          style: GoogleFonts.dmSans(
            fontWeight: FontWeight.w700,
            color: AppColors.accentCyan,
            fontSize: 20,
            letterSpacing: 0.8,
          ),
        ),
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.accentCyan,
        iconTheme: IconThemeData(color: AppColors.accentCyan),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          if (headerWidget != null) ...[
            headerWidget!,
            const SizedBox(height: 16),
          ],
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.borderSubtle),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: Column(
                children: options.asMap().entries.map((entry) {
                  final option = entry.value;
                  final isLast = entry.key == options.length - 1;
                  return Column(
                    children: [
                      _buildOptionTile(option),
                      if (!isLast)
                        Divider(
                          height: 1,
                          indent: 64,
                          color: AppColors.borderSubtle,
                        ),
                    ],
                  );
                }).toList(),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOptionTile(SettingsOption option) {
    return InkWell(
      onTap: option.onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: AppColors.accentCyan.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: Icon(option.icon, color: AppColors.accentCyan, size: 18),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    option.title,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (option.subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      option.subtitle!,
                      style: GoogleFonts.dmSans(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              color: AppColors.textMuted,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }
}
