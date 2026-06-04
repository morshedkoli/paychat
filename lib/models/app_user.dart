import 'package:cloud_firestore/cloud_firestore.dart';

enum UserRole {
  user('user'),
  admin('admin');

  final String value;
  const UserRole(this.value);
}

class AppUser {
  final String uid;
  final String phoneNumber;
  final String fullName;
  final String photoUrl;
  final String email;
  final String address;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime lastLoginAt;
  final bool isProfileComplete;
  final bool isBlocked;
  final UserRole role;
  final List<String> deviceTokens;
  final double walletBalance;

  AppUser({
    required this.uid,
    required this.phoneNumber,
    required this.fullName,
    required this.photoUrl,
    required this.email,
    required this.address,
    required this.createdAt,
    required this.updatedAt,
    required this.lastLoginAt,
    required this.isProfileComplete,
    required this.isBlocked,
    required this.role,
    required this.deviceTokens,
    required this.walletBalance,
  });

  String get displayName {
    final name = fullName.trim();
    if (name.isNotEmpty) return name;
    if (phoneNumber.isNotEmpty) return phoneNumber;
    return 'PayChat User';
  }

  factory AppUser.fromMap(Map<String, dynamic> map) {
    return AppUser(
      uid: map['uid'] as String? ?? '',
      phoneNumber: map['phoneNumber'] as String? ?? '',
      fullName: map['fullName'] as String? ?? '',
      photoUrl: map['photoUrl'] as String? ?? '',
      email: map['email'] as String? ?? '',
      address: map['address'] as String? ?? '',
      createdAt: _toDateTime(map['createdAt']),
      updatedAt: _toDateTime(map['updatedAt']),
      lastLoginAt: _toDateTime(map['lastLoginAt']),
      isProfileComplete: map['isProfileComplete'] as bool? ?? false,
      isBlocked: map['isBlocked'] as bool? ?? false,
      role: _parseRole(map['role'] as String?),
      deviceTokens: List<String>.from(map['deviceTokens'] as List? ?? []),
      walletBalance: (map['walletBalance'] as num?)?.toDouble() ?? 0.0,
    );
  }

  factory AppUser.fromFirestore(DocumentSnapshot doc) {
    return AppUser.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'uid': uid,
      'phoneNumber': phoneNumber,
      'fullName': fullName,
      'photoUrl': photoUrl,
      'email': email,
      'address': address,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
      'lastLoginAt': Timestamp.fromDate(lastLoginAt),
      'isProfileComplete': isProfileComplete,
      'isBlocked': isBlocked,
      'role': role.value,
      'deviceTokens': deviceTokens,
      'walletBalance': walletBalance,
    };
  }

  /// Use this map for client-initiated profile updates.
  /// Excludes server-owned fields (walletBalance, isBlocked, role) that must
  /// only be written by Cloud Functions or admin tools.
  Map<String, dynamic> toProfileUpdateMap() {
    return {
      'fullName': fullName,
      'photoUrl': photoUrl,
      'email': email,
      'address': address,
      'updatedAt': Timestamp.fromDate(updatedAt),
      'isProfileComplete': isProfileComplete,
    };
  }

  AppUser copyWith({
    String? uid,
    String? phoneNumber,
    String? fullName,
    String? photoUrl,
    String? email,
    String? address,
    DateTime? createdAt,
    DateTime? updatedAt,
    DateTime? lastLoginAt,
    bool? isProfileComplete,
    bool? isBlocked,
    UserRole? role,
    List<String>? deviceTokens,
    double? walletBalance,
  }) {
    return AppUser(
      uid: uid ?? this.uid,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      fullName: fullName ?? this.fullName,
      photoUrl: photoUrl ?? this.photoUrl,
      email: email ?? this.email,
      address: address ?? this.address,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      lastLoginAt: lastLoginAt ?? this.lastLoginAt,
      isProfileComplete: isProfileComplete ?? this.isProfileComplete,
      isBlocked: isBlocked ?? this.isBlocked,
      role: role ?? this.role,
      deviceTokens: deviceTokens ?? this.deviceTokens,
      walletBalance: walletBalance ?? this.walletBalance,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppUser &&
          runtimeType == other.runtimeType &&
          uid == other.uid;

  @override
  int get hashCode => uid.hashCode;
}

DateTime _toDateTime(dynamic value) {
  if (value is Timestamp) return value.toDate();
  if (value is DateTime) return value;
  if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
  return DateTime.now();
}

UserRole _parseRole(String? value) {
  return UserRole.values.firstWhere(
    (e) => e.value == value,
    orElse: () => UserRole.user,
  );
}
