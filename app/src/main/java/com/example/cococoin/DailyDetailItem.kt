package com.example.cococoin

// 每日明細的資料結構：
// 在分析頁面中，點擊某一天後會顯示當天的所有交易，
// 這個類別代表「一筆」交易的摘要資訊
data class DailyDetailItem(
    val title: String,      // 標題（例如「餐飲」）
    val subTitle: String,   // 副標題（例如「支出 ・ 現金」）
    val amount: Int,        // 金額
    val type: String        // 類型（「收入」或「支出」）
)