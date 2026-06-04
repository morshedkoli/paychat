import 'dart:convert';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:file_picker/file_picker.dart';
import 'package:mime/mime.dart';
import 'package:open_filex/open_filex.dart';
import 'package:paychat/core/config.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/models/app_message.dart';
import 'package:paychat/models/app_order.dart';
import 'package:paychat/models/app_transaction.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/chat_wallpaper_provider.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/screens/orders/order_service_sheet.dart';
import 'package:paychat/services/file_transfer_service.dart';

// ── Chat screen constants ─────────────────────────────────────────────────────

// Tabs
const int _kChatTabCount = 2; // Messages + Orders

// Send-button press animation
const Duration _kSendBtnAnimDuration = Duration(milliseconds: 150);
const double   _kSendBtnScaleNormal  = 1.0;
const double   _kSendBtnScalePressed = 0.92;

// Image upload snackbar
const Duration _kUploadSnackDuration = Duration(seconds: 30);
const int      _kHttpOk              = 200;

// Message bubble
const Duration _kMessageAnimDuration    = Duration(milliseconds: 200);
const double   _kBubbleMaxWidthFraction = 0.82; // fraction of screen width

// Image bubble
const double _kImageBubbleWidthFraction   = 0.65;
const double _kImagePlaceholderHeight     = 160.0;
const double _kImageErrorHeight           = 100.0;

// Input bar
const double _kInputMaxHeight = 120.0;
const int    _kInputMaxLines  = 6;
const double _kSendBtnSize    = 44.0;
const double _kSendIconSize   = 18.0;

// PIN dialog
const int      _kPinLength      = 4;
const String   _kDemoPin        = '1234';
const Duration _kPinVerifyDelay = Duration(milliseconds: 300);

// PIN keypad layout — 3 columns × 4 rows = 12 keys
// [1][2][3]
// [4][5][6]
// [7][8][9]
// [×][0][⌫]
const int    _kKeypadColumns      = 3;
const int    _kKeypadRows         = 4;
const int    _kKeypadItemCount    = _kKeypadColumns * _kKeypadRows;
const int    _kKeypadCancelIdx    = _kKeypadColumns * (_kKeypadRows - 1); // bottom-left
const int    _kKeypadZeroIdx      = _kKeypadItemCount - 2;                 // bottom-center
const int    _kKeypadBackspaceIdx = _kKeypadItemCount - 1;                 // bottom-right
const double _kKeypadAspectRatio  = 1.4;
const double _kKeypadSpacing      = 10.0;

// ─────────────────────────────────────────────────────────────────────────────

class ChatScreen extends ConsumerStatefulWidget {
  final String threadId;
  final String partnerName;
  final String partnerInitials;
  final Color avatarColor;
  final String partnerUserId;

  const ChatScreen({
    super.key,
    required this.threadId,
    required this.partnerName,
    required this.partnerInitials,
    required this.avatarColor,
    required this.partnerUserId,
  });

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen>
    with TickerProviderStateMixin {
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  bool _isTyping = false;
  late TabController _tabController;

  final Map<String, FileTransferProgress> _uploadProgress = {};
  final Map<String, FileTransferProgress> _downloadProgress = {};

  late AnimationController _sendBtnController;
  late Animation<double> _sendBtnScale;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _kChatTabCount, vsync: this);
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging) setState(() {});
    });

    _messageController.addListener(() {
      setState(() => _isTyping = _messageController.text.isNotEmpty);
    });

    _sendBtnController = AnimationController(
      vsync: this,
      duration: _kSendBtnAnimDuration,
    );
    _sendBtnScale = Tween<double>(begin: _kSendBtnScaleNormal, end: _kSendBtnScalePressed).animate(
      CurvedAnimation(parent: _sendBtnController, curve: Curves.easeInOut),
    );

    // Reset unread badge when chat is opened.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(chatRepositoryProvider).markThreadAsRead(widget.threadId);
      final currentUserId = ref.read(sessionStateProvider).user?.uid;
      if (currentUserId != null) {
        ref.read(chatRepositoryProvider).markMessagesAsRead(
          threadId: widget.threadId,
          currentUserId: currentUserId,
        );
      }
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    _messageController.dispose();
    _scrollController.dispose();
    _sendBtnController.dispose();
    super.dispose();
  }

  // ── ImgBB upload ─────────────────────────────────────────────────────────────

  static const int _kMaxImageBytes = 5 * 1024 * 1024; // 5 MB

  Future<String?> _uploadToImgBB(Uint8List bytes) async {
    if (bytes.length > _kMaxImageBytes) return null;

    // Validate that bytes are actually an image by inspecting the file header.
    final header = bytes.length >= 12 ? bytes.sublist(0, 12) : bytes;
    final mimeType = lookupMimeType('', headerBytes: header);
    if (mimeType == null || !mimeType.startsWith('image/')) return null;

    try {
      final response = await http.post(
        Uri.parse('https://api.imgbb.com/1/upload'),
        body: {
          'key': AppConfig.imgbbApiKey,
          'image': base64Encode(bytes),
        },
      );
      if (response.statusCode == _kHttpOk) {
        final data = jsonDecode(response.body);
        return data['data']['display_url'] as String?;
      }
    } catch (_) {}
    return null;
  }

  // ── Send image ────────────────────────────────────────────────────────────────

  Future<void> _sendImage(AppUser currentUser) async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 75,
    );
    if (picked == null) return;

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Uploading image…'),
        duration: _kUploadSnackDuration,
        backgroundColor: AppColors.bgCard,
      ),
    );

    final bytes = await picked.readAsBytes();
    final url = await _uploadToImgBB(bytes);

    if (!mounted) return;
    ScaffoldMessenger.of(context).clearSnackBars();

    if (url == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Image upload failed. Check your ImgBB API key.')),
      );
      return;
    }

    try {
      await ref.read(chatRepositoryProvider).sendImageMessage(
        threadId: widget.threadId,
        senderId: currentUser.uid,
        imageUrl: url,
      );
      HapticFeedback.lightImpact();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: const Text('Failed to send image. Please try again.')));
      }
    }
  }

  // ── Transaction sheet ────────────────────────────────────────────────────────

  void _showTransactionSheet(AppUser currentUser) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _TransactionBottomSheet(
        onTransactionSubmit: (isSend, amount, note) async {
          final chatRepo = ref.read(chatRepositoryProvider);
          final transRepo = ref.read(transactionRepositoryProvider);

          try {
            final batch = chatRepo.firestore.batch();
            final messageId = chatRepo.firestore.collection('messages').doc().id;

            if (isSend) {
              // Send Money Request — pending until recipient accepts.
              // Sender's transaction record is created as PENDING.
              await chatRepo.sendTransactionMessage(
                threadId: widget.threadId,
                senderId: currentUser.uid,
                partnerUserId: widget.partnerUserId,
                text: note?.isNotEmpty == true ? note! : 'Sent ৳$amount',
                amount: amount,
                isReceive: false,
                txnStatus: TransactionMessageStatus.pending,
                messageId: messageId,
                batch: batch,
              );
              await transRepo.addTransaction(
                userId: currentUser.uid,
                transactionId: '${messageId}_sender',
                contactName: widget.partnerName,
                contactPhone: '',
                type: TransactionType.sent,
                amount: amount,
                note: note ?? '',
                status: TransactionStatus.pending,
                batch: batch,
              );
            } else {
              // Record Receipt — now pending, requires confirmation from the partner.
              // Sender records that they received money from the other person.
              await chatRepo.sendTransactionMessage(
                threadId: widget.threadId,
                senderId: currentUser.uid,
                partnerUserId: widget.partnerUserId,
                text: note?.isNotEmpty == true ? note! : 'Received ৳$amount',
                amount: amount,
                isReceive: true,
                txnStatus: TransactionMessageStatus.pending,
                messageId: messageId,
                batch: batch,
              );
              // Sender transaction record (received, pending until partner approves)
              await transRepo.addTransaction(
                userId: currentUser.uid,
                transactionId: '${messageId}_sender',
                contactName: widget.partnerName,
                contactPhone: '',
                type: TransactionType.received,
                amount: amount,
                note: note ?? '',
                status: TransactionStatus.pending,
                batch: batch,
              );
            }

            await batch.commit();
            HapticFeedback.heavyImpact();
          } catch (e) {
            if (!context.mounted) return;
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: const Text('Failed to process transaction. Please try again.')),
            );
          }
        },
      ),
    );
  }

  // ── Accept / Reject pending payment ─────────────────────────────────────────

  Future<void> _acceptPayment(_ChatMessage message, AppUser currentUser) async {
    final pinAccepted = await _showTransactionApprovalDialog(message);
    if (pinAccepted != true) return;

    final chatRepo = ref.read(chatRepositoryProvider);
    final transRepo = ref.read(transactionRepositoryProvider);
    try {
      final batch = chatRepo.firestore.batch();
      
      // 1. Accept message state securely
      await chatRepo.acceptTransactionMessage(
        messageId: message.messageId,
        threadId: widget.threadId,
        senderId: message.senderId,
        acceptorId: currentUser.uid,
        amount: message.amount!,
        isReceive: message.isRequest,
      );

      // 2. Complete sender's pending transaction
      await transRepo.updateTransactionStatus(
        transactionId: '${message.messageId}_sender',
        status: TransactionStatus.completed,
        batch: batch,
      );

      // 3. Complete recipient's (acceptor's) side of transaction
      final acceptorTxnType = message.isRequest ? TransactionType.sent : TransactionType.received;

      await transRepo.addTransaction(
        userId: currentUser.uid,
        transactionId: '${message.messageId}_partner',
        contactName: widget.partnerName,
        contactPhone: '',
        type: acceptorTxnType,
        amount: message.amount!,
        note: message.note ?? '',
        status: TransactionStatus.completed,
        batch: batch,
      );

      await batch.commit();
      HapticFeedback.heavyImpact();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: const Text('Failed to accept payment. Please try again.')));
      }
    }
  }

  Future<void> _rejectPayment(_ChatMessage message) async {
    final chatRepo = ref.read(chatRepositoryProvider);
    final transRepo = ref.read(transactionRepositoryProvider);
    try {
      final batch = chatRepo.firestore.batch();

      // 1. Reject message state
      await chatRepo.rejectTransactionMessage(message.messageId);

      // 2. Cancel sender's pending transaction
      await transRepo.updateTransactionStatus(
        transactionId: '${message.messageId}_sender',
        status: TransactionStatus.cancelled,
        batch: batch,
      );

      await batch.commit();
      HapticFeedback.lightImpact();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: const Text('Failed to reject payment. Please try again.')));
      }
    }
  }

  Future<bool?> _showTransactionApprovalDialog(_ChatMessage message) {
    return showDialog<bool>(
      context: context,
      barrierDismissible: true,
      builder: (context) {
        String pin = '';
        return StatefulBuilder(
          builder: (context, setModalState) {
            void handleKeyPress(String val) {
              if (pin.length < _kPinLength) {
                setModalState(() => pin += val);
                if (pin.length == _kPinLength) {
                  // Verify PIN after a brief delay for user feedback
                  Future.delayed(_kPinVerifyDelay, () {
                    if (!context.mounted) return;
                    if (pin == _kDemoPin) {
                      Navigator.pop(context, true);
                    } else {
                      HapticFeedback.heavyImpact();
                      setModalState(() {
                        pin = '';
                      });
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: const Text('Invalid PIN. Please try again.'),
                          backgroundColor: AppColors.errorColor,
                        ),
                      );
                    }
                  });
                }
              }
            }

            void handleBackspace() {
              if (pin.isNotEmpty) {
                setModalState(() => pin = pin.substring(0, pin.length - 1));
              }
            }

            return Dialog(
              backgroundColor: Colors.transparent,
              insetPadding: const EdgeInsets.symmetric(horizontal: 24),
              child: Container(
                decoration: BoxDecoration(
                  color: AppColors.bgCard,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.3)),
                  boxShadow: [
                    BoxShadow(
                      color: AppColors.accentCyan.withValues(alpha: 0.1),
                      blurRadius: 24,
                      spreadRadius: 4,
                    ),
                  ],
                ),
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Shield Icon
                    Container(
                      width: 60,
                      height: 60,
                      decoration: BoxDecoration(
                        color: AppColors.accentCyan.withValues(alpha: 0.1),
                        shape: BoxShape.circle,
                        border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.3)),
                      ),
                      child: Icon(
                        Icons.shield_rounded,
                        color: AppColors.accentCyan,
                        size: 32,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Security Verification',
                      style: GoogleFonts.dmSans(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Confirm payment approval of',
                      style: GoogleFonts.dmSans(
                        fontSize: 13,
                        color: AppColors.textMuted,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      '৳${message.amount?.toStringAsFixed(2) ?? '0.00'}',
                      style: GoogleFonts.dmSans(
                        fontSize: 24,
                        fontWeight: FontWeight.w800,
                        color: AppColors.accentCyan,
                      ),
                    ),
                    const SizedBox(height: 24),
                    // PIN dots indicator
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: List.generate(_kPinLength, (index) {
                        final filled = index < pin.length;
                        return Container(
                          margin: const EdgeInsets.symmetric(horizontal: 12),
                          width: 16,
                          height: 16,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: filled ? AppColors.accentCyan : Colors.transparent,
                            border: Border.all(
                              color: filled ? AppColors.accentCyan : AppColors.textMuted.withValues(alpha: 0.5),
                              width: 2,
                            ),
                            boxShadow: filled
                                ? [
                                    BoxShadow(
                                      color: AppColors.accentCyan.withValues(alpha: 0.5),
                                      blurRadius: 8,
                                    )
                                  ]
                                : null,
                          ),
                        );
                      }),
                    ),
                    const SizedBox(height: 32),
                    // Custom keypad
                    GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: _kKeypadColumns,
                        childAspectRatio: _kKeypadAspectRatio,
                        crossAxisSpacing: _kKeypadSpacing,
                        mainAxisSpacing: _kKeypadSpacing,
                      ),
                      itemCount: _kKeypadItemCount,
                      itemBuilder: (context, idx) {
                        if (idx == _kKeypadCancelIdx) {
                          // Clear/Cancel button
                          return IconButton(
                            icon: Icon(Icons.close_rounded, color: AppColors.textMuted),
                            onPressed: () => Navigator.pop(context, false),
                          );
                        }
                        if (idx == _kKeypadBackspaceIdx) {
                          // Backspace button
                          return IconButton(
                            icon: Icon(Icons.backspace_rounded, color: AppColors.textMuted),
                            onPressed: handleBackspace,
                          );
                        }
                        final numValue = idx == _kKeypadZeroIdx ? '0' : '${idx + 1}';
                        return Material(
                          color: Colors.transparent,
                          child: InkWell(
                            onTap: () {
                              HapticFeedback.lightImpact();
                              handleKeyPress(numValue);
                            },
                            borderRadius: BorderRadius.circular(16),
                            child: Container(
                              decoration: BoxDecoration(
                                color: AppColors.bgSecondary.withValues(alpha: 0.5),
                                borderRadius: BorderRadius.circular(16),
                                border: Border.all(color: AppColors.borderSubtle.withValues(alpha: 0.5)),
                              ),
                              alignment: Alignment.center,
                              child: Text(
                                numValue,
                                style: GoogleFonts.dmSans(
                                  fontSize: 22,
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.textPrimary,
                                ),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Authorization required to confirm payment',
                      style: GoogleFonts.dmSans(
                        fontSize: 11,
                        fontStyle: FontStyle.italic,
                        color: AppColors.textMuted.withValues(alpha: 0.7),
                      ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  // ── Order sheet ───────────────────────────────────────────────────────────────

  void _showOrderSheet(AppUser currentUser) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => OrderServiceSheet(
        currentUser: currentUser,
        partnerUserId: widget.partnerUserId,
        partnerName: widget.partnerName,
        threadId: widget.threadId,
      ),
    );
  }

  // ── Send text message ────────────────────────────────────────────────────────

  void _sendMessage(AppUser currentUser) async {
    if (_messageController.text.trim().isEmpty) return;
    _sendBtnController.forward().then((_) => _sendBtnController.reverse());
    final text = _messageController.text.trim();
    try {
      await ref.read(chatRepositoryProvider).sendMessage(
        threadId: widget.threadId,
        senderId: currentUser.uid,
        text: text,
      );
      _messageController.clear();
      HapticFeedback.lightImpact();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to send message. Please try again.')),
      );
    }
  }

  // ── Send file ────────────────────────────────────────────────────────────────

  Future<void> _sendFile(AppUser currentUser) async {
    final result = await FilePicker.pickFiles(
      allowMultiple: false,
      withData: false,
      withReadStream: false,
      type: FileType.any,
    );
    if (result == null || result.files.isEmpty) return;

    final picked = result.files.first;
    final filePath = picked.path;
    if (filePath == null) return;

    final fileName = picked.name;
    final fileSize = picked.size;
    // Use the mime package for accurate MIME type detection.
    final mimeType = lookupMimeType(filePath) ?? 'application/octet-stream';
    final totalChunks = (fileSize / FileTransferService.kChunkSizeBytes).ceil();

    final chatRepo = ref.read(chatRepositoryProvider);
    final ftService = ref.read(fileTransferServiceProvider);

    final (msg, transferId) = await chatRepo.sendFileMessage(
      threadId: widget.threadId,
      senderId: currentUser.uid,
      fileName: fileName,
      fileSize: fileSize,
      mimeType: mimeType,
      totalChunks: totalChunks,
    );

    setState(() {
      _uploadProgress[msg.id] = const FileTransferProgress(progress: 0);
    });

    ftService.sendFile(transferId: transferId, filePath: filePath).listen((progress) {
      if (!mounted) return;
      setState(() => _uploadProgress[msg.id] = progress);
      if (progress.isComplete) {
        chatRepo.updateFileStatus(msg.id, FileTransferStatus.ready);
      } else if (progress.isFailed) {
        chatRepo.updateFileStatus(msg.id, FileTransferStatus.failed);
      }
    });
  }

  Future<void> _downloadFile(_ChatMessage message) async {
    final transferId = message.transferId;
    final fileName = message.fileName;
    final totalChunks = message.totalChunks;
    if (transferId == null || fileName == null || totalChunks == null) return;

    final ftService = ref.read(fileTransferServiceProvider);
    setState(() {
      _downloadProgress[transferId] = const FileTransferProgress(progress: 0);
    });

    ftService
        .receiveFile(
            transferId: transferId, fileName: fileName, totalChunks: totalChunks)
        .listen((progress) {
      if (!mounted) return;
      setState(() => _downloadProgress[transferId] = progress);
    });
  }

  // ── Balance ──────────────────────────────────────────────────────────────────

  double _netBalance(List<AppMessage> messages, String myUid) {
    double balance = 0;
    for (var msg in messages) {
      if (msg.type != MessageContentType.transaction) continue;
      // Skip pending and rejected requests — they haven't settled.
      // null txnStatus = legacy message, treat as accepted.
      final status = msg.txnStatus;
      if (status == TransactionMessageStatus.pending ||
          status == TransactionMessageStatus.rejected) {
        continue;
      }

      final amount = msg.amount ?? 0;
      final iMine = msg.senderId == myUid;
      if (iMine) {
        balance += msg.isReceive ? -amount : amount;
      } else {
        balance += msg.isReceive ? amount : -amount;
      }
    }
    return balance;
  }

  // ── Build ─────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    final sessionState = ref.watch(sessionStateProvider);
    final currentUser = sessionState.user;
    final messagesAsync = ref.watch(threadMessagesProvider(widget.threadId));
    final messages = messagesAsync.valueOrNull ?? [];
    final wallpaper = ref.watch(chatWallpaperProvider).selectedWallpaper;

    final threadsAsync = ref.watch(userThreadsProvider(currentUser?.uid ?? ''));
    final threads = threadsAsync.valueOrNull ?? [];
    final thread = threads.firstWhere(
      (t) => t['id'] == widget.threadId,
      orElse: () => <String, dynamic>{},
    );
    final balancesMap = thread['balances'] as Map<String, dynamic>? ?? {};
    final String myUid = currentUser?.uid ?? '';
    final double balance = myUid.isEmpty
        ? 0.0
        : (balancesMap[myUid] as num?)?.toDouble()
            ?? _netBalance(messages, myUid);

    final presenceAsync = ref.watch(userPresenceProvider(widget.partnerUserId));
    final presenceData = presenceAsync.valueOrNull;
    final bool isOnline = presenceData?['isOnline'] == true;
    final Timestamp? lastActiveTimestamp = presenceData?['lastActive'] as Timestamp?;
    String presenceText = 'offline';
    if (isOnline) {
      presenceText = 'online';
    } else if (lastActiveTimestamp != null) {
      final dateTime = lastActiveTimestamp.toDate();
      final timeStr = DateFormat('hh:mm a').format(dateTime);
      final now = DateTime.now();
      final isToday = dateTime.day == now.day && dateTime.month == now.month && dateTime.year == now.year;
      if (isToday) {
        presenceText = 'last seen today at $timeStr';
      } else {
        final dateStr = DateFormat('MMM d, hh:mm a').format(dateTime);
        presenceText = 'last seen $dateStr';
      }
    }

    ref.listen<AsyncValue<List<AppMessage>>>(
      threadMessagesProvider(widget.threadId),
      (previous, next) {
        final messagesList = next.valueOrNull ?? [];
        final hasUnread = messagesList.any((msg) => !msg.isRead && msg.senderId != currentUser?.uid);
        if (hasUnread && currentUser != null) {
          ref.read(chatRepositoryProvider).markMessagesAsRead(
            threadId: widget.threadId,
            currentUserId: currentUser.uid,
          );
        }
      },
    );

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: _buildAppBar(balance, isOnline, presenceText),
      body: Stack(
        children: [
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(gradient: wallpaper.gradient(AppColors.isDark)),
            ),
          ),
          Positioned.fill(
            child: Column(
              children: [
                Container(
                  color: AppColors.bgSecondary,
                  child: TabBar(
                    controller: _tabController,
                    indicatorColor: AppColors.accentCyan,
                    indicatorWeight: 2,
                    labelColor: AppColors.accentCyan,
                    unselectedLabelColor: AppColors.textMuted,
                    labelStyle: GoogleFonts.dmSans(
                        fontSize: 13, fontWeight: FontWeight.w600),
                    unselectedLabelStyle: GoogleFonts.dmSans(
                        fontSize: 13, fontWeight: FontWeight.w500),
                    tabs: const [Tab(text: 'Messages'), Tab(text: 'Orders')],
                  ),
                ),
                Expanded(
                  child: TabBarView(
                    controller: _tabController,
                    children: [
                      // Messages tab
                      ListView.builder(
                        controller: _scrollController,
                        reverse: true,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 18),
                        itemCount: messages.length,
                        itemBuilder: (context, index) {
                          final msg = messages[index];
                          return _buildMessageBubble(
                            _toLocalMessage(msg, currentUser?.uid ?? ''),
                            index,
                            currentUser,
                          );
                        },
                      ),
                      // Orders tab
                      _OrdersTab(
                        threadId: widget.threadId,
                        currentUserId: currentUser?.uid ?? '',
                      ),
                    ],
                  ),
                ),
                _buildInputBar(currentUser),
              ],
            ),
          ),
        ],
      ),
    );
  }

  _ChatMessage _toLocalMessage(AppMessage msg, String myUid) {
    return _ChatMessage(
      text: msg.text,
      time: DateFormat('hh:mm a').format(msg.createdAt),
      isMe: msg.senderId == myUid,
      senderId: msg.senderId,
      isPayment: msg.type == MessageContentType.transaction,
      isOrder: msg.type == MessageContentType.order,
      isFile: msg.type == MessageContentType.file,
      isImage: msg.type == MessageContentType.image,
      isRequest: msg.isReceive,
      amount: msg.amount,
      note: msg.text,
      status: msg.isRead ? MessageStatus.seen : MessageStatus.sent,
      orderId: msg.orderId,
      txnStatus: msg.txnStatus,
      imageUrl: msg.imageUrl,
      fileName: msg.fileName,
      fileSize: msg.fileSize,
      mimeType: msg.mimeType,
      transferId: msg.transferId,
      fileStatus: msg.fileStatus,
      totalChunks: msg.totalChunks,
      messageId: msg.id,
    );
  }

  PreferredSizeWidget _buildAppBar(double balance, bool isOnline, String presenceText) {

    return AppBar(
      backgroundColor: AppColors.bgSecondary,
      foregroundColor: AppColors.textPrimary,
      elevation: 0,
      titleSpacing: 0,
      leadingWidth: 72,
      leading: Row(
        children: [
          const SizedBox(width: 4),
          IconButton(
            icon: Icon(Icons.arrow_back_rounded, color: AppColors.accentCyan),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
          ),
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: LinearGradient(
                colors: [AppColors.accentCyan, AppColors.accentViolet],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              boxShadow: [
                BoxShadow(
                  color: AppColors.accentCyan.withValues(alpha: 0.3),
                  blurRadius: 8,
                ),
              ],
            ),
            alignment: Alignment.center,
            child: Text(
              widget.partnerInitials,
              style: GoogleFonts.dmSans(
                  color: Colors.white, fontSize: 14, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
      title: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.partnerName,
              style: GoogleFonts.dmSans(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: AppColors.textPrimary),
            ),
            const SizedBox(height: 2),
            Row(
              children: [
                Container(
                  width: 6, height: 6,
                  decoration: BoxDecoration(
                    color: isOnline ? AppColors.accentGreen : AppColors.textMuted.withValues(alpha: 0.8),
                    shape: BoxShape.circle,
                    boxShadow: isOnline ? AppColors.greenGlow : null,
                  ),
                ),
                const SizedBox(width: 4),
                Text('$presenceText  •  ',
                    style: GoogleFonts.dmSans(
                        fontSize: 11, color: AppColors.textMuted)),
                Text(
                  balance > 0
                      ? 'Receivable: ৳${balance.toStringAsFixed(0)}'
                      : (balance < 0
                          ? 'Payable: ৳${balance.abs().toStringAsFixed(0)}'
                          : 'Settled'),
                  style: GoogleFonts.dmSans(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: balance > 0
                        ? AppColors.accentGreen
                        : (balance < 0
                            ? AppColors.accentAmber
                            : AppColors.textMuted),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
      actions: [
        IconButton(
          icon: Icon(Icons.more_vert_rounded, color: AppColors.accentCyan),
          onPressed: () {},
        ),
      ],
    );
  }

  Widget _buildStatusIcon(MessageStatus? status) {
    if (status == null) return const SizedBox.shrink();
    switch (status) {
      case MessageStatus.sent:
        return Icon(Icons.check_rounded,
            size: 14, color: AppColors.textMuted.withValues(alpha: 0.8));
      case MessageStatus.delivered:
        return Icon(Icons.done_all_rounded,
            size: 14, color: AppColors.textMuted.withValues(alpha: 0.8));
      case MessageStatus.seen:
        return Icon(Icons.done_all_rounded,
            size: 14, color: AppColors.accentCyan);
    }
  }

  Widget _buildMessageBubble(_ChatMessage message, int index, AppUser? currentUser) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: _kMessageAnimDuration,
      curve: Curves.easeOutCubic,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, 16 * (1 - value)),
          child: child,
        ),
      ),
      child: Align(
        alignment: message.isMe ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          margin: const EdgeInsets.only(bottom: 6),
          constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width * _kBubbleMaxWidthFraction),
          decoration: _bubbleDecoration(message),
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              (message.isPayment || message.isOrder || message.isFile || message.isImage) ? 0 : 12,
              (message.isPayment || message.isOrder || message.isFile || message.isImage) ? 0 : 9,
              (message.isPayment || message.isOrder || message.isFile || message.isImage) ? 0 : 9,
              (message.isPayment || message.isOrder || message.isFile || message.isImage) ? 0 : 6,
            ),
            child: message.isImage
                ? _buildImageBubble(message)
                : message.isFile
                    ? _buildFileBubble(message)
                    : message.isOrder
                        ? _buildOrderBubble(message)
                        : message.isPayment
                            ? _buildPaymentBubble(message, currentUser)
                            : _buildTextBubble(message),
          ),
        ),
      ),
    );
  }

  BoxDecoration _bubbleDecoration(_ChatMessage message) {
    if (message.isOrder) {
      return BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.4)),
        boxShadow: AppColors.cyanGlow,
      );
    }
    if (message.isImage) {
      return BoxDecoration(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(16),
      );
    }
    if (message.isMe) {
      return BoxDecoration(
        gradient: AppColors.gradientViolet,
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(16),
          topRight: Radius.circular(4),
          bottomLeft: Radius.circular(16),
          bottomRight: Radius.circular(16),
        ),
        boxShadow: AppColors.violetGlow,
      );
    }
    return BoxDecoration(
      color: AppColors.bgSurface,
      borderRadius: const BorderRadius.only(
        topLeft: Radius.circular(4),
        topRight: Radius.circular(16),
        bottomLeft: Radius.circular(16),
        bottomRight: Radius.circular(16),
      ),
      border: Border.all(color: AppColors.borderSubtle),
      boxShadow: [
        BoxShadow(
            color: Colors.black.withValues(alpha: 0.2),
            blurRadius: 6,
            offset: const Offset(0, 2)),
      ],
    );
  }

  // ── Text bubble ───────────────────────────────────────────────────────────────

  Widget _buildTextBubble(_ChatMessage message) {
    final textColor = message.isMe ? Colors.white : AppColors.textPrimary;
    final timeColor = message.isMe
        ? Colors.white.withValues(alpha: 0.6)
        : AppColors.textMuted;

    return Stack(
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 18, right: 50),
          child: Text(
            message.text,
            style: GoogleFonts.dmSans(
                fontSize: 15, color: textColor, height: 1.4),
          ),
        ),
        Positioned(
          bottom: 0, right: 0,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(message.time,
                  style: GoogleFonts.dmSans(
                      fontSize: 10, color: timeColor)),
              if (message.isMe) ...[
                const SizedBox(width: 3),
                _buildStatusIcon(message.status),
              ],
            ],
          ),
        ),
      ],
    );
  }

  // ── Image bubble ──────────────────────────────────────────────────────────────

  Widget _buildImageBubble(_ChatMessage message) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Stack(
        children: [
          CachedNetworkImage(
            imageUrl: message.imageUrl ?? '',
            fit: BoxFit.cover,
            width: MediaQuery.of(context).size.width * _kImageBubbleWidthFraction,
            placeholder: (context, url) => Container(
              height: _kImagePlaceholderHeight,
              color: AppColors.bgCard,
              child: Center(
                child: CircularProgressIndicator(
                    color: AppColors.accentCyan, strokeWidth: 2),
              ),
            ),
            errorWidget: (context, url, error) => Container(
              height: _kImageErrorHeight,
              color: AppColors.bgCard,
              child: Center(
                child: Icon(Icons.broken_image_rounded,
                    color: AppColors.textMuted, size: 32),
              ),
            ),
          ),
          Positioned(
            bottom: 6, right: 8,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.black54,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(message.time,
                      style: GoogleFonts.dmSans(
                          fontSize: 10, color: Colors.white)),
                  if (message.isMe) ...[
                    const SizedBox(width: 3),
                    _buildStatusIcon(message.status),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── File bubble ───────────────────────────────────────────────────────────────

  Widget _buildFileBubble(_ChatMessage message) {
    final category = FileTransferService.mimeCategory(message.mimeType);
    final icon = _fileIcon(category);
    final iconColor = _fileIconColor(category);
    final sizeStr = message.fileSize != null
        ? FileTransferService.formatSize(message.fileSize!)
        : '';

    if (message.isMe) {
      final progress = _uploadProgress[message.messageId];
      final isUploading =
          progress != null && !progress.isComplete && !progress.isFailed;
      final isFailed = message.fileStatus == FileTransferStatus.failed ||
          (progress?.isFailed ?? false);

      return Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _fileIconCircle(icon, iconColor),
                const SizedBox(width: 12),
                Expanded(child: _fileNameAndSize(message.fileName, sizeStr, Colors.white, Colors.white60)),
                if (isUploading)
                  const SizedBox(
                    width: 20, height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white70),
                  )
                else if (isFailed)
                  const Icon(Icons.error_rounded, color: AppColors.errorColor, size: 20)
                else
                  Icon(Icons.check_circle_rounded,
                      color: AppColors.accentGreen, size: 20),
              ],
            ),
            if (isUploading) ...[
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: progress.progress,
                  backgroundColor: Colors.white24,
                  color: AppColors.accentCyan,
                  minHeight: 3,
                ),
              ),
              const SizedBox(height: 4),
              Text('${(progress.progress * 100).toStringAsFixed(0)}%  uploading…',
                  style: GoogleFonts.dmSans(
                      color: Colors.white54, fontSize: 10)),
            ],
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.bottomRight,
              child: Text(message.time,
                  style: GoogleFonts.dmSans(
                      color: Colors.white54, fontSize: 10)),
            ),
          ],
        ),
      );
    }

    final dlProgress = message.transferId != null
        ? _downloadProgress[message.transferId]
        : null;
    final isDownloading =
        dlProgress != null && !dlProgress.isComplete && !dlProgress.isFailed;
    final isSaved = dlProgress?.savedFilePath != null;
    final dlFailed = dlProgress?.isFailed ?? false;

    return Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _fileIconCircle(icon, iconColor, alpha: 0.12),
              const SizedBox(width: 12),
              Expanded(child: _fileNameAndSize(
                  message.fileName, sizeStr, AppColors.textPrimary, AppColors.textMuted)),
            ],
          ),
          const SizedBox(height: 12),
          if (dlFailed)
            Text('Download failed. Tap to retry.',
                style: GoogleFonts.dmSans(
                    color: AppColors.errorColor, fontSize: 11))
          else if (isDownloading)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: dlProgress.progress,
                    backgroundColor: AppColors.borderSubtle,
                    color: iconColor,
                    minHeight: 3,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                    '${(dlProgress.progress * 100).toStringAsFixed(0)}%  downloading…',
                    style: GoogleFonts.dmSans(
                        color: AppColors.textMuted, fontSize: 10)),
              ],
            )
          else if (isSaved)
            GestureDetector(
              onTap: () => OpenFilex.open(dlProgress!.savedFilePath!),
              child: Row(
                children: [
                  Icon(Icons.folder_open_rounded, color: iconColor, size: 16),
                  const SizedBox(width: 6),
                  Text('Open file',
                      style: GoogleFonts.dmSans(
                          color: iconColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w600)),
                ],
              ),
            )
          else
            GestureDetector(
              onTap: () => _downloadFile(message),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: iconColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: iconColor.withValues(alpha: 0.3)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.download_rounded, color: iconColor, size: 14),
                    const SizedBox(width: 6),
                    Text('Download',
                        style: GoogleFonts.dmSans(
                            color: iconColor,
                            fontSize: 12,
                            fontWeight: FontWeight.w600)),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 6),
          Align(
            alignment: Alignment.bottomRight,
            child: Text(message.time,
                style: GoogleFonts.dmSans(
                    color: AppColors.textMuted, fontSize: 10)),
          ),
        ],
      ),
    );
  }

  Widget _fileIconCircle(IconData icon, Color color, {double alpha = 0.15}) =>
      Container(
        width: 40, height: 40,
        decoration: BoxDecoration(
          color: color.withValues(alpha: alpha),
          shape: BoxShape.circle,
          border: Border.all(color: color.withValues(alpha: alpha + 0.15)),
        ),
        child: Icon(icon, color: color, size: 18),
      );

  Widget _fileNameAndSize(
      String? name, String size, Color nameColor, Color sizeColor) =>
      Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(name ?? 'File',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.dmSans(
                  color: nameColor, fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 2),
          Text(size,
              style: GoogleFonts.dmSans(color: sizeColor, fontSize: 10)),
        ],
      );

  IconData _fileIcon(String category) {
    switch (category) {
      case 'image':   return Icons.image_rounded;
      case 'video':   return Icons.videocam_rounded;
      case 'audio':   return Icons.audiotrack_rounded;
      case 'pdf':     return Icons.picture_as_pdf_rounded;
      case 'archive': return Icons.folder_zip_rounded;
      case 'doc':     return Icons.description_rounded;
      case 'sheet':   return Icons.table_chart_rounded;
      case 'slide':   return Icons.slideshow_rounded;
      case 'text':    return Icons.text_snippet_rounded;
      default:        return Icons.insert_drive_file_rounded;
    }
  }

  Color _fileIconColor(String category) {
    switch (category) {
      case 'image':   return AppColors.accentCyan;
      case 'video':   return AppColors.accentViolet;
      case 'audio':   return AppColors.accentGreen;
      case 'pdf':     return AppColors.errorColor;
      case 'archive': return AppColors.accentAmber;
      case 'doc':     return AppColors.accentCyan;
      case 'sheet':   return AppColors.accentGreen;
      case 'slide':   return AppColors.accentAmber;
      default:        return AppColors.textSecondary;
    }
  }

  // ── Order bubble ──────────────────────────────────────────────────────────────

  Widget _buildOrderBubble(_ChatMessage message) {
    return Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 34, height: 34,
                decoration: BoxDecoration(
                  color: AppColors.accentCyan.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                  border: Border.all(
                      color: AppColors.accentCyan.withValues(alpha: 0.3)),
                ),
                child: Icon(Icons.shopping_bag_rounded,
                    color: AppColors.accentCyan, size: 16),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text('Service Order',
                    style: GoogleFonts.dmSans(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.accentCyan,
                        letterSpacing: 0.5)),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.accentAmber.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color: AppColors.accentAmber.withValues(alpha: 0.3)),
                ),
                child: Text('PENDING',
                    style: GoogleFonts.dmSans(
                        color: AppColors.accentAmber,
                        fontSize: 10,
                        fontWeight: FontWeight.w700)),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(message.text,
              style: GoogleFonts.dmSans(
                  color: AppColors.textSecondary, fontSize: 13, height: 1.4)),
          const SizedBox(height: 8),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('View in Orders tab →',
                  style: GoogleFonts.dmSans(
                      color: AppColors.accentCyan,
                      fontSize: 11,
                      fontWeight: FontWeight.w600)),
              Text(message.time,
                  style: GoogleFonts.dmSans(
                      color: AppColors.textMuted, fontSize: 10)),
            ],
          ),
        ],
      ),
    );
  }

  // ── Payment bubble ────────────────────────────────────────────────────────────

  Widget _buildPaymentBubble(_ChatMessage message, AppUser? currentUser) {
    final txnStatus = message.txnStatus;

    // Label logic:
    // isRequest (isReceive) = sender recorded receiving money → they got paid
    // !isRequest (isReceive=false) = sender sent a money request
    String titleText;
    if (message.isRequest) {
      // Sender is recording receipt: "I received money from you"
      titleText = message.isMe ? 'You recorded a receipt' : 'Recorded receiving from you';
    } else {
      // Sender is sending a money request
      titleText = message.isMe ? 'You sent money' : '${widget.partnerName} sent you money';
    }

    // Accent colour: amber for requests/pending, green for sent/accepted
    final isPending = txnStatus == TransactionMessageStatus.pending;
    final isRejected = txnStatus == TransactionMessageStatus.rejected;
    final accentColor = isPending
        ? AppColors.accentAmber
        : (isRejected ? AppColors.errorColor : AppColors.accentGreen);

    final textColor = message.isMe ? Colors.white : AppColors.textPrimary;
    final timeColor = message.isMe
        ? Colors.white.withValues(alpha: 0.6)
        : AppColors.textMuted;

    // Status label for footer
    String statusLabel;
    Color statusColor;
    IconData statusIcon;
    if (txnStatus == null || txnStatus == TransactionMessageStatus.accepted) {
      statusLabel = 'Completed';
      statusColor = AppColors.accentGreen;
      statusIcon = Icons.check_circle_rounded;
    } else if (isPending) {
      statusLabel = 'Pending';
      statusColor = AppColors.accentAmber;
      statusIcon = Icons.hourglass_top_rounded;
    } else {
      statusLabel = 'Rejected';
      statusColor = AppColors.errorColor;
      statusIcon = Icons.cancel_rounded;
    }

    // Recipient sees Accept/Reject buttons for both pending send-money requests and recorded receipts.
    final showActions = !message.isMe &&
        isPending &&
        currentUser != null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Header
        Container(
          padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
          child: Row(
            children: [
              Container(
                width: 42, height: 42,
                decoration: BoxDecoration(
                  color: accentColor.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                  border: Border.all(color: accentColor.withValues(alpha: 0.3)),
                ),
                child: Icon(
                  message.isRequest
                      ? Icons.call_received_rounded
                      : Icons.send_rounded,
                  color: accentColor, size: 20,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(titleText,
                        style: GoogleFonts.dmSans(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: textColor.withValues(alpha: 0.8))),
                    const SizedBox(height: 2),
                    Text('৳ ${message.amount?.toStringAsFixed(2) ?? '0.00'}',
                        style: GoogleFonts.dmSans(
                            fontSize: 22,
                            fontWeight: FontWeight.w700,
                            color: accentColor)),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Note
        if (message.note != null &&
            message.note!.isNotEmpty &&
            message.note != message.text)
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
            child: Text('"${message.note}"',
                style: GoogleFonts.dmSans(
                    color: textColor.withValues(alpha: 0.7),
                    fontStyle: FontStyle.italic,
                    fontSize: 13)),
          ),

        // Accept / Reject action buttons (recipient only, pending only)
        if (showActions) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 10),
            child: Row(
              children: [
                Expanded(
                  child: _paymentActionBtn(
                    label: 'Reject',
                    icon: Icons.close_rounded,
                    color: AppColors.errorColor,
                    onTap: () => _rejectPayment(message),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _paymentActionBtn(
                    label: 'Accept',
                    icon: Icons.check_rounded,
                    color: AppColors.accentGreen,
                    onTap: () => _acceptPayment(message, currentUser),
                  ),
                ),
              ],
            ),
          ),
        ],

        // Footer: status + time
        Container(
          padding: const EdgeInsets.fromLTRB(14, 8, 12, 10),
          decoration: BoxDecoration(
            color: Colors.black.withValues(alpha: 0.12),
            borderRadius: const BorderRadius.only(
              bottomLeft: Radius.circular(16),
              bottomRight: Radius.circular(4),
            ),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(statusIcon, size: 13, color: statusColor),
                  const SizedBox(width: 4),
                  Text(statusLabel,
                      style: GoogleFonts.dmSans(
                          color: statusColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w700)),
                ],
              ),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(message.time,
                      style: GoogleFonts.dmSans(
                          fontSize: 10, color: timeColor)),
                  if (message.isMe) ...[
                    const SizedBox(width: 3),
                    _buildStatusIcon(message.status),
                  ],
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _paymentActionBtn({
    required String label,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.4)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 15),
            const SizedBox(width: 5),
            Text(label,
                style: GoogleFonts.dmSans(
                    color: color, fontSize: 13, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }

  // ── Input bar ─────────────────────────────────────────────────────────────────

  Widget _buildInputBar(AppUser? currentUser) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        border: Border(top: BorderSide(color: AppColors.borderSubtle, width: 1)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: SafeArea(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            // Image button
            _inputIconBtn(
              icon: Icons.image_rounded,
              color: AppColors.accentCyan,
              tooltip: 'Send image',
              onPressed: currentUser != null ? () => _sendImage(currentUser) : null,
            ),
            const SizedBox(width: 4),
            // File button
            _inputIconBtn(
              icon: Icons.attach_file_rounded,
              color: AppColors.accentGreen,
              tooltip: 'Send file',
              onPressed: currentUser != null ? () => _sendFile(currentUser) : null,
            ),
            const SizedBox(width: 4),
            // Transaction button
            _inputIconBtn(
              icon: Icons.currency_exchange_rounded,
              color: AppColors.accentAmber,
              tooltip: 'Send money',
              onPressed: currentUser != null ? () => _showTransactionSheet(currentUser) : null,
            ),
            const SizedBox(width: 4),
            // Order button
            _inputIconBtn(
              icon: Icons.shopping_bag_rounded,
              color: AppColors.accentViolet,
              tooltip: 'Order a service',
              onPressed: currentUser != null ? () => _showOrderSheet(currentUser) : null,
            ),
            const SizedBox(width: 6),

            // Text field
            Expanded(
              child: Container(
                constraints: const BoxConstraints(maxHeight: _kInputMaxHeight),
                decoration: BoxDecoration(
                  color: AppColors.bgCard,
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Focus(
                  onFocusChange: (_) => setState(() {}),
                  child: Builder(builder: (context) {
                    final hasFocus = Focus.of(context).hasFocus;
                    return AnimatedContainer(
                      duration: _kMessageAnimDuration,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(28),
                        boxShadow: hasFocus
                            ? [
                                BoxShadow(
                                  color: AppColors.accentCyan.withValues(alpha: 0.15),
                                  blurRadius: 12,
                                )
                              ]
                            : [],
                      ),
                      child: Scrollbar(
                        child: TextField(
                          controller: _messageController,
                          maxLines: _kInputMaxLines,
                          minLines: 1,
                          textCapitalization: TextCapitalization.sentences,
                          style: GoogleFonts.dmSans(
                              color: AppColors.textPrimary, fontSize: 15),
                          decoration: InputDecoration(
                            hintText: 'Message...',
                            hintStyle: GoogleFonts.dmSans(
                                color: AppColors.textMuted, fontSize: 15),
                            border: InputBorder.none,
                            enabledBorder: InputBorder.none,
                            focusedBorder: InputBorder.none,
                            filled: false,
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 16, vertical: 10),
                            isDense: true,
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ),
            ),
            const SizedBox(width: 8),

            // Send button
            ScaleTransition(
              scale: _sendBtnScale,
              child: AnimatedContainer(
                duration: _kSendBtnAnimDuration,
                width: _kSendBtnSize, height: _kSendBtnSize,
                decoration: BoxDecoration(
                  gradient: _isTyping ? AppColors.gradientCyan : null,
                  color: _isTyping ? null : AppColors.bgCard,
                  shape: BoxShape.circle,
                  border: Border.all(
                    color: _isTyping ? Colors.transparent : AppColors.borderSubtle,
                  ),
                  boxShadow: _isTyping ? AppColors.cyanGlow : [],
                ),
                child: IconButton(
                  icon: Icon(Icons.send_rounded,
                      color: _isTyping ? AppColors.bgPrimary : AppColors.textMuted,
                      size: _kSendIconSize),
                  onPressed: currentUser != null
                      ? () => _sendMessage(currentUser)
                      : null,
                  padding: EdgeInsets.zero,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _inputIconBtn({
    required IconData icon,
    required Color color,
    required String tooltip,
    required VoidCallback? onPressed,
  }) {
    return IconButton(
      icon: Icon(icon, color: color, size: 20),
      tooltip: tooltip,
      onPressed: onPressed,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
    );
  }
}

// ─── Enums & local message model ─────────────────────────────────────────────

enum MessageStatus { sent, delivered, seen }

class _ChatMessage {
  final String text;
  final String time;
  final bool isMe;
  final String senderId;
  final bool isPayment;
  final bool isOrder;
  final bool isFile;
  final bool isImage;
  final bool isRequest;
  final double? amount;
  final String? note;
  final MessageStatus? status;
  final String? orderId;
  final TransactionMessageStatus? txnStatus;
  final String? imageUrl;

  // File fields
  final String? fileName;
  final int? fileSize;
  final String? mimeType;
  final String? transferId;
  final FileTransferStatus fileStatus;
  final int? totalChunks;
  final String messageId;

  const _ChatMessage({
    required this.text,
    required this.time,
    required this.isMe,
    required this.senderId,
    required this.isPayment,
    this.isOrder = false,
    this.isFile = false,
    this.isImage = false,
    this.isRequest = false,
    this.amount,
    this.note,
    this.status,
    this.orderId,
    this.txnStatus,
    this.imageUrl,
    this.fileName,
    this.fileSize,
    this.mimeType,
    this.transferId,
    this.fileStatus = FileTransferStatus.pending,
    this.totalChunks,
    this.messageId = '',
  });
}

// ─── Transaction bottom sheet ─────────────────────────────────────────────────

class _TransactionBottomSheet extends ConsumerStatefulWidget {
  /// [isSend] = true  → Send Money request (pending, recipient must accept)
  /// [isSend] = false → Record Receipt (immediate, no confirmation)
  final Function(bool isSend, double amount, String? note) onTransactionSubmit;

  const _TransactionBottomSheet({required this.onTransactionSubmit});

  @override
  ConsumerState<_TransactionBottomSheet> createState() =>
      _TransactionBottomSheetState();
}

class _TransactionBottomSheetState extends ConsumerState<_TransactionBottomSheet> {
  bool? _isSend; // null = not chosen yet
  final _amountController = TextEditingController();
  final _noteController = TextEditingController();

  @override
  void dispose() {
    _amountController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  void _submit() {
    if (_amountController.text.isEmpty) return;
    final amount = double.tryParse(_amountController.text);
    if (amount == null || amount <= 0) return;
    widget.onTransactionSubmit(_isSend!, amount, _noteController.text.trim());
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        border: Border.all(color: AppColors.borderGlow),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.4),
              blurRadius: 30,
              offset: const Offset(0, -8)),
        ],
      ),
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom + 28,
        top: 8, left: 24, right: 24,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: Container(
              width: 40, height: 4,
              margin: const EdgeInsets.only(bottom: 20),
              decoration: BoxDecoration(
                  color: AppColors.borderSubtle,
                  borderRadius: BorderRadius.circular(2)),
            ),
          ),
          Text(
            _isSend == null
                ? 'New Transaction'
                : (_isSend! ? 'Send Money' : 'Record Receipt'),
            style: GoogleFonts.dmSans(
                fontSize: 22,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
                letterSpacing: 0.8),
            textAlign: TextAlign.center,
          ),
          if (_isSend == null) ...[
            const SizedBox(height: 6),
            Text('Choose a transaction type to continue',
                style: GoogleFonts.dmSans(
                    fontSize: 13, color: AppColors.textSecondary),
                textAlign: TextAlign.center),
          ],
          const SizedBox(height: 24),

          if (_isSend == null) ...[
            Row(
              children: [
                Expanded(
                  child: _buildTypeCard(
                    Icons.send_rounded,
                    'Send Money',
                    'Request to send payment\n(recipient must accept)',
                    AppColors.accentCyan,
                    () => setState(() => _isSend = true),
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: _buildTypeCard(
                    Icons.call_received_rounded,
                    'Record Receipt',
                    'Record money you\nalready received',
                    AppColors.accentAmber,
                    () => setState(() => _isSend = false),
                  ),
                ),
              ],
            ),
          ] else ...[
            // Amount
            Container(
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: TextField(
                controller: _amountController,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                style: GoogleFonts.dmSans(
                    fontSize: 34,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary),
                textAlign: TextAlign.center,
                decoration: InputDecoration(
                  prefixText: '৳  ',
                  prefixStyle: GoogleFonts.dmSans(
                      fontSize: 28,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textSecondary),
                  hintText: '0.00',
                  hintStyle: GoogleFonts.dmSans(
                      fontSize: 34,
                      fontWeight: FontWeight.w300,
                      color: AppColors.textMuted),
                  border: InputBorder.none,
                  filled: false,
                ),
              ),
            ),
            const SizedBox(height: 14),

            // Note
            Container(
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              child: TextField(
                controller: _noteController,
                style: GoogleFonts.dmSans(
                    color: AppColors.textPrimary, fontSize: 14),
                decoration: InputDecoration(
                  hintText: 'Add a note (optional)...',
                  hintStyle: GoogleFonts.dmSans(
                      color: AppColors.textMuted, fontSize: 14),
                  prefixIcon: Icon(Icons.notes_rounded,
                      color: AppColors.textSecondary, size: 18),
                  border: InputBorder.none,
                  filled: false,
                  contentPadding: const EdgeInsets.symmetric(vertical: 14),
                ),
              ),
            ),
            const SizedBox(height: 24),

            Row(
              children: [
                Container(
                  width: 44, height: 44,
                  decoration: BoxDecoration(
                    color: AppColors.bgSurface,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.borderSubtle),
                  ),
                  child: IconButton(
                    icon: Icon(Icons.arrow_back_rounded,
                        color: AppColors.textSecondary, size: 20),
                    onPressed: () => setState(() => _isSend = null),
                    padding: EdgeInsets.zero,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: _isSend!
                          ? AppColors.gradientCyan
                          : LinearGradient(
                              colors: [AppColors.accentAmber, AppColors.accentAmber.withValues(alpha: 0.7)]),
                      borderRadius: BorderRadius.circular(12),
                      boxShadow:
                          _isSend! ? AppColors.cyanGlow : AppColors.violetGlow,
                    ),
                    child: ElevatedButton(
                      onPressed: _submit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.transparent,
                        shadowColor: Colors.transparent,
                        padding: const EdgeInsets.symmetric(vertical: 15),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                      ),
                      child: Text(
                        _isSend! ? 'Send Money Request' : 'Record Receipt',
                        style: GoogleFonts.dmSans(
                            fontSize: 15,
                            fontWeight: FontWeight.w700,
                            color: AppColors.bgPrimary),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildTypeCard(IconData icon, String title, String subtitle,
      Color color, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.06),
          border: Border.all(color: color.withValues(alpha: 0.25), width: 1.5),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          children: [
            Container(
              width: 52, height: 52,
              decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12), shape: BoxShape.circle),
              child: Icon(icon, size: 26, color: color),
            ),
            const SizedBox(height: 12),
            Text(title,
                style: GoogleFonts.dmSans(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    color: color,
                    letterSpacing: 0.5)),
            const SizedBox(height: 4),
            Text(subtitle,
                style: GoogleFonts.dmSans(
                    fontSize: 11, color: color.withValues(alpha: 0.7)),
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}


// ─── Orders Tab ───────────────────────────────────────────────────────────────

class _OrdersTab extends ConsumerWidget {
  final String threadId;
  final String currentUserId;

  const _OrdersTab({required this.threadId, required this.currentUserId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(themeControllerProvider);
    final ordersAsync = ref.watch(threadOrdersProvider(threadId));

    return ordersAsync.when(
      data: (orders) {
        final active =
            orders.where((o) => o.status != OrderStatus.delivered).toList();
        final delivered =
            orders.where((o) => o.status == OrderStatus.delivered).toList();

        if (orders.isEmpty) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 72, height: 72,
                  decoration: BoxDecoration(
                    color: AppColors.accentCyan.withValues(alpha: 0.08),
                    shape: BoxShape.circle,
                    border: Border.all(
                        color: AppColors.accentCyan.withValues(alpha: 0.2)),
                  ),
                  child: Icon(Icons.shopping_bag_outlined,
                      size: 32, color: AppColors.accentCyan),
                ),
                const SizedBox(height: 16),
                Text('No orders yet',
                    style: GoogleFonts.dmSans(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary)),
                const SizedBox(height: 6),
                Text('Tap 🛍 to order a service from this user',
                    style: GoogleFonts.dmSans(
                        fontSize: 13, color: AppColors.textSecondary)),
              ],
            ),
          );
        }

        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            if (active.isNotEmpty) ...[
              _sectionHeader('Active Orders', active.length),
              const SizedBox(height: 10),
              ...active.map((o) => _OrderCard(order: o, currentUserId: currentUserId)),
              const SizedBox(height: 20),
            ],
            if (delivered.isNotEmpty) ...[
              _sectionHeader('Completed', delivered.length),
              const SizedBox(height: 10),
              ...delivered.map((o) => _OrderCard(order: o, currentUserId: currentUserId)),
            ],
          ],
        );
      },
      loading: () => Center(
        child: CircularProgressIndicator(
            color: AppColors.accentCyan, strokeWidth: 2.5),
      ),
      error: (e, _) => Center(
        child: Text('Error: $e',
            style: const TextStyle(color: AppColors.errorColor)),
      ),
    );
  }

  Widget _sectionHeader(String label, int count) {
    return Row(
      children: [
        Text(label,
            style: GoogleFonts.dmSans(
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: AppColors.textSecondary,
                letterSpacing: 0.5)),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
          decoration: BoxDecoration(
            color: AppColors.accentCyan.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.2)),
          ),
          child: Text('$count',
              style: GoogleFonts.dmSans(
                  color: AppColors.accentCyan,
                  fontSize: 11,
                  fontWeight: FontWeight.w700)),
        ),
      ],
    );
  }
}

// ─── Order Card ───────────────────────────────────────────────────────────────

class _OrderCard extends ConsumerStatefulWidget {
  final AppOrder order;
  final String currentUserId;

  const _OrderCard({required this.order, required this.currentUserId});

  @override
  ConsumerState<_OrderCard> createState() => _OrderCardState();
}

class _OrderCardState extends ConsumerState<_OrderCard> {
  bool _isUpdating = false;

  Color _statusColor(OrderStatus s) {
    switch (s) {
      case OrderStatus.pending:    return AppColors.accentAmber;
      case OrderStatus.processing: return AppColors.accentCyan;
      case OrderStatus.delivered:  return AppColors.accentGreen;
    }
  }

  Future<void> _updateStatus(OrderStatus newStatus) async {
    setState(() => _isUpdating = true);
    try {
      await ref.read(orderRepositoryProvider).updateOrderStatus(
            orderId: widget.order.id,
            sellerId: widget.order.sellerId,
            price: widget.order.price,
            newStatus: newStatus,
          );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e',
              style: GoogleFonts.dmSans(color: AppColors.textPrimary)),
          backgroundColor: AppColors.bgCard,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ));
      }
    } finally {
      if (mounted) setState(() => _isUpdating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final o = widget.order;
    final isSeller = widget.currentUserId == o.sellerId;
    final statusColor = _statusColor(o.status);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: statusColor.withValues(alpha: 0.3)),
        boxShadow: [
          BoxShadow(
              color: statusColor.withValues(alpha: 0.08),
              blurRadius: 12,
              offset: const Offset(0, 4)),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 38, height: 38,
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.12),
                    shape: BoxShape.circle,
                    border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                  ),
                  child: Icon(Icons.shopping_bag_rounded,
                      color: statusColor, size: 18),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(o.serviceTitle,
                      style: GoogleFonts.dmSans(
                          color: AppColors.textPrimary,
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.4)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                      gradient: AppColors.gradientCyan,
                      borderRadius: BorderRadius.circular(20)),
                  child: Text('৳${o.price.toStringAsFixed(0)}',
                      style: GoogleFonts.dmSans(
                          color: AppColors.bgPrimary,
                          fontSize: 12,
                          fontWeight: FontWeight.w700)),
                ),
              ],
            ),
            const SizedBox(height: 10),

            if (o.serviceDescription.isNotEmpty)
              Text(o.serviceDescription,
                  style: GoogleFonts.dmSans(
                      color: AppColors.textSecondary, fontSize: 13, height: 1.4)),

            if (o.buyerNote.isNotEmpty) ...[
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppColors.bgSurface,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.borderSubtle),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.note_rounded,
                        color: AppColors.textMuted, size: 14),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(o.buyerNote,
                          style: GoogleFonts.dmSans(
                              color: AppColors.textSecondary,
                              fontSize: 12,
                              fontStyle: FontStyle.italic)),
                    ),
                  ],
                ),
              ),
            ],

            if (o.requirementsAnswers.isNotEmpty) ...[
              const SizedBox(height: 8),
              ...o.requirementsAnswers.entries.map((e) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppColors.bgSurface,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: AppColors.borderSubtle),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(Icons.help_outline_rounded,
                            color: AppColors.accentCyan, size: 14),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                e.key,
                                style: GoogleFonts.dmSans(
                                  color: AppColors.textSecondary,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                e.value,
                                style: GoogleFonts.dmSans(
                                  color: AppColors.textPrimary,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              }),
            ],

            const SizedBox(height: 12),

            Row(
              children: [
                Icon(
                  isSeller ? Icons.person_rounded : Icons.storefront_rounded,
                  size: 14, color: AppColors.textMuted,
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                      isSeller ? 'From: ${o.buyerName}' : 'Seller: ${o.sellerName}',
                      style: GoogleFonts.dmSans(
                          color: AppColors.textMuted, fontSize: 12)),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                  ),
                  child: Text(o.status.label.toUpperCase(),
                      style: GoogleFonts.dmSans(
                          color: statusColor,
                          fontSize: 10,
                          fontWeight: FontWeight.w700)),
                ),
              ],
            ),

            if (isSeller && o.status != OrderStatus.delivered) ...[
              const SizedBox(height: 12),
              Divider(color: AppColors.borderSubtle, height: 1),
              const SizedBox(height: 12),
              _isUpdating
                  ? Center(
                      child: SizedBox(
                        width: 22, height: 22,
                        child: CircularProgressIndicator(
                            color: AppColors.accentCyan, strokeWidth: 2.5),
                      ),
                    )
                  : Row(
                      children: [
                        if (o.status == OrderStatus.pending)
                          Expanded(
                            child: _actionBtn(
                              label: 'Accept Order',
                              icon: Icons.check_circle_rounded,
                              color: AppColors.accentCyan,
                              onTap: () => _updateStatus(OrderStatus.processing),
                            ),
                          ),
                        if (o.status == OrderStatus.processing)
                          Expanded(
                            child: _actionBtn(
                              label: 'Mark Delivered',
                              icon: Icons.local_shipping_rounded,
                              color: AppColors.accentGreen,
                              onTap: () => _updateStatus(OrderStatus.delivered),
                            ),
                          ),
                      ],
                    ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _actionBtn({
    required String label,
    required IconData icon,
    required Color color,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.35)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: color, size: 16),
            const SizedBox(width: 6),
            Text(label,
                style: GoogleFonts.dmSans(
                    color: color, fontSize: 13, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }
}
