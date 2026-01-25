import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:equatable/equatable.dart';

/// Transaction status enum
enum TransactionStatus {
  pending,
  approved,
  rejected,
  autoApproved, // For unregistered users
}

/// Transaction model for ledger entries
/// Transactions are immutable after approval
class Transaction extends Equatable {
  final String id;
  final String chatId;
  final String senderId; // Who created the transaction
  final String receiverId; // Who needs to approve
  final double amount;
  final String note;
  final String currency;
  final TransactionStatus status;
  final DateTime createdAt;
  final DateTime? approvedAt;
  final DateTime? rejectedAt;

  const Transaction({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.receiverId,
    required this.amount,
    required this.note,
    this.currency = 'BDT',
    required this.status,
    required this.createdAt,
    this.approvedAt,
    this.rejectedAt,
  });

  /// Check if transaction is immutable (approved or rejected)
  bool get isImmutable =>
      status == TransactionStatus.approved ||
      status == TransactionStatus.autoApproved ||
      status == TransactionStatus.rejected;

  /// Create Transaction from Firestore document
  factory Transaction.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data()!;
    return Transaction(
      id: doc.id,
      chatId: data['chatId'] ?? '',
      senderId: data['senderId'] ?? '',
      receiverId: data['receiverId'] ?? '',
      amount: (data['amount'] ?? 0).toDouble(),
      note: data['note'] ?? '',
      currency: data['currency'] ?? 'USD',
      status: TransactionStatus.values.firstWhere(
        (e) => e.toString() == 'TransactionStatus.${data['status']}',
        orElse: () => TransactionStatus.pending,
      ),
      createdAt: (data['createdAt'] as Timestamp).toDate(),
      approvedAt: data['approvedAt'] != null
          ? (data['approvedAt'] as Timestamp).toDate()
          : null,
      rejectedAt: data['rejectedAt'] != null
          ? (data['rejectedAt'] as Timestamp).toDate()
          : null,
    );
  }

  /// Convert Transaction to JSON for Firestore
  Map<String, dynamic> toJson() {
    return {
      'chatId': chatId,
      'senderId': senderId,
      'receiverId': receiverId,
      'amount': amount,
      'note': note,
      'currency': currency,
      'status': status.name,
      'createdAt': Timestamp.fromDate(createdAt),
      'approvedAt': approvedAt != null ? Timestamp.fromDate(approvedAt!) : null,
      'rejectedAt': rejectedAt != null ? Timestamp.fromDate(rejectedAt!) : null,
    };
  }

  /// Copy with method for status updates
  Transaction copyWith({
    String? id,
    String? chatId,
    String? senderId,
    String? receiverId,
    double? amount,
    String? note,
    String? currency,
    TransactionStatus? status,
    DateTime? createdAt,
    DateTime? approvedAt,
    DateTime? rejectedAt,
  }) {
    return Transaction(
      id: id ?? this.id,
      chatId: chatId ?? this.chatId,
      senderId: senderId ?? this.senderId,
      receiverId: receiverId ?? this.receiverId,
      amount: amount ?? this.amount,
      note: note ?? this.note,
      currency: currency ?? this.currency,
      status: status ?? this.status,
      createdAt: createdAt ?? this.createdAt,
      approvedAt: approvedAt ?? this.approvedAt,
      rejectedAt: rejectedAt ?? this.rejectedAt,
    );
  }

  @override
  List<Object?> get props => [
        id,
        chatId,
        senderId,
        receiverId,
        amount,
        note,
        currency,
        status,
        createdAt,
        approvedAt,
        rejectedAt,
      ];
}
