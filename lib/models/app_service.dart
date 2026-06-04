import 'package:cloud_firestore/cloud_firestore.dart';

class AppService {
  final String id;
  final String sellerId;
  final String title;
  final String description;
  final double price;
  final String category;
  final bool isActive;
  final List<String> requiredFields;
  final DateTime createdAt;
  final DateTime updatedAt;

  AppService({
    required this.id,
    required this.sellerId,
    required this.title,
    required this.description,
    required this.price,
    required this.category,
    required this.isActive,
    this.requiredFields = const [],
    required this.createdAt,
    required this.updatedAt,
  });

  factory AppService.fromMap(Map<String, dynamic> map) {
    return AppService(
      id: map['id'] as String? ?? '',
      sellerId: map['sellerId'] as String? ?? '',
      title: map['title'] as String? ?? '',
      description: map['description'] as String? ?? '',
      price: (map['price'] as num?)?.toDouble() ?? 0.0,
      category: map['category'] as String? ?? '',
      isActive: map['isActive'] as bool? ?? true,
      requiredFields: List<String>.from(map['requiredFields'] ?? []),
      createdAt: _toDateTime(map['createdAt']),
      updatedAt: _toDateTime(map['updatedAt']),
    );
  }

  factory AppService.fromFirestore(DocumentSnapshot doc) {
    return AppService.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'sellerId': sellerId,
      'title': title,
      'description': description,
      'price': price,
      'category': category,
      'isActive': isActive,
      'requiredFields': requiredFields,
      'createdAt': Timestamp.fromDate(createdAt),
      'updatedAt': Timestamp.fromDate(updatedAt),
    };
  }

  AppService copyWith({
    String? id,
    String? sellerId,
    String? title,
    String? description,
    double? price,
    String? category,
    bool? isActive,
    List<String>? requiredFields,
    DateTime? createdAt,
    DateTime? updatedAt,
  }) {
    return AppService(
      id: id ?? this.id,
      sellerId: sellerId ?? this.sellerId,
      title: title ?? this.title,
      description: description ?? this.description,
      price: price ?? this.price,
      category: category ?? this.category,
      isActive: isActive ?? this.isActive,
      requiredFields: requiredFields ?? this.requiredFields,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppService &&
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
