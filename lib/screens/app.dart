import 'dart:async';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/core/theme/app_theme.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/models/session_state.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/screens/auth_screen.dart';
import 'package:paychat/screens/chat_screen.dart';
import 'package:paychat/screens/home_screen.dart';
import 'package:paychat/screens/phone_setup_screen.dart';
import 'package:paychat/screens/profile_setup_screen.dart';
import 'package:paychat/services/notification_service.dart';

final _navigatorKey = GlobalKey<NavigatorState>();

class ThemeSyncWrapper extends StatelessWidget {
  final Widget child;
  const ThemeSyncWrapper({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    AppColors.isDark = Theme.of(context).brightness == Brightness.dark;
    return child;
  }
}

class PayChatApp extends ConsumerWidget {
  const PayChatApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessionState = ref.watch(sessionStateProvider);
    final themeState = ref.watch(themeControllerProvider);

    final themeData = AppTheme.themeForPreset(themeState.selectedTheme);
    return MaterialApp(
      title: 'PayChat',
      navigatorKey: _navigatorKey,
      theme: themeData,
      darkTheme: themeData,
      themeMode: ThemeMode.light,
      home: ThemeSyncWrapper(
        child: _NotificationAwareHome(
          sessionState: sessionState,
          ref: ref,
        ),
      ),
    );
  }
}

/// Wraps home content and reacts to notification taps for navigation.
class _NotificationAwareHome extends StatefulWidget {
  final SessionState sessionState;
  final WidgetRef ref;
  const _NotificationAwareHome({required this.sessionState, required this.ref});

  @override
  State<_NotificationAwareHome> createState() => _NotificationAwareHomeState();
}

class _NotificationAwareHomeState extends State<_NotificationAwareHome> with WidgetsBindingObserver {
  StreamSubscription<Map<String, dynamic>>? _notifSub;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _updatePresence(true);
    // Inform the notification service who is currently logged in so the
    // foreground handler can suppress notifications sent by this user.
    _syncUserId(widget.sessionState);
    if (!kIsWeb) {
      _notifSub = NotificationService.instance.onNotificationTap.listen(
        _handleNotificationTap,
      );
    }
  }

  @override
  void didUpdateWidget(covariant _NotificationAwareHome oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.sessionState.user?.uid != widget.sessionState.user?.uid) {
      _syncUserId(widget.sessionState);
      _updatePresence(true);
    }
  }

  void _syncUserId(SessionState state) {
    if (!kIsWeb) {
      NotificationService.instance.setCurrentUserId(state.user?.uid);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _updatePresence(true);
    } else if (state == AppLifecycleState.paused || state == AppLifecycleState.inactive) {
      _updatePresence(false);
    }
  }

  Future<void> _updatePresence(bool isOnline) async {
    final uid = widget.sessionState.user?.uid;
    if (uid == null) return;
    try {
      final firestore = widget.ref.read(firestoreProvider);
      await firestore.collection('users').doc(uid).update({
        'isOnline': isOnline,
        'lastActive': FieldValue.serverTimestamp(),
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _updatePresence(false);
    _notifSub?.cancel();
    super.dispose();
  }

  Future<void> _handleNotificationTap(Map<String, dynamic> data) async {
    final type = data['type'] as String?;
    final threadId = data['threadId'] as String?;
    final currentUserId = widget.sessionState.user?.uid;

    // Only navigate when the user is authenticated
    if (widget.sessionState.status != SessionStatus.authenticated) return;
    final navigator = _navigatorKey.currentState;
    if (navigator == null) return;

    if (type == 'message' && threadId != null && threadId.isNotEmpty) {
      try {
        // Load thread info from Firestore to build ChatScreen params
        final firestore = widget.ref.read(firestoreProvider);
        final threadDoc =
            await firestore.collection('threads').doc(threadId).get();
        if (!threadDoc.exists) return;

        final tData = threadDoc.data()!;
        final participantIds =
            List<String>.from(tData['participantIds'] as List? ?? []);
        final partnerId = participantIds
            .firstWhere((id) => id != currentUserId, orElse: () => '');
        final users =
            Map<String, dynamic>.from(tData['users'] as Map? ?? {});
        final partnerData =
            Map<String, dynamic>.from(users[partnerId] as Map? ?? {});
        final partnerName =
            (partnerData['name'] as String?)?.trim() ?? 'Unknown';
        final initials = partnerName.isNotEmpty
            ? partnerName
                .split(' ')
                .take(2)
                .map((w) => w.isNotEmpty ? w[0].toUpperCase() : '')
                .join()
            : '?';

        navigator.push(
          MaterialPageRoute(
            builder: (_) => ChatScreen(
              threadId: threadId,
              partnerName: partnerName,
              partnerInitials: initials,
              avatarColor: Colors.cyan,
              partnerUserId: partnerId,
            ),
          ),
        );
        // Reset unread count now that the user is viewing the chat
        try {
          final firestore = widget.ref.read(firestoreProvider);
          await firestore
              .collection('threads')
              .doc(threadId)
              .update({'unreadCount': 0});
        } catch (_) {}
      } catch (_) {}
    } else if (type == 'transaction') {
      // Pop to HomeScreen root (transactions tab visible there)
      navigator.popUntil((route) => route.isFirst);
    }
  }

  @override
  Widget build(BuildContext context) {
    return _buildHome(widget.sessionState);
  }

  Widget _buildHome(SessionState sessionState) {
    switch (sessionState.status) {
      case SessionStatus.loading:
        return Scaffold(
          backgroundColor: const Color(0xFFE8F5EE),
          body: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: const BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      colors: [Color(0xFF4CAF80), Color(0xFF2E7D5E)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: const Icon(Icons.chat_bubble_rounded, color: Colors.white, size: 30),
                ),
                const SizedBox(height: 24),
                const CircularProgressIndicator(
                  color: Color(0xFF2E7D5E),
                  strokeWidth: 2.5,
                ),
              ],
            ),
          ),
        );
      case SessionStatus.unauthenticated:
        return const AuthScreen();
      case SessionStatus.phoneSetupRequired:
        return PhoneSetupScreen(user: sessionState.user!);
      case SessionStatus.profileIncomplete:
        return ProfileSetupScreen(user: sessionState.user!);
      case SessionStatus.authenticated:
        return const HomeScreen();
      case SessionStatus.blocked:
        return Scaffold(
          backgroundColor: const Color(0xFFE8F5EE),
          body: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.red.withValues(alpha: 0.1),
                    ),
                    child: const Icon(Icons.block_rounded, size: 40, color: Colors.red),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    'Account Blocked',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF0F2318),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Your account has been blocked. Please contact support.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey[600], fontSize: 15),
                  ),
                ],
              ),
            ),
          ),
        );
    }
  }
}
