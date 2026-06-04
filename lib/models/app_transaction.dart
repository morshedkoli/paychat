import 'package:cloud_firestore/cloud_firestore.dart';

enum TransactionType {
  sent('SENT'),
  received('RECEIVED'),
  paid('PAID'),
  due('DUE');

  final String value;
  const TransactionType(this.value);

  factory TransactionType.fromString(String value) {
    return TransactionType.values.firstWhere(
      (e) => e.value == value,
      orElse: () => TransactionType.sent,
    );
  }
}

enum TransactionStatus {
  pending('PENDING'),
  completed('COMPLETED'),
  cancelled('CANCELLED');

  final String value;
  const TransactionStatus(this.value);

  factory TransactionStatus.fromString(String value) {
    return TransactionStatus.values.firstWhere(
      (e) => e.value == value,
      orElse: () => TransactionStatus.pending,
    );
  }
}

class AppTransaction {
  final String transactionId;
  final String userId;
  final String contactName;
  final String contactPhone;
  final TransactionType type;
  final double amount;
  final String note;
  final DateTime createdAt;
  final DateTime updatedAt;
  final TransactionStatus status;

  AppTransaction({
    required this.transactionId,
    required this.userId,
    required this.contactName,
    required this.contactPhone,
    required this.type,
    required this.amount,
    required this.note,
    required this.createdAt,
    required this.updatedAt,
    required this.status,
  });

  bool get isPositive => type == TransactionType.received || type == TransactionType.due;

  factory AppTransaction.fromMap(Map<String, dynamic> map) {
    return AppTransaction(
      transactionId: map['transactionId'] as String? ?? '',
      userId: map['userId'] as String? ?? '',
      contactName: map['contactName'] as String? ?? '',
      contactPhone: map['contactPhone'] as String? ?? '',
      type: TransactionType.fromString(map['type'] as String? ?? 'SENT'),
      amount: (map['amount'] as num?)?.toDouble() ?? 0.0,
      note: map['note'] as String? ?? '',
      createdAt: _toDateTime(map['createdAt']),
      updatedAt: _toDateTime(map['updatedAt']),
      status: TransactionStatus.fromString(map['status'] as String? ?? 'PENDING'),
    );
  }

  factory AppTransaction.fromFirestore(DocumentSnapshot doc) {
    return AppTransaction.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'transactionId': transactionId,
      'userId': userId,
      'contactName': contactName,
      'contactPhone': contactPhone,
      'type': type.value,
      'amount': amount,
      'note': note,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
      'status': status.value,
    };
  }

  AppTransaction copyWith({
    String? transactionId,
    String? userId,
    String? contactName,
    String? contactPhone,
    TransactionType? type,
    double? amount,
    String? note,
    DateTime? createdAt,
    DateTime? updatedAt,
    TransactionStatus? status,
  }) {
    return AppTransaction(
      transactionId: transactionId ?? this.transactionId,
      userId: userId ?? this.userId,
      contactName: contactName ?? this.contactName,
      contactPhone: contactPhone ?? this.contactPhone,
      type: type ?? this.type,
      amount: amount ?? this.amount,
      note: note ?? this.note,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      status: status ?? this.status,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppTransaction &&
          runtimeType == other.runtimeType &&
          transactionId == other.transactionId;

  @override
  int get hashCode => transactionId.hashCode;
}

DateTime _toDateTime(dynamic value) {
  if (value is Timestamp) return value.toDate();
  if (value is DateTime) return value;
  if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
  return DateTime.now();
}
