import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:paychat/providers/theme_provider.dart';
import '../../core/theme/app_colors.dart';
import '../../providers/auth_providers.dart';
import '../../providers/service_providers.dart';
import '../chat_screen.dart';

class QrScannerScreen extends ConsumerStatefulWidget {
  const QrScannerScreen({super.key});

  @override
  ConsumerState<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends ConsumerState<QrScannerScreen> {
  late final MobileScannerController _cameraController;
  bool _hasScanned = false;
  bool _isProcessing = false;
  bool _cameraError = false;
  String? _cameraErrorMessage;

  // Manual entry fallback
  bool _showManual = false;
  final _manualCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _cameraController = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
    );
    _cameraController.start().catchError((e) {
      if (mounted) {
        setState(() {
          _cameraError = true;
          _cameraErrorMessage = e.toString();
        });
      }
    });
  }

  @override
  void dispose() {
    _cameraController.dispose();
    _manualCtrl.dispose();
    super.dispose();
  }

  // ── QR detection ─────────────────────────────────────────────────────────────

  void _onDetect(BarcodeCapture capture) {
    if (_hasScanned || _isProcessing) return;
    for (final barcode in capture.barcodes) {
      final raw = barcode.rawValue;
      if (raw != null && raw.startsWith('paychat:user:')) {
        _hasScanned = true;
        _initiateChat(raw.split('paychat:user:').last);
        return;
      }
    }
  }

  // ── Manual entry ──────────────────────────────────────────────────────────────

  void _submitManual() {
    final input = _manualCtrl.text.trim();
    if (input.isEmpty) return;
    // Accept either a raw UID or the full paychat:user:<uid> string
    final uid = input.startsWith('paychat:user:')
        ? input.split('paychat:user:').last
        : input;
    if (uid.isEmpty) return;
    _hasScanned = true;
    _initiateChat(uid);
  }

  // ── Core: look up user and open / create thread ───────────────────────────────

  Future<void> _initiateChat(String scannedUserId) async {
    setState(() => _isProcessing = true);
    try {
      final session = ref.read(sessionStateProvider);
      final currentUser = session.user;

      if (currentUser == null) {
        _showError('You must be logged in to start a chat.');
        _resetScan();
        return;
      }
      if (scannedUserId == currentUser.uid) {
        _showError('You cannot start a chat with yourself.');
        _resetScan();
        return;
      }

      final userRepo = ref.read(userRepositoryProvider);
      final chatRepo = ref.read(chatRepositoryProvider);

      final contactUser = await userRepo.fetchUserProfile(scannedUserId);
      if (contactUser == null) {
        _showError('User not found. The QR code may be outdated.');
        _resetScan();
        return;
      }

      final threadId = await chatRepo.getOrCreateThread(
        currentUserId: currentUser.uid,
        currentUserDisplayName: currentUser.displayName,
        currentUserPhotoUrl: currentUser.photoUrl,
        contactUserId: contactUser.uid,
        contactName: contactUser.displayName,
        contactPhotoUrl: contactUser.photoUrl,
      );

      if (!mounted) return;

      Navigator.pushReplacement(
        context,
        MaterialPageRoute(
          builder: (_) => ChatScreen(
            threadId: threadId,
            partnerName: contactUser.displayName,
            partnerInitials: contactUser.displayName.isNotEmpty
                ? contactUser.displayName[0].toUpperCase()
                : '?',
            avatarColor: AppColors.accentCyan,
            partnerUserId: contactUser.uid,
          ),
        ),
      );
    } catch (e) {
      _showError('Something went wrong: $e');
      _resetScan();
    }
  }

  void _resetScan() {
    setState(() {
      _hasScanned = false;
      _isProcessing = false;
    });
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message,
            style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
        backgroundColor: AppColors.bgCard,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  // ── Build ─────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: AppColors.bgSecondary,
        foregroundColor: AppColors.primaryGreen,
        iconTheme: IconThemeData(color: AppColors.primaryGreen),
        title: Text(
          'Scan QR Code',
          style: GoogleFonts.dmSans(
            color: AppColors.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.w700,
          ),
        ),
        actions: [
          // Torch — only on native (not web)
          if (!kIsWeb)
            IconButton(
              icon: Icon(Icons.flash_on_rounded,
                  color: AppColors.primaryGreen),
              onPressed: () => _cameraController.toggleTorch(),
            ),
          // Switch camera — only on native
          if (!kIsWeb)
            IconButton(
              icon: Icon(Icons.flip_camera_ios_rounded,
                  color: AppColors.primaryGreen),
              onPressed: () => _cameraController.switchCamera(),
            ),
          // Manual entry toggle
          IconButton(
            icon: Icon(
              _showManual ? Icons.qr_code_scanner_rounded : Icons.keyboard_rounded,
              color: AppColors.primaryGreen,
            ),
            tooltip: _showManual ? 'Camera scan' : 'Enter UID manually',
            onPressed: () => setState(() {
              _showManual = !_showManual;
              _hasScanned = false;
            }),
          ),
        ],
      ),
      body: _showManual ? _buildManualEntry() : _buildScanner(),
    );
  }

  // ── Camera scanner view ───────────────────────────────────────────────────────

  Widget _buildScanner() {
    if (_cameraError) {
      return _buildCameraError();
    }
    return Stack(
      children: [
        // Camera feed
        MobileScanner(
          controller: _cameraController,
          onDetect: _onDetect,
          errorBuilder: (context, error) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (mounted && !_cameraError) {
                setState(() {
                  _cameraError = true;
                  _cameraErrorMessage = error.errorDetails?.message;
                });
              }
            });
            return const SizedBox.shrink();
          },
        ),

        // Overlay cutout
        CustomPaint(
          size: Size.infinite,
          painter: _ScannerOverlayPainter(accentColor: AppColors.primaryGreen),
        ),

        // Processing overlay
        if (_isProcessing)
          Container(
            color: Colors.black54,
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircularProgressIndicator(color: AppColors.primaryGreen),
                  const SizedBox(height: 16),
                  Text(
                    'Connecting…',
                    style: GoogleFonts.dmSans(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),

        // Hint text
        Positioned(
          bottom: 48,
          left: 0,
          right: 0,
          child: Column(
            children: [
              Text(
                'Align a PayChat QR code within the frame',
                textAlign: TextAlign.center,
                style: GoogleFonts.dmSans(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  shadows: const [Shadow(color: Colors.black, blurRadius: 6)],
                ),
              ),
              const SizedBox(height: 12),
              GestureDetector(
                onTap: () => setState(() => _showManual = true),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 20, vertical: 10),
                  decoration: BoxDecoration(
                    color: AppColors.primaryGreen.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                        color: AppColors.primaryGreen.withValues(alpha: 0.5)),
                  ),
                  child: Text(
                    'Enter User ID manually',
                    style: GoogleFonts.dmSans(
                      color: AppColors.primaryGreen,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ── Camera error view ─────────────────────────────────────────────────────────

  Widget _buildCameraError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppColors.errorColor.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.videocam_off_rounded,
                  color: AppColors.errorColor, size: 34),
            ),
            const SizedBox(height: 20),
            Text(
              'Camera unavailable',
              style: GoogleFonts.dmSans(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              kIsWeb
                  ? 'Allow camera access in your browser, or use manual entry.'
                  : 'Camera permission was denied. Please enable it in Settings.',
              textAlign: TextAlign.center,
              style: GoogleFonts.dmSans(
                  color: Colors.white60, fontSize: 13, height: 1.5),
            ),
            const SizedBox(height: 28),
            ElevatedButton.icon(
              onPressed: () => setState(() {
                _cameraError = false;
                _showManual = true;
              }),
              icon: const Icon(Icons.keyboard_rounded),
              label: const Text('Enter User ID manually'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryGreen,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(
                    horizontal: 24, vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ── Manual entry view ─────────────────────────────────────────────────────────

  Widget _buildManualEntry() {
    return Padding(
      padding: EdgeInsets.only(
        left: 24,
        right: 24,
        top: 40,
        bottom: MediaQuery.of(context).viewInsets.bottom + 32,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          // Icon
          Center(
            child: Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: AppColors.primaryGreen.withValues(alpha: 0.12),
                shape: BoxShape.circle,
                border: Border.all(
                    color: AppColors.primaryGreen.withValues(alpha: 0.3)),
              ),
              child: Icon(Icons.person_search_rounded,
                  color: AppColors.primaryGreen, size: 32),
            ),
          ),
          const SizedBox(height: 20),
          Text(
            'Enter User ID',
            textAlign: TextAlign.center,
            style: GoogleFonts.dmSans(
              color: Colors.white,
              fontSize: 20,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Paste the PayChat User ID or the full\npaychat:user:<id> QR string.',
            textAlign: TextAlign.center,
            style: GoogleFonts.dmSans(
                color: Colors.white54, fontSize: 13, height: 1.5),
          ),
          const SizedBox(height: 28),
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.borderSubtle),
            ),
            child: TextField(
              controller: _manualCtrl,
              autofocus: true,
              style: GoogleFonts.dmSans(
                  color: AppColors.textPrimary, fontSize: 14),
              decoration: InputDecoration(
                hintText: 'Paste User ID here…',
                hintStyle: GoogleFonts.dmSans(
                    color: AppColors.textMuted, fontSize: 14),
                prefixIcon: Icon(Icons.qr_code_rounded,
                    color: AppColors.textSecondary, size: 20),
                border: InputBorder.none,
                filled: false,
                contentPadding: const EdgeInsets.symmetric(vertical: 16),
              ),
              onSubmitted: (_) => _submitManual(),
            ),
          ),
          const SizedBox(height: 16),
          if (_isProcessing)
            Center(
              child: CircularProgressIndicator(
                  color: AppColors.primaryGreen, strokeWidth: 2.5),
            )
          else
            ElevatedButton(
              onPressed: _submitManual,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryGreen,
                foregroundColor: Colors.white,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
              child: Text(
                'Start Chat',
                style: GoogleFonts.dmSans(
                    fontSize: 15, fontWeight: FontWeight.w700),
              ),
            ),
          const SizedBox(height: 12),
          // Back to camera (only if camera didn't error)
          if (!_cameraError)
            TextButton.icon(
              onPressed: () => setState(() => _showManual = false),
              icon: Icon(Icons.qr_code_scanner_rounded,
                  color: AppColors.textSecondary, size: 18),
              label: Text(
                'Use camera instead',
                style: GoogleFonts.dmSans(
                    color: AppColors.textSecondary, fontSize: 13),
              ),
            ),
        ],
      ),
    );
  }
}

// ── Scanner overlay painter ───────────────────────────────────────────────────

class _ScannerOverlayPainter extends CustomPainter {
  final Color accentColor;
  const _ScannerOverlayPainter({required this.accentColor});

  @override
  void paint(Canvas canvas, Size size) {
    final dimPaint = Paint()
      ..color = Colors.black.withValues(alpha: 0.55)
      ..style = PaintingStyle.fill;

    const scanSize = 260.0;
    final scanRect = Rect.fromCenter(
      center: Offset(size.width / 2, size.height * 0.45),
      width: scanSize,
      height: scanSize,
    );

    // Dim everything outside the scan area
    canvas.drawPath(
      Path()
        ..addRect(Rect.fromLTWH(0, 0, size.width, size.height))
        ..addRRect(RRect.fromRectAndRadius(scanRect, const Radius.circular(16)))
        ..fillType = PathFillType.evenOdd,
      dimPaint,
    );

    // Corner brackets
    final bracketPaint = Paint()
      ..color = accentColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.5
      ..strokeCap = StrokeCap.round;

    const bl = 32.0; // bracket length
    const r = 16.0;  // corner radius

    // Top-left
    canvas.drawPath(
      Path()
        ..moveTo(scanRect.left + r, scanRect.top)
        ..lineTo(scanRect.left + bl, scanRect.top)
        ..moveTo(scanRect.left, scanRect.top + r)
        ..lineTo(scanRect.left, scanRect.top + bl),
      bracketPaint,
    );
    // Top-right
    canvas.drawPath(
      Path()
        ..moveTo(scanRect.right - bl, scanRect.top)
        ..lineTo(scanRect.right - r, scanRect.top)
        ..moveTo(scanRect.right, scanRect.top + r)
        ..lineTo(scanRect.right, scanRect.top + bl),
      bracketPaint,
    );
    // Bottom-left
    canvas.drawPath(
      Path()
        ..moveTo(scanRect.left, scanRect.bottom - bl)
        ..lineTo(scanRect.left, scanRect.bottom - r)
        ..moveTo(scanRect.left + r, scanRect.bottom)
        ..lineTo(scanRect.left + bl, scanRect.bottom),
      bracketPaint,
    );
    // Bottom-right
    canvas.drawPath(
      Path()
        ..moveTo(scanRect.right, scanRect.bottom - bl)
        ..lineTo(scanRect.right, scanRect.bottom - r)
        ..moveTo(scanRect.right - bl, scanRect.bottom)
        ..lineTo(scanRect.right - r, scanRect.bottom),
      bracketPaint,
    );
  }

  @override
  bool shouldRepaint(_ScannerOverlayPainter old) => old.accentColor != accentColor;
}
