package com.example.cococoin

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Room 資料庫：
// 這是 App 的「資料庫本體」，負責建立和管理資料庫檔案
// 告訴 Room 有哪些表格（entities）
@Database(
    entities = [Transaction::class, AssetAccount::class, BudgetSetting::class],  // 三張表格
    version = 2,      // 資料庫版本（從 1 升級到 2 加了 updated_at 欄位）
    exportSchema = false  // 不匯出架構檔案（簡化設定）
)
abstract class CocoCoinRoomDatabase : RoomDatabase() {

    // 提供 DAO（資料存取物件）的抽象方法
    abstract fun transactionDao(): TransactionDao  // 交易表的操作
    abstract fun accountDao(): AccountDao          // 帳戶表的操作
    abstract fun budgetDao(): BudgetDao            // 預算表的操作

    // 存放單例（只有一份）和資料庫升級邏輯
    companion object {
        private const val DATABASE_NAME = "cococoin.db"  // 資料庫檔案名稱

        // 資料庫升級：從版本 1 升級到版本 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1.建立新的交易表（有 updated_at 欄位）
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        time TEXT NOT NULL,
                        account_name TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 2.把舊表的資料複製到新表（updated_at 先用 0 代替）
                database.execSQL(
                    """
                    INSERT INTO transactions_new (id, type, category, amount, note, time, account_name, updated_at)
                    SELECT id, type, category, amount, note, time, account_name, updated_at
                    FROM transactions
                    """.trimIndent()
                )

                // 3.刪除舊表，把新表改名
                database.execSQL("DROP TABLE transactions")
                database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                // 4.同樣的步驟處理帳戶表
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        balance INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO accounts_new (id, name, balance, updated_at)
                    SELECT id, name, balance, updated_at
                    FROM accounts
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE accounts")
                database.execSQL("ALTER TABLE accounts_new RENAME TO accounts")

                // 5.建立索引（加速查詢）
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts(name)"
                )
            }
        }

        @Volatile  // 確保多執行緒時變數的變化能即時被看到
        private var instance: CocoCoinRoomDatabase? = null  // 單例實例

        // 取得資料庫實例（單例模式）：
        // 只會建立一次資料庫，之後都回傳同一個（避免重複開啟）
        fun getInstance(context: Context): CocoCoinRoomDatabase {
            return instance ?: synchronized(this) {  // 同步鎖，確保只有一個執行緒執行
                instance ?: Room.databaseBuilder(
                    context.applicationContext,  // 使用 App 的全局 Context
                    CocoCoinRoomDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)  // 加入升級邏輯
                    .build()
                    .also { instance = it }  // 建立後存到 instance
            }
        }
    }
}