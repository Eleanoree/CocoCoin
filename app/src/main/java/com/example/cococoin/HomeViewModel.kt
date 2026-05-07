package com.example.cococoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 🏠【首頁的資料管家】
 *
 * 想像你有一個超厲害的私人秘書（HomeViewModel），他的工作是：
 * 1. 去倉庫（Repository）把記帳資料搬出來
 * 2. 幫你算這個月花了多少、賺了多少、還剩多少預算
 * 3. 當你按「上個月」、「下個月」時，重新算給你看
 * 4. 你刪掉一筆帳時，他還會貼心問：「要復原嗎？」
 *
 * 重點：這個秘書很聰明，手機畫面旋轉時資料也不會不見！
 */
class HomeViewModel(
    private val repository: CocoCoinRepository  // 📦 資料倉庫：所有記帳資料都存在這裡
) : ViewModel() {

    // ---------- 📅 日期相關的魔法小道具 ----------

    /**
     * 🗓️ 目前正在「觀看」的月份（使用者可能在看 3 月、5 月、或去年）
     *
     * 就像日曆上你翻到哪一頁，這裡就記錄那一頁的日期
     * 預設是「當月的第一天 00:00:00」，例如 2026/04/01 午夜
     */
    private val selectedMonthCalendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)      // 日期設為 1 號
        set(Calendar.HOUR_OF_DAY, 0)       // 時：午夜 0 點
        set(Calendar.MINUTE, 0)            // 分：0 分
        set(Calendar.SECOND, 0)            // 秒：0 秒
        set(Calendar.MILLISECOND, 0)       // 毫秒：0（精準歸零）
    }

    /**
     * 📍「真實的」當前月份（就是現在幾月啦！）
     *
     * 這個不會被使用者亂翻頁影響，永遠代表「現在」
     * 用來判斷使用者是不是在看「當月」（決定能不能按「下個月」）
     */
    private val currentMonthCalendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    /**
     * 🖨️ 日期轉文字機（把 Calendar 變成「2026 年 4 月」這種台灣人看得懂的格式）
     *
     * 例如：2026/04/01 → 「2026 年 4 月」
     */
    private val monthDisplayFormat = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault())

    /**
     * ⏰ 交易時間轉文字機（把日期變成「2026/04/06 14:30」）
     *
     * 用來顯示每一筆記帳是「什麼時候」記錄的
     */
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    // ---------- 📊 暫存資料區（秘書的小筆記本）----------

    /**
     * 📋 所有交易紀錄的暫存區
     *
     * 從資料庫讀出來後先放在這裡，避免每次都重新讀取資料庫（省電、省時間）
     * 就像秘書把所有文件先從倉庫搬到自己桌上，要找資料比較快
     */
    private var allTransactions: List<Transaction> = emptyList()

    /**
     * 💰 目前選中月份的預算金額
     *
     * 例如：這個月預算 15000 元
     */
    private var currentMonthlyBudget: Int = 0

    // ---------- 📡 跟畫面溝通的無線電（Flow）----------

    /**
     * 📺 畫面狀態（StateFlow）
     *
     * 這是「持續更新的狀態」，例如：
     * - 這個月總支出多少
     * - 交易列表有哪些
     * - 預算剩多少%
     *
     * 畫面會「盯著」這個狀態，一有變化就自動更新（就像訂閱 YouTube 頻道）
     */
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * ⚡ 一次性事件（SharedFlow）
     *
     * 這是「只執行一次」的事件，例如：
     * - 跳出 Toast 訊息：「刪除成功！」
     * - 顯示 Snackbar：「已刪除，要復原嗎？」
     *
     * 跟 StateFlow 不同，它不會保留舊事件（不會一直跳一樣的訊息）
     */
    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    // ---------- 🚀 初始化（秘書上任第一天就開始工作）----------

    /**
     * 🎬 開機初始化
     *
     * 當 ViewModel 被創建時，會自動執行這裡面的程式
     * 確保資料庫準備好之後，立刻載入資料
     */
    init {
        // 等倉庫準備好（資料庫打開），就去搬資料
        repository.ensureInitialized {
            reload()
        }
    }

    // ---------- 🛠️ 公開功能（畫面可以叫秘書做這些事）----------

    /**
     * 🔄 重新載入所有資料
     *
     * 什麼時候會用到？
     * - 剛打開 App 時
     * - 新增/刪除/修改一筆交易後
     * - 從設定頁面回來時
     *
     * 就像用力拍桌子大喊：「秘書！去倉庫把最新資料搬出來！」
     */
    fun reload() {
        viewModelScope.launch {
            loadData()  // 去背景搬資料
        }
    }

    /**
     * ⏪ 切換到上一個月
     *
     * 使用者按「◀ 上個月」按鈕時呼叫
     * 例如：從 4 月變成 3 月
     */
    fun moveToPreviousMonth() {
        selectedMonthCalendar.add(Calendar.MONTH, -1)  // 月份減 1（往前翻）
        publishState()  // 重新計算並更新畫面
    }

    /**
     * ⏩ 切換到下一個月
     *
     * 使用者按「下個月 ▶」按鈕時呼叫
     * 但如果已經在看「當月」，就不能再往後（不能看到未來的帳～）
     */
    fun moveToNextMonth() {
        if (!isViewingCurrentMonth()) {  // 只有在「不是當月」時才能往後
            selectedMonthCalendar.add(Calendar.MONTH, 1)
            publishState()
        }
        // 如果已經是當月，點下個月按鈕沒反應（因為未來還沒發生的事不能記帳）
    }

    /**
     * 🗑️ 刪除一筆交易
     *
     * @param transactionId 要刪除的交易編號（就像身分證字號）
     */
    fun deleteTransaction(transactionId: Int) {
        // 先從暫存區找出要被刪除的交易（為了做「復原」功能）
        val deletedTransaction = allTransactions.firstOrNull { it.id == transactionId }

        // 如果找不到這筆交易？那可能早就被刪了，跳出提示
        if (deletedTransaction == null) {
            emitMessage("找不到要刪除的交易")
            return
        }

        // 派小弟（協程）去背景執行刪除任務
        viewModelScope.launch {
            // 切換到 IO 跑道（專門處理檔案/資料庫的背景通道）
            val result = withContext(Dispatchers.IO) {
                repository.deleteTransaction(transactionId)
            }

            if (result.success) {
                // ✅ 刪除成功：重新載入最新資料，然後問使用者要不要復原
                loadData()
                _events.emit(HomeUiEvent.ShowUndoDelete(deletedTransaction))
            } else {
                // ❌ 刪除失敗：顯示錯誤訊息
                emitMessage(result.message ?: "刪除失敗")
            }
        }
    }

    /**
     * ↩️ 復原剛才刪除的交易
     *
     * 當使用者按 Snackbar 上的「復原」按鈕時會呼叫
     * 把刪除的資料從垃圾桶撿回來
     *
     * @param transaction 被刪掉的那筆交易（之前先偷偷存起來的）
     */
    fun restoreDeletedTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.restoreDeletedTransaction(transaction)
            }
            if (result.success) {
                loadData()  // 重新載入，讓畫面顯示復原後的資料
            } else {
                emitMessage(result.message ?: "復原失敗")
            }
        }
    }

    /**
     * ✏️ 更新一筆交易（編輯後儲存）
     *
     * 使用者修改了某筆交易的「金額」或「類別」後，會呼叫這個
     *
     * @param updatedTransaction 修改後的交易資料
     */
    fun updateTransaction(updatedTransaction: Transaction) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.updateTransaction(updatedTransaction)
            }
            if (result.success) {
                loadData()
            } else {
                emitMessage(result.message ?: "更新失敗")
            }
        }
    }

    // ---------- 🔧 內部工具（秘書自己偷偷用的方法）----------

    /**
     * 📥 從資料庫讀取資料（在背景執行，不會卡住畫面）
     *
     * 這是 suspend 函數（可以暫停、等資料庫讀完再回來）
     * 就像去倉庫搬貨，搬到一半可以休息，搬完再回來報告
     */
    private suspend fun loadData() {
        // 取得目前選中的年份和月份（例如：2026 年、4 月）
        val selectedYear = selectedMonthCalendar.get(Calendar.YEAR)
        val selectedMonth = selectedMonthCalendar.get(Calendar.MONTH) + 1  // 注意！Calendar 的月份從 0 開始（1 月 = 0），所以要 +1

        // 📖 讀取所有交易紀錄（切換到 IO 跑道，不干擾畫面）
        val transactions = withContext(Dispatchers.IO) {
            repository.getTransactions()
        }

        // 💰 讀取選中月份的預算
        val monthlyBudget = withContext(Dispatchers.IO) {
            repository.getMonthlyBudget(selectedYear, selectedMonth)
        }

        // 💾 存到暫存變數（秘書的小筆記本）
        allTransactions = transactions
        currentMonthlyBudget = monthlyBudget

        // 🖥️ 更新畫面狀態（告訴畫面：資料變了，請重畫）
        publishState()
    }

    /**
     * 🧮 計算並發布畫面狀態
     *
     * 這是整個 ViewModel 最核心的大腦！
     * 把原始資料（一堆交易紀錄）轉換成畫面好用的格式：
     * - 總收入、總支出、結餘
     * - 預算進度條、提醒文字
     * - 過濾出選中月份的交易
     */
    private fun publishState() {
        // 📌 只取「選中月份」的交易（例如只看 4 月的帳）
        val filteredTransactions = transactionsForSelectedMonth()

        // 💵 計算當月總收入（把所有「收入」類型的金額加起來）
        val monthlyIncome = filteredTransactions
            .filter { it.type == "收入" }  // 只留收入
            .sumOf { it.amount }           // 把金額加總

        // 💸 計算當月總支出
        val monthlyExpense = filteredTransactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        // ⚖️ 計算當月結餘 = 收入 - 支出
        // 正數代表賺比較多，負數代表花比較多（赤字）
        val monthlyBalance = monthlyIncome - monthlyExpense

        // 📊 計算預算相關數據（這部分是預算功能的核心）
        val remainingBudget = currentMonthlyBudget - monthlyExpense  // 剩餘預算 = 預算 - 已經花掉的

        /**
         * 🎯 預算使用百分比
         *
         * 公式：剩餘預算 ÷ 總預算 × 100
         * 例如：預算 10000，剩下 3000 → (3000/10000)*100 = 30%
         *
         * 注意：要限制在 0~100 之間，不能超過 100%（不然進度條會爆掉）
         */
        val budgetPercent = if (currentMonthlyBudget > 0) {
            ((remainingBudget.toFloat() / currentMonthlyBudget.toFloat()) * 100)
                .toInt()
                .coerceIn(0, 100)  // 強制在 0~100 之間（像安全氣囊）
        } else {
            0  // 如果沒設定預算，百分比就是 0
        }

        /**
         * 💬 根據預算情況產生提示文字（不同情況說不同的話）
         *
         * 三種狀況：
         * 1. 沒設定預算 → 「尚未設定當月預算」
         * 2. 花超過預算 → 「當月已超支 XXX 元」
         * 3. 還有預算剩 → 「當月剩餘 XX%」
         */
        val budgetMessage = when {
            currentMonthlyBudget <= 0 -> "尚未設定當月預算"
            remainingBudget < 0 -> "當月已超支 ${abs(remainingBudget)} 元"  // abs() 取絕對值（把負數變正數）
            else -> "當月剩餘 $budgetPercent%"
        }

        // 📦 把所有東西打包成一個「UI 狀態包裹」，寄給畫面
        _uiState.value = HomeUiState(
            selectedMonthText = monthDisplayFormat.format(selectedMonthCalendar.time),
            isViewingCurrentMonth = isViewingCurrentMonth(),
            transactions = filteredTransactions,
            monthlyIncome = monthlyIncome,
            monthlyExpense = monthlyExpense,
            monthlyBalance = monthlyBalance,
            monthlyBudget = currentMonthlyBudget,
            remainingBudget = remainingBudget,
            budgetPercent = budgetPercent,
            budgetMessage = budgetMessage,
            emptyMessage = "${monthDisplayFormat.format(selectedMonthCalendar.time)} 尚無記帳資料"
        )
    }

    /**
     * 🔍 過濾出選中月份的交易
     *
     * 從「所有交易」中，挑出「年份和月份」符合的那些
     *
     * @return 屬於選中月份的帳單列表
     */
    private fun transactionsForSelectedMonth(): List<Transaction> {
        val selectedYear = selectedMonthCalendar.get(Calendar.YEAR)
        val selectedMonth = selectedMonthCalendar.get(Calendar.MONTH)

        return allTransactions.filter { transaction ->
            try {
                // 把交易時間的文字（例如「2026/04/06 14:30」）轉成日期物件
                val date = transactionDateFormat.parse(transaction.time) ?: return@filter false
                val calendar = Calendar.getInstance().apply { time = date }

                // 檢查：年份相同 且 月份相同
                calendar.get(Calendar.YEAR) == selectedYear &&
                        calendar.get(Calendar.MONTH) == selectedMonth
            } catch (_: Exception) {
                false  // 如果日期格式壞掉了，就跳過這筆（不讓 App 當機）
            }
        }
    }

    /**
     * 🔎 檢查使用者是否正在查看「當月」
     *
     * @return true 代表正在看這個月，false 代表在看別的月份
     */
    private fun isViewingCurrentMonth(): Boolean {
        return selectedMonthCalendar.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                selectedMonthCalendar.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH)
    }

    /**
     * 📢 發送一則訊息（讓畫面顯示 Toast）
     *
     * 例如：「刪除成功」、「網路不穩」這類一次性提示
     *
     * @param message 要顯示的文字
     */
    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.ShowMessage(message))
        }
    }

    // ---------- 🏭 ViewModel 工廠（專門生產 HomeViewModel 的機器）----------

    /**
     * 🏭 ViewModel 工廠
     *
     * 為什麼需要工廠？
     * 因為 HomeViewModel 需要一個「repository」參數，
     * 而 Android 系統預設只能建立「沒有參數」的 ViewModel，
     * 所以要用工廠來「手動」把 repository 傳進去。
     *
     * 就像你要訂做一個客製化蛋糕，不能直接從自動販賣機拿，
     * 要跟師傅（工廠）說：「我要加草莓！」（repository）
     */
    class Factory(
        private val repository: CocoCoinRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

/**
 * 📊【畫面狀態的資料類別】
 *
 * 這就像一個「包裹」，裡面裝著首頁要顯示的所有東西：
 * - 月份文字
 * - 交易列表
 * - 收入、支出、結餘
 * - 預算資訊
 *
 * 畫面會拆開這個包裹，把裡面的數字填到畫面上
 */
data class HomeUiState(
    val selectedMonthText: String = "",              // 🗓️ 顯示的月份文字（例如「2026 年 4 月」）
    val isViewingCurrentMonth: Boolean = true,       // 🔒 是否正在看當月（用來控制下個月按鈕能不能按）
    val transactions: List<Transaction> = emptyList(), // 📋 該月的交易列表
    val monthlyIncome: Int = 0,                     // 💵 當月總收入
    val monthlyExpense: Int = 0,                    // 💸 當月總支出
    val monthlyBalance: Int = 0,                    // ⚖️ 當月結餘（收入 - 支出）
    val monthlyBudget: Int = 0,                     // 💰 當月預算
    val remainingBudget: Int = 0,                   // 📉 剩餘預算（預算 - 支出）
    val budgetPercent: Int = 0,                     // 📊 預算使用百分比（進度條用）
    val budgetMessage: String = "尚未設定當月預算",    // 💬 預算提示文字
    val emptyMessage: String = "尚無記帳資料"         // 🦗 沒有交易時顯示的文字（代表空空如也）
)

/**
 * ⚡【一次性事件】
 *
 * 這是「只會發生一次」的事情，不像 StateFlow 會一直記著
 *
 * 有兩種事件：
 * 1. ShowMessage：跳出一個短暫的小提示（Toast）
 * 2. ShowUndoDelete：顯示一個可以「復原」的 Snackbar
 *
 * sealed class 的意思是：「事件只有這兩種，不會有其他亂七八糟的」
 * 就像交通號誌只有紅、黃、綠三種，不會突然出現紫色
 */
sealed class HomeUiEvent {
    data class ShowMessage(val message: String) : HomeUiEvent()           // 📢 顯示 Toast 訊息
    data class ShowUndoDelete(val transaction: Transaction) : HomeUiEvent() // ↩️ 顯示「復原」Snackbar
}