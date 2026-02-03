import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:gap/gap.dart';
import 'package:intl/intl.dart';
import '../core/models/chat.dart';
import '../core/theme/app_colors.dart';
import '../core/theme/app_spacing.dart';
import '../core/theme/app_shadows.dart';
import '../core/utils/animations.dart';
import 'balance_pill.dart';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/providers/providers.dart';

/// Enhanced chat card with hover animation and smooth transitions
class ChatCard extends ConsumerStatefulWidget {
  final Chat chat;
  final VoidCallback onTap;
  final int? index;
  final String? heroTagPrefix;

  const ChatCard({
    super.key,
    required this.chat,
    required this.onTap,
    this.index,
    this.heroTagPrefix,
  });

  @override
  ConsumerState<ChatCard> createState() => _ChatCardState();
}

class _ChatCardState extends ConsumerState<ChatCard> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final balanceAsync = ref.watch(balanceProvider(widget.chat.id));
    final double balance = balanceAsync.when(
      data: (value) => value,
      loading: () => widget.chat.balance,
      error: (_, __) => widget.chat.balance,
    );

    return GestureDetector(
      onTapDown: (_) => setState(() => _isPressed = true),
      onTapUp: (_) => setState(() => _isPressed = false),
      onTapCancel: () => setState(() => _isPressed = false),
      onTap: widget.onTap,
      child: AnimatedContainer(
        duration: AppAnimations.fast,
        curve: AppAnimations.smoothCurve,
        transform: Matrix4.identity()..scale(_isPressed ? 0.98 : 1.0),
        margin: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.xs,
        ),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: AppSpacing.borderRadiusLg,
          boxShadow: _isPressed ? AppShadows.sm : AppShadows.md,
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            vertical: AppSpacing.md,
            horizontal: AppSpacing.md,
          ),
          child: Row(
            children: [
              // Avatar with hero animation support
              Hero(
                tag: '${widget.heroTagPrefix ?? 'avatar'}_${widget.chat.id}',
                child: _buildAvatar(),
              ),
              const Gap(AppSpacing.md),

              // Content
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.chat.user.name,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const Gap(AppSpacing.xs),
                    Text(
                      widget.chat.lastMessage,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),

              // Right side: Balance pill or time
              if (balance != 0)
                BalancePill(balance: balance, animate: true)
              else
                Text(
                  DateFormat('h:mm a').format(widget.chat.lastActive),
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
            ],
          ),
        ),
      ),
    )
        .animate(
          delay: widget.index != null
              ? AppAnimations.staggerDelay(widget.index!)
              : Duration.zero,
        )
        .fadeIn(
          duration: AppAnimations.fadeInDuration,
          curve: AppAnimations.fadeInCurve,
        )
        .slideY(
          begin: 0.1,
          end: 0,
          duration: AppAnimations.slideInDuration,
          curve: AppAnimations.slideInCurve,
        );
  }

  Widget _buildAvatar() {
    return Container(
      width: AppSpacing.avatarMd,
      height: AppSpacing.avatarMd,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: widget.chat.user.avatarUrl == null
            ? LinearGradient(
                colors: [
                  AppColors.primaryLight.withOpacity(0.2),
                  AppColors.primary.withOpacity(0.1),
                ],
              )
            : null,
        boxShadow: AppShadows.sm,
      ),
      child: widget.chat.user.avatarUrl != null
          ? ClipOval(
              child: Image.network(
                widget.chat.user.avatarUrl!,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) =>
                    _buildAvatarFallback(),
              ),
            )
          : _buildAvatarFallback(),
    );
  }

  Widget _buildAvatarFallback() {
    return Center(
      child: Text(
        widget.chat.user.name.isNotEmpty
            ? widget.chat.user.name[0].toUpperCase()
            : '?',
        style: const TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: AppColors.primary,
        ),
      ),
    );
  }
}
