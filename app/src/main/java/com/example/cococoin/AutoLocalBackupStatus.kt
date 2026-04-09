package com.example.cococoin

// 自動本機備份的狀態：
// 記錄「自動備份到手機資料夾」功能的設定狀態
data class AutoLocalBackupStatus(
    val isConfigured: Boolean,   // 是否已設定備份資料夾？（true = 有設定，false = 沒設定）
    val folderName: String?,     // 備份資料夾的名稱（例如「CocoCoin備份」）
    val lastBackupAt: Long?,     // 上次備份的時間（毫秒，沒備份過就是 null）
    val lastMessage: String      // 上次備份的結果訊息（例如「已自動更新本機備份」或「備份失敗」）
)