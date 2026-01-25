import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:intl/intl.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/buttons.dart';
import '../settings/export_ledger_screen.dart'; // Next

class MonthlySummaryScreen extends StatelessWidget {
  const MonthlySummaryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.backgroundLight,
      appBar: AppBar(title: const Text("Monthly Summary")),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Month Selector
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                IconButton(
                    icon: Icon(PhosphorIcons.caretLeft()), onPressed: () {}),
                const Text("October 2023",
                    style:
                        TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                IconButton(
                    icon: Icon(PhosphorIcons.caretRight()), onPressed: () {}),
              ],
            ),
            const Gap(24),

            // Cards
            const Row(
              children: [
                Expanded(
                    child: _SummaryCard(
                        label: "You Spent",
                        amount: 1250,
                        color: AppColors.textPrimary)),
                Gap(16),
                Expanded(
                    child: _SummaryCard(
                        label: "Net Balance",
                        amount: -320,
                        color: AppColors.danger)),
              ],
            ).animate().slideY(begin: 0.2),

            const Gap(16),

            const _SummaryCard(
                    label: "Total Receivable",
                    amount: 450,
                    color: AppColors.success,
                    fullWidth: true)
                .animate()
                .slideY(begin: 0.2, delay: 100.ms),

            const Gap(32),

            // Chart Placeholder
            Container(
              height: 200,
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.divider),
              ),
              alignment: Alignment.center,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(PhosphorIcons.chartBar(),
                      size: 48, color: AppColors.primary.withOpacity(0.5)),
                  const Gap(8),
                  const Text("Spending Trends Chart"),
                ],
              ),
            ).animate().fadeIn(delay: 200.ms),

            const Gap(32),

            PrimaryButton(
              label: "Generate PDF Report",
              icon: PhosphorIcons.filePdf(),
              onTap: () {
                // Open Export
                Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => const ExportLedgerScreen()));
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final String label;
  final double amount;
  final Color color;
  final bool fullWidth;

  const _SummaryCard({
    required this.label,
    required this.amount,
    required this.color,
    this.fullWidth = false,
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
            color: Colors.black.withOpacity(0.04),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(
                  fontSize: 12, color: AppColors.textSecondary)),
          const Gap(8),
          Text(
            NumberFormat.simpleCurrency().format(amount),
            style: TextStyle(
                fontSize: 24, fontWeight: FontWeight.bold, color: color),
          ),
        ],
      ),
    );
  }
}
