package com.example.cococoin

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//CSV 報表匯出器
object CocoCoinCsvExporter {

    // 匯出交易紀錄為 CSV 字串
    fun exportTransactionsCsv(snapshot: CocoCoinSnapshot): String {
        val builder = StringBuilder()

        // 1.寫入報表頭部資訊
        builder.appendLine("匯出時間,${escapeCsv(formatExportedAt(System.currentTimeMillis()))}")
        builder.appendLine("交易筆數,${snapshot.transactions.size}")
        builder.appendLine()  // 空行分隔

        // 2.寫入欄位名稱（第一行是標題列）
        builder.appendLine("日期時間,類型,分類,金額,帳戶,備註,更新時間")

        // 3.逐筆寫入交易資料
        snapshot.transactions.forEach { transaction ->
            builder.appendLine(
                listOf(
                    transaction.time,           // 日期時間
                    transaction.type,           // 支出/收入
                    transaction.category,       // 分類名稱
                    transaction.amount.toString(),  // 金額
                    transaction.accountName,    // 帳戶名稱
                    transaction.note,           // 備註
                    formatExportedAt(transaction.updatedAt)  // 更新時間
                ).joinToString(",") { value -> escapeCsv(value) }
            )
        }

        return builder.toString()
    }

    // 把時間戳記（毫秒）轉成可讀的日期時間格式：
    // 把「1744012800000」變成「2026/04/07 14:30:00」
    private fun formatExportedAt(timestamp: Long): String {
        return SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    // 轉義 CSV 特殊字符
    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")  // 雙引號變成兩個雙引號
        return "\"$escaped\""  // 前後加上雙引號包起來
    }
}