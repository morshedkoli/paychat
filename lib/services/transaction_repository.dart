import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:paychat/models/app_transaction.dart';
import 'package:uuid/uuid.dart';

class TransactionRepository {
  final FirebaseFirestore firestore;

  TransactionRepository({required this.firestore});

  late final CollectionReference<Map<String, dynamic>> _transactions =
      firestore.collection('transactions');

  /// Observe transactions for a user
  Stream<List<AppTransaction>> observeTransactions(String userId) {
    return _transactions
        .where('userId', isEqualTo: userId)
        .orderBy('createdAt', descending: true)
        .snapshots()
        .map((snapshot) {
      return snapshot.docs
          .map((doc) => AppTransaction.fromMap(doc.data()))
          .toList();
    });
  }

  /// Get a specific transaction
  Future<AppTransaction?> getTransactionById(String transactionId) async {
    final doc = await _transactions.doc(transactionId).get();
    if (!doc.exists) return null;
    return AppTransaction.fromMap(doc.data()!);
  }

  /// Add a new transaction
  Future<void> addTransaction({
    required String userId,
    required String contactName,
    required String contactPhone,
    required TransactionType type,
    required double amount,
    required String note,
    String? transactionId,
    TransactionStatus status = TransactionStatus.completed,
    WriteBatch? batch,
  }) async {
    final id = transactionId ?? const Uuid().v4();
    final now = DateTime.now();
    final transaction = AppTransaction(
      transactionId: id,
      userId: userId,
      contactName: contactName.trim(),
      contactPhone: contactPhone.trim(),
      type: type,
      amount: amount,
      note: note.trim(),
      createdAt: now,
      updatedAt: now,
      status: status,
    );

    if (batch != null) {
      batch.set(_transactions.doc(transaction.transactionId), transaction.toMap());
    } else {
      await _transactions.doc(transaction.transactionId).set(transaction.toMap());
    }
  }

  /// Update the status of a transaction
  Future<void> updateTransactionStatus({
    required String transactionId,
    required TransactionStatus status,
    WriteBatch? batch,
  }) async {
    final updates = {
      'status': status.value,
      'updatedAt': Timestamp.now(),
    };

    if (batch != null) {
      batch.update(_transactions.doc(transactionId), updates);
    } else {
      await _transactions.doc(transactionId).update(updates);
    }
  }

}


