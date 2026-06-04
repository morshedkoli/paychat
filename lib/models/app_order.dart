import 'package:cloud_firestore/cloud_firestore.dart';

enum OrderStatus {
  pending('PENDING'),
  processing('PROCESSING'),
  delivered('DELIVERED');

  final String value;
  const OrderStatus(this.value);

  factory OrderStatus.fromString(String? value) {
    return OrderStatus.values.firstWhere(
      (e) => e.value == value,
      orElse: () => OrderStatus.pending,
    );
  }

  String get label {
    switch (this) {
      case OrderStatus.pending:
        return 'Pending';
      case OrderStatus.processing:
        return 'Processing';
      case OrderStatus.delivered:
        return 'Delivered';
    }
  }
}

class AppOrder {
  final String id;
  final String threadId;
  final String serviceId;
  final String serviceTitle;
  final String serviceDescription;
  final double price;
  final String buyerId;
  final String sellerId;
  final String buyerName;
  final String sellerName;
  final OrderStatus status;
  final String buyerNote;
  final Map<String, String> requirementsAnswers;
  final DateTime createdAt;
  final DateTime updatedAt;

  AppOrder({
    required this.id,
    required this.threadId,
    required this.serviceId,
    required this.serviceTitle,
    required this.serviceDescription,
    required this.price,
    required this.buyerId,
    required this.sellerId,
    required this.buyerName,
    required this.sellerName,
    required this.status,
    required this.buyerNote,
    this.requirementsAnswers = const {},
    required this.createdAt,
    required this.updatedAt,
  });

  factory AppOrder.fromMap(Map<String, dynamic> map) {
    return AppOrder(
      id: map['id'] as String? ?? '',
      threadId: map['threadId'] as String? ?? '',
      serviceId: map['serviceId'] as String? ?? '',
      serviceTitle: map['serviceTitle'] as String? ?? '',
      serviceDescription: map['serviceDescription'] as String? ?? '',
      price: (map['price'] as num?)?.toDouble() ?? 0.0,
      buyerId: map['buyerId'] as String? ?? '',
      sellerId: map['sellerId'] as String? ?? '',
      buyerName: map['buyerName'] as String? ?? '',
      sellerName: map['sellerName'] as String? ?? '',
      status: OrderStatus.fromString(map['status'] as String?),
      buyerNote: map['buyerNote'] as String? ?? '',
      requirementsAnswers: Map<String, String>.from(map['requirementsAnswers'] ?? {}),
      createdAt: _toDateTime(map['createdAt']),
      updatedAt: _toDateTime(map['updatedAt']),
    );
  }

  factory AppOrder.fromFirestore(DocumentSnapshot doc) {
    return AppOrder.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'threadId': threadId,
      'serviceId': serviceId,
      'serviceTitle': serviceTitle,
      'serviceDescription': serviceDescription,
      'price': price,
      'buyerId': buyerId,
      'sellerId': sellerId,
      'buyerName': buyerName,
      'sellerName': sellerName,
      'status': status.value,
      'buyerNote': buyerNote,
      'requirementsAnswers': requirementsAnswers,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }

  AppOrder copyWith({
    String? id,
    String? threadId,
    String? serviceId,
    String? serviceTitle,
    String? serviceDescription,
    double? price,
    String? buyerId,
    String? sellerId,
    String? buyerName,
    String? sellerName,
    OrderStatus? status,
    String? buyerNote,
    Map<String, String>? requirementsAnswers,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return AppOrder(
      id: id ?? this.id,
      threadId: threadId ?? this.threadId,
      serviceId: serviceId ?? this.serviceId,
      serviceTitle: serviceTitle ?? this.serviceTitle,
      serviceDescription: serviceDescription ?? this.serviceDescription,
      price: price ?? this.price,
      buyerId: buyerId ?? this.buyerId,
      sellerId: sellerId ?? this.sellerId,
      buyerName: buyerName ?? this.buyerName,
      sellerName: sellerName ?? this.sellerName,
      status: status ?? this.status,
      buyerNote: buyerNote ?? this.buyerNote,
      requirementsAnswers: requirementsAnswers ?? this.requirementsAnswers,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppOrder &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;
}

DateTime _toDateTime(dynamic value) {
  if (value is Timestamp) return value.toDate();
  if (value is DateTime) return value;
  if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
  return DateTime.now();
}
