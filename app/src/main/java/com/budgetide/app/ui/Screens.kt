package com.budgetide.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.budgetide.app.data.GoalEntity
import com.budgetide.app.data.LendingDirection
import com.budgetide.app.data.LendingEntity
import com.budgetide.app.data.RecurringEntity
import com.budgetide.app.data.TransactionEntity
import com.budgetide.app.data.TransactionType
import com.budgetide.app.data.WarrantyEntity
import com.budgetide.app.viewmodel.MoneyViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

fun pct(part: Double, whole: Double): Double =
    if (whole > 0) (part / whole * 100).coerceIn(0.0, 100.0) else 0.0


// Free tier is limited to these 6 built-in categories; Pro users can type
// any custom category via free text. Keep this list in sync with the
// "6 built-in categories" / "Custom categories" claims in the paywall.
val freeTierCategories = listOf("Food", "Rent", "Travel", "Bills", "Entertainment", "Other")

val dateHeaderFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

/** Google-Pay-style day grouping label: "Today", "Yesterday", or a formatted date. */
fun dayGroupLabel(millis: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(target, today) -> "Today"
        sameDay(target, yesterday) -> "Yesterday"
        else -> dateHeaderFormat.format(Date(millis))
    }
}

/** (year, month) key used to bucket transactions by calendar month. */
fun monthKey(millis: Long): Pair<Int, Int> {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
}


// ============================================================
// MONEY FORMATTER
// ============================================================

fun money(v: Double): String {
    // Fixed to Indian Rupees for every user regardless of device locale/region -
    // using Locale.getDefault() here previously meant a phone set to a US/UK/etc
    // region would show $/£ instead of ₹, which is not what we want.
    val nf = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    nf.maximumFractionDigits = 0
    nf.minimumFractionDigits = 0
    return nf.format(v)
}


// ============================================================
// COMMON PAGE
// ============================================================

@Composable
fun Page(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        content()
    }
}


// ============================================================
// EMPTY STATE
// ============================================================

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


// ============================================================
// MONTH-OVER-MONTH COMPARISON
// "how does this month compare to last month" - a Pro / advanced
// reports feature, consistent with the other analysis screens.
// ============================================================

@Composable
fun MonthComparisonCard(items: List<TransactionEntity>, vm: MoneyViewModel? = null, onUpgradeClick: () -> Unit = {}) {

    val isPro = vm?.isPro?.collectAsState()?.value ?: true

    if (!isPro) {
        ProLockedCard(
            title = "Month-over-month comparison is a Pro feature",
            description = "See how this month's spending compares to last month, automatically.",
            onUpgradeClick = onUpgradeClick
        )
        return
    }

    val now = System.currentTimeMillis()
    val thisMonthKey = monthKey(now)
    val lastMonthKey = monthKey(
        Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }.timeInMillis
    )

    val thisMonthExpense = items
        .filter { it.type == "EXPENSE" && monthKey(it.dateMillis) == thisMonthKey }
        .sumOf { it.amount }

    val lastMonthExpense = items
        .filter { it.type == "EXPENSE" && monthKey(it.dateMillis) == lastMonthKey }
        .sumOf { it.amount }

    val changePct = if (lastMonthExpense > 0) {
        (thisMonthExpense - lastMonthExpense) / lastMonthExpense * 100
    } else null

    val spendingUp = changePct != null && changePct > 0
    // Spending less than last month is the "good" direction, so colour by
    // that - not by income/expense meaning.
    val trendColor = when {
        changePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        spendingUp -> expenseColor()
        else -> incomeColor()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tintedContainer(neutralAccentColor())),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("This month vs last month", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("This month", style = MaterialTheme.typography.labelMedium)
                    Text(money(thisMonthExpense), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Last month", style = MaterialTheme.typography.labelMedium)
                    Text(money(lastMonthExpense), style = MaterialTheme.typography.titleLarge)
                }
            }

            Text(
                text = when {
                    changePct == null -> "No spending recorded last month to compare against."
                    spendingUp -> "Spending is up %.0f%% from last month.".format(changePct)
                    else -> "Spending is down %.0f%% from last month.".format(-changePct)
                },
                color = trendColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


// ============================================================
// DONUT CHART
// Simple category breakdown chart drawn with Canvas - no chart
// library needed. Animates in on first composition.
// ============================================================

@Composable
fun DonutChart(
    slices: List<Triple<String, Double, Color>>,
    total: Double,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "donut"
    )

    var selected by remember(slices) { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices, total) {
                    detectTapGestures { offset ->
                        if (total <= 0 || slices.isEmpty()) return@detectTapGestures

                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)

                        // size here is IntSize (from PointerInputScope), which has no
                        // .minDimension - that property only exists on the float Size
                        // type used inside DrawScope. Compute it manually instead.
                        val minDim = minOf(size.width, size.height).toFloat()
                        val outerRadius = minDim / 2f
                        val strokeWidth = minDim * 0.22f
                        val innerRadius = outerRadius - strokeWidth

                        if (dist < innerRadius || dist > outerRadius) {
                            selected = null
                            return@detectTapGestures
                        }

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = ((angle + 90f) % 360f + 360f) % 360f

                        var cumulative = 0f
                        var hit: Int? = null
                        for ((index, slice) in slices.withIndex()) {
                            val sweep = (slice.second / total * 360.0).toFloat()
                            if (angle >= cumulative && angle < cumulative + sweep) {
                                hit = index
                                break
                            }
                            cumulative += sweep
                        }
                        selected = if (selected == hit) null else hit
                    }
                }
        ) {
            val strokeWidth = size.minDimension * 0.22f
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            var startAngle = -90f
            if (total > 0) {
                slices.forEachIndexed { index, (_, amount, color) ->
                    val sweep = (amount / total * 360.0).toFloat() * animatedProgress
                    val isDimmed = selected != null && selected != index
                    drawArc(
                        color = if (isDimmed) color.copy(alpha = 0.35f) else color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = if (selected == index) strokeWidth * 1.15f else strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Butt
                        )
                    )
                    startAngle += sweep
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val sel = selected?.let { slices.getOrNull(it) }
            if (sel != null) {
                val (name, amt, color) = sel
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
                Text(
                    text = money(amt),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "%.0f%% of spend".format(if (total > 0) amt / total * 100 else 0.0),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = money(total),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tap a slice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


// ============================================================
// DASHBOARD
// ============================================================

@Composable
fun DashboardScreen(vm: MoneyViewModel) {

    val items by vm.transactions.collectAsState()

    // --------------------------------------------------------
    // Income / Expense calculations
    // --------------------------------------------------------

    val income = items
        .filter {
            it.type == TransactionType.INCOME.name
        }
        .sumOf {
            it.amount
        }

    val expense = items
        .filter {
            it.type == TransactionType.EXPENSE.name
        }
        .sumOf {
            it.amount
        }

    val balance = income - expense

    // --------------------------------------------------------
    // Where did my money go? (category breakdown, expenses only)
    // --------------------------------------------------------

    val byCategory = items
        .filter { it.type == TransactionType.EXPENSE.name }
        .groupBy { it.category }
        .map { (cat, list) -> cat to list.sumOf { it.amount } }
        .sortedByDescending { it.second }

    // --------------------------------------------------------
    // Needs vs wants: how much is going to unnecessary things
    // --------------------------------------------------------

    val essentialSpend = items
        .filter { it.type == TransactionType.EXPENSE.name && it.essential }
        .sumOf { it.amount }

    val discretionarySpend = items
        .filter { it.type == TransactionType.EXPENSE.name && !it.essential }
        .sumOf { it.amount }

    val savingsRate =
        if (income > 0) {
            balance / income * 100
        } else {
            0.0
        }


    // --------------------------------------------------------
    // Financial health score
    // --------------------------------------------------------

    val score = when {
        savingsRate >= 30 -> 90
        savingsRate >= 20 -> 80
        savingsRate >= 10 -> 70
        else -> 55
    }


    // --------------------------------------------------------
    // --------------------------------------------------------
    // Dashboard
    // --------------------------------------------------------

    Page("Budgetide") {

        // ----------------------------------------------------
        // Welcome text
        // ----------------------------------------------------

        Text(
            text = "Your money, your decisions.",
            style = MaterialTheme.typography.titleMedium
        )


        // ----------------------------------------------------
        // AVAILABLE BALANCE
        // ----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(if (balance >= 0) incomeColor() else expenseColor(), amount = 0.18f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Available balance",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    text = money(balance),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (balance >= 0) incomeColor() else expenseColor()
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Savings rate: %.1f%%".format(savingsRate)
                )
            }
        }


        // ----------------------------------------------------
        // INCOME / EXPENSE
        // ----------------------------------------------------

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            StatCard(
                label = "Income",
                value = money(income),
                modifier = Modifier.weight(1f),
                valueColor = incomeColor()
            )

            StatCard(
                label = "Expenses",
                value = money(expense),
                modifier = Modifier.weight(1f),
                valueColor = expenseColor()
            )
        }


        // ----------------------------------------------------
        // WHERE DID MY MONEY GO
        // ----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(neutralAccentColor())
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    text = "Where did my money go?",
                    style = MaterialTheme.typography.titleMedium
                )

                if (byCategory.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "No expenses yet",
                        subtitle = "Add a transaction to see where your money goes."
                    )
                } else {
                    DonutChart(
                        slices = byCategory.take(8).map { (cat, amt) -> Triple(cat, amt, categoryColor(cat)) },
                        total = expense,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(180.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    byCategory.take(8).forEach { (cat, amt) ->
                        val share = pct(amt, expense)
                        val dotColor = categoryColor(cat)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(dotColor, shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = cat)
                            }
                            Text(text = "${money(amt)} · %.0f%%".format(share))
                        }
                    }
                }
            }
        }


        // ----------------------------------------------------
        // NEEDS VS WANTS (unnecessary spending)
        // ----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(warningColor())
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Necessary vs unnecessary spending",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Necessary: ${money(essentialSpend)}  ·  Unnecessary: ${money(discretionarySpend)}"
                )

                val discretionaryShare = pct(discretionarySpend, expense)

                LinearProgressIndicator(
                    progress = { discretionaryShare.toFloat() / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = warningColor()
                )

                Text(
                    text =
                        if (expense <= 0.0) {
                            "Log some expenses to see this breakdown."
                        } else {
                            "%.0f%% of your spending is on non-essential items.".format(discretionaryShare)
                        },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }


        // ----------------------------------------------------
        // FINANCIAL HEALTH
        // ----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(
                    when {
                        score >= 80 -> incomeColor()
                        score >= 70 -> warningColor()
                        else -> expenseColor()
                    }
                )
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Financial health",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "$score / 100",
                    style = MaterialTheme.typography.headlineMedium,
                    color = when {
                        score >= 80 -> incomeColor()
                        score >= 70 -> warningColor()
                        else -> expenseColor()
                    }
                )

                Text(
                    text =
                        if (score >= 80) {
                            "Excellent saving discipline."
                        } else {
                            "There is room to improve your savings."
                        }
                )
            }
        }


        // ----------------------------------------------------
        // CURRENT SAVINGS INSIGHT
        // ----------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(incomeColor())
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    text = "Budgetide Insight",
                    style = MaterialTheme.typography.titleMedium
                )

                when {

                    savingsRate >= 30 -> {
                        Text(
                            "🔥 Excellent! You are currently saving more than 30% of your income."
                        )
                    }

                    savingsRate >= 20 -> {
                        Text(
                            "👍 Good progress! Try to gradually increase your savings."
                        )
                    }

                    savingsRate >= 10 -> {
                        Text(
                            "💡 You are saving, but there is room to build a stronger financial cushion."
                        )
                    }

                    else -> {
                        Text(
                            "⚠️ Your current savings are low. Review your expenses and commitments."
                        )
                    }
                }
            }
        }
    }
}


// ============================================================
// STAT CARD
// ============================================================

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier,
    valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (valueColor != Color.Unspecified) tintedContainer(valueColor) else CardDefaults.cardColors().containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor
            )
        }
    }
}


// ============================================================
// TRANSACTIONS SCREEN
// ============================================================

@Composable
fun TransactionsScreen(vm: MoneyViewModel, onUpgradeClick: () -> Unit = {}) {

    val items by vm.transactions.collectAsState()

    var show by remember {
        mutableStateOf(false)
    }

    var editing by remember {
        mutableStateOf<TransactionEntity?>(null)
    }


    Page("Transactions") {

        Button(
            onClick = {
                show = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            Text("Add transaction")
        }


        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Default.ReceiptLong,
                title = "No transactions yet",
                subtitle = "Tap \"Add transaction\" to log your first income or expense."
            )
        } else {
            MonthComparisonCard(items = items, vm = vm, onUpgradeClick = onUpgradeClick)
        }

        // Grouped Google-Pay-style: a date header ("Today", "Yesterday", or
        // the full date) followed by that day's transactions. groupBy keeps
        // insertion order, and items() is already sorted newest-first by the
        // DAO query, so groups come out newest-day-first automatically.
        val grouped = items.groupBy { dayGroupLabel(it.dateMillis) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {

            grouped.forEach { (label, dayItems) ->

                item(key = "header_$label") {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(dayItems, key = { it.id }) { t ->

                    TransactionRow(
                        t = t,
                        onEdit = {
                            editing = t
                        },
                        onDelete = {
                            vm.deleteTransaction(t)
                        }
                    )
                }
            }
        }
    }


    if (show) {

        AddTransactionDialog(vm, existing = null) {
            show = false
        }
    }

    editing?.let { t ->
        AddTransactionDialog(vm, existing = t) {
            editing = null
        }
    }
}


// ============================================================
// TRANSACTION ROW
// ============================================================

@Composable
fun TransactionRow(
    t: TransactionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = tintedContainer(if (t.type == "INCOME") incomeColor() else expenseColor(), amount = 0.16f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = t.title,
                    style = MaterialTheme.typography.titleMedium
                )

                Row {
                    Text(
                        text = t.category,
                        color = categoryColor(t.category)
                    )
                    Text(
                        text = "  ·  ${timeFormat.format(Date(t.dateMillis))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }


            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    text =
                        (if (t.type == "INCOME") "+" else "-") +
                                money(t.amount),
                    color = if (t.type == "INCOME") incomeColor() else expenseColor(),
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(
                    onClick = onEdit
                ) {

                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit"
                    )
                }

                IconButton(
                    onClick = onDelete
                ) {

                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete"
                    )
                }
            }
        }
    }
}


// ============================================================
// ADD / EDIT TRANSACTION DIALOG
// existing == null -> add mode. existing != null -> edit mode,
// pre-filled and updating that row in place instead of inserting.
// ============================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
    vm: MoneyViewModel,
    existing: TransactionEntity? = null,
    close: () -> Unit
) {

    var title by remember {
        mutableStateOf(existing?.title ?: "")
    }

    var amount by remember {
        mutableStateOf(existing?.amount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "")
    }

    var category by remember {
        mutableStateOf(existing?.category ?: "Other")
    }

    var type by remember {
        mutableStateOf(existing?.type?.let { TransactionType.valueOf(it) } ?: TransactionType.EXPENSE)
    }

    var essential by remember {
        mutableStateOf(existing?.essential ?: true)
    }

    var dateMillis by remember {
        mutableStateOf(existing?.dateMillis ?: System.currentTimeMillis())
    }

    val isPro by vm.isPro.collectAsState()

    val context = LocalContext.current

    fun openDatePicker() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = java.util.Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                dateMillis = picked.timeInMillis
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            // Can't log a transaction in the future.
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }


    AlertDialog(

        onDismissRequest = close,

        title = {
            Text(if (existing == null) "Add transaction" else "Edit transaction")
        },

        text = {

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text("Title")
                    }
                )


                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                    },
                    label = {
                        Text("Amount")
                    }
                )


                OutlinedButton(
                    onClick = { openDatePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateHeaderFormat.format(Date(dateMillis)))
                }


                if (isPro) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {
                            category = it
                        },
                        label = {
                            Text("Category")
                        }
                    )
                } else {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge
                    )

                    val chipOptions = remember(category) {
                        if (category in freeTierCategories) freeTierCategories
                        else freeTierCategories + category
                    }

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chipOptions.forEach { option ->
                            FilterChip(
                                selected = category == option,
                                onClick = { category = option },
                                label = { Text(option) }
                            )
                        }
                    }

                    Text(
                        text = "Unlock Pro for custom categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                Row {

                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = {
                            type = TransactionType.EXPENSE
                        },
                        label = {
                            Text("Expense")
                        }
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = {
                            type = TransactionType.INCOME
                        },
                        label = {
                            Text("Income")
                        }
                    )
                }

                if (type == TransactionType.EXPENSE) {

                    Row {

                        FilterChip(
                            selected = essential,
                            onClick = {
                                essential = true
                            },
                            label = {
                                Text("Necessary")
                            }
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        FilterChip(
                            selected = !essential,
                            onClick = {
                                essential = false
                            },
                            label = {
                                Text("Unnecessary")
                            }
                        )
                    }
                }
            }
        },


        confirmButton = {

            Button(
                onClick = {

                    val a = amount.toDoubleOrNull()

                    if (
                        title.isNotBlank() &&
                        a != null &&
                        a > 0
                    ) {

                        if (existing == null) {
                            vm.addTransaction(
                                title,
                                a,
                                category,
                                type,
                                essential,
                                dateMillis
                            )
                        } else {
                            vm.updateTransaction(
                                existing.copy(
                                    title = title,
                                    amount = a,
                                    category = category,
                                    type = type.name,
                                    essential = essential,
                                    dateMillis = dateMillis
                                )
                            )
                        }

                        close()
                    }
                }
            ) {

                Text("Save")
            }
        },


        dismissButton = {

            TextButton(
                onClick = close
            ) {

                Text("Cancel")
            }
        }
    )
}


// ============================================================
// CALCULATOR SCREEN
// ============================================================

@Composable
fun CalculatorScreen(vm: MoneyViewModel, onUpgradeClick: () -> Unit = {}) {

    var income by remember {
        mutableStateOf("50000")
    }

    var essential by remember {
        mutableStateOf("25000")
    }

    var other by remember {
        mutableStateOf("5000")
    }

    var purchase by remember {
        mutableStateOf("20000")
    }

    var monthlySave by remember {
        mutableStateOf("10000")
    }

    var years by remember {
        mutableStateOf("5")
    }

    var rate by remember {
        mutableStateOf("8")
    }

    // "What happens if my salary decreases?" scenario simulator
    var decreasePct by remember {
        mutableStateOf("20")
    }


    val i =
        income.toDoubleOrNull() ?: 0.0

    val e =
        essential.toDoubleOrNull() ?: 0.0

    val o =
        other.toDoubleOrNull() ?: 0.0

    val p =
        purchase.toDoubleOrNull() ?: 0.0


    val safe =
        (i - e - o)
            .coerceAtLeast(0.0) * 0.5


    val monthly =
        monthlySave.toDoubleOrNull() ?: 0.0

    val n =
        (years.toDoubleOrNull() ?: 0.0) * 12

    val r =
        (rate.toDoubleOrNull() ?: 0.0) / 1200


    val future =
        if (r > 0) {

            monthly *
                    ((1 + r).pow(n) - 1) /
                    r

        } else {

            monthly * n
        }

    val decrease = (decreasePct.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val reducedIncome = i * (1 - decrease / 100)
    val currentLeftover = (i - e - o)
    val reducedLeftover = (reducedIncome - e - o)
    val reducedMonthlySave = reducedLeftover.coerceAtLeast(0.0) * 0.5
    val reducedFuture =
        if (r > 0) {
            reducedMonthlySave * ((1 + r).pow(n) - 1) / r
        } else {
            reducedMonthlySave * n
        }


    Page("Money Calculators") {

        Text(
            text = "⚠️ These are simplified educational estimates, not regulated financial " +
                    "or investment advice. Actual returns, taxes, and loan terms will differ.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        // ----------------------------------------------------
        // CAN I AFFORD IT?
        // ----------------------------------------------------

        Text(
            text = "Can I afford it?",
            style = MaterialTheme.typography.titleLarge
        )


        OutlinedTextField(
            value = income,
            onValueChange = {
                income = it
            },
            label = {
                Text("Monthly income")
            },
            modifier = Modifier.fillMaxWidth()
        )


        OutlinedTextField(
            value = essential,
            onValueChange = {
                essential = it
            },
            label = {
                Text("Essential expenses")
            },
            modifier = Modifier.fillMaxWidth()
        )


        OutlinedTextField(
            value = other,
            onValueChange = {
                other = it
            },
            label = {
                Text("Other commitments")
            },
            modifier = Modifier.fillMaxWidth()
        )


        OutlinedTextField(
            value = purchase,
            onValueChange = {
                purchase = it
            },
            label = {
                Text("Purchase price")
            },
            modifier = Modifier.fillMaxWidth()
        )


        Text(
            text =
                if (p <= safe && p > 0) {
                    "✅ Comfortable: within your suggested discretionary budget."
                } else {
                    "⚠️ Caution: this purchase is above your suggested discretionary budget."
                },
            style = MaterialTheme.typography.titleMedium
        )


        Text(
            text = "Suggested discretionary budget: ${money(safe)}"
        )


        HorizontalDivider()


        // ----------------------------------------------------
        // FUTURE SAVINGS CALCULATOR
        // ----------------------------------------------------

        Text(
            text = "Future savings",
            style = MaterialTheme.typography.titleLarge
        )


        OutlinedTextField(
            value = monthlySave,
            onValueChange = {
                monthlySave = it
            },
            label = {
                Text("Monthly saving")
            },
            modifier = Modifier.fillMaxWidth()
        )


        OutlinedTextField(
            value = years,
            onValueChange = {
                years = it
            },
            label = {
                Text("Years")
            },
            modifier = Modifier.fillMaxWidth()
        )


        OutlinedTextField(
            value = rate,
            onValueChange = {
                rate = it
            },
            label = {
                Text("Annual return % (assumption)")
            },
            modifier = Modifier.fillMaxWidth()
        )


        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = tintedContainer(neutralAccentColor())
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Projected value",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    text = money(future),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }


        HorizontalDivider()


        // ----------------------------------------------------
        // WHAT IF MY SALARY DECREASES? (Pro feature)
        // ----------------------------------------------------

        val isPro by vm.isPro.collectAsState()

        if (isPro) {

            Text(
                text = "What if my salary decreases?",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Uses the monthly income, essential expenses and other commitments entered above.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = decreasePct,
                onValueChange = {
                    decreasePct = it
                },
                label = {
                    Text("Salary decrease (%)")
                },
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = tintedContainer(if (reducedLeftover < 0) expenseColor() else incomeColor())
                ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Income after cut")
                        Text(money(reducedIncome))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Leftover today")
                        Text(money(currentLeftover))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Leftover after cut")
                        Text(money(reducedLeftover))
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${years.ifBlank { "5" }}-year projection now")
                        Text(money(future))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Same projection after cut")
                        Text(money(reducedFuture))
                    }

                    Text(
                        text =
                            run {
                                val decreaseStr = "%.0f".format(decrease)
                                if (reducedLeftover < 0) {
                                    "⚠️ A $decreaseStr% pay cut would leave you unable to cover essentials and commitments — you'd be short by ${money(-reducedLeftover)}/month."
                                } else {
                                    "A $decreaseStr% pay cut still leaves ${money(reducedLeftover)}/month after essentials and commitments."
                                }
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reducedLeftover < 0) expenseColor() else incomeColor()
                    )
                }
            }
        } else {
            ProLockedCard(
                title = "Advanced analysis is a Pro feature",
                description = "See exactly what a pay cut would mean for your monthly budget and long-term savings.",
                onUpgradeClick = onUpgradeClick
            )
        }
    }
}


// ============================================================
// GOALS SCREEN
// ============================================================

@Composable
fun GoalsScreen(vm: MoneyViewModel) {

    val goals by vm.goals.collectAsState()

    var show by remember {
        mutableStateOf(false)
    }

    var editing by remember {
        mutableStateOf<GoalEntity?>(null)
    }


    Page("Goals") {

        Button(
            onClick = {
                show = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Add savings goal")
        }


        if (goals.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Flag,
                title = "No goals yet",
                subtitle = "Set a savings goal like an emergency fund or a big purchase."
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 500.dp)
        ) {

            items(goals, key = { it.id }) { g ->

                val goalPct = pct(g.saved, g.target)

                var addAmount by remember(g.id) {
                    mutableStateOf("")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = tintedContainer(if (goalPct >= 100.0) incomeColor() else neutralAccentColor())
                    ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Text(
                                text = g.name,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Row {
                                IconButton(
                                    onClick = {
                                        editing = g
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit goal"
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        vm.deleteGoal(g)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete goal"
                                    )
                                }
                            }
                        }


                        Text(
                            text = "${money(g.saved)} of ${money(g.target)}"
                        )

                        val animatedGoalProgress by animateFloatAsState(
                            targetValue = (goalPct.toFloat() / 100f),
                            animationSpec = tween(durationMillis = 500),
                            label = "goalProgress"
                        )

                        LinearProgressIndicator(
                            progress = {
                                animatedGoalProgress
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (goalPct >= 100.0) incomeColor() else neutralAccentColor()
                        )


                        Text(
                            text = "%.0f%% complete".format(goalPct),
                            color = if (goalPct >= 100.0) incomeColor() else Color.Unspecified
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            OutlinedTextField(
                                value = addAmount,
                                onValueChange = {
                                    addAmount = it
                                },
                                label = {
                                    Text("Add amount")
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    addAmount.toDoubleOrNull()?.let {
                                        if (it > 0) {
                                            vm.addToGoal(g, it)
                                            addAmount = ""
                                        }
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }
    }


    if (show) {
        GoalDialog(vm = vm, existing = null) {
            show = false
        }
    }

    editing?.let { g ->
        GoalDialog(vm = vm, existing = g) {
            editing = null
        }
    }
}


// ============================================================
// ADD / EDIT GOAL DIALOG
// existing == null -> add mode. existing != null -> edit mode.
// ============================================================

@Composable
fun GoalDialog(vm: MoneyViewModel, existing: GoalEntity?, close: () -> Unit) {

    var name by remember {
        mutableStateOf(existing?.name ?: "")
    }

    var target by remember {
        mutableStateOf(existing?.target?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "")
    }


    AlertDialog(

        onDismissRequest = close,

        title = {
            Text(if (existing == null) "New goal" else "Edit goal")
        },

        text = {

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = {
                        Text("Goal name")
                    }
                )


                OutlinedTextField(
                    value = target,
                    onValueChange = {
                        target = it
                    },
                    label = {
                        Text("Target amount")
                    }
                )
            }
        },


        confirmButton = {

            Button(
                onClick = {

                    target
                        .toDoubleOrNull()
                        ?.let { t ->

                            if (name.isNotBlank()) {
                                if (existing == null) {
                                    vm.addGoal(name, t)
                                } else {
                                    vm.updateGoal(existing.copy(name = name, target = t))
                                }
                            }
                        }

                    close()
                }
            ) {

                Text("Save")
            }
        },


        dismissButton = {

            TextButton(
                onClick = close
            ) {

                Text("Cancel")
            }
        }
    )
}