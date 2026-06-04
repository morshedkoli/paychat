import 'package:flutter/material.dart';
import 'app_color_theme.dart';

/// Single source of truth for all runtime colors.
/// All getters read from the currently active [AppColorTheme] preset.
/// Call [setTheme] whenever the preset changes.
class AppColors {
  AppColors._();

  static AppColorTheme _active = AppColorTheme.mint;

  /// Whether the active preset is a dark-mode palette.
  static bool isDark = true;

  static void setTheme(AppColorTheme theme) {
    _active = theme;
    isDark = theme.themeMode == ThemeMode.dark;
  }

  // ── Backgrounds ──────────────────────────────────────────────────────────────
  static Color get bgPrimary   => _active.bgPrimary;
  static Color get bgSecondary => _active.bgSecondary;
  static Color get bgCard      => _active.bgCard;
  static Color get bgSurface   => _active.bgSurface;
  static Color get bgGlass     =>
      Colors.white.withValues(alpha: isDark ? 0.08 : 0.70);

  // ── Brand ────────────────────────────────────────────────────────────────────
  static Color get primaryGreen       => _active.primary;
  static Color get primaryGreenLight  => _active.primaryLight;
  static Color get primaryGreenBright => _active.primaryDark;
  static Color get accentEmerald      => _active.accent;
  static const Color mintOrb          = Color(0xFF8FD4AA);

  // Legacy aliases — used throughout the codebase
  static Color get accentGold       => primaryGreen;
  static Color get accentGoldBright => primaryGreenLight;
  static Color get accentIndigo     => _active.primaryLight;
  static Color get accentCyan       => primaryGreen;
  static const Color accentRose     = Color(0xFFEF5350);
  static Color get accentCyan_      => accentCyan;
  static Color get accentViolet     => accentIndigo;
  static Color get accentGreen      => accentEmerald;
  static Color get accentAmber      => primaryGreen;

  // ── Text ─────────────────────────────────────────────────────────────────────
  static Color get textPrimary   => _active.textPrimary;
  static Color get textSecondary => _active.textSecondary;
  static Color get textMuted     => _active.textMuted;

  // ── Borders ──────────────────────────────────────────────────────────────────
  static Color get borderSubtle => _active.borderSubtle;
  static Color get borderGlow   => primaryGreen.withValues(alpha: 0.18);
  static Color get borderGlass  =>
      Colors.white.withValues(alpha: isDark ? 0.1 : 0.6);

  // ── Error ─────────────────────────────────────────────────────────────────────
  static const Color errorColor = Color(0xFFEF5350);

  // ── Chat bubbles ──────────────────────────────────────────────────────────────
  static Color get sentBubble         => primaryGreen;
  static Color get receivedBubble     => bgCard;
  static Color get sentBubbleText     => Colors.white;
  static Color get receivedBubbleText => textPrimary;

  // ── Shadows ───────────────────────────────────────────────────────────────────
  static List<BoxShadow> get goldGlow => [
    BoxShadow(
      color: primaryGreen.withValues(alpha: 0.20),
      blurRadius: 10,
      spreadRadius: -2,
      offset: const Offset(0, 4),
    ),
  ];
  static List<BoxShadow> get cyanGlow   => goldGlow;
  static List<BoxShadow> get greenGlow  => goldGlow;
  static List<BoxShadow> get indigoGlow => goldGlow;
  static List<BoxShadow> get violetGlow => goldGlow;

  static List<BoxShadow> get cardGlow => [
    BoxShadow(
      color: Colors.black.withValues(alpha: isDark ? 0.20 : 0.06),
      blurRadius: 8,
      spreadRadius: 0,
      offset: const Offset(0, 2),
    ),
  ];

  // ── Decorations ───────────────────────────────────────────────────────────────
  static BoxDecoration glassCard({
    double borderRadius = 20,
    Color? accentBorder,
  }) =>
      BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: accentBorder ?? borderSubtle, width: 1),
        boxShadow: cardGlow,
      );

  static BoxDecoration get mintOrbDecoration => BoxDecoration(
    shape: BoxShape.circle,
    gradient: RadialGradient(
      colors: [
        mintOrb.withValues(alpha: isDark ? 0.15 : 0.35),
        Colors.transparent,
      ],
    ),
  );

  // ── Gradients ─────────────────────────────────────────────────────────────────
  static LinearGradient get gradientGold => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [primaryGreenLight, primaryGreen],
  );
  static LinearGradient get gradientCyan    => gradientGold;
  static LinearGradient get gradientEmerald => gradientGold;
  static LinearGradient get gradientGreen   => gradientGold;

  static LinearGradient get gradientHeroCard => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [primaryGreenLight, primaryGreen, _active.primaryDark],
    stops: const [0.0, 0.5, 1.0],
  );

  static LinearGradient get gradientBackground => LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [bgSurface, bgPrimary],
  );

  static const LinearGradient gradientIndigo = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF4CAF80), Color(0xFF2E7D5E)],
  );
  static LinearGradient get gradientViolet => gradientIndigo;
}
