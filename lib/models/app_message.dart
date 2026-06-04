import 'package:cloud_firestore/cloud_firestore.dart';

// ── File transfer status ───────────────────────────────────────────────────────

enum FileTransferStatus {
  pending('PENDING'),
  uploading('UPLOADING'),
  ready('READY'),
  downloading('DOWNLOADING'),
  saved('SAVED'),
  failed('FAILED');

  final String value;
  const FileTransferStatus(this.value);

  factory FileTransferStatus.fromString(String? value) {
    return FileTransferStatus.values.firstWhere(
      (e) => e.value == value,
      orElse: () => FileTransferStatus.pending,
    );
  }
}

// ── Transaction message status ────────────────────────────────────────────────

enum TransactionMessageStatus {
  pending('PENDING'),
  accepted('ACCEPTED'),
  rejected('REJECTED');

  final String value;
  const TransactionMessageStatus(this.value);

  factory TransactionMessageStatus.fromString(String? value) {
    return TransactionMessageStatus.values.firstWhere(
      (e) => e.value == value,
      orElse: () => TransactionMessageStatus.pending,
    );
  }
}

// ── Message content type ───────────────────────────────────────────────────────

enum MessageContentType {
  text('TEXT'),
  transaction('TRANSACTION'),
  order('ORDER'),
  file('FILE'),
  image('IMAGE');

  final String value;
  const MessageContentType(this.value);

  factory MessageContentType.fromString(String value) {
    return MessageContentType.values.firstWhere(
      (e) => e.value == value,
      orElse: () => MessageContentType.text,
    );
  }
}

// ── AppMessage ─────────────────────────────────────────────────────────────────

class AppMessage {
  final String id;
  final String threadId;
  final String senderId;
  final String text;
  final DateTime createdAt;
  final MessageContentType type;
  final double? amount;
  final bool isRead;
  final bool isReceive; // true = receiver recorded this as incoming money
  final String? orderId; // set when type == order

  // ── Transaction request status (only for type == transaction) ────────────────
  // null = legacy message (treat as accepted/completed)
  final TransactionMessageStatus? txnStatus;

  // ── Image fields (only populated when type == image) ─────────────────────────
  final String? imageUrl; // ImgBB CDN URL

  // ── File transfer fields (only populated when type == file) ─────────────────
  final String? fileName;
  final int? fileSize; // bytes
  final String? mimeType;
  final String? transferId; // key in RTDB file_transfers/{transferId}
  final FileTransferStatus fileStatus;
  final int? totalChunks;

  AppMessage({
    required this.id,
    required this.threadId,
    required this.senderId,
    required this.text,
    required this.createdAt,
    required this.type,
    this.amount,
    required this.isRead,
    this.isReceive = false,
    this.orderId,
    this.txnStatus,
    this.imageUrl,
    this.fileName,
    this.fileSize,
    this.mimeType,
    this.transferId,
    this.fileStatus = FileTransferStatus.pending,
    this.totalChunks,
  });

  factory AppMessage.fromMap(Map<String, dynamic> map) {
    return AppMessage(
      id: map['id'] as String? ?? '',
      threadId: map['threadId'] as String? ?? '',
      senderId: map['senderId'] as String? ?? '',
      text: map['text'] as String? ?? '',
      createdAt: _toDateTime(map['createdAt']),
      type: MessageContentType.fromString(map['type'] as String? ?? 'TEXT'),
      amount: (map['amount'] as num?)?.toDouble(),
      isRead: map['isRead'] as bool? ?? true,
      isReceive: map['isReceive'] as bool? ?? false,
      orderId: map['orderId'] as String?,
      txnStatus: map['txnStatus'] != null
          ? TransactionMessageStatus.fromString(map['txnStatus'] as String?)
          : null,
      imageUrl: map['imageUrl'] as String?,
      fileName: map['fileName'] as String?,
      fileSize: (map['fileSize'] as num?)?.toInt(),
      mimeType: map['mimeType'] as String?,
      transferId: map['transferId'] as String?,
      fileStatus: FileTransferStatus.fromString(map['fileStatus'] as String?),
      totalChunks: (map['totalChunks'] as num?)?.toInt(),
    );
  }

  factory AppMessage.fromFirestore(DocumentSnapshot doc) {
    return AppMessage.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'threadId': threadId,
      'senderId': senderId,
      'text': text,
      'createdAt': Timestamp.fromDate(createdAt),
      'type': type.value,
      if (amount != null) 'amount': amount,
      'isRead': isRead,
      'isReceive': isReceive,
      if (orderId != null) 'orderId': orderId,
      if (txnStatus != null) 'txnStatus': txnStatus!.value,
      if (imageUrl != null) 'imageUrl': imageUrl,
      if (fileName != null) 'fileName': fileName,
      if (fileSize != null) 'fileSize': fileSize,
      if (mimeType != null) 'mimeType': mimeType,
      if (transferId != null) 'transferId': transferId,
      'fileStatus': fileStatus.value,
      if (totalChunks != null) 'totalChunks': totalChunks,
    };
  }

  AppMessage copyWith({
    String? id,
    String? threadId,
    String? senderId,
    String? text,
    DateTime? createdAt,
    MessageContentType? type,
    double? amount,
    bool? isRead,
    bool? isReceive,
    String? orderId,
    TransactionMessageStatus? txnStatus,
    String? imageUrl,
    String? fileName,
    int? fileSize,
    String? mimeType,
    String? transferId,
    FileTransferStatus? fileStatus,
    int? totalChunks,
  }) {
    return AppMessage(
      id: id ?? this.id,
      threadId: threadId ?? this.threadId,
      senderId: senderId ?? this.senderId,
      text: text ?? this.text,
      createdAt: createdAt ?? this.createdAt,
      type: type ?? this.type,
      amount: amount ?? this.amount,
      isRead: isRead ?? this.isRead,
      isReceive: isReceive ?? this.isReceive,
      orderId: orderId ?? this.orderId,
      txnStatus: txnStatus ?? this.txnStatus,
      imageUrl: imageUrl ?? this.imageUrl,
      fileName: fileName ?? this.fileName,
      fileSize: fileSize ?? this.fileSize,
      mimeType: mimeType ?? this.mimeType,
      transferId: transferId ?? this.transferId,
      fileStatus: fileStatus ?? this.fileStatus,
      totalChunks: totalChunks ?? this.totalChunks,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppMessage &&
          runtimeType == other.runtimeType &&
          id == other.id;

  @override
  int get hashCode => id.hashCode;
}

// ── ChatThread ─────────────────────────────────────────────────────────────────

class ChatThread {
  final String id;
  final List<String> participantIds;
  final String title;
  final String avatarUrl;
  final String lastMessagePreview;
  final DateTime updatedAt;
  final int unreadCount;

  ChatThread({
    required this.id,
    required this.participantIds,
    required this.title,
    required this.avatarUrl,
    required this.lastMessagePreview,
    required this.updatedAt,
    required this.unreadCount,
  });

  factory ChatThread.fromMap(Map<String, dynamic> map) {
    return ChatThread(
      id: map['id'] as String? ?? '',
      participantIds: List<String>.from(map['participantIds'] as List? ?? []),
      title: map['title'] as String? ?? '',
      avatarUrl: map['avatarUrl'] as String? ?? '',
      lastMessagePreview: map['lastMessagePreview'] as String? ?? '',
      updatedAt: _toDateTime(map['updatedAt']),
      unreadCount: (map['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }

  factory ChatThread.fromFirestore(DocumentSnapshot doc) {
    return ChatThread.fromMap(doc.data() as Map<String, dynamic>);
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'participantIds': participantIds,
      'title': title,
      'avatarUrl': avatarUrl,
      'lastMessagePreview': lastMessagePreview,
      'updatedAt': Timestamp.fromDate(updatedAt),
      'unreadCount': unreadCount,
    };
  }

  ChatThread copyWith({
    String? id,
    List<String>? participantIds,
    String? title,
    String? avatarUrl,
    String? lastMessagePreview,
    DateTime? updatedAt,
    int? unreadCount,
  }) {
    return ChatThread(
      id: id ?? this.id,
      participantIds: participantIds ?? this.participantIds,
      title: title ?? this.title,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      lastMessagePreview: lastMessagePreview ?? this.lastMessagePreview,
      updatedAt: updatedAt ?? this.updatedAt,
      unreadCount: unreadCount ?? this.unreadCount,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ChatThread &&
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
