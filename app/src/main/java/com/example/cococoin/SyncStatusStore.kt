package com.example.cococoin

import android.content.Context

// 同步狀態儲存器：用來記住「雲端同步的狀況」
class SyncStatusStore(
    context: Context  // 需要 App 的環境才能存取手機儲存空間
) {

    // 用 App 的整體環境來存取 SharedPreferences
    private val appContext = context.applicationContext

    // SharedPreferences：手機裡的一個小筆記本，可以存簡單的資料（開關、數字、文字）
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // 記錄「認證狀態」：「Firebase 有沒有設定好？使用者 ID 是多少？」
    fun markAuthState(
        firebaseConfigured: Boolean,  // Firebase 是否已經設定好？
        uid: String?                  // 使用者的唯一編號（像身分證字號）
    ) {
        preferences.edit()   // 打開筆記本準備寫東西
            .putBoolean(KEY_FIREBASE_CONFIGURED, firebaseConfigured)  // 寫下 Firebase 狀態
            .putString(KEY_USER_ID, uid)                              // 寫下使用者 ID
            .apply()          // 儲存
    }

    // 清除登入狀態（使用者登出時呼叫）
    fun clearForSignedOutUser(message: String = "已登出，請重新登入以找回雲端資料") {
        preferences.edit()
            .putString(KEY_USER_ID, null)                    // 把使用者 ID 清掉（變空白）
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)     // 記錄「上一次同步失敗」
            .putString(KEY_LAST_SYNC_MESSAGE, message)       // 寫下原因
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間
            .apply()
    }

    // 記錄同步成功
    fun markSyncSuccess(message: String = "雲端同步成功") {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, true)             // 成功！
            .putString(KEY_LAST_SYNC_MESSAGE, message)              // 寫下「成功」的訊息
            .apply()
    }

    // 記錄同步失敗
    fun markSyncFailure(message: String) {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)            // 失敗...
            .putString(KEY_LAST_SYNC_MESSAGE, message)              // 寫下失敗的原因
            .apply()
    }

    // 讀取目前的同步狀態（從筆記本裡把資料讀出來）
    fun getStatus(): SyncStatus {
        // 從筆記本裡讀取使用者 ID（如果沒有就是 null）
        val userId = preferences.getString(KEY_USER_ID, null)

        // 檢查筆記本裡有沒有「上次同步時間」這筆紀錄
        val hasLastSync = preferences.contains(KEY_LAST_SYNC_AT)
        val lastSyncAt = if (hasLastSync) preferences.getLong(KEY_LAST_SYNC_AT, 0L) else null

        // 檢查筆記本裡有沒有「同步結果」這筆紀錄
        val hasSyncResult = preferences.contains(KEY_LAST_SYNC_SUCCESSFUL)
        val syncSuccessful = if (hasSyncResult) {
            preferences.getBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)
        } else {
            null  // 從來沒同步過
        }

        // 把讀到的資料打包成一個 SyncStatus 物件還回去
        return SyncStatus(
            isFirebaseConfigured = preferences.getBoolean(KEY_FIREBASE_CONFIGURED, false),
            isSignedIn = !userId.isNullOrBlank(),  // 有使用者 ID 就表示有登入
            userId = userId,
            lastSyncAt = lastSyncAt,
            lastSyncSuccessful = syncSuccessful,
            lastSyncMessage = preferences.getString(KEY_LAST_SYNC_MESSAGE, "尚未進行雲端同步")
                ?: "尚未進行雲端同步"
        )
    }

    // 「常數」：固定不變的鑰匙名稱
    companion object {
        private const val PREF_NAME = "cococoin_sync_status"           // 筆記本的名字
        private const val KEY_FIREBASE_CONFIGURED = "firebase_configured"  // Firebase 有沒有設定好
        private const val KEY_USER_ID = "user_id"                      // 使用者 ID 的欄位名稱
        private const val KEY_LAST_SYNC_AT = "last_sync_at"            // 最後同步時間
        private const val KEY_LAST_SYNC_SUCCESSFUL = "last_sync_successful"  // 同步成功與否
        private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"  // 同步結果的說明文字
    }
}