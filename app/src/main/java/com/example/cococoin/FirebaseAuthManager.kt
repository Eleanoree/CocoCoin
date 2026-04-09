package com.example.cococoin

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

// Firebase 認證管理器：
// 負責使用者的登入/登出。支援「匿名登入」，讓使用者不用註冊就能使用雲端備份
object FirebaseAuthManager {

    // 確保使用者已登入（如果還沒登入，就自動匿名登入） ：
    // 檢查 Firebase 有沒有登入，沒有的話就自動用「匿名」方式登入
    fun ensureSignedIn(
        context: Context,
        onComplete: (String?) -> Unit  // 完成後回傳使用者的 UID（唯一識別碼）
    ) {
        val syncStatusStore = SyncStatusStore(context)

        // 檢查 Firebase 是否可用
        if (!FirebaseInitializer.canUseFirebase(context)) {
            syncStatusStore.markAuthState(firebaseConfigured = false, uid = null)
            onComplete(null)
            return
        }

        // 取得 FirebaseAuth 實例
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        if (auth == null) {
            syncStatusStore.markAuthState(firebaseConfigured = true, uid = null)
            onComplete(null)
            return
        }

        // 如果已經有使用者登入，直接回傳他的 UID
        val currentUser = auth.currentUser
        if (currentUser != null) {
            syncStatusStore.markAuthState(firebaseConfigured = true, uid = currentUser.uid)
            onComplete(currentUser.uid)
            return
        }

        // 沒有使用者登入 → 執行匿名登入
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = uid)
                onComplete(uid)
            }
            .addOnFailureListener {
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = null)
                onComplete(null)
            }
    }

    // 取得目前使用者的 UID（如果沒登入就回 null）
    fun currentUidOrNull(): String? {
        return runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    }

    // 登出
    fun signOut(context: Context) {
        // 執行 Firebase 登出
        runCatching { FirebaseAuth.getInstance().signOut() }
        // 清除本機的同步狀態記錄
        SyncStatusStore(context).clearForSignedOutUser()
    }
}