package com.example.cococoin

import android.app.AlertDialog          // 對話框工具（跳出小視窗詢問使用者）
import android.graphics.Color           // 顏色處理
import android.net.Uri                  // 檔案路徑
import android.os.Bundle                // 用來傳遞資料的包裹
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts  // 檔案選擇器工具
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope // 跟隨 Fragment 生命週期的協程範圍
import kotlinx.coroutines.Dispatchers   // 協程的調度器（決定程式在哪條跑道執行）
import kotlinx.coroutines.launch        // 啟動一個協程（像開一條新的執行緒）
import kotlinx.coroutines.withContext   // 切換協程的跑道（換線執行）

// 設定頁面 Fragment：讓使用者管理帳本名稱、雲端同步、帳號安全、資料備份
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private lateinit var layoutBookNameCard: View      // 帳本名稱的卡片（點擊可編輯）
    private lateinit var tvBookName: TextView          // 顯示帳本名稱的文字
    private lateinit var tvTransactionCount: TextView  // 顯示記帳筆數（例如「128 筆」）
    private lateinit var tvLastSync: TextView          // 顯示上次雲端同步時間
    private lateinit var tvAutoBackupStatus: TextView  // 顯示自動本機備份狀態
    private lateinit var tvCategorySummary: TextView   // 顯示目前支出/收入分類數量
    private lateinit var tvLinkedProviders: TextView   // 顯示已綁定的登入方式（Email、Google）
    private lateinit var btnPrimaryAccountAction: Button   // 主要帳號操作按鈕（升級/登入/新增）
    private lateinit var btnSecondaryAccountAction: Button // 次要帳號操作按鈕
    private lateinit var btnBackupNow: Button          // 立即備份到雲端的按鈕
    private lateinit var btnSignOut: Button            // 登出按鈕
    private lateinit var btnManageCategories: Button   // 管理自訂分類的按鈕
    private lateinit var btnChooseAutoBackupFolder: Button  // 選擇自動備份資料夾的按鈕
    private lateinit var btnDisableAutoBackup: Button  // 停用自動備份的按鈕
    private lateinit var btnExportData: Button         // 匯出備份檔的按鈕
    private lateinit var btnImportData: Button         // 匯入備份檔的按鈕
    private lateinit var btnExportCsv: Button          // 匯出 CSV 報表的按鈕
    private lateinit var btnClearAllData: Button       // 清除所有資料的按鈕

    // 資料倉庫（延遲初始化，等到需要時才建立）
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 帳號連結管理器（處理登入、綁定 Email/Google）：
    //負責跟 Firebase 溝通，讓使用者可以登入、綁定帳號
    private val authLinkManager by lazy(LazyThreadSafetyMode.NONE) {
        AuthLinkManager(requireContext().applicationContext)
    }

    // 匯出備份檔的啟動器：當使用者點「匯出備份」，會跳出 Android 的檔案總管讓他選存放位置
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")  // 建立 JSON 檔案
    ) { uri ->
        if (uri != null) {
            exportBackupToUri(uri)  // 執行匯出（把資料寫到那個檔案）
        }
    }

    // 匯入備份檔的啟動器：當使用者點「匯入備份」，會跳出檔案總管讓他選要讀取的 JSON 檔
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()  // 開啟一個既有文件
    ) { uri ->
        if (uri != null) {
            importBackupFromUri(uri)  // 執行匯入（讀取檔案內容）
        }
    }

    // 匯出 CSV 報表的啟動器：CSV 是 Excel 可以打開的格式，方便使用者用電腦分析花費
    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportCsvToUri(uri)
        }
    }

    // 選擇自動備份資料夾的啟動器：讓使用者選一個資料夾，以後 App 會自動把備份存到這裡
    private val autoBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()  // 開啟一個資料夾
    ) { uri ->
        if (uri != null) {
            configureAutoBackupFolder(uri)
        }
    }

    // 當畫面建立完成時呼叫（像「畫面 ready，可以開始設定按鈕了」）
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutBookNameCard = view.findViewById(R.id.layoutBookNameCard)
        tvBookName = view.findViewById(R.id.tvBookName)
        tvTransactionCount = view.findViewById(R.id.tvTransactionCount)
        tvLastSync = view.findViewById(R.id.tvLastSync)
        tvAutoBackupStatus = view.findViewById(R.id.tvAutoBackupStatus)
        tvCategorySummary = view.findViewById(R.id.tvCategorySummary)
        tvLinkedProviders = view.findViewById(R.id.tvLinkedProviders)
        btnPrimaryAccountAction = view.findViewById(R.id.btnPrimaryAccountAction)
        btnSecondaryAccountAction = view.findViewById(R.id.btnSecondaryAccountAction)
        btnBackupNow = view.findViewById(R.id.btnBackupNow)
        btnSignOut = view.findViewById(R.id.btnSignOut)
        btnManageCategories = view.findViewById(R.id.btnManageCategories)
        btnChooseAutoBackupFolder = view.findViewById(R.id.btnChooseAutoBackupFolder)
        btnDisableAutoBackup = view.findViewById(R.id.btnDisableAutoBackup)
        btnExportData = view.findViewById(R.id.btnExportData)
        btnImportData = view.findViewById(R.id.btnImportData)
        btnExportCsv = view.findViewById(R.id.btnExportCsv)
        btnClearAllData = view.findViewById(R.id.btnClearAllData)

        // 點擊帳本名稱卡片 → 跳出編輯視窗讓使用者改名字
        layoutBookNameCard.setOnClickListener {
            showEditBookNameDialog()
        }

        // 點擊「立即備份」按鈕 → 把本機資料上傳到雲端
        btnBackupNow.setOnClickListener {
            backupNow()
        }

        // 點擊「管理分類」按鈕 → 開啟分類管理對話框（新增/編輯/刪除支出/收入分類）
        btnManageCategories.setOnClickListener {
            showCategoryManagerDialog()
        }

        // 點擊「登出」按鈕 → 登出目前帳號
        btnSignOut.setOnClickListener {
            signOutCurrentAccount()
        }

        // 等待資料庫初始化完成後，刷新畫面（讀取最新資料顯示）
        repository.ensureInitialized {
            refreshSettings()
        }

        // 點擊「清除所有資料」→ 跳出確認視窗（怕使用者誤按）
        btnClearAllData.setOnClickListener {
            confirmClearAllData()
        }

        // 點擊「匯出備份檔」→ 選擇儲存位置
        btnExportData.setOnClickListener {
            startExportBackup()
        }

        // 點擊「匯入備份檔」→ 跳出確認視窗，然後選擇檔案
        btnImportData.setOnClickListener {
            confirmImportBackup()
        }

        // 點擊「匯出 CSV」→ 選擇儲存位置
        btnExportCsv.setOnClickListener {
            startExportCsv()
        }

        // 點擊「選擇自動備份資料夾」→ 開啟資料夾選擇器
        btnChooseAutoBackupFolder.setOnClickListener {
            autoBackupFolderLauncher.launch(null)
        }

        // 點擊「停用自動備份」→ 跳出確認視窗
        btnDisableAutoBackup.setOnClickListener {
            disableAutoBackup()
        }
    }

    // 當畫面重新顯示時呼叫（例如從其他頁面返回設定頁）
    override fun onResume() {
        super.onResume()
        refreshSettings()  // 重新讀取資料並更新畫面
    }

    // 更新畫面上的所有設定資訊
    private fun refreshSettings() {
        // 啟動一個協程（背景任務），避免卡住畫面
        viewLifecycleOwner.lifecycleScope.launch {

            // 從資料庫讀取帳本名稱（在背景執行緒執行，避免卡畫面）
            val bookName = withContext(Dispatchers.IO) {
                repository.getBookName()
            }

            // 讀取記帳總筆數
            val count = withContext(Dispatchers.IO) {
                repository.getTransactionCount()
            }

            // 讀取雲端同步狀態（上次同步時間、成功與否）
            val syncStatus = withContext(Dispatchers.IO) {
                repository.getSyncStatus()
            }

            // 讀取帳號綁定狀態（有沒有綁 Email、Google）
            val authLinkStatus = withContext(Dispatchers.IO) {
                authLinkManager.getFreshStatus()
            }

            // 讀取自動本機備份狀態（有沒有開啟、存在哪個資料夾）
            val autoBackupStatus = withContext(Dispatchers.IO) {
                repository.getAutoLocalBackupStatus()
            }

            // 讀取所有分類（支出和收入的分類清單）
            val categories = withContext(Dispatchers.IO) {
                repository.getAllCategories()
            }

            // 把讀到的資料顯示到畫面上（這些操作要在主執行緒做）
            tvBookName.text = bookName
            tvTransactionCount.text = "${count} 筆"
            bindSyncStatus(syncStatus)              // 顯示同步狀態
            bindAuthStatus(authLinkStatus)          // 顯示帳號綁定狀態
            bindAutoBackupStatus(autoBackupStatus)  // 顯示自動備份狀態
            bindCategorySummary(categories)         // 顯示分類數量統計
        }
    }

    // 顯示分類統計（例如「支出 10 種 · 收入 7 種」）
    private fun bindCategorySummary(categories: List<TransactionCategoryDefinition>) {
        val expenseCount = categories.count { it.type == TransactionCategoryCatalog.TYPE_EXPENSE }
        val incomeCount = categories.count { it.type == TransactionCategoryCatalog.TYPE_INCOME }
        tvCategorySummary.text = "支出 $expenseCount 種 · 收入 $incomeCount 種"
    }

    // 顯示「自動本機備份」的狀態 ：
    // 在畫面上顯示「已啟用」或「尚未設定」，以及備份位置和時間
    private fun bindAutoBackupStatus(status: AutoLocalBackupStatus) {
        // 可以把多段文字組裝成一句話
        tvAutoBackupStatus.text = buildString {
            // 先寫「已啟用」或「尚未設定」
            append(if (status.isConfigured) "已啟用" else "尚未設定")

            // 如果有設定資料夾名稱，就加上「 · 資料夾名稱」
            status.folderName?.let {
                append(" · ")
                append(it)
            }

            // 如果有上次備份時間，就加上「 · 2025/01/01 12:00」
            status.lastBackupAt?.let {
                append(" · ")
                append(android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", it))
            }
        }

        // 如果自動備份有開啟，就顯示「停用」按鈕；否則隱藏它
        btnDisableAutoBackup.visibility = if (status.isConfigured) View.VISIBLE else View.GONE
    }

    // 顯示雲端同步狀態
    private fun bindSyncStatus(syncStatus: SyncStatus) {
        tvLastSync.text = when {
            // 如果同步失敗過，顯示「無同步資料」
            syncStatus.lastSyncSuccessful == false -> "無同步資料"
            // 如果有同步時間，就把時間格式化成 yyyy/MM/dd HH:mm 顯示
            syncStatus.lastSyncAt != null -> android.text.format.DateFormat
                .format("yyyy/MM/dd HH:mm", syncStatus.lastSyncAt)
                .toString()
            // 其他情況（從未同步過）顯示「--」
            else -> "--"
        }
    }

    // 顯示帳號綁定狀態
    private fun bindAuthStatus(status: AuthLinkStatus) {
        // 建立一個列表，收集已綁定的登入方式
        val providers = buildList {
            if (status.hasGoogleProvider) add("Google")  // 有綁 Google 就加進去
        }

        // 顯示綁定方式，如果都沒有就顯示「尚未綁定任何登入方式」
        tvLinkedProviders.text =
            if (providers.isEmpty()) "尚未綁定任何登入方式" else providers.joinToString("、")

        // 根據有沒有綁定，套用不同的背景樣式和文字顏色
        applyBadgeStyle(
            textView = tvLinkedProviders,
            backgroundRes = if (providers.isEmpty()) {
                R.drawable.bg_settings_badge_neutral  // 未綁定：中性背景
            } else {
                R.drawable.bg_settings_badge_soft     // 已綁定：柔和背景
            },
            textColor = if (providers.isEmpty()) "#7A6F68" else "#6F665F"
        )

        // 根據登入狀態決定顯示哪些按鈕（升級、登入、備份、登出）
        bindAccountActions(status)
    }

    // 套用徽章樣式（改變文字框的背景和文字顏色）
    private fun applyBadgeStyle(textView: TextView, backgroundRes: Int, textColor: String) {
        textView.setBackgroundResource(backgroundRes)  // 設定背景圖片
        textView.setTextColor(Color.parseColor(textColor))  // 設定文字顏色
    }

    // 根據登入狀態決定顯示哪些按鈕
    // 白話：根據「Firebase 有沒有準備好」、「使用者有沒有登入」、「是不是匿名帳號」
    //       來決定要顯示「升級」、「登入」、「備份」、「登出」哪個按鈕
    private fun bindAccountActions(status: AuthLinkStatus) {
        // 一開始先把所有按鈕隱藏
        btnPrimaryAccountAction.visibility = View.GONE
        btnSecondaryAccountAction.visibility = View.GONE
        btnBackupNow.visibility = View.GONE
        btnSignOut.visibility = View.GONE

        // 根據不同情況顯示不同按鈕
        when {
            // 情況 1：Firebase 還沒設定好 → 什麼按鈕都不顯示
            !status.isFirebaseReady -> {
                btnPrimaryAccountAction.visibility = View.GONE
                btnSecondaryAccountAction.visibility = View.GONE
            }

            // 情況 2：使用者還沒登入 → 顯示「登入既有帳號」按鈕
            !status.isSignedIn -> {
                btnPrimaryAccountAction.visibility = View.VISIBLE
                btnPrimaryAccountAction.text = "登入 Google 帳號"
                btnPrimaryAccountAction.setOnClickListener {
                    signInWithGoogle()
                }
            }

            // 情況 3：使用者是匿名登入 → 顯示「升級」和「登入既有帳號」
            status.isAnonymous -> {
                btnPrimaryAccountAction.visibility = View.VISIBLE
                btnPrimaryAccountAction.text = "綁定 Google 帳號"
                btnPrimaryAccountAction.setOnClickListener {
                    linkGoogle()
                }

                btnSecondaryAccountAction.visibility = View.VISIBLE
                btnSecondaryAccountAction.text = "登入既有 Google 帳號找回資料"
                btnSecondaryAccountAction.setOnClickListener {
                    signInWithGoogle()
                }
            }

            // 情況 4：已登入且不是匿名 → 顯示「新增登入方式」、「備份」、「登出」
            else -> {
                if (!status.hasGoogleProvider) {
                    btnPrimaryAccountAction.visibility = View.VISIBLE
                    btnPrimaryAccountAction.text = "新增 Google 登入方式"
                    btnPrimaryAccountAction.setOnClickListener {
                        linkGoogle()
                    }
                }

                btnBackupNow.visibility = View.VISIBLE
                btnBackupNow.isEnabled = true
                btnSignOut.visibility = View.VISIBLE
                btnSignOut.isEnabled = true
            }
        }

        // 額外檢查：如果 Firebase 準備好、已登入、且不是匿名 → 確保備份按鈕出現
        if (status.isFirebaseReady && status.isSignedIn && !status.isAnonymous) {
            btnBackupNow.visibility = View.VISIBLE
            btnBackupNow.isEnabled = true
        }

        // 決定登出按鈕是否顯示
        btnSignOut.visibility =
            if (status.isFirebaseReady && status.isSignedIn && !status.isAnonymous) {
                View.VISIBLE
            } else {
                View.GONE
            }
        btnSignOut.isEnabled = status.isFirebaseReady && status.isSignedIn && !status.isAnonymous
    }

    // 綁定 Google 帳號
    private fun linkGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authLinkManager.linkGoogle(requireActivity())
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "Google 綁定成功" else "Google 綁定失敗",
                Toast.LENGTH_SHORT
            ).show()
            refreshSettings()  // 刷新畫面
        }
    }

    // 用 Google 帳號登入
    private fun signInWithGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authLinkManager.signInWithGoogle(requireActivity())
            if (!result.success) {
                Toast.makeText(
                    requireContext(),
                    result.message ?: "Google 登入失敗",
                    Toast.LENGTH_SHORT
                ).show()
                refreshSettings()
                return@launch
            }

            restoreRemoteDataAfterLogin(result.message ?: "Google 登入成功")
        }
    }

    // 登入成功後，從雲端還原資料到本機
    private fun restoreRemoteDataAfterLogin(loginMessage: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val restoreResult = withContext(Dispatchers.IO) {
                repository.restoreFromCloudAfterLogin()
            }

            val toastMessage = when {
                restoreResult.success -> "$loginMessage，${restoreResult.message}"
                else -> "$loginMessage，${restoreResult.message ?: "雲端還原失敗"}"
            }

            Toast.makeText(
                requireContext(),
                toastMessage,
                Toast.LENGTH_LONG
            ).show()

            refreshSettings()
            // 回到首頁（讓使用者看到還原後的資料）
            (requireActivity() as? MainActivity)?.showHomeFragment()
        }
    }

    // 立即備份到雲端
    private fun backupNow() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.backupNow()
            }
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "備份成功" else "備份失敗",
                Toast.LENGTH_SHORT
            ).show()
            refreshSettings()
        }
    }

    // ==================== 分類管理功能 ====================

    // 顯示分類管理對話框（讓使用者新增、編輯、刪除分類）
    private fun showCategoryManagerDialog(initialType: String = TransactionCategoryCatalog.TYPE_EXPENSE) {
        // 載入 dialog_manage_categories.xml 這個畫面
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_categories, null)
        val btnExpenseCategories =
            dialogView.findViewById<Button>(R.id.btnExpenseCategories)  // 支出按鈕
        val btnIncomeCategories =
            dialogView.findViewById<Button>(R.id.btnIncomeCategories)    // 收入按鈕
        val btnAddExpenseCategory =
            dialogView.findViewById<Button>(R.id.btnAddExpenseCategory)
        val btnAddIncomeCategory =
            dialogView.findViewById<Button>(R.id.btnAddIncomeCategory)
        val layoutCategoryList =
            dialogView.findViewById<LinearLayout>(R.id.layoutCategoryList) // 放分類列表的容器

        var currentType = initialType  // 目前正在查看的分類類型（支出或收入）

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("管理分類 / icon")
            .setView(dialogView)
            .setPositiveButton("完成", null)  // 只是關閉對話框，不做任何事
            .create()

        // 重新繪製分類列表（每次切換類型或新增/編輯/刪除後呼叫）
        fun render() {
            viewLifecycleOwner.lifecycleScope.launch {
                // 從資料庫讀取所有分類
                val allCategories = withContext(Dispatchers.IO) {
                    repository.getAllCategories()
                }
                // 只顯示目前選中的類型（支出或收入）
                val currentCategories = allCategories.filter { it.type == currentType }

                // 更新支出/收入按鈕的樣式（哪個被選中）
                updateCategoryToggleStyles(
                    currentType = currentType,
                    expenseButton = btnExpenseCategories,
                    incomeButton = btnIncomeCategories
                )

                // 繪製分類列表（每個分類顯示成一個項目，附帶編輯/刪除按鈕）
                renderCategoryList(
                    container = layoutCategoryList,
                    categories = currentCategories,
                    onEdit = { category ->
                        showEditCategoryDialog(
                            type = currentType,
                            existingCategory = category,
                            onSaved = {
                                render()          // 重新繪製列表
                                refreshSettings() // 更新設定頁面的分類數量
                            }
                        )
                    },
                    onDelete = { category ->
                        confirmDeleteCategory(
                            type = currentType,
                            category = category,
                            onDeleted = {
                                render()          // 重新繪製列表
                                refreshSettings() // 更新設定頁面的分類數量
                            }
                        )
                    }
                )
            }
        }

        // 點擊「支出」按鈕 → 切換到支出分類
        btnExpenseCategories.setOnClickListener {
            currentType = TransactionCategoryCatalog.TYPE_EXPENSE
            render()
        }

        // 點擊「收入」按鈕 → 切換到收入分類
        btnIncomeCategories.setOnClickListener {
            currentType = TransactionCategoryCatalog.TYPE_INCOME
            render()
        }

        // 點擊「新增支出種類」→ 直接開啟支出分類新增流程
        btnAddExpenseCategory.setOnClickListener {
            showEditCategoryDialog(
                type = TransactionCategoryCatalog.TYPE_EXPENSE,
                existingCategory = null,
                onSaved = {
                    currentType = TransactionCategoryCatalog.TYPE_EXPENSE
                    render()
                    refreshSettings()
                }
            )
        }

        // 點擊「新增收入種類」→ 直接開啟收入分類新增流程
        btnAddIncomeCategory.setOnClickListener {
            showEditCategoryDialog(
                type = TransactionCategoryCatalog.TYPE_INCOME,
                existingCategory = null,
                onSaved = {
                    currentType = TransactionCategoryCatalog.TYPE_INCOME
                    render()
                    refreshSettings()
                }
            )
        }

        // 對話框顯示時，立刻繪製分類列表
        dialog.setOnShowListener { render() }
        dialog.show()
    }

    // 更新支出/收入切換按鈕的樣式
    private fun updateCategoryToggleStyles(
        currentType: String,
        expenseButton: Button,
        incomeButton: Button
    ) {
        if (currentType == TransactionCategoryCatalog.TYPE_EXPENSE) {
            // 支出被選中：支出按鈕高亮，收入按鈕普通
            expenseButton.setBackgroundResource(R.drawable.bg_category_button_expense_selected)
            expenseButton.setTextColor(Color.parseColor("#FFFDFB"))
            incomeButton.setBackgroundResource(R.drawable.bg_category_button_expense_unselected)
            incomeButton.setTextColor(Color.parseColor("#906C6A"))
        } else {
            // 收入被選中：收入按鈕高亮，支出按鈕普通
            incomeButton.setBackgroundResource(R.drawable.bg_category_button_expense_selected)
            incomeButton.setTextColor(Color.parseColor("#FFFDFB"))
            expenseButton.setBackgroundResource(R.drawable.bg_category_button_expense_unselected)
            expenseButton.setTextColor(Color.parseColor("#906C6A"))
        }
    }

    // 繪製分類列表（把每個分類顯示成一個項目）
    private fun renderCategoryList(
        container: LinearLayout,                     // 要放列表的容器
        categories: List<TransactionCategoryDefinition>,  // 分類清單
        onEdit: (TransactionCategoryDefinition) -> Unit,   // 點擊編輯時要做的事
        onDelete: (TransactionCategoryDefinition) -> Unit  // 點擊刪除時要做的事
    ) {
        container.removeAllViews()  // 清空舊的內容（避免重複顯示）

        categories.forEach { category ->
            // 載入 item_manage_category.xml 這個項目模板
            val itemView = layoutInflater.inflate(R.layout.item_manage_category, container, false)
            val tvManageCategoryIcon = itemView.findViewById<TextView>(R.id.tvManageCategoryIcon)
            val tvManageCategoryName = itemView.findViewById<TextView>(R.id.tvManageCategoryName)
            val btnEditCategory = itemView.findViewById<Button>(R.id.btnEditCategory)
            val btnDeleteCategory = itemView.findViewById<Button>(R.id.btnDeleteCategory)

            // 顯示分類的圖示和名稱
            tvManageCategoryIcon.text = category.icon
            tvManageCategoryName.text = category.name

            // 設定編輯和刪除按鈕的點擊事件
            btnEditCategory.setOnClickListener { onEdit(category) }
            btnDeleteCategory.setOnClickListener { onDelete(category) }

            // 把這個項目加到列表中
            container.addView(itemView)
        }
    }

    // 顯示編輯/新增分類的對話框
    private fun showEditCategoryDialog(
        type: String,                          // 支出 或 收入
        existingCategory: TransactionCategoryDefinition?,  // 如果是編輯就有值，新增就是 null
        onSaved: () -> Unit                    // 儲存成功後要做的事（重新繪製列表）
    ) {
        // 載入 dialog_edit_category.xml 這個畫面設計圖
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_category, null)

        // 取得畫面中的元件
        val btnPickCategoryIcon =
            dialogView.findViewById<Button>(R.id.btnPickCategoryIcon)  // 選擇圖示的按鈕
        val etCategoryName =
            dialogView.findViewById<EditText>(R.id.etCategoryName)          // 分類名稱輸入框

        // 決定一開始要顯示哪個圖示
        // 如果是編輯模式，就用原本的圖示；如果是新增模式，就用預設圖示（支出用🧾，收入用💰）
        var selectedIcon = existingCategory?.icon ?: TransactionCategoryCatalog.fallbackIcon(type)

        // 更新圖示按鈕上的文字（顯示目前選擇的 emoji + 「選擇 icon」）
        fun refreshIconButton() {
            btnPickCategoryIcon.text = "$selectedIcon  選擇 icon"
        }

        // 如果是編輯模式，把原有的分類名稱填進輸入框
        etCategoryName.setText(existingCategory?.name.orEmpty())
        // 把游標移到文字的最右邊，方便使用者直接修改
        etCategoryName.setSelection(etCategoryName.text.length)

        // 更新按鈕上的圖示顯示
        refreshIconButton()

        // 點擊圖示選擇按鈕 → 開啟圖示選擇器（讓使用者挑選 emoji）
        btnPickCategoryIcon.setOnClickListener {
            showCategoryIconChooser(selectedIcon) { icon ->
                selectedIcon = icon      // 記住使用者選的新圖示
                refreshIconButton()      // 更新按鈕上的顯示
            }
        }

        // 建立對話框
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingCategory == null) "新增分類" else "編輯${type}分類")
            .setView(dialogView)
            .setPositiveButton("儲存", null)  // 先設 null，等等再覆寫，避免按了直接關閉
            .setNegativeButton("取消", null)
            .create()

        // 當對話框顯示出來時，設定「儲存」按鈕的行為
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // 開啟協程（背景任務），避免卡住畫面
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道（專門做資料庫操作），執行儲存分類
                    val result = withContext(Dispatchers.IO) {
                        repository.upsertCategory(
                            type = type,
                            originalName = existingCategory?.name,  // 原本的名稱（編輯時用）
                            newName = etCategoryName.text.toString(), // 新的名稱
                            icon = selectedIcon                       // 選擇的圖示
                        )
                    }

                    // 如果儲存失敗，顯示錯誤訊息
                    if (!result.success) {
                        Toast.makeText(
                            requireContext(),
                            result.message ?: "分類更新失敗",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // 儲存成功，顯示成功訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: "分類已更新",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 關閉對話框
                    dialog.dismiss()

                    // 執行儲存後的回呼（通常是重新繪製分類列表）
                    onSaved()
                }
            }
        }

        // 顯示對話框
        dialog.show()
    }

    // 顯示圖示選擇器（讓使用者從清單中挑選 emoji）
    private fun showCategoryIconChooser(
        currentIcon: String,               // 目前選中的圖示（用來預先標記哪個被選中）
        onIconSelected: (String) -> Unit   // 選中後要做的事（把選中的 emoji 傳回去）
    ) {
        // 取得所有可選的圖示選項（從 TransactionCategoryCatalog 裡來）
        val options = TransactionCategoryCatalog.iconOptions

        // 把每個選項變成「🍽 餐飲」這樣的顯示文字
        val labels = options.map { "${it.icon}  ${it.label}" }.toTypedArray()

        // 找出目前選中的圖示在清單中的位置（如果找不到就預設選第一個）
        val selectedIndex = options.indexOfFirst { it.icon == currentIcon }.coerceAtLeast(0)

        // 跳出單選清單對話框
        AlertDialog.Builder(requireContext())
            .setTitle("選擇分類 icon")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                // 使用者點選某個選項時，把對應的 emoji 傳回去
                onIconSelected(options[which].icon)
                dialog.dismiss()  // 選完就關閉對話框
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 確認刪除分類（會檢查是否有交易使用這個分類）
    private fun confirmDeleteCategory(
        type: String,
        category: TransactionCategoryDefinition,
        onDeleted: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("刪除分類")
            .setMessage("確定要刪除「${category.icon} ${category.name}」嗎？若已有交易使用此分類，系統會阻止刪除。")
            .setPositiveButton("刪除") { _, _ ->
                // 開啟協程（背景任務）
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道，執行刪除分類
                    val result = withContext(Dispatchers.IO) {
                        repository.deleteCategory(type, category.name)
                    }

                    // 顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "分類已刪除" else "刪除失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 如果刪除成功，執行刪除後的回呼（重新繪製列表）
                    if (result.success) {
                        onDeleted()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 其他設定功能 ====================

    // 顯示編輯帳本名稱的對話框
    private fun showEditBookNameDialog() {
        // 建立一個輸入框
        val input = EditText(requireContext()).apply {
            setText(tvBookName.text)           // 預先填入目前的帳本名稱
            setSelection(text.length)          // 把游標移到最右邊，方便直接修改
            hint = "請輸入帳本名稱"              // 提示文字
            setPadding(40, 24, 40, 24)         // 讓輸入框內文字不要貼邊
        }

        AlertDialog.Builder(requireContext())
            .setTitle("編輯帳本名稱")
            .setView(input)
            .setPositiveButton("儲存") { _, _ ->
                // 開啟協程（背景任務）
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道，更新帳本名稱
                    val result = withContext(Dispatchers.IO) {
                        repository.updateBookName(input.text.toString())
                    }

                    // 顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳本名稱已更新" else "更新失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 如果更新成功，刷新整個設定頁面
                    if (result.success) {
                        refreshSettings()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 登出目前帳號
    // 白話：把使用者登出，同時清除手機上的本機資料（下次登入會從雲端重新下載）
    private fun signOutCurrentAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle("登出帳號")
            .setMessage("登出後會清除目前裝置上的本機資料，之後可再透過 Google 重新登入找回雲端備份。")
            .setPositiveButton("登出") { _, _ ->
                // 開啟協程（背景任務）
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道，執行登出並開始匿名 session
                    val result = withContext(Dispatchers.IO) {
                        repository.signOutAndStartAnonymousSession()
                    }

                    // 顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "已登出" else "登出失敗",
                        Toast.LENGTH_LONG
                    ).show()

                    // 刷新設定頁面
                    refreshSettings()

                    // 回到首頁（讓使用者看到空白的記帳畫面）
                    (requireActivity() as? MainActivity)?.showHomeFragment()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 確認清除所有資料（危險操作，需要使用者二次確認）
    private fun confirmClearAllData() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除所有記帳資料")
            .setMessage("這會清除目前裝置上的所有交易、帳戶與預算資料。若尚未備份到雲端，資料將無法復原。確定要繼續嗎？")
            .setPositiveButton("確認清除") { _, _ ->
                // 開啟協程（背景任務）
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道，清除所有資料
                    withContext(Dispatchers.IO) {
                        repository.clearAllData()
                    }

                    // 顯示結果訊息
                    Toast.makeText(requireContext(), "已清除所有資料", Toast.LENGTH_SHORT).show()

                    // 刷新設定頁面
                    refreshSettings()

                    // 回到首頁（讓使用者看到空白的畫面）
                    (requireActivity() as? MainActivity)?.showHomeFragment()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 開始匯出備份（讓使用者選擇檔案名稱和位置）
    private fun startExportBackup() {
        // 產生預設的檔案名稱，例如「cococoin-backup-20260406-1430.json」
        val defaultName = "cococoin-backup-${
            android.text.format.DateFormat.format(
                "yyyyMMdd-HHmm",
                System.currentTimeMillis()
            )
        }.json"
        // 啟動檔案選擇器
        exportBackupLauncher.launch(defaultName)
    }

    // 實際執行匯出備份（把資料寫到指定的 URI） ：
    // 把資料庫的內容轉成 JSON 格式，然後寫到使用者選的那個檔案裡
    private fun exportBackupToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                // runCatching 像一個防護罩，如果有錯誤可以抓下來不讓 App 當機
                runCatching {
                    // 從資料庫讀取所有資料並轉成 JSON 字串
                    val json = repository.exportBackupJson()

                    // 打開使用者選的檔案，準備寫入
                    val outputStream = requireContext().contentResolver.openOutputStream(uri)
                        ?: error("無法建立備份檔")

                    // 把 JSON 字串寫入檔案
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(json)
                    }

                    // 成功就回傳「成功」的結果
                    OperationResult.ok("備份檔已匯出")
                }.getOrElse { exception ->
                    // 失敗就回傳「失敗」的結果，附上錯誤訊息
                    OperationResult.fail(exception.message ?: "匯出失敗")
                }
            }

            // 顯示結果訊息（成功或失敗）
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "匯出成功" else "匯出失敗",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 設定自動本機備份的資料夾
    private fun configureAutoBackupFolder(uri: Uri) {
        val resolver = requireContext().contentResolver
        // 設定要取得的權限：讀取和寫入
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        // 請求永久存取這個資料夾的權限（這樣 App 關掉再開還是能用）
        runCatching {
            resolver.takePersistableUriPermission(uri, flags)
        }

        // 開啟協程（背景任務）
        viewLifecycleOwner.lifecycleScope.launch {
            // 切換到 IO 跑道，儲存使用者的選擇
            val result = withContext(Dispatchers.IO) {
                repository.configureAutoLocalBackupFolder(uri)
            }

            // 顯示結果訊息
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "已啟用自動本機備份" else "設定失敗",
                Toast.LENGTH_SHORT
            ).show()

            // 刷新設定頁面
            refreshSettings()
        }
    }

    // 開始匯出 CSV 報表
    private fun startExportCsv() {
        // 產生預設的檔案名稱，例如「cococoin-transactions-20260406-1430.csv」
        val defaultName = "cococoin-transactions-${
            android.text.format.DateFormat.format(
                "yyyyMMdd-HHmm",
                System.currentTimeMillis()
            )
        }.csv"
        // 啟動檔案選擇器
        exportCsvLauncher.launch(defaultName)
    }

    // 實際執行匯出 CSV ：
    // 把交易紀錄轉成 CSV 格式（用逗號分隔的表格），然後寫到使用者選的檔案裡
    private fun exportCsvToUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 從資料庫讀取交易紀錄並轉成 CSV 格式
                    val csv = repository.exportTransactionsCsv()

                    // 打開使用者選的檔案，準備寫入（使用 UTF-8 編碼，支援中文）
                    val outputStream = requireContext().contentResolver.openOutputStream(uri)
                        ?: error("無法建立 CSV 檔案")

                    // 把 CSV 字串寫入檔案
                    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(csv)
                    }

                    // 成功就回傳「成功」的結果
                    OperationResult.ok("CSV 報表已匯出")
                }.getOrElse { exception ->
                    // 失敗就回傳「失敗」的結果
                    OperationResult.fail(exception.message ?: "CSV 匯出失敗")
                }
            }

            // 顯示結果訊息
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "CSV 匯出成功" else "CSV 匯出失敗",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 停用自動本機備份
    private fun disableAutoBackup() {
        AlertDialog.Builder(requireContext())
            .setTitle("停用自動本機備份")
            .setMessage("停用後將不再自動更新備份檔，但已經匯出的檔案不會被刪除。")
            .setPositiveButton("停用") { _, _ ->
                // 開啟協程（背景任務）
                viewLifecycleOwner.lifecycleScope.launch {
                    // 切換到 IO 跑道，停用自動備份
                    val result = withContext(Dispatchers.IO) {
                        repository.disableAutoLocalBackup()
                    }

                    // 顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "已停用" else "停用失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 刷新設定頁面
                    refreshSettings()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 確認匯入備份（會覆蓋現有資料，需要確認）
    private fun confirmImportBackup() {
        AlertDialog.Builder(requireContext())
            .setTitle("匯入備份檔")
            .setMessage("匯入後會以備份檔內容覆蓋目前裝置上的交易、帳戶與預算資料。確定要繼續嗎？")
            .setPositiveButton("選擇檔案") { _, _ ->
                // 啟動檔案選擇器，讓使用者選要匯入的 JSON 檔案
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 實際執行匯入備份（從 JSON 檔案讀取資料並寫入資料庫）
    private fun importBackupFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 讀取使用者選的檔案內容
                    val json =
                        requireContext().contentResolver.openInputStream(uri)?.bufferedReader()
                            ?.use { reader ->
                                reader.readText()
                            } ?: error("無法讀取備份檔")

                    // 把 JSON 資料匯入資料庫（會覆蓋現有資料）
                    repository.importBackupJson(json)
                }.getOrElse { exception ->
                    OperationResult.fail(exception.message ?: "匯入失敗")
                }
            }

            // 顯示結果訊息
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "匯入成功" else "匯入失敗",
                Toast.LENGTH_LONG
            ).show()

            // 如果匯入成功，刷新設定頁面並回到首頁
            if (result.success) {
                refreshSettings()
                (requireActivity() as? MainActivity)?.showHomeFragment()
            }
        }
    }
}
