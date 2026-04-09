package com.example.cococoin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BudgetDao {

    // 查詢某年某月的預算金額
    // 只回傳 Int? 金額，不回傳整筆資料，比較簡單
    @Query("SELECT amount FROM budgets WHERE year = :year AND month = :month LIMIT 1")
    fun getBudgetAmount(year: Int, month: Int): Int?
    // 回傳 null 表示「這個月沒設預算」

    // 查詢所有預算紀錄，按年、
    @Query("SELECT * FROM budgets ORDER BY year ASC, month ASC")
    fun getAllBudgets(): List<BudgetSetting>

    // 新增或更新一個月的預算
    // 如果該年該月已經有預算，就用新的覆蓋（REPLACE）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBudget(budgetSetting: BudgetSetting)

    // 一次設定好幾個月的預算（例如年初一次設整年）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBudgets(budgets: List<BudgetSetting>)

    // 清空所有預算資料
    @Query("DELETE FROM budgets")
    fun clearAll()
}