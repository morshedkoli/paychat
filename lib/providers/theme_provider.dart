import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/theme/app_color_theme.dart';
import '../core/theme/app_colors.dart';

class ThemeState {
  final AppColorTheme selectedTheme;
  final bool isLoaded;

  const ThemeState({
    required this.selectedTheme,
    required this.isLoaded,
  });

  ThemeState copyWith({AppColorTheme? selectedTheme, bool? isLoaded}) {
    return ThemeState(
      selectedTheme: selectedTheme ?? this.selectedTheme,
      isLoaded: isLoaded ?? this.isLoaded,
    );
  }

  /// Convenience getter so existing call sites that read themeMode still work.
  ThemeMode get themeMode => selectedTheme.themeMode;
}

class ThemeController extends StateNotifier<ThemeState> {
  ThemeController()
      : super(const ThemeState(
          selectedTheme: AppColorTheme.mint,
          isLoaded: false,
        )) {
    AppColors.setTheme(state.selectedTheme);
    _loadSavedTheme();
  }

  static const _selectedThemeKey = 'selected_chat_theme';

  Future<void> _loadSavedTheme() async {
    final prefs = await SharedPreferences.getInstance();
    final savedId = prefs.getString(_selectedThemeKey);
    final saved = savedId == null
        ? AppColorTheme.mint
        : AppColorTheme.fromId(savedId);

    AppColors.setTheme(saved);
    state = state.copyWith(selectedTheme: saved, isLoaded: true);
  }

  Future<void> setTheme(AppColorTheme theme) async {
    AppColors.setTheme(theme);
    state = state.copyWith(selectedTheme: theme, isLoaded: true);

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_selectedThemeKey, theme.id);
  }

  /// Kept for backward compat — now maps to setTheme based on mode.
  Future<void> setThemeMode(ThemeMode mode) async {
    final current = state.selectedTheme;

    AppColorTheme next;
    if (mode == ThemeMode.light) {
      next = AppColorTheme.light;
    } else if (mode == ThemeMode.dark) {
      // Preserve mint vs pure-dark choice; default to dark if already on light
      next = (current.id == 'mint') ? AppColorTheme.mint : AppColorTheme.dark;
    } else {
      next = current;
    }
    await setTheme(next);
  }
}

final themeControllerProvider =
    StateNotifierProvider<ThemeController, ThemeState>(
  (ref) => ThemeController(),
);
