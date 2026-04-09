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

// 首頁的資料管家：
// 負責管理「首頁」需要的所有資料（收入、支出、結餘、預算、交易列表）
class HomeViewModel(
    private val repository: CocoCoinRepository  // 資料倉庫，負責存取資料庫
) : ViewModel() {

    // 目前選中的月份（使用者可以往前/往後切換）
    // 預設是當月的第一天，時間歸零（00:00:00）
    private val selectedMonthCalendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // 真正的當前月份（用來判斷使用者是否在看「當月」）
    private val currentMonthCalendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // 日期格式化工具（把 Calendar 變成「2026 年 4 月」這樣的文字）
    private val monthDisplayFormat = SimpleDateFormat("yyyy 年 M 月", Locale.getDefault())

    // 交易時間格式化工具（把「2026/04/06 14:30」變成可讀的格式）
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    // 暫存所有的交易紀錄（從資料庫讀來後先存在這裡）
    private var allTransactions: List<Transaction> = emptyList()

    // 目前選中月份的預算金額
    private var currentMonthlyBudget: Int = 0

    // 發送給畫面的「狀態」（畫面會根據這個來顯示內容）
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 發送給畫面的「一次性事件」（例如跳出提示訊息、顯示復原按鈕）
    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    // 初始化區塊（ViewModel 一建立就會執行）
    init {
        // 確保資料庫準備好之後，載入資料
        repository.ensureInitialized {
            reload()
        }
    }

    // 重新載入所有資料（從資料庫讀取最新資料）
    fun reload() {
        viewModelScope.launch {
            loadData()
        }
    }

    // 切換到上一個月
    fun moveToPreviousMonth() {
        selectedMonthCalendar.add(Calendar.MONTH, -1)
        publishState()  // 重新計算並更新畫面
    }

    // 切換到下一個月（如果已經在當月就不能再往後）
    fun moveToNextMonth() {
        if (!isViewingCurrentMonth()) {
            selectedMonthCalendar.add(Calendar.MONTH, 1)
            publishState()
        }
    }

    // 刪除一筆交易
    fun deleteTransaction(transactionId: Int) {
        // 找出要被刪除的交易（用來做「復原」功能）
        val deletedTransaction = allTransactions.firstOrNull { it.id == transactionId }
        if (deletedTransaction == null) {
            emitMessage("找不到要刪除的交易")
            return
        }

        viewModelScope.launch {
            // 切換到 IO 跑道，執行刪除
            val result = withContext(Dispatchers.IO) {
                repository.deleteTransaction(transactionId)
            }
            if (result.success) {
                // 刪除成功：重新載入資料，並發送「可復原」事件
                loadData()
                _events.emit(HomeUiEvent.ShowUndoDelete(deletedTransaction))
            } else {
                emitMessage(result.message ?: "刪除失敗")
            }
        }
    }

    // 復原剛才刪除的交易（Snackbar 的「復原」按鈕會呼叫這個）
    fun restoreDeletedTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.restoreDeletedTransaction(transaction)
            }
            if (result.success) {
                loadData()
            } else {
                emitMessage(result.message ?: "復原失敗")
            }
        }
    }

    // 更新一筆交易（編輯後儲存）
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

    // 從資料庫讀取資料（在背景執行）
    private suspend fun loadData() {
        val selectedYear = selectedMonthCalendar.get(Calendar.YEAR)
        val selectedMonth = selectedMonthCalendar.get(Calendar.MONTH) + 1  // 月份從 0 開始所以要 +1

        // 讀取所有交易紀錄
        val transactions = withContext(Dispatchers.IO) {
            repository.getTransactions()
        }

        // 讀取選中月份的預算
        val monthlyBudget = withContext(Dispatchers.IO) {
            repository.getMonthlyBudget(selectedYear, selectedMonth)
        }

        // 存到暫存變數
        allTransactions = transactions
        currentMonthlyBudget = monthlyBudget

        // 更新畫面狀態
        publishState()
    }

    // 計算並發布畫面狀態（把原始資料轉成畫面好用的格式）
    private fun publishState() {
        // 只取選中月份的交易
        val filteredTransactions = transactionsForSelectedMonth()

        // 計算當月總收入（加總所有「收入」類型的金額）
        val monthlyIncome = filteredTransactions
            .filter { it.type == "收入" }
            .sumOf { it.amount }

        // 計算當月總支出
        val monthlyExpense = filteredTransactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        // 計算當月結餘 = 收入 - 支出
        val monthlyBalance = monthlyIncome - monthlyExpense

        // 計算預算相關數據
        val remainingBudget = currentMonthlyBudget - monthlyExpense  // 剩餘預算
        val budgetPercent = if (currentMonthlyBudget > 0) {
            // 剩餘預算 ÷ 總預算 × 100（並限制在 0~100 之間）
            ((remainingBudget.toFloat() / currentMonthlyBudget.toFloat()) * 100)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        // 根據預算情況產生提示文字
        val budgetMessage = when {
            currentMonthlyBudget <= 0 -> "尚未設定當月預算"
            remainingBudget < 0 -> "當月已超支 ${abs(remainingBudget)} 元"
            else -> "當月剩餘 $budgetPercent%"
        }

        // 打包成 UI 狀態，發送給畫面
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

    // 過濾出選中月份的交易
    private fun transactionsForSelectedMonth(): List<Transaction> {
        val selectedYear = selectedMonthCalendar.get(Calendar.YEAR)
        val selectedMonth = selectedMonthCalendar.get(Calendar.MONTH)

        return allTransactions.filter { transaction ->
            try {
                // 把交易時間字串轉成日期物件
                val date = transactionDateFormat.parse(transaction.time) ?: return@filter false
                val calendar = Calendar.getInstance().apply { time = date }
                // 檢查年份和月份是否相符
                calendar.get(Calendar.YEAR) == selectedYear &&
                        calendar.get(Calendar.MONTH) == selectedMonth
            } catch (_: Exception) {
                false  // 解析失敗就忽略這筆交易
            }
        }
    }

    // 檢查使用者是否正在查看「當月」
    private fun isViewingCurrentMonth(): Boolean {
        return selectedMonthCalendar.get(Calendar.YEAR) == currentMonthCalendar.get(Calendar.YEAR) &&
                selectedMonthCalendar.get(Calendar.MONTH) == currentMonthCalendar.get(Calendar.MONTH)
    }

    // 發送一則訊息（讓畫面顯示 Toast）
    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(HomeUiEvent.ShowMessage(message))
        }
    }

    // ViewModel 工廠（用來建立 HomeViewModel）
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

// 畫面狀態的資料類別（記錄首頁要顯示的所有數據）
data class HomeUiState(
    val selectedMonthText: String = "",        // 顯示的月份文字（例如「2026 年 4 月」）
    val isViewingCurrentMonth: Boolean = true, // 是否正在看當月（用來控制下一月按鈕）
    val transactions: List<Transaction> = emptyList(),  // 該月的交易列表
    val monthlyIncome: Int = 0,                // 當月總收入
    val monthlyExpense: Int = 0,               // 當月總支出
    val monthlyBalance: Int = 0,               // 當月結餘
    val monthlyBudget: Int = 0,                // 當月預算
    val remainingBudget: Int = 0,              // 剩餘預算
    val budgetPercent: Int = 0,                // 預算使用百分比
    val budgetMessage: String = "尚未設定當月預算",  // 預算提示文字
    val emptyMessage: String = "尚無記帳資料"       // 沒有交易時顯示的文字
)

// 一次性事件（不會被重複顯示）
sealed class HomeUiEvent {
    data class ShowMessage(val message: String) : HomeUiEvent()           // 顯示 Toast 訊息
    data class ShowUndoDelete(val transaction: Transaction) : HomeUiEvent() // 顯示「復原」Snackbar
}