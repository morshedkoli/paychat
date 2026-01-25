import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/buttons.dart';

class ExpenseApprovedScreen extends StatelessWidget {
  const ExpenseApprovedScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(32),
                decoration: BoxDecoration(
                  color: AppColors.success.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: Icon(PhosphorIcons.check(),
                    size: 64, color: AppColors.success),
              ).animate().scale(curve: Curves.elasticOut, duration: 800.ms),
              const Gap(32),
              const Text(
                "Expense Added!",
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: AppColors.textPrimary,
                ),
              ).animate().fadeIn().slideY(begin: 0.5),
              const Gap(8),
              const Text(
                "The group has been notified.",
                style: TextStyle(color: AppColors.textSecondary),
              ).animate().fadeIn(delay: 200.ms),
              const Gap(48),
              PrimaryButton(
                label: "Back to Chat",
                onTap: () {
                  Navigator.popUntil(context, (route) => route.isFirst);
                },
              ).animate().fadeIn(delay: 400.ms),
            ],
          ),
        ),
      ),
    );
  }
}
