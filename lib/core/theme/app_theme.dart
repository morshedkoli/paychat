import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_color_theme.dart';
import 'app_colors.dart';

class AppTheme {
  // ── Color aliases — getters so they always reflect the active theme ───────────
  static Color get primaryColor => AppColors.primaryGreen;
  static Color get primaryDark  => AppColors.primaryGreenBright;
  static Color get primaryLight => AppColors.primaryGreenLight;
  static Color get accentGreen  => AppColors.accentEmerald;

  /// AppBar for all secondary screens (settings, detail views, QR, etc.).
  /// Centralises the repeated cyan-header pattern so it can be updated once.
  static AppBar secondaryAppBar(String title) => AppBar(
    title: Text(
      title,
      style: GoogleFonts.dmSans(
        color: AppColors.accentCyan,
        fontSize: 20,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.8,
      ),
    ),
    backgroundColor: AppColors.bgSecondary,
    foregroundColor: AppColors.accentCyan,
    elevation: 0,
    surfaceTintColor: Colors.transparent,
    iconTheme: IconThemeData(color: AppColors.accentCyan),
  );

  /// Build a [ThemeData] for [preset] and activate it in [AppColors].
  static ThemeData themeForPreset(AppColorTheme preset) {
    AppColors.setTheme(preset);
    final brightness = preset.themeMode == ThemeMode.dark
        ? Brightness.dark
        : Brightness.light;
    return _buildTheme(brightness);
  }

  // ── Semantic color getters ────────────────────────────────────────────────────
  static Color get accentBlue   => AppColors.accentCyan;
  static Color get errorColor   => AppColors.errorColor;
  static Color get warningColor => AppColors.primaryGreen;

  // ── Legacy compat ─────────────────────────────────────────────────────────────
  static Color get lightBackground   => AppColors.bgPrimary;
  static Color get lightSurface      => AppColors.bgCard;
  static Color get lightChatBg       => AppColors.bgPrimary;
  static Color get lightBorder       => AppColors.borderSubtle;
  static Color get lightTextPrimary  => AppColors.textPrimary;
  static Color get lightTextSecond   => AppColors.textSecondary;
  static Color get darkBackground    => AppColors.bgPrimary;
  static Color get darkSurface       => AppColors.bgSecondary;
  static Color get darkElevated      => AppColors.bgSurface;
  static Color get darkBorder        => AppColors.borderSubtle;
  static Color get darkTextSecond    => AppColors.textSecondary;

  // ── Gradients ─────────────────────────────────────────────────────────────────
  static LinearGradient get primaryGradient => AppColors.gradientGold;
  static LinearGradient get accentGradient  => AppColors.gradientEmerald;

  static LinearGradient get darkCardGradient => LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [AppColors.bgCard, AppColors.bgSecondary],
  );

  // ── Shadows ───────────────────────────────────────────────────────────────────
  static List<BoxShadow> get cardShadowLight => AppColors.cardGlow;
  static List<BoxShadow> get cardShadowDark  => AppColors.cardGlow;

  static List<BoxShadow> primaryShadow(double alpha) => [
    BoxShadow(
      color: primaryColor.withValues(alpha: alpha),
      blurRadius: 20,
      spreadRadius: -2,
      offset: const Offset(0, 6),
    ),
  ];

  // ── Text Theme ────────────────────────────────────────────────────────────────
  static TextTheme _buildTextTheme(Color primary, Color secondary) {
    return TextTheme(
      displayLarge: GoogleFonts.dmSans(
        fontSize: 34, fontWeight: FontWeight.w700, color: primary, letterSpacing: -0.8,
      ),
      displayMedium: GoogleFonts.dmSans(
        fontSize: 28, fontWeight: FontWeight.w700, color: primary, letterSpacing: -0.5,
      ),
      displaySmall: GoogleFonts.dmSans(
        fontSize: 24, fontWeight: FontWeight.w700, color: primary, letterSpacing: -0.3,
      ),
      headlineLarge: GoogleFonts.dmSans(
        fontSize: 22, fontWeight: FontWeight.w700, color: primary, letterSpacing: -0.2,
      ),
      headlineMedium: GoogleFonts.dmSans(
        fontSize: 20, fontWeight: FontWeight.w600, color: primary, letterSpacing: -0.1,
      ),
      headlineSmall: GoogleFonts.dmSans(
        fontSize: 18, fontWeight: FontWeight.w600, color: primary,
      ),
      titleLarge: GoogleFonts.dmSans(
        fontSize: 17, fontWeight: FontWeight.w700, color: primary,
      ),
      titleMedium: GoogleFonts.dmSans(
        fontSize: 15, fontWeight: FontWeight.w600, color: primary, letterSpacing: 0.1,
      ),
      titleSmall: GoogleFonts.dmSans(
        fontSize: 14, fontWeight: FontWeight.w500, color: secondary, letterSpacing: 0.1,
      ),
      bodyLarge: GoogleFonts.dmSans(
        fontSize: 16, fontWeight: FontWeight.w400, color: primary, height: 1.55,
      ),
      bodyMedium: GoogleFonts.dmSans(
        fontSize: 14, fontWeight: FontWeight.w400, color: primary, height: 1.5,
      ),
      bodySmall: GoogleFonts.dmSans(
        fontSize: 13, fontWeight: FontWeight.w400, color: secondary, height: 1.4,
      ),
      labelLarge: GoogleFonts.dmSans(
        fontSize: 14, fontWeight: FontWeight.w600, color: primary,
      ),
      labelMedium: GoogleFonts.dmSans(
        fontSize: 12, fontWeight: FontWeight.w500, color: secondary,
      ),
      labelSmall: GoogleFonts.dmSans(
        fontSize: 11, fontWeight: FontWeight.w500, color: secondary, letterSpacing: 0.3,
      ),
    );
  }

  // ── Theme Builder ─────────────────────────────────────────────────────────────
  static ThemeData _buildTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    AppColors.isDark = isDark;

    final primary   = primaryColor;
    final secondary = accentGreen;
    final bg        = AppColors.bgPrimary;
    final cardBg    = AppColors.bgCard;
    final surface   = AppColors.bgSurface;
    final text      = AppColors.textPrimary;
    final textSec   = AppColors.textSecondary;
    final border    = AppColors.borderSubtle;

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      primaryColor: primary,
      scaffoldBackgroundColor: bg,

      colorScheme: ColorScheme(
        brightness: brightness,
        primary: primary,
        onPrimary: Colors.white,
        secondary: secondary,
        onSecondary: Colors.white,
        tertiary: primaryLight,
        onTertiary: Colors.white,
        error: AppColors.errorColor,
        onError: Colors.white,
        surface: cardBg,
        onSurface: text,
        outline: border,
        outlineVariant: AppColors.borderGlass,
        surfaceContainerHighest: surface,
      ),

      textTheme: _buildTextTheme(text, textSec),

      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: text,
        elevation: 0,
        centerTitle: false,
        surfaceTintColor: Colors.transparent,
        shadowColor: Colors.black.withValues(alpha: 0.06),
        systemOverlayStyle: isDark
            ? SystemUiOverlayStyle.light.copyWith(
                statusBarColor: Colors.transparent,
                systemNavigationBarColor: AppColors.bgSecondary,
              )
            : SystemUiOverlayStyle.dark.copyWith(
                statusBarColor: Colors.transparent,
                systemNavigationBarColor: AppColors.bgSecondary,
              ),
        titleTextStyle: GoogleFonts.dmSans(
          color: text,
          fontSize: 20,
          fontWeight: FontWeight.w700,
          letterSpacing: -0.3,
        ),
        iconTheme: IconThemeData(color: text.withValues(alpha: 0.7), size: 22),
        actionsIconTheme: IconThemeData(color: text.withValues(alpha: 0.7), size: 22),
        shape: Border(
          bottom: BorderSide(color: border, width: 0.5),
        ),
      ),

      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          elevation: 0,
          textStyle: GoogleFonts.dmSans(
            fontSize: 15, fontWeight: FontWeight.w700, letterSpacing: 0.2,
          ),
        ),
      ),

      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: primary,
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          side: BorderSide(color: primary.withValues(alpha: 0.5), width: 1.5),
          textStyle: GoogleFonts.dmSans(
            fontSize: 15, fontWeight: FontWeight.w600,
          ),
        ),
      ),

      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primary,
          textStyle: GoogleFonts.dmSans(fontSize: 14, fontWeight: FontWeight.w600),
        ),
      ),

      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: border, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: BorderSide(color: primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.errorColor),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AppColors.errorColor, width: 1.5),
        ),
        hintStyle: GoogleFonts.dmSans(color: AppColors.textMuted, fontSize: 15),
        labelStyle: GoogleFonts.dmSans(color: textSec),
      ),

      cardTheme: CardThemeData(
        color: cardBg,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: border, width: 0.5),
        ),
        margin: EdgeInsets.zero,
        shadowColor: Colors.black.withValues(alpha: 0.08),
      ),

      dividerTheme: DividerThemeData(
        color: border,
        thickness: 0.5,
        space: 1,
      ),

      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: AppColors.bgSecondary,
        selectedItemColor: primary,
        unselectedItemColor: AppColors.textMuted,
        elevation: 0,
        type: BottomNavigationBarType.fixed,
        selectedLabelStyle: GoogleFonts.dmSans(fontWeight: FontWeight.w600, fontSize: 10),
        unselectedLabelStyle: GoogleFonts.dmSans(fontWeight: FontWeight.w500, fontSize: 10),
      ),

      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: primary,
        foregroundColor: Colors.white,
        elevation: 0,
        shape: const CircleBorder(),
      ),

      popupMenuTheme: PopupMenuThemeData(
        color: cardBg,
        shadowColor: Colors.black.withValues(alpha: 0.12),
        elevation: 8,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: border, width: 0.5),
        ),
        textStyle: GoogleFonts.dmSans(color: text, fontSize: 14),
      ),

      snackBarTheme: SnackBarThemeData(
        backgroundColor: cardBg,
        contentTextStyle: GoogleFonts.dmSans(color: text),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        behavior: SnackBarBehavior.floating,
        elevation: 6,
      ),

      listTileTheme: ListTileThemeData(
        tileColor: Colors.transparent,
        selectedColor: primary,
        iconColor: textSec,
        textColor: text,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),

      chipTheme: ChipThemeData(
        backgroundColor: surface,
        selectedColor: primary.withValues(alpha: 0.15),
        side: BorderSide(color: border, width: 0.5),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        labelStyle: GoogleFonts.dmSans(fontSize: 13, fontWeight: FontWeight.w500),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),

      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) return primary;
          return textSec;
        }),
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return primary.withValues(alpha: 0.3);
          }
          return border;
        }),
      ),

      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          foregroundColor: textSec,
          highlightColor: primary.withValues(alpha: 0.08),
        ),
      ),
    );
  }

  static ThemeData get lightTheme {
    AppColors.isDark = false;
    return _buildTheme(Brightness.light);
  }

  static ThemeData get darkTheme {
    AppColors.isDark = true;
    return _buildTheme(Brightness.dark);
  }

  // ── Backwards-compat aliases ───────────────────────────────────────────────────
  static Color get primaryDarkColor    => primaryDark;
  static Color get secondaryColor      => accentGreen;
  static Color get lightChatBackground => lightChatBg;
}
