import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../core/theme/chat_wallpaper.dart';

class ChatWallpaperState {
  final ChatWallpaper selectedWallpaper;
  final bool isLoaded;

  const ChatWallpaperState({
    required this.selectedWallpaper,
    required this.isLoaded,
  });

  ChatWallpaperState copyWith({
    ChatWallpaper? selectedWallpaper,
    bool? isLoaded,
  }) {
    return ChatWallpaperState(
      selectedWallpaper: selectedWallpaper ?? this.selectedWallpaper,
      isLoaded: isLoaded ?? this.isLoaded,
    );
  }
}

class ChatWallpaperController extends StateNotifier<ChatWallpaperState> {
  ChatWallpaperController()
      : super(
          const ChatWallpaperState(
            selectedWallpaper: ChatWallpaper.cloud,
            isLoaded: false,
          ),
        ) {
    _loadSavedWallpaper();
  }

  static const _selectedWallpaperKey = 'selected_chat_wallpaper';

  Future<void> _loadSavedWallpaper() async {
    final prefs = await SharedPreferences.getInstance();
    final savedWallpaperId = prefs.getString(_selectedWallpaperKey);
    final savedWallpaper = savedWallpaperId == null
        ? state.selectedWallpaper
        : ChatWallpaper.fromId(savedWallpaperId);

    state = state.copyWith(
      selectedWallpaper: savedWallpaper,
      isLoaded: true,
    );
  }

  Future<void> setWallpaper(ChatWallpaper wallpaper) async {
    if (wallpaper.id == state.selectedWallpaper.id) {
      state = state.copyWith(isLoaded: true);
      return;
    }

    state = state.copyWith(
      selectedWallpaper: wallpaper,
      isLoaded: true,
    );

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_selectedWallpaperKey, wallpaper.id);
  }
}

final chatWallpaperProvider =
    StateNotifierProvider<ChatWallpaperController, ChatWallpaperState>(
  (ref) => ChatWallpaperController(),
);
