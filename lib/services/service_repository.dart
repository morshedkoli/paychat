import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:paychat/models/app_service.dart';
import 'package:uuid/uuid.dart';

class ServiceRepository {
  final FirebaseFirestore firestore;

  ServiceRepository({required this.firestore});

  CollectionReference<Map<String, dynamic>> _servicesCol(String uid) =>
      firestore.collection('users').doc(uid).collection('services');

  /// Watch a user's services in real-time.
  Stream<List<AppService>> watchUserServices(String uid) {
    return _servicesCol(uid)
        .snapshots()
        .map((snap) {
          final list = snap.docs.map((d) => AppService.fromMap(d.data())).toList();
          // Sort in-memory — no composite index required
          list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
          return list;
        });
  }

  /// One-shot fetch of a user's active services (for order picker).
  Future<List<AppService>> fetchActiveServices(String uid) async {
    final snap = await _servicesCol(uid)
        .where('isActive', isEqualTo: true)
        .get();
    // Sort in-memory — avoids requiring a composite index on (isActive, createdAt)
    final list = snap.docs.map((d) => AppService.fromMap(d.data())).toList();
    list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return list;
  }

  /// Add a new service.
  Future<AppService> addService({
    required String sellerId,
    required String title,
    required String description,
    required double price,
    required String category,
    List<String> requiredFields = const [],
  }) async {
    const uuid = Uuid();
    final now = DateTime.now();
    final service = AppService(
      id: uuid.v4(),
      sellerId: sellerId,
      title: title.trim(),
      description: description.trim(),
      price: price,
      category: category.trim(),
      isActive: true,
      requiredFields: requiredFields,
      createdAt: now,
      updatedAt: now,
    );
    await _servicesCol(sellerId).doc(service.id).set(service.toMap());
    return service;
  }

  /// Update an existing service.
  Future<void> updateService({
    required String sellerId,
    required String serviceId,
    String? title,
    String? description,
    double? price,
    String? category,
    bool? isActive,
    List<String>? requiredFields,
  }) async {
    final updates = <String, dynamic>{
      'updatedAt': FieldValue.serverTimestamp(),
    };
    if (title != null) updates['title'] = title.trim();
    if (description != null) updates['description'] = description.trim();
    if (price != null) updates['price'] = price;
    if (category != null) updates['category'] = category.trim();
    if (isActive != null) updates['isActive'] = isActive;
    if (requiredFields != null) updates['requiredFields'] = requiredFields;
    await _servicesCol(sellerId).doc(serviceId).update(updates);
  }

  /// Delete a service.
  Future<void> deleteService({
    required String sellerId,
    required String serviceId,
  }) async {
    await _servicesCol(sellerId).doc(serviceId).delete();
  }
}
