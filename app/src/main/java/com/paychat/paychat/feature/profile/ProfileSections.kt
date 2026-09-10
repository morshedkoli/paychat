package com.paychat.paychat.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paychat.paychat.data.settings.ThemeChoice
import com.paychat.paychat.feature.lock.canLock

/**
 * The setting rows the profile tab is built from, each group on its own card.
 */

/**
 * One group of settings. The card carries the group's name, so the rows inside
 * need no heading of their own, and the rows sit on the card rather than on the
 * page — hence the transparent [ListItem] containers.
 */
@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
        )
        content()
    }
}

/** Rows inside a card must not paint their own background over it. */
@Composable
private fun cardRowColours() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** App lock, with the reason it is unavailable when the phone has no screen lock. */
@Composable
internal fun AccountSection(state: ProfileUiState, onToggleAppLock: (Boolean) -> Unit) {
    val context = LocalContext.current

    // A phone with no PIN, pattern or fingerprint cannot ask for one, so the
    // switch says why instead of producing a lock nobody can get past.
    val lockAvailable = remember { canLock(context) }

    ProfileCard("Privacy") {
        ListItem(
            colors = cardRowColours(),
            headlineContent = { Text("App lock") },
            supportingContent = {
                Text(
                    if (lockAvailable) {
                        "Ask for your screen lock when PayChat comes back to the front."
                    } else {
                        "Set a screen lock on this phone first."
                    }
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
    }
}

/** The theme the user picked; nothing here forces a theme. */
@Composable
internal fun AppearanceSection(state: ProfileUiState, onChooseTheme: (ThemeChoice) -> Unit) {
    ProfileCard("Appearance") {
        ThemeRow(current = state.theme, onChoose = onChooseTheme)
    }
}

/** Taking the account's records out of the app. */
@Composable
internal fun DataSection(
    state: ProfileUiState,
    onExportStatements: () -> Unit,
    onExportData: () -> Unit,
) {
    ProfileCard("Your money") {
        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.exporting,
                onClick = onExportStatements,
            ),
            headlineContent = { Text("Export all statements") },
            supportingContent = {
                Text("One PDF covering every conversation with a closing balance.")
            },
            trailingContent = {
                if (state.exporting) CircularProgressIndicator(Modifier.size(20.dp))
            },
        )

        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.exporting,
                onClick = onExportData,
            ),
            headlineContent = { Text("Export my data") },
            supportingContent = {
                Text("Every conversation, message and transaction, as JSON.")
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
            headlineContent = {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            },
            supportingContent = {
                Text("This device stops receiving notifications for the account.")
            },
        )

        ListItem(
            colors = cardRowColours(),
            modifier = Modifier.clickable(
                enabled = !state.deleting,
                onClick = onDeleteAccount,
            ),
            headlineContent = {
                Text("Delete my account", color = MaterialTheme.colorScheme.error)
            },
            supportingContent = {
                Text("Permanent. Your profile and your number are released.")
            },
            trailingContent = {
                if (state.deleting) CircularProgressIndicator(Modifier.size(20.dp))
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
