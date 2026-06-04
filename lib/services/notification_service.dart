import 'dart:async';
import 'dart:io';

import 'dart:ui';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Top-level handler for background FCM messages (must be a top-level function).
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  // Background messages are handled by the OS notification tray automatically.
  // Any heavy processing (e.g., local DB writes) can be done here.
}

/// Centralized push notification service for PayChat.
///
/// Responsibilities:
/// - Request notification permissions
/// - Set up FCM foreground + background handlers
/// - Show local notifications when the app is in the foreground
/// - Expose [onNotificationTap] stream for navigation
class NotificationService {
  NotificationService._();
  static final NotificationService instance = NotificationService._();

  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  /// Android notification channel for chat messages.
  static const AndroidNotificationChannel _messageChannel =
      AndroidNotificationChannel(
    'paychat_messages',
    'Messages',
    description: 'Notifications for new PayChat messages',
    importance: Importance.high,
    playSound: true,
  );

  /// Android notification channel for transactions.
  static const AndroidNotificationChannel _transactionChannel =
      AndroidNotificationChannel(
    'paychat_transactions',
    'Transactions',
    description: 'Notifications for PayChat payment activity',
    importance: Importance.high,
    playSound: true,
  );

  /// Stream of notification tap payloads — listened to by the app router.
  final StreamController<Map<String, dynamic>> _tapStreamController =
      StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get onNotificationTap =>
      _tapStreamController.stream;

  bool _initialized = false;

  /// The UID of the currently signed-in user. Set by [setCurrentUserId].
  String? _currentUserId;

  /// Call this whenever the authenticated user changes (login/logout).
  void setCurrentUserId(String? uid) => _currentUserId = uid;

  /// Call once after [Firebase.initializeApp] in main().
  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;

    // Register the background handler BEFORE anything else.
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);

    // Request permission (iOS requires this, Android 13+ also).
    await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
    );

    // Configure local notifications.
    const androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    await _localNotifications.initialize(
      const InitializationSettings(
        android: androidSettings,
        iOS: iosSettings,
      ),
      onDidReceiveNotificationResponse: _onLocalNotificationTap,
    );

    // Create Android notification channels.
    if (!kIsWeb && Platform.isAndroid) {
      final androidPlugin = _localNotifications
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>();
      await androidPlugin?.createNotificationChannel(_messageChannel);
      await androidPlugin?.createNotificationChannel(_transactionChannel);
    }

    // Keep notification options when app is foregrounded (iOS).
    await _messaging.setForegroundNotificationPresentationOptions(
      alert: true,
      badge: true,
      sound: true,
    );

    // Foreground: show a local notification manually.
    FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

    // Background tap: user tapped notification while app was in background.
    FirebaseMessaging.onMessageOpenedApp.listen(_handleNotificationTap);

    // Terminated tap: user tapped notification that launched the app.
    final initialMessage = await _messaging.getInitialMessage();
    if (initialMessage != null) {
      _handleNotificationTap(initialMessage);
    }
  }

  // ── Internal handlers ─────────────────────────────────────────────────────

  void _handleForegroundMessage(RemoteMessage message) {
    final notification = message.notification;
    final data = message.data;
    if (notification == null) return;

    // Suppress the notification if the current user is the SENDER.
    // This prevents the sender from seeing their own "new message" popup
    // in the rare case FCM delivers the push to both participants' devices
    // (e.g., stale token reuse) or when recipientId is missing.
    final senderId   = data['senderId']   as String?;
    final recipientId = data['recipientId'] as String?;

    // If we know the intended recipient and it's NOT us, skip showing.
    if (recipientId != null && recipientId.isNotEmpty && recipientId != _currentUserId) {
      return;
    }
    // Fallback: if we ARE the sender, skip showing (sender never needs their own push).
    if (senderId != null && senderId.isNotEmpty && senderId == _currentUserId) {
      return;
    }

    final isTransaction = data['type'] == 'transaction';
    final channel = isTransaction ? _transactionChannel : _messageChannel;

    _localNotifications.show(
      message.hashCode,
      notification.title,
      notification.body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          channel.id,
          channel.name,
          channelDescription: channel.description,
          importance: Importance.high,
          priority: Priority.high,
          icon: '@mipmap/ic_launcher',
          color: const Color(0xFF00E5FF), // AppColors.accentCyan
        ),
        iOS: const DarwinNotificationDetails(
          presentAlert: true,
          presentBadge: true,
          presentSound: true,
        ),
      ),
      payload: _encodePayload(data),
    );
  }

  void _handleNotificationTap(RemoteMessage message) {
    if (message.data.isNotEmpty) {
      _tapStreamController.add(message.data);
    }
  }

  void _onLocalNotificationTap(NotificationResponse response) {
    final payload = response.payload;
    if (payload != null && payload.isNotEmpty) {
      _tapStreamController.add(_decodePayload(payload));
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  String _encodePayload(Map<String, dynamic> data) =>
      data.entries.map((e) => '${e.key}=${e.value}').join('&');

  Map<String, dynamic> _decodePayload(String payload) {
    final map = <String, dynamic>{};
    for (final part in payload.split('&')) {
      final idx = part.indexOf('=');
      if (idx != -1) map[part.substring(0, idx)] = part.substring(idx + 1);
    }
    return map;
  }

  void dispose() => _tapStreamController.close();
}
