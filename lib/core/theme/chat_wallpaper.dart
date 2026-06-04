import 'package:flutter/material.dart';

class ChatWallpaper {
  final String id;
  final String name;
  final String description;
  final Color topColor;
  final Color bottomColor;
  final Color accentColor;

  const ChatWallpaper({
    required this.id,
    required this.name,
    required this.description,
    required this.topColor,
    required this.bottomColor,
    required this.accentColor,
  });

  List<Color> get previewColors => [topColor, bottomColor, accentColor];

  // ── Stitch-inspired chat wallpaper presets ─────────────────────────────────────

  /// PayChat Mint: the default Stitch mint green wallpaper
  static const cloud = ChatWallpaper(
    id: 'cloud',
    name: 'PayChat Mint',
    description: 'Soft mint green background matching the Stitch design',
    topColor:    Color(0xFFDFF2E8),
    bottomColor: Color(0xFFE8F5EE),
    accentColor: Color(0xFF8FD4AA),
  );

  /// Forest Calm: deeper green tone
  static const meadow = ChatWallpaper(
    id: 'meadow',
    name: 'Forest Calm',
    description: 'Deep forest green with calming undertones',
    topColor:    Color(0xFFD4EDE0),
    bottomColor: Color(0xFFDFF2E8),
    accentColor: Color(0xFF5DAF88),
  );

  /// Pure White: clean minimal white
  static const peach = ChatWallpaper(
    id: 'peach',
    name: 'Pure White',
    description: 'Clean and minimal pure white background',
    topColor:    Color(0xFFF8F8F8),
    bottomColor: Color(0xFFFFFFFF),
    accentColor: Color(0xFFD4EDE0),
  );

  static const List<ChatWallpaper> all = [cloud, meadow, peach];

  static ChatWallpaper fromId(String id) {
    return all.firstWhere(
      (wallpaper) => wallpaper.id == id,
      orElse: () => cloud,
    );
  }

  /// Resolve gradient based on active light/dark state.
  LinearGradient gradient(bool isDark) {
    if (!isDark) {
      return LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [topColor, bottomColor],
      );
    }
    // Dark mode — deep green tones
    return LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: [
        Color.lerp(topColor, const Color(0xFF0D1F17), 0.85) ?? const Color(0xFF0D1F17),
        Color.lerp(bottomColor, const Color(0xFF0D1F17), 0.90) ?? const Color(0xFF0D1F17),
      ],
    );
  }

  Color orbColor(bool isDark) => isDark
      ? accentColor.withValues(alpha: 0.18)
      : accentColor.withValues(alpha: 0.40);
}
