// 🏠 App 的「住址」
package com.example.cococoin

import androidx.room.Dao          // 📋 標記這個介面是「資料庫對話窗口」
import androidx.room.Insert       // ✍️ 「新增資料」的魔法標籤
import androidx.room.OnConflictStrategy // ⚔️ 「撞名或衝突時怎麼辦」的規則
import androidx.room.Query        // 🔍 「查詢資料」的魔法標籤
import androidx.room.Update       // ✏️ 「更新資料」的魔法標籤

/**
 * 📋【帳戶資料存取介面】- AccountDao
 *
 * 想像你是一家銀行的「金庫管理員」，而這個 DAO 就是你的「工作手冊」：
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                    📋 工作手冊                           │
 * ├─────────────────────────────────────────────────────────┤
 * │                                                          │
 * │  📖 讀取操作（查詢）                                     │
 * │  ├─ getAllAccounts()  → 把所有錢包列出來                │
 * │  └─ getAccountByName() → 找到某個特定的錢包             │
 * │                                                          │
 * │  ✍️ 寫入操作（修改）                                     │
 * │  ├─ insertAccount()    → 新增一個錢包                   │
 * │  ├─ insertAccounts()   → 一次新增好多錢包               │
 * │  ├─ updateAccount()    → 修改錢包名稱或餘額             │
 * │  ├─ deleteAccount()    → 刪除一個錢包                   │
 * │  ├─ adjustAccountBalance() → 加錢/扣錢（最常用！）      │
 * │  └─ clearAll()         → 把所有錢包清空（核彈按鈕）     │
 * │                                                          │
 * └─────────────────────────────────────────────────────────┘
 *
 * 為什麼叫 DAO（Data Access Object）？
 * DAO = 資料存取物件，聽起來很專業，其實就是：
 * 「負責跟資料庫說話的那個人／那本說明書」
 *
 * 有了這本說明書，App 其他地方就不用寫複雜的 SQL 語法，
 * 只要呼叫這些函數就好了！超方便～
 *
 * @Dao 是貼在介面上的標籤，告訴 Room：「這是我的存取說明書」
 */
@Dao
interface AccountDao {

    // ========== 📖 讀取操作（查詢） ==========

    /**
     * 📋 取得所有帳戶（從 accounts 表拿全部欄位，依照 id 從小到大排序）
     *
     * SQL 語法翻譯：
     * ┌─────────────────────────────────────────────────────────┐
     * │ SELECT * FROM accounts ORDER BY id ASC                 │
     * │   ↓         ↓           ↓              ↓               │
     * │   │         │           │              └─ 照 id 從小到大排
     * │   │         │           └─ 從 accounts 這張表
     * │   │         └─ 拿出所有欄位
     * │   └─ 拿出（SELECT）                                    │
     * └─────────────────────────────────────────────────────────┘
     *
     * 白話：翻開帳戶名冊，把所有錢包一筆一筆列出來，
     *       照著編號從小到大排好（1, 2, 3, 4...）
     *
     * @return 一疊「帳戶卡片」（List<AssetAccount>）
     *         如果沒有任何帳戶，就回傳空列表 []
     *
     * 使用範例：
     * val accounts = accountDao.getAllAccounts()
     * accounts.forEach { println("${it.name}: ${it.balance}") }
     * // 輸出：現金: 10000 / 信用卡: 30000 / Line Pay: 5000
     */
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): List<AssetAccount>

    /**
     * 🔍 查詢特定名稱的帳戶，例如「現金」、「信用卡」
     *
     * SQL 語法翻譯：
     * ┌─────────────────────────────────────────────────────────┐
     * │ SELECT * FROM accounts WHERE name = :accountName LIMIT 1│
     * │   ↓              ↓                ↓            ↓       │
     * │   │              │                │            └─ 只拿第一筆
     * │   │              │                └─ 名稱等於傳進來的參數
     * │   │              └─ 從 accounts 表
     * │   └─ 拿出所有欄位                                       │
     * └─────────────────────────────────────────────────────────┘
     *
     * 白話：在帳戶名冊裡翻找，有沒有叫「現金」的錢包？
     *       如果找到一個就停下來（LIMIT 1），不用繼續找了
     *
     * 為什麼要 LIMIT 1？
     * 因為理論上不應該有兩個同名的帳戶（設計上會避免）
     * 但萬一真的發生了，只拿第一個，不會讓 App 當機
     *
     * @param accountName 要尋找的帳戶名稱（例如「現金」）
     * @return 找到回傳該帳戶，找不到回傳 null（表示沒有這個錢包）
     *
     * 使用範例：
     * val cash = accountDao.getAccountByName("現金")
     * if (cash != null) {
     *     println("現金還有 ${cash.balance} 元")
     * } else {
     *     println("你沒有現金帳戶喔！")
     * }
     */
    @Query("SELECT * FROM accounts WHERE name = :accountName LIMIT 1")
    fun getAccountByName(accountName: String): AssetAccount?
    // 回傳 ? 表示「可能找不到，沒有就回傳 null（空的）」
    // 要編輯某個帳戶前，要先從資料庫查出來

    // ========== ✍️ 寫入操作（新增） ==========

    /**
     * ➕ 新增一個帳戶
     *
     * @Insert 是 Room 的魔法標籤，只要貼上去，
     *         Room 會自動幫我們寫好 INSERT 的 SQL 語法！
     *
     * onConflict = OnConflictStrategy.REPLACE：衝突處理策略
     *
     * 什麼是衝突？
     * 如果你要新增的帳戶，ID 已經存在（例如已經有一個 id=1 的帳戶）
     * 這時候就會發生「衝突」
     *
     * 處理方式有幾種：
     * - REPLACE：用新的蓋掉舊的（像用立可帶蓋掉寫錯的字）
     * - ABORT：取消這次操作（像踩煞車）
     * - IGNORE：保留舊的，忽略新的
     * - FAIL：直接失敗給你看
     *
     * 這裡用 REPLACE：如果同樣 ID 已經存在，就用新的蓋掉舊的
     *
     * @param account 要新增的帳戶物件（AssetAccount）
     *
     * 使用範例：
     * val newAccount = AssetAccount(
     *     id = 0,  // id = 0 表示讓資料庫自動產生新編號
     *     name = "悠遊卡",
     *     balance = 500
     * )
     * accountDao.insertAccount(newAccount)
     * // ✅ 成功！悠遊卡新增到資料庫了
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccount(account: AssetAccount)

    /**
     * 📦 一次新增多個帳戶（例如初次安裝 App 時匯入預設帳戶）
     *
     * 白話：與其一本一本塞，不如一次把一疊書都放進書架！
     *
     * 這個方法跟上面那個很像，只是參數變成「帳戶列表」
     *
     * @param accounts 要新增的帳戶列表（List<AssetAccount>）
     *
     * 使用範例：
     * val defaultAccounts = listOf(
     *     AssetAccount(name = "現金", balance = 0),
     *     AssetAccount(name = "信用卡", balance = 0)
     * )
     * accountDao.insertAccounts(defaultAccounts)
     * // ✅ 成功！兩個預設帳戶一次加入
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccounts(accounts: List<AssetAccount>)

    // ========== ✏️ 寫入操作（更新） ==========

    /**
     * ✏️ 修改某個帳戶的資料（例如改帳戶名稱或初始金額）
     *
     * @Update 是另一個魔法標籤，Room 會根據「主鍵 id」自動找到該筆並更新
     *
     * 白話：拿著有編號的帳戶卡片，找到書架上對應的那本，
     *       然後用新的內容覆蓋上去～
     *
     * 不需要寫 SQL！Room 會自動幫你：
     * 1. 讀取 account 物件的 id
     * 2. 用 WHERE id = :account.id 找到那筆資料
     * 3. 把其他欄位（name, balance, updated_at）更新成新的值
     *
     * @param account 要更新的帳戶物件（必須有 id）
     *
     * 使用範例：
     * val cash = accountDao.getAccountByName("現金")
     * val updatedCash = cash.copy(
     *     name = "新台幣現金",  // 改名
     *     balance = 20000,      // 改餘額
     *     updatedAt = System.currentTimeMillis()
     * )
     * accountDao.updateAccount(updatedCash)
     * // ✅ 成功！現金變成「新台幣現金」，餘額變成 20000
     */
    @Update
    fun updateAccount(account: AssetAccount)

    // ========== 🗑️ 寫入操作（刪除） ==========

    /**
     * 🗑️ 刪除某個帳戶（根據 id）
     *
     * 白話：從書架上把某本書拿掉（永久消失）
     *
     * @param accountId 要刪除的帳戶 ID
     *
     * 使用範例：
     * accountDao.deleteAccount(5)  // 刪除 id = 5 的帳戶
     *
     * 注意：刪除後無法復原！使用者確認後才能執行～
     */
    @Query("DELETE FROM accounts WHERE id = :accountId")
    fun deleteAccount(accountId: Int)

    /**
     * 💰 調整某個帳戶的餘額（記帳 App 的核心！）
     *
     * 這可能是整個記帳 App 最常被呼叫的方法！
     * 每次記帳（支出或收入），都會用到它～
     *
     * SQL 語法翻譯：
     * ┌─────────────────────────────────────────────────────────┐
     * │ UPDATE accounts                                        │
     * │ SET balance = balance + :delta,                        │
     * │     updated_at = :updatedAt                            │
     * │ WHERE name = :accountName                              │
     * └─────────────────────────────────────────────────────────┘
     *
     * 為什麼要直接在資料庫裡做「balance + delta」？
     *
     * 好處 1：執行緒安全
     * 如果同時有兩個人在記帳（例如收入 +500 和支出 -200），
     * 直接在資料庫裡加減，比「先讀出來、在 App 裡加減、再存回去」
     * 更安全，不會發生「你加的時候我還在用舊資料」的問題
     *
     * 好處 2：簡潔
     * 不用自己寫「取得目前餘額 → 計算新餘額 → 存回去」這三個步驟
     *
     * @param accountName 要調整的帳戶名稱（例如「現金」）
     * @param delta 變動值（正數 = 加錢，負數 = 扣錢）
     * @param updatedAt 更新時間戳記（記錄異動時間）
     *
     * 使用範例：
     * // 支出 150 元（扣錢）
     * accountDao.adjustAccountBalance("現金", -150, System.currentTimeMillis())
     *
     * // 收入 5000 元（加錢）
     * accountDao.adjustAccountBalance("銀行", 5000, System.currentTimeMillis())
     */
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

    /**
     * 💣 全部刪除！通常用來「重置 App 資料」或測試時清空
     *
     * 白話：把整個書架清空，一本書都不留！
     *
     * 使用範例：
     * accountDao.clearAll()  // 清空所有帳戶！
     *
     * ⚠️ 警告：這個操作無法復原！
     *        只在「清除所有資料」功能中使用
     */
    @Query("DELETE FROM accounts")
    fun clearAll()
}

/**
 * 📚【小教室：為什麼要用 DAO？】
 *
 * 沒有 DAO 的年代（用原生 SQL）：
 * ┌─────────────────────────────────────────────────────────┐
 * │ val db = database.writableDatabase                     │
 * │ val values = ContentValues()                           │
 * │ values.put("name", "現金")                             │
 * │ values.put("balance", 10000)                           │
 * │ db.insert("accounts", null, values)                    │
 * │ // 要自己寫欄位名稱、自己處理 null、容易打錯字...      │
 * └─────────────────────────────────────────────────────────┘
 *
 * 有 DAO 的現代：
 * ┌─────────────────────────────────────────────────────────┐
 * │ accountDao.insertAccount(                              │
 * │     AssetAccount(name = "現金", balance = 10000)       │
 * │ )                                                      │
 * │ // 簡潔、型別安全、編譯器幫你檢查！                    │
 * └─────────────────────────────────────────────────────────┘
 *
 * 這就是 Room + DAO 的威力！✨
 */

/**
 * 💡【關於 OnConflictStrategy 的小補充】
 *
 * 情境：你想要新增一個帳戶，但是 id=1 的帳戶已經存在了
 *
 * 選項說明：
 * ┌────────────┬─────────────────────────────────────────┐
 * │ 策略        │ 行為                                     │
 * ├────────────┼─────────────────────────────────────────┤
 * │ REPLACE    │ 用新的蓋掉舊的（像用立可帶塗掉重寫）       │
 * │ ABORT      │ 取消新增，保持原樣（踩煞車）               │
 * │ IGNORE     │ 保留舊的，忽略新的（當沒這件事）           │
 * │ FAIL       │ 直接讓程式失敗（發出錯誤）                 │
 * │ ROLLBACK   │ 回復到之前的狀態（像時光倒流）             │
 * └────────────┴─────────────────────────────────────────┘
 *
 * 我們的 App 用 REPLACE 最適合，因為：
 * - 如果使用者想新增一個已經存在的帳戶（不太可能發生）
 * - 用新的蓋掉舊的比較合理
 */

/**
 * 🎭【完整使用範例 - 劇場版】
 *
 * 場景：使用者新增一筆「午餐 150 元」從「現金」帳戶支出
 *
 * 第 1 幕：Repository 呼叫 adjustAccountBalance
 *    accountDao.adjustAccountBalance("現金", -150, now)
 *
 * 第 2 幕：Room 執行 SQL
 *    UPDATE accounts
 *    SET balance = balance - 150, updated_at = 1744012800000
 *    WHERE name = '現金'
 *
 * 第 3 幕：假設原本現金是 10000
 *    新餘額 = 10000 - 150 = 9850
 *
 * 第 4 幕：完成！現金餘額變成 9850
 *
 * 整個過程不到 1 毫秒，使用者完全無感～
 */