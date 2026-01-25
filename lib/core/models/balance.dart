import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:equatable/equatable.dart';

/// Balance model for tracking calculated balances
/// Balance is calculated from approved transactions and group expense splits
class Balance extends Equatable {
  final String id; // Format: {chatId}_{userId}
  final String chatId;
  final String userId;
  final String otherUserId; // For 1-to-1 chats
  final double balance; // Positive = you will receive, Negative = you owe
  final DateTime lastCalculatedAt;

  const Balance({
    required this.id,
    required this.chatId,
    required this.userId,
    required this.otherUserId,
    required this.balance,
    required this.lastCalculatedAt,
  });

  /// Create Balance from Firestore document
  factory Balance.fromFirestore(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data()!;
    return Balance(
      id: doc.id,
      chatId: data['chatId'] ?? '',
      userId: data['userId'] ?? '',
      otherUserId: data['otherUserId'] ?? '',
      balance: (data['balance'] ?? 0).toDouble(),
      lastCalculatedAt: (data['lastCalculatedAt'] as Timestamp).toDate(),
    );
  }

  /// Convert Balance to JSON for Firestore
  Map<String, dynamic> toJson() {
    return {
      'chatId': chatId,
      'userId': userId,
      'otherUserId': otherUserId,
      'balance': balance,
      'lastCalculatedAt': Timestamp.fromDate(lastCalculatedAt),
    };
  }

  /// Copy with method
  Balance copyWith({
    String? id,
    String? chatId,
    String? userId,
    String? otherUserId,
    double? balance,
    DateTime? lastCalculatedAt,
  }) {
    return Balance(
      id: id ?? this.id,
      chatId: chatId ?? this.chatId,
      userId: userId ?? this.userId,
      otherUserId: otherUserId ?? this.otherUserId,
      balance: balance ?? this.balance,
      lastCalculatedAt: lastCalculatedAt ?? this.lastCalculatedAt,
    );
  }

  @override
  List<Object?> get props => [
        id,
        chatId,
        userId,
        otherUserId,
        balance,
        lastCalculatedAt,
      ];
}
