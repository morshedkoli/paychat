import 'package:flutter/material.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/buttons.dart';

class ExportLedgerScreen extends StatefulWidget {
  const ExportLedgerScreen({super.key});

  @override
  State<ExportLedgerScreen> createState() => _ExportLedgerScreenState();
}

class _ExportLedgerScreenState extends State<ExportLedgerScreen> {
  String _selectedFormat = 'PDF';
  String _scope = 'This Chat';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.backgroundLight,
      appBar: AppBar(title: const Text("Export Ledger")),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _SectionTitle("Export Scope"),
            _buildRadioOption("This Chat", "This Chat"),
            _buildRadioOption("All Chats", "All Chats"),
            const Gap(24),
            const _SectionTitle("Date Range"),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.divider),
              ),
              child: Row(
                children: [
                  Icon(PhosphorIcons.calendarBlank(), color: AppColors.primary),
                  const Gap(12),
                  const Text("Last 30 Days"),
                  const Spacer(),
                  Icon(PhosphorIcons.caretDown(),
                      size: 16, color: AppColors.textSecondary),
                ],
              ),
            ),
            const Gap(24),
            const _SectionTitle("Format"),
            Row(
              children: [
                Expanded(
                    child: _FormatCard(
                        "PDF",
                        PhosphorIcons.filePdf(),
                        _selectedFormat == 'PDF',
                        () => setState(() => _selectedFormat = 'PDF'))),
                const Gap(12),
                Expanded(
                    child: _FormatCard(
                        "CSV",
                        PhosphorIcons.fileCsv(),
                        _selectedFormat == 'CSV',
                        () => setState(() => _selectedFormat = 'CSV'))),
                const Gap(12),
                Expanded(
                    child: _FormatCard(
                        "JSON",
                        PhosphorIcons.bracketsCurly(),
                        _selectedFormat == 'JSON',
                        () => setState(() => _selectedFormat = 'JSON'))),
              ],
            ),
            const Gap(40),
            PrimaryButton(
              label: "Export $_selectedFormat",
              icon: PhosphorIcons.downloadSimple(),
              onTap: () {
                // Mock export
                ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text("Export started...")));
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRadioOption(String title, String value) {
    return RadioListTile<String>(
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w500)),
      value: value,
      groupValue: _scope,
      activeColor: AppColors.primary,
      contentPadding: EdgeInsets.zero,
      onChanged: (v) => setState(() => _scope = v!),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String title;
  const _SectionTitle(this.title);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Text(
        title,
        style: const TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 16,
            color: AppColors.textPrimary),
      ),
    );
  }
}

class _FormatCard extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _FormatCard(this.label, this.icon, this.isSelected, this.onTap);

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          color: isSelected ? AppColors.primary.withOpacity(0.1) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppColors.primary : AppColors.divider,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(icon,
                color: isSelected ? AppColors.primary : AppColors.textSecondary,
                size: 32),
            const Gap(8),
            Text(
              label,
              style: TextStyle(
                fontWeight: FontWeight.bold,
                color: isSelected ? AppColors.primary : AppColors.textSecondary,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
