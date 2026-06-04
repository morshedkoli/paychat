import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/models/app_service.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/providers/service_providers.dart';

/// Bottom sheet shown when the buyer taps the Order button in a chat.
/// Fetches the partner's (seller's) active services and lets the buyer
/// pick one, add a note, and confirm the order.
class OrderServiceSheet extends ConsumerStatefulWidget {
  final AppUser currentUser;
  final String partnerUserId;
  final String partnerName;
  final String threadId;

  const OrderServiceSheet({
    super.key,
    required this.currentUser,
    required this.partnerUserId,
    required this.partnerName,
    required this.threadId,
  });

  @override
  ConsumerState<OrderServiceSheet> createState() => _OrderServiceSheetState();
}

class _OrderServiceSheetState extends ConsumerState<OrderServiceSheet> {
  AppService? _selected;
  final _noteCtrl = TextEditingController();
  final Map<String, TextEditingController> _answersCtrls = {};
  bool _isPlacing = false;
  List<AppService>? _services;
  bool _loadingServices = true;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    _loadServices();
  }

  void _clearAnswers() {
    for (final ctrl in _answersCtrls.values) {
      ctrl.dispose();
    }
    _answersCtrls.clear();
  }

  void _initAnswers(AppService s) {
    _clearAnswers();
    for (final req in s.requiredFields) {
      _answersCtrls[req] = TextEditingController();
    }
  }

  @override
  void dispose() {
    _noteCtrl.dispose();
    _clearAnswers();
    super.dispose();
  }

  Future<void> _loadServices() async {
    try {
      final services = await ref
          .read(serviceRepositoryProvider)
          .fetchActiveServices(widget.partnerUserId);
      if (mounted) {
        setState(() {
          _services = services;
          _loadingServices = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loadError = e.toString();
          _loadingServices = false;
        });
      }
    }
  }

  void _showValidationError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg, style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
      backgroundColor: AppColors.bgCard,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ));
  }

  Future<void> _placeOrder() async {
    if (_selected == null) return;

    final answers = <String, String>{};
    for (final req in _selected!.requiredFields) {
      final text = _answersCtrls[req]?.text.trim() ?? '';
      if (text.isEmpty) {
        _showValidationError('Please fill: $req');
        return;
      }
      answers[req] = text;
    }

    setState(() => _isPlacing = true);
    try {
      final orderRepo = ref.read(orderRepositoryProvider);
      await orderRepo.placeOrder(
        threadId: widget.threadId,
        serviceId: _selected!.id,
        serviceTitle: _selected!.title,
        serviceDescription: _selected!.description,
        price: _selected!.price,
        buyerId: widget.currentUser.uid,
        sellerId: widget.partnerUserId,
        buyerName: widget.currentUser.displayName,
        sellerName: widget.partnerName,
        buyerNote: _noteCtrl.text.trim(),
        requirementsAnswers: answers,
      );
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed to place order: $e',
              style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
          backgroundColor: AppColors.bgCard,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ));
      }
    } finally {
      if (mounted) setState(() => _isPlacing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        border: Border.all(color: AppColors.borderGlow),
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 28,
        top: 8,
        left: 20,
        right: 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Drag handle
          Center(
            child: Container(
              width: 40, height: 4,
              margin: const EdgeInsets.only(bottom: 16),
              decoration: BoxDecoration(
                color: AppColors.borderSubtle,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          Text(
            'Order a Service',
            style: GoogleFonts.dmSans(
              fontSize: 22,
              fontWeight: FontWeight.w700,
              color: AppColors.textPrimary,
              letterSpacing: 0.8,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 4),
          Text(
            'from ${widget.partnerName}',
            style: GoogleFonts.dmSans(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 20),

          // Service list
          if (_loadingServices)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: CircularProgressIndicator(color: AppColors.accentCyan, strokeWidth: 2.5),
              ),
            )
          else if (_loadError != null)
            Center(
              child: Text('Error loading services: $_loadError',
                  style: const TextStyle(color: AppColors.errorColor)),
            )
          else if (_services == null || _services!.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: Column(
                children: [
                  Icon(Icons.design_services_rounded,
                      color: AppColors.textMuted, size: 36),
                  const SizedBox(height: 10),
                  Text(
                    '${widget.partnerName} has no active services yet.',
                    style: GoogleFonts.dmSans(
                      color: AppColors.textSecondary,
                      fontSize: 14,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            )
          else ...[
            ConstrainedBox(
              constraints: const BoxConstraints(maxHeight: 280),
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: _services!.length,
                separatorBuilder: (context, index) => const SizedBox(height: 8),
                itemBuilder: (_, i) {
                  final s = _services![i];
                  final isSelected = _selected?.id == s.id;
                  return GestureDetector(
                    onTap: () {
                      setState(() {
                        if (isSelected) {
                          _selected = null;
                          _clearAnswers();
                        } else {
                          _selected = s;
                          _initAnswers(s);
                        }
                      });
                    },
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 180),
                      decoration: BoxDecoration(
                        color: isSelected
                            ? AppColors.accentCyan.withValues(alpha: 0.08)
                            : AppColors.bgSurface,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: isSelected ? AppColors.accentCyan : AppColors.borderSubtle,
                          width: isSelected ? 1.5 : 1,
                        ),
                      ),
                      padding: const EdgeInsets.all(14),
                      child: Row(
                        children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? AppColors.accentCyan.withValues(alpha: 0.15)
                                  : AppColors.bgCard,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: isSelected
                                    ? AppColors.accentCyan.withValues(alpha: 0.4)
                                    : AppColors.borderSubtle,
                              ),
                            ),
                            child: Icon(
                              Icons.design_services_rounded,
                              color: isSelected ? AppColors.accentCyan : AppColors.textMuted,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  s.title,
                                  style: GoogleFonts.dmSans(
                                    color: AppColors.textPrimary,
                                    fontSize: 14,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                                if (s.description.isNotEmpty)
                                  Text(
                                    s.description,
                                    style: GoogleFonts.dmSans(
                                      color: AppColors.textSecondary,
                                      fontSize: 12,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                            decoration: BoxDecoration(
                              gradient: isSelected ? AppColors.gradientCyan : null,
                              color: isSelected ? null : AppColors.bgCard,
                              borderRadius: BorderRadius.circular(20),
                              border: Border.all(
                                color: isSelected ? Colors.transparent : AppColors.borderSubtle,
                              ),
                            ),
                            child: Text(
                              '৳${s.price.toStringAsFixed(0)}',
                              style: GoogleFonts.dmSans(
                                color: isSelected ? AppColors.bgPrimary : AppColors.accentCyan,
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          if (isSelected)
                            Padding(
                              padding: const EdgeInsets.only(left: 8),
                              child: Icon(Icons.check_circle_rounded,
                                  color: AppColors.accentCyan, size: 20),
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),

            // Custom required fields & Note field (shown only when a service is selected)
            if (_selected != null) ...[
              if (_selected!.requiredFields.isNotEmpty) ...[
                const SizedBox(height: 14),
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text(
                    'Required Information',
                    style: GoogleFonts.dmSans(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.accentCyan,
                    ),
                  ),
                ),
                ..._selected!.requiredFields.map((req) {
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Container(
                      decoration: BoxDecoration(
                        color: AppColors.bgSurface,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppColors.borderSubtle),
                      ),
                      child: TextField(
                        controller: _answersCtrls[req],
                        style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 14),
                        decoration: InputDecoration(
                          filled: false,
                          hintText: '$req *',
                          hintStyle: GoogleFonts.dmSans(
                              color: AppColors.textMuted, fontSize: 13),
                          prefixIcon: Icon(Icons.help_outline_rounded,
                              color: AppColors.textSecondary, size: 20),
                          border: InputBorder.none,
                          contentPadding:
                              const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        ),
                      ),
                    ),
                  );
                }),
              ],
              const SizedBox(height: 14),
              Container(
                decoration: BoxDecoration(
                  color: AppColors.bgSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: TextField(
                  controller: _noteCtrl,
                  maxLines: 3,
                  style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 14),
                  decoration: InputDecoration(
                    filled: false,
                    hintText: 'Add a note for the seller (optional)…',
                    hintStyle: GoogleFonts.dmSans(
                        color: AppColors.textMuted, fontSize: 13),
                    prefixIcon: Icon(Icons.note_alt_rounded,
                        color: AppColors.textSecondary, size: 20),
                    border: InputBorder.none,
                    contentPadding:
                        const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  ),
                ),
              ),
            ],

            const SizedBox(height: 20),

            // Place Order button (disabled if nothing selected)
            AnimatedOpacity(
              duration: const Duration(milliseconds: 200),
              opacity: _selected != null ? 1 : 0.4,
              child: Container(
                decoration: BoxDecoration(
                  gradient: AppColors.gradientCyan,
                  borderRadius: BorderRadius.circular(14),
                  boxShadow: _selected != null ? AppColors.cyanGlow : [],
                ),
                child: ElevatedButton(
                  onPressed: (_selected != null && !_isPlacing) ? _placeOrder : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.transparent,
                    shadowColor: Colors.transparent,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: _isPlacing
                      ? const SizedBox(width: 22, height: 22,
                          child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5))
                      : Text(
                          _selected != null
                              ? 'Place Order • ৳${_selected!.price.toStringAsFixed(0)}'
                              : 'Select a service above',
                          style: GoogleFonts.dmSans(
                            color: AppColors.bgPrimary,
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
