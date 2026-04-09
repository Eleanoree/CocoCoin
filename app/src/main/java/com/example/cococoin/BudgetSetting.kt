package com.example.cococoin

import androidx.room.ColumnInfo
import androidx.room.Entity

// 預算設定資料類別：
// 每個月的預算設定，存在 Room 資料庫的 budgets 表格中用 year + month 當作複合主鍵（同一筆年月只能有一筆預算）
@Entity(
    tableName = "budgets",           // 表格名稱
    primaryKeys = ["year", "month"]  // 複合主鍵：年份 + 月份
)
data class BudgetSetting(
    @ColumnInfo(name = "year")
    val year: Int,       // 年份（例如 2026）

    @ColumnInfo(name = "month")
    val month: Int,      // 月份（1~12）

    @ColumnInfo(name = "amount")
    val amount: Int,     // 預算金額（例如 20000）

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()  // 更新時間（毫秒）
)