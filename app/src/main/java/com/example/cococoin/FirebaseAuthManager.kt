// ============================================================
// 🔐 Firebase 認證管理器 — 白話文：雲端大門的「守衛」+「訪客通行證發放員」
// ============================================================
//
// 情境劇：想像你要進入一棟有雲端備份服務的大樓（Firebase）
//         這個守衛的工作就是：
//           1. 確認你有沒有資格進入（有沒有登入）
//           2. 如果沒有，就發給你一張「訪客通行證」（匿名登入）
//           3. 讓你不用註冊帳號就能先進去看一看
//           4. 如果你之後想升級成正式會員，可以用這張通行證綁定 Google 或信箱
//
// 為什麼要匿名登入？
//   很多使用者不喜歡一開始就被迫註冊帳號
//   匿名登入讓他們「先體驗，後註冊」，降低使用門檻
//   等他們覺得 App 好用，再升級成完整會員，資料也不會消失！
// ============================================================
package com.example.cococoin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

// object 關鍵字：單例模式，白話文就是「全宇宙只有一個守衛」
// 不管你在 App 的哪裡呼叫 FirebaseAuthManager，拿到的都是同一個！
object FirebaseAuthManager {

    // ============================================================
    // 🎫 確保使用者已登入 — 白話文：檢查通行證，沒有的話就發一張訪客證
    // ============================================================
    // 這是最重要的函式！在 App 啟動時呼叫
    // 它會：
    //   1. 檢查 Firebase 能不能用
    //   2. 檢查使用者是否已經登入
    //   3. 如果已經登入，直接回傳 UID（身分證字號）
    //   4. 如果還沒登入，自動執行「匿名登入」，發一張訪客通行證
    fun ensureSignedIn(
        context: Context,
        onComplete: (String?) -> Unit  // 完成後回傳使用者的 UID（沒有的話就 null）
    ) {
        // 拿出「同步狀態小筆記本」，準備記錄這次的狀況
        val syncStatusStore = SyncStatusStore(context)

        // ----- 檢查 1：Firebase 能不能用？ -----
        // 如果不能用（例如 google-services.json 沒放好），就放棄登入
        if (!FirebaseInitializer.canUseFirebase(context)) {
            syncStatusStore.markAuthState(firebaseConfigured = false, uid = null)
            onComplete(null)
            return
        }

        // ----- 檢查 2：能不能拿到 FirebaseAuth 實例？ -----
        // runCatching 是安全防護罩，避免 crash
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        if (auth == null) {
            syncStatusStore.markAuthState(firebaseConfigured = true, uid = null)
            onComplete(null)
            return
        }

        // ----- 檢查 3：已經有使用者登入了嗎？ -----
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 有！表示之前已經登入過了（可能是匿名或用 Google 登入）
            // 直接回傳他的 UID，不需要重新登入
            syncStatusStore.markAuthState(firebaseConfigured = true, uid = currentUser.uid)
            onComplete(currentUser.uid)
            return
        }

        // ----- 沒有使用者登入 → 執行匿名登入（發訪客通行證）-----
        // 白話文：你是新朋友，先給你一張訪客證，讓你進去逛逛
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                // 登入成功！拿到 UID 了（訪客證號碼）
                val uid = result.user?.uid
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = uid)
                onComplete(uid)
            }
            .addOnFailureListener {
                // 登入失敗...可能是網路問題或 Firebase 出狀況
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = null)
                onComplete(null)
            }
    }

    // ============================================================
    // 🆔 取得目前使用者的 UID — 白話文：看看現在誰在裡面
    // ============================================================
    // UID 是使用者的「唯一識別碼」，就像身分證字號
    // 沒登入的話就回傳 null
    fun currentUidOrNull(): String? {
        // runCatching 安全防護：如果 Firebase 還沒初始化，不會 crash
        return runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    }

    // ============================================================
    // 🚪 登出 — 白話文：守衛趕人啦！
    // ============================================================
    // 使用者按「登出」按鈕時呼叫
    // 會做兩件事：
    //   1. 告訴 Firebase「這位使用者要離開了」
    //   2. 清除本機的同步狀態記錄（避免下次啟動時自動登入）
    fun signOut(context: Context) {
        // 執行 Firebase 登出（把雲端大門的記錄清除）
        runCatching { FirebaseAuth.getInstance().signOut() }

        // 清除本機的同步狀態記錄（小筆記本）
        // 這樣下次打開 App 時，會重新以匿名身份登入
        SyncStatusStore(context).clearForSignedOutUser()
    }
}
/*
Firebase	            Google 開的雲端銀行，可以存資料、推播通知
FirebaseInitializer	    幫你辦銀行帳戶的專員
google-services.json	Google 發給你的「快速通關VIP卡」
strings.xml 裡的設定	    備用申請資料（沒有VIP卡時手動填表）
@Volatile	            群組公告要確保大家都收到最新消息
synchronized	        櫃檯的「一次只服務一位客人」告示牌
runCatching	            安全氣囊，失敗也不會爆炸
attemptedInitialization	已經跑過流程的「已處理」標記
 */