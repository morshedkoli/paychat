package com.paychat.koli.ui.nav

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
import com.paychat.koli.feature.auth.AuthGate
import com.paychat.koli.feature.auth.AuthGateViewModel
import com.paychat.koli.feature.auth.login.LoginScreen
import com.paychat.koli.feature.auth.otp.OtpPurpose
import com.paychat.koli.feature.auth.otp.OtpScreen
import com.paychat.koli.feature.auth.register.RegisterScreen
import com.paychat.koli.feature.contacts.AddContactScreen
import com.paychat.koli.feature.contacts.ContactsScreen
import com.paychat.koli.ui.screens.PlaceholderScreen

/**
 * Navigation skeleton. Screens are filled in phase by phase; each placeholder
 * names the phase that replaces it.
 */
@Composable
fun PayChatNavHost(
    navController: NavHostController,
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
                PlaceholderScreen(
                    title = "Home",
                    phase = "Phase 3 and 6",
                    navController = navController,
                    actions = listOf(
                        "Contacts" to { navController.navigate(Routes.CONTACTS) },
                        "Sign out" to { authGateViewModel.signOut() },
                    ),
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
                PlaceholderScreen("Chat", "Phase 3", navController)
            }
            composable(
                Routes.ADD_TRANSACTION,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                PlaceholderScreen("New transaction", "Phase 5", navController)
            }
            composable(
                Routes.TRANSACTION_DETAIL,
                arguments = listOf(navArgument(NavArgs.TXN_ID) { type = NavType.StringType })
            ) {
                PlaceholderScreen("Transaction", "Phase 5", navController)
            }
            composable(
                Routes.LEDGER,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                PlaceholderScreen("Ledger", "Phase 6", navController)
            }
            composable(
                Routes.INHERITED_REVIEW,
                arguments = listOf(navArgument(NavArgs.THREAD_ID) { type = NavType.StringType })
            ) {
                PlaceholderScreen("Review inherited history", "Phase 7", navController)
            }

            composable(Routes.SEARCH) {
                PlaceholderScreen("Search", "Phase 8", navController)
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen("Settings", "Phase 9", navController)
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
