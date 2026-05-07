package com.example.cococoin

/**
 * 📋【交易摘要】- 分類明細列表的一筆資料
 *
 * 想像你去餐廳吃飯，拿到一張「帳單明細」：
 * ┌─────────────────────────────────────────┐
 * │ 日期         │ 項目           │ 金額    │
 * ├─────────────┼───────────────┼─────────┤
 * │ 2026/04/07  │ 跟同事吃午餐   │ -150    │
 * │ 2026/04/07  │ 搭捷運         │ -30     │
 * │ 2026/04/06  │ 買咖啡         │ -120    │
 * └─────────────┴───────────────┴─────────┘
 *
 * CategoryDetailItem 就是上面「一列」資料的格式！
 *
 * 使用情境：
 * - 在「分析」頁面，點擊某個分類（例如：「餐飲」）
 * - 就會跳出這個分類底下的所有交易明細
 * - 每一筆明細就是一個 CategoryDetailItem
 *
 * @param date 交易日期（例如「2026/04/07」）
 * @param noteOrCategory 備註或分類名稱
 *                       （有備註就顯示備註，沒有就顯示分類）
 * @param amount 金額（支出顯示負數，收入顯示正數）
 *
 * 為什麼叫「noteOrCategory」而不是「note」？
 * 因為有時候備註是空的，這時候要顯示「餐飲」這種分類名稱
 * 所以是一個彈性的欄位～
 */
data class CategoryDetailItem(
    val date: String,           // 📅 交易日期（例如「2026/04/06」）
    val noteOrCategory: String, // 📝 備註或分類名稱（沒有備註就顯示分類）
    val amount: Int             // 💰 金額（負數表示支出，正數表示收入）
)

/**
 * 💡【data class 的好處】
 *
 * 為什麼用 data class 而不是普通 class？
 *
 * 普通 class：
 * class CategoryDetailItem(...)  // 要自己寫 equals、hashCode、toString
 *
 * data class：
 * data class CategoryDetailItem(...)  // 自動產生這些方法！
 *
 * 自動得到的超能力：
 * ✅ .copy()：快速產生新物件（例如修改金額）
 * ✅ .toString()：自動變成人類可讀的字串
 * ✅ .equals()：比較兩個物件是否內容相同
 * ✅ .hashCode()：用在集合（Map、Set）裡當索引
 *
 * 使用範例：
 * val lunch = CategoryDetailItem("2026/04/07", "午餐", -150)
 * val copyLunch = lunch.copy(amount = -200)  // 改成晚餐金額
 *
 * println(lunch)  // CategoryDetailItem(date=2026/04/07, noteOrCategory=午餐, amount=-150)
 */

/**
 * 🎭【使用情境 - 劇場版】
 *
 * 情境：使用者在「分析」頁面點擊「餐飲」分類
 *
 * 第 1 幕：查詢資料庫，找出所有「餐飲」分類的交易
 *    ↓
 * 第 2 幕：把每一筆交易轉換成 CategoryDetailItem
 *
 *    交易 1：「2026/04/07，備註=午餐，金額=150」
 *    → CategoryDetailItem("2026/04/07", "午餐", -150)
 *
 *    交易 2：「2026/04/06，備註=，金額=120」
 *    → CategoryDetailItem("2026/04/06", "餐飲", -120)  // 備註空白，用分類代替
 *    ↓
 * 第 3 幕：把 List<CategoryDetailItem> 丟給 CategoryDetailAdapter
 *    ↓
 * 第 4 幕：RecyclerView 顯示出明細列表 ✨
 */