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

/**
 * 🏦【資料倉庫】- CocoCoin 的超級金庫管理員
 *
 * 想像你開了一間銀行（CocoCoin App），裡面有：
 * - 金庫（資料庫）：存放所有客戶的錢和交易紀錄
 * - 保險箱（SharedPreferences）：存放一些小秘密（帳本名稱）
 * - 分類架（CategoryStore）：標籤系統（餐飲、交通...）
 * - 雲端分行（Firebase）：遠端備份，手機掉了也不怕
 * - 自動備份機（AutoLocalBackup）：定時把資料存到手機資料夾
 *
 * 這個 CocoCoinRepository 就是「超級金庫管理員」！
 * 他的工作：
 * 1️⃣ 所有人要讀資料、寫資料都要透過他（統一窗口）
 * 2️⃣ 確保資料庫準備好才能使用（ensureInitialized）
 * 3️⃣ 自動管理雲端同步（上傳/下載/合併）
 * 4️⃣ 處理舊版資料的遷移（搬家）
 *
 * 重點：他是「單例模式」（Singleton），全 App 只有一個管理員！
 * 為什麼？因為如果有兩個管理員同時開金庫，會天下大亂啊～
 */
class CocoCoinRepository private constructor(
    context: Context
) {
    // ---------- 🏗️ 重要資產（管理員的工具箱）----------

    /**
     * 📱 應用程式上下文（App 的身份證）
     * 使用 applicationContext 而不是 activity context
     * 為什麼？因為 Repository 的生命週期比 Activity 長，
     * 如果用 Activity 的 context，Activity 關閉後 context 還在，
     * 就會造成「記憶體洩漏」（像冰箱門沒關好，一直耗電）
     */
    private val appContext = context.applicationContext

    /**
     * 🗄️ 資料庫輔助類（金庫的鑰匙）
     * 負責操作 Room 資料庫（增删改查）
     */
    private val databaseHelper = CocoCoinDatabaseHelper(appContext)

    /**
     * 📝 舊版 SharedPreferences（老銀行的帳本）
     * 用於讀取舊版資料和帳本名稱
     * SharedPreferences 就像一個小筆記本，存一些簡單的設定
     */
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 🏷️ 分類儲存器（標籤管理員）
     * 管理支出/收入分類（餐飲、交通、薪水...）
     */
    private val categoryStore = TransactionCategoryStore(appContext)

    /**
     * ☁️ Firebase 同步管理器（雲端分行經理）
     * 負責雲端備份、同步、上傳、下載
     */
    private val firebaseSyncManager = FirebaseSyncManager(appContext)

    /**
     * 💾 自動本機備份管理器（定時備份員）
     * 自動備份到手機資料夾，就算沒有網路也能還原
     */
    private val autoLocalBackupManager = AutoLocalBackupManager(appContext)

    /**
     * 📡 同步狀態儲存器（記錄雲端同步狀況）
     * 記錄最後一次同步時間、成功/失敗狀態
     */
    private val syncStatusStore = SyncStatusStore(appContext)

    /**
     * 🏃 協程範圍（管理員的跑腿小弟們）
     * SupervisorJob：一個小弟失敗不會影響其他小弟（不會連坐法）
     * Dispatchers.IO：專門處理資料庫和網路的跑道
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 📢 主執行緒的 Handler（用來切回主執行緒執行 callback）
     * 白話：小弟在背景做完事後，要回到老闆（UI）面前報告時用的
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------- 🚦 初始化狀態（確保金庫只開一次）----------

    @Volatile
    private var initializationCompleted = false  // ✅ 是否已完成初始化

    @Volatile
    private var initializationRunning = false    /// 🏃 是否正在初始化中（避免重複執行）

    /**
     * 📋 等待初始化的回呼函式清單
     *
     * 情境：App 一打開，很多地方同時呼叫「我要讀資料！」
     * 但金庫還沒準備好（還在遷移資料、同步雲端）
     *
     * 解決方法：把這些請求先記在清單裡，
     * 等金庫準備好了，再一個一個處理（像餐廳的候位名單）
     */
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    // ---------- 🎬 確保資料庫已初始化（金庫開門流程）----------

    /**
     * 確保資料庫已初始化（在 App 啟動時呼叫）
     *
     * 這個方法會做一系列事情：
     * 1. 確保 Firebase 登入（就像先刷員工證）
     * 2. 遷移舊版資料（把舊銀行的錢搬到新銀行）
     * 3. 打包本機資料快照（清點庫存）
     * 4. 啟動雲端同步（比對本機和雲端，決定要上傳還是下載）
     * 5. 執行所有等待中的 callback（通知大家：金庫開了！）
     *
     * @param onComplete 初始化完成後要執行的回呼函式（可選）
     *
     * 白話：打開銀行大門，確保所有系統都 ready，
     *       然後對外面喊：「可以進來辦事了！」
     */
    fun ensureInitialized(onComplete: (() -> Unit)? = null) {
        synchronized(this) {  // 🔒 一次只讓一個人進來（避免多執行緒混亂）
            // 🚪 情況 1：已經初始化完成 → 直接執行 callback
            if (initializationCompleted) {
                onComplete?.let { mainHandler.post(it) }
                return
            }

            // 📝 還沒完成，把 callback 加入候位清單
            onComplete?.let(pendingCallbacks::add)

            // 🏃 情況 2：正在初始化中 → 不要再重複執行（避免浪費資源）
            if (initializationRunning) {
                return
            }

            // 🚀 情況 3：還沒開始初始化 → 啟動初始化流程
            initializationRunning = true
        }

        // 🔐 步驟 1：確保 Firebase 已登入
        // 如果沒有登入，會自動匿名登入（就像銀行給臨時訪客證）
        FirebaseAuthManager.ensureSignedIn(appContext) {
            repositoryScope.launch {
                // 📦 步驟 2：遷移舊版資料（如果有的話）
                // 白話：把老銀行的錢搬到新銀行
                migrateLegacyDataIfNeeded()

                // 📸 步驟 3：打包本機資料快照（清點庫存）
                val localSnapshot = buildSnapshot()

                // ☁️ 步驟 4：啟動雲端同步
                // 比對本機和雲端，決定上傳、下載或合併
                firebaseSyncManager.bootstrapSync(
                    localSnapshot = localSnapshot,
                    applyRemoteSnapshot = { remoteSnapshot ->
                        applySnapshot(remoteSnapshot)  // 把雲端資料寫入本機
                    },
                    onComplete = {
                        // 🎉 步驟 5：同步完成！執行所有等待中的 callback
                        val callbacks = synchronized(this@CocoCoinRepository) {
                            initializationCompleted = true
                            initializationRunning = false
                            pendingCallbacks.toList().also { pendingCallbacks.clear() }
                        }

                        callbacks.forEach { callback ->
                            mainHandler.post(callback)  // 在主執行緒執行（更新 UI）
                        }
                    }
                )
            }
        }
    }

    // ==================== 📖 讀取資料（查詢功能）====================
    // 以下都是「唯讀」操作，不會修改資料

    /**
     * 📒 取得所有交易紀錄
     * @return 交易列表（午餐、晚餐、購物...）
     */
    suspend fun getTransactions(): List<Transaction> = withContext(Dispatchers.IO) {
        databaseHelper.getAllTransactions()
    }

    /**
     * 💰 取得所有帳戶
     * @return 帳戶列表（現金、信用卡、Line Pay...）
     */
    suspend fun getAccounts(): List<AssetAccount> = withContext(Dispatchers.IO) {
        databaseHelper.getAllAccounts()
    }

    /**
     * 📊 取得某年某月的預算金額
     * @param year 年份（2026）
     * @param month 月份（4）
     * @return 預算金額（例如 15000）
     */
    suspend fun getMonthlyBudget(year: Int, month: Int): Int = withContext(Dispatchers.IO) {
        databaseHelper.getBudget(year, month)
    }

    /**
     * 🔢 取得交易總筆數
     * @return 總共有幾筆記帳（例如 128 筆）
     */
    suspend fun getTransactionCount(): Int = withContext(Dispatchers.IO) {
        databaseHelper.getTransactionCount()
    }

    /**
     * 🏷️ 取得所有分類（支出 + 收入）
     * @return 分類列表（餐飲、交通、薪水、紅包...）
     */
    suspend fun getAllCategories(): List<TransactionCategoryDefinition> = withContext(Dispatchers.IO) {
        categoryStore.getCategories()
    }

    /**
     * 🏷️ 取得指定類型的分類（只拿支出或只拿收入）
     * @param type 「支出」或「收入」
     * @return 該類型的分類列表
     */
    suspend fun getCategories(type: String): List<TransactionCategoryDefinition> = withContext(Dispatchers.IO) {
        categoryStore.getCategories().filter { it.type == type }
    }

    /**
     * 📓 取得帳本名稱
     * @return 帳本名稱（例如「我的記帳本」）
     */
    suspend fun getBookName(): String = withContext(Dispatchers.IO) {
        legacyPreferences.getString(KEY_BOOK_NAME, DEFAULT_BOOK_NAME).orEmpty()
            .ifBlank { DEFAULT_BOOK_NAME }
    }

    /**
     * ☁️ 取得雲端同步狀態
     * @return SyncStatus（上次同步時間、成功與否...）
     */
    suspend fun getSyncStatus(): SyncStatus = withContext(Dispatchers.IO) {
        syncStatusStore.getStatus()
    }

    /**
     * 💾 取得自動本機備份狀態
     * @return AutoLocalBackupStatus（是否啟用、備份路徑...）
     */
    suspend fun getAutoLocalBackupStatus(): AutoLocalBackupStatus = withContext(Dispatchers.IO) {
        autoLocalBackupManager.getStatus()
    }

    // ==================== ✍️ 寫入資料（修改功能）====================
    // 以下操作會修改資料，並且會自動觸發雲端同步

    /**
     * 📓 更新帳本名稱
     * @param name 新的帳本名稱
     * @return 操作結果（成功/失敗 + 訊息）
     */
    suspend fun updateBookName(name: String): OperationResult = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext OperationResult.fail("帳本名稱不能空白")
        }

        legacyPreferences.edit().putString(KEY_BOOK_NAME, trimmedName).apply()
        OperationResult.ok("帳本名稱已更新")
    }

    /**
     * ☁️ 手動備份到雲端
     * @return 操作結果
     */
    suspend fun backupNow(): OperationResult = withContext(Dispatchers.IO) {
        firebaseSyncManager.pushSnapshotAwait(buildSnapshot())
    }

    /**
     * 📤 匯出備份檔（JSON 格式）
     * @return JSON 字串（可以存成檔案或分享）
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        CocoCoinBackupCodec.encode(buildSnapshot())
    }

    /**
     * 📊 匯出 CSV 報表（可用 Excel 打開）
     * @return CSV 格式的字串
     */
    suspend fun exportTransactionsCsv(): String = withContext(Dispatchers.IO) {
        CocoCoinCsvExporter.exportTransactionsCsv(buildSnapshot())
    }

    /**
     * 📥 匯入備份檔（從 JSON 恢復資料）
     * @param json 備份檔的 JSON 字串
     * @return 操作結果
     */
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

    /**
     * 💾 設定自動本機備份的資料夾
     * @param uri 使用者選擇的資料夾 URI（透過系統檔案選擇器）
     * @return 操作結果
     */
    suspend fun configureAutoLocalBackupFolder(uri: Uri): OperationResult = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            autoLocalBackupManager.configureFolder(uri)
            autoLocalBackupManager.backupSnapshot(buildSnapshot())
            OperationResult.ok("已啟用自動本機備份")
        }.getOrElse { exception ->
            OperationResult.fail(exception.message ?: "設定自動本機備份失敗")
        }
    }

    /**
     * ⏸️ 停用自動本機備份
     * @return 操作結果
     */
    suspend fun disableAutoLocalBackup(): OperationResult = withContext(Dispatchers.IO) {
        autoLocalBackupManager.clearConfiguration()
        OperationResult.ok("已停用自動本機備份")
    }

    /**
     * 🔄 登入後從雲端還原資料
     *
     * 情境：使用者用 Google 登入，想把雲端的資料下載到這支手機
     *
     * @return 操作結果
     */
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

    /**
     * 🚪 登出並開始匿名 session（清除本機資料）
     *
     * 情境：使用者按「登出」，清除所有本機資料，回到匿名模式
     *
     * @return 操作結果
     */
    suspend fun signOutAndStartAnonymousSession(): OperationResult = withContext(Dispatchers.IO) {
        // 🧹 清除所有本機資料
        databaseHelper.clearAllData()
        clearLegacyPreferences()
        FirebaseAuthManager.signOut(appContext)
        categoryStore.clearCustomizations()
        resetInitializationState()

        // 🔄 重新初始化（會自動匿名登入）
        suspendCancellableCoroutine { continuation ->
            ensureInitialized {
                continuation.resume(OperationResult.ok("已登出，目前回到匿名模式"))
            }
        }
    }

    /**
     * 💰 儲存每月預算
     * @param year 年份
     * @param month 月份
     * @param amount 預算金額
     */
    suspend fun saveMonthlyBudget(year: Int, month: Int, amount: Int) = withContext(Dispatchers.IO) {
        databaseHelper.runInTransaction { database ->
            databaseHelper.upsertBudget(
                database,
                BudgetSetting(year = year, month = month, amount = amount)
            )
        }
        syncRemoteAsync()  // 同步到雲端
    }

    /**
     * ➕ 新增帳戶
     * @param name 帳戶名稱（例如「現金」）
     * @param balance 初始餘額
     * @return 操作結果
     */
    suspend fun addAccount(name: String, balance: Int): OperationResult = withContext(Dispatchers.IO) {
        // 檢查是否已有同名帳戶（不能有兩個「現金」）
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

    /**
     * ✏️ 更新帳戶（可改名、改餘額）
     * @param accountId 帳戶 ID
     * @param newName 新名稱
     * @param newBalance 新餘額
     * @return 操作結果
     */
    suspend fun updateAccount(accountId: Int, newName: String, newBalance: Int): OperationResult =
        withContext(Dispatchers.IO) {
            // 🔍 找到要修改的帳戶
            val currentAccount = databaseHelper.getAllAccounts().firstOrNull { it.id == accountId }
                ?: return@withContext OperationResult.fail("找不到帳戶")

            // 🚨 檢查新名稱是否與其他帳戶重複
            val duplicated = databaseHelper.getAllAccounts().any {
                it.id != accountId && it.name == newName
            }
            if (duplicated) {
                return@withContext OperationResult.fail("已存在相同名稱的帳戶")
            }

            databaseHelper.runInTransaction { database ->
                // ✏️ 更新帳戶資料
                databaseHelper.updateAccount(
                    database,
                    currentAccount.copy(
                        name = newName,
                        balance = newBalance,
                        updatedAt = System.currentTimeMillis()  // ✨ 更新時間戳
                    )
                )

                // 🔄 如果名稱有變，同步更新所有交易中的帳戶名稱
                // 白話：把「現金」改成「新台幣現金」，所有記帳也要跟著改
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

    /**
     * 🗑️ 刪除帳戶（只能刪除沒有交易記錄的帳戶）
     *
     * 為什麼有限制？
     * 如果有交易用了這個帳戶（例如「午餐 100 元」用「現金」付），
     * 刪掉帳戶後，那筆交易就不知道從哪個錢包付錢了～
     *
     * @param accountId 帳戶 ID
     * @return 操作結果
     */
    suspend fun deleteAccount(accountId: Int): OperationResult = withContext(Dispatchers.IO) {
        val account = databaseHelper.getAllAccounts().firstOrNull { it.id == accountId }
            ?: return@withContext OperationResult.fail("找不到帳戶")

        // 🔍 檢查是否有交易使用這個帳戶
        if (databaseHelper.isAccountUsed(account.name)) {
            return@withContext OperationResult.fail("此帳戶已有交易紀錄使用，請先修改或刪除相關交易。")
        }

        databaseHelper.runInTransaction { database ->
            databaseHelper.deleteAccount(database, accountId)
        }

        syncRemoteAsync()
        OperationResult.ok("帳戶已刪除")
    }
    // ==================== ✍️ 寫入資料（續）====================

    /**
     * ➕ 新增一筆交易
     *
     * 想像你去銀行櫃檯：
     * - 你要存錢（收入）或領錢（支出）
     * - 櫃檯會先檢查你的帳戶餘額夠不夠（領錢時）
     * - 夠的話，幫你記錄這筆交易，並更新帳戶餘額
     * - 然後同步到雲端備份
     *
     * @param type 「支出」或「收入」
     * @param category 分類（餐飲、交通、薪水...）
     * @param amount 金額
     * @param note 備註（吃什麼、買什麼）
     * @param time 交易時間
     * @param accountName 從哪個帳戶扣錢或存錢
     * @return 操作結果（成功/失敗 + 訊息）
     */
    suspend fun addTransaction(
        type: String,
        category: String,
        amount: Int,
        note: String,
        time: String,
        accountName: String
    ): OperationResult = withContext(Dispatchers.IO) {
        // 🔍 步驟 1：找到要使用的帳戶（像確認你要領哪個戶頭的錢）
        val account = databaseHelper.getAccountByName(accountName)
            ?: return@withContext OperationResult.fail("找不到所選帳戶")

        // 💰 步驟 2：檢查餘額是否足夠（支出才需要檢查）
        // 白話：你要領 1000 元，但戶頭只有 500 元 → 不行！
        if (type == "支出" && account.balance < amount) {
            return@withContext OperationResult.fail("$accountName 餘額不足，目前只剩 NT$ ${account.balance}")
        }

        // 🏦 步驟 3：開始資料庫交易（Transaction）
        // 白話：一次要做兩件事（記帳 + 改餘額），要嘛兩件都成功，要嘛都不做
        // 不能發生「記帳成功但餘額沒扣到」的慘劇！
        databaseHelper.runInTransaction { database ->
            // 📝 3.1 新增交易紀錄
            databaseHelper.insertTransaction(
                database,
                Transaction(
                    id = 0,  // id = 0 表示讓資料庫自動產生新編號
                    type = type,
                    category = category,
                    amount = amount,
                    note = note,
                    time = time,
                    accountName = accountName
                )
            )

            // 💰 3.2 調整帳戶餘額
            // 支出：金額變負數（扣錢），收入：金額變正數（加錢）
            val delta = if (type == "支出") -amount else amount
            databaseHelper.adjustAccountBalance(database, accountName, delta)
        }

        // ☁️ 步驟 4：背景同步到雲端（不等待結果，不影響使用者體驗）
        syncRemoteAsync()

        OperationResult.ok("儲存成功")
    }

    /**
     * ✏️ 新增或更新分類
     *
     * 白話：管理你的標籤系統（餐飲、交通、薪水、紅包...）
     *
     * 兩種使用情境：
     * 1. 新增分類：originalName = null，直接加入新分類
     * 2. 編輯分類：originalName = 舊名稱，修改現有分類
     *
     * 貼心功能：如果改了分類名稱，所有交易的對應分類也會跟著改！
     *
     * @param type 「支出」或「收入」（分類屬於哪一類）
     * @param originalName 原本的名稱（編輯時用，新增時為 null）
     * @param newName 新的分類名稱
     * @param icon 分類圖示（🍽、🚗、🛒...）
     * @return 操作結果
     */
    suspend fun upsertCategory(
        type: String,
        originalName: String?,
        newName: String,
        icon: String
    ): OperationResult = withContext(Dispatchers.IO) {
        val trimmedName = newName.trim()

        // 🚨 檢查名稱不能空白（不能叫「無名氏」分類）
        if (trimmedName.isEmpty()) {
            return@withContext OperationResult.fail("分類名稱不能空白")
        }

        // 🎨 如果沒有提供圖示，就根據類型給一個預設圖示
        val normalizedIcon = icon.ifBlank { TransactionCategoryCatalog.fallbackIcon(type) }

        // 📋 取得現有分類列表（可變版本，方便修改）
        val categories = categoryStore.getCategories().toMutableList()

        // 🔍 檢查是否已有同名分類（編輯時要排除自己）
        // 白話：不能有兩個「餐飲」分類，會打架
        val duplicate = categories.any {
            it.type == type &&
                    it.name == trimmedName &&
                    it.name != originalName  // 編輯時排除自己
        }
        if (duplicate) {
            return@withContext OperationResult.fail("已存在相同名稱的分類")
        }

        if (originalName == null) {
            // ➕ 模式 A：新增分類
            categories += TransactionCategoryDefinition(
                type = type,
                name = trimmedName,
                icon = normalizedIcon
            )
        } else {
            // ✏️ 模式 B：編輯分類
            val currentIndex = categories.indexOfFirst { it.type == type && it.name == originalName }
            if (currentIndex == -1) {
                return@withContext OperationResult.fail("找不到要編輯的分類")
            }

            val currentCategory = categories[currentIndex]
            // 更新分類（保留原來的其他欄位，只改名稱、圖示、時間）
            categories[currentIndex] = currentCategory.copy(
                name = trimmedName,
                icon = normalizedIcon,
                updatedAt = System.currentTimeMillis()  // ✨ 更新時間戳
            )

            // 🔄 如果名稱有變，同步更新所有交易中的分類名稱
            // 白話：把「美食」改成「餐飲」，所有記帳也要跟著改
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

        // 💾 儲存分類列表
        categoryStore.replaceCategories(categories)

        // ☁️ 同步到雲端
        syncRemoteAsync()

        OperationResult.ok(if (originalName == null) "分類已新增" else "分類已更新")
    }

    /**
     * 🗑️ 刪除分類
     *
     * 限制 1：每種類型至少保留一個分類（不能全刪光）
     * 限制 2：如果有交易使用這個分類，不能刪除（要先改掉那些交易）
     *
     * @param type 「支出」或「收入」
     * @param name 要刪除的分類名稱
     * @return 操作結果
     */
    suspend fun deleteCategory(type: String, name: String): OperationResult = withContext(Dispatchers.IO) {
        val categories = categoryStore.getCategories().toMutableList()
        val sameTypeCategories = categories.filter { it.type == type }

        // 🚫 限制 1：至少要保留一個分類
        // 白話：不能把所有「支出」分類都刪光，不然以後記帳要選什麼？
        if (sameTypeCategories.size <= 1) {
            return@withContext OperationResult.fail("至少要保留一個${type}分類")
        }

        // 🚫 限制 2：檢查是否有交易使用這個分類
        // 白話：如果有人記了「午餐 100 元」用「餐飲」分類，就不能刪「餐飲」
        if (databaseHelper.getAllTransactions().any { it.type == type && it.category == name }) {
            return@withContext OperationResult.fail("已有交易紀錄使用此分類，請先編輯相關交易")
        }

        // ❌ 執行刪除
        val removed = categories.removeAll { it.type == type && it.name == name }
        if (!removed) {
            return@withContext OperationResult.fail("找不到要刪除的分類")
        }

        // 💾 儲存分類列表
        categoryStore.replaceCategories(categories)

        // ☁️ 同步到雲端
        syncRemoteAsync()

        OperationResult.ok("分類已刪除")
    }

    /**
     * ✏️ 更新一筆交易（編輯）
     *
     * 這是整個 Repository 最複雜的方法之一！
     *
     * 為什麼複雜？因為修改交易會影響：
     * - 交易紀錄本身
     * - 可能換了金額（要調整餘額）
     * - 可能換了帳戶（A 帳戶還原，B 帳戶扣款）
     * - 可能換了類型（支出變收入？那影響可以很大！）
     *
     * 處理策略：
     * 1. 先用「模擬」的方式計算餘額變化，確認不會變負數
     * 2. 確認沒問題後，才正式更新資料庫
     *
     * @param updatedTransaction 修改後的交易物件
     * @return 操作結果
     */
    suspend fun updateTransaction(updatedTransaction: Transaction): OperationResult =
        withContext(Dispatchers.IO) {
            // 🔍 找到原本的交易（修改前）
            val oldTransaction = databaseHelper.getTransactionById(updatedTransaction.id)
                ?: return@withContext OperationResult.fail("找不到原始交易")

            // 📋 取得所有帳戶及其餘額（做成 Map 方便查找）
            val accountMap = databaseHelper.getAllAccounts().associateBy { it.name }.toMutableMap()
            val newAccount = accountMap[updatedTransaction.accountName]
                ?: return@withContext OperationResult.fail("找不到所選帳戶")

            // 🧮 步驟 1：模擬餘額變動（試算）
            // 白話：先在紙筆上計算，看看會不會出問題
            val simulatedBalances = accountMap.mapValues { it.value.balance }.toMutableMap()

            // 復原舊交易的影響（把原本扣的加回來，原本加的回扣回去）
            applyTransactionDelta(simulatedBalances, oldTransaction, reverse = true)

            // 套用新交易的影響
            applyTransactionDelta(simulatedBalances, updatedTransaction, reverse = false)

            // 🚨 步驟 2：檢查新餘額是否為負數
            val newBalance = simulatedBalances[newAccount.name] ?: 0
            if (newBalance < 0) {
                return@withContext OperationResult.fail(
                    "${newAccount.name} 餘額不足，可用 NT$ ${newAccount.balance}"
                )
            }

            // ✅ 步驟 3：試算沒問題，正式更新資料庫
            databaseHelper.runInTransaction { database ->
                // 復原舊交易對資料庫的影響
                applyDatabaseTransactionDelta(database, oldTransaction, reverse = true)

                // 套用新交易對資料庫的影響
                applyDatabaseTransactionDelta(database, updatedTransaction, reverse = false)

                // 更新交易紀錄本身
                databaseHelper.updateTransaction(
                    database,
                    updatedTransaction.copy(updatedAt = System.currentTimeMillis())
                )
            }

            // ☁️ 同步到雲端
            syncRemoteAsync()

            OperationResult.ok("交易已更新")
        }

    /**
     * 🗑️ 刪除一筆交易
     *
     * 流程：
     * 1. 復原這筆交易對帳戶餘額的影響（支出加回來，收入扣回去）
     * 2. 刪除交易紀錄
     *
     * @param transactionId 要刪除的交易 ID
     * @return 操作結果
     */
    suspend fun deleteTransaction(transactionId: Int): OperationResult = withContext(Dispatchers.IO) {
        // 🔍 找到要刪除的交易
        val transaction = databaseHelper.getTransactionById(transactionId)
            ?: return@withContext OperationResult.fail("找不到要刪除的交易")

        // 💰 復原該筆交易對帳戶餘額的影響
        // 白話：如果不復原，帳戶餘額會一直停留在「扣完錢」的狀態
        databaseHelper.runInTransaction { database ->
            // 復原（支出：把錢加回來，收入：把錢扣回去）
            applyDatabaseTransactionDelta(database, transaction, reverse = true)
            // 刪除交易紀錄
            databaseHelper.deleteTransaction(database, transactionId)
        }

        // ☁️ 同步到雲端
        syncRemoteAsync()

        OperationResult.ok("交易已刪除")
    }

    /**
     * ↩️ 復原被刪除的交易（從 Snackbar 的「復原」按鈕呼叫）
     *
     * 白話：使用者不小心刪錯了，按「復原」救回來！
     *
     * 注意：復原時要重新檢查餘額（因為可能刪除後又花了錢）
     *
     * @param transaction 被刪掉的那筆交易（之前先偷偷存起來的）
     * @return 操作結果
     */
    suspend fun restoreDeletedTransaction(transaction: Transaction): OperationResult =
        withContext(Dispatchers.IO) {
            // 🔍 檢查原帳戶是否還在
            val account = databaseHelper.getAccountByName(transaction.accountName)
                ?: return@withContext OperationResult.fail("原帳戶已不存在，無法復原這筆交易")

            // 💰 檢查餘額是否足夠（支出才需要檢查）
            // 白話：如果這筆是支出 500 元，但現在戶頭只剩 200 元，就不能復原
            if (transaction.type == "支出" && account.balance < transaction.amount) {
                return@withContext OperationResult.fail(
                    "${account.name} 餘額不足，無法復原這筆支出"
                )
            }

            databaseHelper.runInTransaction { database ->
                // 📝 重新插入交易（id 設為 0 讓資料庫自動產生新編號）
                databaseHelper.insertTransaction(
                    database,
                    transaction.copy(
                        id = 0,
                        updatedAt = System.currentTimeMillis()  // ✨ 用現在的時間
                    )
                )

                // 💰 調整帳戶餘額（支出扣錢，收入加錢）
                val delta = if (transaction.type == "支出") -transaction.amount else transaction.amount
                databaseHelper.adjustAccountBalance(database, transaction.accountName, delta)
            }

            // ☁️ 同步到雲端
            syncRemoteAsync()

            OperationResult.ok("已復原刪除的交易")
        }

    /**
     * ⚠️ 清除所有資料（危險操作）
     *
     * 白話：核彈按鈕，把所有記帳資料清光光
     *
     * 使用情境：
     * - 使用者按「清除所有資料」
     * - 登出時順便清資料
     *
     * 注意：此操作不可復原！
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        databaseHelper.clearAllData()           // 清空資料庫
        clearLegacyPreferences()                // 清空舊設定
        categoryStore.clearCustomizations()     // 恢復預設分類
        syncRemoteAsync()                       // 同步到雲端（空的）
    }

    // ==================== 🔧 內部輔助函式 ====================

    /**
     * 📦 打包目前所有資料成快照
     *
     * 白話：把金庫裡的所有東西（交易、帳戶、預算、分類）裝進一個大箱子
     *
     * @return CocoCoinSnapshot 超級打包箱
     */
    private fun buildSnapshot(): CocoCoinSnapshot {
        return databaseHelper.getSnapshot().copy(
            categories = categoryStore.getCategories()
        )
    }

    /**
     * 📥 套用快照到本機（完全取代）
     *
     * 白話：把一個大箱子的內容完全替換掉金庫裡的東西
     *
     * 使用情境：
     * - 從雲端下載備份後還原
     * - 匯入 JSON 備份檔
     *
     * @param snapshot 要套用的快照
     */
    private fun applySnapshot(snapshot: CocoCoinSnapshot) {
        databaseHelper.replaceAllData(snapshot)           // 取代資料庫內容
        categoryStore.replaceCategories(snapshot.categories)  // 取代分類
    }

    /**
     * 🧮 模擬交易對餘額的影響（用於檢查餘額是否足夠）
     *
     * 這是「試算」版本，不會真的改資料庫，只是紙上算算看
     *
     * 白話：在真正扣錢之前，先在腦子裡算一遍：「如果這筆錢扣下去，戶頭會變多少？」
     *
     * @param balances 帳戶餘額的模擬表（這個方法會直接修改它）
     * @param transaction 要模擬的交易
     * @param reverse true = 復原（逆向操作），false = 正常套用
     */
    private fun applyTransactionDelta(
        balances: MutableMap<String, Int>,
        transaction: Transaction,
        reverse: Boolean
    ) {
        val current = balances[transaction.accountName] ?: return

        /**
         * 🧮 delta 計算邏輯：
         *
         * 情況表格：
         * ┌──────────────┬─────────┬────────────┬──────────────┐
         * │ 交易類型     │ reverse │ delta      │ 白話         │
         * ├──────────────┼─────────┼────────────┼──────────────┤
         * │ 支出         │ false   │ -amount    │ 正常扣錢     │
         * │ 支出         │ true    │ +amount    │ 復原：把錢加回來 │
         * │ 收入         │ false   │ +amount    │ 正常加錢     │
         * │ 收入         │ true    │ -amount    │ 復原：把錢扣回去 │
         * └──────────────┴─────────┴────────────┴──────────────┘
         */
        val delta = when {
            transaction.type == "支出" && reverse -> transaction.amount    // 復原支出：加回來
            transaction.type == "支出" -> -transaction.amount              // 新增支出：扣掉
            reverse -> -transaction.amount                                 // 復原收入：扣回去
            else -> transaction.amount                                     // 新增收入：加上
        }
        balances[transaction.accountName] = current + delta
    }

    /**
     * 💾 實際套用交易對資料庫餘額的影響
     *
     * 這是「正式執行」版本，會真的修改資料庫
     *
     * @param database 資料庫實例
     * @param transaction 要套用的交易
     * @param reverse true = 復原（逆向操作），false = 正常套用
     */
    private fun applyDatabaseTransactionDelta(
        database: CocoCoinRoomDatabase,
        transaction: Transaction,
        reverse: Boolean
    ) {
        // 同一個 delta 計算邏輯
        val delta = when {
            transaction.type == "支出" && reverse -> transaction.amount
            transaction.type == "支出" -> -transaction.amount
            reverse -> -transaction.amount
            else -> transaction.amount
        }
        databaseHelper.adjustAccountBalance(database, transaction.accountName, delta)
    }

    /**
     * ☁️ 非同步同步到雲端和自動備份（不等待結果）
     *
     * 白話：派一個小弟默默去做雲端備份，不影響使用者正在做的事
     *
     * 為什麼不等待？因為：
     * - 使用者不需要等雲端上傳完成才繼續記帳
     * - 就算上傳失敗，下次同步會再試
     */
    private fun syncRemoteAsync() {
        repositoryScope.launch {
            val snapshot = buildSnapshot()
            firebaseSyncManager.pushSnapshot(snapshot)      // 上傳到雲端
            autoLocalBackupManager.backupSnapshot(snapshot) // 自動本機備份
        }
    }

    /**
     * 🔄 重設初始化狀態（登出時使用）
     *
     * 白話：把金庫的門關上，下次要重新開門
     */
    private fun resetInitializationState() {
        synchronized(this) {
            initializationCompleted = false
            initializationRunning = false
            pendingCallbacks.clear()
        }
    }

    // ==================== 📦 舊版資料遷移 ====================

    /**
     * 🚚 遷移舊版資料（如果是第一次安裝新版本）
     *
     * 白話：使用者之前用舊版 App，現在升級到新版
     * 要把舊版儲存的資料（SharedPreferences）搬到新版的資料庫（Room）
     *
     * 格式範例：
     * - 交易：「支出||餐飲||100||午餐||2026/04/06 12:00||現金」
     * - 帳戶：「現金||5000」
     * - 預算：「monthly_budget_2026_4」= 15000
     */
    private fun migrateLegacyDataIfNeeded() {
        // 🚩 已經遷移過了，跳過
        if (legacyPreferences.getBoolean(KEY_LEGACY_IMPORTED, false)) {
            return
        }

        // 如果資料庫已經有資料，就不需要遷移（可能是乾淨安裝）
        if (!databaseHelper.getSnapshot().isEmpty()) {
            legacyPreferences.edit().putBoolean(KEY_LEGACY_IMPORTED, true).apply()
            return
        }

        val currentTime = System.currentTimeMillis()

        // 📖 從舊版 SharedPreferences 讀取資料
        val legacyTransactions = parseLegacyTransactions(
            legacyPreferences.getString("transaction_list", "").orEmpty(),
            currentTime
        )
        val legacyAccounts = parseLegacyAccounts(
            legacyPreferences.getString("asset_account_list", "").orEmpty(),
            currentTime
        )
        val legacyBudgets = parseLegacyBudgets(currentTime)

        // 📝 如果有舊版資料，就寫入新資料庫
        if (legacyTransactions.isNotEmpty() || legacyAccounts.isNotEmpty() || legacyBudgets.isNotEmpty()) {
            databaseHelper.replaceAllData(
                CocoCoinSnapshot(
                    transactions = legacyTransactions,
                    accounts = legacyAccounts,
                    budgets = legacyBudgets
                )
            )
        }

        // ✅ 標記已遷移，下次不再執行
        legacyPreferences.edit().putBoolean(KEY_LEGACY_IMPORTED, true).apply()
    }

    /**
     * 🔍 解析舊版交易資料
     *
     * 舊版格式：type||category||amount||note||time||accountName##...
     * 例如：「支出||餐飲||100||午餐||2026/04/06 12:00||現金##收入||薪水||50000||...」
     *
     * @param savedData 儲存的字串
     * @param baseUpdatedAt 基礎時間戳（用於給舊資料一個時間）
     * @return Transaction 列表
     */
    private fun parseLegacyTransactions(savedData: String, baseUpdatedAt: Long): List<Transaction> {
        if (savedData.isBlank()) return emptyList()

        return savedData.split("##").mapIndexedNotNull { index, item ->
            val parts = item.split("||")
            if (parts.size < 5) {
                return@mapIndexedNotNull null  // 格式不對，跳過
            }

            Transaction(
                id = index + 1,
                type = parts[0],
                category = parts[1],
                amount = parts[2].toIntOrNull() ?: 0,
                note = parts[3],
                time = parts[4],
                accountName = parts.getOrNull(5) ?: "未指定帳戶",  // 舊版可能沒有帳戶欄位
                updatedAt = baseUpdatedAt + index  // 給每筆交易不同的時間戳
            )
        }
    }

    /**
     * 🔍 解析舊版帳戶資料
     *
     * 舊版格式：name||balance##...
     * 例如：「現金||5000##信用卡||30000##...」
     *
     * @param savedData 儲存的字串
     * @param baseUpdatedAt 基礎時間戳
     * @return AssetAccount 列表
     */
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

    /**
     * 🔍 解析舊版預算資料
     *
     * 舊版格式：monthly_budget_2026_4 = 15000
     * 還原成 BudgetSetting(year=2026, month=4, amount=15000)
     *
     * @param baseUpdatedAt 基礎時間戳
     * @return BudgetSetting 列表
     */
    private fun parseLegacyBudgets(baseUpdatedAt: Long): List<BudgetSetting> {
        return legacyPreferences.all.mapNotNull { (key, value) ->
            // 只處理以 monthly_budget_ 開頭的鍵
            if (!key.startsWith("monthly_budget_")) {
                return@mapNotNull null
            }

            // 解析：monthly_budget_2026_4 → [monthly, budget, 2026, 4]
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

    /**
     * 🧹 清除舊版偏好設定
     *
     * 白話：搬完家後，把舊紙箱丟掉，保持乾淨
     */
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

    // ---------- 📦 伴生物件（單例模式的魔法）----------

    companion object {
        private const val LEGACY_PREFS_NAME = "cococoin_prefs"          // 舊版偏好設定名稱
        private const val KEY_LEGACY_IMPORTED = "legacy_data_imported_v1"  // 是否已遷移舊資料
        private const val KEY_BOOK_NAME = "book_name"                  // 帳本名稱的儲存鍵
        private const val DEFAULT_BOOK_NAME = "CocoCoin"               // 預設帳本名稱

        @Volatile
        private var instance: CocoCoinRepository? = null

        /**
         * 🏭 取得 Repository 單例物件
         *
         * 白話：只有一個金庫管理員，全 App 共用
         *
         * Double-Checked Locking（雙重檢查鎖定）模式：
         * 1. 第一次檢查：如果已經有管理員，直接回傳（快速路徑）
         * 2. 同步鎖：確保多執行緒安全
         * 3. 第二次檢查：可能排隊期間被別人建立了，再檢查一次
         *
         * @param context 應用程式上下文
         * @return 唯一的 CocoCoinRepository 實例
         */
        fun getInstance(context: Context): CocoCoinRepository {
            return instance ?: synchronized(this) {
                instance ?: CocoCoinRepository(context).also { instance = it }
            }
        }
    }
}