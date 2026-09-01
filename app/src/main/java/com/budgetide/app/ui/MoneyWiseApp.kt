package com.budgetide.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.budgetide.app.viewmodel.MoneyViewModel

private val bottomTabs = listOf(
    Triple("home", "Home", Icons.Default.Home),
    Triple("transactions", "Transactions", Icons.Default.ReceiptLong),
    Triple("calculators", "Calculators", Icons.Default.Calculate),
    Triple("goals", "Goals", Icons.Default.Flag),
    Triple("more", "More", Icons.Default.MoreHoriz)
)

@Composable
fun BudgetideApp(vm: MoneyViewModel) {
    val nav = rememberNavController()
    // Navigation keeps destinations composed in its back stack. Keying the
    // host to entitlement makes the current destination rebuild immediately
    // when a purchase, restore, or developer override changes Pro status.
    val isPro by vm.isPro.collectAsState()
    key(isPro) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                bottomTabs.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            nav.navigate(route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { DashboardScreen(vm) }
            composable("transactions") { TransactionsScreen(vm) { nav.navigate("go_pro") } }
            composable("calculators") { CalculatorScreen(vm) { nav.navigate("go_pro") } }
            composable("goals") {
                val isPro by vm.isPro.collectAsState()
                if (isPro) {
                    GoalsScreen(vm)
                } else {
                    Page("Goals") {
                        ProLockedCard(
                            title = "Financial goals is a Pro feature",
                            description = "Set savings goals like an emergency fund or a big purchase, and track progress toward them.",
                            onUpgradeClick = { nav.navigate("go_pro") }
                        )
                    }
                }
            }
            composable("more") { MoreScreen(vm) { route -> nav.navigate(route) } }
            composable("go_pro") {
                GoProScreen(vm, onProActivated = { nav.popBackStack() })
            }
            composable("recurring") {
                ProGate(vm, nav, "Recurring & EMIs", "Track bills, subscriptions, and EMI outflow in one place.") {
                    RecurringScreen(vm)
                }
            }
            composable("warranties") {
                ProGate(vm, nav, "Warranties", "Track item warranties so you never miss a return or claim window.") {
                    WarrantiesScreen(vm)
                }
            }
            composable("lending") {
                ProGate(vm, nav, "Who owes me money?", "Keep track of money you've lent or borrowed.") {
                    LendingScreen(vm)
                }
            }
        }
    }
    }
}

/** Shows [content] if the user is Pro, otherwise a lock prompt for the given feature. */
@Composable
private fun ProGate(
    vm: MoneyViewModel,
    nav: androidx.navigation.NavHostController,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    val isPro by vm.isPro.collectAsState()
    if (isPro) {
        content()
    } else {
        Page(title) {
            ProLockedCard(
                title = "$title is a Pro feature",
                description = description,
                onUpgradeClick = { nav.navigate("go_pro") }
            )
        }
    }
}
