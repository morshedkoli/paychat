import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/foundation.dart';
import 'package:paychat/models/app_message.dart';
import 'package:uuid/uuid.dart';

class ChatRepository {
  final FirebaseFirestore firestore;

  ChatRepository({required this.firestore});

  late final CollectionReference<Map<String, dynamic>> _threads =
      firestore.collection('threads');

  late final CollectionReference<Map<String, dynamic>> _messages =
      firestore.collection('messages');

  // ── Read ─────────────────────────────────────────────────────────────────────

  Stream<List<Map<String, dynamic>>> watchUserThreads(String userId) {
    return _threads
        .where('participantIds', arrayContains: userId)
        .snapshots()
        .map((snapshot) {
      final list = snapshot.docs.map((doc) => doc.data()).toList();
      list.sort((a, b) {
        final aTime =
            (a['updatedAt'] as Timestamp?)?.millisecondsSinceEpoch ?? 0;
        final bTime =
            (b['updatedAt'] as Timestamp?)?.millisecondsSinceEpoch ?? 0;
        return bTime.compareTo(aTime);
      });
      return list;
    });
  }

  Stream<List<AppMessage>> watchThreadMessages(String threadId) {
    return _messages
        .where('threadId', isEqualTo: threadId)
        .snapshots()
        .map((snapshot) {
      final list =
          snapshot.docs.map((doc) => AppMessage.fromFirestore(doc)).toList();
      list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return list;
    });
  }

  Future<List<AppMessage>> fetchMessages(String threadId) async {
    final snapshot =
        await _messages.where('threadId', isEqualTo: threadId).get();

    final list =
        snapshot.docs.map((doc) => AppMessage.fromFirestore(doc)).toList();
    list.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return list;
  }

  // ── Write ────────────────────────────────────────────────────────────────────

  Future<AppMessage> sendMessage({
    required String threadId,
    required String senderId,
    required String text,
  }) async {
    final message = AppMessage(
      id: _messages.doc().id,
      threadId: threadId,
      senderId: senderId,
      text: text,
      createdAt: DateTime.now(),
      type: MessageContentType.text,
      isRead: false,
    );

    await _messages.doc(message.id).set(message.toMap());
    await _threads.doc(threadId).update({
      'lastMessagePreview': text,
      'updatedAt': Timestamp.now(),
      'unreadCount': FieldValue.increment(1),
      'lastSenderId': senderId,
    });
    return message;
  }

  /// Send a transaction message.
  ///
  /// [txnStatus] controls the flow:
  /// - [TransactionMessageStatus.pending]: Send Money request — recipient must accept.
  ///   Balances are NOT updated until [acceptTransactionMessage] is called.
  /// - [TransactionMessageStatus.accepted]: Record Receipt — immediately completed.
  ///   Balances update right now.
  Future<AppMessage> sendTransactionMessage({
    required String threadId,
    required String senderId,
    required String partnerUserId,
    required String text,
    required double amount,
    required bool isReceive,
    required TransactionMessageStatus txnStatus,
    String? messageId,
    WriteBatch? batch,
  }) async {
    final message = AppMessage(
      id: messageId ?? _messages.doc().id,
      threadId: threadId,
      senderId: senderId,
      text: text,
      createdAt: DateTime.now(),
      type: MessageContentType.transaction,
      amount: amount,
      isRead: false,
      isReceive: isReceive,
      txnStatus: txnStatus,
    );

    final updateData = <String, dynamic>{
      'lastMessagePreview': text,
      'updatedAt': Timestamp.now(),
      'unreadCount': FieldValue.increment(1),
      'lastSenderId': senderId,
    };

    // Note: Thread balance updates (balances map) are now securely handled
    // on the server side by Firebase Cloud Functions triggers to prevent
    // client-side manipulation.

    if (batch != null) {
      batch.set(_messages.doc(message.id), message.toMap());
      batch.update(_threads.doc(threadId), updateData);
    } else {
      await _messages.doc(message.id).set(message.toMap());
      await _threads.doc(threadId).update(updateData);
    }

    return message;
  }

  /// Accept a pending send-money request.
  /// Updates the message txnStatus to accepted. Balance changes are computed
  /// and applied securely by the onTransactionStatusChange Cloud Function.
  /// The caller should also create a transaction record for the acceptor.
  Future<void> acceptTransactionMessage({
    required String messageId,
    required String threadId,
    required String senderId,   // original message sender (who initiated)
    required String acceptorId, // recipient who is accepting
    required double amount,
    required bool isReceive,    // original isReceive flag from the message
  }) async {
    final batch = firestore.batch();
    batch.update(_messages.doc(messageId), {'txnStatus': TransactionMessageStatus.accepted.value});
    await batch.commit();
  }

  /// Reject a pending send-money request.
  Future<void> rejectTransactionMessage(String messageId) async {
    await _messages.doc(messageId).update({
      'txnStatus': TransactionMessageStatus.rejected.value,
    });
  }

  /// Send an image message. The image is already uploaded to ImgBB;
  /// [imageUrl] is the CDN URL returned by the API.
  Future<AppMessage> sendImageMessage({
    required String threadId,
    required String senderId,
    required String imageUrl,
  }) async {
    final message = AppMessage(
      id: _messages.doc().id,
      threadId: threadId,
      senderId: senderId,
      text: '📷 Photo',
      createdAt: DateTime.now(),
      type: MessageContentType.image,
      isRead: false,
      imageUrl: imageUrl,
    );

    await _messages.doc(message.id).set(message.toMap());
    await _threads.doc(threadId).update({
      'lastMessagePreview': '📷 Photo',
      'updatedAt': Timestamp.now(),
      'unreadCount': FieldValue.increment(1),
      'lastSenderId': senderId,
    });
    return message;
  }

  /// Send a file metadata message.
  Future<(AppMessage, String)> sendFileMessage({
    required String threadId,
    required String senderId,
    required String fileName,
    required int fileSize,
    required String mimeType,
    required int totalChunks,
  }) async {
    final transferId = const Uuid().v4();
    final message = AppMessage(
      id: _messages.doc().id,
      threadId: threadId,
      senderId: senderId,
      text: '📎 $fileName',
      createdAt: DateTime.now(),
      type: MessageContentType.file,
      isRead: false,
      fileName: fileName,
      fileSize: fileSize,
      mimeType: mimeType,
      transferId: transferId,
      fileStatus: FileTransferStatus.uploading,
      totalChunks: totalChunks,
    );

    await _messages.doc(message.id).set(message.toMap());
    await _threads.doc(threadId).update({
      'lastMessagePreview': '📎 $fileName',
      'updatedAt': Timestamp.now(),
      'unreadCount': FieldValue.increment(1),
      'lastSenderId': senderId,
    });
    return (message, transferId);
  }

  Future<void> updateFileStatus(String messageId, FileTransferStatus status) async {
    try {
      await _messages.doc(messageId).update({'fileStatus': status.value});
    } catch (_) {}
  }

  Future<void> markThreadAsRead(String threadId) async {
    try {
      await _threads.doc(threadId).update({'unreadCount': 0});
    } catch (_) {}
  }

  /// Batch update all unread messages received in this thread as read
  Future<void> markMessagesAsRead({
    required String threadId,
    required String currentUserId,
  }) async {
    try {
      final unreadSnapshot = await _messages
          .where('threadId', isEqualTo: threadId)
          .where('isRead', isEqualTo: false)
          .get();

      if (unreadSnapshot.docs.isEmpty) return;

      final batch = firestore.batch();
      bool hasUpdates = false;
      for (final doc in unreadSnapshot.docs) {
        if (doc.data()['senderId'] != currentUserId) {
          batch.update(doc.reference, {'isRead': true});
          hasUpdates = true;
        }
      }
      if (hasUpdates) {
        await batch.commit();
      }
    } catch (_) {}
  }

  // ── Thread creation ───────────────────────────────────────────────────────────

  Future<String> getOrCreateThread({
    required String currentUserId,
    required String currentUserDisplayName,
    required String currentUserPhotoUrl,
    required String contactUserId,
    required String contactName,
    required String contactPhotoUrl,
  }) async {
    final threadId = _generateThreadId(currentUserId, contactUserId);

    final doc = await _threads.doc(threadId).get();

    if (!doc.exists) {
      await _threads.doc(threadId).set({
        'id': threadId,
        'participantIds': [currentUserId, contactUserId],
        'users': {
          currentUserId: {
            'name': currentUserDisplayName,
            'photoUrl': currentUserPhotoUrl,
            'isGuest': false,
          },
          contactUserId: {
            'name': contactName,
            'photoUrl': contactPhotoUrl,
            'isGuest': false,
          },
        },
        'lastMessagePreview': '',
        'updatedAt': Timestamp.now(),
        'unreadCount': 0,
        'lastSenderId': '',
      });
    } else {
      await _threads.doc(threadId).update({
        'users.$contactUserId': {
          'name': contactName,
          'photoUrl': contactPhotoUrl,
          'isGuest': false,
        },
        'users.$currentUserId': {
          'name': currentUserDisplayName,
          'photoUrl': currentUserPhotoUrl,
          'isGuest': false,
        },
      });
    }

    return threadId;
  }

  Future<String> getOrCreateGuestThread({
    required String currentUserId,
    required String currentUserDisplayName,
    required String currentUserPhotoUrl,
    required String guestPhone,
    required String guestName,
  }) async {
    final guestId = 'guest_${guestPhone.replaceAll(RegExp(r'[^\d]'), '')}';
    final threadId = _generateThreadId(currentUserId, guestId);

    final doc = await _threads.doc(threadId).get();
    if (!doc.exists) {
      await _threads.doc(threadId).set({
        'id': threadId,
        'participantIds': [currentUserId, guestId],
        'guestPhone': guestPhone,
        'users': {
          currentUserId: {
            'name': currentUserDisplayName,
            'photoUrl': currentUserPhotoUrl,
            'isGuest': false,
          },
          guestId: {
            'name': guestName.isNotEmpty ? guestName : guestPhone,
            'photoUrl': '',
            'isGuest': true,
            'phoneNumber': guestPhone,
          },
        },
        'lastMessagePreview': '',
        'updatedAt': Timestamp.now(),
        'unreadCount': 0,
        'lastSenderId': '',
      });
    }
    return threadId;
  }

  Future<void> migrateGuestThreads({
    required String guestPhone,
    required String realUserId,
    required String realUserName,
    required String realUserPhotoUrl,
  }) async {
    try {
      final snapshot =
          await _threads.where('guestPhone', isEqualTo: guestPhone).get();

      for (final doc in snapshot.docs) {
        final data = doc.data();
        final participantIds =
            List<String>.from(data['participantIds'] ?? []);
        final users =
            Map<String, dynamic>.from(data['users'] ?? {});

        final guestId = participantIds.firstWhere(
          (id) => id.startsWith('guest_'),
          orElse: () => '',
        );
        if (guestId.isEmpty) continue;

        participantIds.remove(guestId);
        if (!participantIds.contains(realUserId)) {
          participantIds.add(realUserId);
        }

        users.remove(guestId);
        users[realUserId] = {
          'name': realUserName,
          'photoUrl': realUserPhotoUrl,
          'isGuest': false,
        };

        await doc.reference.update({
          'participantIds': participantIds,
          'users': users,
          'guestPhone': FieldValue.delete(),
        });
      }
    } catch (e, st) {
      debugPrint('migrateGuestThreads error: $e\n$st');
    }
  }

  // ── Internal ──────────────────────────────────────────────────────────────────

  String _generateThreadId(String userId1, String userId2) {
    final ids = [userId1, userId2]..sort();
    return '${ids[0]}_${ids[1]}';
  }
}
