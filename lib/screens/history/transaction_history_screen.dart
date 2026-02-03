import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gap/gap.dart';
import 'package:intl/intl.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/providers/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/models/transaction.dart';
import '../../core/models/user.dart';
import '../../screens/summary/report_screen.dart';

class TransactionHistoryScreen extends ConsumerWidget {
  const TransactionHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final transactionsAsync = ref.watch(userTransactionsProvider);
    final currentUser = ref.watch(currentUserProvider).value;

    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: const Text('Transaction History'),
        actions: [
          IconButton(
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const ReportScreen()),
              );
            },
            icon: Icon(
              PhosphorIcons.chartPieSlice(),
              color: AppColors.primary,
            ),
          ),
          const Gap(8),
        ],
      ),
      body: transactionsAsync.when(
        data: (transactions) {
          if (transactions.isEmpty) {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    PhosphorIcons.clockCounterClockwise(),
                    size: 64,
                    color: AppColors.textSecondary.withOpacity(0.5),
                  ),
                  const Gap(16),
                  Text(
                    'No transactions yet',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textSecondary.withOpacity(0.7),
                    ),
                  ),
                  const Gap(8),
                  Text(
                    'Your transaction history will appear here',
                    style: TextStyle(
                      fontSize: 14,
                      color: AppColors.textSecondary.withOpacity(0.6),
                    ),
                  ),
                ],
              ),
            );
          }

          return ListView.builder(
            padding: AppSpacing.paddingMd,
            itemCount: transactions.length,
            itemBuilder: (context, index) {
              final transaction = transactions[index];
              return _TransactionListItem(
                transaction: transaction,
                currentUser: currentUser,
                index: index,
              );
            },
          );
        },
        loading: () => const Center(
          child: CircularProgressIndicator(),
        ),
        error: (error, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                PhosphorIcons.warning(),
                size: 48,
                color: AppColors.danger,
              ),
              const Gap(16),
              const Text(
                'Error loading transactions',
                style: TextStyle(
                  fontSize: 16,
                  color: AppColors.textSecondary,
                ),
              ),
              const Gap(8),
              TextButton(
                onPressed: () => ref.refresh(userTransactionsProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TransactionListItem extends ConsumerWidget {
  final Transaction transaction;
  final User? currentUser;
  final int index;

  const _TransactionListItem({
    required this.transaction,
    required this.currentUser,
    required this.index,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Determine display based on who PAID (sender)
    final isApproved = transaction.status == TransactionStatus.approved ||
        transaction.status == TransactionStatus.autoApproved;
    final isYouPaid =
        currentUser != null && transaction.senderId == currentUser!.id;
    // For display: green/positive when you paid, orange/negative when they paid
    final isIncome = isYouPaid && isApproved;

    // Determine other user ID to fetch name
    final otherUserId =
        isYouPaid ? transaction.receiverId : transaction.senderId;
    final otherUserAsync = ref.watch(userProvider(otherUserId));

    return otherUserAsync.when(
      data: (otherUser) {
        final otherName = otherUser?.name ?? 'Unknown';

        String title;
        if (transaction.note.isNotEmpty) {
          title = '${transaction.note} • $otherName';
        } else {
          // Fallback titles if note is empty
          if (isYouPaid) {
            title = 'Paid to $otherName';
          } else {
            title = 'Paid by $otherName';
          }
        }

        return Container(
          margin: const EdgeInsets.only(bottom: AppSpacing.md),
          padding: AppSpacing.paddingMd,
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: AppSpacing.borderRadiusLg,
            boxShadow: AppShadows.sm,
          ),
          child: Row(
            children: [
              // Transaction Icon
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: isIncome
                      ? AppColors.success.withOpacity(0.1)
                      : AppColors.warning.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  isIncome
                      ? PhosphorIcons.arrowDown()
                      : PhosphorIcons.arrowUp(),
                  color: isIncome ? AppColors.success : AppColors.warning,
                  size: 24,
                ),
              ),
              const Gap(16),

              // Transaction Details
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppColors.textPrimary,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const Gap(4),
                    Row(
                      children: [
                        _StatusBadge(status: transaction.status),
                        const Gap(8),
                        Text(
                          '• ${transaction.currency}',
                          style: TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary.withOpacity(0.8),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              // Amount & Time
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${isIncome ? '+' : '-'}৳${transaction.amount.toStringAsFixed(2)}',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: isIncome ? AppColors.success : AppColors.warning,
                    ),
                  ),
                  const Gap(4),
                  Text(
                    DateFormat('MMM d, h:mm a').format(transaction.createdAt),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ).animate().fadeIn(
              delay: Duration(milliseconds: 50 * index),
              duration: 300.ms,
            );
      },
      loading: () => Container(
          height: 80,
          margin: const EdgeInsets.only(bottom: AppSpacing.md),
          child: const Center(child: CircularProgressIndicator())),
      error: (_, __) => const SizedBox(), // Hide if user load fails
    );
  }
}

class _StatusBadge extends StatelessWidget {
  final TransactionStatus status;

  const _StatusBadge({required this.status});

  @override
  Widget build(BuildContext context) {
    Color color;
    String label;

    switch (status) {
      case TransactionStatus.approved:
      case TransactionStatus.autoApproved:
        color = AppColors.success;
        label = 'Approved';
        break;
      case TransactionStatus.pending:
        color = AppColors.warning;
        label = 'Pending';
        break;
      case TransactionStatus.rejected:
        color = AppColors.danger;
        label = 'Rejected';
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w600,
          color: color,
        ),
      ),
    );
  }
}
