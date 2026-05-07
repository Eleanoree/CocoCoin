// ============================================================
// 🗄️ 資料庫輔助類 — 白話文：資料庫的「專屬助理」
// ============================================================
//
// 情境劇：想像你有一間超大倉庫（Room 資料庫）
//         管家（CocoCoinRepository）很忙，沒時間親自搬貨
//         所以就請了一位助理（DatabaseHelper）專門打理倉庫：
//           📦 搬貨、上架、下架（新增、更新、刪除）
//           🔍 找東西（查詢交易、帳戶、預算）
//           📸 拍照記錄（取得快照 Snapshot）
//           🔄 整批換貨（用快照取代全部資料）
//
// 這個 DatabaseHelper 就是那位「倉庫助理」！
// 它把所有跟 Room 資料庫相關的操作都包裝起來，讓管家可以輕鬆叫它做事
// ============================================================
package com.example.cococoin

import android.content.Context

class CocoCoinDatabaseHelper(context: Context) {

    // ============================================================
    // 🏭 取得倉庫的「鑰匙」和「搬運工們」
    // ============================================================
    // roomDatabase：倉庫本體（單例，全 App 共用）
    // transactionDao：交易貨架的搬運工
    // accountDao：帳戶貨架的搬運工
    // budgetDao：預算貨架的搬運工
    private val roomDatabase = CocoCoinRoomDatabase.getInstance(context)
    private val transactionDao = roomDatabase.transactionDao()  // 交易操作員
    private val accountDao = roomDatabase.accountDao()          // 帳戶操作員
    private val budgetDao = roomDatabase.budgetDao()            // 預算操作員

    // ==================== 📦 交易相關操作 ====================

    // 取得所有交易（按時間倒序，最新的在前面）
    // 白話文：「助理，把所有交易記錄都拿出來，最新的放最上面！」
    fun getAllTransactions(): List<Transaction> = transactionDao.getAllTransactions()

    // 根據 ID 取得單筆交易
    // 白話文：「助理，幫我找編號 123 的那筆交易」
    fun getTransactionById(transactionId: Int): Transaction? = transactionDao.getTransactionById(transactionId)

    // 取得交易總筆數
    // 白話文：「助理，算一下總共記了幾筆帳？」
    fun getTransactionCount(): Int = transactionDao.getTransactionCount()

    // 檢查某個帳戶是否被任何交易使用過
    // 白話文：「助理，檢查一下『現金』這個帳戶有沒有被用過？」
    // 用途：刪除帳戶前要先檢查，不然刪了帳戶那些交易會變成「幽靈交易」
    fun isAccountUsed(accountName: String): Boolean = transactionDao.isAccountUsed(accountName)

    // ==================== 💰 帳戶相關操作 ====================

    // 取得所有帳戶（按 ID 排序）
    // 白話文：「助理，把所有帳戶清單拿出來！」（現金、信用卡、Line Pay...）
    fun getAllAccounts(): List<AssetAccount> = accountDao.getAllAccounts()

    // 根據名稱取得帳戶
    // 白話文：「助理，幫我找叫做『現金』的那個帳戶」
    fun getAccountByName(accountName: String): AssetAccount? = accountDao.getAccountByName(accountName)

    // ==================== 📊 預算相關操作 ====================

    // 取得某年某月的預算金額（如果沒設定就回傳 0）
    // 白話文：「助理，2026 年 4 月的預算是多少？」（沒設就當作 0）
    fun getBudget(year: Int, month: Int): Int = budgetDao.getBudgetAmount(year, month) ?: 0

    // 取得所有預算設定
    // 白話文：「助理，把所有月份的預算設定都拿出來！」
    fun getAllBudgets(): List<BudgetSetting> = budgetDao.getAllBudgets()

    // ==================== 📸 快照相關操作 ====================

    // 取得整個資料庫的快照（把全部資料打包成一箱）
    // 白話文：「助理，把倉庫裡所有的貨物都拍照記錄下來，打包成一箱！」
    // 用途：雲端備份、匯出備份檔時使用
    fun getSnapshot(): CocoCoinSnapshot {
        return CocoCoinSnapshot(
            transactions = getAllTransactions(),  // 所有交易
            accounts = getAllAccounts(),          // 所有帳戶
            budgets = getAllBudgets()             // 所有預算
            // 注意：categories（分類）不在 Room 資料庫中，存在 SharedPreferences
            // 所以快照裡的 categories 需要由呼叫方另外補上
        )
    }

    // ==================== 🔄 交易操作（在事務中執行） ====================

    // 執行一個資料庫事務（要同時改多張表時用）
    // 白話文：「助理，接下來我要做一連串動作，你要確保全部成功或全部失敗」
    // 就像銀行轉帳：扣 A 戶頭 + 加 B 戶頭，兩件事要一起完成
    fun runInTransaction(block: (CocoCoinRoomDatabase) -> Unit) {
        roomDatabase.runInTransaction {
            block(roomDatabase)
        }
    }

    // 新增一筆交易
    // 白話文：「助理，把這筆『雞排 80 元』放到交易貨架上」
    fun insertTransaction(database: CocoCoinRoomDatabase, transaction: Transaction) {
        database.transactionDao().insertTransaction(transaction)
    }

    // 更新一筆交易
    // 白話文：「助理，幫我把編號 123 那筆交易的金額從 300 改成 150」
    fun updateTransaction(database: CocoCoinRoomDatabase, transaction: Transaction) {
        database.transactionDao().updateTransaction(transaction)
    }

    // 刪除一筆交易
    // 白話文：「助理，把編號 456 那筆交易從貨架上拿掉」
    fun deleteTransaction(database: CocoCoinRoomDatabase, transactionId: Int) {
        database.transactionDao().deleteTransaction(transactionId)
    }

    // ==================== 💰 帳戶操作（在事務中執行） ====================

    // 新增一個帳戶
    // 白話文：「助理，幫我新增一個『Line Pay』帳戶」
    fun insertAccount(database: CocoCoinRoomDatabase, account: AssetAccount) {
        database.accountDao().insertAccount(account)
    }

    // 更新一個帳戶
    // 白話文：「助理，把『現金』帳戶的名稱改成『實體現金』」
    fun updateAccount(database: CocoCoinRoomDatabase, account: AssetAccount) {
        database.accountDao().updateAccount(account)
    }

    // 刪除一個帳戶
    // 白話文：「助理，把『Google Pay』這個帳戶刪掉」
    fun deleteAccount(database: CocoCoinRoomDatabase, accountId: Int) {
        database.accountDao().deleteAccount(accountId)
    }

    // 調整帳戶餘額（增加或減少）
    // 白話文：「助理，『現金』帳戶的餘額要調整 100 元」
    // delta 可以是正數（加錢）或負數（扣錢）
    fun adjustAccountBalance(database: CocoCoinRoomDatabase, accountName: String, delta: Int) {
        database.accountDao().adjustAccountBalance(
            accountName = accountName,
            delta = delta,
            updatedAt = System.currentTimeMillis()  // 順便記錄修改時間
        )
    }

    // 重新命名交易中的帳戶名稱（當使用者修改帳戶名稱時）
    // 白話文：使用者把「現金」改成「實體現金」，
    //         以前記的「現金」交易也要跟著改名，不然會找不到帳戶
    fun renameTransactionsAccount(database: CocoCoinRoomDatabase, oldName: String, newName: String) {
        database.transactionDao().renameTransactionsAccount(
            oldName = oldName,
            newName = newName,
            updatedAt = System.currentTimeMillis()
        )
    }

    // 重新命名交易中的分類名稱（當使用者修改分類名稱時）
    // 白話文：使用者把「餐飲」改成「吃貨人生」，
    //         以前記的「餐飲」交易也要跟著改名
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
    // 白話文：「助理，2026 年 4 月的預算設為 20000 元」
    fun upsertBudget(database: CocoCoinRoomDatabase, budgetSetting: BudgetSetting) {
        database.budgetDao().upsertBudget(budgetSetting)
    }

    // ==================== 📦 大量資料操作 ====================

    // 用快照完全取代資料庫內容（先清空再寫入）
    // 白話文：「助理，把倉庫裡所有的貨物清空，然後放進這箱快照裡的東西」
    // 用途：匯入備份檔、從雲端還原資料時使用
    fun replaceAllData(snapshot: CocoCoinSnapshot) {
        runInTransaction { database ->
            // 1. 清空所有貨架
            database.transactionDao().clearAll()   // 清空交易貨架
            database.accountDao().clearAll()       // 清空帳戶貨架
            database.budgetDao().clearAll()        // 清空預算貨架

            // 2. 把快照裡的貨物放上去
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
    // 白話文：「助理，把倉庫清空！」（核彈按鈕）
    fun clearAllData() {
        runInTransaction { database ->
            database.transactionDao().clearAll()
            database.accountDao().clearAll()
            database.budgetDao().clearAll()
        }
    }
}