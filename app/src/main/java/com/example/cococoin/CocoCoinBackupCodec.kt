package com.example.cococoin

import org.json.JSONArray
import org.json.JSONObject

// 備份檔編碼器/解碼器：
// 把資料快照（CocoCoinSnapshot）轉成 JSON 字串（匯出備份時用），也把 JSON 字串轉回資料快照（匯入備份時用）
object CocoCoinBackupCodec {

    // 編碼：把資料快照轉成 JSON 字串
    fun encode(snapshot: CocoCoinSnapshot): String {
        val root = JSONObject().apply {
            // 檔案識別（用來檢查是不是 CocoCoin 的備份檔）
            put("format", "cococoin-backup")
            // 備份格式版本（未來升級時可相容）
            put("version", 2)
            // 匯出時間（毫秒）
            put("exportedAt", System.currentTimeMillis())

            // 交易資料
            put("transactions", JSONArray().apply {
                snapshot.transactions.forEach { transaction ->
                    put(
                        JSONObject().apply {
                            put("id", transaction.id)
                            put("type", transaction.type)
                            put("category", transaction.category)
                            put("amount", transaction.amount)
                            put("note", transaction.note)
                            put("time", transaction.time)
                            put("accountName", transaction.accountName)
                            put("updatedAt", transaction.updatedAt)
                        }
                    )
                }
            })

            // 帳戶資料
            put("accounts", JSONArray().apply {
                snapshot.accounts.forEach { account ->
                    put(
                        JSONObject().apply {
                            put("id", account.id)
                            put("name", account.name)
                            put("balance", account.balance)
                            put("updatedAt", account.updatedAt)
                        }
                    )
                }
            })

            // 預算資料
            put("budgets", JSONArray().apply {
                snapshot.budgets.forEach { budget ->
                    put(
                        JSONObject().apply {
                            put("year", budget.year)
                            put("month", budget.month)
                            put("amount", budget.amount)
                            put("updatedAt", budget.updatedAt)
                        }
                    )
                }
            })

            // 分類資料
            put("categories", JSONArray().apply {
                snapshot.categories.forEach { category ->
                    put(
                        JSONObject().apply {
                            put("type", category.type)
                            put("name", category.name)
                            put("icon", category.icon)
                            put("updatedAt", category.updatedAt)
                        }
                    )
                }
            })
        }

        // 轉成 JSON 字串，參數 2 表示縮排 2 個空格（方便閱讀）
        return root.toString(2)
    }

    // 解碼：把 JSON 字串轉回資料快照
    fun decode(json: String): CocoCoinSnapshot {
        val root = JSONObject(json)

        // 檢查格式是否正確
        val format = root.optString("format")
        require(format == "cococoin-backup") { "不是 CocoCoin 可辨識的備份檔" }

        // 解析各個區塊
        val transactions = root.optJSONArray("transactions").toTransactions()
        val accounts = root.optJSONArray("accounts").toAccounts()
        val budgets = root.optJSONArray("budgets").toBudgets()
        val categories = root.optJSONArray("categories").toCategories()

        return CocoCoinSnapshot(
            transactions = transactions,
            accounts = accounts,
            budgets = budgets,
            categories = categories
        )
    }

    // 把 JSON 陣列轉成交易列表
    private fun JSONArray?.toTransactions(): List<Transaction> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
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