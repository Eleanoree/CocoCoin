package com.example.cococoin

import android.content.Context

// 資料庫輔助類：封裝了 Room 資料庫的操作
class CocoCoinDatabaseHelper(context: Context) {

    // 取得 Room 資料庫實例和 DAO
    private val roomDatabase = CocoCoinRoomDatabase.getInstance(context)
    private val transactionDao = roomDatabase.transactionDao()  // 交易操作
    private val accountDao = roomDatabase.accountDao()          // 帳戶操作
    private val budgetDao = roomDatabase.budgetDao()            // 預算操作

    // ==================== 交易相關操作 ====================

    // 取得所有交易（按時間倒序，最新的在前面）
    fun getAllTransactions(): List<Transaction> = transactionDao.getAllTransactions()

    // 根據 ID 取得單筆交易
    fun getTransactionById(transactionId: Int): Transaction? = transactionDao.getTransactionById(transactionId)

    // 取得交易總筆數
    fun getTransactionCount(): Int = transactionDao.getTransactionCount()

    // 檢查某個帳戶是否被任何交易使用過
    fun isAccountUsed(accountName: String): Boolean = transactionDao.isAccountUsed(accountName)

    // ==================== 帳戶相關操作 ====================

    // 取得所有帳戶（按 ID 排序）
    fun getAllAccounts(): List<AssetAccount> = accountDao.getAllAccounts()

    // 根據名稱取得帳戶
    fun getAccountByName(accountName: String): AssetAccount? = accountDao.getAccountByName(accountName)

    // ==================== 預算相關操作 ====================

    // 取得某年某月的預算金額（如果沒設定就回傳 0）
    fun getBudget(year: Int, month: Int): Int = budgetDao.getBudgetAmount(year, month) ?: 0

    // 取得所有預算設定
    fun getAllBudgets(): List<BudgetSetting> = budgetDao.getAllBudgets()

    // ==================== 快照相關操作 ====================

    // 取得整個資料庫的快照（把全部資料打包）
    fun getSnapshot(): CocoCoinSnapshot {
        return CocoCoinSnapshot(
            transactions = getAllTransactions(),
            accounts = getAllAccounts(),
            budgets = getAllBudgets()
            // 注意：categories（分類）不在 Room 資料庫中，存在 SharedPreferences
        )
    }

    // ==================== 交易操作（在事務中執行） ====================

    // 執行一個資料庫事務（要同時改多張表時用）
    // 白話：像「要嘛全部成功，要嘛全部失敗」，不會發生改到一半當機的情況
    fun runInTransaction(block: (CocoCoinRoomDatabase) -> Unit) {
        roomDatabase.runInTransaction {
            block(roomDatabase)
        }
    }

    // 新增一筆交易
    fun insertTransaction(database: CocoCoinRoomDatabase, transaction: Transaction) {
        database.transactionDao().insertTransaction(transaction)
    }

    // 更新一筆交易
    fun updateTransaction(database: CocoCoinRoomDatabase, transaction: Transaction) {
        database.transactionDao().updateTransaction(transaction)
    }

    // 刪除一筆交易
    fun deleteTransaction(database: CocoCoinRoomDatabase, transactionId: Int) {
        database.transactionDao().deleteTransaction(transactionId)
    }

    // ==================== 帳戶操作（在事務中執行） ====================

    // 新增一個帳戶
    fun insertAccount(database: CocoCoinRoomDatabase, account: AssetAccount) {
        database.accountDao().insertAccount(account)
    }

    // 更新一個帳戶
    fun updateAccount(database: CocoCoinRoomDatabase, account: AssetAccount) {
        database.accountDao().updateAccount(account)
    }

    // 刪除一個帳戶
    fun deleteAccount(database: CocoCoinRoomDatabase, accountId: Int) {
        database.accountDao().deleteAccount(accountId)
    }

    // 調整帳戶餘額（增加或減少）
    fun adjustAccountBalance(database: CocoCoinRoomDatabase, accountName: String, delta: Int) {
        database.accountDao().adjustAccountBalance(
            accountName = accountName,
            delta = delta,
            updatedAt = System.currentTimeMillis()
        )
    }

    // 重新命名交易中的帳戶名稱（當使用者修改帳戶名稱時）
    fun renameTransactionsAccount(database: CocoCoinRoomDatabase, oldName: String, newName: String) {
        database.transactionDao().renameTransactionsAccount(
            oldName = oldName,
            newName = newName,
            updatedAt = System.currentTimeMillis()
        )
    }

    // 重新命名交易中的分類名稱（當使用者修改分類名稱時）
    fun renameTransactionsCategory(
        database: CocoCoinRoomDatabase,
        type: String,
        oldName: String,
        newName: String
    ) {
        database.transactionDao().renameTransactionsCategory(
            type = type,
            oldName = oldName,
            newName = newName,
            updatedAt = System.currentTimeMillis()
        )
    }

    // 新增或更新預算
    fun upsertBudget(database: CocoCoinRoomDatabase, budgetSetting: BudgetSetting) {
        database.budgetDao().upsertBudget(budgetSetting)
    }

    // ==================== 大量資料操作 ====================

    // 用快照完全取代資料庫內容（先清空再寫入）
    // 白話：把整個資料庫的內容換成快照裡的資料（用於匯入備份或雲端同步）
    fun replaceAllData(snapshot: CocoCoinSnapshot) {
        runInTransaction { database ->
            // 1. 清空所有表格
            database.transactionDao().clearAll()
            database.accountDao().clearAll()
            database.budgetDao().clearAll()

            // 2.寫入快照中的資料
            if (snapshot.accounts.isNotEmpty()) {
                database.accountDao().insertAccounts(snapshot.accounts)
            }
            if (snapshot.transactions.isNotEmpty()) {
                database.transactionDao().insertTransactions(snapshot.transactions)
            }
            if (snapshot.budgets.isNotEmpty()) {
                database.budgetDao().upsertBudgets(snapshot.budgets)
            }
        }
    }

    // 清除所有資料（清空表格）
    fun clearAllData() {
        runInTransaction { database ->
            database.transactionDao().clearAll()
            database.accountDao().clearAll()
            database.budgetDao().clearAll()
        }
    }
}