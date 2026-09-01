package com.budgetide.app.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budgetide.app.BuildConfig
import com.budgetide.app.billing.BillingManager
import com.budgetide.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = MoneyDatabase.get(app).dao()

    private val billingManager = BillingManager(app)

    // Debug-only override so you (the developer) can preview every Pro-gated
    // screen without going through a real Play Billing purchase - sideloaded
    // debug builds can't complete real purchases anyway. This flag does not
    // exist in release builds: BuildConfig.DEBUG is false there, so
    // toggleDevProOverride() is a no-op and isPro always reflects the real
    // entitlement from Play Billing.
    private val _devProOverride = MutableStateFlow(false)

    // Eagerly (not WhileSubscribed) is deliberate here: isPro gates screens
    // across the whole app (Goals, Recurring, Warranties, Lending, Backup,
    // calculators, category picker). A WhileSubscribed flow only starts
    // combining its sources once something first collects it, and its
    // cached .value can otherwise lag by a frame right after toggling the
    // dev override or completing a purchase - the same class of staleness
    // bug that caused duplicate seeded goals earlier in this project.
    // Entitlement is cheap to keep hot for the life of the ViewModel, so
    // there's no reason to risk that here.
    val isPro: StateFlow<Boolean> =
        if (BuildConfig.DEBUG) {
            combine(billingManager.isPro, _devProOverride) { real, dev -> real || dev }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)
        } else {
            billingManager.isPro
        }

    val devProOverride: StateFlow<Boolean> = _devProOverride

    fun toggleDevProOverride() {
        if (BuildConfig.DEBUG) {
            _devProOverride.value = !_devProOverride.value
        }
    }

    /** Enables the local Pro preview from the Upgrade button in debug builds. */
    fun enableDevProPreview() {
        if (BuildConfig.DEBUG) {
            _devProOverride.value = true
        }
    }

    val proPrice: StateFlow<String?> = billingManager.productDetails.map { details ->
        details?.oneTimePurchaseOfferDetails?.formattedPrice
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startBillingConnection() {
        billingManager.startConnection()
    }

    fun refreshEntitlement() {
        billingManager.queryExistingPurchases()
    }

    fun launchProPurchase(activity: Activity) {
        billingManager.launchPurchase(activity)
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.endConnection()
    }

    val transactions = dao.transactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = dao.goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recurring = dao.recurring().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val warranties = dao.warranties().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lending = dao.lending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Fixed: seeding now checks the actual DB row count with a one-shot
        // suspend query (or a one-shot first() read of the flow) instead of
        // the StateFlow's ".value", which is still an empty list at this
        // point - it only fills in later once the UI subscribes to it. That
        // race is what caused "Emergency Fund" / "New Laptop" and the other
        // sample rows to be re-inserted on every app start.
        viewModelScope.launch {
            if (dao.transactionCount() == 0) SampleData.transactions.forEach { dao.insertTransaction(it) }
            if (dao.goalCount() == 0) SampleData.goals.forEach { dao.insertGoal(it) }
            if (dao.recurringCount() == 0) SampleData.recurring.forEach { dao.insertRecurring(it) }
            if (dao.warranties().first().isEmpty()) SampleData.warranties.forEach { dao.insertWarranty(it) }
            if (dao.lending().first().isEmpty()) SampleData.lending.forEach { dao.insertLending(it) }
        }
    }

    fun addTransaction(
        title: String,
        amount: Double,
        category: String,
        type: TransactionType,
        essential: Boolean = true,
        dateMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            dao.insertTransaction(TransactionEntity(
                title = title, amount = amount, category = category,
                type = type.name, essential = essential, dateMillis = dateMillis
            ))
        }
    }

    fun deleteTransaction(item: TransactionEntity) {
        viewModelScope.launch { dao.deleteTransaction(item) }
    }

    fun updateTransaction(item: TransactionEntity) {
        viewModelScope.launch { dao.updateTransaction(item) }
    }

    fun addGoal(name: String, target: Double) {
        viewModelScope.launch { dao.insertGoal(GoalEntity(name=name, target=target)) }
    }

    fun addToGoal(goal: GoalEntity, amount: Double) {
        viewModelScope.launch {
            dao.updateGoal(goal.copy(saved = (goal.saved + amount).coerceAtMost(goal.target)))
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch { dao.deleteGoal(goal) }
    }

    /** Full edit (name/target) - keeps whatever is already saved unless the caller changes it too. */
    fun updateGoal(goal: GoalEntity) {
        viewModelScope.launch { dao.updateGoal(goal) }
    }

    fun addRecurring(name: String, amount: Double, category: String, nextDateMillis: Long) {
        viewModelScope.launch {
            dao.insertRecurring(
                RecurringEntity(
                    name=name,
                    amount=amount,
                    category=category,
                    nextDateMillis=nextDateMillis
                )
            )
        }
    }

    fun updateRecurring(item: RecurringEntity) {
        viewModelScope.launch { dao.updateRecurring(item) }
    }

    fun deleteRecurring(item: RecurringEntity) {
        viewModelScope.launch { dao.deleteRecurring(item) }
    }

    fun addWarranty(itemName: String, expiryMillis: Long, note: String = "") {
        viewModelScope.launch {
            dao.insertWarranty(WarrantyEntity(itemName = itemName, expiryMillis = expiryMillis, note = note))
        }
    }

    fun updateWarranty(item: WarrantyEntity) {
        viewModelScope.launch { dao.updateWarranty(item) }
    }

    fun deleteWarranty(item: WarrantyEntity) {
        viewModelScope.launch { dao.deleteWarranty(item) }
    }

    fun addLending(personName: String, amount: Double, direction: LendingDirection, note: String = "") {
        viewModelScope.launch {
            dao.insertLending(
                LendingEntity(personName = personName, amount = amount, direction = direction.name, note = note)
            )
        }
    }

    fun toggleLendingSettled(item: LendingEntity) {
        viewModelScope.launch { dao.updateLending(item.copy(settled = !item.settled)) }
    }

    /** Full edit (person/amount/direction/note) - preserves settled state unless caller changes it. */
    fun updateLending(item: LendingEntity) {
        viewModelScope.launch { dao.updateLending(item) }
    }

    fun deleteLending(item: LendingEntity) {
        viewModelScope.launch { dao.deleteLending(item) }
    }

    // ------------------------------------------------------------
    // Backup & Restore (JSON). Used from the "More" screen together
    // with the Storage Access Framework (CreateDocument/OpenDocument)
    // so no storage permission or FileProvider is needed - the caller
    // gets a content Uri and writes/reads through contentResolver.
    // ------------------------------------------------------------

    suspend fun buildBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val tx = org.json.JSONArray()
        dao.transactions().first().forEach {
            tx.put(org.json.JSONObject().apply {
                put("title", it.title); put("amount", it.amount); put("category", it.category)
                put("type", it.type); put("dateMillis", it.dateMillis); put("note", it.note)
                put("essential", it.essential)
            })
        }
        root.put("transactions", tx)

        val goalsArr = org.json.JSONArray()
        dao.goals().first().forEach {
            goalsArr.put(org.json.JSONObject().apply {
                put("name", it.name); put("target", it.target); put("saved", it.saved)
            })
        }
        root.put("goals", goalsArr)

        val recurringArr = org.json.JSONArray()
        dao.recurring().first().forEach {
            recurringArr.put(org.json.JSONObject().apply {
                put("name", it.name); put("amount", it.amount); put("frequency", it.frequency)
                put("nextDateMillis", it.nextDateMillis); put("category", it.category)
            })
        }
        root.put("recurring", recurringArr)

        val warrantiesArr = org.json.JSONArray()
        dao.warranties().first().forEach {
            warrantiesArr.put(org.json.JSONObject().apply {
                put("itemName", it.itemName); put("purchaseDateMillis", it.purchaseDateMillis)
                put("expiryMillis", it.expiryMillis); put("note", it.note)
            })
        }
        root.put("warranties", warrantiesArr)

        val lendingArr = org.json.JSONArray()
        dao.lending().first().forEach {
            lendingArr.put(org.json.JSONObject().apply {
                put("personName", it.personName); put("amount", it.amount); put("direction", it.direction)
                put("dateMillis", it.dateMillis); put("settled", it.settled); put("note", it.note)
            })
        }
        root.put("lending", lendingArr)

        return root.toString(2)
    }

    /** Replaces ALL local data with the contents of [json]. Throws on malformed input. */
    suspend fun restoreBackupJson(json: String) {
        val root = org.json.JSONObject(json)

        dao.clearTransactions(); dao.clearGoals(); dao.clearRecurring()
        dao.clearWarranties(); dao.clearLending()

        root.optJSONArray("transactions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertTransaction(
                    TransactionEntity(
                        title = o.getString("title"), amount = o.getDouble("amount"),
                        category = o.getString("category"), type = o.getString("type"),
                        dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                        note = o.optString("note", ""), essential = o.optBoolean("essential", true)
                    )
                )
            }
        }

        root.optJSONArray("goals")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertGoal(
                    GoalEntity(name = o.getString("name"), target = o.getDouble("target"), saved = o.optDouble("saved", 0.0))
                )
            }
        }

        root.optJSONArray("recurring")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertRecurring(
                    RecurringEntity(
                        name = o.getString("name"), amount = o.getDouble("amount"),
                        frequency = o.optString("frequency", "Monthly"),
                        nextDateMillis = o.optLong("nextDateMillis", System.currentTimeMillis()),
                        category = o.optString("category", "Bill")
                    )
                )
            }
        }

        root.optJSONArray("warranties")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertWarranty(
                    WarrantyEntity(
                        itemName = o.getString("itemName"),
                        purchaseDateMillis = o.optLong("purchaseDateMillis", System.currentTimeMillis()),
                        expiryMillis = o.getLong("expiryMillis"), note = o.optString("note", "")
                    )
                )
            }
        }

        root.optJSONArray("lending")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertLending(
                    LendingEntity(
                        personName = o.getString("personName"), amount = o.getDouble("amount"),
                        direction = o.getString("direction"),
                        dateMillis = o.optLong("dateMillis", System.currentTimeMillis()),
                        settled = o.optBoolean("settled", false), note = o.optString("note", "")
                    )
                )
            }
        }
    }
}
