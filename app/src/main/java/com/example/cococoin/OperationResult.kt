package com.example.cococoin

// 包裝「操作結果」的資料類別
data class OperationResult(
    val success: Boolean,   // 操作是否成功？
    val message: String? = null  // 附帶的說明文字（例如「備份成功」或「網路錯誤」）
) {
    companion object {
        // 建立一個「成功」的結果
        fun ok(message: String? = null) = OperationResult(success = true, message = message)

        // 建立一個「失敗」的結果
        fun fail(message: String) = OperationResult(success = false, message = message)
    }
}