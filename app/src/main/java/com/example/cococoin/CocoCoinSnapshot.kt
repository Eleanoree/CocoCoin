package com.example.cococoin

// 資料快照：
// 把整個 App 的資料（交易、帳戶、預算、分類）打包成一個包裹
// 方便用來上傳到雲端、從雲端下載、或備份到手機
data class CocoCoinSnapshot(
    val transactions: List<Transaction> = emptyList(),  // 所有交易紀錄
    val accounts: List<AssetAccount> = emptyList(),     // 所有帳戶
    val budgets: List<BudgetSetting> = emptyList(),     // 所有預算設定
    val categories: List<TransactionCategoryDefinition> = emptyList()  // 所有分類
) {

    // 檢查這個快照是不是空的（完全沒有任何資料）
    fun isEmpty(): Boolean {
        return transactions.isEmpty() &&
                accounts.isEmpty() &&
                budgets.isEmpty() &&
                categories.isEmpty()
    }

    // 找出整份快照中「最新的更新時間」：
    // 看所有資料（交易、帳戶、預算、分類）中，誰的更新時間最晚，
    // 用來判斷哪個版本比較新（合併時用）
    fun latestUpdatedAt(): Long {
        return buildList {
            // 把所有資料的 updatedAt 時間收集起來
            addAll(transactions.map { it.updatedAt })
            addAll(accounts.map { it.updatedAt })
            addAll(budgets.map { it.updatedAt })
            addAll(categories.map { it.updatedAt })
        }.maxOrNull() ?: 0L  // 回傳最大的（最新的），如果都沒有就回傳 0
    }
}