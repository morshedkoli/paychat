import 'package:flutter/material.dart';

import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../core/theme/app_colors.dart';

class CustomKeypad extends StatelessWidget {
  final ValueChanged<String> onKeyPressed;
  final VoidCallback onBackPressed;

  const CustomKeypad({
    super.key,
    required this.onKeyPressed,
    required this.onBackPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.backgroundLight,
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
      child: Column(
        children: [
          _buildRow(['1', '2', '3']),
          _buildRow(['4', '5', '6']),
          _buildRow(['7', '8', '9']),
          _buildRow(['.', '0', '<']),
        ],
      ),
    );
  }

  Widget _buildRow(List<String> keys) {
    return Expanded(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: keys.map((k) {
          if (k == '<') {
            return _buildKey(
              child: Icon(PhosphorIcons.backspace(), size: 24),
              onTap: onBackPressed,
            );
          }
          return _buildKey(
            child: Text(
              k,
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w600),
            ),
            onTap: () => onKeyPressed(k),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildKey({required Widget child, required VoidCallback onTap}) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(32),
        // Hover effect handled by InkWell default splash
        child: Container(
          alignment: Alignment.center,
          margin: const EdgeInsets.all(8),
          child: child,
        ),
      ),
    );
  }
}
