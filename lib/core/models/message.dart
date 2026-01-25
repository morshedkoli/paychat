enum MessageType { text, transaction }

enum TransactionStatus { pending, approved, autoApproved, rejected }

class Message {
  final String id;
  final String senderId;
  final String content; // Text or Note
  final DateTime timestamp;
  final MessageType type;

  // Transaction specific
  final double? amount;
  final TransactionStatus? status;

  const Message({
    required this.id,
    required this.senderId,
    required this.content,
    required this.timestamp,
    this.type = MessageType.text,
    this.amount,
    this.status,
  });

  bool get isMe => senderId == 'me'; // Simplified
}

final kDummyMessages = [
  Message(
    id: '1',
    senderId: 'other',
    content: "Hey, do you have the dinner receipt?",
    timestamp: DateTime.now().subtract(const Duration(minutes: 10)),
  ),
  Message(
    id: '2',
    senderId: 'me',
    content: "Dinner at Italian Place",
    amount: 85.50,
    type: MessageType.transaction,
    status: TransactionStatus.approved,
    timestamp: DateTime.now().subtract(const Duration(minutes: 8)),
  ),
  Message(
    id: '3',
    senderId: 'other',
    content: "Sent my share!",
    timestamp: DateTime.now().subtract(const Duration(minutes: 5)),
  ),
  Message(
    id: '4',
    senderId: 'other',
    content: "Taxi to Airport",
    amount: 25.0,
    type: MessageType.transaction,
    status: TransactionStatus.pending,
    timestamp: DateTime.now().subtract(const Duration(minutes: 2)),
  ),
];
