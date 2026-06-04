import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/theme/app_color_theme.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/chat_wallpaper.dart';
import '../../providers/chat_wallpaper_provider.dart';
import '../../providers/theme_provider.dart';

class ChatSettingsScreen extends ConsumerWidget {
  const ChatSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeState = ref.watch(themeControllerProvider);
    final wallpaperState = ref.watch(chatWallpaperProvider);
    final selectedTheme = themeState.selectedTheme;
    final selectedWallpaper = wallpaperState.selectedWallpaper;

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        title: Text(
          'Chats',
          style: GoogleFonts.dmSans(
            color: AppColors.textPrimary,
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.1,
          ),
        ),
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.textPrimary,
        iconTheme: IconThemeData(color: AppColors.textPrimary),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
        children: [
          // Header card
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppColors.borderSubtle),
              boxShadow: AppColors.cardGlow,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'CHAT APPEARANCE',
                  style: GoogleFonts.dmSans(
                    color: AppColors.textSecondary.withValues(alpha: 0.7),
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.5,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '${selectedTheme.name} palette',
                  style: GoogleFonts.dmSans(
                    color: AppColors.textPrimary,
                    fontSize: 26,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.2,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  selectedTheme.description,
                  style: GoogleFonts.dmSans(
                    color: AppColors.textSecondary,
                    fontSize: 13,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: _ThemePreviewBubble(
                        alignment: Alignment.centerLeft,
                        backgroundColor: AppColors.bgSurface,
                        textColor: AppColors.textPrimary,
                        label: 'Preview',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _ThemePreviewBubble(
                        alignment: Alignment.centerRight,
                        backgroundColor: AppColors.bgSurface,
                        textColor: AppColors.textSecondary,
                        label: 'Minimalist',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.borderSubtle),
            ),
            child: Column(
              children: [
                _SettingsRow(
                  icon: Icons.palette_outlined,
                  iconColor: AppColors.accentCyan,
                  title: 'Theme',
                  subtitle: '${selectedTheme.name} soft palette',
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ...selectedTheme.previewColors.map(
                        (color) => Container(
                          width: 10,
                          height: 10,
                          margin: const EdgeInsets.only(left: 6),
                          decoration: BoxDecoration(
                            color: color,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.chevron_right_rounded,
                        color: AppColors.textMuted,
                        size: 20,
                      ),
                    ],
                  ),
                  onTap: () => _showThemePicker(context, ref, selectedTheme),
                ),
                Divider(height: 1, indent: 64, color: AppColors.borderSubtle),
                _SettingsRow(
                  icon: Icons.dark_mode_outlined,
                  iconColor: AppColors.accentGreen,
                  title: 'Dark Mode',
                  subtitle: themeState.themeMode == ThemeMode.system
                      ? 'System Default'
                      : (themeState.themeMode == ThemeMode.dark ? 'Enabled' : 'Disabled'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        themeState.themeMode == ThemeMode.system
                            ? 'System'
                            : (themeState.themeMode == ThemeMode.dark ? 'Dark' : 'Light'),
                        style: GoogleFonts.dmSans(
                          color: AppColors.textSecondary,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.chevron_right_rounded,
                        color: AppColors.textMuted,
                        size: 20,
                      ),
                    ],
                  ),
                  onTap: () => _showThemeModePicker(context, ref, themeState.themeMode),
                ),
                Divider(height: 1, indent: 64, color: AppColors.borderSubtle),
                _SettingsRow(
                  icon: Icons.wallpaper_rounded,
                  iconColor: AppColors.accentViolet,
                  title: 'Wallpaper',
                  subtitle: '${selectedWallpaper.name} wallpaper',
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ...selectedWallpaper.previewColors.map(
                        (color) => Container(
                          width: 10,
                          height: 10,
                          margin: const EdgeInsets.only(left: 6),
                          decoration: BoxDecoration(
                            color: color,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Icon(
                        Icons.chevron_right_rounded,
                        color: AppColors.textMuted,
                        size: 20,
                      ),
                    ],
                  ),
                  onTap: () => _showWallpaperPicker(context, ref, selectedWallpaper),
                ),
                Divider(height: 1, indent: 64, color: AppColors.borderSubtle),
                _SettingsRow(
                  icon: Icons.history_rounded,
                  iconColor: AppColors.accentAmber,
                  title: 'Chat history',
                  subtitle: 'Export and manage conversations',
                  onTap: () => _showComingSoon(context, 'Chat history'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Text(
            'Choose one of the three soft minimalist themes and a wallpaper preset to update chat appearance across the app.',
            style: GoogleFonts.dmSans(
              color: AppColors.textSecondary,
              fontSize: 13,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _showWallpaperPicker(
    BuildContext context,
    WidgetRef ref,
    ChatWallpaper selectedWallpaper,
  ) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Container(
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: AppColors.borderGlow),
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 44,
                        height: 4,
                        decoration: BoxDecoration(
                          color: AppColors.borderSubtle,
                          borderRadius: BorderRadius.circular(999),
                        ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Choose wallpaper',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textPrimary,
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Pick a soft wallpaper style for all chat screens.',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 18),
                    ...ChatWallpaper.all.map(
                      (wallpaper) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _WallpaperChoiceCard(
                          wallpaper: wallpaper,
                          isSelected: wallpaper.id == selectedWallpaper.id,
                          onTap: () async {
                            await ref
                                .read(chatWallpaperProvider.notifier)
                                .setWallpaper(wallpaper);
                            if (sheetContext.mounted) {
                              Navigator.pop(sheetContext);
                            }
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _showThemePicker(
    BuildContext context,
    WidgetRef ref,
    AppColorTheme selectedTheme,
  ) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Container(
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: AppColors.borderGlow),
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 44,
                        height: 4,
                        decoration: BoxDecoration(
                          color: AppColors.borderSubtle,
                          borderRadius: BorderRadius.circular(999),
                        ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Choose theme',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textPrimary,
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Three soft minimalist color combinations for chats and accents.',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 18),
                    ...AppColorTheme.all.map(
                      (theme) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _ThemeChoiceCard(
                          theme: theme,
                          isSelected: theme.id == selectedTheme.id,
                          onTap: () async {
                            await ref
                                .read(themeControllerProvider.notifier)
                                .setTheme(theme);
                            if (sheetContext.mounted) {
                              Navigator.pop(sheetContext);
                            }
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _showThemeModePicker(
    BuildContext context,
    WidgetRef ref,
    ThemeMode selectedMode,
  ) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: Container(
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: AppColors.borderGlow),
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Center(
                      child: Container(
                        width: 44,
                        height: 4,
                        decoration: BoxDecoration(
                          color: AppColors.borderSubtle,
                          borderRadius: BorderRadius.circular(999),
                        ),
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Choose theme mode',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textPrimary,
                        fontSize: 22,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Switch between Light, Dark, or System mode.',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textSecondary,
                        fontSize: 13,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 18),
                    _buildThemeModeItem(sheetContext, ref, 'Light Mode', ThemeMode.light, selectedMode),
                    const SizedBox(height: 8),
                    _buildThemeModeItem(sheetContext, ref, 'Dark Mode', ThemeMode.dark, selectedMode),
                    const SizedBox(height: 8),
                    _buildThemeModeItem(sheetContext, ref, 'System Default', ThemeMode.system, selectedMode),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildThemeModeItem(
    BuildContext context,
    WidgetRef ref,
    String label,
    ThemeMode mode,
    ThemeMode selectedMode,
  ) {
    final isSelected = mode == selectedMode;
    final borderColor = isSelected ? AppColors.accentCyan : AppColors.borderSubtle;
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () async {
        await ref.read(themeControllerProvider.notifier).setThemeMode(mode);
        if (!context.mounted) return;
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: borderColor, width: isSelected ? 1.6 : 1),
          boxShadow: isSelected ? AppColors.cyanGlow : [],
        ),
        child: Row(
          children: [
            Icon(
              mode == ThemeMode.light
                  ? Icons.light_mode_outlined
                  : (mode == ThemeMode.dark ? Icons.dark_mode_outlined : Icons.settings_brightness_outlined),
              color: isSelected ? AppColors.accentCyan : AppColors.textPrimary,
              size: 20,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: GoogleFonts.dmSans(
                  color: AppColors.textPrimary,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Icon(
              isSelected ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              color: isSelected ? AppColors.accentCyan : AppColors.textMuted,
              size: 20,
            ),
          ],
        ),
      ),
    );
  }

  void _showComingSoon(BuildContext context, String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          '$feature coming soon!',
          style: GoogleFonts.dmSans(color: AppColors.textPrimary),
        ),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        backgroundColor: AppColors.bgCard,
      ),
    );
  }
}

class _SettingsRow extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final Widget? trailing;
  final VoidCallback onTap;

  const _SettingsRow({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: iconColor.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(10),
              ),
              alignment: Alignment.center,
              child: Icon(icon, color: iconColor, size: 18),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            trailing ??
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

class _ThemePreviewBubble extends StatelessWidget {
  final Alignment alignment;
  final Color backgroundColor;
  final Color textColor;
  final String label;

  const _ThemePreviewBubble({
    required this.alignment,
    required this.backgroundColor,
    required this.textColor,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: alignment,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: backgroundColor,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.borderSubtle.withValues(alpha: 0.5)),
        ),
        child: Text(
          label,
          style: GoogleFonts.dmSans(
            color: textColor,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _ThemeChoiceCard extends StatelessWidget {
  final AppColorTheme theme;
  final bool isSelected;
  final VoidCallback onTap;

  const _ThemeChoiceCard({
    required this.theme,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final borderColor = isSelected ? AppColors.accentCyan : AppColors.borderSubtle;

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: borderColor, width: isSelected ? 1.6 : 1),
          boxShadow: isSelected ? AppColors.cyanGlow : [],
        ),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [theme.primaryLight, theme.primaryDark],
                ),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: theme.previewColors
                    .map(
                      (color) => Container(
                        width: 8,
                        height: 8,
                        margin: const EdgeInsets.symmetric(horizontal: 2),
                        decoration: BoxDecoration(
                          color: color,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.5),
                          ),
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    theme.name,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    theme.description,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textSecondary,
                      fontSize: 11,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(
              isSelected
                  ? Icons.check_circle_rounded
                  : Icons.radio_button_unchecked_rounded,
              color: isSelected ? AppColors.accentCyan : AppColors.textMuted,
              size: 22,
            ),
          ],
        ),
      ),
    );
  }
}

class _WallpaperChoiceCard extends StatelessWidget {
  final ChatWallpaper wallpaper;
  final bool isSelected;
  final VoidCallback onTap;

  const _WallpaperChoiceCard({
    required this.wallpaper,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final borderColor = isSelected ? AppColors.accentCyan : AppColors.borderSubtle;

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.bgSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: borderColor, width: isSelected ? 1.6 : 1),
          boxShadow: isSelected ? AppColors.cyanGlow : [],
        ),
        child: Row(
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                gradient: wallpaper.gradient(true),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: wallpaper.accentColor.withValues(alpha: 0.3)),
              ),
              child: Stack(
                children: [
                  Positioned(
                    top: 10,
                    right: 8,
                    child: Container(
                      width: 24,
                      height: 24,
                      decoration: BoxDecoration(
                        color: wallpaper.accentColor.withValues(alpha: 0.4),
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                  Positioned(
                    bottom: 10,
                    left: 8,
                    child: Container(
                      width: 28,
                      height: 18,
                      decoration: BoxDecoration(
                        color: wallpaper.accentColor.withValues(alpha: 0.25),
                        borderRadius: BorderRadius.circular(999),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    wallpaper.name,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      letterSpacing: -0.1,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    wallpaper.description,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textSecondary,
                      fontSize: 11,
                      height: 1.4,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(
              isSelected
                  ? Icons.check_circle_rounded
                  : Icons.radio_button_unchecked_rounded,
              color: isSelected ? AppColors.accentCyan : AppColors.textMuted,
              size: 22,
            ),
          ],
        ),
      ),
    );
  }
}
