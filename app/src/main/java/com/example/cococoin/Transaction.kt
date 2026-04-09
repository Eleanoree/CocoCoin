package com.example.cococoin

import androidx.room.ColumnInfo   // 告訴 Room「這個欄位對應到資料庫的哪一列」
import androidx.room.Entity       // 標記這個類別是一張「資料表」
import androidx.room.PrimaryKey   // 標記哪個欄位是「主鍵」

// 把這個類別變成資料庫裡的一張表，表名叫 transactions
@Entity(tableName = "transactions")
data class Transaction(

    // 主鍵（Primary Key），autoGenerate = true 表示讓資料庫自動產生編號
    // 就像銀行交易序號，每筆都不一樣
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int,

    // 類型：是「收入」還是「支出」？
    @ColumnInfo(name = "type")
    val type: String,

    // 分類：例如「餐飲」、「交通」、「購物」
    @ColumnInfo(name = "category")
    val category: String,

    // 金額：花了多少錢或賺了多少錢
    @ColumnInfo(name = "amount")
    val amount: Int,

    // 備註：使用者可以額外寫的文字，例如「跟朋友吃火鍋」
    @ColumnInfo(name = "note")
    val note: String,

    // 時間：這筆交易發生的時間（例如「2026/03/26 16:02」）
    @ColumnInfo(name = "time")
    val time: String,

    // 帳戶名稱：這筆錢是從哪個帳戶扣的（例如「現金」、「國泰銀行」）
    @ColumnInfo(name = "account_name")
    val accountName: String,

    // 更新時間：這筆資料最後一次被修改的時間（用毫秒儲存）
    // 預設值是「現在這一刻」，方便之後做同步或排序
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)