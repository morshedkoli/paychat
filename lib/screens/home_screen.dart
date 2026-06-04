import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'package:paychat/core/theme/app_colors.dart';
import 'package:paychat/providers/theme_provider.dart';
import 'package:paychat/models/app_user.dart';
import 'package:paychat/models/app_transaction.dart';
import 'package:paychat/providers/auth_providers.dart';
import 'package:paychat/providers/service_providers.dart';
import 'package:paychat/screens/chat_screen.dart';
import 'package:paychat/screens/settings_detail_screen.dart';
import 'package:paychat/screens/settings/mock_toggle_screen.dart';
import 'package:paychat/screens/settings/chat_settings_screen.dart';
import 'package:paychat/screens/settings/change_number_screen.dart';
import 'package:paychat/screens/settings/delete_account_screen.dart';
import 'package:paychat/screens/qr/qr_display_screen.dart';
import 'package:paychat/screens/qr/qr_scanner_screen.dart';
import 'package:paychat/screens/add_contact_screen.dart';
import 'package:paychat/screens/profile_setup_screen.dart';
import 'package:paychat/screens/services/manage_services_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with TickerProviderStateMixin {
  int _selectedIndex = 0;
  late final TabController _tabController;

  bool _isSearching = false;
  String _searchQuery = '';
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _tabController.addListener(() {
      setState(() {
        _selectedIndex = _tabController.index;
      });
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    final sessionState = ref.watch(sessionStateProvider);
    final user = sessionState.user;

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: _buildAppBar(),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildChatsTab(user),
          _buildAnalyticsTab(user),
          _buildSettingsTab(user),
        ],
      ),
      floatingActionButton: _buildFab(user),
      bottomNavigationBar: _buildBottomNav(),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      backgroundColor: AppColors.bgPrimary,
      elevation: 0,
      systemOverlayStyle: SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness:
            AppColors.isDark ? Brightness.light : Brightness.dark,
      ),
      title: _isSearching
          ? Container(
              height: 40,
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.primaryGreen.withValues(alpha: 0.50)),
              ),
              child: TextField(
                controller: _searchController,
                autofocus: true,
                style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 15),
                decoration: InputDecoration(
                  hintText: 'Search chats…',
                  hintStyle: GoogleFonts.dmSans(
                    color: AppColors.textMuted,
                    fontSize: 15,
                  ),
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  filled: false,
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                  prefixIcon: Icon(
                    Icons.search_rounded,
                    color: AppColors.textSecondary,
                    size: 18,
                  ),
                ),
                onChanged: (val) =>
                    setState(() => _searchQuery = val.toLowerCase()),
              ),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: AppColors.primaryGreen,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                ShaderMask(
                  shaderCallback: (bounds) => LinearGradient(
                    colors: [AppColors.primaryGreen, AppColors.primaryGreenLight],
                  ).createShader(bounds),
                  child: Text(
                    'PayChat',
                    style: GoogleFonts.dmSans(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: AppColors.textPrimary,
                      letterSpacing: -0.3,
                    ),
                  ),
                ),
              ],
            ),
      actions: _isSearching
          ? [
              IconButton(
                icon: Icon(
                  Icons.close_rounded,
                  color: AppColors.textPrimary.withValues(alpha: 0.85),
                  size: 20,
                ),
                onPressed: () => setState(() {
                  _isSearching = false;
                  _searchQuery = '';
                  _searchController.clear();
                }),
              ),
            ]
          : [
              IconButton(
                icon: Icon(
                  Icons.qr_code_scanner_rounded,
                  color: AppColors.textPrimary.withValues(alpha: 0.85),
                  size: 22,
                ),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const QrDisplayScreen()),
                ),
              ),
              IconButton(
                icon: Icon(
                  Icons.search_rounded,
                  color: AppColors.textPrimary.withValues(alpha: 0.85),
                  size: 22,
                ),
                onPressed: () => setState(() => _isSearching = true),
              ),
              PopupMenuButton<String>(
                icon: Icon(
                  Icons.more_vert_rounded,
                  color: AppColors.textPrimary.withValues(alpha: 0.85),
                  size: 22,
                ),
                color: AppColors.bgSecondary,
                elevation: 12,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(16),
                  side: BorderSide(
                    color: AppColors.bgSurface,
                    width: 0.5,
                  ),
                ),
                onSelected: (value) {
                  if (value == 'logout') {
                    ref.read(sessionStateProvider.notifier).signOut();
                  } else {
                    _showComingSoon(value);
                  }
                },
                itemBuilder: (BuildContext context) => [
                  _popupItem('New group', Icons.group_add_rounded),
                  _popupItem('Payments', Icons.account_balance_wallet_rounded),
                  _popupItem('Settings', Icons.settings_rounded),
                  const PopupMenuDivider(height: 0.5),
                  _popupItem('logout', Icons.logout_rounded, isDestructive: true),
                ],
              ),
            ],
    );
  }

  PopupMenuItem<String> _popupItem(String value, IconData icon,
      {bool isDestructive = false}) {
    return PopupMenuItem<String>(
      value: value,
      child: Row(
        children: [
          Icon(
            icon,
            size: 17,
            color: isDestructive
                ? AppColors.errorColor
                : AppColors.textSecondary,
          ),
          const SizedBox(width: 10),
          Text(
            value == 'logout' ? 'Log out' : value,
            style: GoogleFonts.dmSans(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: isDestructive
                  ? AppColors.errorColor
                  : AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNav() {
    final List<List<IconData>> tabIcons = [
      [Icons.chat_bubble_outline_rounded, Icons.chat_bubble_rounded],
      [Icons.account_balance_wallet_outlined, Icons.account_balance_wallet_rounded],
      [Icons.person_outline_rounded, Icons.person_rounded],
    ];
    const tabLabels = ['Chats', 'Pay', 'Profile'];

    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgSecondary,
        border: Border(
          top: BorderSide(
            color: AppColors.bgCard,
            width: 1,
          ),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.40),
            blurRadius: 24,
            offset: const Offset(0, -6),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 10),
          child: Row(
            children: List.generate(3, (i) {
              final isSelected = _selectedIndex == i;
              return Expanded(
                child: GestureDetector(
                  onTap: () => _tabController.animateTo(i),
                  behavior: HitTestBehavior.opaque,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 250),
                    curve: Curves.easeOutCubic,
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: isSelected
                          ? AppColors.primaryGreen.withValues(alpha: 0.12)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(14),
                      border: isSelected
                          ? Border.all(
                              color: AppColors.primaryGreen.withValues(alpha: 0.30),
                              width: 1,
                            )
                          : null,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 200),
                          child: Icon(
                            isSelected ? tabIcons[i][1] : tabIcons[i][0],
                            key: ValueKey(isSelected),
                            color: isSelected
                                ? AppColors.primaryGreen
                                : AppColors.textMuted,
                            size: 22,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          tabLabels[i],
                          style: GoogleFonts.dmSans(
                            fontSize: 10,
                            fontWeight:
                                isSelected ? FontWeight.w700 : FontWeight.w500,
                            color: isSelected
                                ? AppColors.primaryGreen
                                : AppColors.textMuted,
                            letterSpacing: 0.2,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }),
          ),
        ),
      ),
    );
  }

  Widget _buildFab(AppUser? user) {
    return FloatingActionButton(
      onPressed: () {
        if (_selectedIndex == 0) {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => const AddContactScreen()),
          );
        } else if (_selectedIndex == 2 && user != null) {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (_) => ProfileSetupScreen(user: user)),
          );
        } else {
          _showComingSoon('New Action');
        }
      },
      child: Icon(
        _selectedIndex == 0
            ? Icons.edit_rounded
            : _selectedIndex == 1
                ? Icons.send_rounded
                : Icons.edit_rounded,
        size: 22,
      ),
    );
  }

  // ─── Chats Tab ──────────────────────────────────────────────────────────────

  Widget _buildChatsTab(AppUser? user) {
    if (user == null) {
      return Container(
        color: AppColors.bgPrimary,
        child: Center(
          child: CircularProgressIndicator(
            color: AppColors.primaryGreen,
            strokeWidth: 2.5,
          ),
        ),
      );
    }

    final threadsAsync = ref.watch(userThreadsProvider(user.uid));

    return Container(
      // Slight gradient shift from the app bar into the list
      decoration: BoxDecoration(gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [AppColors.bgPrimary, AppColors.bgSecondary],
          stops: [0.0, 1.0],
        ),
      ),
      child: threadsAsync.when(
        data: (threads) {
          final filteredThreads = _searchQuery.isEmpty
              ? threads
              : threads.where((chat) {
                  final users =
                      chat['users'] as Map<String, dynamic>? ?? {};
                  bool matched = false;
                  for (final u in users.values) {
                    final name = (u['name'] as String? ?? '').toLowerCase();
                    if (name.contains(_searchQuery)) matched = true;
                  }
                  return matched;
                }).toList();

          if (filteredThreads.isEmpty) {
            return _buildEmptyChats();
          }

          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 100),
            itemCount: filteredThreads.length,
            separatorBuilder: (context, index) => const SizedBox(height: 6),
            itemBuilder: (context, index) {
              return _buildChatTile(filteredThreads[index], user);
            },
          );
        },
        loading: () => Center(
          child: CircularProgressIndicator(
            color: AppColors.primaryGreen,
            strokeWidth: 2.5,
          ),
        ),
        error: (e, st) => Center(
          child: Text(
            'Error: $e',
            style: const TextStyle(color: AppColors.errorColor),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyChats() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Pulsing icon
          Container(
            width: 96,
            height: 96,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: LinearGradient(
                colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primaryGreen.withValues(alpha: 0.35),
                  blurRadius: 28,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: const Icon(
              Icons.chat_bubble_outline_rounded,
              size: 42,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 28),
          Text(
            'No chats yet',
            style: GoogleFonts.dmSans(
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: AppColors.textPrimary,
              letterSpacing: -0.4,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'Start a conversation with someone\nand split payments instantly.',
            textAlign: TextAlign.center,
            style: GoogleFonts.dmSans(
              fontSize: 14,
              color: AppColors.textSecondary,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 32),
          GestureDetector(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const AddContactScreen()),
            ),
            child: Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 28, vertical: 14),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(50),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primaryGreen.withValues(alpha: 0.40),
                    blurRadius: 16,
                    offset: const Offset(0, 5),
                  ),
                ],
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.add_rounded, color: Colors.white, size: 18),
                  const SizedBox(width: 8),
                  Text(
                    'New Chat',
                    style: GoogleFonts.dmSans(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
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

  Widget _buildChatTile(Map<String, dynamic> thread, AppUser currentUser) {
    final usersMap = thread['users'] as Map<String, dynamic>? ?? {};
    final otherUserIds =
        usersMap.keys.where((id) => id != currentUser.uid).toList();
    final otherUserId =
        otherUserIds.isNotEmpty ? otherUserIds.first : currentUser.uid;
    final otherUser = usersMap[otherUserId] as Map<String, dynamic>? ?? {};

    final String name = otherUser['name'] ?? 'Unknown User';
    final String photoUrl = otherUser['photoUrl'] ?? '';
    final String initials = name.isNotEmpty ? name[0].toUpperCase() : '?';
    final bool isGuest = otherUser['isGuest'] == true;
    final String lastMessage = thread['lastMessagePreview'] ?? '';
    final int unread = thread['unreadCount'] ?? 0;
    final balancesMap = thread['balances'] as Map<String, dynamic>? ?? {};
    final double balance =
        (balancesMap[currentUser.uid] as num?)?.toDouble() ?? 0.0;

    // Timestamp
    String timeLabel = '';
    final lastTs = thread['lastMessageAt'];
    if (lastTs != null) {
      try {
        final dt = (lastTs as dynamic).toDate() as DateTime;
        final now = DateTime.now();
        if (now.difference(dt).inDays == 0) {
          timeLabel = '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
        } else if (now.difference(dt).inDays == 1) {
          timeLabel = 'Yesterday';
        } else {
          timeLabel = '${dt.day}/${dt.month}';
        }
      } catch (_) {}
    }

    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        splashColor: AppColors.primaryGreen.withValues(alpha: 0.08),
        highlightColor: Colors.white.withValues(alpha: 0.03),
        onTap: () async {
          final chatRepo = ref.read(chatRepositoryProvider);
          await chatRepo.markThreadAsRead(thread['id']);
          if (!mounted) return;
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => ChatScreen(
                threadId: thread['id'],
                partnerName: name,
                partnerInitials: initials,
                avatarColor: AppColors.primaryGreen,
                partnerUserId: otherUserId,
              ),
            ),
          );
        },
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            color: unread > 0
                ? AppColors.bgCard
                : AppColors.bgSurface,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: unread > 0
                  ? AppColors.primaryGreen.withValues(alpha: 0.35)
                  : AppColors.bgCard,
              width: 1,
            ),
          ),
          child: Row(
            children: [
              // ── Avatar ──────────────────────────────────────────────────
              Stack(
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                    padding: const EdgeInsets.all(2),
                    child: Container(
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: AppColors.bgPrimary,
                        image: photoUrl.isNotEmpty
                            ? DecorationImage(
                                image: CachedNetworkImageProvider(photoUrl),
                                fit: BoxFit.cover,
                              )
                            : null,
                      ),
                      alignment: Alignment.center,
                      child: photoUrl.isEmpty
                          ? Text(
                              initials,
                              style: GoogleFonts.dmSans(
                                color: AppColors.primaryGreen,
                                fontSize: 20,
                                fontWeight: FontWeight.w800,
                              ),
                            )
                          : null,
                    ),
                  ),
                  // Unread badge
                  if (unread > 0)
                    Positioned(
                      right: 0,
                      top: 0,
                      child: Container(
                        width: 20,
                        height: 20,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                          ),
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: AppColors.bgPrimary,
                            width: 2,
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          unread > 9 ? '9+' : '$unread',
                          style: GoogleFonts.dmSans(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: 13),

              // ── Text content ─────────────────────────────────────────────
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Expanded(
                          child: Text(
                            name,
                            style: GoogleFonts.dmSans(
                              color: AppColors.textPrimary,
                              fontSize: 15,
                              fontWeight: unread > 0
                                  ? FontWeight.w700
                                  : FontWeight.w600,
                              letterSpacing: -0.1,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        // Timestamp
                        if (timeLabel.isNotEmpty) ...[  
                          const SizedBox(width: 6),
                          Text(
                            timeLabel,
                            style: GoogleFonts.dmSans(
                              fontSize: 11,
                              color: unread > 0
                                  ? AppColors.primaryGreen
                                  : AppColors.textMuted,
                              fontWeight: unread > 0
                                  ? FontWeight.w700
                                  : FontWeight.w400,
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            lastMessage.isEmpty ? 'Say hello! 👋' : lastMessage,
                            style: GoogleFonts.dmSans(
                              color: unread > 0
                                  ? AppColors.textPrimary.withValues(alpha: 0.85)
                                  : AppColors.textMuted,
                              fontSize: 13,
                              fontWeight: unread > 0
                                  ? FontWeight.w600
                                  : FontWeight.w400,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        // Tags row — guest + balance
                        if (isGuest) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color:
                                  AppColors.bgSurface,
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(
                                color: AppColors.borderSubtle,
                              ),
                            ),
                            child: Text(
                              'Guest',
                              style: GoogleFonts.dmSans(
                                color: AppColors.textSecondary,
                                fontSize: 9,
                                fontWeight: FontWeight.w700,
                                letterSpacing: 0.3,
                              ),
                            ),
                          ),
                        ],
                        if (balance != 0.0) ...[
                          const SizedBox(width: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 7, vertical: 2),
                            decoration: BoxDecoration(
                              color: (balance > 0
                                      ? AppColors.primaryGreen
                                      : AppColors.accentRose)
                                  .withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(7),
                              border: Border.all(
                                color: (balance > 0
                                        ? AppColors.primaryGreen
                                        : AppColors.accentRose)
                                    .withValues(alpha: 0.40),
                              ),
                            ),
                            child: Text(
                              balance > 0
                                  ? '+৳${balance.toStringAsFixed(0)}'
                                  : '-৳${balance.abs().toStringAsFixed(0)}',
                              style: GoogleFonts.dmSans(
                                color: balance > 0
                                    ? AppColors.primaryGreen
                                    : AppColors.accentRose,
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ─── Pay Tab ─────────────────────────────────────────────────────────────

  Widget _buildAnalyticsTab(AppUser? user) {
    if (user == null) {
      return Container(
        color: AppColors.bgPrimary,
        child: Center(
          child: CircularProgressIndicator(
            color: AppColors.primaryGreen,
            strokeWidth: 2.5,
          ),
        ),
      );
    }

    final transactionsAsync = ref.watch(userTransactionsProvider(user.uid));

    return Container(
      decoration: BoxDecoration(gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [AppColors.bgPrimary, AppColors.bgSecondary],
        ),
      ),
      child: transactionsAsync.when(
        data: (transactions) {
          double totalSent = 0;
          double totalReceived = 0;
          for (var t in transactions) {
            if (t.type == TransactionType.received ||
                t.type == TransactionType.due) {
              totalReceived += t.amount;
            }
            if (t.type == TransactionType.paid ||
                t.type == TransactionType.sent) {
              totalSent += t.amount;
            }
          }
          final displayBalance = user.walletBalance;

          return SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 100),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // ── Hero Balance Card ───────────────────────────────────────
                _buildBalanceCard(displayBalance, totalReceived, totalSent),
                const SizedBox(height: 24),

                // ── Quick Actions ───────────────────────────────────────────
                _buildQuickActionsRow(user, transactions),
                const SizedBox(height: 28),

                // ── Section Header ──────────────────────────────────────────
                Row(
                  children: [
                    Container(
                      width: 3,
                      height: 16,
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                        ),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      'Recent Transactions',
                      style: GoogleFonts.dmSans(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const Spacer(),
                    if (transactions.isNotEmpty)
                      Text(
                        '${transactions.length} total',
                        style: GoogleFonts.dmSans(
                          fontSize: 12,
                          color: AppColors.textMuted,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 14),

                // ── Transactions ─────────────────────────────────────────────
                if (transactions.isEmpty)
                  _buildEmptyTransactions()
                else
                  Container(
                    decoration: BoxDecoration(
                      color: AppColors.bgSurface,
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: AppColors.bgCard,
                      ),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(20),
                      child: Column(
                        children: transactions
                            .take(10)
                            .toList()
                            .asMap()
                            .entries
                            .map((entry) {
                          final t = entry.value;
                          final isLast =
                              entry.key == (transactions.take(10).length - 1);
                          final isPositive =
                              t.type == TransactionType.received ||
                                  t.type == TransactionType.due;
                          final amountPrefix = isPositive ? '+' : '-';
                          final amountColor = isPositive
                              ? AppColors.primaryGreen
                              : AppColors.accentRose;
                          final dateStr =
                              DateFormat('MMM d, h:mm a').format(t.createdAt);

                          return Column(
                            children: [
                              _buildTransactionTile(
                                t.contactName,
                                dateStr,
                                '$amountPrefix ৳${t.amount.toStringAsFixed(0)}',
                                amountColor,
                                isPositive,
                              ),
                              if (!isLast)
                                Divider(
                                  height: 1,
                                  indent: 68,
                                  color: AppColors.borderSubtle,
                                ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
        loading: () => Center(
          child: CircularProgressIndicator(
            color: AppColors.primaryGreen,
            strokeWidth: 2.5,
          ),
        ),
        error: (err, st) => Center(
          child: Text(
            'Error: $err',
            style: const TextStyle(color: AppColors.errorColor),
          ),
        ),
      ),
    );
  }

  Widget _buildBalanceCard(double balance, double received, double sent) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: AppColors.primaryGreen.withValues(alpha: 0.25),
          width: 1,
        ),
        boxShadow: [
          BoxShadow(
            color: AppColors.primaryGreen.withValues(alpha: 0.12),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: Stack(
          children: [
            // Card base
            Container(color: AppColors.bgCard),
            Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Header row
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: AppColors.primaryGreen.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                            color: AppColors.primaryGreen.withValues(alpha: 0.30),
                          ),
                        ),
                        child: Icon(
                          Icons.account_balance_wallet_rounded,
                          color: AppColors.primaryGreen,
                          size: 16,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Text(
                        'NET BALANCE',
                        style: GoogleFonts.dmSans(
                          color: AppColors.textSecondary,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          letterSpacing: 1.8,
                        ),
                      ),
                      const Spacer(),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.bgCard,
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(
                            color: AppColors.borderSubtle,
                          ),
                        ),
                        child: Text(
                          'BDT',
                          style: GoogleFonts.dmSans(
                            color: AppColors.textPrimary.withValues(alpha: 0.85),
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 1,
                          ),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 20),

                  // Big balance number
                  ShaderMask(
                    shaderCallback: (bounds) => LinearGradient(
                      colors: balance >= 0
                          ? [
                              AppColors.primaryGreen,
                              AppColors.primaryGreenLight,
                            ]
                          : [
                              AppColors.errorColor,
                              AppColors.errorColor.withValues(alpha: 0.6),
                            ],
                    ).createShader(bounds),
                    child: Text(
                      '৳ ${balance.toStringAsFixed(2)}',
                      style: GoogleFonts.dmSans(
                        color: Colors.white,
                        fontSize: 42,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -1.8,
                        height: 1.0,
                      ),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: BoxDecoration(
                          color: balance >= 0
                              ? AppColors.primaryGreen
                              : AppColors.accentRose,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        balance >= 0
                            ? 'You are in the clear'
                            : 'You have pending dues',
                        style: GoogleFonts.dmSans(
                          color: AppColors.textMuted,
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 24),

                  // Stat chips
                  Row(
                    children: [
                      Expanded(
                        child: _buildStatChip(
                          Icons.arrow_downward_rounded,
                          'Received',
                          received,
                          AppColors.primaryGreen,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _buildStatChip(
                          Icons.arrow_upward_rounded,
                          'Sent',
                          sent,
                          AppColors.accentRose,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatChip(IconData icon, String label, double amount, Color iconColor) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
      decoration: BoxDecoration(
        color: iconColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: iconColor.withValues(alpha: 0.25),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(5),
                decoration: BoxDecoration(
                  color: iconColor.withValues(alpha: 0.15),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, color: iconColor, size: 12),
              ),
              const SizedBox(width: 7),
              Text(
                label,
                style: GoogleFonts.dmSans(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            '৳${amount.toStringAsFixed(0)}',
            style: GoogleFonts.dmSans(
              color: iconColor,
              fontSize: 16,
              fontWeight: FontWeight.w800,
              letterSpacing: -0.5,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQuickActionsRow(AppUser user, List<AppTransaction> transactions) {
    final actions = [
      (
        icon: Icons.send_rounded,
        label: 'Send',
        color: AppColors.primaryGreen,
        onTap: () => _showSendMoneySheet(user),
      ),
      (
        icon: Icons.call_received_rounded,
        label: 'Request',
        color: AppColors.accentCyan,
        onTap: () => _showRequestMoneySheet(user),
      ),
      (
        icon: Icons.qr_code_scanner_rounded,
        label: 'Scan',
        color: AppColors.accentViolet,
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const QrScannerScreen()),
        ),
      ),
      (
        icon: Icons.receipt_long_rounded,
        label: 'History',
        color: AppColors.accentAmber,
        onTap: () => _showTransactionHistorySheet(transactions),
      ),
    ];

    return Row(
      children: actions.map((action) {
        return Expanded(
          child: GestureDetector(
            onTap: action.onTap,
            child: Column(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: action.color.withValues(alpha: 0.10),
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: action.color.withValues(alpha: 0.30),
                      width: 1,
                    ),
                  ),
                  child: Icon(action.icon, color: action.color, size: 22),
                ),
                const SizedBox(height: 8),
                Text(
                  action.label,
                  style: GoogleFonts.dmSans(
                    color: AppColors.textSecondary,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }

  // ─── Send Money sheet ────────────────────────────────────────────────────────

  void _showSendMoneySheet(AppUser user) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PayActionSheet(
        title: 'Send Money',
        subtitle: 'Record money you sent to someone',
        accentColor: AppColors.primaryGreen,
        icon: Icons.send_rounded,
        buttonLabel: 'Confirm Send',
        onSubmit: (contactName, contactPhone, amount, note) async {
          final transRepo = ref.read(transactionRepositoryProvider);
          await transRepo.addTransaction(
            userId: user.uid,
            contactName: contactName,
            contactPhone: contactPhone,
            type: TransactionType.sent,
            amount: amount,
            note: note,
            status: TransactionStatus.completed,
          );
        },
      ),
    );
  }

  // ─── Request Money sheet ─────────────────────────────────────────────────────

  void _showRequestMoneySheet(AppUser user) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PayActionSheet(
        title: 'Request Money',
        subtitle: 'Record money owed to you',
        accentColor: AppColors.accentCyan,
        icon: Icons.call_received_rounded,
        buttonLabel: 'Confirm Request',
        onSubmit: (contactName, contactPhone, amount, note) async {
          final transRepo = ref.read(transactionRepositoryProvider);
          await transRepo.addTransaction(
            userId: user.uid,
            contactName: contactName,
            contactPhone: contactPhone,
            type: TransactionType.due,
            amount: amount,
            note: note,
            status: TransactionStatus.pending,
          );
        },
      ),
    );
  }

  // ─── Transaction History sheet ───────────────────────────────────────────────

  void _showTransactionHistorySheet(List<AppTransaction> transactions) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => DraggableScrollableSheet(
        initialChildSize: 0.85,
        minChildSize: 0.5,
        maxChildSize: 0.95,
        builder: (_, scrollController) => Container(
          decoration: BoxDecoration(
            color: AppColors.bgCard,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
            border: Border.all(color: AppColors.borderSubtle),
          ),
          child: Column(
            children: [
              // Handle
              Padding(
                padding: const EdgeInsets.only(top: 12, bottom: 8),
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: AppColors.borderSubtle,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              // Header
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                child: Row(
                  children: [
                    Text(
                      'All Transactions',
                      style: GoogleFonts.dmSans(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        color: AppColors.textPrimary,
                        letterSpacing: -0.3,
                      ),
                    ),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppColors.bgSurface,
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: AppColors.borderSubtle),
                      ),
                      child: Text(
                        '${transactions.length} total',
                        style: GoogleFonts.dmSans(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Divider(height: 1, color: AppColors.borderSubtle),
              // List
              Expanded(
                child: transactions.isEmpty
                    ? Center(
                        child: Text(
                          'No transactions yet',
                          style: GoogleFonts.dmSans(
                            color: AppColors.textMuted,
                            fontSize: 14,
                          ),
                        ),
                      )
                    : ListView.separated(
                        controller: scrollController,
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        itemCount: transactions.length,
                        separatorBuilder: (_, __) => Divider(
                          height: 1,
                          indent: 72,
                          color: AppColors.borderSubtle,
                        ),
                        itemBuilder: (_, i) {
                          final t = transactions[i];
                          final isPositive = t.type == TransactionType.received ||
                              t.type == TransactionType.due;
                          final amountColor = isPositive
                              ? AppColors.primaryGreen
                              : AppColors.accentRose;
                          return _buildTransactionTile(
                            t.contactName.isEmpty ? 'Unknown' : t.contactName,
                            DateFormat('MMM d, h:mm a').format(t.createdAt),
                            '${isPositive ? '+' : '-'} ৳${t.amount.toStringAsFixed(0)}',
                            amountColor,
                            isPositive,
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyTransactions() {
    return Container(
      padding: const EdgeInsets.all(40),
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: AppColors.bgCard,
        ),
      ),
      child: Center(
        child: Column(
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [AppColors.primaryGreen, AppColors.primaryGreenBright],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.primaryGreen.withValues(alpha: 0.30),
                    blurRadius: 20,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: const Icon(
                Icons.receipt_long_rounded,
                color: Colors.white,
                size: 28,
              ),
            ),
            const SizedBox(height: 18),
            Text(
              'No transactions yet',
              style: GoogleFonts.dmSans(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: AppColors.textPrimary,
                letterSpacing: -0.2,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Send money or request a payment\nto get started.',
              textAlign: TextAlign.center,
              style: GoogleFonts.dmSans(
                fontSize: 13,
                color: AppColors.textMuted,
                height: 1.6,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTransactionTile(
    String title,
    String date,
    String amount,
    Color amountColor,
    bool isPositive,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: [
          // Icon badge
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: amountColor.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: amountColor.withValues(alpha: 0.30),
              ),
            ),
            child: Icon(
              isPositive
                  ? Icons.arrow_downward_rounded
                  : Icons.arrow_upward_rounded,
              color: amountColor,
              size: 20,
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: GoogleFonts.dmSans(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w600,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  date,
                  style: GoogleFonts.dmSans(
                    color: AppColors.textMuted,
                    fontSize: 11,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ],
            ),
          ),
          Flexible(
            child: Text(
              amount,
              style: GoogleFonts.dmSans(
                color: amountColor,
                fontWeight: FontWeight.w800,
                fontSize: 15,
                letterSpacing: -0.3,
              ),
              textAlign: TextAlign.end,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  // ─── Profile avatar widget ──────────────────────────────────────────────────

  Widget _buildProfileAvatar(AppUser? user, {double size = 52}) {
    final photoUrl = user?.photoUrl ?? '';
    final initial = (user?.displayName.isNotEmpty == true)
        ? user!.displayName[0].toUpperCase()
        : 'U';
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.bgSurface,
        border: Border.all(
          color: AppColors.primaryGreen.withValues(alpha: 0.4),
          width: 2,
        ),
        image: photoUrl.isNotEmpty
            ? DecorationImage(
                image: CachedNetworkImageProvider(photoUrl),
                fit: BoxFit.cover,
              )
            : null,
      ),
      alignment: Alignment.center,
      child: photoUrl.isEmpty
          ? Text(
              initial,
              style: GoogleFonts.dmSans(
                color: AppColors.primaryGreen,
                fontSize: size * 0.36,
                fontWeight: FontWeight.w700,
              ),
            )
          : null,
    );
  }

  // ─── Settings Tab ───────────────────────────────────────────────────────────

  Widget _buildSettingsTab(AppUser? user) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 32),
      children: [
        // Profile card
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: AppColors.bgCard,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: AppColors.borderSubtle,
              width: 0.5,
            ),
            boxShadow: AppColors.cardGlow,
          ),
          child: Row(
            children: [
              _buildProfileAvatar(user, size: 62),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user?.displayName ?? 'PayChat User',
                      style: GoogleFonts.dmSans(
                        color: AppColors.textPrimary,
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Container(
                          width: 7,
                          height: 7,
                          decoration: BoxDecoration(
                            color: AppColors.accentEmerald,
                            shape: BoxShape.circle,
                            boxShadow: AppColors.greenGlow,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'Available',
                          style: GoogleFonts.dmSans(
                            color: AppColors.textSecondary,
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: AppColors.accentCyan.withValues(alpha: 0.1),
                  shape: BoxShape.circle,
                  border: Border.all(color: AppColors.accentCyan.withValues(alpha: 0.2)),
                ),
                child: IconButton(
                  icon: const Icon(Icons.qr_code_rounded, size: 18),
                  color: AppColors.accentCyan,
                  onPressed: () => _showComingSoon('QR Code'),
                  padding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 24),

        // Settings groups
        _buildSettingsGroup(
          items: [
            _SettingsGroupItem(icon: Icons.design_services_rounded, iconBg: AppColors.accentIndigo, title: 'My Services', subtitle: 'Manage services you offer', onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ManageServicesScreen()))),
            _SettingsGroupItem(icon: Icons.key_rounded, iconBg: AppColors.accentIndigo, title: 'Account', subtitle: 'Privacy, security, change number', onTap: _navigateToAccount),
            _SettingsGroupItem(icon: Icons.account_balance_wallet_rounded, iconBg: AppColors.accentGold, title: 'Payments', subtitle: 'History, bank accounts', onTap: _navigateToPayments),
          ],
        ),

        const SizedBox(height: 12),

        _buildSettingsGroup(
          items: [
            _SettingsGroupItem(icon: Icons.chat_rounded, iconBg: AppColors.accentCyan, title: 'Chats', subtitle: 'Theme, wallpapers, chat history', onTap: _navigateToChatsSettings),
            _SettingsGroupItem(icon: Icons.notifications_rounded, iconBg: AppColors.accentGold, title: 'Notifications', subtitle: 'Message, group & call tones', onTap: _navigateToNotifications),
          ],
        ),

        const SizedBox(height: 12),

        _buildSettingsGroup(
          items: [
            _SettingsGroupItem(icon: Icons.help_outline_rounded, iconBg: AppColors.accentIndigo, title: 'Help', subtitle: 'Help center, contact us, privacy policy', onTap: _navigateToHelp),
          ],
        ),
      ],
    );
  }

  Widget _buildSettingsGroup({required List<_SettingsGroupItem> items}) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppColors.borderSubtle, width: 0.5),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: Column(
          children: items.asMap().entries.map((entry) {
            final item = entry.value;
            final isLast = entry.key == items.length - 1;
            return Column(
              children: [
                _buildSettingsItem(item.icon, item.iconBg, item.title, item.subtitle, item.onTap),
                if (!isLast)
                  Divider(
                    height: 0.5,
                    indent: 66,
                    endIndent: 0,
                    color: AppColors.borderSubtle,
                    thickness: 0.5,
                  ),
              ],
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildSettingsItem(
    IconData icon, Color iconBg, String title, String? subtitle, VoidCallback onTap,
  ) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: iconBg.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: iconBg.withValues(alpha: 0.25),
                  width: 0.5,
                ),
              ),
              alignment: Alignment.center,
              child: Icon(icon, color: iconBg, size: 19),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: GoogleFonts.dmSans(
                      color: AppColors.textPrimary,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: GoogleFonts.dmSans(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                        fontWeight: FontWeight.w400,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            Icon(
              Icons.chevron_right_rounded,
              color: AppColors.textMuted,
              size: 18,
            ),
          ],
        ),
      ),
    );
  }

  void _navigateToAccount() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsDetailScreen(
          title: 'Account',
          options: [
            SettingsOption(
              title: 'Security notifications',
              icon: Icons.security,
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MockToggleScreen(
                title: 'Security Notifications',
                description: 'Show security notifications on this device when a contact\'s security code changes.',
                toggleLabel: 'Show security notifications',
              ))),
            ),
            SettingsOption(
              title: 'Two-step verification',
              icon: Icons.password,
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MockToggleScreen(
                title: 'Two-step verification',
                description: 'For extra security, turn on two-step verification, which will require a PIN when registering your phone number with PayChat again.',
                toggleLabel: 'Enable Two-step verification',
              ))),
            ),
            SettingsOption(
              title: 'Change number',
              icon: Icons.phone_android,
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ChangeNumberScreen())),
            ),
            SettingsOption(
              title: 'Delete my account',
              icon: Icons.delete_outline,
              onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DeleteAccountScreen())),
            ),
          ],
        ),
      ),
    );
  }

  void _navigateToPayments() {
    final balance = ref.read(sessionStateProvider).user?.walletBalance ?? 0.0;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsDetailScreen(
          title: 'Payments',
          headerWidget: Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: AppColors.gradientCyan,
              borderRadius: BorderRadius.circular(16),
              boxShadow: AppColors.cyanGlow,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'PayChat Wallet Balance',
                  style: GoogleFonts.dmSans(
                    fontSize: 13,
                    color: AppColors.bgPrimary.withValues(alpha: 0.7),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '৳ ${balance.toStringAsFixed(2)}',
                  style: GoogleFonts.dmSans(
                    fontSize: 32,
                    fontWeight: FontWeight.w700,
                    color: AppColors.bgPrimary,
                  ),
                ),
              ],
            ),
          ),
          options: [
            SettingsOption(title: 'Send new payment', icon: Icons.send, onTap: () => _showComingSoon('Send Payment')),
            SettingsOption(title: 'Payment history', icon: Icons.history, onTap: () => _showComingSoon('History')),
            SettingsOption(title: 'Add payment method', icon: Icons.add_card, onTap: () => _showComingSoon('Payment Method')),
          ],
        ),
      ),
    );
  }

  void _navigateToChatsSettings() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => const ChatSettingsScreen(),
      ),
    );
  }

  void _navigateToNotifications() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsDetailScreen(
          title: 'Notifications',
          options: [
            SettingsOption(title: 'Notification tone', subtitle: 'Default ringtone', icon: Icons.music_note, onTap: () => _showComingSoon('Tone')),
            SettingsOption(title: 'Vibrate', subtitle: 'Default', icon: Icons.vibration, onTap: () => _showComingSoon('Vibration')),
            SettingsOption(title: 'Reaction notifications', icon: Icons.face, onTap: () => _showComingSoon('Reactions')),
          ],
        ),
      ),
    );
  }

  void _navigateToHelp() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SettingsDetailScreen(
          title: 'Help',
          options: [
            SettingsOption(title: 'Help Center', icon: Icons.help, onTap: () => _showComingSoon('Help Center')),
            SettingsOption(title: 'Contact us', subtitle: 'Questions? Need help?', icon: Icons.group, onTap: () => _showComingSoon('Contact Us')),
            SettingsOption(title: 'App info', icon: Icons.info_outline, onTap: () => _showComingSoon('App Info')),
          ],
        ),
      ),
    );
  }

  void _showComingSoon(String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(Icons.rocket_launch_rounded, color: AppColors.accentGold, size: 16),
            const SizedBox(width: 10),
            Text(
              '$feature coming soon!',
              style: GoogleFonts.dmSans(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(
            color: AppColors.accentGold.withValues(alpha: 0.2),
            width: 0.5,
          ),
        ),
        backgroundColor: AppColors.bgCard,
        margin: const EdgeInsets.all(16),
      ),
    );
  }
}

// ─── Internal helper data class ────────────────────────────────────────────────

class _SettingsGroupItem {
  final IconData icon;
  final Color iconBg;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  const _SettingsGroupItem({
    required this.icon,
    required this.iconBg,
    required this.title,
    this.subtitle,
    required this.onTap,
  });
}

// ─── Reusable Send / Request bottom sheet ────────────────────────────────────

class _PayActionSheet extends ConsumerStatefulWidget {
  final String title;
  final String subtitle;
  final Color accentColor;
  final IconData icon;
  final String buttonLabel;
  final Future<void> Function(
    String contactName,
    String contactPhone,
    double amount,
    String note,
  ) onSubmit;

  const _PayActionSheet({
    required this.title,
    required this.subtitle,
    required this.accentColor,
    required this.icon,
    required this.buttonLabel,
    required this.onSubmit,
  });

  @override
  ConsumerState<_PayActionSheet> createState() => _PayActionSheetState();
}

class _PayActionSheetState extends ConsumerState<_PayActionSheet> {
  final _amountCtrl = TextEditingController();
  final _nameCtrl   = TextEditingController();
  final _phoneCtrl  = TextEditingController();
  final _noteCtrl   = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _amountCtrl.dispose();
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final amount = double.tryParse(_amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Enter a valid amount');
      return;
    }
    if (_nameCtrl.text.trim().isEmpty) {
      setState(() => _error = 'Enter a contact name');
      return;
    }
    setState(() { _loading = true; _error = null; });
    try {
      await widget.onSubmit(
        _nameCtrl.text.trim(),
        _phoneCtrl.text.trim(),
        amount,
        _noteCtrl.text.trim(),
      );
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) setState(() { _loading = false; _error = e.toString(); });
    }
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(themeControllerProvider);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          border: Border.all(color: AppColors.borderSubtle),
        ),
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Handle
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

            // Title row
            Row(
              children: [
                Container(
                  width: 40, height: 40,
                  decoration: BoxDecoration(
                    color: widget.accentColor.withValues(alpha: 0.12),
                    shape: BoxShape.circle,
                    border: Border.all(color: widget.accentColor.withValues(alpha: 0.30)),
                  ),
                  child: Icon(widget.icon, color: widget.accentColor, size: 18),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.title,
                        style: GoogleFonts.dmSans(
                            fontSize: 17,
                            fontWeight: FontWeight.w700,
                            color: AppColors.textPrimary)),
                    Text(widget.subtitle,
                        style: GoogleFonts.dmSans(
                            fontSize: 12, color: AppColors.textMuted)),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Amount
            Container(
              decoration: BoxDecoration(
                color: AppColors.bgSurface,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.borderSubtle),
              ),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: TextField(
                controller: _amountCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                style: GoogleFonts.dmSans(
                    fontSize: 32, fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary),
                textAlign: TextAlign.center,
                decoration: InputDecoration(
                  prefixText: '৳  ',
                  prefixStyle: GoogleFonts.dmSans(
                      fontSize: 26, fontWeight: FontWeight.w600,
                      color: AppColors.textSecondary),
                  hintText: '0.00',
                  hintStyle: GoogleFonts.dmSans(
                      fontSize: 32, fontWeight: FontWeight.w300,
                      color: AppColors.textMuted),
                  border: InputBorder.none,
                  filled: false,
                ),
                onChanged: (_) => setState(() => _error = null),
              ),
            ),
            const SizedBox(height: 12),

            // Contact name
            _field(_nameCtrl, 'Contact name', Icons.person_outline_rounded),
            const SizedBox(height: 10),

            // Phone (optional)
            _field(_phoneCtrl, 'Phone number (optional)',
                Icons.phone_outlined,
                keyboard: TextInputType.phone),
            const SizedBox(height: 10),

            // Note
            _field(_noteCtrl, 'Note (optional)', Icons.notes_rounded),
            const SizedBox(height: 20),

            // Error
            if (_error != null) ...[
              Text(_error!,
                  style: GoogleFonts.dmSans(
                      color: AppColors.errorColor, fontSize: 13)),
              const SizedBox(height: 10),
            ],

            // Submit button
            SizedBox(
              height: 50,
              child: ElevatedButton(
                onPressed: _loading ? null : _submit,
                style: ElevatedButton.styleFrom(
                  backgroundColor: widget.accentColor,
                  foregroundColor: Colors.white,
                  elevation: 0,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14)),
                ),
                child: _loading
                    ? const SizedBox(
                        width: 20, height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : Text(widget.buttonLabel,
                        style: GoogleFonts.dmSans(
                            fontSize: 15, fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _field(TextEditingController ctrl, String hint, IconData icon,
      {TextInputType keyboard = TextInputType.text}) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgSurface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.borderSubtle),
      ),
      child: TextField(
        controller: ctrl,
        keyboardType: keyboard,
        style: GoogleFonts.dmSans(color: AppColors.textPrimary, fontSize: 14),
        decoration: InputDecoration(
          hintText: hint,
          hintStyle: GoogleFonts.dmSans(color: AppColors.textMuted, fontSize: 14),
          prefixIcon: Icon(icon, color: AppColors.textSecondary, size: 18),
          border: InputBorder.none,
          filled: false,
          contentPadding: const EdgeInsets.symmetric(vertical: 14),
        ),
        onChanged: (_) => setState(() => _error = null),
      ),
    );
  }
}

