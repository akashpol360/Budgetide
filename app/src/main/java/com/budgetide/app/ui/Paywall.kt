package com.budgetide.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.budgetide.app.viewmodel.MoneyViewModel

private data class ProFeature(val icon: ImageVector, val title: String, val description: String)

private val freeFeatures = listOf(
    "Unlimited expense & income tracking",
    "Basic dashboard summary",
    "Can-I-afford-it & future savings calculators",
    "6 built-in categories"
)

private val proFeatures = listOf(
    ProFeature(Icons.Default.Insights, "Advanced reports", "Category breakdown chart and necessary-vs-unnecessary spending analysis"),
    ProFeature(Icons.Default.Flag, "Financial goals", "Set savings goals and track progress toward them"),
    ProFeature(Icons.Default.Repeat, "Recurring payments & EMIs", "Track bills, subscriptions, and EMI outflow"),
    ProFeature(Icons.Default.Shield, "Warranty tracking", "Never miss a return or claim window"),
    ProFeature(Icons.Default.People, "Lending tracker", "Keep track of who owes you and who you owe"),
    ProFeature(Icons.Default.TrendingDown, "Advanced analysis", "\"What if my salary decreases\" scenario simulator"),
    ProFeature(Icons.Default.Category, "Custom categories", "Add your own categories instead of the 6 built-in ones"),
    ProFeature(Icons.Default.CloudUpload, "Backup & restore", "Export and restore all your data as a file you control"),
    ProFeature(Icons.Default.FamilyRestroom, "Family accounts", "Coming soon")
)

@Composable
fun GoProScreen(vm: MoneyViewModel, onProActivated: () -> Unit) {
    val context = LocalContext.current
    val isPro by vm.isPro.collectAsState()
    val price by vm.proPrice.collectAsState()

    LaunchedEffect(Unit) {
        vm.startBillingConnection()
    }

    // Return to the screen the person was trying to use as soon as Pro is
    // enabled. That destination has already observed isPro and now renders
    // its real interface instead of the lock card.
    LaunchedEffect(isPro) {
        if (isPro) onProActivated()
    }

    Page("Budgetide Pro") {

        if (isPro) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tintedContainer(incomeColor())),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = incomeColor(),
                        modifier = Modifier.size(48.dp)
                    )
                    Text("You're on Budgetide Pro", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Every feature below is unlocked. Thank you for supporting Budgetide.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (com.budgetide.app.BuildConfig.DEBUG) {
                        val devOverride by vm.devProOverride.collectAsState()
                        if (devOverride) {
                            Text(
                                "(Unlocked via Developer options -> Simulate Pro, not a real purchase)",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Text(
                text = "Everything unlocked:",
                style = MaterialTheme.typography.titleMedium
            )
            Card(modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    proFeatures.forEach { feature ->
                        Row {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = incomeColor())
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(feature.title, style = MaterialTheme.typography.titleSmall)
                                Text(feature.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tintedContainer(neutralAccentColor(), amount = 0.20f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = neutralAccentColor(),
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Unlock Budgetide Pro", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "One-time purchase. No subscription, no ads, no expiry.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Button(
                        onClick = {
                            // Sideloaded debug builds cannot complete a Play purchase.
                            // Enable the local preview immediately so every Pro gate
                            // refreshes in-place during development. Release builds
                            // continue into the real Play Billing flow.
                            if (com.budgetide.app.BuildConfig.DEBUG) {
                                vm.enableDevProPreview()
                            } else {
                                (context as? Activity)?.let { vm.launchProPurchase(it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (price != null) "Upgrade for $price - one time" else "Upgrade to Pro"
                        )
                    }

                    TextButton(onClick = { vm.refreshEntitlement() }) {
                        Text("Restore purchase")
                    }
                }
            }
        }

        if (!isPro) {
            Text("Free", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    freeFeatures.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = incomeColor())
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(feature)
                        }
                    }
                }
            }

            Text("Premium", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tintedContainer(neutralAccentColor())),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    proFeatures.forEach { feature ->
                        Row {
                            Icon(feature.icon, contentDescription = null, tint = neutralAccentColor())
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(feature.title, style = MaterialTheme.typography.titleSmall)
                                Text(feature.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Small reusable "this is a Pro feature" lock card, shown in place of gated
 * screens/sections for free users instead of the real content.
 */
@Composable
fun ProLockedCard(
    title: String,
    description: String,
    onUpgradeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tintedContainer(neutralAccentColor(), amount = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = neutralAccentColor(),
                modifier = Modifier.size(36.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onUpgradeClick) {
                Text("Unlock with Pro")
            }
        }
    }
}
