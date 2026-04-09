package com.example.cococoin

// 交易分類定義：
// 每個分類有「類型（支出/收入）」、「名稱」、「圖示（emoji）」、「更新時間」
data class TransactionCategoryDefinition(
    val type: String,      // 支出 或 收入
    val name: String,      // 分類名稱（例如「餐飲」）
    val icon: String,      // 圖示（例如 🍽）
    val updatedAt: Long = System.currentTimeMillis()  // 最後更新時間
)

// 分類圖示選項：讓使用者選擇時用的清單
data class CategoryIconOption(
    val icon: String,   // emoji 圖示（例如 🍽）
    val label: String   // 說明文字（例如「餐飲」）
)

// 交易分類目錄：存放預設的分類、圖示選項、工具函式
object TransactionCategoryCatalog {
    // 常數定義
    const val TYPE_EXPENSE = "支出"   // 支出類型
    const val TYPE_INCOME = "收入"    // 收入類型

    // 所有可選的圖示清單（讓使用者在編輯分類時可以挑選）
    val iconOptions = listOf(
        CategoryIconOption("🍽", "餐飲"),
        CategoryIconOption("☕", "飲品"),
        CategoryIconOption("🏠", "居家"),
        CategoryIconOption("🚌", "交通"),
        CategoryIconOption("👕", "衣物"),
        CategoryIconOption("🛍", "購物"),
        CategoryIconOption("🎬", "娛樂"),
        CategoryIconOption("💊", "醫療"),
        CategoryIconOption("📚", "學習"),
        CategoryIconOption("🧾", "其他"),
        CategoryIconOption("💼", "薪資"),
        CategoryIconOption("🎉", "獎金"),
        CategoryIconOption("📈", "投資"),
        CategoryIconOption("🧧", "紅包"),
        CategoryIconOption("↩", "退款"),
        CategoryIconOption("💰", "收入"),
        CategoryIconOption("🪙", "零用"),
        CategoryIconOption("🚕", "車資"),
        CategoryIconOption("🎁", "禮物"),
        CategoryIconOption("📱", "數位")
    )

    // 取得預設的分類清單（App 第一次安裝時使用）
    fun defaultCategories(): List<TransactionCategoryDefinition> {
        return listOf(
            // 支出分類
            TransactionCategoryDefinition(TYPE_EXPENSE, "餐飲", "🍽"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "飲料/點心", "☕"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "房租", "🏠"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "交通", "🚌"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "衣物", "👕"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "購物", "🛍"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "娛樂", "🎬"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "醫療", "💊"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "學習", "📚"),
            TransactionCategoryDefinition(TYPE_EXPENSE, "其他", "🧾"),
            // 收入分類
            TransactionCategoryDefinition(TYPE_INCOME, "薪水", "💼"),
            TransactionCategoryDefinition(TYPE_INCOME, "獎金", "🎉"),
            TransactionCategoryDefinition(TYPE_INCOME, "投資", "📈"),
            TransactionCategoryDefinition(TYPE_INCOME, "兼職", "🪙"),
            TransactionCategoryDefinition(TYPE_INCOME, "紅包", "🧧"),
            TransactionCategoryDefinition(TYPE_INCOME, "退款", "↩"),
            TransactionCategoryDefinition(TYPE_INCOME, "其他收入", "💰")
        )
    }

    // 備用圖示（當分類沒有設定圖示時使用）
    fun fallbackIcon(type: String): String {
        return if (type == TYPE_INCOME) "💰" else "🧾"  // 收入用錢袋，支出用收據
    }
}

// 交易分類格式化器：處理分類的顯示文字
object TransactionCategoryFormatter {
    // 把分類變成「🍽 餐飲」這樣的顯示文字
    fun toDisplayLabel(category: TransactionCategoryDefinition): String {
        return "${category.icon}  ${category.name}"
    }

    // 從顯示文字反推出分類名稱（處理使用者輸入時用）
    fun resolveName(
        input: String,
        categories: List<TransactionCategoryDefinition>
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        // 如果輸入的是完整格式「🍽 餐飲」，就取出名稱
        categories.firstOrNull { toDisplayLabel(it) == trimmed }?.let { return it.name }
        // 如果輸入的就是名稱，直接回傳
        categories.firstOrNull { it.name == trimmed }?.let { return it.name }

        // 如果前面都不符合，試著去掉前面的 emoji 和空格
        val stripped = trimmed.replace(Regex("^\\S+\\s+"), "").trim()
        return stripped.ifEmpty { trimmed }
    }
}