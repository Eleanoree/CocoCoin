// App 的「住址」
package com.example.cococoin

import androidx.room.Dao          // 標記這個介面是「資料庫對話窗口」
import androidx.room.Insert
import androidx.room.OnConflictStrategy // 「撞名或衝突時怎麼辦」的規則
import androidx.room.Query
import androidx.room.Update

// @Dao 是貼在介面上的標籤，告訴 Room：「這是我的存取說明書」
@Dao
interface AccountDao {

    // 從「accounts 這本帳戶表」拿全部欄位 (*)，依照 id 從小到大排序
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): List<AssetAccount>
    // 顯示一份「全部帳戶清單」
    // 回傳型別 List<AssetAccount> ：一疊「帳戶卡片」

    // 查詢特定名稱的帳戶，例如「現金」、「信用卡」
    // :accountName 意思是「等一下傳進來的名字，要放來這」
    // LIMIT 1 只拿第一筆，避免重複
    @Query("SELECT * FROM accounts WHERE name = :accountName LIMIT 1")
    fun getAccountByName(accountName: String): AssetAccount?
    // 回傳 ? 表示「可能找不到，沒有就回傳 null（空的）」
    // 要編輯某個帳戶前，要先從資料庫查出來

    // 新增一個帳戶，如果「同樣 ID 或唯一值」已經存在，就用新的蓋掉舊的（REPLACE）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccount(account: AssetAccount)

    // 一次新增多個帳戶（例如初次安裝 App 時匯入預設帳戶）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccounts(accounts: List<AssetAccount>)

    // 修改某個帳戶的資料（例如改帳戶名稱或初始金額）
    // 不需要寫 SQL，Room 會根據「主鍵 id」自動找到該筆並更新
    @Update
    fun updateAccount(account: AssetAccount)

    // 刪除某個帳戶（根據 id）
    @Query("DELETE FROM accounts WHERE id = :accountId")
    fun deleteAccount(accountId: Int)

    // 調整某個帳戶的餘額（記帳 App 的核心）
    // 直接請資料庫幫我們做 balance + delta 更安全、不會被其他操作打亂
    @Query(
        """
        UPDATE accounts
        SET balance = balance + :delta,
            updated_at = :updatedAt
        WHERE name = :accountName
        """
    )
    fun adjustAccountBalance(accountName: String, delta: Int, updatedAt: Long)
    // delta 可以是 +1000（收入）或 -200（支出）
    // updatedAt 是時間戳記，記錄最後異動時間，方便同步或排序

    // 全部刪除！通常用來「重置 App 資料」或測試時清空
    @Query("DELETE FROM accounts")
    fun clearAll()
}