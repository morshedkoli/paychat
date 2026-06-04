import 'package:flutter/material.dart';

/// A complete color palette preset for PayChat.
/// Each preset defines every surface, text, border and accent color
/// plus the Flutter ThemeMode that should be used with it.
class AppColorTheme {
  final String id;
  final String name;
  final String description;
  final ThemeMode themeMode;

  // Surfaces
  final Color bgPrimary;
  final Color bgSecondary;
  final Color bgCard;
  final Color bgSurface;

  // Text
  final Color textPrimary;
  final Color textSecondary;
  final Color textMuted;

  // Borders
  final Color borderSubtle;

  // Brand accent
  final Color primary;
  final Color primaryLight;
  final Color primaryDark;
  final Color accent;

  const AppColorTheme({
    required this.id,
    required this.name,
    required this.description,
    required this.themeMode,
    required this.bgPrimary,
    required this.bgSecondary,
    required this.bgCard,
    required this.bgSurface,
    required this.textPrimary,
    required this.textSecondary,
    required this.textMuted,
    required this.borderSubtle,
    required this.primary,
    required this.primaryLight,
    required this.primaryDark,
    required this.accent,
  });

  /// Called by ThemeController — no-op here; activation is handled via
  /// AppColors.setTheme() called directly from ThemeController.
  void applyToAppTheme() {}

  List<Color> get previewColors => [primaryLight, primary, accent];

  // ── Mint ─────────────────────────────────────────────────────────────────────
  // Dark emerald-green background with bright mint accents (current default look)
  static const mint = AppColorTheme(
    id: 'mint',
    name: 'Mint',
    description: 'Dark emerald background with mint green accents',
    themeMode: ThemeMode.dark,
    bgPrimary:     Color(0xFF1B4332),
    bgSecondary:   Color(0xFF122A1F),
    bgCard:        Color(0xFF1F3528),
    bgSurface:     Color(0xFF243E2E),
    textPrimary:   Color(0xFFF0FAF4),
    textSecondary: Color(0xFF7BA88F),
    textMuted:     Color(0xFF4A6B57),
    borderSubtle:  Color(0xFF2A4A38),
    primary:       Color(0xFF52D98F),
    primaryLight:  Color(0xFF7EEAA8),
    primaryDark:   Color(0xFF2E7D5E),
    accent:        Color(0xFF3DAA74),
  );

  // ── Dark ─────────────────────────────────────────────────────────────────────
  // Pure OLED charcoal with mint accent — modern dark fintech
  static const dark = AppColorTheme(
    id: 'dark',
    name: 'Dark',
    description: 'Pure dark mode with charcoal surfaces and mint accents',
    themeMode: ThemeMode.dark,
    bgPrimary:     Color(0xFF0F0F0F),
    bgSecondary:   Color(0xFF171717),
    bgCard:        Color(0xFF1E1E1E),
    bgSurface:     Color(0xFF252525),
    textPrimary:   Color(0xFFEEEEEE),
    textSecondary: Color(0xFFA0A0A0),
    textMuted:     Color(0xFF606060),
    borderSubtle:  Color(0xFF2E2E2E),
    primary:       Color(0xFF52D98F),
    primaryLight:  Color(0xFF7EEAA8),
    primaryDark:   Color(0xFF2E7D5E),
    accent:        Color(0xFF3DAA74),
  );

  // ── Light ────────────────────────────────────────────────────────────────────
  // Clean white with dark-green accents — clear daytime look
  static const light = AppColorTheme(
    id: 'light',
    name: 'Light',
    description: 'Clean white theme with green accents',
    themeMode: ThemeMode.light,
    bgPrimary:     Color(0xFFF5F7F5),
    bgSecondary:   Color(0xFFFFFFFF),
    bgCard:        Color(0xFFFFFFFF),
    bgSurface:     Color(0xFFEDF4EF),
    textPrimary:   Color(0xFF111827),
    textSecondary: Color(0xFF4B5563),
    textMuted:     Color(0xFF9CA3AF),
    borderSubtle:  Color(0xFFE5E7EB),
    primary:       Color(0xFF2E7D5E),
    primaryLight:  Color(0xFF4CAF80),
    primaryDark:   Color(0xFF1B5E42),
    accent:        Color(0xFF3DAA74),
  );

  // Legacy ID mappings so old saved preferences still resolve
  static const sage   = mint;
  static const sky    = dark;
  static const blush  = light;
  static const neon   = mint;
  static const violet = dark;
  static const matrix = light;

  static const List<AppColorTheme> all = [mint, dark, light];

  static AppColorTheme fromId(String id) => all.firstWhere(
        (t) => t.id == id,
        orElse: () => mint,
      );
}
