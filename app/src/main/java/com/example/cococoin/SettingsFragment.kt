// ============================================================
// ⚙️ 設定頁面 Fragment — 白話文：記帳 App 的「控制中心」或「總部設定室」
// ============================================================
//
// 情境劇：想像你打開一個 App，點進「設定」頁面，你會看到：
//   - 📝 帳本名稱（可以改名字，例如「小明的記帳本」）
//   - ☁️ 雲端同步狀態（上次備份是什麼時候？）
//   - 🔐 帳號管理（用 Google 登入、綁定信箱、登出）
//   - 🏷️ 分類管理（新增/編輯/刪除支出和收入的分類）
//   - 💾 資料備份（手動備份到雲端、匯出/匯入 JSON 檔、匯出 CSV 報表）
//   - 🗑️ 清除所有資料（核彈按鈕，小心使用）
//
// 這個 SettingsFragment 就是負責把上面所有功能串起來的「總指揮」！
// 它繼承 Fragment，表示它是一個可以顯示在螢幕上的「畫面片段」
// ============================================================
package com.example.cococoin

import android.app.AlertDialog          // 彈出式對話框（那種會跳出來問你「確定嗎？」的小視窗）
import android.graphics.Color           // 調色盤（負責處理文字、背景的五顏六色）
import android.net.Uri                  // 檔案的地址（告訴手機檔案在哪裡的門牌號碼）
import android.os.Bundle                // 資料小提箱（頁面轉換時用來裝東西帶過去的包裹）
import android.util.Patterns            // 格式檢查員（檢查你有沒有把 Email 寫錯成電話號碼）
import android.view.View                // 畫面元件的祖先（所有你看得到的按鈕、文字都算它的後代）
import android.widget.Button            // 按鈕（點了會有反應的東西）
import android.widget.EditText          // 打字輸入框（讓使用者可以填寫資料的地方）
import android.widget.LinearLayout      // 線性排版（把元件乖乖排成一排的夾心餅乾）
import android.widget.TextView          // 文字顯示器（單純讓你閱讀的標籤文字）
import android.widget.Toast             // 吐司小提醒（畫面下方彈出來又消失的神祕小黑條）
import androidx.activity.result.contract.ActivityResultContracts  // 檔案選擇器合約（去跟系統討檔案的規矩）
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment   // 碎片頁面（大畫面裡的一個小零件，或是像投影片的一張）
import androidx.lifecycle.lifecycleScope // 生命週期守護神（保證程式在頁面死掉時也跟著停，不會浪費資源）
import kotlinx.coroutines.Dispatchers   // 協程調度官（決定工作要在「快速跑道」還是「背景慢速跑道」執行）
import kotlinx.coroutines.launch        // 啟動任務（像發射火箭一樣開啟一個背景小工程）
import kotlinx.coroutines.withContext   // 切換跑道（像在切換車道，一下去搬重物，一下回來畫畫面）

// ============================================================
// 📱 設定頁面本體
// ============================================================
// 白話文：「嗨！我就是設定頁面！使用者點進來會看到我！」
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    // ============================================================
    // 📌 畫面元件們 — 白話文：設定頁面上所有的「按鈕、文字、卡片」
    // ============================================================
    // 這些都是從 fragment_settings.xml 這個設計圖裡「認領」回來的元件
    // lateinit 表示：「先說有這個東西，等我準備好再給它真正的值」
    // 就像你先說「我有一個書桌」，等到真的進房間再把書桌擺好

    // --- 這些是畫面上的「演員們」（UI 元件變數） ---
    private lateinit var layoutBookNameCard: View      // 帳本名字的那塊小墊子（點了可以改名）
    private lateinit var tvBookName: TextView          // 帳本的大名顯示在這裡
    private lateinit var tvTransactionCount: TextView  // 告訴你至今已經認真記了幾筆帳
    private lateinit var tvLastSync: TextView          // 上次把資料送到雲端躲起來的時間
    private lateinit var tvAutoBackupStatus: TextView  // 檢查你的手機有沒有偷偷在背景備份
    private lateinit var tvCategorySummary: TextView   // 你的支出與收入分類總結（看你分了幾類）
    private lateinit var tvLinkedProviders: TextView   // 看看你的帳號是綁了 Google 還是 Email 呢？

    // --- 這些是跟帳號登入有關的「按鈕大軍」 ---
    private lateinit var layoutLoginEntryRow: View     // 還沒登入時，那排邀請你登入的導覽列
    private lateinit var btnGoogleSignInAction: Button // 點我用 Google 帳號一鍵登入
    private lateinit var btnEmailSignInAction: Button  // 點我用 Email 慢慢登入
    private lateinit var btnPrimaryAccountAction: Button   // 第一主角按鈕（可能是升級或新增）
    private lateinit var btnSecondaryAccountAction: Button // 配角按鈕
    private lateinit var btnEmailAccountAction: Button  // 專門處理綁定 Email 的按鈕
    private lateinit var btnUnlinkGoogleAction: Button  // 跟 Google 鬧翻時用的（解除綁定）
    private lateinit var btnUnlinkEmailAction: Button   // 跟 Email 鬧翻時用的（解除綁定）

    // --- 這些是負責「搬運資料」的按鈕們 ---
    private lateinit var btnBackupNow: Button          // 「緊急撤離」按鈕（現在就手動備份！）
    private lateinit var btnSignOut: Button            // 登出按鈕（掰掰～我要離開了）
    private lateinit var btnManageCategories: Button   // 分類管理員（進去可以加新的分類標籤）
    private lateinit var btnChooseAutoBackupFolder: Button  // 幫備份資料找一個舒服的新家（選資料夾）
    private lateinit var btnDisableAutoBackup: Button  // 懶得備份了，點我關閉自動備份功能
    private lateinit var btnExportData: Button         // 把整份帳本打包帶走（導出 JSON）
    private lateinit var btnImportData: Button         // 把之前的帳本搬回來（匯入備份）
    private lateinit var btnExportCsv: Button          // 把帳本變 Excel 能讀的表格（導出 CSV）
    private lateinit var btnClearAllData: Button       // 「砍掉重練」按鈕（大掃除，慎點！）

    // ============================================================
    // 🏭 資料倉庫與管理器 — 白話文：負責跟資料庫和 Firebase 溝通的「專業部門」
    // ============================================================
    // by lazy 表示：「等到真的有人要用的時候才建立，不是一開始就做」
    // 這樣可以加快 Fragment 的啟動速度，就像「工具用的時候再從工具箱拿，不用一開始全部擺桌上」

    // 資料倉庫：負責所有「資料存取」相關操作（交易、分類、帳戶...）
    // 就像公司的「資料管理部」，你要存錢、查錢都找它
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 帳號連結管理器：負責跟 Firebase 溝通，讓使用者可以登入、綁定帳號
    // 就像公司的「會員管理部」，你要登入、綁定 Google/信箱都找它
    private val authLinkManager by lazy(LazyThreadSafetyMode.NONE) {
        AuthLinkManager(requireContext().applicationContext)
    }

    // ============================================================
    // 📂 檔案選擇器們 — 白話文：跟 Android 檔案總管借來的「選檔工具」
    // ============================================================
    // 這些 registerForActivityResult 就像是「預約好的任務」
    // 使用者點了按鈕 → 跳出檔案總管 → 使用者選檔案 → 執行對應的函式
    // 就像你打電話預約餐廳，時間到了餐廳會 call 你

    // 匯出備份檔的啟動器：讓使用者選擇「要把備份檔存在哪裡」
    // CreateDocument 的意思是「建立一個新檔案」，不是選一個舊的
    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")  // 建立一個 JSON 格式的檔案
    ) { uri ->
        if (uri != null) {
            exportBackupToUri(uri)  // 使用者選好位置了，開始匯出！
        }
    }

    // 匯入備份檔的啟動器：讓使用者選擇「要讀取哪個備份檔」
    // OpenDocument 的意思是「開啟一個既有的檔案」
    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()  // 開啟一個既有文件
    ) { uri ->
        if (uri != null) {
            importBackupFromUri(uri)  // 使用者選好檔案了，開始匯入！
        }
    }

    // 匯出 CSV 報表的啟動器：讓使用者選擇「要把 CSV 存在哪裡」
    // CSV 是 Excel 可以打開的格式，方便使用者用電腦分析花費
    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            exportCsvToUri(uri)
        }
    }

    // 選擇自動備份資料夾的啟動器：讓使用者選擇「一個資料夾」
    // OpenDocumentTree 的意思是「開啟一個資料夾」（不是檔案）
    private val autoBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()  // 開啟一個資料夾
    ) { uri ->
        if (uri != null) {
            configureAutoBackupFolder(uri)
        }
    }

    // ============================================================
    // 🎬 畫面生命週期：onViewCreated — 白話文：畫面準備好了，開始設定按鈕吧！
    // ============================================================
    // 這個函式會在 Fragment 的畫面建立完成後被系統呼叫
    // 就像你走進新家，開始擺設家具、接電器
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- 第一步：認領所有畫面元件（從 XML 設計圖裡找出來）---
        // 就像從 Ikea 的說明書裡找出每個零件的編號
        layoutBookNameCard = view.findViewById(R.id.layoutBookNameCard)
        tvBookName = view.findViewById(R.id.tvBookName)
        tvTransactionCount = view.findViewById(R.id.tvTransactionCount)
        tvLastSync = view.findViewById(R.id.tvLastSync)
        tvAutoBackupStatus = view.findViewById(R.id.tvAutoBackupStatus)
        tvCategorySummary = view.findViewById(R.id.tvCategorySummary)
        tvLinkedProviders = view.findViewById(R.id.tvLinkedProviders)
        layoutLoginEntryRow = view.findViewById(R.id.layoutLoginEntryRow)
        btnGoogleSignInAction = view.findViewById(R.id.btnGoogleSignInAction)
        btnEmailSignInAction = view.findViewById(R.id.btnEmailSignInAction)
        btnPrimaryAccountAction = view.findViewById(R.id.btnPrimaryAccountAction)
        btnSecondaryAccountAction = view.findViewById(R.id.btnSecondaryAccountAction)
        btnEmailAccountAction = view.findViewById(R.id.btnEmailAccountAction)
        btnUnlinkGoogleAction = view.findViewById(R.id.btnUnlinkGoogleAction)
        btnUnlinkEmailAction = view.findViewById(R.id.btnUnlinkEmailAction)
        btnBackupNow = view.findViewById(R.id.btnBackupNow)
        btnSignOut = view.findViewById(R.id.btnSignOut)
        btnManageCategories = view.findViewById(R.id.btnManageCategories)
        btnChooseAutoBackupFolder = view.findViewById(R.id.btnChooseAutoBackupFolder)
        btnDisableAutoBackup = view.findViewById(R.id.btnDisableAutoBackup)
        btnExportData = view.findViewById(R.id.btnExportData)
        btnImportData = view.findViewById(R.id.btnImportData)
        btnExportCsv = view.findViewById(R.id.btnExportCsv)
        btnClearAllData = view.findViewById(R.id.btnClearAllData)

        // --- 第二步：設定按鈕的點擊事件（「當使用者點這個，就做這件事」）---
        // 就像在按鈕上貼標籤：「如果你被人按了，就打電話給某某人」

        // 點擊帳本名稱卡片 → 跳出編輯視窗讓使用者改名字
        layoutBookNameCard.setOnClickListener {
            showEditBookNameDialog()
        }

        // 點擊「立即備份」按鈕 → 把本機資料上傳到雲端
        btnBackupNow.setOnClickListener {
            backupNow()
        }

        // 點擊「管理分類」按鈕 → 開啟分類管理對話框
        btnManageCategories.setOnClickListener {
            showCategoryManagerDialog()
        }

        // 點擊「登出」按鈕 → 登出目前帳號
        btnSignOut.setOnClickListener {
            signOutCurrentAccount()
        }

        // --- 第三步：等待資料庫初始化完成，然後刷新畫面 ---
        // ensureInitialized 確保資料庫準備好了才執行後面的動作
        // 就像等廚房準備好才開始煮菜
        repository.ensureInitialized {
            refreshSettings()  // 把最新的資料顯示到畫面上
        }

        // 點擊「清除所有資料」→ 跳出確認視窗（怕使用者誤按核彈按鈕）
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

    // ============================================================
    // 🔄 畫面生命週期：onResume — 白話文：使用者回到這個頁面時
    // ============================================================
    // onResume 會在畫面「即將顯示給使用者看」的時候被呼叫
    // 每次從其他頁面返回設定頁，都要重新讀取資料，確保顯示的是最新的
    // 就像你每次回到座位，都會重新看一下桌上東西有沒有被人動過
    override fun onResume() {
        super.onResume()
        refreshSettings()  // 重新讀取資料並更新畫面
    }

    // ============================================================
    // 🛡️ 安全啟動協程 — 白話文：只有在畫面還活著的時候才執行背景任務
    // ============================================================
    // 為什麼需要這個？因為背景任務可能跑很久（例如讀取資料庫、上傳檔案）
    // 如果使用者在這期間關閉了畫面，任務結束後要更新 UI 時就會 crash
    // （因為畫面已經不見了，就像你人已經離開房間，還要調整房間裡的電視一樣荒謬）
    //
    // 這個函式就像一個守門員，檢查「畫面還在嗎？在的話才執行」
    private fun launchWhenViewActive(block: suspend () -> Unit) {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            block()
        }
    }

    // ============================================================
    // 📊 刷新設定頁面 — 白話文：從資料庫讀取最新資料，然後顯示到畫面上
    // ============================================================
    // 這是整個設定頁面的「大腦中樞」！
    // 所有要顯示在畫面左上角的數字、右下角的狀態，都是從這裡讀取和更新的
    private fun refreshSettings() {
        // 啟動一個協程（背景任務），避免卡住畫面
        // 就像叫一個助理去跑腿，你自己繼續做別的事
        launchWhenViewActive {

            // 注意：withContext(Dispatchers.IO) 包起來的程式碼會跑到「背景執行緒」
            // 為什麼？因為讀取資料庫可能會花時間，不能卡住畫面（讓使用者覺得卡卡的）
            // IO 是 Input/Output 的縮寫，代表「輸入輸出」相關的慢速操作

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

            // 把讀到的資料顯示到畫面上（這些操作要在「主執行緒」做，因為要更新 UI）
            // 主執行緒就像「畫家」，只有他能碰畫布；背景執行緒是「助手」，只能準備顏料
            tvBookName.text = bookName
            tvTransactionCount.text = "${count} 筆"
            bindSyncStatus(syncStatus)              // 顯示同步狀態
            bindAuthStatus(authLinkStatus)          // 顯示帳號綁定狀態
            bindAutoBackupStatus(autoBackupStatus)  // 顯示自動備份狀態
            bindCategorySummary(categories)         // 顯示分類數量統計
        }
    }

    // ============================================================
    // 🏷️ 顯示分類統計 — 白話文：「支出 10 種 · 收入 7 種」
    // ============================================================
    // 這個函式很簡單：數一數支出和收入各有幾個分類，然後組成一串文字顯示出來
    private fun bindCategorySummary(categories: List<TransactionCategoryDefinition>) {
        // count { 條件 } 的意思是：數一數有多少個分類符合這個條件
        val expenseCount = categories.count { it.type == TransactionCategoryCatalog.TYPE_EXPENSE }
        val incomeCount = categories.count { it.type == TransactionCategoryCatalog.TYPE_INCOME }
        // 組裝文字並顯示，中間用「 · 」分隔（一個漂亮的小圓點）
        tvCategorySummary.text = "支出 $expenseCount 種 · 收入 $incomeCount 種"
    }

    // ============================================================
    // 💾 顯示自動本機備份狀態 — 白話文：「已啟用 · 備份資料夾名稱 · 2025/01/01 12:00」
    // ============================================================
    // 自動本機備份：App 會自動把資料備份到手機的一個資料夾裡
    // 這裡負責把備份狀態顯示成一串易讀的文字
    private fun bindAutoBackupStatus(status: AutoLocalBackupStatus) {
        // buildString 是一個 Kotlin 的懶人工具，讓你不用一直寫 + 來連接字串
        // 就像用積木組裝文字，可以一直 append（追加）
        tvAutoBackupStatus.text = buildString {
            // 先寫「已啟用」或「尚未設定」
            append(if (status.isConfigured) "已啟用" else "尚未設定")

            // 如果有設定資料夾名稱，就加上「 · 資料夾名稱」
            // ?.let 的意思是：「如果不為 null，就執行裡面的程式碼」
            status.folderName?.let {
                append(" · ")
                append(it)
            }

            // 如果有上次備份時間，就加上「 · 2025/01/01 12:00」
            status.lastBackupAt?.let {
                append(" · ")
                // DateFormat.format 可以把毫秒數轉成人類看得懂的時間格式
                append(android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", it))
            }
        }

        // 如果自動備份有開啟，就顯示「停用」按鈕；否則隱藏它
        // GONE 表示完全隱藏且不佔空間，INVISIBLE 則是隱藏但留著空白位置
        btnDisableAutoBackup.visibility = if (status.isConfigured) View.VISIBLE else View.GONE
    }

    // ============================================================
    // ☁️ 顯示雲端同步狀態 — 白話文：上次同步時間或「無同步資料」
    // ============================================================
    // 雲端同步：把資料備份到 Firebase 雲端，換手機也能找回
    // 這裡決定要顯示「2025/01/01 12:00」還是「無同步資料」還是「--」
    private fun bindSyncStatus(syncStatus: SyncStatus) {
        tvLastSync.text = when {
            // 情況 1：同步失敗過（例如沒網路）→ 顯示「無同步資料」
            // 注意：lastSyncSuccessful 是 Boolean? 型態，可以和 false 直接比較
            syncStatus.lastSyncSuccessful == false -> "無同步資料"

            // 情況 2：有同步時間 → 把毫秒轉成人類看得懂的格式顯示
            syncStatus.lastSyncAt != null -> android.text.format.DateFormat
                .format("yyyy/MM/dd HH:mm", syncStatus.lastSyncAt)
                .toString()

            // 情況 3：其他（從未同步過）→ 顯示「--」
            else -> "--"
        }
    }

    // ============================================================
    // 🔐 顯示帳號綁定狀態 — 白話文：顯示已綁定的登入方式
    // ============================================================
    // 這個函式負責顯示「你已經綁了哪幾個登入方式」（例如 Email、Google）
    // 以及根據綁定狀態調整顯示樣式
    private fun bindAuthStatus(status: AuthLinkStatus) {
        // 建立一個列表，收集已綁定的登入方式
        // buildList 是 Kotlin 的懶人工具，幫你建立一個 List 並自動回傳
        val providers = buildList {
            if (status.hasEmailProvider) add("Email")   // 有綁信箱就加入清單
            if (status.hasGoogleProvider) add("Google")  // 有綁 Google 就加入清單
        }

        // 顯示綁定方式，如果都沒有就顯示「尚未綁定任何登入方式」
        // joinToString 會把清單變成「Email、Google」這樣的字串
        tvLinkedProviders.text =
            if (providers.isEmpty()) "尚未綁定任何登入方式" else providers.joinToString("、")

        // 根據有沒有綁定，套用不同的背景樣式和文字顏色
        applyBadgeStyle(
            textView = tvLinkedProviders,
            backgroundRes = if (providers.isEmpty()) {
                R.drawable.bg_settings_badge_neutral  // 未綁定：灰色/中性背景
            } else {
                R.drawable.bg_settings_badge_soft     // 已綁定：柔和彩色背景
            },
            textColor = if (providers.isEmpty()) "#7A6F68" else "#6F665F"
        )

        // 根據登入狀態決定顯示哪些按鈕（升級、登入、備份、登出）
        bindAccountActions(status)
    }

    // ============================================================
    // 🎨 套用徽章樣式 — 白話文：改變文字框的背景和文字顏色
    // ============================================================
    // 「徽章」就是那種有圓角的、像貼紙一樣的小標籤
    // 這個函式就是負責幫那個小標籤換衣服（背景和文字顏色）
    private fun applyBadgeStyle(textView: TextView, backgroundRes: Int, textColor: String) {
        textView.setBackgroundResource(backgroundRes)  // 設定背景圖片（從資源檔載入）
        textView.setTextColor(Color.parseColor(textColor))  // 設定文字顏色（從顏色碼轉換）
    }

    // ============================================================
    // 🎛️ 根據登入狀態決定顯示哪些按鈕 — 白話文：帳號狀態的「消防隊」+「控制台」
    // ============================================================
    // 這個函式是整個設定頁面最複雜的邏輯之一！
    // 它根據三種條件：
    //   1. Firebase 有沒有準備好？
    //   2. 使用者有沒有登入？
    //   3. 是不是匿名帳號（訪客模式）？
    //
    // 來決定要顯示哪些按鈕組合。就像一個聰明的櫃檯人員，根據你的身分給你不同的服務選項：
    //   - 路人甲（未登入）→ 給你「登入」按鈕
    //   - 訪客（匿名）→ 給你「升級成正式會員」的按鈕
    //   - VIP會員（完整登入）→ 給你「備份」、「新增登入方式」、「登出」等完整功能
    private fun bindAccountActions(status: AuthLinkStatus) {
        // --- 第一步：把所有按鈕都先藏起來（「重設」動作）---
        // 為什麼要先全部隱藏？因為等一下要根據情況「逐一打開」需要的按鈕
        // 就像你要重新布置房間，先把所有東西都收起來，再拿出需要的傢俱
        layoutLoginEntryRow.visibility = View.GONE      // 雙入口列（Google + 信箱登入）
        btnPrimaryAccountAction.visibility = View.GONE  // 主要動作按鈕（綁定 Google/新增 Google）
        btnSecondaryAccountAction.visibility = View.GONE // 次要動作按鈕（登入既有帳號找回資料）
        btnEmailAccountAction.visibility = View.GONE    // 信箱相關按鈕（綁定信箱）
        btnUnlinkGoogleAction.visibility = View.GONE    // 解除綁定 Google
        btnUnlinkEmailAction.visibility = View.GONE     // 解除綁定信箱
        btnBackupNow.visibility = View.GONE             // 立即備份按鈕
        btnSignOut.visibility = View.GONE               // 登出按鈕
        setButtonStartIcon(btnPrimaryAccountAction, null) // 清除按鈕左邊的圖示

        // --- 第二步：根據不同情況，顯示不同的按鈕組合 ---
        // when 就像是一個多岔路的選擇題，根據條件執行對應的程式碼區塊
        when {
            // ============================================================
            // 情況 1：Firebase 還沒設定好 → 什麼按鈕都不顯示
            // ============================================================
            // 白話文：雲端服務的大門還沒開，什麼登入、備份都不能做
            // 通常發生在 App 剛啟動，Firebase 還在初始化時
            !status.isFirebaseReady -> {
                // 全部保持隱藏（上面已經隱藏了，這邊只是再確認）
                layoutLoginEntryRow.visibility = View.GONE
                btnPrimaryAccountAction.visibility = View.GONE
                btnSecondaryAccountAction.visibility = View.GONE
                btnEmailAccountAction.visibility = View.GONE
                btnUnlinkGoogleAction.visibility = View.GONE
                btnUnlinkEmailAction.visibility = View.GONE
            }

            // ============================================================
            // 情況 2：使用者還沒登入 → 顯示「Google 登入」和「信箱登入」按鈕
            // ============================================================
            // 白話文：你是路人甲，還沒登入過。給你兩個登入入口：
            //   - 用 Google 帳號登入
            //   - 用 Email + 密碼登入
            !status.isSignedIn -> {
                layoutLoginEntryRow.visibility = View.VISIBLE  // 顯示雙入口列

                // 設定 Google 登入按鈕的文字和點擊行為
                btnGoogleSignInAction.text = "Google 登入"
                btnGoogleSignInAction.setOnClickListener {
                    signInWithGoogle()  // 跳出 Google 選帳號視窗
                }

                // 設定信箱登入按鈕的文字和點擊行為
                btnEmailSignInAction.text = "信箱登入"
                btnEmailSignInAction.setOnClickListener {
                    showEmailSignInDialog()  // 跳出輸入 Email/密碼的對話框
                }
            }

            // ============================================================
            // 情況 3：使用者是匿名登入（訪客模式）→ 顯示「升級」按鈕
            // ============================================================
            // 白話文：你現在是「訪客模式」，資料只存在這支手機裡
            //        如果換手機或刪除 App，資料就掰掰了
            //        所以給你「升級」的選項，綁定 Google 或信箱來保留資料！
            status.isAnonymous -> {
                // 按鈕 A：「綁定 Google 帳號」— 把目前的訪客帳號跟 Google 綁在一起
                btnPrimaryAccountAction.visibility = View.VISIBLE
                btnPrimaryAccountAction.text = "綁定 Google 帳號"
                setButtonStartIcon(
                    btnPrimaryAccountAction,
                    R.drawable.ic_login_google
                )  // 放 Google 圖示
                btnPrimaryAccountAction.setOnClickListener {
                    linkGoogle()  // 綁定 Google
                }

                // 按鈕 B：「登入既有 Google 帳號找回資料」— 用已有的帳號登入，覆蓋目前的訪客資料
                // 白話文：你可能之前有用 Google 登入過，雲端有舊資料，想把它們找回來
                btnSecondaryAccountAction.visibility = View.VISIBLE
                btnSecondaryAccountAction.text = "登入既有 Google 帳號找回資料"
                btnSecondaryAccountAction.setOnClickListener {
                    signInWithGoogle()  // 用 Google 登入
                }

                // 按鈕 C：「綁定信箱」— 用 Email + 密碼綁定
                btnEmailAccountAction.visibility = View.VISIBLE
                btnEmailAccountAction.text = "綁定信箱"
                btnEmailAccountAction.setOnClickListener {
                    showBindEmailDialog()  // 跳出輸入 Email/密碼的綁定視窗
                }
            }

            // ============================================================
            // 情況 4：已登入且不是匿名（完整會員）→ 顯示完整功能
            // ============================================================
            // 白話文：你是 VIP 會員！已經綁定至少一種登入方式（Google 或 Email）
            //        給你全部的功能：新增其他登入方式、解除綁定、備份、登出
            else -> {
                // ----- 如果還沒綁定 Google，顯示「新增 Google 登入方式」按鈕 -----
                // 白話文：你已經有綁信箱了，但還沒綁 Google？可以再加一個 Google 登入方式
                if (!status.hasGoogleProvider) {
                    btnPrimaryAccountAction.visibility = View.VISIBLE
                    btnPrimaryAccountAction.text = "新增 Google 登入方式"
                    setButtonStartIcon(btnPrimaryAccountAction, R.drawable.ic_login_google)
                    btnPrimaryAccountAction.setOnClickListener {
                        linkGoogle()  // 綁定 Google
                    }
                }

                // ----- 如果還沒綁定信箱，顯示「綁定信箱」按鈕 -----
                if (!status.hasEmailProvider) {
                    btnEmailAccountAction.visibility = View.VISIBLE
                    btnEmailAccountAction.text = "綁定信箱"
                    btnEmailAccountAction.setOnClickListener {
                        showBindEmailDialog()  // 跳出綁定信箱對話框
                    }
                }

                // ----- 如果有綁定 Google，顯示「解除綁定 Google」按鈕 -----
                // 白話文：你不想用 Google 登入了？可以解除綁定
                if (status.hasGoogleProvider) {
                    btnUnlinkGoogleAction.visibility = View.VISIBLE
                    btnUnlinkGoogleAction.text = "解除綁定 Google"
                    btnUnlinkGoogleAction.setOnClickListener {
                        confirmUnlinkGoogle()  // 跳出確認視窗，確定後解除綁定
                    }
                }

                // ----- 如果有綁定信箱，顯示「解除綁定信箱」按鈕 -----
                if (status.hasEmailProvider) {
                    btnUnlinkEmailAction.visibility = View.VISIBLE
                    btnUnlinkEmailAction.text = "解除綁定信箱"
                    btnUnlinkEmailAction.setOnClickListener {
                        confirmUnlinkEmail()  // 跳出確認視窗，確定後解除綁定
                    }
                }

                // ----- 顯示備份和登出按鈕 -----
                btnBackupNow.visibility = View.VISIBLE
                btnBackupNow.isEnabled = true
                btnSignOut.visibility = View.VISIBLE
                btnSignOut.isEnabled = true
            }
        }

        // ============================================================
        // 🛡️ 額外安全檢查：確保備份按鈕在正確的狀態下顯示
        // ============================================================
        // 在某些邊緣情況下（例如登入流程還沒完全跑完），備份按鈕可能沒出現
        // 這裡再補一刀，確保「Firebase 就緒 + 已登入 + 不是匿名」時，備份按鈕一定出現
        if (status.isFirebaseReady && status.isSignedIn && !status.isAnonymous) {
            btnBackupNow.visibility = View.VISIBLE
            btnBackupNow.isEnabled = true
        }

        // ----- 決定登出按鈕是否顯示（跟上面一樣的條件）-----
        btnSignOut.visibility =
            if (status.isFirebaseReady && status.isSignedIn && !status.isAnonymous) {
                View.VISIBLE
            } else {
                View.GONE
            }
        btnSignOut.isEnabled = status.isFirebaseReady && status.isSignedIn && !status.isAnonymous
    }

    // ============================================================
    // 🔗 綁定 Google 帳號 — 白話文：把目前的記帳資料跟 Google 帳號綁在一起
    // ============================================================
    // 情境劇：
    //   使用者目前是「訪客模式」（資料只存在這支手機）
    //   點擊這個按鈕後，會跳出 Google 選帳號視窗
    //   選完後，訪客帳號就跟 Google 帳號「結婚」了！
    //   以後用同一個 Google 帳號登入，就可以在其他手機找回這些資料
    private fun linkGoogle() {
        launchWhenViewActive {
            // 呼叫 AuthLinkManager 執行綁定（需要 Activity 來顯示 Google 選單）
            val result = authLinkManager.linkGoogle(requireActivity())

            // 顯示結果：成功或失敗
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "Google 綁定成功" else "Google 綁定失敗",
                Toast.LENGTH_SHORT
            ).show()

            refreshSettings()  // 刷新畫面（按鈕會變成「已綁定」的狀態）
        }
    }

    // ============================================================
    // 🚪 用 Google 帳號登入 — 白話文：用 Google 帳號登入，並從雲端找回資料
    // ============================================================
    // 情境劇：
    //   使用者已經有綁定過 Google 帳號（可能有雲端備份）
    //   點擊這個按鈕後，會跳出 Google 選帳號視窗
    //   登入成功後，會自動從雲端下載之前的記帳資料
    private fun signInWithGoogle() {
        launchWhenViewActive {
            // 呼叫 AuthLinkManager 執行 Google 登入
            val result = authLinkManager.signInWithGoogle(requireActivity())

            // 登入失敗就顯示錯誤訊息，不繼續執行
            if (!result.success) {
                Toast.makeText(
                    requireContext(),
                    result.message ?: "Google 登入失敗",
                    Toast.LENGTH_SHORT
                ).show()
                refreshSettings()
                return@launchWhenViewActive
            }

            // 登入成功！從雲端把資料抓回來
            restoreRemoteDataAfterLogin(result.message ?: "Google 登入成功")
        }
    }

    // ============================================================
    // 📧 顯示「綁定信箱」對話框 — 白話文：用 Email + 密碼來綁定帳號
    // ============================================================
    // 情境劇：
    //   使用者不想用 Google，想用傳統的 Email + 密碼來綁定
    //   這個對話框會讓使用者輸入 Email、密碼、確認密碼
    //   綁定成功後，以後就可以用 Email 登入
    private fun showBindEmailDialog() {
        // 載入對話框的設計圖（XML 佈局檔）
        val dialogView = layoutInflater.inflate(R.layout.dialog_bind_email_verification, null)

        // 找到對話框裡的輸入框們
        val etEmail = dialogView.findViewById<EditText>(R.id.etBindEmail)           // Email 輸入框
        val etPassword = dialogView.findViewById<EditText>(R.id.etBindPassword)     // 密碼輸入框
        val etPasswordConfirm =
            dialogView.findViewById<EditText>(R.id.etBindPasswordConfirm) // 確認密碼輸入框
        val tvHint = dialogView.findViewById<TextView>(R.id.tvEmailCodeHint)        // 顯示錯誤訊息的文字

        // 建立對話框（但先不顯示）
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("綁定信箱")
            .setView(dialogView)
            .setPositiveButton("確認綁定", null)  // 先設 null，等等再手動設定點擊行為（避免點了直接關閉）
            .setNegativeButton("取消", null)
            .create()

        // 當對話框「顯示出來」的時候，才設定「確認綁定」按鈕的行為
        // 為什麼要這樣？因為 AlertDialog 的按鈕預設點了就會關閉對話框
        // 我們希望在「綁定失敗」時不要關閉，讓使用者可以修改錯誤後重試
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // 讀取使用者輸入的內容（去掉頭尾空格）
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                val passwordConfirm = etPasswordConfirm.text.toString()

                // 驗證輸入的資料是否有效
                val validationError = validateEmailBindingInputs(email, password, passwordConfirm)

                // 如果有任何錯誤（例如 Email 格式不對、兩次密碼不一致），顯示錯誤訊息並返回
                if (validationError != null) {
                    showDialogError(tvHint, validationError)
                    return@setOnClickListener
                }

                // 驗證通過！清除之前的錯誤訊息
                tvHint.visibility = View.GONE

                // 執行綁定（非同步，需要等待 Firebase 回應）
                authLinkManager.linkEmailPassword(email, password) { result ->
                    if (result.success) {
                        // 綁定成功！關閉對話框，顯示成功訊息，刷新畫面
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "信箱綁定成功", Toast.LENGTH_SHORT).show()
                        refreshSettings()
                    } else {
                        // 綁定失敗！顯示錯誤訊息（對話框保持開啟）
                        showDialogError(tvHint, bindEmailErrorMessage(result.message))
                    }
                }
            }
        }

        // 顯示對話框
        dialog.show()
    }

    // ============================================================
    // 📧 顯示「信箱登入」對話框 — 白話文：用 Email + 密碼登入既有帳號
    // ============================================================
    // 情境劇：
    //   使用者之前已經用 Email 註冊過，現在想用同樣的 Email 登入
    //   這個對話框會讓使用者輸入 Email 和密碼
    //   登入成功後，會自動從雲端下載之前的記帳資料
    private fun showEmailSignInDialog() {
        // 載入對話框的設計圖（XML 佈局檔）
        // 這個 layout 裡面有 Email 輸入框、密碼輸入框、錯誤訊息顯示區
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_credentials, null)

        // 找到對話框裡的輸入框們
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)        // Email 輸入框
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)  // 密碼輸入框
        val tvError = dialogView.findViewById<TextView>(R.id.tvRegisterHint) // 顯示錯誤訊息的文字

        // 建立對話框（但先不顯示）
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("登入信箱帳號")
            .setView(dialogView)
            .setPositiveButton("登入", null)  // 先設 null，等等再手動設定點擊行為
            .setNegativeButton("取消", null)
            .create()

        // 當對話框「顯示出來」的時候，才設定「登入」按鈕的行為
        // 為什麼要這樣？因為我們希望在「登入失敗」時不要關閉對話框
        // 讓使用者可以修改 Email 或密碼後重新嘗試
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // 讀取使用者輸入的內容（去掉頭尾空格）
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()

                // ========== 輸入驗證三部曲 ==========
                // 驗證 1：Email 不能是空白
                when {
                    email.isEmpty() -> {
                        showDialogError(tvError, "請輸入信箱帳號")
                        return@setOnClickListener  // 停止執行，不繼續登入
                    }
                    // 驗證 2：Email 格式要正確（例如要有 @ 和 .com）
                    !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                        showDialogError(tvError, "信箱格式不正確")
                        return@setOnClickListener
                    }
                    // 驗證 3：密碼不能是空白
                    password.isEmpty() -> {
                        showDialogError(tvError, "請輸入密碼")
                        return@setOnClickListener
                    }
                }

                // 驗證通過！清除之前的錯誤訊息
                tvError.visibility = View.GONE

                // 執行登入（非同步，需要等待 Firebase 回應）
                authLinkManager.signInWithEmailPassword(email, password) { result ->
                    if (!result.success) {
                        // 登入失敗！顯示錯誤訊息（對話框保持開啟）
                        // 注意：不要告訴使用者是「帳號不存在」還是「密碼錯誤」
                        // 這是安全考量！避免被壞人試出哪些 Email 有註冊
                        showDialogError(tvError, "帳號或密碼錯誤")
                        return@signInWithEmailPassword
                    }

                    // 登入成功！關閉對話框
                    dialog.dismiss()
                    // 從雲端下載資料，然後回到首頁
                    restoreRemoteDataAfterLogin("信箱登入成功")
                }
            }
        }

        // 顯示對話框
        dialog.show()
    }

    // ============================================================
    // ✅ 驗證信箱綁定的輸入 — 白話文：檢查使用者輸入的 Email 和密碼是否有效
    // ============================================================
    // 這個函式是一個「守門員」，在送出綁定請求之前先檢查：
    //   1. Email 有沒有填？
    //   2. Email 格式對不對（有沒有 @ 和 .com）？
    //   3. 兩次輸入的密碼一不一樣？
    //
    // 回傳值：
    //   - 如果一切正常 → 回傳 null（表示沒有錯誤）
    //   - 如果有問題 → 回傳錯誤訊息文字（例如「請輸入信箱帳號」）
    private fun validateEmailBindingInputs(
        email: String,
        password: String,
        passwordConfirm: String
    ): String? {
        return when {
            // 檢查 1：Email 不能是空白
            email.isEmpty() -> "請輸入信箱帳號"

            // 檢查 2：Email 格式要正確
            // Patterns.EMAIL_ADDRESS 是 Android 內建的正規表達式
            // 可以檢查 email 是不是像「user@example.com」這種格式
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "信箱格式不正確"

            // 檢查 3：兩次密碼要一致（避免手殘打錯）
            password != passwordConfirm -> "兩次密碼不一致"

            // 全部通過！回傳 null 表示沒有錯誤
            else -> null
        }
    }

    // ============================================================
    // 📝 轉換信箱綁定的錯誤訊息 — 白話文：把 Firebase 的錯誤訊息「翻譯」成人話
    // ============================================================
    // Firebase 回傳的錯誤訊息是英文的，而且可能很技術性
    // 這個函式負責把它們轉換成使用者看得懂的中文
    //
    // 例如：
    //   "This email is already linked to another account" → 「帳號已綁定」
    //   "EMAIL_COLLISION" → 「帳號已綁定」
    private fun bindEmailErrorMessage(message: String?): String {
        return when {
            // 情況 1：訊息裡有「已綁定」三個字（中文）
            message?.contains("已綁定") == true -> "帳號已綁定"

            // 情況 2：訊息裡有「collision」這個英文單字（不分大小寫）
            // collision 意思是「碰撞」，在 Firebase 裡表示這個 Email 已經被其他帳號用過了
            message?.contains("collision", ignoreCase = true) == true -> "帳號已綁定"

            // 情況 3：其他錯誤，顯示原本的訊息，如果原本是 null 就顯示「綁定失敗」
            else -> message ?: "綁定失敗"
        }
    }

    // ============================================================
    // ⚠️ 顯示對話框中的錯誤訊息 — 白話文：在對話框裡顯示紅色的錯誤提示
    // ============================================================
    // 這個函式做了兩件事：
    //   1. 把錯誤訊息文字設定到 TextView 上
    //   2. 讓那個 TextView 變成「可見」（因為它預設可能是隱藏的）
    private fun showDialogError(textView: TextView, message: String) {
        textView.text = message      // 寫入錯誤訊息，例如「請輸入信箱帳號」
        textView.visibility = View.VISIBLE  // 顯示出來（之前可能是 GONE）
    }

    // ============================================================
    // 🖼️ 設定按鈕左邊的圖示 — 白話文：在按鈕文字的左邊貼一個小圖案
    // ============================================================
    // 例如：Google 登入按鈕的左邊放一個 Google 的 G 圖案
    //
    // setCompoundDrawablesRelativeWithIntrinsicBounds 是一個很長的名字
    // 白話文就是：「設定按鈕上下左右四個方向的附加圖案」
    // 我們只設定左邊（start），其他三個方向都設 null
    private fun setButtonStartIcon(button: Button, drawableRes: Int?) {
        // 如果有給圖案資源 ID，就載入那個圖案；否則給 null
        val startDrawable = drawableRes?.let { ContextCompat.getDrawable(requireContext(), it) }
        // 四個參數分別是：左、上、右、下
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(startDrawable, null, null, null)
    }

    // ============================================================
    // 🔓 確認解除綁定 Google — 白話文：先問使用者「你確定嗎？」，確定後才解除
    // ============================================================
    // 解除綁定是一個「危險動作」，所以要跳出確認視窗
    // 避免使用者不小心按到，失去一種登入方式
    private fun confirmUnlinkGoogle() {
        confirmUnlinkProvider(
            title = "解除綁定 Google",
            message = "解除後將不能再用 Google 登入這個帳號。若這是最後一種登入方式，之後可能無法登入找回此帳號。",
            action = { onComplete ->
                authLinkManager.unlinkGoogleProvider(onComplete)  // 執行真正的解除綁定
            }
        )
    }

    // ============================================================
    // 🔓 確認解除綁定信箱 — 白話文：先問使用者「你確定嗎？」，確定後才解除
    // ============================================================
    private fun confirmUnlinkEmail() {
        confirmUnlinkProvider(
            title = "解除綁定信箱",
            message = "解除後將不能再用信箱密碼登入這個帳號。若這是最後一種登入方式，之後可能無法登入找回此帳號。",
            action = { onComplete ->
                authLinkManager.unlinkEmailProvider(onComplete)  // 執行真正的解除綁定
            }
        )
    }

    // ============================================================
    // 🔓 確認解除綁定的通用模板 — 白話文：解除綁定的「標準作業流程」
    // ============================================================
    // 這是一個「模板函式」，Google 和 Email 的解除綁定都用同一個流程：
    //   1. 跳出確認視窗，顯示標題和警告訊息
    //   2. 使用者按「解除綁定」後，執行 action 函式
    //   3. action 執行完後，顯示結果訊息
    //   4. 刷新設定頁面
    private fun confirmUnlinkProvider(
        title: String,
        message: String,
        action: ((OperationResult) -> Unit) -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)                       // 對話框標題
            .setMessage(message)                   // 警告訊息（提醒使用者風險）
            .setPositiveButton("解除綁定") { _, _ ->
                // 使用者確認了！執行真正的解除綁定
                action { result ->
                    // 解除綁定完成後，顯示結果
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "已解除綁定" else "解除綁定失敗",
                        Toast.LENGTH_LONG
                    ).show()

                    refreshSettings()  // 刷新畫面（按鈕狀態會改變）
                }
            }
            .setNegativeButton("取消", null)  // 取消按鈕：什麼都不做
            .show()
    }

    // ============================================================
    // ☁️ 登入成功後，從雲端還原資料到本機 — 白話文：把雲端備份的資料抓下來
    // ============================================================
    // 情境劇：
    //   使用者用 Google 或 Email 登入成功
    //   但手機裡可能沒有資料（或是舊的訪客資料）
    //   這個函式會從 Firebase 雲端把之前備份的資料下載回來
    //   讓使用者換手機也不會失去記帳記錄！
    private fun restoreRemoteDataAfterLogin(loginMessage: String) {
        launchWhenViewActive {
            // 切換到背景執行緒執行（避免卡畫面）
            val restoreResult = withContext(Dispatchers.IO) {
                repository.restoreFromCloudAfterLogin()  // 從雲端還原資料
            }

            // 組裝要顯示的訊息
            val toastMessage = when {
                restoreResult.success -> "$loginMessage，${restoreResult.message}"  // 成功：例如「信箱登入成功，已從雲端還原 128 筆交易」
                else -> "$loginMessage，${restoreResult.message ?: "雲端還原失敗"}"   // 失敗：例如「信箱登入成功，雲端還原失敗」
            }

            // 顯示結果訊息
            Toast.makeText(
                requireContext(),
                toastMessage,
                Toast.LENGTH_LONG
            ).show()

            // 刷新設定頁面（顯示最新的登入狀態和同步狀態）
            refreshSettings()

            // 回到首頁（讓使用者看到還原後的記帳資料）
            // as? MainActivity 是一種「安全轉型」，如果不是 MainActivity 就不執行
            (requireActivity() as? MainActivity)?.showHomeFragment()
        }
    }

    // ============================================================
    // ☁️ 立即備份到雲端 — 白話文：把本機資料上傳到雲端備份
    // ============================================================
    // 情境劇：
    //   使用者記了好幾筆帳，怕手機壞掉資料不見
    //   點擊「立即備份」按鈕，強制把資料上傳到雲端
    //   之後換手機或用其他裝置登入，就可以找回這些資料
    private fun backupNow() {
        launchWhenViewActive {
            // 切換到背景執行緒執行（因為要讀取資料庫並上傳網路）
            val result = withContext(Dispatchers.IO) {
                repository.backupNow()  // 執行備份
            }

            // 顯示備份結果
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "備份成功" else "備份失敗",
                Toast.LENGTH_SHORT
            ).show()

            // 刷新設定頁面（更新上次同步時間）
            refreshSettings()
        }
    }

    // ============================================================
    // 🏷️ 分類管理功能 — 白話文：記帳本的「標籤管理員」
    // ============================================================
    // 這個區塊負責讓使用者可以：
    //   1. 新增分類（例如「手搖飲」、「寵物美容」）
    //   2. 編輯分類（把「餐飲」改成「吃貨人生」）
    //   3. 刪除分類（不想要「購物」這個分類了）
    //   4. 選擇圖示（每個分類都可以配一個可愛的 emoji）
    //
    // 支出和收入的分類是分開管理的，因為「買雞排」不會被當成「收入」😂
    // ============================================================

    // ============================================================
    // 📂 顯示分類管理對話框 — 白話文：打開「分類管理員」的辦公室
    // ============================================================
    // 這是分類管理的「總入口」，會顯示一個對話框，裡面有：
    //   - 「支出」和「收入」兩個切換按鈕
    //   - 當前類型的分類列表（每個分類都有編輯和刪除按鈕）
    //   - 「新增支出種類」和「新增收入種類」兩個新增按鈕
    private fun showCategoryManagerDialog(initialType: String = TransactionCategoryCatalog.TYPE_EXPENSE) {
        // 載入 dialog_manage_categories.xml 這個畫面設計圖
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_categories, null)

        // ========== 找到對話框裡的元件們 ==========
        // 切換按鈕：點「支出」只看支出分類，點「收入」只看收入分類
        val btnExpenseCategories = dialogView.findViewById<Button>(R.id.btnExpenseCategories)
        val btnIncomeCategories = dialogView.findViewById<Button>(R.id.btnIncomeCategories)

        // 新增按鈕：直接新增一個新的分類
        val btnAddExpenseCategory = dialogView.findViewById<Button>(R.id.btnAddExpenseCategory)
        val btnAddIncomeCategory = dialogView.findViewById<Button>(R.id.btnAddIncomeCategory)

        // 分類列表的容器：一個直的 LinearLayout，裡面會放很多「分類項目」
        val layoutCategoryList = dialogView.findViewById<LinearLayout>(R.id.layoutCategoryList)

        // 目前正在查看的分類類型（支出 或 收入），預設是「支出」
        var currentType = initialType

        // 建立對話框（先不顯示）
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("管理分類 / icon")  // 標題：管理分類 + 可以選圖示
            .setView(dialogView)
            .setPositiveButton("完成", null)  // 「完成」只是關閉對話框，不做任何儲存動作
            .create()

        // ============================================================
        // 🎨 重新繪製分類列表 — 白話文：把分類清單「畫」到畫面上
        // ============================================================
        // 這是一個內部函式（函式裡面還有函式），每次切換類型、新增、編輯、刪除後都會呼叫
        // 它的工作就是：去資料庫讀取最新的分類清單，然後顯示在畫面上
        fun render() {
            // 使用 launchWhenViewActive 確保畫面還活著（避免使用者關掉後還執行）
            launchWhenViewActive {
                // 從資料庫讀取所有分類（在背景執行緒執行）
                val allCategories = withContext(Dispatchers.IO) {
                    repository.getAllCategories()
                }

                // 只顯示目前選中的類型（支出或收入）
                // 例如 currentType = "支出"，就只顯示「餐飲、交通、購物...」這些支出分類
                val currentCategories = allCategories.filter { it.type == currentType }

                // 更新支出/收入按鈕的樣式（讓使用者知道現在在看哪一類）
                // 就像選取頁籤（Tab），被選中的會高亮顯示
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
                        // 點擊「編輯」按鈕時，開啟編輯對話框
                        showEditCategoryDialog(
                            type = currentType,
                            existingCategory = category,  // 傳入原有的分類資料
                            onSaved = {
                                render()          // 編輯完成後，重新繪製列表
                                refreshSettings() // 更新設定頁面的分類數量
                            }
                        )
                    },
                    onDelete = { category ->
                        // 點擊「刪除」按鈕時，先跳出確認視窗
                        confirmDeleteCategory(
                            type = currentType,
                            category = category,
                            onDeleted = {
                                render()          // 刪除完成後，重新繪製列表
                                refreshSettings() // 更新設定頁面的分類數量
                            }
                        )
                    }
                )
            }
        }

        // ========== 設定按鈕的點擊事件 ==========

        // 點擊「支出」按鈕 → 切換到支出分類，然後重新繪製
        btnExpenseCategories.setOnClickListener {
            currentType = TransactionCategoryCatalog.TYPE_EXPENSE
            render()  // 重新繪製，現在會顯示支出分類
        }

        // 點擊「收入」按鈕 → 切換到收入分類，然後重新繪製
        btnIncomeCategories.setOnClickListener {
            currentType = TransactionCategoryCatalog.TYPE_INCOME
            render()
        }

        // 點擊「新增支出種類」→ 直接開啟新增分類對話框（預設類型是支出）
        btnAddExpenseCategory.setOnClickListener {
            showEditCategoryDialog(
                type = TransactionCategoryCatalog.TYPE_EXPENSE,
                existingCategory = null,  // null 表示這是「新增」模式，不是「編輯」
                onSaved = {
                    currentType = TransactionCategoryCatalog.TYPE_EXPENSE  // 確保留在支出頁面
                    render()          // 重新繪製列表（新增的會出現）
                    refreshSettings() // 更新設定頁面的分類數量
                }
            )
        }

        // 點擊「新增收入種類」→ 直接開啟新增分類對話框（預設類型是收入）
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

        // 顯示對話框（讓使用者看到）
        dialog.show()
    }

    // ============================================================
    // 🎨 更新支出/收入切換按鈕的樣式 — 白話文：讓被選中的按鈕「亮起來」
    // ============================================================
    // 這個函式負責改變兩個按鈕的外觀：
    //   - 被選中的按鈕：深色背景 + 白色文字（高亮）
    //   - 沒被選中的按鈕：淺色背景 + 灰色文字（普通）
    //
    // 就像頁籤（Tab），讓使用者知道現在正在看「支出」還是「收入」
    private fun updateCategoryToggleStyles(
        currentType: String,
        expenseButton: Button,
        incomeButton: Button
    ) {
        if (currentType == TransactionCategoryCatalog.TYPE_EXPENSE) {
            // ========== 支出被選中的樣子 ==========
            // 支出按鈕：高亮（深色背景 + 白色文字）
            expenseButton.setBackgroundResource(R.drawable.bg_category_button_expense_selected)
            expenseButton.setTextColor(Color.parseColor("#FFFDFB"))

            // 收入按鈕：普通（淺色背景 + 灰色文字）
            incomeButton.setBackgroundResource(R.drawable.bg_category_button_expense_unselected)
            incomeButton.setTextColor(Color.parseColor("#906C6A"))
        } else {
            // ========== 收入被選中的樣子 ==========
            // 收入按鈕：高亮（深色背景 + 白色文字）
            incomeButton.setBackgroundResource(R.drawable.bg_category_button_expense_selected)
            incomeButton.setTextColor(Color.parseColor("#FFFDFB"))

            // 支出按鈕：普通（淺色背景 + 灰色文字）
            expenseButton.setBackgroundResource(R.drawable.bg_category_button_expense_unselected)
            expenseButton.setTextColor(Color.parseColor("#906C6A"))
        }
    }

    // ============================================================
    // 📋 繪製分類列表 — 白話文：把分類一個一個「印」到畫面上
    // ============================================================
    // 這個函式會把傳進來的分類清單，轉換成畫面上可以看到的「分類項目」
    // 每個分類項目包含：
    //   - 圖示（emoji，例如 🍽）
    //   - 名稱（例如「餐飲」）
    //   - 編輯按鈕（鉛筆圖案）
    //   - 刪除按鈕（垃圾桶圖案）
    private fun renderCategoryList(
        container: LinearLayout,                     // 要放列表的容器（像是一個空的展示架）
        categories: List<TransactionCategoryDefinition>,  // 分類清單（要展示的商品）
        onEdit: (TransactionCategoryDefinition) -> Unit,   // 點擊編輯時要做的事
        onDelete: (TransactionCategoryDefinition) -> Unit  // 點擊刪除時要做的事
    ) {
        // 先清空容器裡舊的內容（避免重複顯示）
        // 就像把展示架上的舊商品先清掉，再放新的
        container.removeAllViews()

        // 一個一個處理每個分類
        categories.forEach { category ->
            // 載入 item_manage_category.xml 這個「分類項目」的設計圖
            // 這個 layout 裡面有：圖示、名稱、編輯按鈕、刪除按鈕
            val itemView = layoutInflater.inflate(R.layout.item_manage_category, container, false)

            // 找到這個項目裡面的元件們
            val tvManageCategoryIcon =
                itemView.findViewById<TextView>(R.id.tvManageCategoryIcon)  // 圖示
            val tvManageCategoryName =
                itemView.findViewById<TextView>(R.id.tvManageCategoryName)  // 名稱
            val btnEditCategory = itemView.findViewById<Button>(R.id.btnEditCategory)    // 編輯按鈕
            val btnDeleteCategory = itemView.findViewById<Button>(R.id.btnDeleteCategory) // 刪除按鈕

            // 把分類的資料填進去
            tvManageCategoryIcon.text = category.icon   // 顯示「🍽」
            tvManageCategoryName.text = category.name   // 顯示「餐飲」

            // 設定按鈕的點擊事件
            // 點擊編輯按鈕時，執行 onEdit（外面傳進來的函式）
            btnEditCategory.setOnClickListener { onEdit(category) }
            // 點擊刪除按鈕時，執行 onDelete（外面傳進來的函式）
            btnDeleteCategory.setOnClickListener { onDelete(category) }

            // 把這個做好的項目加到容器（展示架）裡
            container.addView(itemView)
        }
    }

    // ============================================================
    // ✏️ 顯示編輯/新增分類的對話框 — 白話文：分類的「編輯器」或「新增表單」
    // ============================================================
    // 這個對話框有兩種模式：
    //   1. 新增模式：existingCategory == null → 建立一個全新的分類
    //   2. 編輯模式：existingCategory 有值 → 修改原有的分類
    //
    // 使用者可以：
    //   - 點擊「選擇 icon」按鈕來挑選 emoji
    //   - 在輸入框裡輸入分類名稱（例如「手搖飲」）
    //   - 按「儲存」完成
    private fun showEditCategoryDialog(
        type: String,                          // 支出 或 收入
        existingCategory: TransactionCategoryDefinition?,  // 如果是編輯就有值，新增就是 null
        onSaved: () -> Unit                    // 儲存成功後要做的事（重新繪製列表）
    ) {
        // 載入 dialog_edit_category.xml 這個畫面設計圖
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_category, null)

        // ========== 找到對話框裡的元件 ==========
        // 選擇圖示的按鈕：點下去會跳出 emoji 選單
        val btnPickCategoryIcon = dialogView.findViewById<Button>(R.id.btnPickCategoryIcon)
        // 分類名稱輸入框：讓使用者輸入「餐飲」、「交通」之類的文字
        val etCategoryName = dialogView.findViewById<EditText>(R.id.etCategoryName)

        // ========== 決定一開始要顯示哪個圖示 ==========
        // 如果是編輯模式（existingCategory != null），就用原本的圖示
        // 如果是新增模式（existingCategory == null），就用預設圖示（支出用🧾，收入用💰）
        var selectedIcon = existingCategory?.icon ?: TransactionCategoryCatalog.fallbackIcon(type)

        // 更新圖示按鈕上的文字（顯示目前選擇的 emoji + 「選擇 icon」）
        // 例如：🍽  選擇 icon
        fun refreshIconButton() {
            btnPickCategoryIcon.text = "$selectedIcon  選擇 icon"
        }

        // 如果是編輯模式，把原有的分類名稱填進輸入框
        etCategoryName.setText(existingCategory?.name.orEmpty())
        // 把游標移到文字的最右邊，方便使用者直接修改（不用再點一次）
        etCategoryName.setSelection(etCategoryName.text.length)

        // 更新按鈕上的圖示顯示
        refreshIconButton()

        // 點擊圖示選擇按鈕 → 開啟圖示選擇器（讓使用者挑選 emoji）
        btnPickCategoryIcon.setOnClickListener {
            showCategoryIconChooser(selectedIcon) { icon ->
                selectedIcon = icon      // 把使用者選的新圖示記起來
                refreshIconButton()      // 更新按鈕上的顯示（變成新圖示）
            }
        }

        // 建立對話框
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (existingCategory == null) "新增分類" else "編輯${type}分類")
            .setView(dialogView)
            .setPositiveButton("儲存", null)  // 先設 null，等一下再手動設定（避免點了直接關閉）
            .setNegativeButton("取消", null)
            .create()

        // 當對話框顯示出來時，設定「儲存」按鈕的行為
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // 開啟協程（背景任務），避免卡住畫面
                launchWhenViewActive {
                    // 切換到 IO 跑道（專門做資料庫操作），執行儲存分類
                    val result = withContext(Dispatchers.IO) {
                        repository.upsertCategory(
                            type = type,
                            originalName = existingCategory?.name,  // 原本的名稱（編輯時用，新增時是 null）
                            newName = etCategoryName.text.toString(), // 新的名稱（使用者輸入的）
                            icon = selectedIcon                       // 選擇的圖示
                        )
                    }

                    // 如果儲存失敗，顯示錯誤訊息，並且不關閉對話框
                    if (!result.success) {
                        Toast.makeText(
                            requireContext(),
                            result.message ?: "分類更新失敗",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launchWhenViewActive
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

    // ============================================================
    // 🎨 顯示圖示選擇器 — 白話文：打開 emoji 的「購物車」，讓使用者挑選一個圖示
    // ============================================================
    // 這個函式會跳出一個單選清單對話框，裡面有所有可選的 emoji（例如 🍽、🚗、💰）
    // 使用者點選其中一個後，對話框會關閉，並把選中的 emoji 傳回去
    private fun showCategoryIconChooser(
        currentIcon: String,               // 目前選中的圖示（用來預先標記哪個被選中）
        onIconSelected: (String) -> Unit   // 選中後要做的事（把選中的 emoji 傳回去）
    ) {
        // 取得所有可選的圖示選項（從 TransactionCategoryCatalog 這個「分類目錄」裡來）
        // 這個清單裡面有：🍽 餐飲、🚌 交通、💰 收入... 等等
        val options = TransactionCategoryCatalog.iconOptions

        // 把每個選項變成「🍽 餐飲」這樣的顯示文字（讓使用者一看就懂）
        // toTypedArray() 是把 List 轉換成 Array，因為 setSingleChoiceItems 需要陣列
        val labels = options.map { "${it.icon}  ${it.label}" }.toTypedArray()

        // 找出目前選中的圖示在清單中的位置（例如「🍽」在第 0 個位置）
        // coerceAtLeast(0) 是「確保至少是 0」，如果找不到就選第一個（避免 -1 造成 crash）
        val selectedIndex = options.indexOfFirst { it.icon == currentIcon }.coerceAtLeast(0)

        // 跳出單選清單對話框（就像「選單」一樣，只能選一個）
        AlertDialog.Builder(requireContext())
            .setTitle("選擇分類 icon")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                // 使用者點選某個選項時，把對應的 emoji 傳回去給呼叫的人
                // which 是使用者點選的「索引位置」（0, 1, 2...）
                onIconSelected(options[which].icon)
                dialog.dismiss()  // 選完就關閉對話框，不需要按「確定」
            }
            .setNegativeButton("取消", null)  // 取消按鈕：什麼都不做，直接關閉
            .show()
    }

    // ============================================================
    // 🗑️ 確認刪除分類 — 白話文：先問使用者「你確定嗎？」確定後才刪除
    // ============================================================
    // 刪除分類是一個「危險操作」，因為如果已經有交易用了這個分類
    // 刪除後那些交易的「分類欄位」就會變成空的（幽靈分類）
    //
    // 所以：
    //   1. 先跳出確認視窗問使用者
    //   2. 使用者確定後，才去呼叫 repository 刪除
    //   3. repository 會檢查這個分類有沒有被使用過，有的話會阻止刪除
    private fun confirmDeleteCategory(
        type: String,
        category: TransactionCategoryDefinition,
        onDeleted: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle("刪除分類")
            .setMessage("確定要刪除「${category.icon} ${category.name}」嗎？若已有交易使用此分類，系統會阻止刪除。")
            .setPositiveButton("刪除") { _, _ ->
                // 使用者確認刪除！開啟協程執行真正的刪除
                launchWhenViewActive {
                    // 切換到背景執行緒（資料庫操作）
                    val result = withContext(Dispatchers.IO) {
                        repository.deleteCategory(type, category.name)
                    }

                    // 顯示結果訊息（成功或失敗）
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "分類已刪除" else "刪除失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 如果刪除成功，執行刪除後的回呼（通常是重新繪製分類列表）
                    if (result.success) {
                        onDeleted()
                    }
                }
            }
            .setNegativeButton("取消", null)  // 取消：什麼都不做
            .show()
    }

    // ==================== 其他設定功能 ====================

    // ============================================================
    // 📝 顯示編輯帳本名稱的對話框 — 白話文：讓使用者修改記帳本的「封面標題」
    // ============================================================
    // 情境劇：
    //   使用者一開始的帳本名稱叫「我的帳本」，想改成「小明的記帳本」
    //   點擊帳本名稱卡片後，跳出一個輸入框，修改後按儲存
    private fun showEditBookNameDialog() {
        // 建立一個輸入框（EditText）
        val input = EditText(requireContext()).apply {
            setText(tvBookName.text)           // 預先填入目前的帳本名稱（例如「我的帳本」）
            setSelection(text.length)          // 把游標移到文字的最右邊，方便直接修改
            hint = "請輸入帳本名稱"              // 提示文字（輸入框空白時顯示）
            setPadding(40, 24, 40, 24)         // 讓輸入框內的文字不要貼邊（美觀）
        }

        // 跳出對話框
        AlertDialog.Builder(requireContext())
            .setTitle("編輯帳本名稱")
            .setView(input)                    // 把輸入框放進對話框
            .setPositiveButton("儲存") { _, _ ->
                // 使用者按儲存！開始更新帳本名稱
                launchWhenViewActive {
                    // 切換到背景執行緒執行更新
                    val result = withContext(Dispatchers.IO) {
                        repository.updateBookName(input.text.toString())
                    }

                    // 顯示結果
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳本名稱已更新" else "更新失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 如果更新成功，刷新整個設定頁面（讓新名稱顯示出來）
                    if (result.success) {
                        refreshSettings()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // 🚪 登出目前帳號 — 白話文：使用者說「我要走了」，App 幫他清除資料
    // ============================================================
    // 注意！登出不只是「切換帳號」，還會清除手機上的本機資料
    // 為什麼？因為登出後，下一個使用者（或同一個使用者重新登入）應該看到自己的資料
    // 而不是上一個人的記帳記錄！
    //
    // 登出後，App 會轉換成「匿名模式」（訪客模式），可以繼續記帳
    // 但資料不會上雲端，換手機就掰掰了
    private fun signOutCurrentAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle("登出帳號")
            .setMessage("登出後會清除目前裝置上的本機資料，之後可再透過 Google 重新登入找回雲端備份。")
            .setPositiveButton("登出") { _, _ ->
                launchWhenViewActive {
                    // 切換到背景執行緒，執行登出並啟動匿名 session
                    val result = withContext(Dispatchers.IO) {
                        repository.signOutAndStartAnonymousSession()
                    }

                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "已登出" else "登出失敗",
                        Toast.LENGTH_LONG
                    ).show()

                    refreshSettings()  // 刷新設定頁面（登出後按鈕會變）

                    // 回到首頁（讓使用者看到空白的記帳畫面，而不是上一個人的資料）
                    (requireActivity() as? MainActivity)?.showHomeFragment()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // 💣 確認清除所有資料 — 白話文：「核彈按鈕」的確認視窗
    // ============================================================
    // 這是最危險的操作！會刪除所有的交易、分類、帳戶...
    // 所以需要兩次確認：
    //   1. 第一次：這個對話框本身（問「確定嗎？」）
    //   2. 第二次：repository 裡面可能還有提醒（但我們只做一次確認）
    private fun confirmClearAllData() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除所有記帳資料")
            .setMessage("這會清除目前裝置上的所有交易、帳戶與預算資料。若尚未備份到雲端，資料將無法復原。確定要繼續嗎？")
            .setPositiveButton("確認清除") { _, _ ->
                launchWhenViewActive {
                    // 切換到背景執行緒，清除所有資料
                    withContext(Dispatchers.IO) {
                        repository.clearAllData()
                    }

                    Toast.makeText(requireContext(), "已清除所有資料", Toast.LENGTH_SHORT).show()
                    refreshSettings()
                    (requireActivity() as? MainActivity)?.showHomeFragment()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // 💾 開始匯出備份 — 白話文：讓使用者選擇「要把備份檔案存到哪裡」
    // ============================================================
    // 這個函式只是「開啟檔案選擇器」，真正的匯出在 exportBackupToUri 裡
    // 就像你點「另存新檔」，先選位置，然後才真正儲存
    private fun startExportBackup() {
        // 產生預設的檔案名稱，例如「cococoin-backup-20260406-1430.json」
        // 格式：cococoin-backup-年年月月日日-時時分分.json
        val defaultName = "cococoin-backup-${
            android.text.format.DateFormat.format(
                "yyyyMMdd-HHmm",
                System.currentTimeMillis()
            )
        }.json"

        // 啟動檔案選擇器（系統會跳出檔案總管讓使用者選位置）
        // 使用者選完後，會自動呼叫 exportBackupToUri
        exportBackupLauncher.launch(defaultName)
    }

    // ============================================================
    // 💾 實際執行匯出備份 — 白話文：把資料寫進使用者選的檔案裡
    // ============================================================
    // URI 是什麼？白話文：檔案在手機裡的「地址」或「門牌號碼」
    // 就像「C:\我的文件\backup.json」或「/sdcard/Download/backup.json」
    private fun exportBackupToUri(uri: Uri) {
        launchWhenViewActive {
            val result = withContext(Dispatchers.IO) {
                // runCatching 像一個「安全防護罩」
                // 裡面如果發生任何錯誤（例如磁碟空間不足、沒有寫入權限）
                // 不會讓 App 當機，而是會被 catch 住
                runCatching {
                    // 從資料庫讀取所有資料並轉成 JSON 字串
                    val json = repository.exportBackupJson()

                    // 打開使用者選的檔案，準備寫入
                    // contentResolver 是 Android 的「檔案管理員」，可以讀寫各種檔案
                    val outputStream = requireContext().contentResolver.openOutputStream(uri)
                        ?: error("無法建立備份檔")  // 如果打不開就拋出錯誤

                    // 把 JSON 字串寫入檔案（使用緩衝區，效率更高）
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

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "匯出成功" else "匯出失敗",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // 📂 設定自動本機備份的資料夾 — 白話文：讓使用者選一個「自動備份的存放位置」
    // ============================================================
    // 自動本機備份：App 會每隔一段時間，自動把資料存到使用者選的資料夾
    // 這樣就算手機壞掉，資料夾裡還有備份檔可以救回來
    private fun configureAutoBackupFolder(uri: Uri) {
        val resolver = requireContext().contentResolver

        // 設定要取得的權限：讀取 和 寫入
        // FLAG_GRANT_READ_URI_PERMISSION：讀取權限
        // FLAG_GRANT_WRITE_URI_PERMISSION：寫入權限
        // `or` 就是把兩個權限合併在一起
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        // 請求「永久」存取這個資料夾的權限
        // 這樣 App 關掉再開，還是可以繼續使用這個資料夾，不用每次都要重新選
        runCatching {
            resolver.takePersistableUriPermission(uri, flags)
        }

        launchWhenViewActive {
            // 儲存使用者的選擇到資料庫
            val result = withContext(Dispatchers.IO) {
                repository.configureAutoLocalBackupFolder(uri)
            }

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "已啟用自動本機備份" else "設定失敗",
                Toast.LENGTH_SHORT
            ).show()

            refreshSettings()
        }
    }

    // ============================================================
    // 📊 開始匯出 CSV 報表 — 白話文：讓使用者選擇「要把報表存到哪裡」
    // ============================================================
    // CSV 是一種可以用 Excel 打開的格式
    // 使用者可以匯出 CSV，然後用電腦分析自己花了多少錢
    private fun startExportCsv() {
        // 產生預設的檔案名稱，例如「cococoin-transactions-20260406-1430.csv」
        val defaultName = "cococoin-transactions-${
            android.text.format.DateFormat.format(
                "yyyyMMdd-HHmm",
                System.currentTimeMillis()
            )
        }.csv"

        exportCsvLauncher.launch(defaultName)
    }

    // ============================================================
    // 📊 實際執行匯出 CSV — 白話文：把交易紀錄轉成 CSV 格式並寫入檔案
    // ============================================================
    private fun exportCsvToUri(uri: Uri) {
        launchWhenViewActive {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 從資料庫讀取交易紀錄並轉成 CSV 格式（逗號分隔的表格）
                    val csv = repository.exportTransactionsCsv()

                    // 打開使用者選的檔案，準備寫入
                    val outputStream = requireContext().contentResolver.openOutputStream(uri)
                        ?: error("無法建立 CSV 檔案")

                    // 把 CSV 字串寫入檔案
                    // 使用 UTF-8 編碼，這樣中文才不會變成亂碼
                    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(csv)
                    }

                    OperationResult.ok("CSV 報表已匯出")
                }.getOrElse { exception ->
                    OperationResult.fail(exception.message ?: "CSV 匯出失敗")
                }
            }

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "CSV 匯出成功" else "CSV 匯出失敗",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // ⏹️ 停用自動本機備份 — 白話文：關掉自動備份功能
    // ============================================================
    private fun disableAutoBackup() {
        AlertDialog.Builder(requireContext())
            .setTitle("停用自動本機備份")
            .setMessage("停用後將不再自動更新備份檔，但已經匯出的檔案不會被刪除。")
            .setPositiveButton("停用") { _, _ ->
                launchWhenViewActive {
                    val result = withContext(Dispatchers.IO) {
                        repository.disableAutoLocalBackup()
                    }

                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "已停用" else "停用失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    refreshSettings()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // 📥 確認匯入備份 — 白話文：先警告使用者「會覆蓋現有資料」，然後才選檔案
    // ============================================================
    // 匯入是一個「破壞性操作」，因為它會用備份檔的內容覆蓋目前的資料
    // 所以要先跳出警告視窗，讓使用者知道嚴重性
    private fun confirmImportBackup() {
        AlertDialog.Builder(requireContext())
            .setTitle("匯入備份檔")
            .setMessage("匯入後會以備份檔內容覆蓋目前裝置上的交易、帳戶與預算資料。確定要繼續嗎？")
            .setPositiveButton("選擇檔案") { _, _ ->
                // 使用者確認了，才去選檔案
                // 支援的檔案類型：JSON、純文字、或其他（*/* 表示所有類型）
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ============================================================
    // 📥 實際執行匯入備份 — 白話文：從 JSON 檔案讀取資料，然後寫入資料庫
    // ============================================================
    private fun importBackupFromUri(uri: Uri) {
        launchWhenViewActive {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 讀取使用者選的檔案內容
                    // openInputStream 打開檔案，bufferedReader 讀取文字，readText 全部讀進來
                    val json =
                        requireContext().contentResolver.openInputStream(uri)?.bufferedReader()
                            ?.use { reader ->
                                reader.readText()
                            } ?: error("無法讀取備份檔")  // 如果無法讀取就拋出錯誤

                    // 把 JSON 資料匯入資料庫（會覆蓋現有資料）
                    repository.importBackupJson(json)
                }.getOrElse { exception ->
                    OperationResult.fail(exception.message ?: "匯入失敗")
                }
            }

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "匯入成功" else "匯入失敗",
                Toast.LENGTH_LONG
            ).show()

            // 如果匯入成功，刷新設定頁面並回到首頁（讓使用者看到匯入的資料）
            if (result.success) {
                refreshSettings()
                (requireActivity() as? MainActivity)?.showHomeFragment()
            }
        }
    }
}
