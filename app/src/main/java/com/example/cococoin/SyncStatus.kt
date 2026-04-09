package com.example.cococoin

// 記錄「雲端同步的狀況」
data class SyncStatus(
    val isFirebaseConfigured: Boolean,   // Firebase 雲端服務是否已經設定好了？
    val isSignedIn: Boolean,              // 使用者是否已登入？
    val userId: String?,                  // 使用者的唯一編號（像身分證字號，沒登入就是 null）
    val lastSyncAt: Long?,                // 上次同步的時間（毫秒，沒同步過就是 null）
    val lastSyncSuccessful: Boolean?,     // 上次同步是否成功？（沒同步過就是 null）
    val lastSyncMessage: String           // 上次同步的結果說明（例如「同步成功」或「網路錯誤」）
)