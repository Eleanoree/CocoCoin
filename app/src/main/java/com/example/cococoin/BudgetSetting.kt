// ============================================================
// 📊 預算設定資料類別 — 白話文：每個月的「省錢小目標」
// ============================================================
//
// 情境劇：想像你每個月初都會設定一個預算目標：
//           「2026 年 4 月，我總共只能花 20000 元！」
//           或者更細的：「餐飲只能花 5000 元，交通只能花 3000 元...」
//
// 這個 BudgetSetting 就是記錄那個「月預算」的便條紙！
// 它會存在 Room 資料庫的 budgets 表格中，每個月一張便條紙
//
// 注意：這裡的預算是「總預算」，不是「各分類預算」
//       如果要各分類預算，需要另外設計（進階功能）
// ============================================================
package com.example.cococoin

import androidx.room.ColumnInfo
import androidx.room.Entity

// @Entity — 告訴 Room：「這個類別要變成一張資料表喔！」
// tableName = "budgets" → 表格名字叫 budgets
// primaryKeys = ["year", "month"] → 用「年份 + 月份」當作複合主鍵
// 白話文：同一年同一個月只能有一筆預算，不能有兩筆四月預算
@Entity(
    tableName = "budgets",
    primaryKeys = ["year", "month"]  // 兩個欄位合起來當主鍵
)
data class BudgetSetting(
    @ColumnInfo(name = "year")
    val year: Int,       // 📅 年份（例如 2026）
    // 白話文：哪一年的預算？

    @ColumnInfo(name = "month")
    val month: Int,      // 📆 月份（1~12，1 是 1 月，12 是 12 月）
    // 白話文：哪一個月的預算？

    @ColumnInfo(name = "amount")
    val amount: Int,     // 💰 預算金額（例如 20000）
    // 白話文：這個月總共可以花多少錢？

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()  // ⏰ 更新時間（毫秒）
    // 白話文：這張便條紙是什麼時候寫的或改的
    // 預設值是「現在這一刻」
)