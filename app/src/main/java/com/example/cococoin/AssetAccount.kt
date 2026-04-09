package com.example.cococoin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 資產帳戶資料類別
@Entity(
    tableName = "accounts",  // 表格名稱
    indices = [Index(value = ["name"], unique = true)]  // 帳戶名稱必須唯一（不能有兩個同名帳戶）
)
data class AssetAccount(
    @PrimaryKey(autoGenerate = true)  // 主鍵，自動產生編號（1, 2, 3...）
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "name")
    val name: String,      // 帳戶名稱（例如「現金」、「國泰銀行」）

    @ColumnInfo(name = "balance")
    val balance: Int,      // 帳戶餘額（例如 10000 表示 NT$ 10,000）

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()  // 更新時間（毫秒）
)