// ============================================================
// ☁️ 雲端同步管理器 — 白話文：記帳本的「雲端管家」+「資料合併大師」
// ============================================================
//
// 情境劇：想像你有一本記帳本（本機資料），還有一個雲端儲存空間（Firebase）
//         管家的工作就是：
//           1. 把你的記帳本備份到雲端（避免手機壞掉）
//           2. 從雲端下載備份回來（換新手機時）
//           3. 如果你同時在手機和平板上記帳，管家會把兩邊的資料「合併」起來
//              （誰最後修改的就用誰的，不會互相覆蓋）
//
// 這個類別負責跟 Firebase Firestore 溝通，把本機的 CocoCoinSnapshot
// （資料快照）上傳到下載，並處理多裝置之間的資料衝突
// ============================================================
package com.example.cococoin

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseSyncManager(
    private val context: Context  // App 的環境資訊（用來取得裝置 ID）
) {
    // 記錄同步狀態的小筆記本（上次同步時間、成功與否...）
    private val syncStatusStore = SyncStatusStore(context)

    // ============================================================
    // 🚀 啟動同步 — 白話文：管家開始工作！比對本機和雲端的資料
    // ============================================================
    // 這是整個同步流程的「總指揮」，在 App 啟動或使用者登入後呼叫
    // 它會：
    //   1. 檢查使用者有沒有登入
    //   2. 從雲端讀取備份資料
    //   3. 根據情況決定：上傳、下載、還是合併
    //   4. 最後記錄同步結果
    fun bootstrapSync(
        localSnapshot: CocoCoinSnapshot,                    // 本機的資料快照（手機裡面的記帳資料）
        applyRemoteSnapshot: (CocoCoinSnapshot) -> Unit,   // 把雲端資料寫入本機的函式
        onComplete: () -> Unit                              // 完成後要執行的函式
    ) {
        // ----- 步驟 1：檢查使用者是否已登入 Firebase -----
        // 如果沒登入，就像沒有雲端帳號，無法同步
        val collectionReference = getUserScopedCollectionReferenceOrNull() ?: run {
            syncStatusStore.markSyncFailure("尚未登入 Firebase，已改用本機資料")
            onComplete()
            return
        }

        // ----- 步驟 2：從雲端讀取所有備份資料 -----
        collectionReference.get()
            .addOnSuccessListener { snapshots ->  // 讀取成功！
                runCatching {  // 用安全防護罩包起來，避免 crash
                    // ----- 步驟 3：把多份備份合併成一份 -----
                    // 同一個使用者的不同裝置（手機、平板）會各自上傳備份
                    // 這裡把它們全部讀出來，然後合併成一份「雲端快照」
                    val remoteSnapshot = snapshots.documents
                        .mapNotNull { it.toCocoCoinSnapshot() }  // 每個文件轉成快照，失敗的跳過
                        .mergeAll()  // 合併所有快照（保留最新的版本）

                    // ----- 步驟 4：根據情況決定要做什麼 -----
                    when {
                        // 情況 1：雲端沒有資料，但本機有資料 → 上傳本機資料到雲端
                        // 白話文：你是第一次使用雲端備份，把你的資料傳上去
                        remoteSnapshot == null || remoteSnapshot.isEmpty() -> {
                            if (!localSnapshot.isEmpty()) {
                                pushSnapshot(localSnapshot)
                            }
                        }

                        // 情況 2：本機沒有資料，但雲端有資料 → 下載雲端資料到本機
                        // 白話文：你換了新手機，從雲端把舊資料抓回來
                        localSnapshot.isEmpty() -> applyRemoteSnapshot(remoteSnapshot)

                        // 情況 3：兩邊都有資料 → 合併（保留最新的版本）
                        // 白話文：你同時在手機和平板記帳，把兩邊的資料合併起來
                        else -> {
                            val mergedSnapshot = mergeSnapshots(localSnapshot, remoteSnapshot)
                            // 如果合併後的資料跟本機不一樣，就更新本機
                            if (mergedSnapshot != localSnapshot) {
                                applyRemoteSnapshot(mergedSnapshot)
                            }
                            // 如果合併後的資料跟雲端不一樣，就上傳到雲端
                            if (mergedSnapshot != remoteSnapshot) {
                                pushSnapshot(mergedSnapshot)
                            }
                        }
                    }

                    // 同步成功！記錄成功狀態
                    syncStatusStore.markSyncSuccess("雲端同步完成")
                }.onFailure { exception ->
                    // 處理過程中發生錯誤（例如資料格式壞掉）
                    syncStatusStore.markSyncFailure(
                        exception.message ?: "同步資料時發生錯誤，已改用本機資料"
                    )
                }
                onComplete()  // 不論成功失敗，最後都要告訴呼叫者「我完成了」
            }
            .addOnFailureListener { exception ->  // 讀取失敗（例如沒網路）
                syncStatusStore.markSyncFailure(
                    exception.message ?: "讀取雲端備份失敗，已改用本機資料"
                )
                onComplete()
            }
    }

    // ============================================================
    // 📤 上傳資料到雲端（非同步） — 白話文：把資料傳上去，不等結果
    // ============================================================
    // 白話文：「丟上去就好了，不用等我回來回報」
    // 使用場景：自動備份、背景同步（不需要立即知道結果）
    fun pushSnapshot(snapshot: CocoCoinSnapshot) {
        val documentReference = getUserScopedDocumentReferenceOrNull() ?: return

        // 把資料轉成 Map 格式，上傳到 Firestore
        documentReference.set(snapshot.toMap())
            .addOnSuccessListener {
                syncStatusStore.markSyncSuccess("雲端備份已更新")
            }
            .addOnFailureListener { exception ->
                syncStatusStore.markSyncFailure(
                    exception.message ?: "雲端備份更新失敗"
                )
            }
    }

    // ============================================================
    // 📥 從雲端下載資料（掛起函式） — 白話文：去雲端拿資料，等到拿到才回來
    // ============================================================
    // suspend 函式 = 可以「暫停」的函式
    // 白話文：我會去雲端拿資料，可能會花一點時間，你等我一下，拿到我就回來
    suspend fun fetchRemoteSnapshot(): CocoCoinSnapshot? = suspendCancellableCoroutine { continuation ->
        val collectionReference = getUserScopedCollectionReferenceOrNull()
        if (collectionReference == null) {
            continuation.resume(null)  // 沒登入，回傳 null
            return@suspendCancellableCoroutine
        }

        collectionReference.get()
            .addOnSuccessListener { snapshots ->
                val mergedSnapshot = snapshots.documents
                    .mapNotNull { it.toCocoCoinSnapshot() }
                    .mergeAll()
                continuation.resume(mergedSnapshot)  // 成功，回傳資料
            }
            .addOnFailureListener { exception ->
                syncStatusStore.markSyncFailure(
                    exception.message ?: "讀取雲端備份失敗"
                )
                continuation.resume(null)  // 失敗，回傳 null
            }
    }

    // ============================================================
    // 📤 上傳資料到雲端（等待結果） — 白話文：傳上去，等到傳完才回來
    // ============================================================
    // 白話文：「我去上傳備份，你等我一下，傳完我會告訴你成功還是失敗」
    // 使用場景：手動備份（使用者需要知道結果）
    suspend fun pushSnapshotAwait(snapshot: CocoCoinSnapshot): OperationResult =
        suspendCancellableCoroutine { continuation ->
            val documentReference = getUserScopedDocumentReferenceOrNull()
            if (documentReference == null) {
                continuation.resume(OperationResult.fail("尚未登入 Firebase，無法備份"))
                return@suspendCancellableCoroutine
            }

            documentReference.set(snapshot.toMap())
                .addOnSuccessListener {
                    syncStatusStore.markSyncSuccess("手動備份成功")
                    continuation.resume(OperationResult.ok("手動備份成功"))
                }
                .addOnFailureListener { exception ->
                    val message = exception.message ?: "手動備份失敗"
                    syncStatusStore.markSyncFailure(message)
                    continuation.resume(OperationResult.fail(message))
                }
        }

    // ============================================================
    // 🔧 工具函式們 — 白話文：管家的工具箱
    // ============================================================

    // 取得 Firestore 實例（如果 Firebase 可用）
    private fun getFirestoreOrNull(): FirebaseFirestore? {
        // 檢查 Firebase 是否可用（google-services.json 有沒有正確設定）
        if (!FirebaseInitializer.canUseFirebase(context)) {
            return null
        }

        return runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    // ============================================================
    // 📁 Firestore 路徑管理 — 白話文：決定資料要存放在雲端的哪個資料夾
    // ============================================================
    // Firestore 的資料結構像是一個檔案系統：
    //   users/
    //     └── {使用者ID}/
    //         └── device_backups/
    //             ├── device_1/（手機的備份）
    //             ├── device_2/（平板的備份）
    //             └── device_3/（另一支手機的備份）
    //
    // 這樣設計的好處：每個裝置都有自己的備份文件，不會互相覆蓋
    // 合併時再把它們全部讀出來整合

    // 取得使用者的文件參考（每位使用者有一份文件）：
    // Firestore 路徑：users/{使用者ID}/device_backups/{裝置ID}
    private fun getUserScopedDocumentReferenceOrNull(): DocumentReference? {
        val collection = getUserScopedCollectionReferenceOrNull() ?: return null
        return collection.document(getDeviceId())
    }

    // 取得使用者的集合參考：
    // Firestore 路徑：users/{使用者ID}/device_backups
    private fun getUserScopedCollectionReferenceOrNull(): CollectionReference? {
        val firestore = getFirestoreOrNull() ?: return null
        val uid = FirebaseAuthManager.currentUidOrNull() ?: return null  // 沒登入就無法同步

        return firestore
            .collection("users")           // 使用者集合（所有使用者的資料夾）
            .document(uid)                 // 當前使用者的文件夾
            .collection("device_backups")  // 裝置備份的子集合（存放不同手機的備份）
    }

    // 取得裝置的唯一識別碼（用來區分不同手機）：
    // 每支手機都有一個獨一無二的 ID（ANDROID_ID）
    // 用來區分「這份備份是從哪支手機來的」
    private fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        // 如果拿不到 Android ID（極少數情況），就用 "local-device" 代替
        return androidId?.takeIf { it.isNotBlank() } ?: "local-device"
    }

    // ============================================================
    // 🔄 資料轉換函式們 — 白話文：把資料「打包」成雲端看得懂的格式
    // ============================================================

    // 把資料快照轉成 Map（Firestore 只能存 Map 格式）
    private fun CocoCoinSnapshot.toMap(): Map<String, Any> {
        return mapOf(
            "updatedAt" to latestUpdatedAt(),  // 整份資料的最新更新時間（用於合併判斷）
            "transactions" to transactions.map { transaction ->
                mapOf(
                    "id" to transaction.id,
                    "type" to transaction.type,
                    "category" to transaction.category,
                    "amount" to transaction.amount,
                    "note" to transaction.note,
                    "time" to transaction.time,
                    "accountName" to transaction.accountName,
                    "updatedAt" to transaction.updatedAt
                )
            },
            "accounts" to accounts.map { account ->
                mapOf(
                    "id" to account.id,
                    "name" to account.name,
                    "balance" to account.balance,
                    "updatedAt" to account.updatedAt
                )
            },
            "budgets" to budgets.map { budget ->
                mapOf(
                    "year" to budget.year,
                    "month" to budget.month,
                    "amount" to budget.amount,
                    "updatedAt" to budget.updatedAt
                )
            },
            "categories" to categories.map { category ->
                mapOf(
                    "type" to category.type,
                    "name" to category.name,
                    "icon" to category.icon,
                    "updatedAt" to category.updatedAt
                )
            }
        )
    }

    // 把 Firestore 的文件轉成資料快照（解析雲端回傳的資料）
    private fun com.google.firebase.firestore.DocumentSnapshot.toCocoCoinSnapshot(): CocoCoinSnapshot? {
        if (!exists()) return null  // 文件不存在

        // 取得整份文件的更新時間（備份用）
        val rootUpdatedAt = (get("updatedAt") as? Number)?.toLong() ?: 0L

        // 解析交易資料（從 Map 轉回 Transaction 物件）
        val transactions = (get("transactions") as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            Transaction(
                id = (map["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                type = map["type"] as? String ?: return@mapNotNull null,
                category = map["category"] as? String ?: return@mapNotNull null,
                amount = (map["amount"] as? Number)?.toInt() ?: 0,
                note = map["note"] as? String ?: "",
                time = map["time"] as? String ?: "",
                accountName = map["accountName"] as? String ?: "未指定帳戶",
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: rootUpdatedAt
            )
        }

        // 解析帳戶資料
        val accounts = (get("accounts") as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            AssetAccount(
                id = (map["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                name = map["name"] as? String ?: return@mapNotNull null,
                balance = (map["balance"] as? Number)?.toInt() ?: 0,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: rootUpdatedAt
            )
        }

        // 解析預算資料
        val budgets = (get("budgets") as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            BudgetSetting(
                year = (map["year"] as? Number)?.toInt() ?: return@mapNotNull null,
                month = (map["month"] as? Number)?.toInt() ?: return@mapNotNull null,
                amount = (map["amount"] as? Number)?.toInt() ?: 0,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: rootUpdatedAt
            )
        }

        // 解析分類資料
        val categories = (get("categories") as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            TransactionCategoryDefinition(
                type = map["type"] as? String ?: return@mapNotNull null,
                name = map["name"] as? String ?: return@mapNotNull null,
                icon = map["icon"] as? String ?: TransactionCategoryCatalog.fallbackIcon(
                    map["type"] as? String ?: TransactionCategoryCatalog.TYPE_EXPENSE
                ),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: rootUpdatedAt
            )
        }

        return CocoCoinSnapshot(
            transactions = transactions,
            accounts = accounts,
            budgets = budgets,
            categories = categories
        )
    }

    // ============================================================
    // 🔀 合併函式們 — 白話文：管家的「合併魔法」
    // ============================================================
    // 這是整個同步機制最核心的部分！
    // 當多個裝置都有資料時，需要把它們合併起來，保留最新版本

    // 合併多份快照（多裝置備份時用）：
    // 把同一個使用者的多個裝置備份合併成一份
    private fun List<CocoCoinSnapshot>.mergeAll(): CocoCoinSnapshot? {
        if (isEmpty()) return null
        // reduce：把清單中的第一項和第二項合併，結果再和第三項合併...以此類推
        // 就像把好幾堆沙子倒在一起，攪拌均勻
        return reduce { acc, snapshot ->
            mergeSnapshots(acc, snapshot)
        }
    }

    // 合併兩份快照（保留最新的版本）：
    // 比對兩邊的資料，誰的 updatedAt 比較新就用誰的
    private fun mergeSnapshots(
        base: CocoCoinSnapshot,     // 基準快照（本機或雲端）
        incoming: CocoCoinSnapshot  // 另一份快照
    ): CocoCoinSnapshot {

        // ----- 合併交易 -----
        // 用 type+category+amount+note+time+accountName 當作「指紋」
        // 白話文：如果兩筆交易的「長相」一模一樣，就當作是同一筆，保留比較新的那筆
        val mergedTransactions = linkedMapOf<String, Transaction>()
        (base.transactions + incoming.transactions).forEach { transaction ->
            val key = buildTransactionKey(transaction)  // 產生這筆交易的「指紋」
            val existing = mergedTransactions[key]
            // 如果沒有這筆交易，或者新的比舊的更新，就覆蓋
            if (existing == null || transaction.updatedAt >= existing.updatedAt) {
                mergedTransactions[key] = transaction
            }
        }

        // ----- 合併帳戶 -----
        // 用名稱當作唯一鍵（例如「現金」、「信用卡」）
        val mergedAccounts = linkedMapOf<String, AssetAccount>()
        (base.accounts + incoming.accounts).forEach { account ->
            val existing = mergedAccounts[account.name]
            if (existing == null || account.updatedAt >= existing.updatedAt) {
                mergedAccounts[account.name] = account
            }
        }

        // ----- 合併預算 -----
        // 用「年-月」當作唯一鍵（例如「2026-04」）
        val mergedBudgets = linkedMapOf<String, BudgetSetting>()
        (base.budgets + incoming.budgets).forEach { budget ->
            val key = "${budget.year}-${budget.month}"
            val existing = mergedBudgets[key]
            if (existing == null || budget.updatedAt >= existing.updatedAt) {
                mergedBudgets[key] = budget
            }
        }

        // ----- 合併分類 -----
        // 用「類型||名稱」當作唯一鍵（例如「支出||餐飲」）
        val mergedCategories = linkedMapOf<String, TransactionCategoryDefinition>()
        (base.categories + incoming.categories).forEach { category ->
            val key = "${category.type}||${category.name}"
            val existing = mergedCategories[key]
            if (existing == null || category.updatedAt >= existing.updatedAt) {
                mergedCategories[key] = category
            }
        }

        // 回傳合併後的快照（排序整理好）
        return normalizeSnapshot(
            CocoCoinSnapshot(
                transactions = mergedTransactions.values.sortedByDescending { it.updatedAt },
                accounts = mergedAccounts.values.sortedBy { it.id },
                budgets = mergedBudgets.values.sortedWith(
                    compareBy<BudgetSetting> { it.year }.thenBy { it.month }
                ),
                categories = mergedCategories.values.sortedWith(
                    compareBy<TransactionCategoryDefinition> { it.type }.thenBy { it.updatedAt }
                )
            )
        )
    }

    // 產生交易的唯一識別鍵（用來判斷兩筆交易是否相同）
    // 白話文：幫每筆交易算一個「指紋」，長得一樣的就是同一筆
    private fun buildTransactionKey(transaction: Transaction): String {
        return listOf(
            transaction.type,        // 支出/收入
            transaction.category,    // 分類名稱
            transaction.amount.toString(),  // 金額
            transaction.note,        // 備註
            transaction.time,        // 時間
            transaction.accountName  // 帳戶名稱
        ).joinToString("||")  // 用「||」當分隔符串起來，例如「支出||餐飲||80||雞排||2026/04/06 14:30||現金」
    }

    // ============================================================
    // 🔧 標準化快照 — 白話文：合併後整理一下，重新編號、去除重複
    // ============================================================
    // 因為不同裝置的 id 編號可能衝突（例如手機 A 的交易 id=1，手機 B 的交易 id=1）
    // 合併後要重新編號，確保 id 是連續且不重複的
    private fun normalizeSnapshot(snapshot: CocoCoinSnapshot): CocoCoinSnapshot {
        // 帳戶重新編號（從 1 開始）
        val normalizedAccounts = snapshot.accounts
            .distinctBy { it.name }  // 名稱相同的只留一個
            .mapIndexed { index, account ->
                account.copy(id = index + 1)  // 重新編號：1, 2, 3...
            }

        // 交易重新編號（從 1 開始）
        val normalizedTransactions = snapshot.transactions
            .mapIndexed { index, transaction ->
                transaction.copy(id = index + 1)  // 重新編號：1, 2, 3...
            }

        return CocoCoinSnapshot(
            transactions = normalizedTransactions,
            accounts = normalizedAccounts,
            budgets = snapshot.budgets,  // 預算沒有 id，不需要重新編號
            categories = snapshot.categories.distinctBy { "${it.type}||${it.name}" }  // 去除重複的分類
        )
    }
}