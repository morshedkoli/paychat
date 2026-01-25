import 'package:firebase_auth/firebase_auth.dart' as auth;
import 'package:google_sign_in/google_sign_in.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../models/user.dart' as models;

/// Authentication service for Google Sign-In and user management
class AuthService {
  final auth.FirebaseAuth _firebaseAuth = auth.FirebaseAuth.instance;
  final GoogleSignIn _googleSignIn = GoogleSignIn();
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;

  /// Get current Firebase user
  auth.User? get currentFirebaseUser => _firebaseAuth.currentUser;

  /// Get current user ID
  String? get currentUserId => _firebaseAuth.currentUser?.uid;

  /// Stream of authentication state changes
  Stream<auth.User?> get authStateChanges => _firebaseAuth.authStateChanges();

  /// Sign in with Google
  /// Returns the User model if successful, throws exception otherwise
  Future<models.User> signInWithGoogle({String? phoneNumber}) async {
    try {
      // Trigger the Google Sign-In flow
      final GoogleSignInAccount? googleUser = await _googleSignIn.signIn();
      if (googleUser == null) {
        throw Exception('Google Sign-In cancelled by user');
      }

      // Obtain the auth details from the request
      final GoogleSignInAuthentication googleAuth =
          await googleUser.authentication;

      // Create a new credential
      final credential = auth.GoogleAuthProvider.credential(
        accessToken: googleAuth.accessToken,
        idToken: googleAuth.idToken,
      );

      // Sign in to Firebase with the Google credential
      final auth.UserCredential userCredential =
          await _firebaseAuth.signInWithCredential(credential);

      final auth.User firebaseUser = userCredential.user!;

      print('DEBUG: Firebase Auth Success. UID: ${firebaseUser.uid}');
      final token = await firebaseUser.getIdToken();
      print('DEBUG: ID Token (first 20 chars): ${token?.substring(0, 20)}...');

      // Get FCM token for push notifications
      String? fcmToken;
      try {
        fcmToken = await _messaging.getToken();
        print('DEBUG: FCM Token obtained');
      } catch (e) {
        print('Failed to get FCM token: $e');
      }

      // Check if user exists in Firestore
      print('DEBUG: Attempting to GET user doc at users/${firebaseUser.uid}');

      DocumentSnapshot<Map<String, dynamic>> userDoc;
      try {
        userDoc =
            await _firestore.collection('users').doc(firebaseUser.uid).get();
        print('DEBUG: GET success. Exists: ${userDoc.exists}');
      } catch (e) {
        print('DEBUG: GET failed with error: $e');
        rethrow;
      }

      models.User user;

      if (!userDoc.exists) {
        // New user - create profile
        // Phone number is mandatory, get from parameter or fallback to Google's
        final String finalPhoneNumber =
            phoneNumber ?? firebaseUser.phoneNumber ?? '';

        user = models.User(
          id: firebaseUser.uid,
          name: firebaseUser.displayName ?? 'User',
          phoneNumber: finalPhoneNumber,
          email: firebaseUser.email,
          avatarUrl: firebaseUser.photoURL,
          fcmToken: fcmToken,
          createdAt: DateTime.now(),
          lastSeen: DateTime.now(),
          isRegistered: true,
        );

        // Save to Firestore
        await _firestore
            .collection('users')
            .doc(firebaseUser.uid)
            .set(user.toJson());
      } else {
        // Existing user - update FCM token and last seen
        user = models.User.fromFirestore(
          userDoc,
        );

        // Update FCM token, last seen, and sync Google profile data
        final updates = {
          'fcmToken': fcmToken,
          'lastSeen': Timestamp.now(),
        };

        // Sync profile data if available from Google
        if (firebaseUser.photoURL != null) {
          updates['photoUrl'] = firebaseUser.photoURL;
        }
        if (firebaseUser.displayName != null) {
          updates['displayName'] = firebaseUser.displayName;
        }
        if (firebaseUser.email != null) {
          updates['email'] = firebaseUser.email;
        }
        if (firebaseUser.phoneNumber != null) {
          updates['phoneNumber'] = firebaseUser.phoneNumber;
        }

        await _firestore
            .collection('users')
            .doc(firebaseUser.uid)
            .update(updates);

        user = user.copyWith(
          fcmToken: fcmToken,
          lastSeen: DateTime.now(),
          avatarUrl: firebaseUser.photoURL ?? user.avatarUrl,
          name: firebaseUser.displayName ?? user.name,
          email: firebaseUser.email ?? user.email,
          phoneNumber: firebaseUser.phoneNumber ?? user.phoneNumber,
        );
      }

      return user;
    } catch (e) {
      print('Error signing in with Google: $e');
      rethrow;
    }
  }

  /// Update user phone number (mandatory field)
  Future<void> updatePhoneNumber(String phoneNumber) async {
    final userId = currentUserId;
    if (userId == null) throw Exception('No user signed in');

    await _firestore.collection('users').doc(userId).update({
      'phoneNumber': phoneNumber,
    });
  }

  /// Get user by ID
  Future<models.User?> getUserById(String userId) async {
    try {
      final doc = await _firestore.collection('users').doc(userId).get();
      if (!doc.exists) return null; // Genuine "User not found"
      return models.User.fromFirestore(doc);
    } catch (e) {
      print('Error getting user: $e');
      rethrow; // Rethrow so the UI knows it's an error, not just "logged out"
    }
  }

  /// Search users by phone number
  Future<models.User?> getUserByPhoneNumber(String phoneNumber) async {
    try {
      final querySnapshot = await _firestore
          .collection('users')
          .where('phoneNumber', isEqualTo: phoneNumber)
          .limit(1)
          .get();

      if (querySnapshot.docs.isEmpty) return null;
      return models.User.fromFirestore(querySnapshot.docs.first);
    } catch (e) {
      print('Error searching user by phone: $e');
      return null;
    }
  }

  /// Update user profile
  Future<void> updateUserProfile({
    String? displayName,
    String? photoUrl,
  }) async {
    final userId = currentUserId;
    if (userId == null) throw Exception('No user signed in');

    final updates = <String, dynamic>{};
    if (displayName != null) updates['displayName'] = displayName;
    if (photoUrl != null) updates['photoUrl'] = photoUrl;

    if (updates.isNotEmpty) {
      await _firestore.collection('users').doc(userId).update(updates);
    }
  }

  /// Sign out
  Future<void> signOut() async {
    await Future.wait([
      _googleSignIn.signOut(),
      _firebaseAuth.signOut(),
    ]);
  }

  /// Delete account (for testing purposes)
  Future<void> deleteAccount() async {
    final userId = currentUserId;
    if (userId == null) throw Exception('No user signed in');

    // Delete user data from Firestore
    await _firestore.collection('users').doc(userId).delete();

    // Delete Firebase Auth account
    await currentFirebaseUser?.delete();
  }
}
