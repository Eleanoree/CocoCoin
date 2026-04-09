package com.example.cococoin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {

    // 查詢所有交易紀錄，最新（時間大）的排上面
    // DESC->記帳首頁通常要先看到「最近一筆花費」
    // 如果時間一樣，再用 id DESC 確保新增的排更上面
    @Query("SELECT * FROM transactions ORDER BY time DESC, id DESC")
    fun getAllTransactions(): List<Transaction>

    // 根據交易編號（id）找某一筆交易
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    fun getTransactionById(transactionId: Int): Transaction?

    // 計算總共有幾筆交易紀錄
    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Int

    // 檢查某個帳戶名稱是否「曾經被用過」
    // 刪除帳戶前要先確認，如果這個帳戶還有交易記錄就不能直接刪，不然刪了帳戶那些交易會變成「幽靈帳戶」
    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE account_name = :accountName")
    fun isAccountUsed(accountName: String): Boolean
    // 回傳 true 表示「這個帳戶還有人在使用」

    // 新增一筆交易（收入或支出）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: Transaction)

    // 一次新增多筆交易（例如匯入備份）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(transactions: List<Transaction>)

    // 修改某一筆交易（例如改金額或分類）
    @Update
    fun updateTransaction(transaction: Transaction)

    // 刪除某一筆交易
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    fun deleteTransaction(transactionId: Int)

    // 大量修改交易中的「帳戶名稱」
    // 當使用者在「帳戶管理」把「現金」改名成「實體現金」時，所有原本記在「現金」的交易，也要跟著改帳戶名稱，不然查帳會找不到
    @Query(
        """
        UPDATE transactions
        SET account_name = :newName,
            updated_at = :updatedAt
        WHERE account_name = :oldName
        """
    )
    fun renameTransactionsAccount(oldName: String, newName: String, updatedAt: Long)

    @Query(
        """
        UPDATE transactions
        SET category = :newName,
            updated_at = :updatedAt
        WHERE type = :type AND category = :oldName
        """
    )
    fun renameTransactionsCategory(type: String, oldName: String, newName: String, updatedAt: Long)

    // 刪除所有交易記錄（重置用）
    @Query("DELETE FROM transactions")
    fun clearAll()
}
