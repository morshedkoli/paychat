import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:paychat/models/app_message.dart';
import 'package:paychat/models/app_order.dart';
import 'package:uuid/uuid.dart';

class OrderRepository {
  final FirebaseFirestore firestore;

  OrderRepository({required this.firestore});

  late final CollectionReference<Map<String, dynamic>> _orders =
      firestore.collection('orders');
  late final CollectionReference<Map<String, dynamic>> _messages =
      firestore.collection('messages');
  late final CollectionReference<Map<String, dynamic>> _threads =
      firestore.collection('threads');

  /// Watch all orders for a specific thread.
  Stream<List<AppOrder>> watchThreadOrders(String threadId) {
    return _orders
        .where('threadId', isEqualTo: threadId)
        .orderBy('createdAt', descending: true)
        .snapshots()
        .map((snap) =>
            snap.docs.map((d) => AppOrder.fromMap(d.data())).toList());
  }

  /// Watch orders where current user is buyer or seller (active orders only).
  Stream<List<AppOrder>> watchActiveUserOrders(String uid) {
    // Firestore doesn't support OR queries across different fields natively,
    // so we query by sellerId and merge client-side if needed.
    // For simplicity, we use buyerId here; seller orders are shown per-thread.
    return _orders
        .where('buyerId', isEqualTo: uid)
        .where('status', whereIn: ['PENDING', 'PROCESSING'])
        .orderBy('createdAt', descending: true)
        .snapshots()
        .map((snap) =>
            snap.docs.map((d) => AppOrder.fromMap(d.data())).toList());
  }

  /// Place a new order and post an ORDER message into the thread.
  Future<AppOrder> placeOrder({
    required String threadId,
    required String serviceId,
    required String serviceTitle,
    required String serviceDescription,
    required double price,
    required String buyerId,
    required String sellerId,
    required String buyerName,
    required String sellerName,
    required String buyerNote,
    Map<String, String> requirementsAnswers = const {},
  }) async {
    const uuid = Uuid();
    final now = DateTime.now();
    final orderId = uuid.v4();

    final order = AppOrder(
      id: orderId,
      threadId: threadId,
      serviceId: serviceId,
      serviceTitle: serviceTitle,
      serviceDescription: serviceDescription,
      price: price,
      buyerId: buyerId,
      sellerId: sellerId,
      buyerName: buyerName,
      sellerName: sellerName,
      status: OrderStatus.pending,
      buyerNote: buyerNote,
      requirementsAnswers: requirementsAnswers,
      createdAt: now,
      updatedAt: now,
    );

    // Write order document
    await _orders.doc(orderId).set(order.toMap());

    // Post an ORDER message into the thread chat
    final msgId = uuid.v4();
    final previewText = '$buyerName ordered "$serviceTitle" • ৳${price.toStringAsFixed(0)}';
    final message = AppMessage(
      id: msgId,
      threadId: threadId,
      senderId: buyerId,
      text: previewText,
      createdAt: now,
      type: MessageContentType.order,
      isRead: false,
      orderId: orderId,
    );

    await _messages.doc(msgId).set(message.toMap());

    // Update thread preview
    await _threads.doc(threadId).update({
      'lastMessagePreview': previewText,
      'updatedAt': Timestamp.now(),
    });

    return order;
  }

  /// Update the status of an order.
  /// walletBalance crediting is handled server-side by the onOrderStatusChange
  /// Cloud Function (which runs with admin privileges), so no client-side
  /// cross-user write is needed here.
  Future<void> updateOrderStatus({
    required String orderId,
    required String sellerId,
    required double price,
    required OrderStatus newStatus,
  }) async {
    await _orders.doc(orderId).update({
      'status': newStatus.value,
      'updatedAt': FieldValue.serverTimestamp(),
    });
  }

  /// Fetch a single order by ID.
  Future<AppOrder?> fetchOrder(String orderId) async {
    final doc = await _orders.doc(orderId).get();
    if (!doc.exists) return null;
    return AppOrder.fromMap(doc.data()!);
  }
}
