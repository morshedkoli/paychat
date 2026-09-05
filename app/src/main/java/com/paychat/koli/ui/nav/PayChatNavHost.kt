package com.paychat.koli.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.paychat.koli.ui.screens.PlaceholderScreen

/**
 * Navigation skeleton. Screens are filled in phase by phase; each placeholder
 * names the phase that replaces it.
 */
@Composable
fun PayChatNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            PlaceholderScreen("Splash", "Phase 1", navController)
        }
        composable(Routes.REGISTER) {
            PlaceholderScreen("Register", "Phase 1", navController)
        }
        composable(
            Routes.OTP,
            arguments = listOf(navArgument(NavArgs.PHONE) { type = NavType.StringType })
        ) {
            PlaceholderScreen("Verify phone", "Phase 1", navController)
        }
        composable(Routes.LOGIN) {
            PlaceholderScreen("Login", "Phase 1", navController)
        }

        composable(Routes.HOME) {
            PlaceholderScreen("Home", "Phase 3 and 6", navController)
        }
        composable(Routes.CONTACTS) {
            PlaceholderScreen("Contacts", "Phase 2", navController)
        }
        composable(Routes.ADD_CONTACT) {
            PlaceholderScreen("Add contact", "Phase 2", navController)
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
}
