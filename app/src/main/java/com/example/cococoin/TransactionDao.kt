// 喔喔喔！這裡是「記帳本的幕後黑手」—— DAO (Data Access Object)
// 白話文就是：「專門負責把錢錢的進出紀錄，從資料庫搬進搬出的小精靈」
// 注意！這只是個「介面」，像是一份工作職責清單，真正的打工仔會讓Room自動生成
package com.example.cococoin

// 這些都是跟Android Room資料庫借來的超能力
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {

    // ========================= 查 詢 區 =========================

    // 蝦毀？想知道這個月花了多少？把所有交易都給我交出來！
    // DESC 就是「大到小」 → 時間數字越大（越新）的排越上面
    // 這樣打開App第一眼就看到「剛剛那杯奶茶60元」，多貼心～
    // 萬一兩筆時間一模一樣（手速太快）？別怕，再用id DESC補一刀，最新新增的還是排上面
    @Query("SELECT * FROM transactions ORDER BY time DESC, id DESC")
    fun getAllTransactions(): List<Transaction>

    // 欸欸，給我找一下「第9527號」交易！
    // 如果找不到？沒關係，我們很佛心，回傳 null 而不是讓你當機 (哭啊)
    // LIMIT 1 告訴資料庫：「查到一筆就收工，不要再翻了，效率！效率！」
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    fun getTransactionById(transactionId: Int): Transaction?

    // 老媽問：「你到底記了幾筆帳？」 一秒回答！
    // COUNT(*) 是SQL界的「點名大會」，把所有交易記錄都算進去
    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Int

    // 這個帳戶名稱（例如「小豬撲滿」）有人用過嗎？
    // 注意！要刪除帳戶前一定要先問這位大神，不然帳戶不見了，
    // 以前用這個帳戶記的交易會變成「幽靈交易」—— 不知道錢從哪來又跑去哪，超可怕！
    // 回傳 true → 表示「有人還在用啦！不能刪帳戶！」
    @Query("SELECT COUNT(*) > 0 FROM transactions WHERE account_name = :accountName")
    fun isAccountUsed(accountName: String): Boolean

    // ========================= 新 增 區 =========================

    // 噹噹噹！新增一筆交易（買了雞排或中了發票）
    // onConflict = OnConflictStrategy.REPLACE 是什麼呢？
    // 白話文：如果撞ID了怎麼辦？「舊的不去，新的不來」，直接覆蓋掉啦！
    // 反正我們的ID會自動生成，不太會撞，但這叫「防呆機制」
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransaction(transaction: Transaction)

    // 一次塞很多筆交易？例如從備份檔或匯入記帳本
    // 就像掃描發票一次丟整疊，省時省力
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTransactions(transactions: List<Transaction>)

    // ========================= 修 改 區 =========================

    // 哎唷！不小心把「午餐300元」打成「3000元」（你是吃和牛嗎？）
    // @Update 這傢伙會自動根據 transaction 裡面的 id 去找出那一筆，然後更新其他欄位，很聰明吧！
    @Update
    fun updateTransaction(transaction: Transaction)

    // 大量改交易中的「帳戶名稱」
    // 情境劇：使用者說「我之前把『現金』帳戶改成『實體現金』，那以前記在『現金』的交易怎麼辦？」
    // 放心！這支程式就是你的時光修改器，oldName 換 newName，而且順便更新『最後修改時間』(updated_at)
    // 這樣回頭查帳才知道什麼時候被改過
    @Query(
        """
        UPDATE transactions
        SET account_name = :newName,
            updated_at = :updatedAt
        WHERE account_name = :oldName
        """
    )
    fun renameTransactionsAccount(oldName: String, newName: String, updatedAt: Long)

    // 啊哈！這跟上面很像，只是這次改的是「分類名稱」
    // 舉例：把 type = 'expense' 且舊分類『食物』全部改成『美食饗宴』（聽起來比較厲害）
    // 注意！一定要指定 type（收入或支出），不然可能連「收入-薪水」都被改成「美食饗宴」就好笑了
    @Query(
        """
        UPDATE transactions
        SET category = :newName,
            updated_at = :updatedAt
        WHERE type = :type AND category = :oldName
        """
    )
    fun renameTransactionsCategory(type: String, oldName: String, newName: String, updatedAt: Long)

    // ========================= 刪 除 區 =========================

    // 刪除「指定編號」的交易（比如那筆3000元的虛假午餐，刪掉！刪掉！）
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    fun deleteTransaction(transactionId: Int)

    // 終極核彈按鈕：把所有交易記錄清空空！
    // 通常用在「重置App」或「匯入全新備份前先清空」
    // 小心使用，按下去連阿嬤都救不了你的記帳本喔
    @Query("DELETE FROM transactions")
    fun clearAll()
}