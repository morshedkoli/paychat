import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:gap/gap.dart';
import 'package:phosphor_flutter/phosphor_flutter.dart';
import '../../core/providers/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../widgets/app_background.dart';
import '../../widgets/buttons.dart';
import '../../core/models/chat.dart';
import '../chat/chat_screen.dart';

class NewChatScreen extends ConsumerStatefulWidget {
  const NewChatScreen({super.key});

  @override
  ConsumerState<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends ConsumerState<NewChatScreen> {
  List<Contact> _contacts = [];
  List<Contact> _filteredContacts = [];
  bool _isLoading = true;
  bool _permissionDenied = false;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _loadContacts();
  }

  Future<void> _loadContacts() async {
    // flutter_contacts doesn't support web platform
    if (kIsWeb) {
      setState(() {
        _isLoading = false;
        _contacts = [];
        _filteredContacts = [];
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _permissionDenied = false;
    });

    try {
      // Request permission
      if (await FlutterContacts.requestPermission()) {
        // Get all contacts with phone numbers
        final contacts = await FlutterContacts.getContacts(
          withProperties: true,
          withPhoto: true,
        );

        // Filter contacts that have phone numbers
        final contactsWithPhones =
            contacts.where((c) => c.phones.isNotEmpty).toList();

        // Sort alphabetically
        contactsWithPhones.sort((a, b) =>
            a.displayName.toLowerCase().compareTo(b.displayName.toLowerCase()));

        setState(() {
          _contacts = contactsWithPhones;
          _filteredContacts = contactsWithPhones;
          _isLoading = false;
        });
      } else {
        setState(() {
          _permissionDenied = true;
          _isLoading = false;
        });
      }
    } catch (e) {
      // Handle MissingPluginException for unsupported platforms
      setState(() {
        _isLoading = false;
        _contacts = [];
        _filteredContacts = [];
      });
      debugPrint('Contact loading not supported: $e');
    }
  }

  void _filterContacts(String query) {
    setState(() {
      _searchQuery = query;
      if (query.isEmpty) {
        _filteredContacts = _contacts;
      } else {
        _filteredContacts = _contacts.where((contact) {
          final nameLower = contact.displayName.toLowerCase();
          final queryLower = query.toLowerCase();

          // Also search in phone numbers
          final phoneMatch = contact.phones.any(
            (phone) => phone.number.contains(query),
          );

          return nameLower.contains(queryLower) || phoneMatch;
        }).toList();
      }
    });
  }

  void _showAddContactDialog() {
    final nameController = TextEditingController();
    final phoneController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(
          children: [
            Icon(PhosphorIcons.userPlus(), color: AppColors.primary),
            const Gap(12),
            const Text('Add New Contact'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: InputDecoration(
                labelText: 'Name',
                hintText: 'Enter contact name',
                prefixIcon: Icon(PhosphorIcons.user()),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              textCapitalization: TextCapitalization.words,
            ),
            const Gap(16),
            TextField(
              controller: phoneController,
              decoration: InputDecoration(
                labelText: 'Phone Number',
                hintText: '01XXXXXXXXX',
                prefixIcon: Icon(PhosphorIcons.phone()),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              keyboardType: TextInputType.phone,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              final name = nameController.text.trim();
              final phone = phoneController.text.trim();

              if (name.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a name')),
                );
                return;
              }
              if (phone.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a phone number')),
                );
                return;
              }

              Navigator.pop(context);
              _onManualContactAdded(name, phone);
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primary,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child:
                const Text('Start Chat', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Future<void> _onManualContactAdded(String name, String phone) async {
    // Show loading
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => const Center(
        child: CircularProgressIndicator(),
      ),
    );

    try {
      final currentUser = ref.read(currentUserProvider).value;
      if (currentUser == null) {
        Navigator.pop(context); // Close loading
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please sign in first')),
        );
        return;
      }

      final firestoreService = ref.read(firestoreServiceProvider);

      // Create chat with phone number
      final chatId = await firestoreService.createChatByPhone(
        currentUserId: currentUser.id,
        phoneNumber: phone,
        contactName: name,
      );

      // Fetch the full chat object to navigate
      final chat = await firestoreService.getChat(chatId, currentUser.id);

      Navigator.pop(context); // Close loading

      if (chat != null) {
        // Refresh chats list
        ref.invalidate(chatsProvider);

        // Close the "New Chat" screen first
        if (mounted) Navigator.pop(context);

        // Navigate to Chat Screen
        if (mounted) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => ChatScreen(chat: chat),
            ),
          );
        }
      } else {
        if (mounted) Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
              content: Text('Chat created but failed to open immediately.')),
        );
      }
    } catch (e) {
      Navigator.pop(context); // Close loading
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error creating chat: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: AppBackground(
        showSparkles: true,
        child: Scaffold(
          backgroundColor: Colors.transparent,
          appBar: AppBar(
            backgroundColor: Colors.transparent,
            elevation: 0,
            leading: IconButton(
              icon: Icon(PhosphorIcons.arrowLeft()),
              onPressed: () => Navigator.pop(context),
            ),
            title: const Text("New Chat"),
            actions: [
              // Plus button in app bar
              IconButton(
                icon: Icon(PhosphorIcons.plus(), color: AppColors.primary),
                onPressed: _showAddContactDialog,
                tooltip: 'Add New Contact',
              ),
            ],
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(60),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: _SearchInput(
                  hint: "Search name or number",
                  onChanged: _filterContacts,
                ),
              ),
            ),
          ),
          floatingActionButton: FloatingActionButton.extended(
            onPressed: _showAddContactDialog,
            backgroundColor: AppColors.primary,
            icon: Icon(PhosphorIcons.userPlus(), color: Colors.white),
            label: const Text(
              'Add Contact',
              style:
                  TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
            ),
          ),
          body: _buildBody(),
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            Gap(16),
            Text(
              'Loading contacts...',
              style: TextStyle(color: AppColors.textSecondary),
            ),
          ],
        ),
      );
    }

    if (_permissionDenied) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                PhosphorIcons.addressBook(),
                size: 64,
                color: AppColors.textSecondary.withOpacity(0.5),
              ),
              const Gap(16),
              const Text(
                'Contact Permission Required',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
                textAlign: TextAlign.center,
              ),
              const Gap(8),
              Text(
                'Please grant access to your contacts to start chatting with friends.',
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.textSecondary.withOpacity(0.8),
                ),
                textAlign: TextAlign.center,
              ),
              const Gap(24),
              PrimaryButton(
                label: 'Grant Permission',
                icon: PhosphorIcons.shieldCheck(),
                onTap: _loadContacts,
              ),
            ],
          ),
        ),
      );
    }

    if (_filteredContacts.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              PhosphorIcons.users(),
              size: 64,
              color: AppColors.textSecondary.withOpacity(0.5),
            ),
            const Gap(16),
            Text(
              _searchQuery.isEmpty
                  ? 'No contacts found'
                  : 'No matching contacts',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: AppColors.textSecondary.withOpacity(0.7),
              ),
            ),
            const Gap(8),
            Text(
              _searchQuery.isEmpty
                  ? 'Add some contacts to your device first'
                  : 'Try a different search term',
              style: TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary.withOpacity(0.6),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        // Add Contact Card
        _AddContactCard(onTap: _showAddContactDialog)
            .animate()
            .fadeIn(duration: 200.ms),

        // Contact count
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            children: [
              Text(
                '${_filteredContacts.length} contact${_filteredContacts.length != 1 ? 's' : ''} from device',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),

        // Contact List
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: _filteredContacts.length,
            itemBuilder: (context, index) {
              final contact = _filteredContacts[index];
              return _ContactCard(
                contact: contact,
                onTap: () => _onContactSelected(contact),
              ).animate().fadeIn(
                    delay: Duration(milliseconds: 30 * (index % 20)),
                    duration: 200.ms,
                  );
            },
          ),
        ),

        // Bottom CTA
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppColors.surface.withOpacity(0.95),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.05),
                blurRadius: 10,
                offset: const Offset(0, -4),
              ),
            ],
          ),
          child: SafeArea(
            child: PrimaryButton(
              label: "Invite via Share",
              icon: PhosphorIcons.shareFat(),
              onTap: () {
                // Share invite link
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Share feature coming soon!')),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  void _onContactSelected(Contact contact) {
    // Get the first phone number
    final phone = contact.phones.isNotEmpty ? contact.phones.first.number : '';

    // TODO: Check if user is registered on PayChat, then start chat
    // For now, show a dialog
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Start Chat with ${contact.displayName}?'),
        content: Text('Phone: $phone'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              Navigator.pop(context); // Go back to home
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                    content: Text(
                        'Chat with ${contact.displayName} - Coming soon!')),
              );
            },
            child: const Text('Start Chat'),
          ),
        ],
      ),
    );
  }
}

class _SearchInput extends StatelessWidget {
  final String hint;
  final ValueChanged<String>? onChanged;

  const _SearchInput({
    required this.hint,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.9),
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: TextField(
        onChanged: onChanged,
        decoration: InputDecoration(
          hintText: hint,
          hintStyle: TextStyle(color: AppColors.textSecondary.withOpacity(0.6)),
          prefixIcon: Icon(
            PhosphorIcons.magnifyingGlass(),
            color: AppColors.textSecondary,
          ),
          border: InputBorder.none,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        ),
      ),
    );
  }
}

class _ContactCard extends StatelessWidget {
  final Contact contact;
  final VoidCallback onTap;

  const _ContactCard({
    required this.contact,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final phone = contact.phones.isNotEmpty ? contact.phones.first.number : '';

    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.surface.withOpacity(0.95),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.02),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            // Avatar
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: _getAvatarColor(contact.displayName),
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: contact.photo != null
                  ? ClipOval(
                      child: Image.memory(
                        contact.photo!,
                        width: 48,
                        height: 48,
                        fit: BoxFit.cover,
                      ),
                    )
                  : Text(
                      contact.displayName.isNotEmpty
                          ? contact.displayName[0].toUpperCase()
                          : '?',
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
            ),
            const Gap(12),

            // Name and Phone
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    contact.displayName,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (phone.isNotEmpty)
                    Text(
                      phone,
                      style: TextStyle(
                        fontSize: 13,
                        color: AppColors.textSecondary.withOpacity(0.8),
                      ),
                    ),
                ],
              ),
            ),

            // Arrow
            Icon(
              PhosphorIcons.caretRight(),
              size: 20,
              color: AppColors.textSecondary.withOpacity(0.5),
            ),
          ],
        ),
      ),
    );
  }

  Color _getAvatarColor(String name) {
    final colors = [
      AppColors.primary,
      AppColors.accent,
      AppColors.success,
      const Color(0xFF9B59B6),
      const Color(0xFFE67E22),
      const Color(0xFF3498DB),
    ];

    if (name.isEmpty) return colors[0];
    return colors[name.codeUnitAt(0) % colors.length];
  }
}

class _AddContactCard extends StatelessWidget {
  final VoidCallback onTap;

  const _AddContactCard({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              AppColors.primary.withOpacity(0.1),
              AppColors.accent.withOpacity(0.1),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: AppColors.primary.withOpacity(0.2),
            width: 1.5,
          ),
        ),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                PhosphorIcons.userPlus(),
                color: Colors.white,
                size: 24,
              ),
            ),
            const Gap(16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Add New Contact',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  const Gap(2),
                  Text(
                    'Enter name and phone number manually',
                    style: TextStyle(
                      fontSize: 13,
                      color: AppColors.textSecondary.withOpacity(0.8),
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              PhosphorIcons.plus(),
              color: AppColors.primary,
              size: 24,
            ),
          ],
        ),
      ),
    );
  }
}
