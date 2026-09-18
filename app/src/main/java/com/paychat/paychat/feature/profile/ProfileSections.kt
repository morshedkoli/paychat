package com.paychat.paychat.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import com.paychat.paychat.data.settings.LockTimeout
import com.paychat.paychat.ui.components.Avatar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paychat.paychat.data.settings.ThemeChoice
import com.paychat.paychat.feature.lock.canLock
import com.paychat.paychat.ui.components.PayChatSpinner

/**
 * The setting rows the profile tab is built from, each group on its own card.
 */

/**
 * 38dp rounded badge icon for setting rows.
 */
@Composable
internal fun SettingBadgeIcon(
    icon: ImageVector,
    badgeColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(badgeColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One group of settings displayed inside an elevated, rounded card.
 */
@Composable
private fun ProfileCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

/** Rows inside a card must not paint their own background over it. */
@Composable
private fun cardRowColours() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** App lock, with the reason it is unavailable when the phone has no screen lock, timeout selector, and default privacy mode. */
@Composable
internal fun AccountSection(
    state: ProfileUiState,
    onToggleAppLock: (Boolean) -> Unit,
    onSelectLockTimeout: (LockTimeout) -> Unit,
    onToggleAlwaysHideBalances: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    // A phone with no PIN, pattern or fingerprint cannot ask for one, so the
    // switch says why instead of producing a lock nobody can get past.
    val lockAvailable = remember { canLock(context) }

    ProfileCard("Security & Privacy") {
        ListItem(
            colors = cardRowColours(),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.Lock,
                    badgeColor = Color(0xFF00A884).copy(alpha = 0.15f),
                    iconColor = Color(0xFF00A884),
                )
            },
            headlineContent = {
                Text(
                    "App lock",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            },
            supportingContent = {
                Text(
                    if (lockAvailable) {
                        "Ask for your screen lock when PayChat returns to the front."
                    } else {
                        "Set a screen lock on this phone first."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Switch(
                    checked = state.appLockEnabled,
                    onCheckedChange = onToggleAppLock,
                    enabled = lockAvailable,
                )
            },
        )

        if (state.appLockEnabled && lockAvailable) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 68.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                Text(
                    "Automatically lock after",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LockTimeout.entries.forEach { timeout ->
                        FilterChip(
                            selected = state.lockTimeout == timeout,
                            onClick = { onSelectLockTimeout(timeout) },
                            label = {
                                Text(
                                    timeout.label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (state.lockTimeout == timeout) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )

        ListItem(
            colors = cardRowColours(),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.VisibilityOff,
                    badgeColor = Color(0xFF00897B).copy(alpha = 0.15f),
                    iconColor = Color(0xFF00897B),
                )
            },
            headlineContent = {
                Text(
                    "Default Privacy Mode",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            },
            supportingContent = {
                Text(
                    "Always mask balance numbers upon opening PayChat.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Switch(
                    checked = state.alwaysHideBalancesOnLaunch,
                    onCheckedChange = onToggleAlwaysHideBalances,
                )
            },
        )
    }
}

/** The theme the user picked; nothing here forces a theme. */
@Composable
internal fun AppearanceSection(state: ProfileUiState, onChooseTheme: (ThemeChoice) -> Unit) {
    ProfileCard("Appearance") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingBadgeIcon(
                    icon = Icons.Default.Palette,
                    badgeColor = Color(0xFF7C4DFF).copy(alpha = 0.15f),
                    iconColor = Color(0xFF7C4DFF),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Choose your preferred color theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = choice == state.theme,
                        onClick = { onChooseTheme(choice) },
                        label = {
                            Text(
                                choice.label(),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (choice == state.theme) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Taking the account's records out of the app and managing storage cache. */
@Composable
internal fun DataSection(
    state: ProfileUiState,
    onExportStatements: () -> Unit,
    onExportData: () -> Unit,
    onClearCache: () -> Unit,
) {
    ProfileCard("Data & Storage") {
        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.exporting,
                onClick = onExportStatements,
            ),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.Description,
                    badgeColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                    iconColor = Color(0xFF2196F3),
                )
            },
            headlineContent = {
                Text(
                    "Export all statements",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            },
            supportingContent = {
                Text(
                    "One PDF covering every conversation with a closing balance.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                if (state.exporting) {
                    PayChatSpinner(size = 20.dp, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )

        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.exporting,
                onClick = onExportData,
            ),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.FileDownload,
                    badgeColor = Color(0xFF00BCD4).copy(alpha = 0.15f),
                    iconColor = Color(0xFF00BCD4),
                )
            },
            headlineContent = {
                Text(
                    "Export my data",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            },
            supportingContent = {
                Text(
                    "Every conversation, message and transaction, as JSON.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )

        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.clearingCache,
                onClick = onClearCache,
            ),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.DeleteSweep,
                    badgeColor = Color(0xFF8E24AA).copy(alpha = 0.15f),
                    iconColor = Color(0xFF8E24AA),
                )
            },
            headlineContent = {
                Text(
                    "Clear temporary cache",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
            },
            supportingContent = {
                Text(
                    "${state.cacheSizeFormatted} • Free up space from cached images & files",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                if (state.clearingCache) {
                    PayChatSpinner(size = 20.dp, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
    }
}

/**
 * Leaving, and leaving for good. The delete row carries [ProfileUiState.deleting]
 * so it stays inert while the account is being removed.
 *
 * Its own card, so a scroll cannot land a destructive tap next to a harmless one.
 */
@Composable
internal fun DangerSection(
    state: ProfileUiState,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    ProfileCard("Account") {
        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(onClick = onSignOut),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    badgeColor = Color(0xFFFF9800).copy(alpha = 0.15f),
                    iconColor = Color(0xFFFF9800),
                )
            },
            headlineContent = {
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFE65100),
                )
            },
            supportingContent = {
                Text(
                    "This device stops receiving notifications for the account.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )

        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.deleting,
                onClick = onDeleteAccount,
            ),
            leadingContent = {
                SettingBadgeIcon(
                    icon = Icons.Default.DeleteOutline,
                    badgeColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    iconColor = MaterialTheme.colorScheme.error,
                )
            },
            headlineContent = {
                Text(
                    "Delete my account",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            supportingContent = {
                Text(
                    "Permanent. Your profile and your number are released.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                if (state.deleting) {
                    PayChatSpinner(size = 20.dp, strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                } else {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
    }
}

@Composable
internal fun ThemeRow(current: ThemeChoice, onChoose: (ThemeChoice) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeChoice.entries.forEach { choice ->
            FilterChip(
                selected = choice == current,
                onClick = { onChoose(choice) },
                label = { Text(choice.label()) },
            )
        }
    }
}

internal fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.SYSTEM -> "System"
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The delete-account confirmation, unchanged from the settings screen. */
@Composable
internal fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Text(
                "Your profile is removed and your phone number is released, so " +
                    "it can be registered again. Transactions in a shared " +
                    "conversation stay: each one is a record between two people, " +
                    "and the other side's ledger has to keep adding up. This " +
                    "cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep my account") }
        },
    )
}

/** The sign-out confirmation, unchanged from the settings screen. */
@Composable
internal fun SignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = {
            Text(
                "Your chats and balances stay on the server. You will need your " +
                    "password to sign back in."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Stay") }
        },
    )
}

/**
 * Modern personal PayChat QR code dialog for sharing contact and payment link in person.
 */
@Composable
internal fun PersonalQrDialog(
    name: String,
    phone: String,
    photoUrl: String?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val qrBitmap = remember(phone, name) {
        val uri = QrCodeHelper.createPayChatUri(phone, name)
        QrCodeHelper.generateQrBitmap(uri, size = 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "My PayChat QR",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share QR code")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(
                    name = name,
                    key = phone,
                    photoUrl = photoUrl,
                    size = 56.dp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = name.ifBlank { "PayChat User" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // The QR Code Container
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "PayChat QR Code",
                                modifier = Modifier.size(190.dp),
                            )
                        } else {
                            PayChatSpinner(size = 48.dp, strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Scan with PayChat to pay or chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onCopyLink,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Payment Link")
                }
            }
        },
    )
}

/** Confirmation before clearing temporary files and image cache. */
@Composable
internal fun ClearCacheDialog(
    cacheSize: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear temporary cache?") },
        text = {
            Text("This will free up $cacheSize of space by removing cached photos and temporary files. Your messages, ledger balances, and account settings remain completely safe.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
