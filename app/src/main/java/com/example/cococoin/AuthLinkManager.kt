package com.example.cococoin

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// 帳號連結管理器：
// 負責處理 Firebase 的登入、登出、綁定 Email/Google 帳號
// 支援匿名登入（不用註冊就能用雲端備份），也支援升級成正式帳號
class AuthLinkManager(
    context: Context
) {
    // 使用 Application Context（避免記憶體洩漏）
    private val appContext = context.applicationContext

    // 同步狀態儲存器（記錄登入狀態）
    private val syncStatusStore = SyncStatusStore(appContext)

    // 取得目前的帳號連結狀態：
    // 檢查 Firebase 是否可用、使用者有沒有登入、是匿名還是正式、綁了哪些登入方式
    fun getStatus(): AuthLinkStatus {
        // 檢查 Firebase 是否可用
        if (!FirebaseInitializer.canUseFirebase(appContext)) {
            return AuthLinkStatus(
                isFirebaseReady = false,
                isSignedIn = false,
                isAnonymous = true,
                uid = null,
                email = null,
                hasEmailProvider = false,
                hasGoogleProvider = false,
                isEmailVerified = false
            )
        }

        // 取得 FirebaseAuth 實例和當前使用者
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val user = auth?.currentUser

        // 取得使用者已綁定的登入方式（providerId 列表）：
        // 過濾掉 "firebase" 這個內部 provider，只留下 "google.com"、"password" 等
        val providerIds = user?.providerData
            ?.mapNotNull { provider -> provider.providerId }
            ?.filter { it != "firebase" }
            .orEmpty()

        return AuthLinkStatus(
            isFirebaseReady = auth != null,
            isSignedIn = user != null,
            isAnonymous = user?.isAnonymous ?: true,  // 沒登入或匿名都算匿名
            uid = user?.uid,
            email = user?.email,
            hasEmailProvider = providerIds.contains(EmailAuthProvider.PROVIDER_ID),  // "password"
            hasGoogleProvider = providerIds.contains(GoogleAuthProvider.PROVIDER_ID),  // "google.com"
            isEmailVerified = user?.isEmailVerified ?: false
        )
    }

    suspend fun getFreshStatus(): AuthLinkStatus {
        val auth = getAuthOrNull() ?: return getStatus()
        val user = auth.currentUser
        if (user != null) {
            suspendCancellableCoroutine<Unit> { continuation ->
                user.reload()
                    .addOnCompleteListener {
                        continuation.resume(Unit)
                    }
            }
        }
        return getStatus()
    }

    // 綁定 Email 登入方式（非掛起函式，用 callback 回傳）：
    // 把 Email 和密碼綁定到目前的匿名帳號，這樣之後就能用 Email 登入
    fun linkEmailPassword(
        email: String,
        password: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))
        val user = auth.currentUser
            ?: return onComplete(OperationResult.fail("目前沒有可綁定的登入帳號"))

        // 檢查是否已經綁定過 Email
        if (getStatus().hasEmailProvider) {
            onComplete(OperationResult.fail("此帳號已綁定 Email"))
            return
        }

        // 建立 Email 憑證
        val credential = EmailAuthProvider.getCredential(email, password)

        // 把憑證綁定到目前的使用者
        user.linkWithCredential(credential)
            .addOnSuccessListener {
                user.sendEmailVerification()
                    .addOnSuccessListener {
                        syncStatusStore.markAuthState(firebaseConfigured = true, uid = user.uid)
                        onComplete(OperationResult.ok("已寄出驗證信，請收信完成驗證後再回到 App"))
                    }
                    .addOnFailureListener { exception ->
                        onComplete(OperationResult.fail(exception.message ?: "驗證信寄送失敗"))
                    }
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(mapLinkError(exception, "Email 綁定失敗")))
            }
    }

    fun sendEmailVerification(onComplete: (OperationResult) -> Unit) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))
        val user = auth.currentUser
            ?: return onComplete(OperationResult.fail("目前沒有可驗證的帳號"))

        if (!getStatus().hasEmailProvider) {
            onComplete(OperationResult.fail("目前帳號尚未綁定 Email"))
            return
        }

        if (user.isEmailVerified) {
            onComplete(OperationResult.ok("這個 Email 已完成驗證"))
            return
        }

        user.sendEmailVerification()
            .addOnSuccessListener {
                onComplete(OperationResult.ok("驗證信已重新寄出，請到信箱完成驗證"))
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(exception.message ?: "驗證信寄送失敗"))
            }
    }

    // 綁定 Google 登入方式（掛起函式，可用協程等待）：
    // 跳出 Google 帳號選擇畫面，讓使用者選要綁定的 Google 帳號
    suspend fun linkGoogle(activity: Activity): OperationResult {
        val auth = getAuthOrNull() ?: return OperationResult.fail("Firebase Auth 尚未初始化")
        val user = auth.currentUser ?: return OperationResult.fail("目前沒有可綁定的登入帳號")

        // 檢查是否已經綁定過 Google
        if (getStatus().hasGoogleProvider) {
            return OperationResult.fail("此帳號已綁定 Google")
        }

        // 取得 Google 的 Web Client ID（從 strings.xml）
        val serverClientId = getDefaultWebClientId()
            ?: return OperationResult.fail("找不到 default_web_client_id，請重新下載 google-services.json")

        return try {
            // 1.建立 Google 登入選項
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            // 2.建立請求
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            // 3.跳出 Google 帳號選擇器，讓使用者選擇
            val result = CredentialManager.create(activity).getCredential(
                context = activity,
                request = request
            )

            // 4.解析 Google 回傳的憑證
            val credential = result.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(
                    googleCredential.idToken,
                    null
                )

                // 5.綁定到 Firebase
                linkWithCredentialSuspend(user, firebaseCredential)
            } else {
                OperationResult.fail("Google 憑證格式不正確")
            }
        } catch (_: GetCredentialCancellationException) {
            // 使用者取消選擇
            OperationResult.fail("已取消 Google 綁定")
        } catch (exception: GetCredentialException) {
            OperationResult.fail(
                when {
                    exception.message?.contains("No credential", ignoreCase = true) == true ->
                        "找不到可用的 Google 登入憑證，請確認裝置已登入 Google 帳號，並已在 Firebase 啟用 Google 登入"
                    else -> exception.message ?: "取得 Google 憑證失敗"
                }
            )
        } catch (exception: Exception) {
            OperationResult.fail(exception.message ?: "Google 綁定失敗")
        }
    }

    // 內部函式：用掛起函式執行綁定（讓 linkGoogle 可以用協程等待）
    private suspend fun linkWithCredentialSuspend(
        user: FirebaseUser,
        credential: AuthCredential
    ): OperationResult = suspendCancellableCoroutine { continuation ->
        user.linkWithCredential(credential)
            .addOnSuccessListener {
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = user.uid)
                continuation.resume(OperationResult.ok("已成功綁定 Google 登入"))
            }
            .addOnFailureListener { exception ->
                continuation.resume(
                    OperationResult.fail(
                        mapLinkError(exception, "Google 綁定失敗")
                    )
                )
            }
    }

    // 用 Email 登入（非掛起函式，用 callback 回傳）
    fun signInWithEmailPassword(
        email: String,
        password: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))

        // 使用 Email 和密碼登入
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                syncStatusStore.markAuthState(
                    firebaseConfigured = true,
                    uid = result.user?.uid
                )
                onComplete(OperationResult.ok("Email 登入成功"))
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(exception.message ?: "Email 登入失敗"))
            }
    }

    fun sendPasswordResetEmail(
        email: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))

        auth.fetchSignInMethodsForEmail(email.trim())
            .addOnSuccessListener { result ->
                val hasPasswordAccount =
                    result.signInMethods?.contains(EmailAuthProvider.PROVIDER_ID) == true
                if (!hasPasswordAccount) {
                    onComplete(OperationResult.fail("這個 Email 尚未註冊 Email 密碼登入"))
                    return@addOnSuccessListener
                }

                auth.sendPasswordResetEmail(email.trim())
                    .addOnSuccessListener {
                        onComplete(OperationResult.ok("已寄出重設密碼信，請到信箱開啟連結設定新密碼"))
                    }
                    .addOnFailureListener { exception ->
                        onComplete(OperationResult.fail(exception.message ?: "重設密碼信寄送失敗"))
                    }
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(exception.message ?: "無法確認此 Email 是否已註冊"))
            }
    }

    // 用 Google 登入（掛起函式）：
    // 跳出 Google 帳號選擇畫面，用選中的帳號登入
    suspend fun signInWithGoogle(activity: Activity): OperationResult {
        val auth = getAuthOrNull() ?: return OperationResult.fail("Firebase Auth 尚未初始化")

        // 取得 Google 憑證
        val firebaseCredential = getGoogleCredential(activity) ?: return OperationResult.fail(
            "找不到可用的 Google 登入憑證，請確認裝置已登入 Google 帳號，並已在 Firebase 啟用 Google 登入"
        )

        return suspendCancellableCoroutine { continuation ->
            // 使用 Google 憑證登入
            auth.signInWithCredential(firebaseCredential)
                .addOnSuccessListener { result ->
                    syncStatusStore.markAuthState(
                        firebaseConfigured = true,
                        uid = result.user?.uid
                    )
                    continuation.resume(OperationResult.ok("Google 登入成功"))
                }
                .addOnFailureListener { exception ->
                    continuation.resume(
                        OperationResult.fail(exception.message ?: "Google 登入失敗")
                    )
                }
        }
    }

    // 取得 Google 登入憑證（內部輔助函式）
    private suspend fun getGoogleCredential(activity: Activity): AuthCredential? {
        val serverClientId = getDefaultWebClientId() ?: return null

        return try {
            // 建立 Google 登入選項
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            // 建立請求
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            // 跳出 Google 帳號選擇器
            val result = CredentialManager.create(activity).getCredential(
                context = activity,
                request = request
            )

            // 解析憑證
            val credential = result.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            } else {
                null
            }
        } catch (_: GetCredentialCancellationException) {
            // 使用者取消
            null
        } catch (_: GetCredentialException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    // 把錯誤訊息轉成使用者看得懂的文字
    private fun mapLinkError(exception: Exception, fallback: String): String {
        return when (exception) {
            // 帳號衝突：這個 Email/Google 已經綁定到其他 Firebase 帳號了
            is FirebaseAuthUserCollisionException -> {
                "這個登入方式已綁定到其他帳號，不能直接綁到目前匿名帳號"
            }
            else -> exception.message ?: fallback
        }
    }

    // 取得 FirebaseAuth 實例（如果不能用就回 null）
    private fun getAuthOrNull(): FirebaseAuth? {
        if (!FirebaseInitializer.canUseFirebase(appContext)) {
            return null
        }
        return runCatching { FirebaseAuth.getInstance() }.getOrNull()
    }

    // 取得 Google 的 Web Client ID（從 strings.xml）：Google 用來識別 App 用的
    private fun getDefaultWebClientId(): String? {
        val resourceId = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (resourceId == 0) return null

        return appContext.getString(resourceId).takeIf { it.isNotBlank() }
    }
}
