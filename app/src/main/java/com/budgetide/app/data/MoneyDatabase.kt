package com.budgetide.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class TransactionType { INCOME, EXPENSE }
enum class LendingDirection { LENT, BORROWED }

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val type: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    // Needs vs wants: true = essential/necessary spend, false = discretionary/unnecessary spend.
    // Ignored for INCOME rows.
    val essential: Boolean = true
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double = 0.0
)

// category: "Bill" | "Subscription" | "EMI"
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val frequency: String = "Monthly",
    val nextDateMillis: Long,
    val category: String = "Bill"
)

@Entity(tableName = "warranties")
data class WarrantyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val purchaseDateMillis: Long = System.currentTimeMillis(),
    val expiryMillis: Long,
    val note: String = ""
)

// direction: "LENT" (they owe you) | "BORROWED" (you owe them)
@Entity(tableName = "lending")
data class LendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amount: Double,
    val direction: String,
    val dateMillis: Long = System.currentTimeMillis(),
    val settled: Boolean = false,
    val note: String = ""
)

@Dao
interface MoneyDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    fun transactions(): Flow<List<TransactionEntity>>

    @Insert suspend fun insertTransaction(item: TransactionEntity)
    @Update suspend fun updateTransaction(item: TransactionEntity)
    @Delete suspend fun deleteTransaction(item: TransactionEntity)

    @Query("SELECT * FROM goals ORDER BY id DESC")
    fun goals(): Flow<List<GoalEntity>>
    @Insert suspend fun insertGoal(item: GoalEntity)
    @Update suspend fun updateGoal(item: GoalEntity)
    @Delete suspend fun deleteGoal(item: GoalEntity)

    @Query("SELECT * FROM recurring ORDER BY nextDateMillis ASC")
    fun recurring(): Flow<List<RecurringEntity>>
    @Insert suspend fun insertRecurring(item: RecurringEntity)
    @Update suspend fun updateRecurring(item: RecurringEntity)
    @Delete suspend fun deleteRecurring(item: RecurringEntity)

    @Query("SELECT * FROM warranties ORDER BY expiryMillis ASC")
    fun warranties(): Flow<List<WarrantyEntity>>
    @Insert suspend fun insertWarranty(item: WarrantyEntity)
    @Update suspend fun updateWarranty(item: WarrantyEntity)
    @Delete suspend fun deleteWarranty(item: WarrantyEntity)

    @Query("SELECT * FROM lending ORDER BY settled ASC, dateMillis DESC")
    fun lending(): Flow<List<LendingEntity>>
    @Insert suspend fun insertLending(item: LendingEntity)
    @Update suspend fun updateLending(item: LendingEntity)
    @Delete suspend fun deleteLending(item: LendingEntity)

    // One-shot counts used only to decide whether sample data needs seeding.
    // NEVER use the StateFlow's cached .value for this check - it starts as
    // an empty list and only fills in once something subscribes to it, so
    // checking it right after app launch is a race condition (this was the
    // cause of goals/sample data being duplicated on every cold start).
    @Query("SELECT COUNT(*) FROM transactions") suspend fun transactionCount(): Int
    @Query("SELECT COUNT(*) FROM goals") suspend fun goalCount(): Int
    @Query("SELECT COUNT(*) FROM recurring") suspend fun recurringCount(): Int

    // Used only by Backup & Restore, to replace all local data with the
    // contents of a restored backup file.
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM warranties") suspend fun clearWarranties()
    @Query("DELETE FROM lending") suspend fun clearLending()
}

@Database(
    entities = [
        TransactionEntity::class,
        GoalEntity::class,
        RecurringEntity::class,
        WarrantyEntity::class,
        LendingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun dao(): MoneyDao

    companion object {
        @Volatile private var INSTANCE: MoneyDatabase? = null

        fun get(context: Context): MoneyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneyDatabase::class.java,
                    "budgetide.db"
                )
                    // App is pre-release / single test device, so a destructive
                    // migration (wipes local db and reseeds) is fine here instead
                    // of hand-writing a Migration for the new columns/tables.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
