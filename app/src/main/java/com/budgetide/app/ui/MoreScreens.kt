package com.budgetide.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.budgetide.app.data.LendingDirection
import com.budgetide.app.data.LendingEntity
import com.budgetide.app.data.RecurringEntity
import com.budgetide.app.data.WarrantyEntity
import com.budgetide.app.viewmodel.MoneyViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

// ============================================================
// MORE HUB — recurring/EMIs, warranties, lending all live here
// so the bottom nav doesn't get overcrowded.
// ============================================================

@Composable
fun MoreScreen(vm: MoneyViewModel, onOpen: (String) -> Unit) {

    val recurring by vm.recurring.collectAsState()
    val warranties by vm.warranties.collectAsState()
    val lending by vm.lending.collectAsState()

    val isPro by vm.isPro.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = vm.buildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                status = "Backup saved."
            } catch (e: Exception) {
                status = "Export failed: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    vm.restoreBackupJson(json)
                    status = "Backup restored. All local data was replaced."
                } else {
                    status = "Couldn't read that file."
                }
            } catch (e: Exception) {
                status = "Restore failed: ${e.message}"
            }
        }
    }

    val monthlyRecurring = recurring.sumOf { it.amount }
    val monthlyEmi = recurring.filter { it.category == "EMI" }.sumOf { it.amount }
    val theyOweYou = lending.filter { it.direction == "LENT" && !it.settled }.sumOf { it.amount }
    val youOweThem = lending.filter { it.direction == "BORROWED" && !it.settled }.sumOf { it.amount }
    val expiringSoon = warranties.count {
        it.expiryMillis - System.currentTimeMillis() in 0..(TimeUnit.DAYS.toMillis(30))
    }

    Page("More") {

        MoreLinkCard(
            title = "Recurring payments & EMIs",
            subtitle = "${recurring.size} active · ${money(monthlyRecurring)}/mo · EMI outflow ${money(monthlyEmi)}/mo",
            icon = Icons.Default.Repeat,
            accent = neutralAccentColor(),
            onClick = { onOpen("recurring") }
        )

        MoreLinkCard(
            title = "Warranties",
            subtitle = if (expiringSoon > 0) "$expiringSoon expiring within 30 days" else "${warranties.size} tracked",
            icon = Icons.Default.Shield,
            accent = if (expiringSoon > 0) warningColor() else incomeColor(),
            onClick = { onOpen("warranties") }
        )

        MoreLinkCard(
            title = "Who owes me money?",
            subtitle = "Owed to you ${money(theyOweYou)} · You owe ${money(youOweThem)}",
            icon = Icons.Default.People,
            accent = incomeColor(),
            onClick = { onOpen("lending") }
        )

        // ----------------------------------------------------
        // BACKUP & RESTORE (Pro feature)
        // ----------------------------------------------------

        if (isPro) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tintedContainer(neutralAccentColor())),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "All your data lives only on this device. Export a backup file before " +
                                "uninstalling, switching phones, or clearing app data.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("budgetide_backup.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Export backup",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Restore backup",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            ProLockedCard(
                title = "Backup & restore is a Pro feature",
                description = "Export and restore all your data as a file you control.",
                onUpgradeClick = { onOpen("go_pro") }
            )
        }

        Text(
            text = "Budgetide is a personal budgeting tool. Calculators show simplified " +
                    "educational estimates, not regulated financial advice.",
            style = MaterialTheme.typography.bodySmall
        )

        // Debug-only: lets you preview every Pro-gated screen without a real
        // Play Billing purchase. BuildConfig.DEBUG is false in release
        // builds, so this section (and the override itself) never appears
        // on a Play Store build - real users never see it.
        if (com.budgetide.app.BuildConfig.DEBUG) {
            val devOverride by vm.devProOverride.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Developer options", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Simulate Pro (testing only, debug builds only)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = devOverride,
                        onCheckedChange = { vm.toggleDevProOverride() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreLinkCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = tintedContainer(accent, amount = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = accent)
        }
    }
}

// ============================================================
// RECURRING PAYMENTS & EMIs
// "What recurring payments do I have?" / "How much am I losing to EMIs?"
// ============================================================

@Composable
fun RecurringScreen(vm: MoneyViewModel) {

    val recurring by vm.recurring.collectAsState()
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }

    val monthlyTotal = recurring.sumOf { it.amount }
    val monthlyEmi = recurring.filter { it.category == "EMI" }.sumOf { it.amount }

    Page("Recurring & EMIs") {

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = tintedContainer(expenseColor())),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Total monthly recurring outflow", style = MaterialTheme.typography.labelLarge)
                Text(money(monthlyTotal), style = MaterialTheme.typography.headlineMedium, color = expenseColor())
                Text("Of which EMIs: ${money(monthlyEmi)}/month", color = warningColor())
            }
        }

        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add recurring payment / EMI")
        }

        if (recurring.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Repeat,
                title = "No recurring payments yet",
                subtitle = "Track bills, subscriptions, and EMIs so you always know what's due."
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            items(recurring, key = { it.id }) { r ->
                val accent = if (r.category == "EMI") warningColor() else neutralAccentColor()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = tintedContainer(accent, amount = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${r.category} · ${r.frequency} · next ${dateFmt.format(Date(r.nextDateMillis))}",
                                color = accent
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = money(r.amount), color = expenseColor())
                            IconButton(onClick = { editing = r }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { vm.deleteRecurring(r) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (show) {
        AddRecurringDialog(vm, existing = null) { show = false }
    }

    editing?.let { r ->
        AddRecurringDialog(vm, existing = r) { editing = null }
    }
}

@Composable
private fun AddRecurringDialog(vm: MoneyViewModel, existing: RecurringEntity? = null, close: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Bill") }
    var nextDateMillis by remember { mutableStateOf(existing?.nextDateMillis ?: System.currentTimeMillis()) }

    val context = LocalContext.current

    fun openDatePicker() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nextDateMillis }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                nextDateMillis = picked.timeInMillis
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "Add recurring payment" else "Edit recurring payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly amount") }
                )
                Row {
                    listOf("Bill", "Subscription", "EMI").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { openDatePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (category == "EMI") "Deduction date: ${dateFmt.format(Date(nextDateMillis))}"
                        else "Next due date: ${dateFmt.format(Date(nextDateMillis))}"
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (name.isNotBlank() && a != null && a > 0) {
                    if (existing == null) {
                        vm.addRecurring(name, a, category, nextDateMillis)
                    } else {
                        vm.updateRecurring(existing.copy(name = name, amount = a, category = category, nextDateMillis = nextDateMillis))
                    }
                    close()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        }
    )
}

// ============================================================
// WARRANTIES
// "What warranties do I have?"
// ============================================================

@Composable
fun WarrantiesScreen(vm: MoneyViewModel) {

    val warranties by vm.warranties.collectAsState()
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WarrantyEntity?>(null) }

    Page("Warranties") {

        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add warranty")
        }

        if (warranties.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Shield,
                title = "No warranties tracked",
                subtitle = "Add one so you never miss a return or claim window."
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            items(warranties, key = { it.id }) { w ->

                val daysLeft = TimeUnit.MILLISECONDS.toDays(w.expiryMillis - System.currentTimeMillis())
                val expired = daysLeft < 0
                val expiringSoon = !expired && daysLeft <= 30
                val statusColor = when {
                    expired -> expenseColor()
                    expiringSoon -> warningColor()
                    else -> incomeColor()
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = tintedContainer(statusColor, amount = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = w.itemName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text =
                                    if (expired) "Expired ${dateFmt.format(Date(w.expiryMillis))}"
                                    else "Expires ${dateFmt.format(Date(w.expiryMillis))} (${daysLeft}d left)",
                                color = statusColor
                            )
                            if (w.note.isNotBlank()) Text(text = w.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            IconButton(onClick = { editing = w }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { vm.deleteWarranty(w) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (show) {
        AddWarrantyDialog(vm, existing = null) { show = false }
    }

    editing?.let { w ->
        AddWarrantyDialog(vm, existing = w) { editing = null }
    }
}

@Composable
private fun AddWarrantyDialog(vm: MoneyViewModel, existing: WarrantyEntity? = null, close: () -> Unit) {
    var itemName by remember { mutableStateOf(existing?.itemName ?: "") }
    var expiryMillis by remember {
        mutableStateOf(existing?.expiryMillis ?: (System.currentTimeMillis() + 365L * 86400000L))
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val context = LocalContext.current

    fun openDatePicker() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = expiryMillis }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                expiryMillis = picked.timeInMillis
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "Add warranty" else "Edit warranty") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item name") }
                )
                OutlinedButton(
                    onClick = { openDatePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Expires: ${dateFmt.format(Date(expiryMillis))}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (itemName.isNotBlank()) {
                    if (existing == null) {
                        vm.addWarranty(itemName, expiryMillis, note)
                    } else {
                        vm.updateWarranty(existing.copy(itemName = itemName, expiryMillis = expiryMillis, note = note))
                    }
                    close()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        }
    )
}

// ============================================================
// LENDING — "Who owes me money?"
// ============================================================

@Composable
fun LendingScreen(vm: MoneyViewModel) {

    val lending by vm.lending.collectAsState()
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LendingEntity?>(null) }

    val theyOweYou = lending.filter { it.direction == "LENT" && !it.settled }.sumOf { it.amount }
    val youOweThem = lending.filter { it.direction == "BORROWED" && !it.settled }.sumOf { it.amount }

    Page("Who owes me money?") {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(label = "Owed to you", value = money(theyOweYou), modifier = Modifier.weight(1f), valueColor = incomeColor())
            StatCard(label = "You owe", value = money(youOweThem), modifier = Modifier.weight(1f), valueColor = expenseColor())
        }

        Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add entry")
        }

        if (lending.isEmpty()) {
            EmptyState(
                icon = Icons.Default.People,
                title = "Nothing tracked yet",
                subtitle = "Add money you've lent or borrowed so it doesn't get forgotten."
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            items(lending, key = { it.id }) { l ->
                val dirColor = if (l.direction == "LENT") incomeColor() else expenseColor()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = tintedContainer(dirColor, amount = 0.16f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = l.personName,
                                style = MaterialTheme.typography.titleMedium,
                                textDecoration = if (l.settled) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Text(
                                text =
                                    (if (l.direction == "LENT") "They owe you " else "You owe them ") +
                                            money(l.amount) +
                                            if (l.settled) " · settled" else "",
                                color = if (l.settled) Color.Unspecified else dirColor
                            )
                            if (l.note.isNotBlank()) Text(text = l.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = { vm.toggleLendingSettled(l) }) {
                                Text(if (l.settled) "Reopen" else "Settle")
                            }
                            IconButton(onClick = { editing = l }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { vm.deleteLending(l) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (show) {
        AddLendingDialog(vm, existing = null) { show = false }
    }

    editing?.let { l ->
        AddLendingDialog(vm, existing = l) { editing = null }
    }
}

@Composable
private fun AddLendingDialog(vm: MoneyViewModel, existing: LendingEntity? = null, close: () -> Unit) {
    var personName by remember { mutableStateOf(existing?.personName ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var direction by remember { mutableStateOf(existing?.direction?.let { LendingDirection.valueOf(it) } ?: LendingDirection.LENT) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "Add entry" else "Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person") }
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") }
                )
                Row {
                    FilterChip(
                        selected = direction == LendingDirection.LENT,
                        onClick = { direction = LendingDirection.LENT },
                        label = { Text("They owe you") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = direction == LendingDirection.BORROWED,
                        onClick = { direction = LendingDirection.BORROWED },
                        label = { Text("You owe them") }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = amount.toDoubleOrNull()
                if (personName.isNotBlank() && a != null && a > 0) {
                    if (existing == null) {
                        vm.addLending(personName, a, direction, note)
                    } else {
                        vm.updateLending(existing.copy(personName = personName, amount = a, direction = direction.name, note = note))
                    }
                    close()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        }
    )
}
