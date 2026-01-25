import 'user.dart';

class Group {
  final String id;
  final String name;
  final List<User> members;
  final double totalExpense;
  final String imageUrl; // Placeholder

  const Group({
    required this.id,
    required this.name,
    required this.members,
    this.totalExpense = 0,
    this.imageUrl = '',
  });
}

final kDummyGroup = Group(
  id: 'g1',
  name: 'Bali Trip 🌴',
  members: [
    User(
        id: '1',
        name: 'Alice',
        phoneNumber: '+15550101',
        createdAt: DateTime(2024, 1, 1),
        isRegistered: true),
    User(
        id: '2',
        name: 'Bob',
        phoneNumber: '+15550102',
        createdAt: DateTime(2024, 1, 2),
        isRegistered: true),
    User(
        id: '3',
        name: 'Charlie',
        phoneNumber: '+15550103',
        createdAt: DateTime(2024, 1, 3),
        isRegistered: true),
    User(
        id: '4',
        name: 'You',
        phoneNumber: '+15550104',
        createdAt: DateTime(2024, 1, 4),
        isRegistered: true),
  ],
  totalExpense: 1250.0,
);
