import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/models/session_state.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/services/auth_service.dart';
import 'package:paychat/services/chat_repository.dart';
import 'package:paychat/services/user_repository.dart';

/// Auth Entry State Provider
final authEntryStateProvider = StateNotifierProvider<
    AuthEntryStateNotifier,
    AuthEntryState>((ref) {
  return AuthEntryStateNotifier();
});

class AuthEntryStateNotifier extends StateNotifier<AuthEntryState> {
  AuthEntryStateNotifier() : super(AuthEntryState());

  void updateCountry({
    required String iso,
    required String dialCode,
  }) {
    state = state.copyWith(
      countryIso: iso,
      countryDialCode: dialCode,
    );
  }

  void updateNationalNumber(String number) {
    state = state.copyWith(
      nationalNumber: number.replaceAll(RegExp(r'[^\d]'), ''),
    );
  }

  void setPhoneStep({
    required String countryIso,
    required String countryDialCode,
    required String phoneNumberE164,
    required bool isSendingCode,
  }) {
    state = state.copyWith(
      step: AuthEntryStep.phone,
      countryIso: countryIso,
      countryDialCode: countryDialCode,
      phoneNumberE164: phoneNumberE164,
      isSendingCode: isSendingCode,
      errorMessage: null,
    );
  }

  void setOtpStep({
    required String verificationId,
    required String otpSentTo,
  }) {
    state = state.copyWith(
      step: AuthEntryStep.otp,
      verificationId: verificationId,
      otpSentTo: otpSentTo,
      isSendingCode: false,
      isVerifyingCode: false,
      errorMessage: null,
    );
  }

  void setVerifying(bool isVerifying) {
    state = state.copyWith(
      isVerifyingCode: isVerifying,
    );
  }

  void setSendingCode(bool isSending) {
    state = state.copyWith(
      isSendingCode: isSending,
    );
  }

  void setError(String? message) {
    state = state.copyWith(
      errorMessage: message,
      isSendingCode: false,
      isVerifyingCode: false,
    );
  }

  void reset() {
    state = AuthEntryState();
  }
}

/// Session State Provider
final sessionStateProvider = StateNotifierProvider<
    SessionStateNotifier,
    SessionState>((ref) {
  final authService = ref.watch(authServiceProvider);
  final userRepository = ref.watch(userRepositoryProvider);
  final chatRepository = ref.watch(chatRepositoryProvider);
  return SessionStateNotifier(
    authService: authService,
    userRepository: userRepository,
    chatRepository: chatRepository,
  );
});

class SessionStateNotifier extends StateNotifier<SessionState> {
  final AuthService authService;
  final UserRepository userRepository;
  final ChatRepository chatRepository;

  SessionStateNotifier({
    required this.authService,
    required this.userRepository,
    required this.chatRepository,
  }) : super(SessionState.loading()) {
    _initialize();
  }

  void _initialize() async {
    final user = authService.currentUser;
    if (user == null) {
      state = SessionState.unauthenticated();
    } else {
      await _loadUserProfile(user.uid);
    }
  }

  Future<void> _loadUserProfile(String uid) async {
    try {
      final profile = await userRepository.fetchUserProfile(uid);
      
      if (profile == null) {
        state = SessionState.unauthenticated();
        return;
      }

      if (profile.isBlocked) {
        state = SessionState.blocked(profile);
      } else if (profile.phoneNumber.isEmpty) {
        // Google (or other OAuth) users who haven't provided a phone yet
        state = SessionState.phoneSetupRequired(profile);
      } else if (!profile.isProfileComplete) {
        state = SessionState.profileIncomplete(profile);
      } else {
        state = SessionState.authenticated(profile);
        // Migrate any guest threads created before this user registered
        if (profile.phoneNumber.isNotEmpty) {
          chatRepository.migrateGuestThreads(
            guestPhone: profile.phoneNumber,
            realUserId: profile.uid,
            realUserName: profile.fullName,
            realUserPhotoUrl: profile.photoUrl,
          );
        }
        // Save FCM token
        _saveFcmToken(profile.uid);
      }
    } catch (e) {
      state = SessionState.unauthenticated(message: 'Error loading profile');
    }
  }

  Future<void> ensureProfile({
    required String uid,
    required String? phoneNumber,
    required String? displayName,
    required String? photoUrl,
    required String? email,
  }) async {
    try {
      final user = await userRepository.ensureUserProfile(
        uid: uid,
        phoneNumber: phoneNumber,
        displayName: displayName,
        photoUrl: photoUrl,
        email: email,
      );

      if (user.isBlocked) {
        state = SessionState.blocked(user);
      } else if (user.phoneNumber.isEmpty) {
        state = SessionState.phoneSetupRequired(user);
      } else if (!user.isProfileComplete) {
        state = SessionState.profileIncomplete(user);
      } else {
        state = SessionState.authenticated(user);
      }
    } catch (e) {
      state = SessionState.unauthenticated(
        message: 'Failed to ensure profile: ${e.toString()}',
      );
    }
  }

  Future<void> _saveFcmToken(String uid) async {
    try {
      final messaging = FirebaseMessaging.instance;
      await messaging.requestPermission();
      final token = await messaging.getToken();
      if (token != null) {
        await userRepository.updateDeviceToken(uid, token);
      }
      // Refresh token listener
      messaging.onTokenRefresh.listen((newToken) {
        userRepository.updateDeviceToken(uid, newToken);
      });
    } catch (_) {}
  }

  void signOut() async {
    try {
      final user = authService.currentUser;
      if (user != null) {
        final token = await FirebaseMessaging.instance.getToken();
        if (token != null) {
          await userRepository.removeDeviceToken(user.uid, token);
        }
      }
    } catch (_) {}
    await authService.signOut();
    state = SessionState.unauthenticated();
  }

  void retry() {
    _initialize();
  }
}
