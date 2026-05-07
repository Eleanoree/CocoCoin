// ============================================================
// 📦 備份檔編碼器/解碼器 — 白話文：資料的「打包/拆包大師」
// ============================================================
//
// 情境劇：想像你要把一整間房間的東西（交易、帳戶、預算、分類）
//         打包成一個箱子（JSON 檔案）搬到新家
//         這個 Codec 就是那位專業的「打包員」：
//           📦 打包（encode）：把亂七八糟的資料整齊地放進 JSON 箱子
//           🔓 拆包（decode）：把 JSON 箱子裡的東西完整地拿出來
//
// JSON 是什麼？白話文：一種「文字格式的箱子」，長得像這樣：
//   {
//     "format": "cococoin-backup",
//     "version": 2,
//     "transactions": [{"id": 1, "amount": 100}, ...],
//     "accounts": [{"name": "現金", "balance": 5000}, ...]
//   }
//
// object 關鍵字：單例模式，白話文就是「全公司只有一位打包員」
// 不管在哪裡呼叫 CocoCoinBackupCodec，都是同一個人！
// ============================================================
package com.example.cococoin

import org.json.JSONArray
import org.json.JSONObject

object CocoCoinBackupCodec {

    // ============================================================
    // 📤 編碼（打包）— 白話文：把資料快照裝進 JSON 箱子
    // ============================================================
    // 輸入：CocoCoinSnapshot（記憶體中的資料）
    // 輸出：String（JSON 格式的文字，可以存成檔案）
    //
    // 流程：
    //   1. 建立一個空箱子（JSONObject）
    //   2. 貼上識別標籤（format、version、exportedAt）
    //   3. 把交易、帳戶、預算、分類分類打包，放進不同隔層
    //   4. 蓋上蓋子，轉成文字（toString）
    fun encode(snapshot: CocoCoinSnapshot): String {
        // 建立一個空的 JSON 箱子
        val root = JSONObject().apply {
            // ----- 貼上識別標籤（讓拆包時知道這是誰的箱子）-----
            // format：這是 CocoCoin 的備份檔（不是別的 App 的亂檔）
            put("format", "cococoin-backup")
            // version：備份格式版本（未來升級時可以相容舊檔案）
            // 例如以後加了新欄位，version 改成 3，還是可以讀 version 2 的檔案
            put("version", 2)
            // exportedAt：什麼時候打包的（毫秒時間戳，方便使用者知道這是哪次備份）
            put("exportedAt", System.currentTimeMillis())

            // ----- 📦 隔層 1：交易資料 -----
            put("transactions", JSONArray().apply {
                snapshot.transactions.forEach { transaction ->
                    // 每筆交易變成一個小盒子，裡面放它的所有欄位
                    put(
                        JSONObject().apply {
                            put("id", transaction.id)
                            put("type", transaction.type)           // 支出/收入
                            put("category", transaction.category)   // 餐飲、交通...
                            put("amount", transaction.amount)       // 金額
                            put("note", transaction.note)           // 備註
                            put("time", transaction.time)           // 交易時間
                            put("accountName", transaction.accountName)  // 帳戶名稱
                            put("updatedAt", transaction.updatedAt) // 最後修改時間
                        }
                    )
                }
            })

            // ----- 💰 隔層 2：帳戶資料 -----
            put("accounts", JSONArray().apply {
                snapshot.accounts.forEach { account ->
                    put(
                        JSONObject().apply {
                            put("id", account.id)
                            put("name", account.name)       // 帳戶名稱（現金、信用卡...）
                            put("balance", account.balance) // 餘額
                            put("updatedAt", account.updatedAt)
                        }
                    )
                }
            })

            // ----- 📊 隔層 3：預算資料 -----
            put("budgets", JSONArray().apply {
                snapshot.budgets.forEach { budget ->
                    put(
                        JSONObject().apply {
                            put("year", budget.year)       // 年份（2026）
                            put("month", budget.month)     // 月份（1-12）
                            put("amount", budget.amount)   // 預算金額
                            put("updatedAt", budget.updatedAt)
                        }
                    )
                }
            })

            // ----- 🏷️ 隔層 4：分類資料 -----
            put("categories", JSONArray().apply {
                snapshot.categories.forEach { category ->
                    put(
                        JSONObject().apply {
                            put("type", category.type)     // 支出/收入
                            put("name", category.name)     // 餐飲、交通...
                            put("icon", category.icon)     // emoji 圖示
                            put("updatedAt", category.updatedAt)
                        }
                    )
                }
            })
        }

        // 把箱子轉成文字，參數 2 表示縮排 2 個空格
        // 白話文：把箱子裡的東西整理得整整齊齊（方便人類閱讀）
        // 如果不縮排，會變成一長串亂七八糟的文字
        return root.toString(2)
    }

    // ============================================================
    // 📥 解碼（拆包）— 白話文：把 JSON 箱子還原成資料快照
    // ============================================================
    // 輸入：String（JSON 格式的文字，從備份檔案讀進來的）
    // 輸出：CocoCoinSnapshot（記憶體中的資料）
    //
    // 流程：
    //   1. 打開箱子（JSONObject(json)）
    //   2. 檢查識別標籤（看看是不是 CocoCoin 的備份檔）
    //   3. 從不同隔層拿出交易、帳戶、預算、分類
    //   4. 組裝成一個完整的資料快照
    fun decode(json: String): CocoCoinSnapshot {
        // 打開箱子（把文字轉回 JSON 物件）
        val root = JSONObject(json)

        // ----- 檢查識別標籤（防止使用者匯入錯誤的檔案）-----
        // require 就像一個守門員：如果條件不符，就拋出錯誤，不繼續執行
        val format = root.optString("format")
        require(format == "cococoin-backup") { "不是 CocoCoin 可辨識的備份檔" }
        // 白話文：「這不是 CocoCoin 的備份檔喔，你是不是拿錯檔案了？」

        // ----- 從不同隔層拿出資料（如果沒找到就給空清單）-----
        val transactions = root.optJSONArray("transactions")?.toTransactions() ?: emptyList()
        val accounts = root.optJSONArray("accounts")?.toAccounts() ?: emptyList()
        val budgets = root.optJSONArray("budgets")?.toBudgets() ?: emptyList()
        val categories = root.optJSONArray("categories")?.toCategories() ?: emptyList()

        // ----- 把拿出來的東西組裝成一個完整的快照 -----
        return CocoCoinSnapshot(
            transactions = transactions,
            accounts = accounts,
            budgets = budgets,
            categories = categories
        )
    }

    // ============================================================
    // 🔧 輔助函式們 — 白話文：打包員的「分類工具」
    // ============================================================
    // 這些擴充函式負責把 JSON 陣列轉換成對應的資料清單
    // 白話文：從箱子裡把「交易」那一疊拿出來，一張一張恢復原狀

    // 把 JSON 陣列轉成交易列表
    private fun JSONArray?.toTransactions(): List<Transaction> {
        if (this == null) return emptyList()  // 沒這隔層？給空清單
        return buildList {
            // 一個一個取出盒子，把裡面的東西恢復成 Transaction 物件
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue  // 跳過空的
                add(
                    Transaction(
                        id = item.optInt("id", 0),
                        type = item.optString("type"),
                        category = item.optString("category"),
                        amount = item.optInt("amount", 0),
                        note = item.optString("note"),
                        time = item.optString("time"),
                        accountName = item.optString("accountName", "未指定帳戶"),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    // 把 JSON 陣列轉成帳戶列表
    private fun JSONArray?.toAccounts(): List<AssetAccount> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    AssetAccount(
                        id = item.optInt("id", 0),
                        name = item.optString("name"),
                        balance = item.optInt("balance", 0),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    // 把 JSON 陣列轉成預算列表
    private fun JSONArray?.toBudgets(): List<BudgetSetting> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    BudgetSetting(
                        year = item.optInt("year", 0),
                        month = item.optInt("month", 0),
                        amount = item.optInt("amount", 0),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    // 把 JSON 陣列轉成分類列表
    private fun JSONArray?.toCategories(): List<TransactionCategoryDefinition> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    TransactionCategoryDefinition(
                        type = item.optString("type"),
                        name = item.optString("name"),
                        icon = item.optString("icon"),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}