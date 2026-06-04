import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:paychat/models/app_message.dart';
import 'package:paychat/models/app_order.dart';
import 'package:paychat/models/app_service.dart';
import 'package:paychat/models/app_transaction.dart';
import 'package:paychat/services/auth_service.dart';
import 'package:paychat/services/chat_repository.dart';
import 'package:paychat/services/file_transfer_service.dart';
import 'package:paychat/services/order_repository.dart';
import 'package:paychat/services/service_repository.dart';
import 'package:paychat/services/transaction_repository.dart';
import 'package:paychat/services/user_repository.dart';

/// Firebase Auth Provider
final firebaseAuthProvider = Provider((ref) => FirebaseAuth.instance);

/// Firestore Provider
final firestoreProvider = Provider((ref) => FirebaseFirestore.instance);

/// Firebase Storage Provider
final firebaseStorageProvider = Provider((ref) => FirebaseStorage.instance);

/// Auth Service Provider
final authServiceProvider = Provider((ref) {
  final auth = ref.watch(firebaseAuthProvider);
  return AuthService(auth: auth);
});

/// User Repository Provider
final userRepositoryProvider = Provider((ref) {
  final firestore = ref.watch(firestoreProvider);
  final storage = ref.watch(firebaseStorageProvider);
  return UserRepository(firestore: firestore, storage: storage);
});

/// Chat Repository Provider
final chatRepositoryProvider = Provider((ref) {
  final firestore = ref.watch(firestoreProvider);
  return ChatRepository(firestore: firestore);
});

/// File Transfer Service Provider
final fileTransferServiceProvider = Provider((ref) {
  return FileTransferService();
});

/// Transaction Repository Provider
final transactionRepositoryProvider = Provider((ref) {
  final firestore = ref.watch(firestoreProvider);
  return TransactionRepository(firestore: firestore);
});

/// Current Firebase User Stream
final currentUserStreamProvider = StreamProvider<User?>((ref) {
  final authService = ref.watch(authServiceProvider);
  return authService.authStateChanges();
});

/// User Threads Stream Provider
final userThreadsProvider = StreamProvider.family<List<Map<String, dynamic>>, String>((ref, userId) {
  final chatRepo = ref.watch(chatRepositoryProvider);
  return chatRepo.watchUserThreads(userId);
});

/// Thread Messages Stream Provider
final threadMessagesProvider = StreamProvider.family<List<AppMessage>, String>((ref, threadId) {
  final chatRepo = ref.watch(chatRepositoryProvider);
  return chatRepo.watchThreadMessages(threadId);
});

/// User Transactions Stream Provider
final userTransactionsProvider = StreamProvider.family<List<AppTransaction>, String>((ref, userId) {
  final transRepo = ref.watch(transactionRepositoryProvider);
  return transRepo.observeTransactions(userId);
});

/// Service Repository Provider
final serviceRepositoryProvider = Provider((ref) {
  final firestore = ref.watch(firestoreProvider);
  return ServiceRepository(firestore: firestore);
});

/// Order Repository Provider
final orderRepositoryProvider = Provider((ref) {
  final firestore = ref.watch(firestoreProvider);
  return OrderRepository(firestore: firestore);
});

/// User Services Stream Provider (by seller UID)
final userServicesProvider = StreamProvider.family<List<AppService>, String>((ref, uid) {
  final serviceRepo = ref.watch(serviceRepositoryProvider);
  return serviceRepo.watchUserServices(uid);
});

/// Thread Orders Stream Provider
final threadOrdersProvider = StreamProvider.family<List<AppOrder>, String>((ref, threadId) {
  final orderRepo = ref.watch(orderRepositoryProvider);
  return orderRepo.watchThreadOrders(threadId);
});

/// User Presence Stream Provider
final userPresenceProvider = StreamProvider.family<Map<String, dynamic>?, String>((ref, userId) {
  final firestore = ref.watch(firestoreProvider);
  return firestore.collection('users').doc(userId).snapshots().map((doc) => doc.data());
});
