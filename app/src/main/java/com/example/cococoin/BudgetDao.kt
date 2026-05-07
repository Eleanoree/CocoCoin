// ============================================================
// 🗄️ 預算 DAO（Data Access Object）— 白話文：預算貨架的「搬運工」
// ============================================================
//
// 情境劇：想像預算資料是一個貨架，上面放著每個月的預算便條紙
//         這個 BudgetDao 就是負責管理這個貨架的搬運工：
//           🔍 查詢某個月的預算（getBudgetAmount）
//           📋 列出所有月份的預算（getAllBudgets）
//           ✏️ 新增或更新一個月的預算（upsertBudget）
//           📦 一次設定好幾個月的預算（upsertBudgets）
//           🗑️ 清空所有預算（clearAll）
//
// @Dao 是 Room 的魔法標籤，告訴 Room：「這個介面會變成資料庫的操作員！」
// ============================================================
package com.example.cococoin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BudgetDao {

    // ============================================================
    // 🔍 查詢某年某月的預算金額 — 白話文：這個月的省錢目標是多少？
    // ============================================================
    // 只回傳金額（Int?），不回傳整筆資料
    // 回傳 null 表示「這個月沒設預算」
    //
    // 使用範例：
    //   val aprilBudget = budgetDao.getBudgetAmount(2026, 4)  // 回傳 20000 或 null
    //   val displayText = if (aprilBudget == null) "未設定" else "預算 NT$ $aprilBudget"
    @Query("SELECT amount FROM budgets WHERE year = :year AND month = :month LIMIT 1")
    fun getBudgetAmount(year: Int, month: Int): Int?
    // 白話文：「搬運工，幫我看看 2026 年 4 月的預算是多少？沒設的話跟我說沒有」

    // ============================================================
    // 📋 查詢所有預算紀錄 — 白話文：把所有月份的便條紙都拿來！
    // ============================================================
    // ORDER BY year ASC, month ASC 表示按「年份從舊到新，月份從小到大」排序
    // 白話文：從 2025 年 1 月 → 2025 年 2 月 → ... → 2026 年 12 月
    @Query("SELECT * FROM budgets ORDER BY year ASC, month ASC")
    fun getAllBudgets(): List<BudgetSetting>
    // 白話文：「全部拿出來，按照時間排好！」

    // ============================================================
    // ✏️ 新增或更新一個月的預算 — 白話文：設定這個月的省錢目標
    // ============================================================
    // onConflict = OnConflictStrategy.REPLACE
    // 白話文：如果這個月已經有預算了，就把舊的蓋掉（以新的為準）
    //
    // 使用範例：
    //   budgetDao.upsertBudget(BudgetSetting(year=2026, month=4, amount=20000))
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBudget(budgetSetting: BudgetSetting)
    // 白話文：「搬運工，幫我把這張便條紙放上去！同一個月的舊的可以丟掉了」

    // ============================================================
    // 📦 一次設定好幾個月的預算 — 白話文：年初一次設好一整年
    // ============================================================
    // 白話文：一次放一堆便條紙（例如 1 月到 12 月全部一起設）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBudgets(budgets: List<BudgetSetting>)
    // 白話文：「搬運工，這疊便條紙全部放上去，有衝突的就蓋掉」

    // ============================================================
    // 🗑️ 清空所有預算資料 — 白話文：把所有便條紙都撕掉
    // ============================================================
    @Query("DELETE FROM budgets")
    fun clearAll()
    // 白話文：「搬運工，貨架清空！」
}