package com.example.cococoin

/**
 * 📦【資料快照】
 *
 * 想像你要搬家，所有東西散落在房子各處：
 * - 記帳本（交易紀錄）在書桌上
 * - 錢包（帳戶）在抽屜裡
 * - 預算表在冰箱上貼著
 * - 分類標籤貼在箱子上
 *
 * 這個 CocoCoinSnapshot 就像一個「超級打包箱」，
 * 把上面所有的東西統統裝進去！
 *
 * 為什麼需要打包？
 * 1️⃣ 雲端備份：把整個 App 的資料上傳到 Google 雲端
 * 2️⃣ 雲端下載：從雲端下載回來還原
 * 3️⃣ 手機備份：存到手機內部儲存空間
 * 4️⃣ 版本比較：看哪個版本的資料比較新（雲端 vs 手機）
 *
 * 有了這個「超級打包箱」，搬家（資料同步）就輕鬆多了！
 *
 * @param transactions 所有交易紀錄（午餐、晚餐、買衣服...）
 * @param accounts 所有帳戶（現金、信用卡、Line Pay...）
 * @param budgets 所有預算設定（這個月餐飲費 5000 元...）
 * @param categories 所有分類（餐飲、交通、購物...加上可愛的 emoji）
 */
data class CocoCoinSnapshot(
    val transactions: List<Transaction> = emptyList(),  // 📒 所有交易紀錄（記帳本就是它）
    val accounts: List<AssetAccount> = emptyList(),     // 💰 所有帳戶（你的錢包們）
    val budgets: List<BudgetSetting> = emptyList(),     // 📊 所有預算設定（花錢的計畫表）
    val categories: List<TransactionCategoryDefinition> = emptyList()  // 🏷️ 所有分類（幫每筆錢貼標籤）
) {

    /**
     * 🕳️ 檢查這個快照是不是空的（完全沒有任何資料）
     *
     * 白話：打開打包箱，看看裡面是不是空空的，連一隻螞蟻都沒有？
     *
     * 什麼時候會用到？
     * - 使用者第一次安裝 App，還沒有任何記帳資料時
     * - 從雲端下載失敗，得到一個空的快照時
     * - 清除所有資料後，檢查是否清乾淨了
     *
     * @return true 表示是空的（什麼都沒有），false 表示裡面有資料
     *
     * 使用範例：
     * if (snapshot.isEmpty()) {
     *     showMessage("還沒有任何資料，趕快記一筆吧！")
     * } else {
     *     uploadToCloud(snapshot)
     * }
     */
    fun isEmpty(): Boolean {
        // 檢查四大類資料是否都是空的
        // 就像檢查打包箱裡：記帳本空的 AND 錢包空的 AND 預算表空的 AND 分類標籤空的
        return transactions.isEmpty() &&
                accounts.isEmpty() &&
                budgets.isEmpty() &&
                categories.isEmpty()
    }

    /**
     * 🕐 找出整份快照中「最新的更新時間」
     *
     * 白話：把打包箱裡所有東西翻出來，看哪一個東西最後被修改過，
     *       然後把那個「最後修改時間」記錄下來。
     *
     * 為什麼需要這個？
     * 想像你有兩個打包箱：
     * - 箱子 A（手機裡的資料）：最後修改時間是 2026/04/06 14:30
     * - 箱子 B（雲端上的資料）：最後修改時間是 2026/04/05 09:15
     *
     * 比較「最新更新時間」就知道：箱子 A 比較新！
     * 這樣資料合併時，就知道要以哪個為主～
     *
     * 🔥 這是資料同步（Data Sync）的核心邏輯！
     *
     * @return 最晚的更新時間（毫秒 timestamp），如果全部都沒資料則回傳 0
     *
     * 使用範例：
     * val localLatest = localSnapshot.latestUpdatedAt()
     * val cloudLatest = cloudSnapshot.latestUpdatedAt()
     * if (cloudLatest > localLatest) {
     *     // 雲端資料比較新，下載回來！
     *     downloadFromCloud()
     * }
     */
    fun latestUpdatedAt(): Long {
        // 🧩 步驟 1：把所有資料的 updatedAt 時間收集到一個列表裡

        // buildList 是 Kotlin 的便利函數：
        // 它會幫我們建立一個新的列表，我們只需要把東西 add 進去就好
        // 白話：拿出一個空箱子，然後把所有東西的時間標籤都丟進去
        return buildList {
            // 📒 把所有「交易」的更新時間丟進去
            // 例如：午餐(14:30)、晚餐(13:20)、購物(10:00)
            addAll(transactions.map { it.updatedAt })

            // 💰 把所有「帳戶」的更新時間丟進去
            // 例如：現金(09:00)、信用卡(08:30)
            addAll(accounts.map { it.updatedAt })

            // 📊 把所有「預算」的更新時間丟進去
            // 例如：餐飲預算(昨天 20:00)
            addAll(budgets.map { it.updatedAt })

            // 🏷️ 把所有「分類」的更新時間丟進去
            // 例如：新增「娛樂」分類(上週五)
            addAll(categories.map { it.updatedAt })

        }.maxOrNull() ?: 0L
        // 📊 步驟 2：找出最大值（最新的時間）
        // maxOrNull()：找出列表裡最大的數字（時間戳越大表示越晚）
        // ?: 0L：如果列表是空的（沒有任何資料），就回傳 0

        /**
         * ⏰ 時間戳小教室：
         *
         * 電腦在記錄時間時，不是記「2026/04/06 14:30」這種文字，
         * 而是記一個很長的數字（從 1970/01/01 開始算的毫秒數）
         *
         * 例如：
         * 1744012345678 → 2026/04/06 14:30
         * 1743925945678 → 2026/04/05 14:30
         *
         * 數字越大 = 時間越晚 = 越新
         */
    }

    /**
     * 💡 小知識補充：為什麼每個資料都有 updatedAt？
     *
     * 在 Transaction、AssetAccount、BudgetSetting、TransactionCategoryDefinition 裡，
     * 都有一個 updatedAt 欄位（Long 類型，時間戳）
     *
     * 每次你「新增」、「修改」、「刪除」一筆資料時，
     * 這個 updatedAt 就會自動更新成「現在的時間」
     *
     * 就像包裹上的「最後處理時間」郵戳一樣～
     *
     * 這樣合併資料時，才知道哪個版本比較新！
     */
}

/**
 * 🎁【快照的使用情境 - 劇場版】
 *
 * 情境 1：雲端備份
 * ┌─────────────────────────────────────────┐
 * │ 使用者按下「備份到雲端」                    │
 * │         ↓                                │
 * │ 把資料打包成 CocoCoinSnapshot             │
 * │         ↓                                │
 * │ 上傳到 Firebase / Google Drive           │
 * │         ↓                                │
 * │ ✅ 備份完成！下次換手機也能還原～           │
 * └─────────────────────────────────────────┘
 *
 * 情境 2：雲端還原
 * ┌─────────────────────────────────────────┐
 * │ 使用者按下「從雲端還原」                    │
 * │         ↓                                │
 * │ 從雲端下載 CocoCoinSnapshot               │
 * │         ↓                                │
 * │ 比較 latestUpdatedAt()                   │
 * │ （雲端時間 > 手機時間 ? 下載 : 不上傳）     │
 * │         ↓                                │
 * │ 解壓縮快照，寫入資料庫                     │
 * │         ↓                                │
 * │ ✅ 還原完成！以前的記帳資料都回來了～        │
 * └─────────────────────────────────────────┘
 *
 * 情境 3：合併衝突（多裝置同時記帳）
 * ┌─────────────────────────────────────────┐
 * │ 手機 A：午餐 100 元 (14:30 修改)          │
 * │ 手機 B：晚餐 200 元 (15:00 修改)          │
 * │         ↓                                │
 * │ 上傳到雲端時發現衝突！                      │
 * │         ↓                                │
 * │ 比較每筆資料的 updatedAt：                │
 * │ - 午餐：手機 A 的比較新 → 保留 A 的        │
 * │ - 晚餐：手機 B 的比較新 → 保留 B 的        │
 * │         ↓                                │
 * │ ✅ 聰明的合併完成！沒有遺失任何資料～        │
 * └─────────────────────────────────────────┘
 */