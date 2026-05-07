package com.example.cococoin

// ============================================================
// 🪪 交易分類定義 — 白話文：分類的「身分證」或「履歷表」
// ============================================================
//
// 情境劇：想像「餐飲」是一個分類，它的身分證上寫著：
//   - 類型：支出（代表這是花錢的分類）
//   - 名稱：餐飲（人類看得懂的名字）
//   - 圖示：🍽（一個可愛的餐具圖案）
//   - 更新時間：2025-04-01 12:00（最後一次修改的時間）
//
// data class 是什麼？白話文：Kotlin 幫你自動生成「拿資料、比對資料、複製資料」的懶人神器！
// 你只需要寫這一行，它就自動幫你生出 equals()、hashCode()、toString()、copy() 這些好用功能
data class TransactionCategoryDefinition(
    val type: String,      // 類型：只能是「支出」或「收入」（就像身分證上的性別欄）
    val name: String,      // 分類名稱：例如「餐飲」、「課金」或是「買快樂」（就像身分證上的姓名）
    val icon: String,      // 圖示：emoji 表情，例如 🍽、🚗（就像身分證上的大頭照）
    val updatedAt: Long = System.currentTimeMillis()  // 最後更新時間（毫秒），預設是「現在這一秒」
    // 白話文：這格記錄「這張分類身分證是什麼時候辦的或更新的」
    // 預設值 = System.currentTimeMillis() → 如果沒特別給時間，就自動貼上「目前的時間」
)

// ============================================================
// 🎨 分類圖示選項 — 白話文：圖示菜單上一道菜的「菜名」和「圖片」
// ============================================================
//
// 情境劇：當使用者要編輯分類時，會跳出一個選單讓他選圖示
// 這個 data class 就是選單上「一道選項」的樣子：
//   - icon: 🍽（實際顯示的 emoji）
//   - label: 餐飲（旁邊的說明文字，告訴使用者這個圖示代表什麼）
data class CategoryIconOption(
    val icon: String,   // emoji 圖示（例如 🍽、🚗、💰）— 就像菜單上的照片
    val label: String   // 說明文字（例如「餐飲」、「交通」）— 就像菜單上的菜名
)

// ============================================================
// 📖 交易分類目錄 — 白話文：分類界的「圖書館管理員」＋「工具人」
// ============================================================
//
// 情境劇：
//   - 這是一個「萬能工具箱」，裡面放著所有分類的「預設值」和「輔助工具」
//   - object 關鍵字：Kotlin 的單例模式，白話文就是「全宇宙只有一個這個東西」
//     不管你在 App 的哪裡呼叫 TransactionCategoryCatalog，拿到的都是同一個！
//     就像學校圖書館的管理員，永遠是同一位大叔，不會有分身
object TransactionCategoryCatalog {

    // ----- 常數定義（就像字典裡固定不變的定義）-----
    const val TYPE_EXPENSE = "支出"   // 支出類型 — 白話文：花錢的方向
    const val TYPE_INCOME = "收入"    // 收入類型 — 白話文：賺錢的方向

    // ----- 🎨 圖示菜單（所有可選的 emoji 清單）-----
    // 白話文：這是一張「圖示點餐單」，使用者編輯分類時可以從裡面挑一個喜歡的 emoji
    // 就像火鍋店的醬料台，有沙茶醬、蒜泥、辣椒... 自己選！
    val iconOptions = listOf(
        CategoryIconOption("🍽", "餐飲"),      // 吃飯皇帝大，錢包先跪下
        CategoryIconOption("☕", "飲品"),      // 咖啡、手搖、奶茶：精神續命費
        CategoryIconOption("🏠", "居家"),      // 房租、水電、生活用品都很適合
        CategoryIconOption("🚌", "交通"),      // 公車捷運通勤小怪獸
        CategoryIconOption("👕", "衣物"),      // 買衣服：衣櫃說不要，心裡說需要
        CategoryIconOption("🛍", "購物"),      // 逛一下而已，結果袋子自己變多
        CategoryIconOption("🎬", "娛樂"),      // 電影、遊戲、放鬆一下合理啦
        CategoryIconOption("💊", "醫療"),      // 看病、藥品、保健相關
        CategoryIconOption("📚", "學習"),      // 課程、書籍、提升自己
        CategoryIconOption("🧾", "其他"),      // 暫時不知道放哪？先放收據抽屜
        CategoryIconOption("💼", "薪資"),      // 上班打怪後的獎勵
        CategoryIconOption("🎉", "獎金"),      // 意外驚喜，錢包開派對
        CategoryIconOption("📈", "投資"),      // 股票基金等等，理財腦上線
        CategoryIconOption("🧧", "紅包"),      // 過年、長輩、祝福與現金
        CategoryIconOption("↩", "退款"),       // 買錯退回來，錢錢迷途知返
        CategoryIconOption("💰", "收入"),      // 通用收入圖示，萬用錢袋
        CategoryIconOption("🪙", "零用"),      // 零用錢、小額收入
        CategoryIconOption("🚕", "車資"),      // 計程車、Uber、臨時移動費
        CategoryIconOption("🎁", "禮物"),      // 禮物、贈品、收到的心意
        CategoryIconOption("📱", "數位")       // App 訂閱、電信、線上服務
    )

    // ----- 🏠 預設分類清單（App 第一次安裝時使用）-----
    // 白話文：這是交給新使用者的「新手包」或「樣板」
    // 就像買新手機時，裡面已經預裝了一些 App，你不用從零開始
    // 使用者之後可以自己修改、刪除、新增
    fun defaultCategories(): List<TransactionCategoryDefinition> {
        return listOf(
            // ========== 支出分類（花錢的項目）==========
            TransactionCategoryDefinition(TYPE_EXPENSE, "餐飲", "🍽"),      // 吃飯皇帝大
            TransactionCategoryDefinition(TYPE_EXPENSE, "飲料/點心", "☕"), // 手搖杯是不良心癮
            TransactionCategoryDefinition(TYPE_EXPENSE, "房租", "🏠"),      // 每個月的痛
            TransactionCategoryDefinition(TYPE_EXPENSE, "交通", "🚌"),      // 油錢、捷運、計程車
            TransactionCategoryDefinition(TYPE_EXPENSE, "衣物", "👕"),      // 治裝費
            TransactionCategoryDefinition(TYPE_EXPENSE, "購物", "🛍"),      // 衝動購物
            TransactionCategoryDefinition(TYPE_EXPENSE, "娛樂", "🎬"),      // 電影、唱歌、遊戲
            TransactionCategoryDefinition(TYPE_EXPENSE, "醫療", "💊"),      // 希望不要常用到
            TransactionCategoryDefinition(TYPE_EXPENSE, "學習", "📚"),      // 投資自己
            TransactionCategoryDefinition(TYPE_EXPENSE, "其他", "🧾"),      // 不知道放哪的都來這

            // ========== 收入分類（賺錢的項目）==========
            TransactionCategoryDefinition(TYPE_INCOME, "薪水", "💼"),       // 上班族的主力
            TransactionCategoryDefinition(TYPE_INCOME, "獎金", "🎉"),       // 年終、績效
            TransactionCategoryDefinition(TYPE_INCOME, "投資", "📈"),       // 股票、基金
            TransactionCategoryDefinition(TYPE_INCOME, "兼職", "🪙"),       // 外送、接案
            TransactionCategoryDefinition(TYPE_INCOME, "紅包", "🧧"),       // 過年、喜事
            TransactionCategoryDefinition(TYPE_INCOME, "退款", "↩"),        // 退貨、退費
            TransactionCategoryDefinition(TYPE_INCOME, "其他收入", "💰")    // 天上掉下來的禮物
        )
    }

    // ----- 🆘 備用圖示（救火隊）-----
    // 白話文：當一個分類「沒有設定圖示」時，給它一個預設的圖示
    // 就像你忘記帶便當，學校的愛心便當一樣 — 不一定是最好吃的，但至少不會餓死！
    // 收入預設用錢袋 💰，支出預設用收據 🧾
    fun fallbackIcon(type: String): String {
        return if (type == TYPE_INCOME) "💰" else "🧾"
    }
}
// ============================================================
// 💄 交易分類格式化器 — 白話文：分類的「化妝師」或「美容師」
// ============================================================
//
// 情境劇：
//   - 使用者在畫面上看到的不只是「餐飲」兩個字，而是「🍽 餐飲」這樣比較可愛
//   - 這個物件的任務就是：把分類變漂亮，以及把漂亮的東西翻譯回原本的樣子
//   - 也是 object（單例），全 App 共用一台「美容機器」
object TransactionCategoryFormatter {

    // ----- 🎀 變漂亮！把分類變成「🍽 餐飲」這樣的顯示文字 -----
    // 白話文：給它化妝，讓它在 UI 上見人
    // 輸入：TransactionCategoryDefinition(type="支出", name="餐飲", icon="🍽")
    // 輸出：「🍽 餐飲」
    //
    // 為什麼要這樣做？因為使用者看到 emoji + 文字 比 純文字 更容易辨識！
    fun toDisplayLabel(category: TransactionCategoryDefinition): String {
        return "${category.icon}  ${category.name}"
        // 注意：中間兩個空格，讓圖示和文字之間有點呼吸感，比較好看
    }

    // ----- 🔍 卸妝！從顯示文字反推出分類名稱 -----
    // 白話文：使用者可能在輸入框打了「🍽 餐飲」或直接打「餐飲」
    // 這個函式負責猜出「他到底想選哪個分類」
    //
    // 情境劇：
    //   1. 使用者從選單選了「🍽 餐飲」 → 我們要取出「餐飲」
    //   2. 使用者自己打了「餐飲」 → 直接回傳「餐飲」
    //   3. 使用者打了「🍽 食物」但分類清單沒有 → 去掉 emoji 變成「食物」
    //   4. 使用者亂打「😀 哈哈」 → 至少留個「哈哈」
    //
    // 這個函式的精神：盡量猜，不要因為一個小錯誤就讓 App 崩潰！
    fun resolveName(
        input: String,
        categories: List<TransactionCategoryDefinition>
    ): String {
        val trimmed = input.trim()  // 去掉頭尾空白（使用者可能不小心按到空格）
        if (trimmed.isEmpty()) return ""  // 什麼都沒打？回傳空字串

        // 步驟 1：如果輸入的完整格式「🍽 餐飲」，就去分類清單裡找對應的名稱
        // 白話文：先試試看「卸妝後」能不能在分類清單裡找到一模一樣的
        categories.firstOrNull { toDisplayLabel(it) == trimmed }?.let { return it.name }

        // 步驟 2：如果輸入的就是名稱（例如「餐飲」），直接回傳
        categories.firstOrNull { it.name == trimmed }?.let { return it.name }

        // 步驟 3：都找不到？使出大絕招 — 去掉前面的 emoji 和空格！
        // 白話文：使用者可能打了「🍽 亂七八糟」，我們把前面的「🍽 」砍掉，留「亂七八糟」
        // Regex("^\\S+\\s+") 是什麼？
        //   - ^  : 開頭
        //   - \\S+ : 一個或多個「非空白字元」（emoji 或文字）
        //   - \\s+ : 一個或多個空白
        // 效果：把開頭的「🍽 」或「😀  」這種東西刪掉
        val stripped = trimmed.replace(Regex("^\\S+\\s+"), "").trim()

        // 如果去掉之後還有東西就回傳，真的空空如也的話就回傳原本的輸入（至少留點什麼）
        return stripped.ifEmpty { trimmed }
    }
}