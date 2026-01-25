import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:equatable/equatable.dart';

/// User model with Firebase integration
/// Phone number is mandatory and globally unique
class User extends Equatable {
  final String id;
  final String name;
  final String phoneNumber; // Mandatory, with country code (e.g., +1234567890)
  final String? email;
  final String? avatarUrl;
  final String? fcmToken; // For push notifications
  final DateTime createdAt;
  final DateTime? lastSeen;
  final bool isRegistered;

  const User({
    required this.id,
    required this.name,
    required this.phoneNumber,
    this.email,
    this.avatarUrl,
    this.fcmToken,
    required this.createdAt,
    this.lastSeen,
    this.isRegistered = true,
  });

  /// Create User from Firestore document
  factory User.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data()!;
    return User(
      id: doc.id,
      name: data['displayName'] ?? '',
      phoneNumber: data['phoneNumber'] ?? '',
      email: data['email'],
      avatarUrl: data['photoUrl'],
      fcmToken: data['fcmToken'],
      createdAt: (data['createdAt'] as Timestamp).toDate(),
      lastSeen: data['lastSeen'] != null
          ? (data['lastSeen'] as Timestamp).toDate()
          : null,
      isRegistered: data['isRegistered'] ?? true,
    );
  }

  /// Create User from JSON map
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      name: json['displayName'] ?? '',
      phoneNumber: json['phoneNumber'] ?? '',
      email: json['email'],
      avatarUrl: json['photoUrl'],
      fcmToken: json['fcmToken'],
      createdAt: json['createdAt'] is Timestamp
          ? (json['createdAt'] as Timestamp).toDate()
          : DateTime.parse(
              json['createdAt'] ?? DateTime.now().toIso8601String()),
      lastSeen: json['lastSeen'] != null
          ? (json['lastSeen'] is Timestamp
              ? (json['lastSeen'] as Timestamp).toDate()
              : DateTime.parse(json['lastSeen']))
          : null,
      isRegistered: json['isRegistered'] ?? true,
    );
  }

  /// Convert User to JSON for Firestore
  Map<String, dynamic> toJson() {
    return {
      'displayName': name,
      'phoneNumber': phoneNumber,
      'email': email,
      'photoUrl': avatarUrl,
      'fcmToken': fcmToken,
      'createdAt': Timestamp.fromDate(createdAt),
      'lastSeen': lastSeen != null ? Timestamp.fromDate(lastSeen!) : null,
      'isRegistered': isRegistered,
    };
  }

  /// Copy with method for updates
  User copyWith({
    String? id,
    String? name,
    String? phoneNumber,
    String? email,
    String? avatarUrl,
    String? fcmToken,
    DateTime? createdAt,
    DateTime? lastSeen,
    bool? isRegistered,
  }) {
    return User(
      id: id ?? this.id,
      name: name ?? this.name,
      phoneNumber: phoneNumber ?? this.phoneNumber,
      email: email ?? this.email,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      fcmToken: fcmToken ?? this.fcmToken,
      createdAt: createdAt ?? this.createdAt,
      lastSeen: lastSeen ?? this.lastSeen,
      isRegistered: isRegistered ?? this.isRegistered,
    );
  }

  @override
  List<Object?> get props => [
        id,
        name,
        phoneNumber,
        email,
        avatarUrl,
        fcmToken,
        createdAt,
        lastSeen,
        isRegistered,
      ];
}
