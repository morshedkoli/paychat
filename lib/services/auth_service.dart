import 'package:firebase_auth/firebase_auth.dart';
// RecaptchaVerifier is in firebase_auth; FirebaseAuthPlatform is in the platform interface.
// ignore: depend_on_referenced_packages
import 'package:firebase_auth_platform_interface/firebase_auth_platform_interface.dart'
    show FirebaseAuthPlatform;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:google_sign_in/google_sign_in.dart';

class AuthService {
  final FirebaseAuth auth;

  // Holds the web ConfirmationResult after signInWithPhoneNumber
  ConfirmationResult? _webConfirmationResult;

  // Mobile-only: GoogleSignIn (lazy, never created on web)
  GoogleSignIn? _googleSignIn;
  GoogleSignIn get _googleSignInInstance {
    _googleSignIn ??= GoogleSignIn(scopes: ['email', 'profile']);
    return _googleSignIn!;
  }

  AuthService({required this.auth});

  /// Get current user
  User? get currentUser => auth.currentUser;

  /// Watch auth state changes
  Stream<User?> authStateChanges() => auth.authStateChanges();

  /// Start phone number verification.
  ///
  /// On web: uses [signInWithPhoneNumber] with an invisible RecaptchaVerifier.
  /// On mobile: uses the standard [verifyPhoneNumber] flow.
  Future<void> verifyPhoneNumber({
    required String phoneNumber,
    required Function(PhoneAuthCredential) onVerificationComplete,
    required Function(FirebaseAuthException) onVerificationFailed,
    required Function(String verificationId, int? resendToken) onCodeSent,
    required Function(String verificationId) onCodeAutoRetrievalTimeOut,
  }) async {
    if (kIsWeb) {
      try {
        final verifier = RecaptchaVerifier(
          auth: FirebaseAuthPlatform.instance,
          container: 'recaptcha-container',
          size: RecaptchaVerifierSize.normal,
        );
        _webConfirmationResult = await auth.signInWithPhoneNumber(
          phoneNumber,
          verifier,
        );

        // Emit a sentinel verificationId so the OTP screen can proceed.
        onCodeSent('web_confirmation', null);
      } on FirebaseAuthException catch (e) {
        onVerificationFailed(e);
      } catch (e) {
        onVerificationFailed(
          FirebaseAuthException(code: 'unknown', message: e.toString()),
        );
      }
    } else {
      // Force reCAPTCHA fallback on Android.
      // Play Integrity (SafetyNet) attestation fails on many devices
      // (rooted, unlocked bootloader, missing Play Store, etc.).
      // Setting forceResendingToken forces the web-based reCAPTCHA flow
      // which works universally.
      await auth.setSettings(
        forceRecaptchaFlow: true,
      );
      await auth.verifyPhoneNumber(
        phoneNumber: phoneNumber,
        verificationCompleted: onVerificationComplete,
        verificationFailed: onVerificationFailed,
        codeSent: onCodeSent,
        codeAutoRetrievalTimeout: onCodeAutoRetrievalTimeOut,
        timeout: const Duration(seconds: 120),
      );
    }
  }

  /// Confirm the OTP on web using the stored [ConfirmationResult].
  Future<UserCredential> confirmOtp(String smsCode) async {
    if (kIsWeb && _webConfirmationResult != null) {
      final result = await _webConfirmationResult!.confirm(smsCode);
      _webConfirmationResult = null;
      return result;
    }
    throw FirebaseAuthException(
      code: 'no-confirmation-result',
      message: 'No pending web confirmation. Please restart verification.',
    );
  }

  /// Sign in with phone credential (mobile path)
  Future<UserCredential> signInWithPhoneCredential(
      PhoneAuthCredential credential) {
    return auth.signInWithCredential(credential);
  }

  /// Create phone auth credential from verification ID and OTP
  PhoneAuthCredential phoneAuthCredential({
    required String verificationId,
    required String smsCode,
  }) {
    return PhoneAuthProvider.credential(
      verificationId: verificationId,
      smsCode: smsCode,
    );
  }

  /// Sign out
  Future<void> signOut() async {

    _webConfirmationResult = null;
    await auth.signOut();
  }

  /// Delete Account
  Future<void> deleteAccount() async {
    if (auth.currentUser != null) {
      await auth.currentUser!.delete();
    }
  }

  /// Update Phone Number
  Future<void> updatePhoneNumber(PhoneAuthCredential credential) async {
    if (auth.currentUser != null) {
      await auth.currentUser!.updatePhoneNumber(credential);
    }
  }

  /// Check if user is authenticated
  bool get isAuthenticated => auth.currentUser != null;

  /// Sign in with Google.
  ///
  /// • Web   → Firebase [signInWithPopup] via [GoogleAuthProvider].
  ///           Uses Firebase's own authDomain — no extra origin registration needed.
  /// • Mobile → [google_sign_in] package → Firebase credential exchange.
  Future<UserCredential> signInWithGoogle() async {
    try {
      if (kIsWeb) {
        // ── Web path ────────────────────────────────────────────────────────
        // Firebase handles the popup internally through its authDomain,
        // so there is no origin_mismatch error regardless of the dev port.
        final provider = GoogleAuthProvider()
          ..addScope('email')
          ..addScope('profile')
          ..setCustomParameters({'prompt': 'select_account'});

        return await auth.signInWithPopup(provider);
      } else {
        // ── Mobile path ─────────────────────────────────────────────────────
        final GoogleSignInAccount? googleUser = await _googleSignInInstance.signIn();
        if (googleUser == null) {
          throw FirebaseAuthException(
            code: 'sign-in-canceled',
            message: 'Google Sign-In was canceled by the user.',
          );
        }

        final GoogleSignInAuthentication googleAuth =
            await googleUser.authentication;

        final OAuthCredential credential = GoogleAuthProvider.credential(
          accessToken: googleAuth.accessToken,
          idToken: googleAuth.idToken,
        );

        return await auth.signInWithCredential(credential);
      }
    } catch (e) {
      if (e is FirebaseAuthException) rethrow;
      throw FirebaseAuthException(
        code: 'unknown',
        message: e.toString(),
      );
    }
  }
}
