import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:intl/intl.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../core/models/user.dart';
import '../core/theme/app_colors.dart';

class GroupExpenseDetailCard extends StatefulWidget {
  final String title;
  final double amount;
  final List<User>
      members; // In real app, would have approval status per member

  const GroupExpenseDetailCard({
    super.key,
    required this.title,
    required this.amount,
    required this.members,
  });

  @override
  State<GroupExpenseDetailCard> createState() => _GroupExpenseDetailCardState();
}

class _GroupExpenseDetailCardState extends State<GroupExpenseDetailCard> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: 300.ms,
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        children: [
          // Header
          InkWell(
            onTap: () => setState(() => _isExpanded = !_isExpanded),
            child: Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.primaryLight.withOpacity(0.1),
                    shape: BoxShape.circle,
                  ),
                  child:
                      Icon(PhosphorIcons.receipt(), color: AppColors.primary),
                ),
                const Gap(16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        style: const TextStyle(
                            fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      const Text(
                        "Paid by Alice",
                        style: TextStyle(
                            fontSize: 12, color: AppColors.textSecondary),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      NumberFormat.simpleCurrency().format(widget.amount),
                      style: const TextStyle(
                          fontWeight: FontWeight.bold, fontSize: 16),
                    ),
                    Icon(
                      _isExpanded
                          ? PhosphorIcons.caretUp()
                          : PhosphorIcons.caretDown(),
                      size: 16,
                      color: AppColors.textSecondary,
                    ),
                  ],
                ),
              ],
            ),
          ),

          // Expanded Content
          AnimatedCrossFade(
            firstChild: const SizedBox(width: double.infinity), // Collapsed
            secondChild: Column(
              children: [
                const Divider(height: 24),
                // Progress Bar
                LinearProgressIndicator(
                  value: 0.75, // Mock
                  backgroundColor: AppColors.divider,
                  color: AppColors.success,
                  borderRadius: BorderRadius.circular(4),
                ),
                const Gap(8),
                const Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text("3 of 4 approved",
                        style: TextStyle(
                            fontSize: 12, color: AppColors.textSecondary)),
                  ],
                ),
                const Gap(16),

                // Member List
                ...widget.members.map((user) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Row(
                        children: [
                          CircleAvatar(radius: 12, child: Text(user.name[0])),
                          const Gap(8),
                          Expanded(child: Text(user.name)),
                          if (user.isRegistered) // Mock logic for "Approved"
                            Icon(PhosphorIcons.checkCircle(),
                                color: AppColors.success, size: 18)
                          else
                            Icon(PhosphorIcons.clock(),
                                color: AppColors.warning, size: 18),
                        ],
                      ),
                    )),
              ],
            ),
            crossFadeState: _isExpanded
                ? CrossFadeState.showSecond
                : CrossFadeState.showFirst,
            duration: 300.ms,
          ),
        ],
      ),
    );
  }
}
