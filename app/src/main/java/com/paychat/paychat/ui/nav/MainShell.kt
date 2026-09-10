package com.paychat.paychat.ui.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paychat.paychat.feature.chats.ChatsScreen
import com.paychat.paychat.feature.profile.ProfileScreen
import com.paychat.paychat.feature.transactions.TransactionsScreen

/**
 * The signed in shell: three tabs over their own graph.
 *
 * The tab roots are deliberately in a nested graph rather than the outer one.
 * That gives each tab its own back stack and scroll position, and it makes it
 * impossible for the bottom bar to show up over a chat or a transaction, which
 * both want the bottom edge for themselves.
 *
 * @param onOpenOuter opens a full screen destination on the outer graph.
 */
@Composable
fun MainShell(
    onOpenOuter: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val tabs = rememberNavController()
    val entry by tabs.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabItem(Routes.CHATS, "Chats", Icons.AutoMirrored.Filled.Chat, current, tabs)
                TabItem(Routes.TRANSACTIONS, "Transactions", Icons.Default.SwapVert, current, tabs)
                TabItem(Routes.PROFILE, "Profile", Icons.Default.Person, current, tabs)
            }
        },
    ) { inner ->
        NavHost(
            navController = tabs,
            startDestination = Routes.CHATS,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.CHATS) {
                ChatsScreen(
                    onOpenThread = { onOpenOuter(Routes.chat(it)) },
                    onNewChat = { onOpenOuter(Routes.CONTACTS) },
                    onSearch = { onOpenOuter(Routes.SEARCH) },
                    onReviewInherited = { onOpenOuter(Routes.inheritedReview(it)) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onOpenTransaction = { onOpenOuter(Routes.transactionDetail(it)) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onSignOut = onSignOut)
            }
        }
    }
}

/**
 * One tab. The navigate options are what let a tab keep the place it was left
 * in: the state is saved on the way out and restored on the way back.
 */
@Composable
private fun RowScope.TabItem(
    route: String,
    label: String,
    icon: ImageVector,
    current: String?,
    controller: NavHostController,
) {
    NavigationBarItem(
        selected = current == route,
        onClick = {
            controller.navigate(route) {
                popUpTo(controller.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
