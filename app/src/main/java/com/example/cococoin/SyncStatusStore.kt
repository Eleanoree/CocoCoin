// ============================================================
// ☁️ 同步狀態儲存器 — 白話文：雲端同步的「日記本」或「工作日誌」
// ============================================================
//
// 情境劇：想像你有一個助理（App），每天都會幫你把記帳資料備份到雲端
// 這個助理會寫一本日記，記錄：
//   - 「今天有登入嗎？誰登入的？」
//   - 「上次備份是什麼時候？」
//   - 「備份成功了嗎？還是失敗了？為什麼？」
//
// 這本日記就叫做 SyncStatusStore（同步狀態儲存器）
// 它把這些資訊記在手機的 SharedPreferences 裡（就像一本小筆記本）
// 下次打開 App 時，就可以翻開日記，知道「上次發生什麼事」
// ============================================================
package com.example.cococoin

import android.content.Context

class SyncStatusStore(
    context: Context  // 需要 App 的「環境」才能存取手機的儲存空間（就像需要鑰匙才能開抽屜）
) {

    // ============================================================
    // 📱 用 App 的整體環境（不是某個畫面的環境）
    // ============================================================
    // 為什麼用 applicationContext 而不是 context 本身？
    // 白話文：applicationContext 是整個 App 的「生命共同體」，App 沒關閉它就不會死
    //         如果用普通的 context，可能 Activity 關閉就跟著消失，造成悲劇（記憶體洩漏）
    // 就像你要保管一本重要的日記，不會交給一個明天就要離職的員工，而是交給公司總部！
    private val appContext = context.applicationContext

    // 📓 SharedPreferences：手機裡的「小筆記本」，可以存簡單的資料（開關、數字、文字）
    // 白話文：就像你書桌上那本隨手記的筆記本，可以寫「今天早餐50元」、「記得繳電話費」
    //         關機也不會消失，下次打開 App 還能翻到之前寫的內容
    private val preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // ✍️ 記錄「認證狀態」：Firebase 有沒有設定好？使用者 ID 是多少？
    // ============================================================
    // 白話文：在日記上寫下「今天誰登入了我的 App？雲端服務有開通嗎？」
    //
    // 情境劇：
    //   使用者點擊「登入 Google」，成功之後就會呼叫這個函式
    //   寫下：「Firebase 已就緒，使用者 ID 是 abc123xyz」
    // firebaseConfigured：
    // 👉 true  = Firebase 已設定好，可以嘗試同步
    // 👉 false = Firebase 沒設定好，雲端同步先不要硬衝
    fun markAuthState(
        firebaseConfigured: Boolean,  // Firebase 是否已經設定好？（就像「雲端大門有沒有打開？」）
        uid: String?                  // 使用者的唯一編號（像身分證字號，用來識別是誰的資料）
    ) {
        preferences.edit()   // 打開筆記本，準備寫東西（拿出筆來）
            .putBoolean(KEY_FIREBASE_CONFIGURED, firebaseConfigured)  // 寫下「雲端大門狀態」
            .putString(KEY_USER_ID, uid)                              // 寫下「使用者的身分證字號」
            .apply()          // 合上筆記本，儲存！（記得把筆蓋蓋好）
        // apply()：
        // 👉 非同步儲存，通常比較快
        // 👉 不會卡住畫面
        //
        // 可以想成：
        // 👉 「先交給小秘書去寫，我們畫面繼續跑」🏃‍♀️

    }

    // ============================================================
    // 🚪 清除登入狀態（使用者登出時呼叫）
    // ============================================================
    // 白話文：使用者說「我要登出了！把日記上有關我的紀錄清掉！」
    //
    // 情境劇：
    //   使用者按下「登出」按鈕
    //   App 會把使用者 ID 刪掉，並記錄：「已登出，請重新登入以找回雲端資料」
    //   這樣下次打開 App 時，就知道「喔，上次使用者登出了，現在是未登入狀態」
    fun clearForSignedOutUser(message: String = "已登出，請重新登入以找回雲端資料") {
        preferences.edit()
            .putString(KEY_USER_ID, null)                    // 把使用者 ID 擦掉（變成空白，就像把名字從日記上塗掉）
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)     // 記錄「上一次同步失敗」（因為登出後就不能同步了）
            .putString(KEY_LAST_SYNC_MESSAGE, message)       // 寫下原因：「使用者登出了啦！」
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間（寫下日記的日期）
            .apply()
    }

    // ============================================================
    // ✅ 記錄同步成功
    // ============================================================
    // 白話文：「耶！備份成功了！趕快在日記上記下來！」
    //
    // 情境劇：
    //   使用者的記帳資料成功上傳到雲端
    //   App 開心地寫下：「2026/03/26 15:30 雲端同步成功，備份了 50 筆交易！」
    fun markSyncSuccess(message: String = "雲端同步成功") {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間（「幾點成功的？」）
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, true)             // 成功！（畫一個笑臉 ✅）
            .putString(KEY_LAST_SYNC_MESSAGE, message)              // 寫下「成功」的訊息（開心紀錄）
            .apply()
    }

    // ============================================================
    // ❌ 記錄同步失敗
    // ============================================================
    // 白話文：「噢不！備份失敗了！要寫下來為什麼，下次才能改進」
    //
    // 情境劇：
    //   使用者沒有網路，或是 Firebase 出了問題
    //   App 嘆口氣寫下：「2026/03/26 15:30 同步失敗，原因是：沒有網路連線」
    //   下次使用者打開 App 時，就可以看到這個錯誤訊息，然後去檢查網路
    fun markSyncFailure(message: String) {
        preferences.edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())  // 記錄現在時間（「幾點失敗的？」）
            .putBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)            // 失敗...（畫一個哭臉 ❌）
            .putString(KEY_LAST_SYNC_MESSAGE, message)              // 寫下失敗的原因（「網路斷線」、「伺服器維修」...）
            .apply()
    }

    // ============================================================
    // 📖 讀取目前的同步狀態（從筆記本裡把資料翻出來看）
    // ============================================================
    // 白話文：「翻開日記，看看上次發生了什麼事」
    //
    // 情境劇：
    //   App 開機時，或是使用者點擊「同步狀態」按鈕時
    //   會呼叫這個函式，把所有紀錄讀出來，顯示在畫面上
    //   讓使用者知道：「上次備份是昨天，成功了」或「上次備份失敗，因為沒網路」
    fun getStatus(): SyncStatus {
        // 從筆記本裡讀取使用者 ID（如果沒有這筆紀錄就是 null，代表沒登入過）
        val userId = preferences.getString(KEY_USER_ID, null)

        // 檢查筆記本裡有沒有「上次同步時間」這筆紀錄
        // 白話文：翻翻日記，看看有沒有寫過「上次備份時間」
        val hasLastSync = preferences.contains(KEY_LAST_SYNC_AT)
        val lastSyncAt = if (hasLastSync) preferences.getLong(KEY_LAST_SYNC_AT, 0L) else null

        // 檢查筆記本裡有沒有「同步結果」這筆紀錄
        // 白話文：翻翻日記，看看上次備份是成功還是失敗
        val hasSyncResult = preferences.contains(KEY_LAST_SYNC_SUCCESSFUL)
        val syncSuccessful = if (hasSyncResult) {
            preferences.getBoolean(KEY_LAST_SYNC_SUCCESSFUL, false)
        } else {
            null  // 從來沒同步過（日記上沒有相關紀錄）
        }

        // 把讀到的資料打包成一個 SyncStatus 物件（就像把日記的內容整理成一張摘要卡片）
        return SyncStatus(
            isFirebaseConfigured = preferences.getBoolean(KEY_FIREBASE_CONFIGURED, false),
            isSignedIn = !userId.isNullOrBlank(),  // 有使用者 ID 就表示有登入（「日記上有名字嗎？」）
            userId = userId,
            lastSyncAt = lastSyncAt,
            lastSyncSuccessful = syncSuccessful,
            lastSyncMessage = preferences.getString(KEY_LAST_SYNC_MESSAGE, "尚未進行雲端同步")
                ?: "尚未進行雲端同步"  // 如果日記上沒寫，就說「從來沒同步過」
        )
    }

    // ============================================================
    // 🗝️ 「常數」：固定不變的鑰匙名稱（就像日記本裡的章節標題）
    // ============================================================
    // companion object：
    // 👉 可以放這個 class 專用的常數
    // 👉 不需要建立 SyncStatusStore 物件，也能被 class 內部使用
    //
    // 為什麼要把 key 集中放這裡？
    // 👉 避免到處散落字串
    // 👉 改名字比較方便
    // 👉 降低打錯字造成資料讀不到的機率
    // 白話文：這些是筆記本裡的「頁面標題」，每一頁都有固定的名稱
    //         寫的時候要用對標題，讀的時候才找得到
    companion object {
        private const val PREF_NAME = "cococoin_sync_status"           // 筆記本的名字（「雲端同步日記本」）
        private const val KEY_FIREBASE_CONFIGURED = "firebase_configured"  // Firebase 有沒有設定好（「雲端大門鑰匙」）
        private const val KEY_USER_ID = "user_id"                      // 使用者 ID 的欄位名稱（「誰的日記」）
        private const val KEY_LAST_SYNC_AT = "last_sync_at"            // 最後同步時間（「最後寫日記的日期」）
        private const val KEY_LAST_SYNC_SUCCESSFUL = "last_sync_successful"  // 同步成功與否（「那天開心嗎？」）
        private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"  // 同步結果的說明文字（「日記的內容」）
    }
}