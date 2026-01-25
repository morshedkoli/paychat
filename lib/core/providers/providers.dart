import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart' as auth;
import '../models/user.dart' as models;
import '../models/chat.dart';
import '../models/message.dart';
import '../models/transaction.dart' as txn;
import '../models/group_expense.dart';
import '../services/auth_service.dart';
import '../services/firestore_service.dart';
import '../services/biometric_service.dart';

// ===== SERVICE PROVIDERS =====

/// Auth Service Provider
final authServiceProvider = Provider<AuthService>((ref) => AuthService());

/// Firestore Service Provider
final firestoreServiceProvider =
    Provider<FirestoreService>((ref) => FirestoreService());

/// Biometric Service Provider
final biometricServiceProvider =
    Provider<BiometricService>((ref) => BiometricService());

// ===== AUTH PROVIDERS =====

/// Current Firebase User Stream
final firebaseAuthStateProvider = StreamProvider<auth.User?>((ref) {
  final authService = ref.watch(authServiceProvider);
  return authService.authStateChanges;
});

/// Current User Model Provider
final currentUserProvider = FutureProvider<models.User?>((ref) async {
  final firebaseUser = ref.watch(firebaseAuthStateProvider).value;
  if (firebaseUser == null) return null;

  final authService = ref.watch(authServiceProvider);
  return await authService.getUserById(firebaseUser.uid);
});

// ===== BIOMETRIC PROVIDERS =====

/// Biometric enabled state provider
final biometricEnabledProvider = FutureProvider<bool>((ref) async {
  final biometricService = ref.watch(biometricServiceProvider);
  return await biometricService.isBiometricEnabled();
});

/// Biometric available (device supports it)
final biometricAvailableProvider = FutureProvider<bool>((ref) async {
  final biometricService = ref.watch(biometricServiceProvider);
  return await biometricService.canCheckBiometrics();
});

// ===== CHAT PROVIDERS =====

/// Stream of chats for current user
final chatsProvider = StreamProvider<List<Chat>>((ref) {
  final firebaseUser = ref.watch(firebaseAuthStateProvider).value;
  if (firebaseUser == null) return Stream.value([]);

  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.getChatsStream(firebaseUser.uid);
});

/// Messages for a specific chat
final messagesProvider =
    StreamProvider.family<List<Message>, String>((ref, chatId) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.getMessagesStream(chatId);
});

// ===== TRANSACTION PROVIDERS =====

/// Transactions for a specific chat
final transactionsProvider =
    StreamProvider.family<List<txn.Transaction>, String>((ref, chatId) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.getTransactionsStream(chatId);
});

/// All transactions for current user (across all chats)
final userTransactionsProvider = StreamProvider<List<txn.Transaction>>((ref) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  final chatsStream = ref.watch(chatsProvider.stream);

  final controller = StreamController<List<txn.Transaction>>();
  final transactions = <String, List<txn.Transaction>>{};
  final transactionSubs = <String, StreamSubscription<List<txn.Transaction>>>{};
  StreamSubscription<List<Chat>>? chatsSub;

  void emitTransactions() {
    final allTxns = <txn.Transaction>[];
    for (final txnList in transactions.values) {
      allTxns.addAll(txnList);
    }
    // Sort by creation date, most recent first
    allTxns.sort((a, b) => b.createdAt.compareTo(a.createdAt));

    if (!controller.isClosed) {
      controller.add(allTxns);
    }
  }

  chatsSub = chatsStream.listen(
    (chats) {
      final chatIds = chats.map((chat) => chat.id).toSet();
      final staleIds =
          transactionSubs.keys.where((id) => !chatIds.contains(id));

      // Remove subscriptions for chats that no longer exist
      for (final chatId in staleIds.toList()) {
        transactionSubs[chatId]?.cancel();
        transactionSubs.remove(chatId);
        transactions.remove(chatId);
      }

      // Add subscriptions for new chats
      for (final chat in chats) {
        if (transactionSubs.containsKey(chat.id)) continue;

        final sub = firestoreService.getTransactionsStream(chat.id).listen(
          (txnList) {
            transactions[chat.id] = txnList;
            emitTransactions();
          },
          onError: (error) {
            // On error, set empty list for this chat and continue
            print('Error loading transactions for chat ${chat.id}: $error');
            transactions[chat.id] = [];
            emitTransactions();
          },
        );
        transactionSubs[chat.id] = sub;
      }

      emitTransactions();
    },
    onError: (error) {
      print('Error loading chats for transactions: $error');
      if (!controller.isClosed) {
        controller.add([]);
      }
    },
  );

  ref.onDispose(() {
    chatsSub?.cancel();
    for (final sub in transactionSubs.values) {
      sub.cancel();
    }
    controller.close();
  });

  return controller.stream;
});

/// Balance summary provider (totals from all chats)
final balanceSummaryProvider = StreamProvider<Map<String, double>>((ref) {
  final firebaseUser = ref.watch(firebaseAuthStateProvider).value;
  if (firebaseUser == null) {
    return Stream.value(
      {'youOwe': 0.0, 'owedToYou': 0.0, 'netBalance': 0.0},
    );
  }

  final firestoreService = ref.watch(firestoreServiceProvider);
  final chatsStream = ref.watch(chatsProvider.stream);
  final controller = StreamController<Map<String, double>>();
  final balances = <String, double>{};
  final balanceSubs = <String, StreamSubscription<double>>{};
  StreamSubscription<List<Chat>>? chatsSub;

  void emitSummary() {
    double youOwe = 0.0;
    double owedToYou = 0.0;

    for (final balance in balances.values) {
      if (balance > 0) {
        owedToYou += balance;
      } else if (balance < 0) {
        youOwe += balance.abs();
      }
    }

    if (!controller.isClosed) {
      controller.add({
        'youOwe': youOwe,
        'owedToYou': owedToYou,
        'netBalance': owedToYou - youOwe,
      });
    }
  }

  chatsSub = chatsStream.listen(
    (chats) {
      final chatIds = chats.map((chat) => chat.id).toSet();
      final staleIds = balanceSubs.keys.where((id) => !chatIds.contains(id));

      for (final chatId in staleIds.toList()) {
        balanceSubs[chatId]?.cancel();
        balanceSubs.remove(chatId);
        balances.remove(chatId);
      }

      for (final chat in chats) {
        if (balanceSubs.containsKey(chat.id)) continue;
        final sub =
            firestoreService.getBalanceStream(chat.id, firebaseUser.uid).listen(
          (balance) {
            balances[chat.id] = balance;
            emitSummary();
          },
          onError: (_) {
            balances[chat.id] = 0.0;
            emitSummary();
          },
        );
        balanceSubs[chat.id] = sub;
      }

      emitSummary();
    },
    onError: (_) {
      if (!controller.isClosed) {
        controller.addError('balance_summary_error');
      }
    },
  );

  ref.onDispose(() {
    chatsSub?.cancel();
    for (final sub in balanceSubs.values) {
      sub.cancel();
    }
    controller.close();
  });

  return controller.stream;
});

// ===== GROUP EXPENSE PROVIDERS =====

/// Group expenses for a specific chat
final groupExpensesProvider =
    StreamProvider.family<List<GroupExpense>, String>((ref, chatId) {
  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.getGroupExpensesStream(chatId);
});

// ===== BALANCE PROVIDER =====

/// Balance for a specific chat
final balanceProvider = StreamProvider.family<double, String>((ref, chatId) {
  final firebaseUser = ref.watch(firebaseAuthStateProvider).value;
  if (firebaseUser == null) return Stream.value(0.0);

  final firestoreService = ref.watch(firestoreServiceProvider);
  return firestoreService.getBalanceStream(chatId, firebaseUser.uid);
});
