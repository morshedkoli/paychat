import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/models/app_service.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/service_providers.dart';

class ManageServicesScreen extends ConsumerWidget {
  const ManageServicesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(themeControllerProvider);
    final session = ref.watch(sessionStateProvider);
    final uid = session.user?.uid;

    if (uid == null) {
      return Scaffold(
        backgroundColor: AppColors.bgPrimary,
        body: Center(child: CircularProgressIndicator(color: AppColors.accentCyan)),
      );
    }

    final servicesAsync = ref.watch(userServicesProvider(uid));

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.accentCyan,
        iconTheme: IconThemeData(color: AppColors.accentCyan),
        title: Text(
          'My Services',
          style: GoogleFonts.dmSans(
            color: AppColors.accentCyan,
            fontSize: 20,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.8,
          ),
        ),
      ),
      floatingActionButton: Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: AppColors.gradientCyan,
          boxShadow: AppColors.cyanGlow,
        ),
        child: FloatingActionButton(
          onPressed: () => _showAddEditSheet(context, ref, uid, null),
          backgroundColor: Colors.transparent,
          foregroundColor: Colors.white,
          elevation: 0,
          child: const Icon(Icons.add_rounded),
        ),
      ),
      body: servicesAsync.when(
        data: (services) {
          if (services.isEmpty) {
            return _buildEmpty(context, ref, uid);
          }
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 100),
            itemCount: services.length,
            itemBuilder: (_, i) => _ServiceCard(
              service: services[i],
              onEdit: () => _showAddEditSheet(context, ref, uid, services[i]),
              onDelete: () => _confirmDelete(context, ref, uid, services[i]),
            ),
          );
        },
        loading: () => Center(
          child: CircularProgressIndicator(color: AppColors.accentCyan, strokeWidth: 2.5),
        ),
        error: (e, _) => Center(
          child: Text('Error: $e', style: const TextStyle(color: AppColors.errorColor)),
        ),
      ),
    );
  }

  Widget _buildEmpty(BuildContext context, WidgetRef ref, String uid) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.accentCyan.withValues(alpha: 0.08),
              shape: BoxShape.circle,
              border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.2)),
            ),
            child: Icon(Icons.design_services_rounded, size: 36, color: AppColors.accentCyan),
          ),
          const SizedBox(height: 20),
          Text(
            'No services yet',
            style: GoogleFonts.dmSans(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: AppColors.textPrimary,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Tap + to add your first service',
            style: GoogleFonts.dmSans(
              fontSize: 14,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  void _showAddEditSheet(BuildContext context, WidgetRef ref, String uid, AppService? existing) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _AddEditServiceSheet(uid: uid, existing: existing),
    );
  }

  void _confirmDelete(BuildContext context, WidgetRef ref, String uid, AppService service) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: AppColors.bgCard,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          'Delete Service',
          style: GoogleFonts.dmSans(
            color: AppColors.textPrimary,
            fontWeight: FontWeight.w700,
            fontSize: 20,
          ),
        ),
        content: Text(
          'Delete "${service.title}"? This cannot be undone.',
          style: GoogleFonts.dmSans(color: AppColors.textSecondary, fontSize: 14),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel', style: GoogleFonts.dmSans(color: AppColors.textMuted)),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(context);
              await ref.read(serviceRepositoryProvider).deleteService(
                sellerId: uid,
                serviceId: service.id,
              );
            },
            child: Text(
              'Delete',
              style: GoogleFonts.dmSans(color: AppColors.errorColor, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Service Card ─────────────────────────────────────────────────────────────

class _ServiceCard extends StatelessWidget {
  final AppService service;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const _ServiceCard({required this.service, required this.onEdit, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.borderGlow),
        boxShadow: AppColors.cardGlow,
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    service.title,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    gradient: AppColors.gradientCyan,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    '৳ ${service.price.toStringAsFixed(0)}',
                    style: GoogleFonts.dmSans(
                      color: AppColors.bgPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
            if (service.description.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                service.description,
                style: GoogleFonts.dmSans(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                  height: 1.4,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
            if (service.category.isNotEmpty) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.accentViolet.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: AppColors.accentViolet.withValues(alpha: 0.3)),
                ),
                child: Text(
                  service.category,
                  style: GoogleFonts.dmSans(
                    color: AppColors.accentViolet,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: service.isActive ? AppColors.accentGreen : AppColors.textMuted,
                    shape: BoxShape.circle,
                    boxShadow: service.isActive ? AppColors.greenGlow : null,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  service.isActive ? 'Active' : 'Inactive',
                  style: GoogleFonts.dmSans(
                    color: service.isActive ? AppColors.accentGreen : AppColors.textMuted,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const Spacer(),
                IconButton(
                  icon: Icon(Icons.edit_rounded, size: 18, color: AppColors.accentCyan),
                  onPressed: onEdit,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                ),
                const SizedBox(width: 4),
                IconButton(
                  icon: const Icon(Icons.delete_outline_rounded, size: 18, color: AppColors.errorColor),
                  onPressed: onDelete,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ── Add / Edit Bottom Sheet ───────────────────────────────────────────────────

class _AddEditServiceSheet extends ConsumerStatefulWidget {
  final String uid;
  final AppService? existing;

  const _AddEditServiceSheet({required this.uid, this.existing});

  @override
  ConsumerState<_AddEditServiceSheet> createState() => _AddEditServiceSheetState();
}

class _AddEditServiceSheetState extends ConsumerState<_AddEditServiceSheet> {
  final _titleCtrl = TextEditingController();
  final _descCtrl = TextEditingController();
  final _priceCtrl = TextEditingController();
  final _categoryCtrl = TextEditingController();
  final List<TextEditingController> _requiredFieldsCtrls = [];
  bool _isActive = true;
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    if (widget.existing != null) {
      final s = widget.existing!;
      _titleCtrl.text = s.title;
      _descCtrl.text = s.description;
      _priceCtrl.text = s.price.toStringAsFixed(0);
      _categoryCtrl.text = s.category;
      _isActive = s.isActive;
      for (final req in s.requiredFields) {
        _requiredFieldsCtrls.add(TextEditingController(text: req));
      }
    }
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    _priceCtrl.dispose();
    _categoryCtrl.dispose();
    for (final ctrl in _requiredFieldsCtrls) {
      ctrl.dispose();
    }
    super.dispose();
  }

  void _save() async {
    final title = _titleCtrl.text.trim();
    final price = double.tryParse(_priceCtrl.text.trim());

    if (title.isEmpty) {
      _snack('Service title is required');
      return;
    }
    if (price == null || price <= 0) {
      _snack('Enter a valid price');
      return;
    }

    final requiredFields = _requiredFieldsCtrls
        .map((c) => c.text.trim())
        .where((text) => text.isNotEmpty)
        .toList();

    setState(() => _isSaving = true);
    try {
      final repo = ref.read(serviceRepositoryProvider);
      if (widget.existing == null) {
        await repo.addService(
          sellerId: widget.uid,
          title: title,
          description: _descCtrl.text.trim(),
          price: price,
          category: _categoryCtrl.text.trim(),
          requiredFields: requiredFields,
        );
      } else {
        await repo.updateService(
          sellerId: widget.uid,
          serviceId: widget.existing!.id,
          title: title,
          description: _descCtrl.text.trim(),
          price: price,
          category: _categoryCtrl.text.trim(),
          isActive: _isActive,
          requiredFields: requiredFields,
        );
      }
      if (mounted) Navigator.pop(context);
    } catch (e) {
      _snack('Error: $e');
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg, style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
      backgroundColor: AppColors.bgCard,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.existing != null;
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        border: Border.all(color: AppColors.borderGlow),
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 28,
        top: 8,
        left: 24,
        right: 24,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Center(
              child: Container(
                width: 40, height: 4,
                margin: const EdgeInsets.only(bottom: 20),
                decoration: BoxDecoration(
                  color: AppColors.borderSubtle,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Text(
              isEdit ? 'Edit Service' : 'Add Service',
              style: GoogleFonts.dmSans(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
                letterSpacing: 0.8,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            _field(_titleCtrl, 'Service Title *', Icons.design_services_rounded),
            const SizedBox(height: 14),
            _field(_descCtrl, 'Description (optional)', Icons.description_rounded, maxLines: 3),
            const SizedBox(height: 14),
            _field(_priceCtrl, 'Price (৳) *', Icons.attach_money_rounded,
                keyboard: TextInputType.number),
            const SizedBox(height: 14),
            _field(_categoryCtrl, 'Category (e.g. Design, Dev)', Icons.label_rounded),
            const SizedBox(height: 20),
            // Required fields section
            Row(
              children: [
                Text(
                  'Required Buyer Info',
                  style: GoogleFonts.dmSans(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.accentCyan,
                  ),
                ),
                const Spacer(),
                TextButton.icon(
                  onPressed: () {
                    setState(() {
                      _requiredFieldsCtrls.add(TextEditingController());
                    });
                  },
                  icon: const Icon(Icons.add_rounded, size: 16),
                  label: Text('Add Field', style: GoogleFonts.dmSans(fontSize: 13)),
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.accentCyan,
                    padding: EdgeInsets.zero,
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ],
            ),
            if (_requiredFieldsCtrls.isEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 8, bottom: 12),
                child: Text(
                  'No custom required fields. Buyers will only see the optional note.',
                  style: GoogleFonts.dmSans(
                    fontSize: 12,
                    color: AppColors.textMuted,
                  ),
                ),
              )
            else
              ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: _requiredFieldsCtrls.length,
                itemBuilder: (context, index) {
                  return Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(
                      children: [
                        Expanded(
                          child: _field(
                            _requiredFieldsCtrls[index],
                            'Requirement (e.g. Logo Text, Website Url)',
                            Icons.help_outline_rounded,
                          ),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          icon: const Icon(Icons.remove_circle_outline_rounded, color: AppColors.errorColor),
                          onPressed: () {
                            setState(() {
                              final ctrl = _requiredFieldsCtrls.removeAt(index);
                              ctrl.dispose();
                            });
                          },
                        ),
                      ],
                    ),
                  );
                },
              ),
            if (isEdit) ...[
              const SizedBox(height: 16),
              Row(
                children: [
                  Text('Active', style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 15)),
                  const Spacer(),
                  Switch(
                    value: _isActive,
                    onChanged: (v) => setState(() => _isActive = v),
                    activeThumbColor: AppColors.accentCyan,
                  ),
                ],
              ),
            ],
            const SizedBox(height: 24),
            Container(
              decoration: BoxDecoration(
                gradient: _isSaving ? null : AppColors.gradientCyan,
                color: _isSaving ? AppColors.bgSurface : null,
                borderRadius: BorderRadius.circular(14),
                boxShadow: _isSaving ? [] : AppColors.cyanGlow,
              ),
              child: ElevatedButton(
                onPressed: _isSaving ? null : _save,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.transparent,
                  shadowColor: Colors.transparent,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                ),
                child: _isSaving
                    ? const SizedBox(width: 22, height: 22,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5))
                    : Text(
                        isEdit ? 'Save Changes' : 'Add Service',
                        style: GoogleFonts.dmSans(
                          color: AppColors.bgPrimary,
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _field(
    TextEditingController ctrl,
    String hint,
    IconData icon, {
    int maxLines = 1,
    TextInputType keyboard = TextInputType.text,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: TextField(
        controller: ctrl,
        maxLines: maxLines,
        keyboardType: keyboard,
        style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 15),
        decoration: InputDecoration(
          filled: false,
          hintText: hint,
          hintStyle: GoogleFonts.dmSans(color: AppColors.textMuted, fontSize: 14),
          prefixIcon: Icon(icon, color: AppColors.textSecondary, size: 20),
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        ),
      ),
    );
  }
}
