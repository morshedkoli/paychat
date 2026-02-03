import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/models/chat.dart';
import '../../core/providers/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../widgets/app_background.dart';
import '../../widgets/buttons.dart';

class AddExpenseScreen extends ConsumerStatefulWidget {
  final Chat? chat; // Chat context for 1-on-1 transactions

  const AddExpenseScreen({super.key, this.chat});

  @override
  ConsumerState<AddExpenseScreen> createState() => _AddExpenseScreenState();
}

class _AddExpenseScreenState extends ConsumerState<AddExpenseScreen> {
  String _amount = "0";
  String _note = "";
  bool _isLoading = false;

  void _onKeyPressed(String key) {
    setState(() {
      if (_amount == "0" && key != ".") {
        _amount = key;
      } else {
        if (key == "." && _amount.contains(".")) return;
        if (_amount.length < 10) {
          _amount += key;
        }
      }
    });
  }

  void _onBackspace() {
    setState(() {
      if (_amount.length > 1) {
        _amount = _amount.substring(0, _amount.length - 1);
      } else {
        _amount = "0";
      }
    });
  }

  Future<void> _addTransaction() async {
    if (_amount == "0" || widget.chat == null) return;

    final amount = double.tryParse(_amount);
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a valid amount')),
      );
      return;
    }

    setState(() => _isLoading = true);

    try {
      final currentUser = ref.read(currentUserProvider).value;
      if (currentUser == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please sign in first')),
        );
        return;
      }

      final firestoreService = ref.read(firestoreServiceProvider);

      // Create transaction (sender creates, receiver approves)
      await firestoreService.createTransaction(
        chatId: widget.chat!.id,
        senderId: currentUser.id,
        receiverId: widget.chat!.user.id,
        amount: amount,
        note: _note.isEmpty ? 'Transaction' : _note,
      );

      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Transaction of ৳$_amount sent for approval')),
        );
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Widget _buildKeypadRow(List<String> keys) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 6),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: keys.map((key) {
          if (key == 'backspace') {
            return _KeypadButton(
              icon: PhosphorIcons.backspace(),
              onTap: _onBackspace,
            );
          }
          return _KeypadButton(
            label: key,
            onTap: () => _onKeyPressed(key),
          );
        }).toList(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isKeyboardOpen = MediaQuery.of(context).viewInsets.bottom > 0;

    return Scaffold(
      body: AppBackground(
        showPattern: true,
        child: Scaffold(
          backgroundColor: Colors.transparent,
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            title: const Text("Add Transaction"),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text("Cancel"),
              )
            ],
          ),
          body: Column(
            children: [
              // Scrollable Top Content
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    children: [
                      const Gap(24),
                      // Amount Display
                      Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              widget.chat != null
                                  ? "To ${widget.chat!.user.name}"
                                  : "Amount",
                              style: Theme.of(context)
                                  .textTheme
                                  .bodyLarge
                                  ?.copyWith(
                                    color: AppColors.textSecondary,
                                  ),
                            ),
                            const Gap(8),
                            Text(
                              "৳$_amount",
                              style: const TextStyle(
                                fontSize: 48,
                                fontWeight: FontWeight.bold,
                                color: AppColors.primary,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const Gap(32),

                      // Note Input
                      Padding(
                        padding: AppSpacing.paddingHorizontalLg,
                        child: TextField(
                          onChanged: (value) => setState(() => _note = value),
                          decoration: InputDecoration(
                            hintText: "Add a note (optional)",
                            prefixIcon: Icon(PhosphorIcons.notepad()),
                            filled: true,
                            fillColor: Colors.white.withOpacity(0.9),
                            border: OutlineInputBorder(
                              borderRadius: AppSpacing.borderRadiusLg,
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),

              // Keypad / Action Button
              // Hide Keypad when system keyboard is open
              if (!isKeyboardOpen)
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.95),
                    borderRadius:
                        const BorderRadius.vertical(top: Radius.circular(32)),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.05),
                        blurRadius: 10,
                        offset: const Offset(0, -4),
                      ),
                    ],
                  ),
                  child: SafeArea(
                    top: false,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Gap(16),
                        // Number pad rows
                        _buildKeypadRow(['1', '2', '3']),
                        _buildKeypadRow(['4', '5', '6']),
                        _buildKeypadRow(['7', '8', '9']),
                        _buildKeypadRow(['.', '0', 'backspace']),
                        const Gap(8),
                        // Add Button
                        Padding(
                          padding: AppSpacing.paddingMd,
                          child: PrimaryButton(
                            label:
                                _isLoading ? "Sending..." : "Add Transaction",
                            onTap: _isLoading ? null : _addTransaction,
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else
                // When keyboard is open, just show the button at the bottom (or nothing/keyboard has action)
                // Typically we can leave the button or hide it.
                // Let's hide the button here to avoid obstruction, user can close keyboard.
                // Or better: Show a compact "Done" button or rely on keyboard "Done".
                const SizedBox(),
            ],
          ),
        ),
      ),
    );
  }
}

class _KeypadButton extends StatelessWidget {
  final String? label;
  final IconData? icon;
  final VoidCallback onTap;

  const _KeypadButton({
    this.label,
    this.icon,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 72,
      height: 56,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          splashColor: AppColors.primary.withOpacity(0.1),
          highlightColor: AppColors.primary.withOpacity(0.05),
          child: Center(
            child: icon != null
                ? Icon(icon, size: 26, color: AppColors.textSecondary)
                : Text(
                    label ?? "",
                    style: const TextStyle(
                      fontSize: 26,
                      fontWeight: FontWeight.w500,
                      color: AppColors.textPrimary,
                    ),
                  ),
          ),
        ),
      ),
    );
  }
}
