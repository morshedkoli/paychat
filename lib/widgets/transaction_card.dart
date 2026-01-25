import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:intl/intl.dart';
import '../core/models/message.dart';
import '../core/theme/app_colors.dart';

class TransactionMessageCard extends StatelessWidget {
  final Message message;
  final bool isMe;

  const TransactionMessageCard({
    super.key,
    required this.message,
    required this.isMe,
  });

  @override
  Widget build(BuildContext context) {
    // Defines the simplified status text
    String statusText = "Pending Approval";
    if (message.status == TransactionStatus.approved) statusText = "Approved";
    if (message.status == TransactionStatus.autoApproved) {
      statusText = "Auto Approved";
    }
    if (message.status == TransactionStatus.rejected) statusText = "Rejected";

    final showButtons = message.status == TransactionStatus.pending && !isMe;

    return Align(
      alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        width: 280,
        margin: const EdgeInsets.symmetric(vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.1),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Blue Header
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    Color(0xFF2E4C75),
                    Color(0xFF3E5C8A),
                  ],
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                ),
              ),
              child: Row(
                children: [
                  Text(
                    NumberFormat.simpleCurrency(name: 'BDT')
                        .currencySymbol, // using generic symbol or just text
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    NumberFormat.currency(locale: 'en_US', symbol: '')
                        .format(message.amount),
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Gap(8),
                  Expanded(
                    child: Text(
                      statusText,
                      style: TextStyle(
                        color: Colors.white.withOpacity(0.8),
                        fontSize: 13,
                      ),
                      textAlign: TextAlign.right,
                    ),
                  ),
                ],
              ),
            ),

            // Buttons Body
            if (showButtons)
              Padding(
                padding: const EdgeInsets.all(12),
                child: Row(
                  children: [
                    Expanded(
                      child: _StatusButton(
                        label: "Approve",
                        color: const Color(0xFF1ECAD3), // Teal
                        onTap: () {},
                      ),
                    ),
                    const Gap(12),
                    Expanded(
                      child: _StatusButton(
                        label: "Reject",
                        color: const Color(0xFFE74C3C), // Red
                        onTap: () {},
                      ),
                    ),
                  ],
                ),
              )
            else if (message.content.isNotEmpty)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  message.content,
                  style: const TextStyle(color: AppColors.textSecondary),
                ),
              ),
          ],
        ),
      ).animate().scale(duration: 300.ms, curve: Curves.easeOutBack),
    );
  }
}

class _StatusButton extends StatelessWidget {
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _StatusButton({
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      borderRadius: BorderRadius.circular(8),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          height: 36,
          alignment: Alignment.center,
          child: Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w600,
              fontSize: 14,
            ),
          ),
        ),
      ),
    );
  }
}
