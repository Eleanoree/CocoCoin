// ============================================================
// 🏛️ Room 資料庫 — 白話文：記帳本的「倉庫管理系統」
// ============================================================
//
// 情境劇：想像你有一個超大倉庫，裡面有三個貨架：
//           📦 貨架 A：交易記錄（transactions）— 每筆花費和收入
//           📦 貨架 B：帳戶清單（accounts）— 現金、信用卡、銀行帳戶
//           📦 貨架 C：預算設定（budgets）— 每個月計畫花多少
//
// 這個 CocoCoinRoomDatabase 就是那個「倉庫管理系統」！
// 它負責：
//   1. 建立倉庫檔案（cococoin.db）
//   2. 管理貨架的結構（表格長什麼樣子）
//   3. 處理倉庫擴建（資料庫升級，從版本 1 到版本 2）
//   4. 提供搬運工（DAO）讓你可以存取貨物
//
// @Database 是 Room 的魔法標籤，告訴 Room：「嘿，這個類別代表整個資料庫！」
// ============================================================
package com.example.cococoin

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ============================================================
// 📋 資料庫定義 — 白話文：告訴 Room 倉庫裡有什麼
// ============================================================
// entities：指定倉庫裡有哪些貨架（表格）
//   - Transaction::class → 交易記錄表
//   - AssetAccount::class → 帳戶表
//   - BudgetSetting::class → 預算表
//
// version：倉庫的版本編號（從 1 升級到 2，加了 updated_at 欄位）
//   版本升級就像倉庫擴建，要搬移舊貨物到新貨架
//
// exportSchema = false：不匯出架構檔案（簡化設定，專業版才會開啟）
@Database(
    entities = [Transaction::class, AssetAccount::class, BudgetSetting::class],
    version = 2,      // 目前是第 2 版（表示已經升級過一次）
    exportSchema = false
)
abstract class CocoCoinRoomDatabase : RoomDatabase() {

    // ============================================================
    // 🔧 提供 DAO（資料存取物件）— 白話文：提供倉庫的「搬運工」
    // ============================================================
    // DAO = Data Access Object
    // 白話文：專門負責幫你把貨物搬進搬出的工人
    //
    // abstract 表示「這個方法沒有實作，Room 會自動幫我們生程式碼」
    // 就像你請了一個工人，但不用自己教他怎麼搬貨，Room 會訓練好
    abstract fun transactionDao(): TransactionDao  // 交易表的搬運工
    abstract fun accountDao(): AccountDao          // 帳戶表的搬運工
    abstract fun budgetDao(): BudgetDao            // 預算表的搬運工

    // ============================================================
    // 🧰 伴侶物件 — 白話文：倉庫的「管理辦公室」
    // ============================================================
    // 這裡面放的是「跟整個倉庫有關」的東西，而不是跟某個貨架有關
    companion object {
        // 倉庫檔案的名稱（就像 Excel 檔名）
        private const val DATABASE_NAME = "cococoin.db"

        // ============================================================
        // 🔄 資料庫升級：從版本 1 升級到版本 2 — 白話文：倉庫擴建計畫
        // ============================================================
        // 為什麼需要升級？
        //   一開始的版本（version 1）沒有 updated_at 欄位
        //   後來我們想要記錄「每筆資料最後修改時間」，所以需要新增欄位
        //   但使用者的舊資料不能刪掉，所以要「搬移」到新貨架
        //
        // Migration 就像一個「搬遷計畫書」，告訴 Room 怎麼把舊貨物搬到新貨架
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ----- 步驟 1：建立新的交易表（有 updated_at 欄位）-----
                // 白話文：做一個新貨架，格子更多、功能更強
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
                        updated_at INTEGER NOT NULL   -- ✨ 這是新增的欄位！
                    )
                    """.trimIndent()
                )

                // ----- 步驟 2：把舊表的資料複製到新表 -----
                // 白話文：把舊貨架的貨物，搬到新貨架
                // 注意：舊的資料沒有 updated_at，先用 0 代替
                database.execSQL(
                    """
                    INSERT INTO transactions_new (id, type, category, amount, note, time, account_name, updated_at)
                    SELECT id, type, category, amount, note, time, account_name, updated_at
                    FROM transactions
                    """.trimIndent()
                )

                // ----- 步驟 3：刪除舊表，把新表改名 -----
                // 白話文：把舊貨架拆掉，新貨架改名成原本的名字
                database.execSQL("DROP TABLE transactions")
                database.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                // ----- 步驟 4：同樣的步驟處理帳戶表 -----
                // 白話文：帳戶貨架也要升級，加上 updated_at 欄位
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

                // ----- 步驟 5：建立索引（加速查詢）-----
                // 白話文：在帳戶名稱上放一個「快速查找標籤」
                // 就像在書本側面貼標籤，找「現金」的時候不用翻完整本書
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts(name)"
                )
            }
        }

        // ============================================================
        // 🏭 單例模式 — 白話文：全 App 只有一個倉庫
        // ============================================================
        // @Volatile：告訴 Kotlin「這個變數可能會被多個執行緒同時存取」
        // 白話文：掛一個「注意！多人使用」的牌子，確保大家看到的是同一個倉庫
        @Volatile
        private var instance: CocoCoinRoomDatabase? = null  // 倉庫實例（一開始是空的）

        // ============================================================
        // 🔑 取得資料庫實例（單例模式）— 白話文：拿到倉庫的鑰匙
        // ============================================================
        // 這個函式保證：
        //   - 第一次呼叫時，會建立倉庫
        //   - 之後呼叫，都回傳同一個倉庫（不會再開一間）
        //   - 多個執行緒同時呼叫時，不會建立兩次
        fun getInstance(context: Context): CocoCoinRoomDatabase {
            return instance ?: synchronized(this) {  // 🔒 同步鎖：一次只讓一個人進來
                instance ?: Room.databaseBuilder(
                    context.applicationContext,  // 使用 App 的全局 Context（不會記憶體洩漏）
                    CocoCoinRoomDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)  // 加入升級計畫書
                    .build()  // 開始建造倉庫！
                    .also { instance = it }  // 建造完成後，存到 instance 變數
            }
        }
    }
}