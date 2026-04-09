package com.example.cococoin

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// 資料倉庫
class CocoCoinRepository private constructor(
    context: Context
) {
    // 使用 Application Context（避免記憶體洩漏）
    private val appContext = context.applicationContext

    // 資料庫輔助類（操作 Room 資料庫）
    private val databaseHelper = CocoCoinDatabaseHelper(appContext)

    // 舊版 SharedPreferences（用於讀取舊版資料和帳本名稱）
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    // 分類儲存器（管理支出/收入分類）
    private val categoryStore = TransactionCategoryStore(appContext)

    // Firebase 同步管理器（雲端備份）
    private val firebaseSyncManager = FirebaseSyncManager(appContext)

    // 自動本機備份管理器（備份到手機資料夾）
    private val autoLocalBackupManager = AutoLocalBackupManager(appContext)

    // 同步狀態儲存器（記錄雲端同步狀況）
    private val syncStatusStore = SyncStatusStore(appContext)

    // 協程範圍（用於背景任務，SupervisorJob 確保一個失敗不影響其他）
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 主執行緒的 Handler（用於切回主執行緒執行 callback）
    private val mainHandler = Handler(Looper.getMainLooper())

    // 初始化狀態（確保只初始化一次）
    @Volatile
    private var initializationCompleted = false  // 是否已完成初始化

    @Volatile
    private var initializationRunning = false    // 是否正在初始化中

    // 等待初始化的回呼函式清單
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    // 確保資料庫已初始化（在 App 啟動時呼叫）：
    // 確保 Firebase 登入、資料遷移、雲端同步都完成後才執行 callback
    fun ensureInitialized(onComplete: (() -> Unit)? = null) {
        synchronized(this) {
            // 如果已經初始化完成，直接執行 callback
            if (initializationCompleted) {
                onComplete?.let { mainHandler.post(it) }
                return
            }

            // 還沒完成，把 callback 加入等待清單
            onComplete?.let(pendingCallbacks::add)

            // 如果正在初始化中，就不重複執行
            if (initializationRunning) {
                return
            }

            initializationRunning = true
        }

        // 確保 Firebase 已登入（沒有的話會自動匿名登入）
        FirebaseAuthManager.ensureSignedIn(appContext) {
            repositoryScope.launch {
                // 1.遷移舊版資料（如果有的話）
                migrateLegacyDataIfNeeded()

                // 2.打包本機資料快照
                val localSnapshot = buildSnapshot()

                // 3.啟動雲端同步（比對本機和雲端，決定上傳、下載或合併）
                firebaseSyncManager.bootstrapSync(
                    localSnapshot = localSnapshot,
                    applyRemoteSnapshot = { remoteSnapshot ->
                        applySnapshot(remoteSnapshot)  // 把雲端資料寫入本機
                    },
                    onComplete = {
                        // 4.同步完成，執行所有等待中的 callback
                        val callbacks = synchronized(this@CocoCoinRepository) {
                            initializationCompleted = true
                            initializationRunning = false
                            pendingCallbacks.toList().also { pendingCallbacks.clear() }
                        }

                        callbacks.forEach { callback ->
                            mainHandler.post(callback)
                        }
                    }
                )
            }
        }
    }

    // ==================== 讀取資料 ====================

    // 取得所有交易紀錄
    suspend fun getTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        databaseHelper.getAllTransactions()
    }

    // 取得所有帳戶
    suspend fun getAccounts(): List<AssetAccount> = withContext(Dispatchers.IO) {
        databaseHelper.getAllAccounts()
    }

    // 取得某年某月的預算金額
    suspend fun getMonthlyBudget(year: Int, month: Int): Int = withContext(Dispatchers.IO) {
        databaseHelper.getBudget(year, month)
    }

    // 取得交易總筆數
    suspend fun getTransactionCount(): Int = withContext(Dispatchers.IO) {
        databaseHelper.getTransactionCount()
    }

    // 取得所有分類（支出+收入）
    suspend fun getAllCategories(): List<TransactionCategoryDefinition> = withContext(Dispatchers.IO) {
        categoryStore.getCategories()
    }

    // 取得指定類型的分類（只拿支出或只拿收入）
    suspend fun getCategories(type: String): List<TransactionCategoryDefinition> = withContext(Dispatchers.IO) {
        categoryStore.getCategories().filter { it.type == type }
    }

    // 取得帳本名稱
    suspend fun getBookName(): String = withContext(Dispatchers.IO) {
        legacyPreferences.getString(KEY_BOOK_NAME, DEFAULT_BOOK_NAME).orEmpty()
            .ifBlank { DEFAULT_BOOK_NAME }
    }

    // 取得雲端同步狀態
    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        syncStatusStore.getStatus()
    }

    // 取得自動本機備份狀態
    suspend fun getAutoLocalBackupStatus(): AutoLocalBackupStatus = withContext(Dispatchers.IO) {
        autoLocalBackupManager.getStatus()
    }

    // ==================== 寫入資料 ====================

    // 更新帳本名稱
    suspend fun updateBookName(name: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext OperationResult.fail("帳本名稱不能空白")
        }

        legacyPreferences.edit().putString(KEY_BOOK_NAME, trimmedName).apply()
        OperationResult.ok("帳本名稱已更新")
    }

    // 手動備份到雲端
    suspend fun backupNow(): OperationResult = withContext(Dispatchers.IO) {
        firebaseSyncManager.pushSnapshotAwait(buildSnapshot())
    }

    // 匯出備份檔（JSON 格式）
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        CocoCoinBackupCodec.encode(buildSnapshot())
    }

    // 匯出 CSV 報表（可用 Excel 打開）
    suspend fun exportTransactionsCsv(): String = withContext(Dispatchers.IO) {
        CocoCoinCsvExporter.exportTransactionsCsv(buildSnapshot())
    }

    // 匯入備份檔（從 JSON 恢復資料）
    suspend fun importBackupJson(json: String): OperationResult = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val snapshot = CocoCoinBackupCodec.decode(json)
            applySnapshot(snapshot)
            syncRemoteAsync()  // 同步到雲端
            OperationResult.ok("匯入成功，已套用備份資料")
        }.getOrElse { exception ->
            OperationResult.fail(exception.message ?: "匯入失敗，備份檔格式不正確")
        }
    }

    // 設定自動本機備份的資料夾
    suspend fun configureAutoLocalBackupFolder(uri: Uri): OperationResult = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            autoLocalBackupManager.configureFolder(uri)
            autoLocalBackupManager.backupSnapshot(buildSnapshot())
            OperationResult.ok("已啟用自動本機備份")
        }.getOrElse { exception ->
            OperationResult.fail(exception.message ?: "設定自動本機備份失敗")
        }
    }

    // 停用自動本機備份
    suspend fun disableAutoLocalBackup(): OperationResult = withContext(Dispatchers.IO) {
        autoLocalBackupManager.clearConfiguration()
        OperationResult.ok("已停用自動本機備份")
    }

    // 登入後從雲端還原資料
    suspend fun restoreFromCloudAfterLogin(): OperationResult = withContext(Dispatchers.IO) {
        val remoteSnapshot = firebaseSyncManager.fetchRemoteSnapshot()
        return@withContext when {
            remoteSnapshot == null -> {
                OperationResult.fail("登入成功，但找不到雲端備份或讀取失敗")
            }
            remoteSnapshot.isEmpty() -> {
                syncStatusStore.markSyncFailure("此帳號目前沒有雲端備份")
                OperationResult.ok("登入成功，但此帳號目前沒有雲端備份")
            }
            else -> {
                applySnapshot(remoteSnapshot)
                syncStatusStore.markSyncSuccess("已從雲端還原資料")
                OperationResult.ok("已從雲端還原資料")
            }
        }
    }

    // 登出並開始匿名 session（清除本機資料）
    suspend fun signOutAndStartAnonymousSession(): OperationResult = withContext(Dispatchers.IO) {
        // 清除所有本機資料
        databaseHelper.clearAllData()
        clearLegacyPreferences()
        FirebaseAuthManager.signOut(appContext)
        categoryStore.clearCustomizations()
        resetInitializationState()

        // 重新初始化（會自動匿名登入）
        suspendCancellableCoroutine { continuation ->
            ensureInitialized {
                continuation.resume(OperationResult.ok("已登出，目前回到匿名模式"))
            }
        }
    }

    // 儲存每月預算
    suspend fun saveMonthlyBudget(year: Int, month: Int, amount: Int) = withContext(Dispatchers.IO) {
        databaseHelper.runInTransaction { database ->
            databaseHelper.upsertBudget(
                database,
                BudgetSetting(year = year, month = month, amount = amount)
            )
        }
        syncRemoteAsync()  // 同步到雲端
    }

    // 新增帳戶
    suspend fun addAccount(name: String, balance: Int): OperationResult = withContext(Dispatchers.IO) {
        // 檢查是否已有同名帳戶
        if (databaseHelper.getAccountByName(name) != null) {
            return@withContext OperationResult.fail("已存在相同名稱的帳戶")
        }

        databaseHelper.runInTransaction { database ->
            databaseHelper.insertAccount(
                database,
                AssetAccount(
                    id = 0,
                    name = name,
                    balance = balance
                )
            )
        }

        syncRemoteAsync()
        OperationResult.ok("帳戶已新增")
    }

    // 更新帳戶（可改名、改餘額）
    suspend fun updateAccount(accountId: Int, newName: String, newBalance: Int): OperationResult =
        withContext(Dispatchers.IO) {
            // 找到要修改的帳戶
            val currentAccount = databaseHelper.getAllAccounts().firstOrNull { it.id == accountId }
                ?: return@withContext OperationResult.fail("找不到帳戶")

            // 檢查新名稱是否與其他帳戶重複
            val duplicated = databaseHelper.getAllAccounts().any {
                it.id != accountId && it.name == newName
            }
            if (duplicated) {
                return@withContext OperationResult.fail("已存在相同名稱的帳戶")
            }

            databaseHelper.runInTransaction { database ->
                // 更新帳戶資料
                databaseHelper.updateAccount(
                    database,
                    currentAccount.copy(
                        name = newName,
                        balance = newBalance,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // 如果名稱有變，同步更新所有交易中的帳戶名稱
                if (currentAccount.name != newName) {
                    databaseHelper.renameTransactionsAccount(
                        database,
                        currentAccount.name,
                        newName
                    )
                }
            }

            syncRemoteAsync()
            OperationResult.ok("帳戶已更新")
        }

    // 刪除帳戶（只能刪除沒有交易記錄的帳戶）
    suspend fun deleteAccount(accountId: Int): OperationResult = withContext(Dispatchers.IO) {
        val account = databaseHelper.getAllAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext OperationResult.fail("找不到帳戶")

        // 檢查是否有交易使用這個帳戶
        if (databaseHelper.isAccountUsed(account.name)) {
            return@withContext OperationResult.fail("此帳戶已有交易紀錄使用，請先修改或刪除相關交易。")
        }

        databaseHelper.runInTransaction { database ->
            databaseHelper.deleteAccount(database, accountId)
        }

        syncRemoteAsync()
        OperationResult.ok("帳戶已刪除")
    }

    // 新增一筆交易
    suspend fun addTransaction(
        type: String,
        category: String,
        amount: Int,
        note: String,
        time: String,
        accountName: String
    ): OperationResult = withContext(Dispatchers.IO) {
        val account = databaseHelper.getAccountByName(accountName)
            ?: return@withContext OperationResult.fail("找不到所選帳戶")

        // 檢查餘額是否足夠（支出才需要檢查）
        if (type == "支出" && account.balance < amount) {
            return@withContext OperationResult.fail("$accountName 餘額不足，目前只剩 NT$ ${account.balance}")
        }

        databaseHelper.runInTransaction { database ->
            // 新增交易
            databaseHelper.insertTransaction(
                database,
                Transaction(
                    id = 0,
                    type = type,
                    category = category,
                    amount = amount,
                    note = note,
                    time = time,
                    accountName = accountName
                )
            )

            // 調整帳戶餘額（支出扣錢，收入加錢）
            val delta = if (type == "支出") -amount else amount
            databaseHelper.adjustAccountBalance(database, accountName, delta)
        }

        syncRemoteAsync()
        OperationResult.ok("儲存成功")
    }

    // 新增或更新分類
    suspend fun upsertCategory(
        type: String,
        originalName: String?,
        newName: String,
        icon: String
    ): OperationResult = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) {
            return@withContext OperationResult.fail("分類名稱不能空白")
        }

        val normalizedIcon = icon.ifBlank { TransactionCategoryCatalog.fallbackIcon(type) }
        val categories = categoryStore.getCategories().toMutableList()

        // 檢查是否已有同名分類（編輯時要排除自己）
        val duplicate = categories.any {
            it.type == type &&
                    it.name == trimmedName &&
                    it.name != originalName
        }
        if (duplicate) {
            return@withContext OperationResult.fail("已存在相同名稱的分類")
        }

        if (originalName == null) {
            // 新增分類
            categories += TransactionCategoryDefinition(
                type = type,
                name = trimmedName,
                icon = normalizedIcon
            )
        } else {
            // 編輯分類
            val currentIndex = categories.indexOfFirst { it.type == type && it.name == originalName }
            if (currentIndex == -1) {
                return@withContext OperationResult.fail("找不到要編輯的分類")
            }

            val currentCategory = categories[currentIndex]
            categories[currentIndex] = currentCategory.copy(
                name = trimmedName,
                icon = normalizedIcon,
                updatedAt = System.currentTimeMillis()
            )

            // 如果名稱有變，同步更新所有交易中的分類名稱
            if (originalName != trimmedName) {
                databaseHelper.runInTransaction { database ->
                    databaseHelper.renameTransactionsCategory(
                        database = database,
                        type = type,
                        oldName = originalName,
                        newName = trimmedName
                    )
                }
            }
        }

        categoryStore.replaceCategories(categories)
        syncRemoteAsync()
        OperationResult.ok(if (originalName == null) "分類已新增" else "分類已更新")
    }

    // 刪除分類（每種類型至少保留一個）
    suspend fun deleteCategory(type: String, name: String): OperationResult = withContext(Dispatchers.IO) {
        val categories = categoryStore.getCategories().toMutableList()
        val sameTypeCategories = categories.filter { it.type == type }

        // 至少保留一個分類
        if (sameTypeCategories.size <= 1) {
            return@withContext OperationResult.fail("至少要保留一個${type}分類")
        }

        // 檢查是否有交易使用這個分類
        if (databaseHelper.getAllTransactions().any { it.type == type && it.category == name }) {
            return@withContext OperationResult.fail("已有交易紀錄使用此分類，請先編輯相關交易")
        }

        val removed = categories.removeAll { it.type == type && it.name == name }
        if (!removed) {
            return@withContext OperationResult.fail("找不到要刪除的分類")
        }

        categoryStore.replaceCategories(categories)
        syncRemoteAsync()
        OperationResult.ok("分類已刪除")
    }

    // 更新一筆交易（編輯）
    suspend fun updateTransaction(updatedTransaction: Transaction): OperationResult =
        withContext(Dispatchers.IO) {
            // 找到原本的交易
            val oldTransaction = databaseHelper.getTransactionById(updatedTransaction.id)
                ?: return@withContext OperationResult.fail("找不到原始交易")

            // 取得所有帳戶的餘額
            val accountMap = databaseHelper.getAllAccounts().associateBy { it.name }.toMutableMap()
            val newAccount = accountMap[updatedTransaction.accountName]
                ?: return@withContext OperationResult.fail("找不到所選帳戶")

            // 模擬餘額變動（先復原舊的，再套用新的）
            val simulatedBalances = accountMap.mapValues { it.value.balance }.toMutableMap()
            applyTransactionDelta(simulatedBalances, oldTransaction, reverse = true)   // 復原舊交易
            applyTransactionDelta(simulatedBalances, updatedTransaction, reverse = false) // 套用新交易

            // 檢查新餘額是否為負數
            val newBalance = simulatedBalances[newAccount.name] ?: 0
            if (newBalance < 0) {
                return@withContext OperationResult.fail(
                    "${newAccount.name} 餘額不足，可用 NT$ ${newAccount.balance}"
                )
            }

            // 正式更新資料庫
            databaseHelper.runInTransaction { database ->
                applyDatabaseTransactionDelta(database, oldTransaction, reverse = true)   // 復原舊交易
                applyDatabaseTransactionDelta(database, updatedTransaction, reverse = false) // 套用新交易
                databaseHelper.updateTransaction(
                    database,
                    updatedTransaction.copy(updatedAt = System.currentTimeMillis())
                )
            }

            syncRemoteAsync()
            OperationResult.ok("交易已更新")
        }

    // 刪除一筆交易
    suspend fun deleteTransaction(transactionId: Int): OperationResult = withContext(Dispatchers.IO) {
        val transaction = databaseHelper.getTransactionById(transactionId)
            ?: return@withContext OperationResult.fail("找不到要刪除的交易")

        // 復原該筆交易對帳戶餘額的影響（支出加回來，收入扣回去）
        databaseHelper.runInTransaction { database ->
            applyDatabaseTransactionDelta(database, transaction, reverse = true)
            databaseHelper.deleteTransaction(database, transactionId)
        }

        syncRemoteAsync()
        OperationResult.ok("交易已刪除")
    }

    // 復原被刪除的交易（從 Snackbar 的「復原」按鈕呼叫）
    suspend fun restoreDeletedTransaction(transaction: Transaction): OperationResult =
        withContext(Dispatchers.IO) {
            val account = databaseHelper.getAccountByName(transaction.accountName)
                ?: return@withContext OperationResult.fail("原帳戶已不存在，無法復原這筆交易")

            // 檢查餘額是否足夠（支出才需要檢查）
            if (transaction.type == "支出" && account.balance < transaction.amount) {
                return@withContext OperationResult.fail(
                    "${account.name} 餘額不足，無法復原這筆支出"
                )
            }

            databaseHelper.runInTransaction { database ->
                // 重新插入交易（id 設為 0 讓資料庫自動產生新編號）
                databaseHelper.insertTransaction(
                    database,
                    transaction.copy(
                        id = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // 調整帳戶餘額（支出扣錢，收入加錢）
                val delta = if (transaction.type == "支出") -transaction.amount else transaction.amount
                databaseHelper.adjustAccountBalance(database, transaction.accountName, delta)
            }

            syncRemoteAsync()
            OperationResult.ok("已復原刪除的交易")
        }

    // 清除所有資料（危險操作）
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        databaseHelper.clearAllData()
        clearLegacyPreferences()
        categoryStore.clearCustomizations()
        syncRemoteAsync()
    }

    // ==================== 內部輔助函式 ====================

    // 打包目前所有資料成快照
    private fun buildSnapshot(): CocoCoinSnapshot {
        return databaseHelper.getSnapshot().copy(
            categories = categoryStore.getCategories()
        )
    }

    // 套用快照到本機（完全取代）
    private fun applySnapshot(snapshot: CocoCoinSnapshot) {
        databaseHelper.replaceAllData(snapshot)
        categoryStore.replaceCategories(snapshot.categories)
    }

    // 模擬交易對餘額的影響（用於檢查餘額是否足夠）
    private fun applyTransactionDelta(
        balances: MutableMap<String, Int>,
        transaction: Transaction,
        reverse: Boolean
    ) {
        val current = balances[transaction.accountName] ?: return
        val delta = when {
            transaction.type == "支出" && reverse -> transaction.amount    // 復原支出：加回來
            transaction.type == "支出" -> -transaction.amount              // 新增支出：扣掉
            reverse -> -transaction.amount                                 // 復原收入：扣回去
            else -> transaction.amount                                     // 新增收入：加上
        }
        balances[transaction.accountName] = current + delta
    }

    // 實際套用交易對資料庫餘額的影響
    private fun applyDatabaseTransactionDelta(
        database: CocoCoinRoomDatabase,
        transaction: Transaction,
        reverse: Boolean
    ) {
        val delta = when {
            transaction.type == "支出" && reverse -> transaction.amount
            transaction.type == "支出" -> -transaction.amount
            reverse -> -transaction.amount
            else -> transaction.amount
        }
        databaseHelper.adjustAccountBalance(database, transaction.accountName, delta)
    }

    // 非同步同步到雲端和自動備份（不等待結果）
    private fun syncRemoteAsync() {
        repositoryScope.launch {
            val snapshot = buildSnapshot()
            firebaseSyncManager.pushSnapshot(snapshot)      // 上傳到雲端
            autoLocalBackupManager.backupSnapshot(snapshot) // 自動本機備份
        }
    }

    // 重設初始化狀態（登出時使用）
    private fun resetInitializationState() {
        synchronized(this) {
            initializationCompleted = false
            initializationRunning = false
            pendingCallbacks.clear()
        }
    }

    // ==================== 舊版資料遷移 ====================

    // 遷移舊版資料（如果是第一次安裝新版本）
    private fun migrateLegacyDataIfNeeded() {
        // 已經遷移過了
        if (legacyPreferences.getBoolean(KEY_LEGACY_IMPORTED, false)) {
            return
        }

        // 如果資料庫已經有資料，就不需要遷移
        if (!databaseHelper.getSnapshot().isEmpty()) {
            legacyPreferences.edit().putBoolean(KEY_LEGACY_IMPORTED, true).apply()
            return
        }

        val currentTime = System.currentTimeMillis()

        // 從舊版 SharedPreferences 讀取交易、帳戶、預算
        val legacyTransactions = parseLegacyTransactions(
            legacyPreferences.getString("transaction_list", "").orEmpty(),
            currentTime
        )
        val legacyAccounts = parseLegacyAccounts(
            legacyPreferences.getString("asset_account_list", "").orEmpty(),
            currentTime
        )
        val legacyBudgets = parseLegacyBudgets(currentTime)

        // 如果有舊版資料，就寫入新資料庫
        if (legacyTransactions.isNotEmpty() || legacyAccounts.isNotEmpty() || legacyBudgets.isNotEmpty()) {
            databaseHelper.replaceAllData(
                CocoCoinSnapshot(
                    transactions = legacyTransactions,
                    accounts = legacyAccounts,
                    budgets = legacyBudgets
                )
            )
        }

        // 標記已遷移
        legacyPreferences.edit().putBoolean(KEY_LEGACY_IMPORTED, true).apply()
    }

    // 解析舊版交易資料（格式：type||category||amount||note||time||accountName##...）
    private fun parseLegacyTransactions(savedData: String, baseUpdatedAt: Long): List<Transaction> {
        if (savedData.isBlank()) return emptyList()

        return savedData.split("##").mapIndexedNotNull { index, item ->
            val parts = item.split("||")
            if (parts.size < 5) {
                return@mapIndexedNotNull null
            }

            Transaction(
                id = index + 1,
                type = parts[0],
                category = parts[1],
                amount = parts[2].toIntOrNull() ?: 0,
                note = parts[3],
                time = parts[4],
                accountName = parts.getOrNull(5) ?: "未指定帳戶",
                updatedAt = baseUpdatedAt + index
            )
        }
    }

    // 解析舊版帳戶資料（格式：name||balance##...）
    private fun parseLegacyAccounts(savedData: String, baseUpdatedAt: Long): List<AssetAccount> {
        if (savedData.isBlank()) return emptyList()

        return savedData.split("##").mapIndexedNotNull { index, item ->
            val parts = item.split("||")
            if (parts.size != 2) {
                return@mapIndexedNotNull null
            }

            AssetAccount(
                id = index + 1,
                name = parts[0],
                balance = parts[1].toIntOrNull() ?: 0,
                updatedAt = baseUpdatedAt + index
            )
        }
    }

    // 解析舊版預算資料（格式：monthly_budget_2026_4 = 20000）
    private fun parseLegacyBudgets(baseUpdatedAt: Long): List<BudgetSetting> {
        return legacyPreferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith("monthly_budget_")) {
                return@mapNotNull null
            }

            val segments = key.split("_")
            if (segments.size != 4) {
                return@mapNotNull null
            }

            val year = segments[2].toIntOrNull() ?: return@mapNotNull null
            val month = segments[3].toIntOrNull() ?: return@mapNotNull null
            val amount = value as? Int ?: return@mapNotNull null

            BudgetSetting(
                year = year,
                month = month,
                amount = amount,
                updatedAt = baseUpdatedAt
            )
        }
    }

    // 清除舊版偏好設定
    private fun clearLegacyPreferences() {
        val editor = legacyPreferences.edit()
        legacyPreferences.all.keys.forEach { key ->
            if (
                key == "transaction_list" ||
                key == "asset_account_list" ||
                key.startsWith("monthly_budget_")
            ) {
                editor.remove(key)
            }
        }
        editor.putBoolean(KEY_LEGACY_IMPORTED, true).apply()
    }
    companion object {
        private const val LEGACY_PREFS_NAME = "cococoin_prefs"          // 舊版偏好設定名稱
        private const val KEY_LEGACY_IMPORTED = "legacy_data_imported_v1"  // 是否已遷移舊資料
        private const val KEY_BOOK_NAME = "book_name"                  // 帳本名稱的儲存鍵
        private const val DEFAULT_BOOK_NAME = "CocoCoin"               // 預設帳本名稱

        @Volatile
        private var instance: CocoCoinRepository? = null

        // 取得物件
        fun getInstance(context: Context): CocoCoinRepository {
            return instance ?: synchronized(this) {
                instance ?: CocoCoinRepository(context).also { instance = it }
            }
        }
    }
}