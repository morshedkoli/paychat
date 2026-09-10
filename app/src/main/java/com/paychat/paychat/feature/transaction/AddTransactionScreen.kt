package com.paychat.paychat.feature.transaction

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.feature.lock.canLock
import com.paychat.paychat.feature.lock.promptBiometric
import com.paychat.paychat.ui.components.PrimaryButton
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.AmountLargeStyle
import com.paychat.paychat.ui.theme.PayChatTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val ledger = PayChatTheme.ledger
    var showDatePicker by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::onPhotoPicked) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDueDateChange(pickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onDueDateChange(null)
                    showDatePicker = false
                }) { Text("Clear") }
            },
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record money") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.direction == TxnDirection.SENT,
                    onClick = { viewModel.onDirectionChange(TxnDirection.SENT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("I gave money") }
                SegmentedButton(
                    selected = state.direction == TxnDirection.RECEIVED,
                    onClick = { viewModel.onDirectionChange(TxnDirection.RECEIVED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("I got money") }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                prefix = { Text(Money.SYMBOL) },
                textStyle = AmountLargeStyle,
                singleLine = true,
                isError = state.amountError != null,
                supportingText = state.amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )

            Text(
                text = state.consequence,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.direction == TxnDirection.SENT && !state.isLocalThread) {
                    ledger.pending
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(TxnCategory.entries) { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { viewModel.onCategoryChange(cat) },
                        label = { Text(cat.displayName) },
                    )
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What was it for? (optional)") },
                maxLines = 3,
            )

            OutlinedTextField(
                value = state.trxId,
                onValueChange = viewModel::onTrxIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("bKash / Nagad TrxID (optional)") },
                placeholder = { Text("e.g. 9J2K5L8M") },
                singleLine = true,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Text(
                        text = state.dueDate?.let { "Due ${Timestamps.daySeparator(it)}" }
                            ?: "Add a due date",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (state.photoLocalPath == null) {
                TextButton(
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Text("Attach a receipt", Modifier.padding(start = 8.dp))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsyncImage(
                        model = File(state.photoLocalPath!!),
                        contentDescription = "Receipt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(140.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    IconButton(onClick = viewModel::removePhoto) {
                        Icon(Icons.Default.Close, contentDescription = "Remove the receipt")
                    }
                }
            }

            val context = LocalContext.current
            val activity = context as? FragmentActivity

            val onRecordClick = {
                if (state.isHighValue && activity != null && canLock(context)) {
                    promptBiometric(
                        activity = activity,
                        title = "Authorize Transaction",
                        subtitle = "Confirm recording transaction of ${state.amount?.format() ?: ""}",
                        onUnlocked = viewModel::save,
                    )
                } else {
                    viewModel.save()
                }
            }

            PrimaryButton(
                text = "Record",
                onClick = onRecordClick,
                enabled = state.canSave,
                loading = state.saving,
            )
        }
    }
}
