import 'user.dart';

class Chat {
  final String id;
  final User user;
  final String lastMessage;
  final DateTime lastActive;
  final double balance; // > 0: you are owed, < 0: you owe
  final int unreadCount;

  const Chat({
    required this.id,
    required this.user,
    required this.lastMessage,
    required this.lastActive,
    this.balance = 0,
    this.unreadCount = 0,
  });
}

final kDummyChats = [
  Chat(
    id: '1',
    user: User(
      id: '1',
      name: 'Alice Freeman',
      phoneNumber: '+15550101',
      createdAt: DateTime(2024, 1, 1),
      isRegistered: true,
    ),
    lastMessage: 'Sent \$50 for dinner',
    lastActive: DateTime.now().subtract(const Duration(minutes: 5)),
    balance: 50.0,
    unreadCount: 2,
  ),
  Chat(
    id: '2',
    user: User(
      id: '2',
      name: 'Bob Smith',
      phoneNumber: '+15550102',
      createdAt: DateTime(2024, 1, 2),
      isRegistered: true,
    ),
    lastMessage: 'Requested \$20 for taxi',
    lastActive: DateTime.now().subtract(const Duration(hours: 1)),
    balance: -20.0,
  ),
  Chat(
    id: '4',
    user: User(
      id: '4',
      name: 'David Evans',
      phoneNumber: '+15550104',
      createdAt: DateTime(2024, 1, 4),
      isRegistered: true,
    ),
    lastMessage: 'Thanks!',
    lastActive: DateTime.now().subtract(const Duration(days: 1)),
    balance: 0,
  ),
];
