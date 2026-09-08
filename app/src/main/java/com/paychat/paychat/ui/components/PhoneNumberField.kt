package com.paychat.paychat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paychat.paychat.core.phone.Countries
import com.paychat.paychat.core.phone.Country

/**
 * A phone number as a country and a national number.
 *
 * The country is chosen rather than inferred. A number typed without one has
 * to be assumed to belong to somewhere, and assuming wrongly either fails to
 * find an account that exists or points at a different number entirely - and
 * the number is the identity in PayChat, so that is not a cosmetic mistake.
 *
 * @param region ISO 3166-1 alpha-2 code of the selected country
 * @param national the number as typed, without the country code
 */
@Composable
fun PhoneNumberField(
    region: String,
    national: String,
    onRegionChange: (String) -> Unit,
    onNationalChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Phone number",
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    var picking by remember { mutableStateOf(false) }
    val country = Countries.byRegion(region) ?: Countries.default

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DialCodeChip(
            country = country,
            enabled = enabled,
            isError = error != null,
            onClick = { picking = true },
        )

        OutlinedTextField(
            value = national,
            onValueChange = { onNationalChange(it.filter(Char::isDigit)) },
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            placeholder = { Text("1712345678") },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            shape = FieldShape,
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = imeAction,
            ),
        )
    }

    if (picking) {
        CountryPickerSheet(
            onDismiss = { picking = false },
            onPick = { picked ->
                onRegionChange(picked.region)
                picking = false
            },
        )
    }
}

/**
 * The country code, drawn to match the field beside it rather than as a
 * button, so the pair reads as one control.
 */
@Composable
private fun DialCodeChip(
    country: Country,
    enabled: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            // The same outline as the field beside it: without one the code
            // reads as text floating on the card rather than half of a control.
            .border(1.dp, border, FieldShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(country.flag, style = MaterialTheme.typography.titleMedium)
        Text(
            "+${country.dialCode}",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = "Change country",
            tint = border,
        )
    }
}

/** The searchable country list behind the dial code chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryPickerSheet(
    onDismiss: () -> Unit,
    onPick: (Country) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { Countries.matching(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Choose a country",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                singleLine = true,
                shape = FieldShape,
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            if (matches.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No country matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(matches, key = { it.region }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(candidate) }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(candidate.flag, style = MaterialTheme.typography.titleMedium)
                            Text(
                                candidate.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "+${candidate.dialCode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
