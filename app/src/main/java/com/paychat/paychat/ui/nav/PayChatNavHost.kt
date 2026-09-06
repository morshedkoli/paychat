package com.paychat.paychat.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paychat.paychat.feature.auth.AuthGate
import com.paychat.paychat.feature.auth.AuthGateViewModel
import com.paychat.paychat.feature.auth.login.LoginScreen
import com.paychat.paychat.feature.auth.otp.OtpPurpose
import com.paychat.paychat.feature.auth.otp.OtpScreen
import com.paychat.paychat.feature.auth.register.RegisterScreen
import com.paychat.paychat.feature.contacts.AddContactScreen
import com.paychat.paychat.feature.chat.ChatScreen
import com.paychat.paychat.feature.contacts.ContactsScreen
import com.paychat.paychat.feature.home.HomeScreen
import com.paychat.paychat.feature.inherited.InheritedReviewScreen
import com.paychat.paychat.feature.ledger.LedgerScreen
import com.paychat.paychat.feature.search.SearchScreen
import com.paychat.paychat.feature.settings.SettingsScreen
import com.paychat.paychat.feature.transaction.AddTransactionScreen
import com.paychat.paychat.feature.transaction.TransactionDetailScreen

/**
 * Every destination in the app.
 */
@Composable
fun PayChatNavHost(
    navController: NavHostController,
    openThreadId: String? = null,
    onThreadOpened: () -> Unit = {},
    authGateViewModel: AuthGateViewModel = hiltViewModel(),
) {
    val gate by authGateViewModel.gate.collectAsStateWithLifecycle()
    val signedOutElsewhere by authGateViewModel.signedOutElsewhere.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // The splash route decides nothing itself; it waits for the gate.
    LaunchedEffect(gate) {
        when (gate) {
            AuthGate.CHECKING -> Unit
            AuthGate.SIGNED_IN -> navController.toTopLevel(Routes.HOME)
            AuthGate.SIGNED_OUT ->
                if (navController.currentDestination?.route !in Routes.AUTH_ROUTES) {
                    navController.toTopLevel(Routes.LOGIN)
                }
        }
    }

    // A tapped notification names a conversation, but it can only be opened
    // once the gate has decided the user is signed in.
    LaunchedEffect(openThreadId, gate) {
        if (openThreadId != null && gate == AuthGate.SIGNED_IN) {
            navController.navigate(Routes.chat(openThreadId)) { launchSingleTop = true }
            onThreadOpened()
        }
    }

    LaunchedEffect(signedOutElsewhere) {
        if (signedOutElsewhere) {
            snackbarHostState.showSnackbar(
                "You were signed out because this account was used on another device."
            )
            authGateViewModel.acknowledgeSignedOutElsewhere()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {

            composable(Routes.SPLASH) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onOtpRequired = { phone ->
                        navController.navigate(Routes.otp(phone, OtpPurpose.REGISTER.name))
                    },
                    onLoginInstead = { navController.toTopLevel(Routes.LOGIN) },
                )
            }

            composable(
                Routes.OTP,
                arguments = listOf(
                    navArgument(NavArgs.PHONE) { type = NavType.StringType },
                    navArgument(NavArgs.PURPOSE) { type = NavType.StringType },
                ),
            ) {
                OtpScreen(
                    onVerified = { authGateViewModel.onSignedIn() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onSignedIn = { authGateViewModel.onSignedIn() },
                    onRegisterInstead = { navController.toTopLevel(Routes.REGISTER) },
                    onResetRequested = { phone ->
                        navController.navigate(
                            Routes.otp(phone, OtpPurpose.RESET_PASSWORD.name)
                        )
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenThread = { threadId -> navController.navigate(Routes.chat(threadId)) },
                    onNewChat = { navController.navigate(Routes.CONTACTS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onReviewInherited = { threadId ->
                        navController.navigate(Routes.inheritedReview(threadId))
                    },
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onOpenThread = { threadId -> navController.navigate(Routes.chat(threadId)) },
                    onAddContact = { navController.navigate(Routes.ADD_CONTACT) },
                )
            }
            composable(Routes.ADD_CONTACT) {
                AddContactScreen(
                    onOpenThread = { threadId ->
                        // The contact is saved, so returning here would only
                        // offer to add them again.
                        navController.navigate(Routes.chat(threadId)) {
                            popUpTo(Routes.ADD_CONTACT) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                Routes.CHAT,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onAddTransaction = { threadId ->
                        navController.navigate(Routes.addTransaction(threadId))
                    },
                    onOpenLedger = { threadId -> navController.navigate(Routes.ledger(threadId)) },
                    onOpenTransaction = { txnId ->
                        navController.navigate(Routes.transactionDetail(txnId))
                    },
                )
            }
            composable(
                Routes.ADD_TRANSACTION,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                AddTransactionScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.TRANSACTION_DETAIL,
                arguments = listOf(navArgument(NavArgs.TXN_ID) { type = NavType.StringType })
            ) {
                TransactionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.LEDGER,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                LedgerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTransaction = { txnId ->
                        navController.navigate(Routes.transactionDetail(txnId))
                    },
                )
            }
            composable(
                Routes.INHERITED_REVIEW,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                InheritedReviewScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenThread = { threadId -> navController.navigate(Routes.chat(threadId)) },
                    onOpenTransaction = { txnId ->
                        navController.navigate(Routes.transactionDetail(txnId))
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onSignOut = authGateViewModel::signOut,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Replaces the whole back stack. Used when crossing between the signed in and
 * signed out halves of the app, where going "back" must not be possible.
 */
private fun NavHostController.toTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
