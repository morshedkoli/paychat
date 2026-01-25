/// Firestore collection and field name constants
class FirestoreCollections {
  // Collection names
  static const String users = 'users';
  static const String chats = 'chats';
  static const String messages = 'messages';
  static const String transactions = 'transactions';
  static const String groupExpenses = 'group_expenses';
  static const String balances = 'balances';

  // User fields
  static const String userId = 'userId';
  static const String phoneNumber = 'phoneNumber';
  static const String email = 'email';
  static const String displayName = 'displayName';
  static const String photoUrl = 'photoUrl';
  static const String fcmToken = 'fcmToken';
  static const String createdAt = 'createdAt';
  static const String lastSeen = 'lastSeen';

  // Chat fields
  static const String chatId = 'chatId';
  static const String members = 'members';
  static const String isGroup = 'isGroup';
  static const String groupName = 'groupName';
  static const String lastMessage = 'lastMessage';
  static const String lastMessageTimestamp = 'lastMessageTimestamp';
  static const String createdBy = 'createdBy';

  // Message fields
  static const String messageId = 'messageId';
  static const String senderId = 'senderId';
  static const String content = 'content';
  static const String timestamp = 'timestamp';
  static const String type = 'type';
  static const String isEncrypted = 'isEncrypted';
  static const String readAt = 'readAt';

  // Transaction fields
  static const String transactionId = 'transactionId';
  static const String receiverId = 'receiverId';
  static const String amount = 'amount';
  static const String note = 'note';
  static const String currency = 'currency';
  static const String status = 'status';
  static const String approvedAt = 'approvedAt';
  static const String rejectedAt = 'rejectedAt';

  // Group expense fields
  static const String expenseId = 'expenseId';
  static const String title = 'title';
  static const String totalAmount = 'totalAmount';
  static const String splitType = 'splitType';
  static const String splits = 'splits';
  static const String approvals = 'approvals';

  // Balance fields
  static const String balance = 'balance';
  static const String lastCalculatedAt = 'lastCalculatedAt';
}
