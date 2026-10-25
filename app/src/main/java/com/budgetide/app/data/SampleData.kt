package com.budgetide.app.data

object SampleData {
    val transactions = listOf(
        TransactionEntity(title="Salary", amount=CurrencyUtil.fromInr(50000.0), category="Salary", type="INCOME", essential=true),
        TransactionEntity(title="Rent", amount=CurrencyUtil.fromInr(12000.0), category="Rent", type="EXPENSE", essential=true),
        TransactionEntity(title="Groceries", amount=CurrencyUtil.fromInr(4800.0), category="Food", type="EXPENSE", essential=true),
        TransactionEntity(title="Fuel", amount=CurrencyUtil.fromInr(2500.0), category="Travel", type="EXPENSE", essential=true),
        TransactionEntity(title="Internet", amount=CurrencyUtil.fromInr(999.0), category="Bills", type="EXPENSE", essential=true),
        TransactionEntity(title="Movie", amount=CurrencyUtil.fromInr(600.0), category="Entertainment", type="EXPENSE", essential=false)
    )
    val goals = listOf(
        GoalEntity(name="Emergency Fund", target=CurrencyUtil.fromInr(100000.0), saved=CurrencyUtil.fromInr(30000.0)),
        GoalEntity(name="New Laptop", target=CurrencyUtil.fromInr(80000.0), saved=CurrencyUtil.fromInr(25000.0))
    )
    val recurring = listOf(
        RecurringEntity(name="Internet", amount=CurrencyUtil.fromInr(999.0), nextDateMillis=System.currentTimeMillis()+86400000L*7, category="Bill"),
        RecurringEntity(name="Rent", amount=CurrencyUtil.fromInr(12000.0), nextDateMillis=System.currentTimeMillis()+86400000L*15, category="Bill"),
        RecurringEntity(name="Phone EMI", amount=CurrencyUtil.fromInr(2200.0), nextDateMillis=System.currentTimeMillis()+86400000L*10, category="EMI")
    )
    val warranties = listOf(
        WarrantyEntity(itemName="Refrigerator", expiryMillis=System.currentTimeMillis()+86400000L*200, note="1 year warranty"),
        WarrantyEntity(itemName="Laptop", expiryMillis=System.currentTimeMillis()+86400000L*20, note="Extended warranty")
    )
    val lending = listOf(
        LendingEntity(personName="Rahul", amount=CurrencyUtil.fromInr(2000.0), direction="LENT"),
        LendingEntity(personName="Credit card - Amit", amount=CurrencyUtil.fromInr(1500.0), direction="BORROWED")
    )
}
