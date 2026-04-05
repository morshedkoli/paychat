package com.paychat.paychat.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.paychat.paychat.PayChatViewModel
import com.paychat.paychat.model.AuthEntryStep
import com.paychat.paychat.model.AppMessage
import com.paychat.paychat.model.AppTransaction
import com.paychat.paychat.model.AppUser
import com.paychat.paychat.model.ChatThread
import com.paychat.paychat.model.MessageContentType
import com.paychat.paychat.model.SessionStatus
import com.paychat.paychat.model.TransactionType
import com.paychat.paychat.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object Routes {
    const val chats = "chats"
    const val profile = "profile"
    const val transactions = "transactions"
    const val editProfile = "edit_profile"
    const val addChatMember = "add_chat_member"
    const val conversation = "conversation/{threadId}"
    const val transactionDetail = "transaction/{transactionId}"

    fun conversation(threadId: String) = "conversation/$threadId"

    fun transactionDetail(transactionId: String) = "transaction/$transactionId"
}

private enum class ChatHomeTab {
    CHATS,
    PENDING,
    RECEIVABLES,
}

private data class DeviceContact(
    val name: String,
    val phone: String,
)

@Composable
fun PayChatApp(viewModel: PayChatViewModel) {
    PayChatTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            var hasCompletedOnboarding by remember {
                mutableStateOf(context.hasCompletedOnboarding())
            }
            val session by viewModel.session.collectAsStateWithLifecycle()

            if (!hasCompletedOnboarding) {
                OnboardingScreen(
                    onFinish = {
                        context.setOnboardingCompleted(true)
                        hasCompletedOnboarding = true
                    },
                )
            } else {
                when (session.status) {
                    SessionStatus.LOADING -> SplashScreen(
                        subtitle = session.message ?: "Securing your records and restoring your session.",
                        onRetry = viewModel::retry,
                    )

                    SessionStatus.UNAUTHENTICATED -> LoginScreen(viewModel = viewModel)
                    SessionStatus.PROFILE_INCOMPLETE -> ProfileEditorScreen(
                        viewModel = viewModel,
                        user = session.user,
                        title = "Complete your profile",
                        subtitle = "Your phone number is verified. Add your name to continue, and optionally include your address and profile photo.",
                        submitLabel = "Save and Continue",
                        onDone = {},
                        onBack = null,
                        nameLabel = "Name",
                        showEmailField = false,
                        showProfileMeta = false,
                    )

                    SessionStatus.BLOCKED -> BlockedAccountScreen(
                        user = session.user,
                        onLogout = viewModel::signOut,
                    )

                    SessionStatus.AUTHENTICATED -> AuthenticatedApp(viewModel = viewModel)
                }
            }
        }
    }
}

private data class OnboardingPage(
    val title: String,
    val description: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationGranted by remember {
        mutableStateOf(context.hasNotificationPermission())
    }
    val pages = remember(needsNotificationPermission) {
        buildList {
            add(
                OnboardingPage(
                    title = "Welcome to PayChat",
                    description = "Chat with customers, track dues, and keep your payment history together in one simple workspace.",
                    accent = Color(0xFF0C6D59),
                    icon = Icons.Rounded.ChatBubbleOutline,
                ),
            )
            add(
                OnboardingPage(
                    title = "Sign In With Phone",
                    description = "Your account is secured with phone verification so your business profile stays connected to your verified number.",
                    accent = Color(0xFF1458B0),
                    icon = Icons.Rounded.CheckCircle,
                ),
            )
            if (needsNotificationPermission) {
                add(
                    OnboardingPage(
                        title = "Stay Updated",
                        description = "Allow notifications so PayChat can alert you about new messages, payment updates, and due reminders.",
                        accent = Color(0xFFC86A12),
                        icon = Icons.Rounded.NotificationsActive,
                    ),
                )
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted || context.hasNotificationPermission()
    }
    val currentPage = pages.getOrNull(pagerState.currentPage)
    val isPermissionPage = needsNotificationPermission && pagerState.currentPage == pages.lastIndex
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFCF4), Color(0xFFF1F8F5), Color(0xFFE6F0FF)),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Skip")
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val item = pages[page]
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                    shape = RoundedCornerShape(32.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(RoundedCornerShape(36.dp))
                                .background(item.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.accent,
                                modifier = Modifier.size(58.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF203043),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = item.description,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF5E6A79),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (isPermissionPage && page == pagerState.currentPage) {
                            Spacer(modifier = Modifier.height(28.dp))
                            if (notificationGranted) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Notifications enabled") },
                                )
                            } else {
                                Button(
                                    onClick = {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text("Allow notifications")
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "You can still continue without it and enable notifications later from Android settings.",
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF7A789A),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (index == pagerState.currentPage) 28.dp else 10.dp, height = 10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (index == pagerState.currentPage) {
                                    currentPage?.accent ?: MaterialTheme.colorScheme.primary
                                } else {
                                    Color(0xFFD5DCE4)
                                },
                            ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    when {
                        !isLastPage -> "Next"
                        isPermissionPage && !notificationGranted -> "Continue for now"
                        else -> "Get started"
                    },
                )
            }
        }
    }
}

@Composable
private fun SplashScreen(
    subtitle: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF144F7D),
                        Color(0xFF1B6CA8),
                        Color(0xFF2C8FD4),
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(2.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "PayChat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text("Retry", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun LoginScreen(viewModel: PayChatViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val authEntry by viewModel.authEntry.collectAsStateWithLifecycle()
    var selectedCountryIso by rememberSaveable(authEntry.countryIso) {
        mutableStateOf(authEntry.countryIso)
    }
    var nationalNumber by rememberSaveable(authEntry.nationalNumber) {
        mutableStateOf(authEntry.nationalNumber)
    }
    var otpCode by rememberSaveable(authEntry.verificationId) { mutableStateOf("") }
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }
    val selectedCountry = remember(selectedCountryIso) {
        countryOptions().firstOrNull { it.iso == selectedCountryIso } ?: countryOptions().first()
    }
    val isPhoneStep = authEntry.step == AuthEntryStep.PHONE
    val errorMessage = authEntry.errorMessage
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    LaunchedEffect(authEntry.countryIso, authEntry.nationalNumber) {
        if (authEntry.countryIso != selectedCountryIso) {
            selectedCountryIso = authEntry.countryIso
        }
        if (authEntry.nationalNumber != nationalNumber) {
            nationalNumber = authEntry.nationalNumber
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedIso = selectedCountryIso,
            onDismiss = { showCountryPicker = false },
            onSelected = { option ->
                selectedCountryIso = option.iso
                showCountryPicker = false
                viewModel.clearAuthError()
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (isPhoneStep) {
            PhoneNumberPage(
                selectedCountry = selectedCountry,
                nationalNumber = nationalNumber,
                onNationalNumberChange = {
                    nationalNumber = it.filter(Char::isDigit)
                    viewModel.clearAuthError()
                },
                onCountryClick = { showCountryPicker = true },
                onNext = {
                    if (activity == null) {
                        showToast(context, "Could not access the current activity.")
                    } else {
                        viewModel.startPhoneNumberVerification(
                            activity = activity,
                            countryIso = selectedCountry.iso,
                            countryDialCode = selectedCountry.dialCode,
                            rawNationalNumber = nationalNumber,
                        )
                    }
                },
                isSending = authEntry.isSendingCode,
                errorMessage = errorMessage,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PayChat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A1A2E),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Confirm the code we just sent and continue to your chats.",
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(34.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 24.dp,
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.10f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AuthHeaderBadge(
                                icon = Icons.Rounded.PhoneIphone,
                                containerColor = primary.copy(alpha = 0.10f),
                                contentColor = primary,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            AuthHeaderBadge(
                                icon = Icons.Rounded.Lock,
                                containerColor = secondary.copy(alpha = 0.10f),
                                contentColor = secondary,
                            )
                        }
                        Spacer(modifier = Modifier.height(26.dp))
                        AuthStatusBubble(
                            title = "Code sent",
                            message = "We sent a 6-digit verification code to ${authEntry.otpSentTo ?: authEntry.phoneNumberE164}.",
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        
                        Text(
                            text = "Fill the code",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1A1A2E),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Enter the 6-digit code to confirm your number and continue.",
                            color = Color(0xFF6B7280),
                        )
                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(18.dp))
                            AuthErrorBanner(message = errorMessage)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        OtpCodeField(
                            value = otpCode,
                            onValueChange = {
                                otpCode = it
                                viewModel.clearAuthError()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Didn't get the code?",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (activity == null) {
                                        showToast(context, "Could not access the current activity.")
                                    } else {
                                        otpCode = ""
                                        viewModel.startPhoneNumberVerification(
                                            activity = activity,
                                            countryIso = selectedCountry.iso,
                                            countryDialCode = selectedCountry.dialCode,
                                            rawNationalNumber = nationalNumber,
                                        )
                                    }
                                },
                                enabled = !authEntry.isSendingCode && !authEntry.isVerifyingCode,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Resend")
                            }
                            TextButton(
                                onClick = {
                                    otpCode = ""
                                    viewModel.editPhoneNumber()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Edit number")
                            }
                        }
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = { viewModel.verifyOtp(otpCode) },
                            enabled = !authEntry.isVerifyingCode && !authEntry.isSendingCode,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primary,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            if (authEntry.isVerifyingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Text("Verify code")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Secure phone verification powered by Firebase",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.AuthDecorativeBackdrop(
    primary: Color,
    secondary: Color,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 24.dp, top = 34.dp)
            .size(112.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.10f)),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 112.dp, end = 36.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(secondary.copy(alpha = 0.20f)),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.08f),
                        secondary.copy(alpha = 0.10f),
                    ),
                ),
            ),
    )
}

@Composable
private fun AuthHeaderBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AuthStatusBubble(
    title: String,
    message: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = title,
                color = Color(0xFF1A1A2E),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AuthErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFF4E8))
            .border(
                width = 1.dp,
                color = Color(0xFFF0D0AF),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = Color(0xFFB46716),
        )
        Text(
            text = message,
            color = Color(0xFF7E4A11),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AuthSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF4B5563),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun AuthSupportCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AuthConsentRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.Transparent),
        cursorBrush = SolidColor(primary),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(6) { index ->
                    val digit = value.getOrNull(index)?.toString().orEmpty()
                    val isActiveCell = if (value.length >= 6) index == 5 else index == value.length
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (digit.isBlank()) Color(0xFFF9FAFB) else Color.White)
                            .border(
                                width = 1.dp,
                                color = when {
                                    digit.isNotBlank() -> primary.copy(alpha = 0.42f)
                                    isFocused && isActiveCell -> primary.copy(alpha = 0.60f)
                                    else -> outline
                                },
                                shape = RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit.ifBlank { if (isFocused && isActiveCell) "|" else "" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A2E),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun authOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
    unfocusedBorderColor = Color(0xFFE5E7EB),
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = Color(0xFF1A1A2E),
    unfocusedTextColor = Color(0xFF1A1A2E),
    focusedPlaceholderColor = Color(0xFF9CA3AF),
    unfocusedPlaceholderColor = Color(0xFF9CA3AF),
)

@Composable
private fun PhoneNumberPage(
    selectedCountry: CountryOption,
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    onCountryClick: () -> Unit,
    onNext: () -> Unit,
    isSending: Boolean,
    errorMessage: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        // Illustration placeholder
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhoneIphone,
                contentDescription = null,
                tint = Color(0xFF5C55D9),
                modifier = Modifier.size(80.dp)
            )
            Box(
                modifier = Modifier
                    .offset(x = 24.dp, y = (-20).dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF55E798)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Verify Your Number",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5C55D9),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please enter your Country &\nyour Phone Number",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFA0A0A0),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (errorMessage != null) {
            AuthErrorBanner(message = errorMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFF5F1))
                    .clickable(onClick = onCountryClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = selectedCountry.flag, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.Black
                )
            }

            BasicTextField(
                value = nationalNumber,
                onValueChange = onNationalNumberChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFF5F1)),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (nationalNumber.isEmpty()) {
                            Text(
                                text = "010277 62 986",
                                color = Color(0xFFA0A0A0),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.LightGray
            ),
            contentPadding = PaddingValues(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF55E798), Color(0xFF35D67D))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "SEND",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(viewModel: PayChatViewModel) {
    val navController = rememberNavController()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination?.route
    val user = viewModel.currentUser() ?: return
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val bottomBarRoutes = setOf(Routes.chats, Routes.profile)

    Scaffold(
        bottomBar = {
            if (currentDestination in bottomBarRoutes) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        BorderStroke(0.5.dp, Color(0xFFE5E7EB)),
                    ),
                ) {
                    NavigationBarItem(
                        selected = currentDestination == Routes.chats,
                        onClick = {
                            navController.navigate(Routes.chats) {
                                popUpTo(Routes.chats) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null) },
                        label = { Text("Chats") },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1B6CA8),
                            selectedTextColor = Color(0xFF1B6CA8),
                            indicatorColor = Color(0xFFD0E8F8),
                            unselectedIconColor = Color(0xFF9CA3AF),
                            unselectedTextColor = Color(0xFF9CA3AF),
                        ),
                    )
                    NavigationBarItem(
                        selected = currentDestination == Routes.profile,
                        onClick = {
                            navController.navigate(Routes.profile) {
                                popUpTo(Routes.chats)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Rounded.PersonOutline, contentDescription = null) },
                        label = { Text("Profile") },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1B6CA8),
                            selectedTextColor = Color(0xFF1B6CA8),
                            indicatorColor = Color(0xFFD0E8F8),
                            unselectedIconColor = Color(0xFF9CA3AF),
                            unselectedTextColor = Color(0xFF9CA3AF),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.chats,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.chats) {
                ChatListScreen(
                    threads = threads,
                    transactions = transactions,
                    onOpenConversation = { threadId ->
                        navController.navigate(Routes.conversation(threadId))
                    },
                    onOpenTransactions = { navController.navigate(Routes.transactions) },
                    onOpenTransactionDetail = { transactionId ->
                        navController.navigate(Routes.transactionDetail(transactionId))
                    },
                    onOpenAddMember = { navController.navigate(Routes.addChatMember) },
                )
            }
            composable(Routes.addChatMember) {
                AddChatMemberScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onMemberCreated = { threadId ->
                        navController.popBackStack()
                        navController.navigate(Routes.conversation(threadId))
                    },
                )
            }
            composable(Routes.profile) {
                ProfileScreen(
                    user = user,
                    onEditProfile = { navController.navigate(Routes.editProfile) },
                    onOpenTransactions = { navController.navigate(Routes.transactions) },
                    onLogout = viewModel::signOut,
                )
            }
            composable(
                route = Routes.conversation,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId").orEmpty()
                val thread = viewModel.thread(threadId)
                if (thread == null) {
                    EmptyScreen(
                        title = "Conversation not found",
                        message = "This conversation could not be opened.",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    ConversationScreen(
                        viewModel = viewModel,
                        threadId = threadId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.transactions) {
                TransactionsScreen(
                    transactions = transactions,
                    onBack = { navController.popBackStack() },
                    onOpenDetails = { id -> navController.navigate(Routes.transactionDetail(id)) },
                )
            }
            composable(
                route = Routes.transactionDetail,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val transactionId = backStackEntry.arguments?.getString("transactionId").orEmpty()
                val transaction = viewModel.transaction(transactionId)
                if (transaction == null) {
                    EmptyScreen(
                        title = "Transaction not found",
                        message = "This transaction could not be opened.",
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    TransactionDetailScreen(
                        transaction = transaction,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.editProfile) {
                ProfileEditorScreen(
                    viewModel = viewModel,
                    user = user,
                    title = "Edit profile",
                    subtitle = "Update your business identity and photo.",
                    submitLabel = "Save Changes",
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    nameLabel = "Full name",
                    showEmailField = true,
                    showProfileMeta = true,
                )
            }
        }
    }
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
private fun ChatListScreen(
    threads: List<ChatThread>,
    transactions: List<AppTransaction>,
    onOpenConversation: (String) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransactionDetail: (String) -> Unit,
    onOpenAddMember: () -> Unit,
) {
    val unreadThreads = threads.count { it.unreadCount > 0 }
    val pendingTransactions = transactions
        .filter { it.status == com.paychat.paychat.model.TransactionStatus.PENDING }
        .sortedByDescending { it.updatedAt }
    val receivableTransactions = transactions
        .filter { it.type == TransactionType.DUE || it.type == TransactionType.RECEIVED }
        .sortedByDescending { it.updatedAt }
    var selectedTabName by rememberSaveable { mutableStateOf(ChatHomeTab.CHATS.name) }
    val selectedTab = ChatHomeTab.valueOf(selectedTabName)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF144F7D), Color(0xFF1B6CA8)),
                )
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenTransactions) {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    HeaderTab(
                        label = "CHATS",
                        count = unreadThreads,
                        active = selectedTab == ChatHomeTab.CHATS,
                        onClick = { selectedTabName = ChatHomeTab.CHATS.name },
                    )
                    HeaderTab(
                        label = "PENDING",
                        count = pendingTransactions.size,
                        active = selectedTab == ChatHomeTab.PENDING,
                        onClick = { selectedTabName = ChatHomeTab.PENDING.name },
                    )
                    HeaderTab(
                        label = "RECEIVABLES",
                        count = receivableTransactions.size,
                        active = selectedTab == ChatHomeTab.RECEIVABLES,
                        onClick = { selectedTabName = ChatHomeTab.RECEIVABLES.name },
                    )
                }
            }

            Surface(
                color = Color(0xFFFDFDFD),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (selectedTab) {
                    ChatHomeTab.CHATS -> {
                        if (threads.isEmpty()) {
                            EmptyState(
                                title = "No chats yet",
                                message = "Conversation placeholders are ready for future realtime integration.",
                                icon = Icons.Rounded.ChatBubbleOutline,
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
                            ) {
                                items(threads, key = { it.id }) { thread ->
                                    ChatRow(thread = thread, onClick = { onOpenConversation(thread.id) })
                                    Divider(
                                        color = Color(0xFFE2E2E2),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 92.dp, end = 14.dp),
                                    )
                                }
                            }
                        }
                    }

                    ChatHomeTab.PENDING -> {
                        TransactionTabContent(
                            items = pendingTransactions,
                            emptyTitle = "No pending items",
                            emptyMessage = "Pending collections or unpaid requests will appear here.",
                            icon = Icons.Rounded.WarningAmber,
                            accentColor = Color(0xFFC86A12),
                            primaryLabel = { "Pending" },
                            secondaryLabel = { "${it.type.label()} • ${Formatters.compactDate(it.updatedAt)}" },
                            onItemClick = { onOpenTransactionDetail(it.transactionId) },
                        )
                    }

                    ChatHomeTab.RECEIVABLES -> {
                        TransactionTabContent(
                            items = receivableTransactions,
                            emptyTitle = "No receivables yet",
                            emptyMessage = "Amounts due from customers and received collections will show here.",
                            icon = Icons.Rounded.ReceiptLong,
                            accentColor = Color(0xFF158169),
                            primaryLabel = { it.type.label() },
                            secondaryLabel = { "${it.status.name.lowercase().replaceFirstChar { char -> char.uppercase() }} • ${Formatters.compactDate(it.updatedAt)}" },
                            onItemClick = { onOpenTransactionDetail(it.transactionId) },
                        )
                    }
                }
            }
        }

        if (selectedTab == ChatHomeTab.CHATS) {
            FloatingActionButton(
                onClick = onOpenAddMember,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp),
                containerColor = Color(0xFF1B6CA8),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "New chat",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChatMemberScreen(
    viewModel: PayChatViewModel,
    onBack: () -> Unit,
    onMemberCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val availableCountries = remember { countryOptions() }
    var memberName by rememberSaveable { mutableStateOf("") }
    var memberNationalNumber by rememberSaveable { mutableStateOf("") }
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }
    var selectedCountryIso by rememberSaveable {
        mutableStateOf(availableCountries.firstOrNull { it.iso == "BD" }?.iso ?: availableCountries.first().iso)
    }
    val selectedCountry = remember(selectedCountryIso, availableCountries) {
        availableCountries.firstOrNull { it.iso == selectedCountryIso } ?: availableCountries.first()
    }
    var contactsPermissionGranted by remember {
        mutableStateOf(context.hasContactsPermission())
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        contactsPermissionGranted = granted || context.hasContactsPermission()
    }
    LaunchedEffect(contactsPermissionGranted) {
        if (!contactsPermissionGranted && !permissionRequested) {
            permissionRequested = true
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    val contacts by produceState(
        initialValue = emptyList<DeviceContact>(),
        key1 = contactsPermissionGranted,
        key2 = context,
    ) {
        value = if (contactsPermissionGranted) {
            withContext(Dispatchers.IO) { context.loadDeviceContacts() }
        } else {
            emptyList()
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedIso = selectedCountry.iso,
            onDismiss = { showCountryPicker = false },
            onSelected = { option ->
                selectedCountryIso = option.iso
                showCountryPicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF144F7D),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                title = { Text("New Chat", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Add manually",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = memberName,
                            onValueChange = { memberName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CountryCodeSelector(
                                option = selectedCountry,
                                onClick = { showCountryPicker = true },
                                modifier = Modifier.weight(0.95f),
                            )
                            OutlinedTextField(
                                value = memberNationalNumber,
                                onValueChange = {
                                    memberNationalNumber = it.filter { char -> char.isDigit() }
                                },
                                label = { Text("Phone number") },
                                placeholder = { Text(selectedCountry.example) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.weight(1.75f),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Use the same phone-number format as registration so PayChat can match this member later, for example ${selectedCountry.dialCode}${selectedCountry.example.trimStart('0')}.",
                            color = Color(0xFF7A789A),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val formattedPhone = formatPhoneNumberForMember(
                                        countryDialCode = selectedCountry.dialCode,
                                        nationalNumber = memberNationalNumber,
                                    )
                                    if (formattedPhone == null) {
                                        showToast(context, "Enter a valid phone number.")
                                        return@launch
                                    }
                                    val result = viewModel.createChatMember(
                                        memberName = memberName,
                                        memberPhone = formattedPhone,
                                    )
                                    result.onSuccess { thread ->
                                        onMemberCreated(thread.id)
                                    }.onFailure {
                                        showToast(context, it.message ?: "Could not add this chat member.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create chat")
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Add from contacts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose a saved contact and we’ll create a chat with that member.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        if (!contactsPermissionGranted) {
                            Button(
                                onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            ) {
                                Text("Allow contacts access")
                            }
                        } else if (contacts.isEmpty()) {
                            Text(
                                text = "No device contacts with phone numbers were found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            contacts.take(20).forEachIndexed { index, contact ->
                                ContactRow(
                                    contact = contact,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.createChatMember(
                                                memberName = contact.name,
                                                memberPhone = contact.phone,
                                            )
                                            result.onSuccess { thread ->
                                                onMemberCreated(thread.id)
                                            }.onFailure {
                                                showToast(context, it.message ?: "Could not add this contact.")
                                            }
                                        }
                                    },
                                )
                                if (index != contacts.take(20).lastIndex) {
                                    Divider(
                                        color = Color(0xFFE8ECEA),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 58.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: DeviceContact,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFEAF7F1)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Formatters.initials(contact.name),
                color = Color(0xFF158169),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = contact.phone,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Add",
            color = Color(0xFF158169),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeaderTab(
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (active) Color.White else Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = count.toString(),
                        color = Color(0xFF0B7C74),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(if (active) 58.dp else 0.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun TransactionTabContent(
    items: List<AppTransaction>,
    emptyTitle: String,
    emptyMessage: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    primaryLabel: (AppTransaction) -> String,
    secondaryLabel: (AppTransaction) -> String,
    onItemClick: (AppTransaction) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            title = emptyTitle,
            message = emptyMessage,
            icon = icon,
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.transactionId }) { item ->
                TransactionTabRow(
                    item = item,
                    accentColor = accentColor,
                    primaryLabel = primaryLabel(item),
                    secondaryLabel = secondaryLabel(item),
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun TransactionTabRow(
    item: AppTransaction,
    accentColor: Color,
    primaryLabel: String,
    secondaryLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Formatters.initials(item.contactName),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = primaryLabel,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = secondaryLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = Formatters.currency(item.amount),
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
    }
}

@Composable
private fun ChatRow(
    thread: ChatThread,
    onClick: () -> Unit,
) {
    val unread = thread.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = thread.title, photoUrl = thread.avatarUrl, size = 48.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1A1A2E),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Formatters.time(thread.updatedAt),
                    color = if (unread) Color(0xFF1B6CA8) else Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.lastMessagePreview,
                    color = if (unread) Color(0xFF1A1A2E) else Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (thread.unreadCount > 99) "99+" else thread.unreadCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    viewModel: PayChatViewModel,
    threadId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = viewModel.currentUser()
    val thread = viewModel.thread(threadId)
    val messages by viewModel.messages(threadId).collectAsStateWithLifecycle()
    var messageText by rememberSaveable { mutableStateOf("") }
    var showTransactionDialog by rememberSaveable { mutableStateOf(false) }
    var transactionType by rememberSaveable { mutableStateOf(TransactionType.SENT) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }

    if (thread == null) {
        EmptyScreen(
            title = "Conversation not found",
            message = "This conversation could not be opened.",
            onBack = onBack,
        )
        return
    }

    if (showTransactionDialog) {
        AlertDialog(
            onDismissRequest = { showTransactionDialog = false },
            title = { Text("Add transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This record will be attached to ${thread.title}.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionType.values().forEach { type ->
                            FilterChip(
                                selected = type == transactionType,
                                onClick = { transactionType = type },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            showToast(context, "Enter a valid amount.")
                            return@Button
                        }
                        scope.launch {
                            val result = viewModel.addConversationTransaction(
                                threadId = thread.id,
                                type = transactionType,
                                amount = amount,
                                note = noteText,
                            )
                            result.onSuccess {
                                showToast(context, "Transaction added to this chat.")
                                amountText = ""
                                noteText = ""
                                transactionType = TransactionType.SENT
                                showTransactionDialog = false
                            }.onFailure {
                                showToast(context, it.message ?: "Could not add the transaction.")
                            }
                        }
                    },
                ) {
                    Text("Save transaction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransactionDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = Color(0xFFECE5DD),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF144F7D),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name = thread.title, photoUrl = thread.avatarUrl, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = thread.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Online",
                                color = Color(0xFFE8F4FD),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Emoji",
                            tint = Color(0xFF6B7280),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFF5F7FA))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF1A1A2E),
                            ),
                            cursorBrush = SolidColor(Color(0xFF1B6CA8)),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (messageText.isEmpty()) {
                                    Text(
                                        "Message",
                                        color = Color(0xFF9CA3AF),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = { showTransactionDialog = true }) {
                        Icon(
                            Icons.Rounded.ReceiptLong,
                            contentDescription = "Add transaction",
                            tint = Color(0xFF6B7280),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (messageText.isNotBlank()) Color(0xFF1B6CA8)
                                else Color(0xFF9CA3AF)
                            )
                            .clickable {
                                if (messageText.isNotBlank()) {
                                    scope.launch {
                                        val result = viewModel.sendMessage(thread.id, messageText)
                                        result.onSuccess { messageText = "" }
                                            .onFailure {
                                                showToast(context, it.message ?: "Could not send the message.")
                                            }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (messageText.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.ReceiptLong,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFECE5DD)),
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF6B7280),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1F000000))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "Messages are end-to-end encrypted",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp, end = 8.dp,
                        top = 8.dp, bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = false,
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isMine = user?.uid == message.senderId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (isMine) 64.dp else 8.dp,
                                    end = if (isMine) 8.dp else 64.dp,
                                    top = 2.dp, bottom = 2.dp,
                                ),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        ) {
                            if (message.type == MessageContentType.TRANSACTION) {
                                TransactionBubble(message = message, mine = isMine)
                            } else {
                                TextBubble(message = message, mine = isMine)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBubble(message: AppMessage, mine: Boolean) {
    val sentShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = 18.dp, bottomEnd = 4.dp,
    )
    val receivedShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 18.dp,
        bottomStart = 18.dp, bottomEnd = 18.dp,
    )
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(if (mine) sentShape else receivedShape)
            .background(
                if (mine) Color(0xFFDCF8C6) else Color.White,
            )
            .then(
                if (!mine) Modifier.border(
                    0.5.dp,
                    Color(0xFFE5E7EB),
                    if (mine) sentShape else receivedShape,
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message.text,
            color = Color(0xFF1A1A2E),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = Formatters.time(message.createdAt),
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.labelSmall,
            )
            if (mine) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF1B6CA8),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun TransactionBubble(message: AppMessage, mine: Boolean) {
    val sentShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = 18.dp, bottomEnd = 4.dp,
    )
    val receivedShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 18.dp,
        bottomStart = 18.dp, bottomEnd = 18.dp,
    )
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(if (mine) sentShape else receivedShape)
            .background(if (mine) Color(0xFFDCF8C6) else Color.White)
            .border(
                0.5.dp,
                Color(0xFFD0E8F8),
                if (mine) sentShape else receivedShape,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B6CA8).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = Color(0xFF1B6CA8),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Payment Record",
                color = Color(0xFF1B6CA8),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = Formatters.currency(message.amount ?: 0.0),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1A1A2E),
        )
        if (message.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Formatters.time(message.createdAt),
            color = Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsScreen(
    transactions: List<AppTransaction>,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF144F7D),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                title = { Text("Transactions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyState(
                title = "No transactions found",
                message = "Transactions can be created from a conversation between two users.",
                icon = Icons.Rounded.ReceiptLong,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(transactions, key = { it.transactionId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetails(item.transactionId) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.contactName, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${item.type.label().uppercase()} • ${Formatters.compactDate(item.createdAt)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = Formatters.currency(item.amount),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailScreen(
    transaction: AppTransaction,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color(0xFFF5F7FA),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF144F7D),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                title = { Text("Transaction Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = Formatters.currency(transaction.amount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(transaction.contactName)
                    Spacer(modifier = Modifier.height(18.dp))
                    DetailRow(label = "Type", value = transaction.type.label())
                    DetailRow(
                        label = "Status",
                        value = transaction.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    )
                    DetailRow(label = "Phone", value = transaction.contactPhone.ifBlank { "-" })
                    DetailRow(label = "Created", value = Formatters.compactDate(transaction.createdAt))
                    DetailRow(label = "Updated", value = Formatters.compactDate(transaction.updatedAt))
                    DetailRow(label = "Note", value = transaction.note.ifBlank { "-" })
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    user: AppUser,
    onEditProfile: () -> Unit,
    onOpenTransactions: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA)),
    ) {
        // ── Primary header banner ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF144F7D), Color(0xFF1B6CA8)),
                    )
                )
                .statusBarsPadding()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Box {
                    Avatar(
                        name = user.displayName,
                        photoUrl = user.photoUrl,
                        size = 88.dp,
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B6CA8))
                            .border(2.dp, Color.White, CircleShape)
                            .align(Alignment.BottomEnd)
                            .clickable { onEditProfile() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user.phoneNumber.ifBlank { user.email.ifBlank { "No contact info" } },
                    color = Color(0xFFE8F4FD),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ── Action cards ──────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ProfileActionCard(
                    icon = Icons.Rounded.Edit,
                    title = "Edit profile",
                    subtitle = "Name, email, address, and photo",
                    onClick = onEditProfile,
                )
            }
            item {
                ProfileActionCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Transactions",
                    subtitle = "View chat-linked payment records",
                    onClick = onOpenTransactions,
                )
            }
            item {
                ProfileActionCard(
                    icon = Icons.Rounded.Lock,
                    title = "Security",
                    subtitle = "Phone-verified Firebase authentication",
                )
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Icon(
                        Icons.Rounded.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Out", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProfileActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE8F4FD)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF1B6CA8),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileEditorScreen(
    viewModel: PayChatViewModel,
    user: AppUser?,
    title: String,
    subtitle: String,
    submitLabel: String,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    nameLabel: String,
    showEmailField: Boolean,
    showProfileMeta: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (user == null) {
        SplashScreen(subtitle = "Loading your profile...", onRetry = viewModel::retry)
        return
    }

    var fullName by remember(user.uid) { mutableStateOf(user.fullName) }
    var email by remember(user.uid) { mutableStateOf(user.email) }
    var address by remember(user.uid) { mutableStateOf(user.address) }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(10.dp))
        Text(subtitle)
        Spacer(modifier = Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(
                        name = user.displayName,
                        photoUrl = user.photoUrl,
                        localPhoto = selectedImageUri,
                        size = 84.dp,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        picker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (selectedImageUri == null) "Add profile photo" else "Change photo")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(nameLabel) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showEmailField) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showProfileMeta) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Phone: ${user.phoneNumber.ifBlank { "-" }}") },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("Role: ${user.role}") },
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (fullName.trim().isEmpty()) {
                            showToast(context, "Full name is required.")
                            return@Button
                        }
                        scope.launch {
                            saving = true
                            val result = viewModel.saveProfile(
                                fullName = fullName,
                                email = email,
                                address = address,
                                imageUri = selectedImageUri,
                            )
                            saving = false
                            result.onSuccess { onDone() }.onFailure {
                                showToast(context, it.message ?: "Could not save your profile.")
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(submitLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedAccountScreen(
    user: AppUser?,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GppMaybe,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Account access paused",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${user?.displayName ?: "This account"} is currently blocked. Please contact support if you think this is a mistake.",
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onLogout) {
                    Text("Log Out")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF144F7D),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                ),
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        EmptyState(
            title = title,
            message = message,
            icon = Icons.Rounded.WarningAmber,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(
    name: String,
    photoUrl: String,
    size: androidx.compose.ui.unit.Dp,
    localPhoto: Any? = null,
) {
    val model = localPhoto ?: photoUrl.takeIf { it.isNotBlank() }
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFFE8F4FD))
                .border(1.5.dp, Color(0xFFD0E8F8), CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFFE8F4FD))
                .border(1.5.dp, Color(0xFFD0E8F8), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Formatters.initials(name),
                color = Color(0xFF1B6CA8),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun TransactionType.label(): String {
    return when (this) {
        TransactionType.SENT -> "Sent"
        TransactionType.RECEIVED -> "Received"
        TransactionType.PAID -> "Paid"
        TransactionType.DUE -> "Due"
    }
}

private data class CountryOption(
    val iso: String,
    val name: String,
    val dialCode: String,
    val flag: String,
    val example: String,
)

@Composable
private fun CountryCodeSelector(
    option: CountryOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFF8FCFA),
            contentColor = Color(0xFF183B34),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.flag,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(
                    text = option.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.dialCode,
                    color = Color(0xFF60706B),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = Color(0xFF60706B),
            )
        }
    }
}

@Composable
private fun CountryPickerDialog(
    selectedIso: String,
    onDismiss: () -> Unit,
    onSelected: (CountryOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select country code") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                countryOptions().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelected(option) }
                            .background(
                                if (option.iso == selectedIso) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.flag,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = option.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = option.dialCode,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (option.iso == selectedIso) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun countryOptions(): List<CountryOption> {
    return listOf(
        CountryOption("BD", "Bangladesh", "+880", "BD", "01712345678"),
        CountryOption("US", "United States", "+1", "US", "6505553434"),
        CountryOption("IN", "India", "+91", "IN", "9876543210"),
        CountryOption("PK", "Pakistan", "+92", "PK", "3012345678"),
        CountryOption("GB", "United Kingdom", "+44", "GB", "7400123456"),
        CountryOption("AE", "United Arab Emirates", "+971", "AE", "501234567"),
        CountryOption("SA", "Saudi Arabia", "+966", "SA", "501234567"),
        CountryOption("CA", "Canada", "+1", "CA", "6045551234"),
        CountryOption("AU", "Australia", "+61", "AU", "412345678"),
        CountryOption("DE", "Germany", "+49", "DE", "15123456789"),
    )
}

private fun formatPhoneNumberForMember(
    countryDialCode: String,
    nationalNumber: String,
): String? {
    val dialDigits = countryDialCode.filter(Char::isDigit)
    val localDigits = nationalNumber.filter(Char::isDigit).trimStart('0')
    if (dialDigits.isBlank() || localDigits.isBlank()) {
        return null
    }

    val e164 = "+$dialDigits$localDigits"
    return if (e164.length in 8..16) e164 else null
}

private const val onboardingPreferencesName = "paychat_preferences"
private const val onboardingCompletedKey = "onboarding_completed"

private fun Context.hasCompletedOnboarding(): Boolean {
    return applicationContext
        .getSharedPreferences(onboardingPreferencesName, Context.MODE_PRIVATE)
        .getBoolean(onboardingCompletedKey, false)
}

private fun Context.setOnboardingCompleted(completed: Boolean) {
    applicationContext
        .getSharedPreferences(onboardingPreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(onboardingCompletedKey, completed)
        .apply()
}

private fun Context.hasNotificationPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.hasContactsPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
}

private fun Context.loadDeviceContacts(): List<DeviceContact> {
    val resolver = applicationContext.contentResolver
    val contacts = linkedMapOf<String, DeviceContact>()
    val cursor = resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    ) ?: return emptyList()

    cursor.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val name = if (nameIndex >= 0) it.getString(nameIndex).orEmpty().trim() else ""
            val phone = if (phoneIndex >= 0) it.getString(phoneIndex).orEmpty().trim() else ""
            if (name.isBlank() || phone.isBlank()) continue
            val key = "$name|$phone"
            contacts.putIfAbsent(key, DeviceContact(name = name, phone = phone))
        }
    }

    return contacts.values.toList()
}
