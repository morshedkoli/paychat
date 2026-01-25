import 'package:flutter/material.dart';
import '../core/models/user.dart';
import '../core/theme/app_colors.dart';

class StackedAvatars extends StatelessWidget {
  final List<User> users;
  final double size;
  final double overlap;

  const StackedAvatars({
    super.key,
    required this.users,
    this.size = 32,
    this.overlap = 0.4, // Percentage overlap
  });

  @override
  Widget build(BuildContext context) {
    final overlapPixels = size * overlap;

    return SizedBox(
      height: size,
      width: (size * users.length) - (overlapPixels * (users.length - 1)),
      child: Stack(
        children: List.generate(users.length, (index) {
          final user = users[index];
          return Positioned(
            left: index * (size - overlapPixels),
            child: Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 2),
                color: AppColors.primaryLight.withOpacity(0.2),
              ),
              alignment: Alignment.center,
              child: Text(
                user.name.substring(0, 1),
                style: TextStyle(
                  fontSize: size * 0.4,
                  fontWeight: FontWeight.bold,
                  color: AppColors.primary,
                ),
              ),
            ),
          );
        }),
      ),
    );
  }
}

class UserAvatar extends StatelessWidget {
  final User? user;
  final double radius;
  final VoidCallback? onTap;

  const UserAvatar({
    super.key,
    this.user,
    this.radius = 20,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    // 1. Determine Image URL
    final String? imageUrl =
        (user?.avatarUrl != null && user!.avatarUrl!.isNotEmpty)
            ? user!.avatarUrl
            : null;

    // 2. Determine Initials
    String initials = "?";
    if (user != null && user!.name.isNotEmpty) {
      initials = user!.name.trim().substring(0, 1).toUpperCase();
    }

    Widget avatarContent;

    if (imageUrl != null) {
      avatarContent = CircleAvatar(
        radius: radius,
        backgroundColor: AppColors.backgroundLight,
        backgroundImage: NetworkImage(imageUrl),
        onBackgroundImageError: (_, __) {
          // Trigger fallback if network load fails?
          // CircleAvatar doesn't easily swap to child on error without custom handling.
          // For simplicity, if URL exists we try it.
          // A more robust way uses existing packages or a custom Image builder.
        },
      );
    } else {
      // Fallback: Initials
      avatarContent = CircleAvatar(
        radius: radius,
        backgroundColor: AppColors.primaryLight.withOpacity(0.3),
        child: Text(
          initials,
          style: TextStyle(
            fontSize: radius * 0.8,
            fontWeight: FontWeight.bold,
            color: AppColors.primary,
          ),
        ),
      );
    }

    if (onTap != null) {
      return GestureDetector(onTap: onTap, child: avatarContent);
    }
    return avatarContent;
  }
}
