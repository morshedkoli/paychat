import 'package:flutter/material.dart';

/// Consistent shadow and elevation system
class AppShadows {
  /// Small shadow - for subtle elevation (cards in lists)
  static const List<BoxShadow> sm = [
    BoxShadow(
      color: Color(0x0A000000), // 4% opacity
      blurRadius: 4,
      offset: Offset(0, 1),
      spreadRadius: 0,
    ),
  ];

  /// Medium shadow - for elevated components (modals, dropdowns)
  static const List<BoxShadow> md = [
    BoxShadow(
      color: Color(0x14000000), // 8% opacity
      blurRadius: 8,
      offset: Offset(0, 2),
      spreadRadius: 0,
    ),
    BoxShadow(
      color: Color(0x0A000000), // 4% opacity
      blurRadius: 4,
      offset: Offset(0, 1),
      spreadRadius: 0,
    ),
  ];

  /// Large shadow - for floating action buttons, prominent cards
  static const List<BoxShadow> lg = [
    BoxShadow(
      color: Color(0x1A000000), // 10% opacity
      blurRadius: 16,
      offset: Offset(0, 4),
      spreadRadius: 0,
    ),
    BoxShadow(
      color: Color(0x0F000000), // 6% opacity
      blurRadius: 8,
      offset: Offset(0, 2),
      spreadRadius: 0,
    ),
  ];

  /// Extra large shadow - for dialogs, important overlays
  static const List<BoxShadow> xl = [
    BoxShadow(
      color: Color(0x1F000000), // 12% opacity
      blurRadius: 24,
      offset: Offset(0, 8),
      spreadRadius: 0,
    ),
    BoxShadow(
      color: Color(0x14000000), // 8% opacity
      blurRadius: 12,
      offset: Offset(0, 4),
      spreadRadius: 0,
    ),
  ];

  /// Colored shadow for primary accent (e.g., FAB)
  static List<BoxShadow> primaryColored(Color color) => [
        BoxShadow(
          color: color.withOpacity(0.3),
          blurRadius: 12,
          offset: const Offset(0, 4),
          spreadRadius: 0,
        ),
      ];

  /// Colored shadow for success elements
  static List<BoxShadow> successColored(Color color) => [
        BoxShadow(
          color: color.withOpacity(0.25),
          blurRadius: 10,
          offset: const Offset(0, 3),
          spreadRadius: 0,
        ),
      ];

  /// Colored shadow for warning/danger elements
  static List<BoxShadow> warningColored(Color color) => [
        BoxShadow(
          color: color.withOpacity(0.25),
          blurRadius: 10,
          offset: const Offset(0, 3),
          spreadRadius: 0,
        ),
      ];

  /// Inner shadow effect (using inset-like appearance)
  static const List<BoxShadow> inner = [
    BoxShadow(
      color: Color(0x14000000),
      blurRadius: 4,
      offset: Offset(0, 2),
      spreadRadius: -2,
    ),
  ];

  /// No shadow
  static const List<BoxShadow> none = [];
}
