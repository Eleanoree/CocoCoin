package com.example.cococoin

// 交易摘要
data class CategoryDetailItem(
    val date: String,           // 交易日期（例如「2026/04/06」）
    val noteOrCategory: String, // 備註或分類名稱（沒有備註就顯示分類）
    val amount: Int             // 金額
)