package com.example.cococoin

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// 雲端同步管理器：
// 負責把使用者的記帳資料（交易、帳戶、預算、分類）上傳到 Firebase 雲端，也負責從雲端下載資料回來。支援多裝置合併（誰最後修改就用誰的）
class FirebaseSyncManager(
    private val context: Context  // App 的環境資訊
) {
    // 記錄同步狀態的小筆記本
    private val syncStatusStore = SyncStatusStore(context)

    // 啟動同步（在 App 啟動或登入後呼叫）：
    // 比對本機資料和雲端資料，決定要上傳、下載、還是合併
    fun bootstrapSync(
        localSnapshot: CocoCoinSnapshot,                    // 本機的資料快照
        applyRemoteSnapshot: (CocoCoinSnapshot) -> Unit,   // 把雲端資料寫入本機的函式
        onComplete: () -> Unit                              // 完成後要執行的函式
    ) {
        // 檢查使用者是否已登入 Firebase（如果沒登入就無法同步）
        val collectionReference = getUserScopedCollectionReferenceOrNull() ?: run {
            syncStatusStore.markSyncFailure("尚未登入 Firebase，已改用本機資料")
            onComplete()
            return
        }

        // 從雲端讀取資料
        collectionReference.get()
            .addOnSuccessListener { snapshots ->  // 讀取成功
                runCatching {  // 如果有錯誤可以抓下來
                    // 把雲端的多份文件合併成一份資料快照 ：
                    // 同一個使用者的不同裝置會各自上傳備份，這裡把它們全部合併
                    val remoteSnapshot = snapshots.documents
                        .mapNotNull { it.toCocoCoinSnapshot() }  // 每個文件轉成快照，失敗的跳過
                        .mergeAll()  // 合併所有快照

                    // 根據情況決定要做什麼
                    when {
                        // 情況1：雲端沒有資料，但本機有資料 → 上傳本機資料到雲端
                        remoteSnapshot == null || remoteSnapshot.isEmpty() -> {
                            if (!localSnapshot.isEmpty()) {
                                pushSnapshot(localSnapshot)
                            }
                        }

                        // 情況2：本機沒有資料，但雲端有資料 → 下載雲端資料到本機
                        localSnapshot.isEmpty() -> applyRemoteSnapshot(remoteSnapshot)

                        // 情況3：兩邊都有資料 → 合併（保留最新的版本）
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

                    syncStatusStore.markSyncSuccess("雲端同步完成")
                }.onFailure { exception ->
                    // 處理過程中發生錯誤
                    syncStatusStore.markSyncFailure(
                        exception.message ?: "同步資料時發生錯誤，已改用本機資料"
                    )
                }
                onComplete()  // 不論成功失敗，最後都要告訴呼叫者「我完成了」
            }
            .addOnFailureListener { exception ->  // 讀取失敗
                syncStatusStore.markSyncFailure(
                    exception.message ?: "讀取雲端備份失敗，已改用本機資料"
                )
                onComplete()
            }
    }

    // 上傳資料到雲端（非同步，不等待結果）：
    fun pushSnapshot(snapshot: CocoCoinSnapshot) {
        val documentReference = getUserScopedDocumentReferenceOrNull() ?: return

        documentReference.set(snapshot.toMap())  // 把資料轉成 Map 格式上傳
            .addOnSuccessListener {
                syncStatusStore.markSyncSuccess("雲端備份已更新")
            }
            .addOnFailureListener { exception ->
                syncStatusStore.markSyncFailure(
                    exception.message ?: "雲端備份更新失敗"
                )
            }
    }

    // 從雲端下載資料（掛起函式，會等待結果）：
    // 會「暫停」等待 Firebase 回傳結果
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

    // 上傳資料到雲端（等待結果）：
    // 等到上傳完成才繼續，並且回傳成功/失敗的結果
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

    // 取得 Firestore
    private fun getFirestoreOrNull(): FirebaseFirestore? {
        // 檢查 Firebase 是否可用（google-services.json 有沒有正確設定）
        if (!FirebaseInitializer.canUseFirebase(context)) {
            return null
        }

        return runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

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
    // 每支手機都有一個獨一無二的 ID，用來區分「這份備份是從哪支手機來的」
    private fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        // 如果拿不到 Android ID（極少數情況），就用 "local-device" 代替
        return androidId?.takeIf { it.isNotBlank() } ?: "local-device"
    }

    // 把資料快照轉成 Map（Firestore 只能存 Map）
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

    // 把 Firestore 的文件轉成資料快照
    private fun com.google.firebase.firestore.DocumentSnapshot.toCocoCoinSnapshot(): CocoCoinSnapshot? {
        if (!exists()) return null  // 文件不存在

        // 取得整份文件的更新時間（備份用）
        val rootUpdatedAt = (get("updatedAt") as? Number)?.toLong() ?: 0L

        // 解析交易資料
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

    // 合併多份快照（多裝置備份時用）：
    // 把同一個使用者的多個裝置備份合併成一份
    private fun List<CocoCoinSnapshot>.mergeAll(): CocoCoinSnapshot? {
        if (isEmpty()) return null
        // reduce：把清單中的第一項和第二項合併，結果再和第三項合併...以此類推
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

        // 合併交易：用 type+category+amount+note+time+accountName 當作唯一鍵：
        // 如果兩邊有「看起來一模一樣」的交易，就保留更新時間比較新那筆
        val mergedTransactions = linkedMapOf<String, Transaction>()
        (base.transactions + incoming.transactions).forEach { transaction ->
            val key = buildTransactionKey(transaction)  // 產生這筆交易的「指紋」
            val existing = mergedTransactions[key]
            // 如果沒有這筆交易，或者新的比舊的更新，就覆蓋
            if (existing == null || transaction.updatedAt >= existing.updatedAt) {
                mergedTransactions[key] = transaction
            }
        }

        // 合併帳戶：用名稱當作唯一鍵：
        // 如果兩邊有相同名稱的帳戶（例如「現金」），保留更新時間比較新的
        val mergedAccounts = linkedMapOf<String, AssetAccount>()
        (base.accounts + incoming.accounts).forEach { account ->
            val existing = mergedAccounts[account.name]
            if (existing == null || account.updatedAt >= existing.updatedAt) {
                mergedAccounts[account.name] = account
            }
        }

        // 合併預算：用「年-月」當作唯一鍵
        val mergedBudgets = linkedMapOf<String, BudgetSetting>()
        (base.budgets + incoming.budgets).forEach { budget ->
            val key = "${budget.year}-${budget.month}"
            val existing = mergedBudgets[key]
            if (existing == null || budget.updatedAt >= existing.updatedAt) {
                mergedBudgets[key] = budget
            }
        }

        // 合併分類：用「類型||名稱」當作唯一鍵
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
    private fun buildTransactionKey(transaction: Transaction): String {
        return listOf(
            transaction.type,        // 支出/收入
            transaction.category,    // 分類名稱
            transaction.amount.toString(),  // 金額
            transaction.note,        // 備註
            transaction.time,        // 時間
            transaction.accountName  // 帳戶名稱
        ).joinToString("||")  // 用「||」當分隔符串起來
    }

    // 標準化快照（重新編號 id，去除重複）：
    // 因為不同裝置的 id 編號可能衝突，合併後要重新編號，確保 id 是連續且不重複的
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