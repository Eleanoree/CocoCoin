package com.example.cococoin

import android.content.Context
// 匯入 JSON 解析工具（用來把分類資料轉成文字儲存，或從文字讀回來）
import org.json.JSONArray   // JSON 陣列，一串清單
import org.json.JSONObject  // JSON 物件，一個有名字的盒子

// 交易分類儲存器：負責把「使用者自訂的分類」存到手機裡
class TransactionCategoryStore(context: Context) {

    // SharedPreferences：手機裡的一個小筆記本，可以存簡單的資料
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 讀取所有分類（如果使用者沒自訂過，就回傳預設分類）
    fun getCategories(): List<TransactionCategoryDefinition> {
        // 從筆記本裡讀取儲存的分類 JSON 字串
        val savedJson = prefs.getString(KEY_CATEGORIES_JSON, null)

        // 如果筆記本裡沒有資料（第一次使用），就回傳預設分類
        if (savedJson.isNullOrBlank()) {
            return TransactionCategoryCatalog.defaultCategories()
        }

        // 試著把 JSON 轉回分類清單，如果失敗（資料壞掉）就回傳預設分類
        return runCatching {
            normalizeCategories(decode(savedJson))  // 讀取並整理分類
        }.getOrElse {
            TransactionCategoryCatalog.defaultCategories()  // 出錯就用預設的
        }
    }

    // 儲存分類（把使用者自訂的分類寫進筆記本）
    fun replaceCategories(categories: List<TransactionCategoryDefinition>) {
        // 先整理分類（去除重複、確保每種至少有一個分類）
        val normalized = normalizeCategories(categories)
        // 轉成 JSON 字串存到筆記本
        prefs.edit().putString(KEY_CATEGORIES_JSON, encode(normalized)).apply()
    }

    // 清除使用者的自訂分類（恢復成預設）
    fun clearCustomizations() {
        prefs.edit().remove(KEY_CATEGORIES_JSON).apply()
    }

    // 整理分類（去除重複、過濾無效、確保每種至少有一個分類）
    private fun normalizeCategories(categories: List<TransactionCategoryDefinition>): List<TransactionCategoryDefinition> {
        // 如果傳進來的清單是空的，就用預設分類
        if (categories.isEmpty()) {
            return TransactionCategoryCatalog.defaultCategories()
        }

        // 用 LinkedHashMap 來去除重複（key 是「類型||名稱」，value 是分類物件）
        val deduped = linkedMapOf<String, TransactionCategoryDefinition>()

        categories.forEach { category ->
            // 檢查類型是不是合法的（只能是「支出」或「收入」）
            val type = category.type.takeIf {
                it == TransactionCategoryCatalog.TYPE_EXPENSE || it == TransactionCategoryCatalog.TYPE_INCOME
            } ?: return@forEach  // 不是合法類型就跳過

            // 名稱不能是空白
            val name = category.name.trim()
            if (name.isEmpty()) return@forEach

            // 如果沒有圖示，就給一個預設圖示
            val icon = category.icon.ifBlank { TransactionCategoryCatalog.fallbackIcon(type) }

            // 用「類型||名稱」當作鑰匙，這樣同類型同名稱只會留一個
            val key = "$type||$name"
            val normalized = category.copy(type = type, name = name, icon = icon)

            val existing = deduped[key]
            // 如果還沒有這筆，或者新的更新時間比較新，就保留新的
            if (existing == null || normalized.updatedAt >= existing.updatedAt) {
                deduped[key] = normalized
            }
        }

        // 把整理好的分類轉成可修改的清單
        val merged = deduped.values.toMutableList()

        // 確保「支出」和「收入」都至少有一個分類（如果被刪光了就補回來）
        ensureTypeHasAtLeastOneCategory(merged, TransactionCategoryCatalog.TYPE_EXPENSE)
        ensureTypeHasAtLeastOneCategory(merged, TransactionCategoryCatalog.TYPE_INCOME)

        // 排序：先按類型（支出在前、收入在後），再按更新時間
        return merged.sortedWith(
            compareBy<TransactionCategoryDefinition> { it.type }.thenBy { it.updatedAt }
        )
    }

    // 確保某種類型至少有一個分類（避免使用者把某個類型的分類刪光）
    private fun ensureTypeHasAtLeastOneCategory(
        categories: MutableList<TransactionCategoryDefinition>,
        type: String
    ) {
        // 如果這種類型沒有任何分類，就把預設分類中這種類型的全部加進來
        if (categories.none { it.type == type }) {
            categories += TransactionCategoryCatalog.defaultCategories().filter { it.type == type }
        }
    }

    // 把分類清單轉成 JSON 字串（用來存到筆記本）
    private fun encode(categories: List<TransactionCategoryDefinition>): String {
        return JSONArray().apply {  // 建立一個 JSON 陣列
            categories.forEach { category ->
                put(  // 把每個分類放進陣列
                    JSONObject().apply {
                        put("type", category.type)      // 類型
                        put("name", category.name)      // 名稱
                        put("icon", category.icon)      // 圖示
                        put("updatedAt", category.updatedAt)  // 更新時間
                    }
                )
            }
        }.toString()  // 轉成字串
    }

    // 把 JSON 字串轉回分類清單（從筆記本讀回來時用）
    private fun decode(json: String): List<TransactionCategoryDefinition> {
        val array = JSONArray(json)  // 把字串變成 JSON 陣列
        return buildList {  // 建立一個新的清單
            for (index in 0 until array.length()) {  // 一個一個讀
                val item = array.optJSONObject(index) ?: continue  // 跳過空的
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

    // 存放固定不變的常數
    companion object {
        private const val PREFS_NAME = "cococoin_category_store"  // 筆記本名稱
        private const val KEY_CATEGORIES_JSON = "categories_json" // 儲存分類的欄位名稱
    }
}