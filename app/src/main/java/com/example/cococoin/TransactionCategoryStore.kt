// 🗂️ 交易分類儲存器 — 白話文：分類的倉庫管理員
//
// 情境劇：使用者打開 App，看到「餐飲」、「交通」這些預設分類
//        但使用者覺得不夠，自己加了「手搖飲」、「課金抽卡」...
//        這個類別就是負責把這些「自訂分類」存到手機裡，下次打開 App 還看得到！
//
// 技術小抄：使用 SharedPreferences（手機內建的簡易資料庫）+ JSON（把分類清單打包成文字）
package com.example.cococoin

import android.content.Context
// JSON 解析工具 — 就像是「打包行李的壓縮袋」和「拆包裹的剪刀」
import org.json.JSONArray   // JSON 陣列：一串清單，像是一串掛起來的五花肉，一條一條的
import org.json.JSONObject  // JSON 物件：一個有名字的盒子，像是 {名字: "小明", 年齡: 18}

// 這個類別需要一個 Context（就像是倉庫的鑰匙，用來打開手機的儲存空間）
class TransactionCategoryStore(context: Context) {

    // 📓 SharedPreferences：手機裡的「小筆記本」，可以存簡單的文字資料
    // 白話文：就像你隨身帶的小本本，裡面寫了幾行字，關機也不會消失
    // MODE_PRIVATE 表示：這本筆記本只有你的 App 能看，別人的 App 偷看不到
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========================= 讀 取 分 類 =========================
    // 📖 讀取所有分類（如果使用者從來沒自訂過，就給預設的分類）
    //
    // 白話文：「倉庫管理員，把分類清單給我！」
    // 管理員會先看看筆記本裡有沒有紀錄，沒有就給預設的（餐飲、交通...）
    fun getCategories(): List<TransactionCategoryDefinition> {
        // 從筆記本裡翻翻看，有沒有 KEY_CATEGORIES_JSON 這一頁
        // 沒有的話就 null（第一次使用 App 的情況）
        val savedJson = prefs.getString(KEY_CATEGORIES_JSON, null)

        // 如果筆記本是空的（第一次使用），就拿出預設分類
        // 就像新買的筆記本，第一頁幫你印好「餐飲、交通、娛樂...」
        if (savedJson.isNullOrBlank()) {
            return TransactionCategoryCatalog.defaultCategories()
        }

        // 試著把 JSON 字串拆開還原成分類清單
        // runCatching 就像「安全網」：如果 JSON 格式壞掉了（被駭或手殘修改），不會讓 App 當機
        return runCatching {
            normalizeCategories(decode(savedJson))  // 先解壓縮，再整理一下分類
        }.getOrElse {
            // 出錯了就投降（比如被外星人干擾），拿出預設分類（至少讓使用者還能用）
            TransactionCategoryCatalog.defaultCategories()
        }
    }

    // ========================= 儲 存 分 類 =========================
    // 💾 儲存分類（把使用者改過的分類寫進筆記本）
    //
    // 白話文：使用者說「我要把『餐飲』改成『吃貨人生』」，管理員就乖乖記下來
    fun replaceCategories(categories: List<TransactionCategoryDefinition>) {
        // 先整理一下：去除重複的、過濾掉亂七八糟的資料
        val normalized = normalizeCategories(categories)
        // 把整理好的分類打包成 JSON 字串，寫進筆記本
        // .apply() 表示：慢慢寫沒關係，不用急著馬上存好（非同步）
        prefs.edit().putString(KEY_CATEGORIES_JSON, encode(normalized)).apply()
    }

    // ========================= 清 除 自 訂 =========================
    // 🧹 清除使用者的自訂分類（恢復成出廠設定）
    //
    // 白話文：使用者玩壞了想重來，大喊「恢復預設！」
    // 管理員就把筆記本裡那一頁撕掉，下次讀取就會用預設的
    fun clearCustomizations() {
        prefs.edit().remove(KEY_CATEGORIES_JSON).apply()
    }

    // ========================= 整 理 分 類（核心邏輯） =========================
    // 🧹✨ 整理分類 — 白話文：分類界的「清潔大隊 + 糾察隊」
    //
    // 做的事情超多：
    // 1. 去除重複（同類型同名稱的只留一筆）
    // 2. 過濾無效（類型不是支出/收入的丟掉、名稱為空的丟掉）
    // 3. 補圖示（沒圖示就給個預設的）
    // 4. 確保支出和收入至少各有一個分類（避免使用者刪光光）
    // 5. 排序（支出在前、收入在後，然後按時間排）
    private fun normalizeCategories(categories: List<TransactionCategoryDefinition>): List<TransactionCategoryDefinition> {
        // 防呆：如果傳進來的清單是空的，直接給預設分類
        if (categories.isEmpty()) {
            return TransactionCategoryCatalog.defaultCategories()
        }

        // 📝 LinkedHashMap 是什麼？白話文：有順序的便利貼牆
        // 它會記住你貼上去的順序，而且同一個位置只能貼一張（key 不能重複）
        // key 我們用「類型||名稱」，例如「支出||餐飲」，這樣同一種分類只會留一個
        val deduped = linkedMapOf<String, TransactionCategoryDefinition>()

        categories.forEach { category ->
            // --- 檢查 1：類型必須是「支出」或「收入」 ---
            val type = category.type.takeIf {
                it == TransactionCategoryCatalog.TYPE_EXPENSE || it == TransactionCategoryCatalog.TYPE_INCOME
            } ?: return@forEach  // 不合法？跳過！就像糞便分類不能丟進回收桶

            // --- 檢查 2：名稱不能是空白 ---
            val name = category.name.trim()
            if (name.isEmpty()) return@forEach  // 沒名字？掰掰

            // --- 檢查 3：圖示不能空白（如果空白就給一個預設的）---
            val icon = category.icon.ifBlank { TransactionCategoryCatalog.fallbackIcon(type) }

            // 製作一把「鑰匙」：類型||名稱（例如「支出||手搖飲」）
            val key = "$type||$name"

            // 建立一個「整理過」的分類副本（把上面的修正都放進去）
            val normalized = category.copy(type = type, name = name, icon = icon)

            val existing = deduped[key]
            // 如果這個 key 還沒有貼過，或者新的是比較晚更新的，就貼新的上去
            // 白話文：同一個分類，保留最新修改的那一版
            if (existing == null || normalized.updatedAt >= existing.updatedAt) {
                deduped[key] = normalized
            }
        }

        // 把整理好的便利貼牆（Map）轉成清單（List），準備繼續加工
        val merged = deduped.values.toMutableList()

        // 🚨 超級重要的防呆機制！
        // 確保「支出」和「收入」至少各有一個分類
        // 為什麼？因為如果使用者手殘把所有「支出」分類都刪光，
        // 那記帳的時候選不到「支出」分類，App 就廢了！
        ensureTypeHasAtLeastOneCategory(merged, TransactionCategoryCatalog.TYPE_EXPENSE)
        ensureTypeHasAtLeastOneCategory(merged, TransactionCategoryCatalog.TYPE_INCOME)

        // 最後排序：先按類型（支出在前、收入在後），再按更新時間（舊的在前新的在後）
        return merged.sortedWith(
            compareBy<TransactionCategoryDefinition> { it.type }.thenBy { it.updatedAt }
        )
    }

    // ========================= 確 保 至 少 有 一 個 分 類 =========================
    // 🛡️ 確保某種類型至少有一個分類（超級防呆）
    //
    // 白話文：檢查「支出」或「收入」是不是被刪光了
    // 如果是，就把預設分類裡面那一類的全部拿來墊底
    private fun ensureTypeHasAtLeastOneCategory(
        categories: MutableList<TransactionCategoryDefinition>,
        type: String
    ) {
        // .none 表示「沒有一個符合條件」 → 就是空空如也
        if (categories.none { it.type == type }) {
            // 從預設分類中，把這個類型的分類全部加進來
            categories += TransactionCategoryCatalog.defaultCategories().filter { it.type == type }
        }
    }

    // ========================= JSON 編 碼（打包） =========================
    // 📦 把分類清單打包成 JSON 字串（準備存入筆記本）
    //
    // 白話文：把一整串分類資料，壓縮成一行長長的文字
    // 例如：[{"type":"支出","name":"餐飲","icon":"🍔"}, {"type":"支出","name":"交通","icon":"🚗"}]
    private fun encode(categories: List<TransactionCategoryDefinition>): String {
        return JSONArray().apply {  // 打開一個空紙箱（JSON 陣列）
            categories.forEach { category ->
                put(  // 把每個分類放進紙箱
                    JSONObject().apply {  // 每個分類是一個小盒子（JSON 物件）
                        put("type", category.type)      // 貼上「類型」標籤
                        put("name", category.name)      // 貼上「名稱」標籤
                        put("icon", category.icon)      // 貼上「圖示」標籤
                        put("updatedAt", category.updatedAt)  // 貼上「更新時間」標籤
                    }
                )
            }
        }.toString()  // 把整個紙箱變成一行字串
    }

    // ========================= JSON 解 碼（拆包裹） =========================
    // 📦🔓 把 JSON 字串還原成分類清單（從筆記本讀回來時用）
    //
    // 白話文：把之前壓縮的那行長長的字串，拆回原本的分類清單
    private fun decode(json: String): List<TransactionCategoryDefinition> {
        val array = JSONArray(json)  // 把字串變回紙箱（JSON 陣列）
        return buildList {  // 建立一個新清單，準備裝拆出來的東西
            for (index in 0 until array.length()) {  // 一個一個翻紙箱裡的東西
                val item = array.optJSONObject(index) ?: continue  // 如果是空盒子就跳過
                add(
                    TransactionCategoryDefinition(
                        type = item.optString("type"),           // 拿出「類型」標籤
                        name = item.optString("name"),           // 拿出「名稱」標籤
                        icon = item.optString("icon"),           // 拿出「圖示」標籤
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())  // 拿出時間（沒有的話就用現在時間）
                    )
                )
            }
        }
    }

    // ========================= 伴 侶 物 件（工具箱） =========================
    // 🧰 祕密辦公室: 存放固定不變的常數（就像工具箱裡面有固定的螺絲起子、板手）
    companion object {
        private const val PREFS_NAME = "cococoin_category_store"  // 筆記本的名稱（就像日記本的封面標題）
        private const val KEY_CATEGORIES_JSON = "categories_json" // 儲存分類的欄位名稱（就像日記本裡某一頁的標題）
    }
}