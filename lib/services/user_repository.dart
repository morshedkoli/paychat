import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:paychat/models/app_user.dart';

class UserRepository {
  final FirebaseFirestore firestore;
  final FirebaseStorage storage;

  UserRepository({
    required this.firestore,
    required this.storage,
  });

  late final CollectionReference<Map<String, dynamic>> _users =
      firestore.collection('users');

  late final CollectionReference<Map<String, dynamic>> _userDirectory =
      firestore.collection('user_directory');

  /// Watch user profile in real-time
  Stream<AppUser?> watchUserProfile(String uid) {
    return _users.doc(uid).snapshots().map((snapshot) {
      if (!snapshot.exists) return null;
      return AppUser.fromMap(snapshot.data() ?? {});
    }).handleError((e) => null);
  }

  /// Fetch user profile once
  Future<AppUser?> fetchUserProfile(String uid) async {
    try {
      final doc = await _users.doc(uid).get();
      if (!doc.exists) return null;
      return AppUser.fromMap(doc.data() ?? {});
    } catch (e) {
      return null;
    }
  }

  /// Ensure user profile exists after login.
  /// Phone is normalized before storage so lookups always match.
  Future<AppUser> ensureUserProfile({
    required String uid,
    required String? phoneNumber,
    required String? displayName,
    required String? photoUrl,
    required String? email,
  }) async {
    final existing = await fetchUserProfile(uid);
    final now = DateTime.now();
    final normalizedPhone = normalizePhone(phoneNumber ?? '');

    if (existing == null) {
      final newUser = AppUser(
        uid: uid,
        phoneNumber: normalizedPhone,
        fullName: displayName?.trim() ?? '',
        photoUrl: photoUrl?.toString() ?? '',
        email: email ?? '',
        address: '',
        createdAt: now,
        updatedAt: now,
        lastLoginAt: now,
        isProfileComplete: displayName != null && displayName.trim().isNotEmpty,
        isBlocked: false,
        role: UserRole.user,
        deviceTokens: [],
        walletBalance: 0.0,
      );

      await _users.doc(uid).set(newUser.toMap());
      await _syncUserDirectory(newUser);
      return newUser;
    }

    final merged = existing.copyWith(
      phoneNumber:
          normalizedPhone.isNotEmpty ? normalizedPhone : existing.phoneNumber,
      email: email ?? existing.email,
      photoUrl: photoUrl?.toString() ?? existing.photoUrl,
      updatedAt: now,
      lastLoginAt: now,
    );

    await _users.doc(uid).update(merged.toMap());
    await _syncUserDirectory(merged);
    return merged;
  }

  /// Update user profile
  Future<AppUser> updateProfile({
    required String uid,
    required String fullName,
    required String email,
    required String address,
    String? photoUrl,
  }) async {
    final existing = await fetchUserProfile(uid);
    if (existing == null) {
      throw Exception('User profile not found.');
    }

    final updatedUrl = photoUrl ?? existing.photoUrl;

    final updated = existing.copyWith(
      fullName: fullName.trim(),
      email: email.trim(),
      address: address.trim(),
      photoUrl: updatedUrl,
      updatedAt: DateTime.now(),
      isProfileComplete: fullName.trim().isNotEmpty,
    );

    await _users.doc(uid).update(updated.toMap());
    await _syncUserDirectory(updated);
    return updated;
  }

  /// Save or refresh FCM device token for a user
  Future<void> updateDeviceToken(String uid, String token) async {
    await _users.doc(uid).update({
      'deviceTokens': FieldValue.arrayUnion([token]),
    });
  }

  /// Remove FCM device token for a user (called on logout)
  Future<void> removeDeviceToken(String uid, String token) async {
    await _users.doc(uid).update({
      'deviceTokens': FieldValue.arrayRemove([token]),
    });
  }

  /// Save a verified phone number for a user (e.g. after Google sign-in).
  /// Normalizes the number, updates the main user doc, and syncs the directory.
  Future<AppUser> updatePhoneNumber(String uid, String rawPhone) async {
    final normalized = normalizePhone(rawPhone);
    if (normalized.isEmpty) {
      throw Exception('Invalid phone number.');
    }

    final existing = await fetchUserProfile(uid);
    if (existing == null) {
      throw Exception('User profile not found.');
    }

    final updated = existing.copyWith(
      phoneNumber: normalized,
      updatedAt: DateTime.now(),
    );

    await _users.doc(uid).update({
      'phoneNumber': normalized,
      'updatedAt': Timestamp.fromDate(updated.updatedAt),
    });
    await _syncUserDirectory(updated);
    return updated;
  }

  /// Find user by phone number.
  /// Normalizes the searched number using the same algorithm as storage,
  /// ensuring registered users are always found (never shown as Guest).
  Future<AppUser?> findUserByPhone(String phoneNumber) async {
    try {
      final normalized = normalizePhone(phoneNumber);
      if (normalized.isEmpty) return null;

      final directorySnapshot = await _userDirectory
          .where('phoneNumber', isEqualTo: normalized)
          .limit(1)
          .get();

      if (directorySnapshot.docs.isEmpty) return null;

      final uid = directorySnapshot.docs.first.data()['uid'] as String?;
      if (uid == null || uid.isEmpty) return null;

      return fetchUserProfile(uid);
    } catch (e) {
      return null;
    }
  }



  /// Sync user to the directory, always using the normalized phone number.
  Future<void> _syncUserDirectory(AppUser user) async {
    final normalizedPhone = normalizePhone(user.phoneNumber);
    if (normalizedPhone.isEmpty) return;

    final payload = {
      'uid': user.uid,
      'phoneNumber': normalizedPhone,
      'fullName': user.fullName,
      'photoUrl': user.photoUrl,
      'phoneSearchTokens': _phoneSearchTokens(normalizedPhone),
      'updatedAt': Timestamp.fromDate(user.updatedAt),
    };

    await _userDirectory.doc(user.uid).set(payload, SetOptions(merge: true));
  }

  /// Canonical phone normalization — used for BOTH storing and searching.
  ///
  /// Rules:
  ///  - Strips all non-digit / non-plus characters
  ///  - Ensures a leading '+'
  ///  - Fixes Bangladesh double-zero: +8800XXXXXXXX → +880XXXXXXXX
  ///  - Fixes US double-one: +11XXXXXXXXXX → +1XXXXXXXXXX
  static String normalizePhone(String raw) {
    if (raw.trim().isEmpty) return '';

    String s = raw.replaceAll(RegExp(r'[^\d+]'), '');
    if (s.isEmpty) return '';

    if (!s.startsWith('+')) {
      s = '+$s';
    }

    // Bangladesh: +8800XXXXXXXX → +880XXXXXXXX
    if (s.startsWith('+8800')) {
      s = '+880${s.substring(5)}';
    }

    // US: +11XXXXXXXXXX (13 chars) → +1XXXXXXXXXX
    if (s.startsWith('+11') && s.length == 13) {
      s = '+1${s.substring(3)}';
    }

    return s;
  }

  /// Generate prefix search tokens for phone-number search
  List<String> _phoneSearchTokens(String phoneNumber) {
    final sanitized = normalizePhone(phoneNumber);
    if (sanitized.isEmpty) return [];

    final tokens = <String>[];
    for (int i = 1; i <= sanitized.length; i++) {
      tokens.add(sanitized.substring(0, i).toLowerCase());
    }
    return tokens;
  }
}
