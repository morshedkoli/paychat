import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/user.dart';
import '../models/chat.dart';
import '../models/message.dart';
import '../models/transaction.dart' as txn;
import '../models/group_expense.dart';

/// Firestore service for all database operations
class FirestoreService {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  // ===== CHAT OPERATIONS =====

  /// Create a new 1-to-1 chat
  Future<String> createChat({
    required String currentUserId,
    required String otherUserId,
    bool isGroup = false,
  }) async {
    try {
      // Check if chat already exists
      final existingChat = await _firestore
          .collection('chats')
          .where('members', arrayContains: currentUserId)
          .where('isGroup', isEqualTo: false)
          .get();

      for (var doc in existingChat.docs) {
        final members = List<String>.from(doc.data()['members'] ?? []);
        if (members.contains(otherUserId)) {
          return doc.id; // Return existing chat ID
        }
      }

      // Create new chat
      final chatDoc = await _firestore.collection('chats').add({
        'members': [currentUserId, otherUserId],
        'isGroup': false,
        'createdBy': currentUserId,
        'createdAt': Timestamp.now(),
        'lastMessage': '',
        'lastMessageTimestamp': Timestamp.now(),
      });

      return chatDoc.id;
    } catch (e) {
      print('Error creating chat: $e');
      rethrow;
    }
  }

  /// Create a group chat
  Future<String> createGroupChat({
    required String currentUserId,
    required List<String> memberIds,
    required String groupName,
  }) async {
    try {
      final chatDoc = await _firestore.collection('chats').add({
        'members': [currentUserId, ...memberIds],
        'isGroup': true,
        'groupName': groupName,
        'createdBy': currentUserId,
        'createdAt': Timestamp.now(),
        'lastMessage': '',
        'lastMessageTimestamp': Timestamp.now(),
      });

      return chatDoc.id;
    } catch (e) {
      print('Error creating group chat: $e');
      rethrow;
    }
  }

  /// Find or create a user by phone number and create chat
  Future<String> createChatByPhone({
    required String currentUserId,
    required String phoneNumber,
    required String contactName,
  }) async {
    try {
      // Normalize phone number (remove spaces, dashes)
      final normalizedPhone = phoneNumber.replaceAll(RegExp(r'[\s\-\(\)]'), '');

      // Check if user exists with this phone number
      final existingUserQuery = await _firestore
          .collection('users')
          .where('phoneNumber', isEqualTo: normalizedPhone)
          .limit(1)
          .get();

      String otherUserId;

      if (existingUserQuery.docs.isNotEmpty) {
        // User exists, use their ID
        otherUserId = existingUserQuery.docs.first.id;
      } else {
        // Create a placeholder user document
        final newUserDoc = await _firestore.collection('users').add({
          'displayName': contactName,
          'phoneNumber': normalizedPhone,
          'email': '',
          'photoUrl': null,
          'isRegistered': false, // Not yet registered on PayChat
          'createdAt': Timestamp.now(),
        });
        otherUserId = newUserDoc.id;
      }

      // Now create the chat with the user ID
      return await createChat(
        currentUserId: currentUserId,
        otherUserId: otherUserId,
      );
    } catch (e) {
      print('Error creating chat by phone: $e');
      rethrow;
    }
  }

  /// Stream of chats for current user
  Stream<List<Chat>> getChatsStream(String userId) {
    return _firestore
        .collection('chats')
        .where('members', arrayContains: userId)
        // .orderBy('lastMessageTimestamp', descending: true) // Removed to avoid index errors
        .snapshots()
        .asyncMap((snapshot) async {
      List<Chat> chats = [];

      for (var doc in snapshot.docs) {
        try {
          final data = doc.data();
          final members = List<String>.from(data['members'] ?? []);

          // For 1-to-1 chats, get the other user
          if (data['isGroup'] == false) {
            final otherUserId =
                members.firstWhere((id) => id != userId, orElse: () => '');
            if (otherUserId.isEmpty) continue;

            final userDoc =
                await _firestore.collection('users').doc(otherUserId).get();

            if (userDoc.exists) {
              final otherUser = User.fromFirestore(userDoc);

              // Get balance for this chat
              double balance = 0.0;
              try {
                final balanceDoc = await _firestore
                    .collection('balances')
                    .doc('${doc.id}_$userId')
                    .get();
                if (balanceDoc.exists) {
                  balance = (balanceDoc.data()?['balance'] ?? 0).toDouble();
                }
              } catch (e) {
                print('Error getting balance: $e');
              }

              chats.add(Chat(
                id: doc.id,
                user: otherUser,
                lastMessage: data['lastMessage'] ?? '',
                lastActive:
                    (data['lastMessageTimestamp'] as Timestamp?)?.toDate() ??
                        DateTime.now(),
                balance: balance,
                unreadCount: 0,
              ));
            }
          }
        } catch (e) {
          print('Error parsing chat ${doc.id}: $e');
        }
      }

      // Client-side sorting
      chats.sort((a, b) => b.lastActive.compareTo(a.lastActive));

      return chats;
    });
  }

  /// Get a single chat by ID
  Future<Chat?> getChat(String chatId, String currentUserId) async {
    try {
      final doc = await _firestore.collection('chats').doc(chatId).get();
      if (!doc.exists) return null;

      final data = doc.data()!;
      final members = List<String>.from(data['members'] ?? []);

      // Only supporting 1-on-1 chats for now via this method
      if (data['isGroup'] == true) return null;

      String otherUserId =
          members.firstWhere((id) => id != currentUserId, orElse: () => '');

      // Handle self-chat or fallback
      if (otherUserId.isEmpty && members.isNotEmpty) {
        otherUserId = members.first;
      }

      if (otherUserId.isEmpty) return null;

      final userDoc =
          await _firestore.collection('users').doc(otherUserId).get();
      if (!userDoc.exists) return null;

      final otherUser = User.fromFirestore(userDoc);

      // Get balance
      double balance = 0.0;
      try {
        final balanceDoc = await _firestore
            .collection('balances')
            .doc('${doc.id}_$currentUserId')
            .get();
        if (balanceDoc.exists) {
          balance = (balanceDoc.data()?['balance'] ?? 0).toDouble();
        }
      } catch (e) {
        print('Error getting balance: $e');
      }

      return Chat(
        id: doc.id,
        user: otherUser,
        lastMessage: data['lastMessage'] ?? '',
        lastActive: (data['lastMessageTimestamp'] as Timestamp?)?.toDate() ??
            DateTime.now(),
        unreadCount: 0,
        balance: balance,
      );
    } catch (e) {
      print('Error getting chat: $e');
      return null;
    }
  }

  /// Send a text message
  Future<void> sendMessage({
    required String chatId,
    required String senderId,
    required String content,
    bool isEncrypted = false,
  }) async {
    try {
      await _firestore
          .collection('chats')
          .doc(chatId)
          .collection('messages')
          .add({
        'senderId': senderId,
        'content': content,
        'timestamp': Timestamp.now(),
        'type': 'text',
        'isEncrypted': isEncrypted,
        'chatId': chatId,
      });

      // Update chat's last message
      await _firestore.collection('chats').doc(chatId).update({
        'lastMessage': content,
        'lastMessageTimestamp': Timestamp.now(),
      });
    } catch (e) {
      print('Error sending message: $e');
      rethrow;
    }
  }

  /// Stream of messages for a chat
  Stream<List<Message>> getMessagesStream(String chatId) {
    return _firestore
        .collection('chats')
        .doc(chatId)
        .collection('messages')
        .orderBy('timestamp', descending: false)
        .snapshots()
        .map((snapshot) {
      return snapshot.docs.map((doc) {
        final data = doc.data();
        return Message(
          id: doc.id,
          senderId: data['senderId'] ?? '',
          content: data['content'] ?? '',
          timestamp: (data['timestamp'] as Timestamp).toDate(),
          type: data['type'] == 'transaction'
              ? MessageType.transaction
              : MessageType.text,
          amount: data['amount']?.toDouble(),
          status: data['status'] != null
              ? TransactionStatus.values.firstWhere(
                  (e) => e.name == data['status'],
                  orElse: () => TransactionStatus.pending,
                )
              : null,
        );
      }).toList();
    });
  }

  // ===== TRANSACTION OPERATIONS =====

  /// Create a transaction atomically with the chat message
  Future<String> createTransaction({
    required String chatId,
    required String senderId,
    required String receiverId,
    required double amount,
    required String note,
    String currency = 'BDT',
  }) async {
    try {
      // Generate IDs upfront
      final transactionRef = _firestore.collection('transactions').doc();
      final messageRef = _firestore
          .collection('chats')
          .doc(chatId)
          .collection('messages')
          .doc();
      final chatRef = _firestore.collection('chats').doc(chatId);

      // Check if receiver is registered
      final receiverDoc =
          await _firestore.collection('users').doc(receiverId).get();
      final isReceiverRegistered =
          receiverDoc.exists && (receiverDoc.data()?['isRegistered'] ?? false);

      final status = isReceiverRegistered ? 'pending' : 'autoApproved';

      await _firestore.runTransaction((transaction) async {
        // IMPORTANT: All reads must come before writes in Firestore transactions
        // Pre-declare balance refs
        final senderBalanceRef =
            _firestore.collection('balances').doc('${chatId}_$senderId');
        final receiverBalanceRef =
            _firestore.collection('balances').doc('${chatId}_$receiverId');
        
        // Read balance docs first (only needed for autoApproved)
        double senderCurrentBalance = 0.0;
        double receiverCurrentBalance = 0.0;
        
        if (status == 'autoApproved') {
          final senderBalanceDoc = await transaction.get(senderBalanceRef);
          final receiverBalanceDoc = await transaction.get(receiverBalanceRef);

          senderCurrentBalance = senderBalanceDoc.exists
              ? (senderBalanceDoc.data()!['balance'] ?? 0).toDouble()
              : 0.0;
          receiverCurrentBalance = receiverBalanceDoc.exists
              ? (receiverBalanceDoc.data()!['balance'] ?? 0).toDouble()
              : 0.0;
        }

        // Now perform all writes
        
        // Create Transaction
        transaction.set(transactionRef, {
          'chatId': chatId,
          'senderId': senderId,
          'receiverId': receiverId,
          'amount': amount,
          'note': note,
          'currency': currency,
          'status': status,
          'createdAt': Timestamp.now(),
          if (status == 'autoApproved') 'approvedAt': Timestamp.now(),
        });

        // Add Message
        transaction.set(messageRef, {
          'senderId': senderId,
          'content': note,
          'timestamp': Timestamp.now(),
          'type': 'transaction',
          'amount': amount,
          'status': status,
          'transactionId': transactionRef.id,
          'chatId': chatId,
        });

        // Update Chat last message
        transaction.update(chatRef, {
          'lastMessage': 'Transaction: $currency${amount.toStringAsFixed(0)}',
          'lastMessageTimestamp': Timestamp.now(),
        });

        // If auto-approved, update balances immediately
        if (status == 'autoApproved') {
          final senderBalanceRef =
              _firestore.collection('balances').doc('${chatId}_$senderId');
          final receiverBalanceRef =
              _firestore.collection('balances').doc('${chatId}_$receiverId');

          final senderBalanceDoc = await transaction.get(senderBalanceRef);
          final receiverBalanceDoc = await transaction.get(receiverBalanceRef);

          final senderCurrentBalance = senderBalanceDoc.exists
              ? (senderBalanceDoc.data()!['balance'] ?? 0).toDouble()
              : 0.0;
          final receiverCurrentBalance = receiverBalanceDoc.exists
              ? (receiverBalanceDoc.data()!['balance'] ?? 0).toDouble()
              : 0.0;

          // Update: Sender +amount (They paid, so they are owed/balance increases)
          transaction.set(
              senderBalanceRef,
              {
                'balance': senderCurrentBalance + amount,
                'lastUpdated': Timestamp.now(),
              },
              SetOptions(merge: true));

          // Update: Receiver -amount (They owe, so balance decreases)
          transaction.set(
              receiverBalanceRef,
              {
                'balance': receiverCurrentBalance - amount,
                'lastUpdated': Timestamp.now(),
              },
              SetOptions(merge: true));
        }
      });

      return transactionRef.id;
    } catch (e) {
      print('Error creating transaction: $e');
      rethrow;
    }
  }

  /// Approve a transaction and update balances atomically
  Future<void> approveTransaction(String transactionId) async {
    try {
      final transactionRef =
          _firestore.collection('transactions').doc(transactionId);

      await _firestore.runTransaction((transaction) async {
        // 1. Read Transaction
        final txnSnapshot = await transaction.get(transactionRef);
        if (!txnSnapshot.exists) {
          throw Exception("Transaction not found");
        }
        final txnData = txnSnapshot.data()!;

        // Check if already processed to avoid double counting
        if (txnData['status'] == 'approved') {
          return; // Already approved
        }

        final chatId = txnData['chatId'];
        final senderId = txnData['senderId'];
        final receiverId = txnData['receiverId'];
        final amount = (txnData['amount'] ?? 0).toDouble();

        // 2. Read Balance Docs (Sender & Receiver)
        final senderBalanceRef =
            _firestore.collection('balances').doc('${chatId}_$senderId');
        final receiverBalanceRef =
            _firestore.collection('balances').doc('${chatId}_$receiverId');

        final senderBalanceDoc = await transaction.get(senderBalanceRef);
        final receiverBalanceDoc = await transaction.get(receiverBalanceRef);

        final senderCurrentBalance = senderBalanceDoc.exists
            ? (senderBalanceDoc.data()!['balance'] ?? 0).toDouble()
            : 0.0;
        final receiverCurrentBalance = receiverBalanceDoc.exists
            ? (receiverBalanceDoc.data()!['balance'] ?? 0).toDouble()
            : 0.0;

        // 3. Find associated message
        // Query inside transaction is limited, but we stored transactionId on the message.
        // Doing a query inside runTransaction requires the query to be properly indexed or exact.
        // NOTE: message update effectively decoupled from critical balance path if query fails,
        // but let's try to do it right.
        // Since we can't easily query inside a transaction without index guarantees,
        // we can try fetching the message ID beforehand if possible, but that breaks atomicity.
        // Alternative: We relax the message status update atomicity (UI can drive off transaction status)
        // OR we query message separate.
        // Ideally, we should have stored messageId on transaction to make this easy.
        // For now, we'll SKIP updating the message status inside the transaction loop to avoid query issues,
        // OR we just rely on Transaction status for UI.
        // Let's rely on Transaction status as truth.
        // BUT, existing UI uses Message object.
        // Let's do a query.
        final messagesQuery = await _firestore
            .collection('chats')
            .doc(chatId)
            .collection('messages')
            .where('transactionId', isEqualTo: transactionId)
            .limit(1)
            .get(); // This is a read, allowed outside or inside? Inside requires strict rules.
        // A simple get() inside is safer if we know the ID.
        // We will do the query OUTSIDE the transaction for the ID, then use valid ref inside.

        // 4. Updates

        // Update Transaction Status
        transaction.update(transactionRef, {
          'status': 'approved',
          'approvedAt': Timestamp.now(),
        });

        // Update Message Status (if found)
        if (messagesQuery.docs.isNotEmpty) {
          transaction.update(messagesQuery.docs.first.reference, {
            'status': 'approved',
          });
        }

        transaction.update(_firestore.collection('chats').doc(chatId), {
          'lastMessageTimestamp': Timestamp.now(),
        });

        // 5. Update Balances
        // SENDER Paid -> Balance Increases (becomes more positive/less negative)
        transaction.set(
            senderBalanceRef,
            {
              'chatId': chatId,
              'userId': senderId,
              'otherUserId': receiverId,
              'balance': senderCurrentBalance + amount,
              'lastCalculatedAt': Timestamp.now(),
            },
            SetOptions(merge: true));

        // RECEIVER Received -> Balance Decreases
        transaction.set(
            receiverBalanceRef,
            {
              'chatId': chatId,
              'userId': receiverId,
              'otherUserId': senderId,
              'balance': receiverCurrentBalance - amount,
              'lastCalculatedAt': Timestamp.now(),
            },
            SetOptions(merge: true));
      });
    } catch (e) {
      print('Error approving transaction: $e');
      rethrow;
    }
  }

  /// Reject a transaction atomically
  Future<void> rejectTransaction(String transactionId) async {
    try {
      final transactionRef =
          _firestore.collection('transactions').doc(transactionId);

      await _firestore.runTransaction((transaction) async {
        final txnSnapshot = await transaction.get(transactionRef);
        if (!txnSnapshot.exists) return;

        final chatId = txnSnapshot.data()!['chatId'];

        // Find message (query needs to happen before/outside to act on ref inside properly?
        // Actually standard Firestore SDK allows queries inside transactions but they must be done before writes).
        // Just doing simple read-update here.

        final messagesQuery = await _firestore
            .collection('chats')
            .doc(chatId)
            .collection('messages')
            .where('transactionId', isEqualTo: transactionId)
            .limit(1)
            .get();

        transaction.update(transactionRef, {
          'status': 'rejected',
          'rejectedAt': Timestamp.now(),
        });

        if (messagesQuery.docs.isNotEmpty) {
          transaction.update(messagesQuery.docs.first.reference, {
            'status': 'rejected',
          });
        }
      });
    } catch (e) {
      print('Error rejecting transaction: $e');
      rethrow;
    }
  }

  /// Get all transactions for a chat
  Stream<List<txn.Transaction>> getTransactionsStream(String chatId) {
    return _firestore
        .collection('transactions')
        .where('chatId', isEqualTo: chatId)
        .orderBy('createdAt', descending: true)
        .snapshots()
        .map((snapshot) {
      return snapshot.docs
          .map((doc) => txn.Transaction.fromFirestore(doc))
          .toList();
    });
  }

  // ===== GROUP EXPENSE OPERATIONS =====

  /// Create a group expense
  Future<String> createGroupExpense(GroupExpense expense) async {
    try {
      final doc =
          await _firestore.collection('group_expenses').add(expense.toJson());
      return doc.id;
    } catch (e) {
      print('Error creating group expense: $e');
      rethrow;
    }
  }

  /// Approve a group expense split for a user
  Future<void> approveGroupExpense(String expenseId, String userId) async {
    try {
      await _firestore.collection('group_expenses').doc(expenseId).update({
        'approvals.$userId': 'approved',
      });
    } catch (e) {
      print('Error approving group expense: $e');
      rethrow;
    }
  }

  /// Get group expenses for a chat
  Stream<List<GroupExpense>> getGroupExpensesStream(String chatId) {
    return _firestore
        .collection('group_expenses')
        .where('chatId', isEqualTo: chatId)
        .orderBy('createdAt', descending: true)
        .snapshots()
        .map((snapshot) {
      return snapshot.docs
          .map((doc) => GroupExpense.fromFirestore(doc))
          .toList();
    });
  }

  // ===== BALANCE OPERATIONS =====

  /// Update balance for a chat/user pair
  /// NOTE: This manually overwrites balance, usually strictly for debugging or correction.
  /// Standard updates should happen via transaction approval.
  Future<void> updateBalance({
    required String chatId,
    required String userId,
    required String otherUserId,
    required double balance,
  }) async {
    try {
      final balanceId = '${chatId}_$userId';
      await _firestore.collection('balances').doc(balanceId).set({
        'chatId': chatId,
        'userId': userId,
        'otherUserId': otherUserId,
        'balance': balance,
        'lastCalculatedAt': Timestamp.now(),
      }, SetOptions(merge: true));
    } catch (e) {
      print('Error updating balance: $e');
      rethrow;
    }
  }

  /// Get balance for a chat
  Future<double> getBalance(String chatId, String userId) async {
    try {
      final balanceId = '${chatId}_$userId';
      final doc = await _firestore.collection('balances').doc(balanceId).get();

      if (doc.exists) {
        return (doc.data()?['balance'] ?? 0).toDouble();
      }
      return 0.0;
    } catch (e) {
      print('Error getting balance: $e');
      return 0.0;
    }
  }

  /// Get balance stream for a chat
  Stream<double> getBalanceStream(String chatId, String userId) {
    final balanceId = '${chatId}_$userId';
    return _firestore
        .collection('balances')
        .doc(balanceId)
        .snapshots()
        .map((doc) {
      if (doc.exists) {
        return (doc.data()?['balance'] ?? 0).toDouble();
      }
      return 0.0;
    });
  }
}

