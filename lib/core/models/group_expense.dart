import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:equatable/equatable.dart';

/// Split type for group expenses
enum SplitType {
  equal, // Divide equally among all members
  custom, // Custom amounts per member
  percentage, // Percentage-based split
}

/// Approval status for individual member
enum ApprovalStatus {
  pending,
  approved,
  rejected,
  autoApproved, // For unregistered users
}

/// Individual split for a member in group expense
class ExpenseSplit extends Equatable {
  final String userId;
  final double amount;
  final double? percentage; // For percentage-based splits

  const ExpenseSplit({
    required this.userId,
    required this.amount,
    this.percentage,
  });

  factory ExpenseSplit.fromJson(Map<String, dynamic> json) {
    return ExpenseSplit(
      userId: json['userId'] ?? '',
      amount: (json['amount'] ?? 0).toDouble(),
      percentage: json['percentage']?.toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'userId': userId,
      'amount': amount,
      if (percentage != null) 'percentage': percentage,
    };
  }

  @override
  List<Object?> get props => [userId, amount, percentage];
}

/// Group expense model
class GroupExpense extends Equatable {
  final String id;
  final String chatId;
  final String createdBy;
  final String title;
  final double totalAmount;
  final String currency;
  final SplitType splitType;
  final List<ExpenseSplit> splits;
  final Map<String, ApprovalStatus> approvals; // userId -> status
  final DateTime createdAt;

  const GroupExpense({
    required this.id,
    required this.chatId,
    required this.createdBy,
    required this.title,
    required this.totalAmount,
    this.currency = 'USD',
    required this.splitType,
    required this.splits,
    required this.approvals,
    required this.createdAt,
  });

  /// Check if all members have approved/rejected/auto-approved
  bool get isCompleted {
    return approvals.values.every((status) =>
        status == ApprovalStatus.approved ||
        status == ApprovalStatus.rejected ||
        status == ApprovalStatus.autoApproved);
  }

  /// Get number of pending approvals
  int get pendingCount {
    return approvals.values
        .where((status) => status == ApprovalStatus.pending)
        .length;
  }

  /// Create GroupExpense from Firestore document
  factory GroupExpense.fromFirestore(
      DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data()!;

    // Parse splits
    final splitsList = (data['splits'] as List<dynamic>?)
            ?.map(
                (split) => ExpenseSplit.fromJson(split as Map<String, dynamic>))
            .toList() ??
        [];

    // Parse approvals
    final approvalsMap = (data['approvals'] as Map<String, dynamic>?)?.map(
          (key, value) => MapEntry(
            key,
            ApprovalStatus.values.firstWhere(
              (e) => e.name == value,
              orElse: () => ApprovalStatus.pending,
            ),
          ),
        ) ??
        {};

    return GroupExpense(
      id: doc.id,
      chatId: data['chatId'] ?? '',
      createdBy: data['createdBy'] ?? '',
      title: data['title'] ?? '',
      totalAmount: (data['totalAmount'] ?? 0).toDouble(),
      currency: data['currency'] ?? 'USD',
      splitType: SplitType.values.firstWhere(
        (e) => e.name == data['splitType'],
        orElse: () => SplitType.equal,
      ),
      splits: splitsList,
      approvals: approvalsMap,
      createdAt: (data['createdAt'] as Timestamp).toDate(),
    );
  }

  /// Convert GroupExpense to JSON for Firestore
  Map<String, dynamic> toJson() {
    return {
      'chatId': chatId,
      'createdBy': createdBy,
      'title': title,
      'totalAmount': totalAmount,
      'currency': currency,
      'splitType': splitType.name,
      'splits': splits.map((split) => split.toJson()).toList(),
      'approvals': approvals.map((key, value) => MapEntry(key, value.name)),
      'createdAt': Timestamp.fromDate(createdAt),
    };
  }

  /// Copy with method for updates
  GroupExpense copyWith({
    String? id,
    String? chatId,
    String? createdBy,
    String? title,
    double? totalAmount,
    String? currency,
    SplitType? splitType,
    List<ExpenseSplit>? splits,
    Map<String, ApprovalStatus>? approvals,
    DateTime? createdAt,
  }) {
    return GroupExpense(
      id: id ?? this.id,
      chatId: chatId ?? this.chatId,
      createdBy: createdBy ?? this.createdBy,
      title: title ?? this.title,
      totalAmount: totalAmount ?? this.totalAmount,
      currency: currency ?? this.currency,
      splitType: splitType ?? this.splitType,
      splits: splits ?? this.splits,
      approvals: approvals ?? this.approvals,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  List<Object?> get props => [
        id,
        chatId,
        createdBy,
        title,
        totalAmount,
        currency,
        splitType,
        splits,
        approvals,
        createdAt,
      ];
}
