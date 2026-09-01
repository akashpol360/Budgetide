package com.budgetide.app.data

object SampleData {
    val transactions = listOf(
        TransactionEntity(title="Salary", amount=50000.0, category="Salary", type="INCOME", essential=true),
        TransactionEntity(title="Rent", amount=12000.0, category="Rent", type="EXPENSE", essential=true),
        TransactionEntity(title="Groceries", amount=4800.0, category="Food", type="EXPENSE", essential=true),
        TransactionEntity(title="Fuel", amount=2500.0, category="Travel", type="EXPENSE", essential=true),
        TransactionEntity(title="Internet", amount=999.0, category="Bills", type="EXPENSE", essential=true),
        TransactionEntity(title="Movie", amount=600.0, category="Entertainment", type="EXPENSE", essential=false)
    )
    val goals = listOf(
        GoalEntity(name="Emergency Fund", target=100000.0, saved=30000.0),
        GoalEntity(name="New Laptop", target=80000.0, saved=25000.0)
    )
    val recurring = listOf(
        RecurringEntity(name="Internet", amount=999.0, nextDateMillis=System.currentTimeMillis()+86400000L*7, category="Bill"),
        RecurringEntity(name="Rent", amount=12000.0, nextDateMillis=System.currentTimeMillis()+86400000L*15, category="Bill"),
        RecurringEntity(name="Phone EMI", amount=2200.0, nextDateMillis=System.currentTimeMillis()+86400000L*10, category="EMI")
    )
    val warranties = listOf(
        WarrantyEntity(itemName="Refrigerator", expiryMillis=System.currentTimeMillis()+86400000L*200, note="1 year warranty"),
        WarrantyEntity(itemName="Laptop", expiryMillis=System.currentTimeMillis()+86400000L*20, note="Extended warranty")
    )
    val lending = listOf(
        LendingEntity(personName="Rahul", amount=2000.0, direction="LENT"),
        LendingEntity(personName="Credit card - Amit", amount=1500.0, direction="BORROWED")
    )
}
