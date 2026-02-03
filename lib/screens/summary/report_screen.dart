import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import 'package:intl/intl.dart';
import '../../core/providers/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/models/transaction.dart';

enum ReportFilter { monthly, yearly, custom }

class ReportScreen extends ConsumerStatefulWidget {
  const ReportScreen({super.key});

  @override
  ConsumerState<ReportScreen> createState() => _ReportScreenState();
}

class _ReportScreenState extends ConsumerState<ReportScreen> {
  ReportFilter _filter = ReportFilter.monthly;
  DateTime _focusedDate = DateTime.now();
  DateTimeRange? _customRange;

  void _updateFilter(ReportFilter filter) {
    setState(() {
      _filter = filter;
      // Reset focused date to now when switching modes, mostly for UX
      if (filter != ReportFilter.custom) {
        _focusedDate = DateTime.now();
      } else {
        // If switching to custom, maybe pick a default range or Keep null until selected
        if (_customRange == null) {
          final now = DateTime.now();
          _customRange = DateTimeRange(
            start: now.subtract(const Duration(days: 7)),
            end: now,
          );
        }
      }
    });
  }

  void _navigateDate(int i) {
    setState(() {
      if (_filter == ReportFilter.monthly) {
        _focusedDate = DateTime(_focusedDate.year, _focusedDate.month + i);
      } else if (_filter == ReportFilter.yearly) {
        _focusedDate = DateTime(_focusedDate.year + i);
      }
    });
  }

  Future<void> _pickDateRange() async {
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDateRange: _customRange,
    );
    if (picked != null) {
      setState(() => _customRange = picked);
    }
  }

  String get _periodLabel {
    switch (_filter) {
      case ReportFilter.monthly:
        return DateFormat('MMMM yyyy').format(_focusedDate);
      case ReportFilter.yearly:
        return DateFormat('yyyy').format(_focusedDate);
      case ReportFilter.custom:
        if (_customRange == null) return 'Select Date Range';
        return '${DateFormat('MMM d').format(_customRange!.start)} - ${DateFormat('MMM d').format(_customRange!.end)}';
    }
  }

  bool _shouldInclude(Transaction tx) {
    if (tx.status != TransactionStatus.approved &&
        tx.status != TransactionStatus.autoApproved) {
      return false;
    }

    final date = tx.createdAt;
    switch (_filter) {
      case ReportFilter.monthly:
        return date.year == _focusedDate.year &&
            date.month == _focusedDate.month;
      case ReportFilter.yearly:
        return date.year == _focusedDate.year;
      case ReportFilter.custom:
        if (_customRange == null) return true;
        return date.isAfter(
                _customRange!.start.subtract(const Duration(seconds: 1))) &&
            date.isBefore(_customRange!.end
                .add(const Duration(days: 1))); // Inclusive-ish
    }
  }

  @override
  Widget build(BuildContext context) {
    final transactionsAsync = ref.watch(userTransactionsProvider);
    final currentUser = ref.watch(currentUserProvider).value;

    return Scaffold(
      backgroundColor: AppColors.backgroundLight,
      appBar: AppBar(
        title: const Text('Financial Report'),
        centerTitle: true,
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          // Filter Toggle
          PopupMenuButton<ReportFilter>(
            icon: const Icon(PhosphorIconsFill.funnelSimple,
                color: AppColors.primary),
            onSelected: _updateFilter,
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: ReportFilter.monthly,
                child: Text('Monthly'),
              ),
              const PopupMenuItem(
                value: ReportFilter.yearly,
                child: Text('Yearly'),
              ),
              const PopupMenuItem(
                value: ReportFilter.custom,
                child: Text('Custom Range'),
              ),
            ],
          ),
          const Gap(8),
        ],
      ),
      body: Column(
        children: [
          // Date Navigator
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                if (_filter != ReportFilter.custom)
                  IconButton(
                    onPressed: () => _navigateDate(-1),
                    icon: const Icon(PhosphorIconsRegular.caretLeft),
                  )
                else
                  const SizedBox(width: 48), // Spacer

                // Label
                GestureDetector(
                    onTap:
                        _filter == ReportFilter.custom ? _pickDateRange : null,
                    child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 8),
                        decoration: _filter == ReportFilter.custom
                            ? BoxDecoration(
                                color: AppColors.surface,
                                borderRadius: BorderRadius.circular(20),
                                border: Border.all(color: AppColors.border),
                              )
                            : null,
                        child: Row(
                          children: [
                            Text(
                              _periodLabel,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w600,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            if (_filter == ReportFilter.custom) ...[
                              const Gap(8),
                              const Icon(PhosphorIconsRegular.calendarBlank,
                                  size: 16),
                            ]
                          ],
                        ))),

                if (_filter != ReportFilter.custom)
                  IconButton(
                    onPressed: () => _navigateDate(1),
                    icon: const Icon(PhosphorIconsRegular.caretRight),
                  )
                else
                  const SizedBox(width: 48), // Spacer
              ],
            ),
          ),

          Expanded(
            child: transactionsAsync.when(
              data: (transactions) {
                if (currentUser == null) return const SizedBox();

                double totalIncome = 0;
                double totalExpense = 0;

                for (final tx in transactions) {
                  if (_shouldInclude(tx)) {
                    if (tx.senderId == currentUser.id) {
                      totalIncome += tx.amount;
                    } else {
                      totalExpense += tx.amount;
                    }
                  }
                }

                final netBalance = totalIncome - totalExpense;
                final totalVolume = totalIncome + totalExpense;

                final incomePercent =
                    totalVolume == 0 ? 0.0 : totalIncome / totalVolume;
                final expensePercent =
                    totalVolume == 0 ? 0.0 : totalExpense / totalVolume;

                return SingleChildScrollView(
                  padding: const EdgeInsets.all(AppSpacing.md),
                  child: Column(
                    children: [
                      // 1. Net Balance Card
                      Container(
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [
                              AppColors.primary,
                              AppColors.primary.withOpacity(0.8)
                            ],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(20),
                          boxShadow: [
                            BoxShadow(
                              color: AppColors.primary.withOpacity(0.3),
                              blurRadius: 15,
                              offset: const Offset(0, 8),
                            ),
                          ],
                        ),
                        child: Column(
                          children: [
                            Text(
                              'Net Balance (${_filter == ReportFilter.yearly ? "Year" : "Period"})',
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.8),
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                            const Gap(8),
                            Text(
                              '${netBalance >= 0 ? '+' : '-'}৳${netBalance.abs().toStringAsFixed(2)}',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 32,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const Gap(8),
                            Text(
                              netBalance >= 0 ? 'You are owed' : 'You owe',
                              style: TextStyle(
                                color: Colors.white.withOpacity(0.9),
                                fontSize: 14,
                                fontStyle: FontStyle.italic,
                              ),
                            ),
                          ],
                        ),
                      )
                          .animate(
                              key: ValueKey(
                                  '$_filter$_focusedDate')) // Animate when filter changes
                          .slideY(begin: 0.2, end: 0, duration: 400.ms)
                          .fadeIn(),

                      const Gap(24),

                      // 2. Summary Row
                      Row(
                        children: [
                          Expanded(
                            child: _SummaryCard(
                              title: 'Lent',
                              amount: totalIncome,
                              color: AppColors.success,
                              icon: PhosphorIconsRegular.arrowUpRight,
                            ),
                          ),
                          const Gap(16),
                          Expanded(
                            child: _SummaryCard(
                              title: 'Borrowed',
                              amount: totalExpense,
                              color: AppColors.warning,
                              icon: PhosphorIconsRegular.arrowDownLeft,
                            ),
                          ),
                        ],
                      ).animate().fadeIn(delay: 200.ms),

                      const Gap(24),

                      // 3. Ratio Bar
                      if (totalVolume > 0)
                        Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.05),
                                blurRadius: 10,
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Ratio',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const Gap(16),
                              ClipRRect(
                                borderRadius: BorderRadius.circular(10),
                                child: SizedBox(
                                  height: 20,
                                  child: Row(
                                    children: [
                                      Expanded(
                                        flex: (incomePercent * 100).toInt(),
                                        child:
                                            Container(color: AppColors.success),
                                      ),
                                      Expanded(
                                        flex: (expensePercent * 100).toInt(),
                                        child:
                                            Container(color: AppColors.warning),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              const Gap(12),
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      Container(
                                        width: 12,
                                        height: 12,
                                        decoration: const BoxDecoration(
                                          color: AppColors.success,
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                      const Gap(8),
                                      Text(
                                          '${(incomePercent * 100).toStringAsFixed(1)}% Lent'),
                                    ],
                                  ),
                                  Row(
                                    children: [
                                      Container(
                                        width: 12,
                                        height: 12,
                                        decoration: const BoxDecoration(
                                          color: AppColors.warning,
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                      const Gap(8),
                                      Text(
                                          '${(expensePercent * 100).toStringAsFixed(1)}% Borrowed'),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                          ),
                        )
                            .animate()
                            .fadeIn(delay: 300.ms)
                            .slideX(begin: 0.1, end: 0)
                      else
                        const Center(
                            child: Padding(
                          padding: EdgeInsets.all(32.0),
                          child: Text("No transactions in this period",
                              style: TextStyle(color: AppColors.textSecondary)),
                        ))
                    ],
                  ),
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, st) => Center(child: Text('Error: $e')),
            ),
          ),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final String title;
  final double amount;
  final Color color;
  final IconData icon;

  const _SummaryCard({
    required this.title,
    required this.amount,
    required this.color,
    required this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: color, size: 24),
          const Gap(12),
          Text(
            title,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
          const Gap(4),
          Text(
            '৳${amount.toStringAsFixed(0)}',
            style: TextStyle(
              color: color,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
