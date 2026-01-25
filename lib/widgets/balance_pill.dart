import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:intl/intl.dart';
import '../core/theme/app_colors.dart';
import '../core/theme/app_spacing.dart';
import '../core/utils/animations.dart';

/// Enhanced balance pill with animation support
class BalancePill extends StatefulWidget {
  final double balance;
  final bool animate;

  const BalancePill({
    super.key,
    required this.balance,
    this.animate = false,
  });

  @override
  State<BalancePill> createState() => _BalancePillState();
}

class _BalancePillState extends State<BalancePill>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  double _previousBalance = 0;

  @override
  void initState() {
    super.initState();
    _previousBalance = widget.balance;
    _pulseController = AnimationController(
      vsync: this,
      duration: AppAnimations.normal,
    );
  }

  @override
  void didUpdateWidget(BalancePill oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.animate && widget.balance != _previousBalance) {
      _previousBalance = widget.balance;
      _pulseController.forward(from: 0);
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.balance == 0) return const SizedBox.shrink();

    final isPositive = widget.balance > 0;
    final gradient = AppColors.getBalanceGradient(widget.balance);

    final text = isPositive
        ? "+৳${NumberFormat.decimalPattern().format(widget.balance)}"
        : "-৳${NumberFormat.decimalPattern().format(widget.balance.abs())}";

    return AnimatedBuilder(
      animation: _pulseController,
      builder: (context, child) {
        final scale = 1.0 + (0.1 * _pulseController.value);
        return Transform.scale(
          scale: scale,
          child: child,
        );
      },
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.sm + 2,
          vertical: AppSpacing.xs + 2,
        ),
        decoration: BoxDecoration(
          gradient: gradient,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          boxShadow: [
            BoxShadow(
              color: (isPositive ? AppColors.success : AppColors.warning)
                  .withOpacity(0.3),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Text(
          text,
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
            fontSize: 13,
          ),
        ),
      ).animate().fadeIn(duration: AppAnimations.fast),
    );
  }
}
