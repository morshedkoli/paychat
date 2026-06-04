import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/services/user_repository.dart';

// ---------------------------------------------------------------------------
// Country data
// ---------------------------------------------------------------------------
class _Country {
  final String iso;
  final String name;
  final String dialCode;
  final String flag;
  const _Country(this.iso, this.name, this.dialCode, this.flag);
}

const _countries = [
  _Country('BD', 'Bangladesh', '+880', '🇧🇩'),
  _Country('US', 'United States', '+1', '🇺🇸'),
  _Country('GB', 'United Kingdom', '+44', '🇬🇧'),
  _Country('IN', 'India', '+91', '🇮🇳'),
  _Country('PK', 'Pakistan', '+92', '🇵🇰'),
  _Country('CA', 'Canada', '+1', '🇨🇦'),
  _Country('AU', 'Australia', '+61', '🇦🇺'),
  _Country('MY', 'Malaysia', '+60', '🇲🇾'),
  _Country('SG', 'Singapore', '+65', '🇸🇬'),
  _Country('AE', 'UAE', '+971', '🇦🇪'),
  _Country('SA', 'Saudi Arabia', '+966', '🇸🇦'),
  _Country('TR', 'Turkey', '+90', '🇹🇷'),
  _Country('DE', 'Germany', '+49', '🇩🇪'),
  _Country('FR', 'France', '+33', '🇫🇷'),
  _Country('JP', 'Japan', '+81', '🇯🇵'),
  _Country('CN', 'China', '+86', '🇨🇳'),
  _Country('KR', 'South Korea', '+82', '🇰🇷'),
  _Country('BR', 'Brazil', '+55', '🇧🇷'),
  _Country('NG', 'Nigeria', '+234', '🇳🇬'),
  _Country('ZA', 'South Africa', '+27', '🇿🇦'),
];

// ---------------------------------------------------------------------------
// PhoneSetupScreen — single step, uniqueness check only, no OTP
// ---------------------------------------------------------------------------
class PhoneSetupScreen extends ConsumerStatefulWidget {
  final AppUser user;
  const PhoneSetupScreen({super.key, required this.user});

  @override
  ConsumerState<PhoneSetupScreen> createState() => _PhoneSetupScreenState();
}

class _PhoneSetupScreenState extends ConsumerState<PhoneSetupScreen>
    with SingleTickerProviderStateMixin {
  _Country _selectedCountry = _countries.first;
  final _phoneCtrl = TextEditingController();
  final _phoneFocus = FocusNode();
  bool _phoneHasFocus = false;

  bool _isLoading = false;
  String? _error;

  // entry animation
  late final AnimationController _entryCtrl;
  late final Animation<double> _entryFade;
  late final Animation<Offset> _entrySlide;

  @override
  void initState() {
    super.initState();

    _entryCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 700),
    )..forward();
    _entryFade = CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOut);
    _entrySlide = Tween<Offset>(
      begin: const Offset(0, 0.05),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _entryCtrl, curve: Curves.easeOut));

    _phoneFocus.addListener(
      () => setState(() => _phoneHasFocus = _phoneFocus.hasFocus),
    );
  }

  @override
  void dispose() {
    _entryCtrl.dispose();
    _phoneCtrl.dispose();
    _phoneFocus.dispose();
    super.dispose();
  }

  // ── computed E.164 number ─────────────────────────────────────────────────
  String get _e164 {
    final digits = _phoneCtrl.text.replaceAll(RegExp(r'[^\d]'), '');
    return UserRepository.normalizePhone('${_selectedCountry.dialCode}$digits');
  }

  // ── save phone — unique check then write ──────────────────────────────────
  Future<void> _savePhone() async {
    final digits = _phoneCtrl.text.replaceAll(RegExp(r'[^\d]'), '');
    if (digits.length < 6) {
      setState(() => _error = 'Please enter a valid phone number.');
      return;
    }

    setState(() { _isLoading = true; _error = null; });

    try {
      final userRepo = ref.read(userRepositoryProvider);
      final sessionNotifier = ref.read(sessionStateProvider.notifier);

      // ── uniqueness check ──────────────────────────────────────────────────
      final existing = await userRepo.findUserByPhone(_e164);
      if (existing != null && existing.uid != widget.user.uid) {
        setState(() {
          _isLoading = false;
          _error = 'This number is already linked to another account.';
        });
        return;
      }

      // ── save to Firestore ─────────────────────────────────────────────────
      await userRepo.updatePhoneNumber(widget.user.uid, _e164);

      // ── advance session so the app navigates forward ──────────────────────
      await sessionNotifier.ensureProfile(
        uid: widget.user.uid,
        phoneNumber: _e164,
        displayName: widget.user.fullName.isNotEmpty ? widget.user.fullName : null,
        photoUrl: widget.user.photoUrl.isNotEmpty ? widget.user.photoUrl : null,
        email: widget.user.email.isNotEmpty ? widget.user.email : null,
      );
    } catch (e) {
      if (mounted) {
        setState(() { _isLoading = false; _error = 'Something went wrong. Please try again.'; });
      }
    }
  }

  // ── country picker ────────────────────────────────────────────────────────
  Future<void> _pickCountry() async {
    final picked = await showModalBottomSheet<_Country>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => _CountryPickerSheet(
        selected: _selectedCountry,
        onSelect: (c) => Navigator.pop(context, c),
      ),
    );
    if (picked != null) setState(() => _selectedCountry = picked);
  }

  // ── build ─────────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      body: Stack(
        children: [
          // Deep emerald gradient — same as login
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    AppColors.bgPrimary,
                    AppColors.bgCard,
                    AppColors.bgSurface,
                  ],
                  stops: const [0.0, 0.55, 1.0],
                ),
              ),
            ),
          ),

          // Content
          SafeArea(
            child: FadeTransition(
              opacity: _entryFade,
              child: SlideTransition(
                position: _entrySlide,
                child: Column(
                  children: [
                    _buildTopBar(),
                    Expanded(child: _buildBody()),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── top bar ───────────────────────────────────────────────────────────────
  Widget _buildTopBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Add Phone Number',
                  style: GoogleFonts.dmSans(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                    letterSpacing: -0.3,
                  ),
                ),
                Text(
                  'Link your number to your PayChat account',
                  style: GoogleFonts.dmSans(
                    fontSize: 13,
                    color: AppColors.textSecondary,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
          // Frosted glass badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
            decoration: BoxDecoration(
              color: AppColors.bgSurface,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white.withValues(alpha: 0.22)),
            ),
            child: Text(
              'Setup',
              style: GoogleFonts.dmSans(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── body ──────────────────────────────────────────────────────────────────
  Widget _buildBody() {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 36, 20, 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Pulsing icon hero
          Center(child: _PulsingIcon(icon: Icons.phone_android_rounded)),
          const SizedBox(height: 32),

          // Headline
          Text(
            'Your number,\nyour identity',
            textAlign: TextAlign.center,
            style: GoogleFonts.dmSans(
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: Colors.white,
              letterSpacing: -0.5,
              height: 1.15,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'Friends can find you by this number.\nIt stays private and is never shared publicly.',
            textAlign: TextAlign.center,
            style: GoogleFonts.dmSans(
              fontSize: 14,
              color: AppColors.textSecondary,
              height: 1.65,
            ),
          ),
          const SizedBox(height: 40),

          // Phone input
          _buildPhoneInputCard(),
          const SizedBox(height: 8),

          // Helper row — number preview
          if (_phoneCtrl.text.replaceAll(RegExp(r'[^\d]'), '').length >= 5)
            Padding(
              padding: const EdgeInsets.only(left: 4, bottom: 4),
              child: Row(
                children: [
                  Icon(Icons.check_circle_outline_rounded,
                      size: 13,
                      color: AppColors.primaryGreen.withValues(alpha: 0.8)),
                  const SizedBox(width: 5),
                  Text(
                    _e164,
                    style: GoogleFonts.dmSans(
                      fontSize: 12,
                      color: AppColors.primaryGreen.withValues(alpha: 0.80),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),

          // Error
          if (_error != null) ...[
            const SizedBox(height: 12),
            _buildErrorBanner(_error!),
          ],

          const SizedBox(height: 28),

          // Save button
          _buildSaveButton(),

          const SizedBox(height: 20),

          // Privacy note
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.lock_outline_rounded,
                size: 12,
                color: Colors.white.withValues(alpha: 0.35),
              ),
              const SizedBox(width: 6),
              Text(
                'Your number is never shared publicly',
                style: GoogleFonts.dmSans(
                  fontSize: 12,
                  color: Colors.white.withValues(alpha: 0.35),
                ),
              ),
            ],
          ),

          const SizedBox(height: 24),

          // Info card about what the number is used for
          _buildInfoCard(),
        ],
      ),
    );
  }

  // ── phone input card ──────────────────────────────────────────────────────
  Widget _buildPhoneInputCard() {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      decoration: BoxDecoration(
        // White frosted glass — clearly visible against the dark green background
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: _error != null
              ? AppColors.errorColor
              : _phoneHasFocus
                  ? AppColors.primaryGreenBright
                  : const Color(0xFFD4EDE0),
          width: _phoneHasFocus ? 2 : 1.5,
        ),
        boxShadow: [
          BoxShadow(
            color: _phoneHasFocus
                ? AppColors.primaryGreen.withValues(alpha: 0.25)
                : Colors.black.withValues(alpha: 0.12),
            blurRadius: _phoneHasFocus ? 20 : 8,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          // Country picker trigger
          GestureDetector(
            onTap: _pickCountry,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
              decoration: const BoxDecoration(
                border: Border(
                  right: BorderSide(
                    color: Color(0xFFD4EDE0),
                    width: 1.5,
                  ),
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _selectedCountry.flag,
                    style: const TextStyle(fontSize: 22),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _selectedCountry.dialCode,
                    style: GoogleFonts.dmSans(
                      color: AppColors.bgPrimary,
                      fontWeight: FontWeight.w800,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(width: 4),
                  const Icon(
                    Icons.keyboard_arrow_down_rounded,
                    color: Color(0xFF4A7A60),
                    size: 18,
                  ),
                ],
              ),
            ),
          ),
          // Number field
          Expanded(
            child: TextField(
              controller: _phoneCtrl,
              focusNode: _phoneFocus,
              keyboardType: TextInputType.phone,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              style: GoogleFonts.dmSans(
                color: const Color(0xFF0F2318),
                fontSize: 17,
                fontWeight: FontWeight.w700,
              ),
              decoration: InputDecoration(
                hintText: 'Phone number',
                hintStyle: GoogleFonts.dmSans(
                  color: const Color(0xFF8FB8A0),
                  fontSize: 15,
                  fontWeight: FontWeight.w400,
                ),
                border: InputBorder.none,
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 18),
              ),
              onChanged: (_) => setState(() => _error = null),
              onSubmitted: (_) => _savePhone(),
            ),
          ),
        ],
      ),
    );
  }

  // ── save button ───────────────────────────────────────────────────────────
  Widget _buildSaveButton() {
    return GestureDetector(
      onTap: _isLoading ? null : _savePhone,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        height: 58,
        decoration: BoxDecoration(
          gradient: _isLoading
              ? null
              : LinearGradient(
                  colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
          color: _isLoading ? Colors.white.withValues(alpha: 0.07) : null,
          borderRadius: BorderRadius.circular(16),
          boxShadow: _isLoading
              ? []
              : [
                  BoxShadow(
                    color: AppColors.primaryGreen.withValues(alpha: 0.38),
                    blurRadius: 20,
                    offset: const Offset(0, 6),
                  ),
                ],
        ),
        child: Center(
          child: _isLoading
              ? const SizedBox(
                  width: 22,
                  height: 22,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.5,
                    valueColor:
                        AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                )
              : Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.save_alt_rounded,
                        size: 18, color: Colors.white),
                    const SizedBox(width: 10),
                    Text(
                      'Save Number',
                      style: GoogleFonts.dmSans(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                        letterSpacing: 0.1,
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }

  // ── info card ─────────────────────────────────────────────────────────────
  Widget _buildInfoCard() {
    final items = [
      (Icons.people_alt_outlined, 'Friends find you via your number'),
      (Icons.payment_outlined,    'Used to receive and send payments'),
      (Icons.security_outlined,   'Number is unique per account'),
    ];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.bgCard),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Why we need your number',
            style: GoogleFonts.dmSans(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: AppColors.textPrimary.withValues(alpha: 0.85),
            ),
          ),
          const SizedBox(height: 12),
          ...items.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Row(
                children: [
                  Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                      color: AppColors.primaryGreen.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(item.$1,
                        size: 15,
                        color: AppColors.primaryGreen),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    item.$2,
                    style: GoogleFonts.dmSans(
                      fontSize: 13,
                      color: Colors.white.withValues(alpha: 0.60),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── error banner ──────────────────────────────────────────────────────────
  Widget _buildErrorBanner(String message) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.errorColor.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(12),
        border:
            Border.all(color: AppColors.errorColor.withValues(alpha: 0.40)),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline_rounded,
              color: AppColors.errorColor, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: GoogleFonts.dmSans(
                color: const Color(0xFFFF7875),
                fontSize: 13,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Pulsing icon hero
// ---------------------------------------------------------------------------
class _PulsingIcon extends StatefulWidget {
  final IconData icon;
  const _PulsingIcon({required this.icon});

  @override
  State<_PulsingIcon> createState() => _PulsingIconState();
}

class _PulsingIconState extends State<_PulsingIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _scale;
  late final Animation<double> _glow;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 2200))
      ..repeat(reverse: true);
    _scale = Tween<double>(begin: 0.94, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut));
    _glow = Tween<double>(begin: 0.20, end: 0.45)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, child) => ScaleTransition(
        scale: _scale,
        child: Container(
          width: 100,
          height: 100,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            boxShadow: [
              BoxShadow(
                color: AppColors.primaryGreen.withValues(alpha: _glow.value * 0.6),
                blurRadius: 14,
              ),
            ],
          ),
          child: Icon(widget.icon, color: Colors.white, size: 46),
        ),
      ),
    );
  }
}


// ---------------------------------------------------------------------------
// Country picker bottom sheet
// ---------------------------------------------------------------------------
class _CountryPickerSheet extends StatefulWidget {
  final _Country selected;
  final ValueChanged<_Country> onSelect;
  const _CountryPickerSheet(
      {required this.selected, required this.onSelect});

  @override
  State<_CountryPickerSheet> createState() => _CountryPickerSheetState();
}

class _CountryPickerSheetState extends State<_CountryPickerSheet> {
  late List<_Country> _filtered;

  @override
  void initState() {
    super.initState();
    _filtered = _countries;
  }

  void _onSearch(String q) {
    setState(() {
      _filtered = _countries
          .where((c) =>
              c.name.toLowerCase().contains(q.toLowerCase()) ||
              c.dialCode.contains(q))
          .toList();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgSecondary,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Handle
          Container(
            margin: const EdgeInsets.only(top: 12, bottom: 10),
            width: 36,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
            child: Row(
              children: [
                Text('Select Country',
                    style: GoogleFonts.dmSans(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                        color: Colors.white)),
                const Spacer(),
                GestureDetector(
                  onTap: () => Navigator.pop(context),
                  child: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      color: AppColors.bgSurface,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.close_rounded,
                        color: Colors.white.withValues(alpha: 0.65), size: 18),
                  ),
                ),
              ],
            ),
          ),
          // Search
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: TextField(
              autofocus: true,
              onChanged: _onSearch,
              style:
                  GoogleFonts.dmSans(color: Colors.white, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'Search country…',
                hintStyle: GoogleFonts.dmSans(
                    color: AppColors.textMuted,
                    fontSize: 14),
                prefixIcon: Icon(Icons.search_rounded,
                    color: AppColors.textMuted, size: 20),
                filled: true,
                fillColor: Colors.white.withValues(alpha: 0.07),
                contentPadding:
                    const EdgeInsets.symmetric(vertical: 12),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(
                      color: AppColors.borderSubtle),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(
                      color: AppColors.borderSubtle),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide(color: AppColors.primaryGreen, width: 1.5),
                ),
              ),
            ),
          ),
          // List
          ConstrainedBox(
            constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height * 0.45),
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: _filtered.length,
              itemBuilder: (_, i) {
                final c = _filtered[i];
                final sel = c.iso == widget.selected.iso;
                return Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () => widget.onSelect(c),
                    splashColor:
                        AppColors.primaryGreen.withValues(alpha: 0.08),
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 20, vertical: 13),
                      decoration: BoxDecoration(
                        color: sel
                            ? AppColors.primaryGreen.withValues(alpha: 0.10)
                            : null,
                        border: Border(
                            bottom: BorderSide(
                                color: AppColors.bgSurface)),
                      ),
                      child: Row(
                        children: [
                          Text(c.flag,
                              style: const TextStyle(fontSize: 22)),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Text(c.name,
                                style: GoogleFonts.dmSans(
                                    color: Colors.white,
                                    fontSize: 14,
                                    fontWeight: sel
                                        ? FontWeight.w700
                                        : FontWeight.w400)),
                          ),
                          Text(c.dialCode,
                              style: GoogleFonts.dmSans(
                                  color: sel
                                      ? AppColors.primaryGreen
                                      : Colors.white.withValues(alpha: 0.48),
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600)),
                          if (sel) ...[
                            const SizedBox(width: 8),
                            Icon(Icons.check_circle_rounded,
                                color: AppColors.primaryGreen, size: 18),
                          ],
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}
