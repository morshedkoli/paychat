import 'package:flutter/material.dart';

/// Enhanced color system with semantic tokens and gradients
class AppColors {
  // ===== BRAND COLORS =====

  /// Primary brand color - Indigo
  static const Color primary = Color(0xFF243A5E);

  /// Lighter variant of primary
  static const Color primaryLight = Color(0xFF3E5C8A);

  /// Darker variant of primary
  static const Color primaryDark = Color(0xFF1A2945);

  /// Accent color - Teal
  static const Color accent = Color(0xFF1ECAD3);

  /// Lighter variant of accent
  static const Color accentLight = Color(0xFF4DD9E0);

  /// Darker variant of accent
  static const Color accentDark = Color(0xFF15A8B0);

  // ===== SEMANTIC COLORS =====

  /// Success/positive state color
  static const Color success = Color(0xFF2ECC71);

  /// Lighter variant for success backgrounds
  static const Color successLight = Color(0xFF8CE5A6);

  /// Warning/caution state color
  static const Color warning = Color(0xFFF5A623);

  /// Lighter variant for warning backgrounds
  static const Color warningLight = Color(0xFFFFC875);

  /// Danger/error state color
  static const Color danger = Color(0xFFE74C3C);

  /// Lighter variant for danger backgrounds
  static const Color dangerLight = Color(0xFFF5A99B);

  /// Info/neutral positive color
  static const Color info = Color(0xFF3498DB);

  /// Lighter variant for info backgrounds
  static const Color infoLight = Color(0xFF85C1E9);

  // ===== NEUTRAL COLORS =====

  /// Light background color
  static const Color backgroundLight = Color(0xFFF6F8FB);

  /// Primary surface color (white)
  static const Color surface = Color(0xFFFFFFFF);

  /// Elevated surface with subtle tint
  static const Color surfaceElevated = Color(0xFFFAFBFC);

  /// Divider/border color
  static const Color divider = Color(0xFFE5E7EB);

  /// Subtle border for inputs
  static const Color border = Color(0xFFD1D5DB);

  /// Overlay background (for modals, dialogs)
  static const Color overlay = Color(0x80000000); // 50% black

  // ===== TEXT COLORS =====

  /// Primary text color - high emphasis
  static const Color textPrimary = Color(0xFF1F2937);

  /// Secondary text color - medium emphasis
  static const Color textSecondary = Color(0xFF6B7280);

  /// Tertiary text color - low emphasis
  static const Color textTertiary = Color(0xFF9CA3AF);

  /// Disabled text color
  static const Color textDisabled = Color(0xFFD1D5DB);

  /// Text on primary color backgrounds
  static const Color textOnPrimary = Color(0xFFFFFFFF);

  /// Text on accent color backgrounds
  static const Color textOnAccent = Color(0xFFFFFFFF);

  // ===== STATE COLORS =====

  /// Hover state background
  static const Color hover = Color(0xFFF3F4F6);

  /// Pressed/active state background
  static const Color pressed = Color(0xFFE5E7EB);

  /// Disabled state background
  static const Color disabled = Color(0xFFF9FAFB);

  /// Focus ring color
  static const Color focus = accent;

  /// Selected state background
  static const Color selected = Color(0xFFEFF6FF);

  // ===== GRADIENTS =====

  /// Primary brand gradient
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [primary, primaryLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Accent gradient
  static const LinearGradient accentGradient = LinearGradient(
    colors: [accent, accentLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Success gradient
  static const LinearGradient successGradient = LinearGradient(
    colors: [success, successLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Warning gradient
  static const LinearGradient warningGradient = LinearGradient(
    colors: [warning, warningLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Danger gradient
  static const LinearGradient dangerGradient = LinearGradient(
    colors: [danger, dangerLight],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  /// Subtle background gradient
  static const LinearGradient backgroundGradient = LinearGradient(
    colors: [Color(0xFFFAFBFC), backgroundLight],
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
  );

  /// Shimmer gradient for loading states
  static const LinearGradient shimmerGradient = LinearGradient(
    colors: [
      Color(0xFFE5E7EB),
      Color(0xFFF3F4F6),
      Color(0xFFE5E7EB),
    ],
    stops: [0.0, 0.5, 1.0],
    begin: Alignment(-1.0, 0.0),
    end: Alignment(1.0, 0.0),
  );

  // ===== DARK MODE COLORS (for future use) =====

  /// Dark mode background
  static const Color darkBackground = Color(0xFF0F1419);

  /// Dark mode surface
  static const Color darkSurface = Color(0xFF1A1F29);

  /// Dark mode text primary
  static const Color darkTextPrimary = Color(0xFFE5E7EB);

  /// Dark mode text secondary
  static const Color darkTextSecondary = Color(0xFF9CA3AF);

  // ===== HELPER METHODS =====

  /// Get balance color based on value
  static Color getBalanceColor(double balance) {
    if (balance > 0) return success;
    if (balance < 0) return warning;
    return textSecondary;
  }

  /// Get balance gradient based on value
  static LinearGradient getBalanceGradient(double balance) {
    if (balance > 0) return successGradient;
    if (balance < 0) return warningGradient;
    return const LinearGradient(colors: [textSecondary, textSecondary]);
  }

  /// Get status color
  static Color getStatusColor(String status) {
    switch (status.toLowerCase()) {
      case 'approved':
      case 'autoapproved':
      case 'success':
        return success;
      case 'pending':
      case 'warning':
        return warning;
      case 'rejected':
      case 'failed':
      case 'error':
        return danger;
      case 'info':
        return info;
      default:
        return textSecondary;
    }
  }
}
