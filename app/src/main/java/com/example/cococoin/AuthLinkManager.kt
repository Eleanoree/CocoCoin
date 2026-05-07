// ============================================================
// 🔐 帳號連結管理器 — 白話文：雲端登入服務台的「櫃檯人員」
// ============================================================
// 
// 情境劇：想像你走進一間大樓的服務台，櫃檯人員可以幫你處理：
//           🎫 發放訪客證（匿名登入）
//           🪪 升級成正式會員卡（綁定 Email/Google）
//           🔓 用會員卡登入（Email/Google 登入）
//           🔗 綁定多種登入方式（例如同時綁 Email 和 Google）
//           🔓 解除綁定（不再用某種方式登入）
//           🚪 辦理離開（登出）
//
// 這個 AuthLinkManager 就是那位「櫃檯人員」！
// 所有跟帳號有關的操作（登入、登出、綁定、解除綁定）都找它
// ============================================================
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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AuthLinkManager(
    context: Context
) {
    // 使用 Application Context（避免記憶體洩漏，App 關閉才消失）
    private val appContext = context.applicationContext

    // 同步狀態儲存器（記錄「現在誰登入了？」）
    private val syncStatusStore = SyncStatusStore(appContext)

    // ============================================================
    // 📋 取得目前的帳號連結狀態 — 白話文：看看現在誰在裡面？
    // ============================================================
    // 這張報告會告訴你：
    //   - Firebase 準備好了沒？
    //   - 有沒有人登入？
    //   - 是訪客還是正式會員？
    //   - 綁了哪些登入方式（Email、Google）？
    fun getStatus(): AuthLinkStatus {
        // 第一步：檢查 Firebase 能不能用
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

        // 第二步：取得 FirebaseAuth 實例和目前登入的使用者
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val user = auth?.currentUser

        // 第三步：取得使用者已綁定的登入方式（providerId 列表）
        // 過濾掉 "firebase" 這個內部 provider，只留下 "google.com"、"password" 等
        // 白話文：使用者是用什麼方式登入的？Google？Email？還是訪客？
        val providerIds = user?.providerData
            ?.mapNotNull { provider -> provider.providerId }
            ?.filter { it != "firebase" }
            .orEmpty()

        return AuthLinkStatus(
            isFirebaseReady = auth != null,
            isSignedIn = user != null,
            isAnonymous = user?.isAnonymous ?: true,  // 沒登入或匿名都算訪客模式
            uid = user?.uid,
            email = user?.email,
            hasEmailProvider = providerIds.contains(EmailAuthProvider.PROVIDER_ID),  // "password"
            hasGoogleProvider = providerIds.contains(GoogleAuthProvider.PROVIDER_ID),  // "google.com"
            isEmailVerified = user?.isEmailVerified ?: false
        )
    }

    // ============================================================
    // 🔄 取得最新的帳號狀態（強制刷新）— 白話文：去雲端確認一下再回來
    // ============================================================
    // 一般的 getStatus() 只讀本機快取的資料
    // 這個函式會先呼叫 user.reload() 從 Firebase 雲端重新載入使用者資料
    // 確保拿到的是最新狀態（例如 Email 驗證狀態可能在其他裝置改了）
    suspend fun getFreshStatus(): AuthLinkStatus {
        val auth = getAuthOrNull() ?: return getStatus()
        val user = auth.currentUser
        if (user != null) {
            // 掛起函式：等 Firebase 回傳結果才繼續
            suspendCancellableCoroutine<Unit> { continuation ->
                user.reload()  // 從雲端重新載入使用者資料
                    .addOnCompleteListener {
                        continuation.resume(Unit)
                    }
            }
        }
        return getStatus()
    }

    // ============================================================
    // 📧 綁定 Email 登入方式 — 白話文：用 Email + 密碼升級成正式會員
    // ============================================================
    // 這個函式給「匿名登入」的使用者使用
    // 讓他們輸入 Email 和密碼，把目前的訪客帳號升級成正式會員
    // 之後就可以用 Email 登入，資料也不會消失了！
    fun linkEmailPassword(
        email: String,
        password: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))
        val user = auth.currentUser
            ?: return onComplete(OperationResult.fail("目前沒有可綁定的登入帳號"))

        // 檢查是否已經綁定過 Email（避免重複綁定）
        if (getStatus().hasEmailProvider) {
            onComplete(OperationResult.fail("此帳號已綁定 Email"))
            return
        }

        // 建立 Email 憑證
        val credential = EmailAuthProvider.getCredential(email.trim(), password)

        // 把憑證綁定到目前的使用者帳號
        user.linkWithCredential(credential)
            .addOnSuccessListener {
                // 綁定成功！記錄狀態
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = user.uid)
                onComplete(OperationResult.ok("信箱綁定成功"))
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(mapLinkError(exception, "Email 綁定失敗")))
            }
    }

    // ============================================================
    // 🔓 解除綁定 Email 登入方式 — 白話文：不能再使用 Email 登入這帳號
    // ============================================================
    fun unlinkEmailProvider(onComplete: (OperationResult) -> Unit) {
        unlinkProvider(
            providerId = EmailAuthProvider.PROVIDER_ID,
            providerName = "信箱",
            onComplete = onComplete
        )
    }

    // ============================================================
    // 🔓 解除綁定 Google 登入方式 — 白話文：不能再使用 Google 登入這帳號
    // ============================================================
    fun unlinkGoogleProvider(onComplete: (OperationResult) -> Unit) {
        unlinkProvider(
            providerId = GoogleAuthProvider.PROVIDER_ID,
            providerName = "Google",
            onComplete = onComplete
        )
    }

    // ============================================================
    // 🔓 解除綁定的通用函式 — 白話文：解除綁定的標準流程
    // ============================================================
    private fun unlinkProvider(
        providerId: String,
        providerName: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))
        val user = auth.currentUser
            ?: return onComplete(OperationResult.fail("目前沒有已登入帳號"))

        // 檢查是否真的綁定過（避免解除不存在的綁定）
        val status = getStatus()
        val hasProvider = when (providerId) {
            EmailAuthProvider.PROVIDER_ID -> status.hasEmailProvider
            GoogleAuthProvider.PROVIDER_ID -> status.hasGoogleProvider
            else -> false
        }
        if (!hasProvider) {
            onComplete(OperationResult.fail("此帳號尚未綁定 $providerName"))
            return
        }

        // 執行解除綁定
        user.unlink(providerId)
            .addOnSuccessListener {
                syncStatusStore.markAuthState(firebaseConfigured = true, uid = user.uid)
                onComplete(OperationResult.ok("已解除綁定 $providerName"))
            }
            .addOnFailureListener { exception ->
                onComplete(OperationResult.fail(exception.message ?: "解除綁定 $providerName 失敗"))
            }
    }

    // ============================================================
    // 🔴 綁定 Google 登入方式 — 白話文：用 Google 帳號升級成正式會員
    // ============================================================
    // 這個函式會跳出 Google 帳號選擇畫面
    // 使用者選中一個 Google 帳號後，會綁定到目前的 Firebase 帳號
    suspend fun linkGoogle(activity: Activity): OperationResult {
        val auth = getAuthOrNull() ?: return OperationResult.fail("Firebase Auth 尚未初始化")
        val user = auth.currentUser ?: return OperationResult.fail("目前沒有可綁定的登入帳號")

        // 檢查是否已經綁定過 Google
        if (getStatus().hasGoogleProvider) {
            return OperationResult.fail("此帳號已綁定 Google")
        }

        // 取得 Google 的 Web Client ID（從 google-services.json 生成的 strings.xml）
        val serverClientId = getDefaultWebClientId()
            ?: return OperationResult.fail("找不到 default_web_client_id，請重新下載 google-services.json")

        return try {
            // 1. 建立 Google 登入選項
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            // 2. 建立請求（跟 Google 說「我要登入」）
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            // 3. 跳出 Google 帳號選擇器，讓使用者選帳號
            val result = CredentialManager.create(activity).getCredential(
                context = activity,
                request = request
            )

            // 4. 解析 Google 回傳的憑證
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

                // 5. 綁定到 Firebase
                linkWithCredentialSuspend(user, firebaseCredential)
            } else {
                OperationResult.fail("Google 憑證格式不正確")
            }
        } catch (_: GetCredentialCancellationException) {
            // 使用者按了取消，不是錯誤，只是不想綁定
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

    // ============================================================
    // 📧 用 Email 登入 — 白話文：用 Email + 密碼登入既有帳號
    // ============================================================
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

    // ============================================================
    // 📧 寄送重設密碼信 — 白話文：忘記密碼？去信箱收信重置
    // ============================================================
    fun sendPasswordResetEmail(
        email: String,
        onComplete: (OperationResult) -> Unit
    ) {
        val auth = getAuthOrNull()
            ?: return onComplete(OperationResult.fail("Firebase Auth 尚未初始化"))

        // 先檢查這個 Email 是否已經註冊過
        auth.fetchSignInMethodsForEmail(email.trim())
            .addOnSuccessListener { result ->
                val hasPasswordAccount =
                    result.signInMethods?.contains(EmailAuthProvider.PROVIDER_ID) == true
                if (!hasPasswordAccount) {
                    onComplete(OperationResult.fail("這個 Email 尚未註冊 Email 密碼登入"))
                    return@addOnSuccessListener
                }

                // 有註冊過，寄送重設密碼信
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

    // ============================================================
    // 🔴 用 Google 登入 — 白話文：用 Google 帳號登入既有帳號
    // ============================================================
    // 這個函式會跳出 Google 帳號選擇畫面
    // 使用者選中一個 Google 帳號後，會用那個帳號登入 Firebase
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

    // ============================================================
    // 🔧 內部輔助函式們 — 白話文：櫃檯人員的工具箱
    // ============================================================

    // 取得 Google 登入憑證（內部輔助函式）
    private suspend fun getGoogleCredential(activity: Activity): AuthCredential? {
        val serverClientId = getDefaultWebClientId() ?: return null

        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = CredentialManager.create(activity).getCredential(
                context = activity,
                request = request
            )

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
            null  // 使用者取消
        } catch (_: GetCredentialException) {
            null  // 取得憑證失敗
        } catch (_: Exception) {
            null  // 其他錯誤
        }
    }

    // 把錯誤訊息轉成使用者看得懂的文字
    private fun mapLinkError(exception: Exception, fallback: String): String {
        return when (exception) {
            // 帳號衝突：這個 Email/Google 已經綁定到其他 Firebase 帳號了
            is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                "帳號已綁定"
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

    // 取得 Google 的 Web Client ID（從 strings.xml）
    // 這個 ID 是 Google 用來識別 App 用的，在 google-services.json 裡會自動產生
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