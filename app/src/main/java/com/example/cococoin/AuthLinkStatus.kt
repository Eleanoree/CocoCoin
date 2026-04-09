package com.example.cococoin

// 帳號連結狀態：
// 記錄使用者與 Firebase 的登入/綁定狀態
// 用來決定設定頁面要顯示哪些按鈕（升級、登入、備份、登出）
data class AuthLinkStatus(
    val isFirebaseReady: Boolean,    // Firebase 是否已設定好？（google-services.json 有正確設定）
    val isSignedIn: Boolean,         // 使用者是否已登入？
    val isAnonymous: Boolean,        // 是否是匿名登入？（沒綁定 Email 或 Google 的臨時帳號）
    val uid: String?,                // 使用者的唯一識別碼（Firebase UID）
    val email: String?,              // 使用者的 Email（如果有綁定的話）
    val hasEmailProvider: Boolean,   // 是否已綁定 Email 登入方式？
    val hasGoogleProvider: Boolean,  // 是否已綁定 Google 登入方式？
    val isEmailVerified: Boolean     // Email 是否已完成驗證？
)
