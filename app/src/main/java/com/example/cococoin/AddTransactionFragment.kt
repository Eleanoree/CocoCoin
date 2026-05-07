package com.example.cococoin

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ➕【新增交易頁面】
 *
 * 想像你去超市買東西，結帳時的收銀機畫面：
 *
 * ┌─────────────────────────────────────────────┐
 * │               💰 記帳                        │
 * ├─────────────────────────────────────────────┤
 * │                                              │
 * │  [支出]  [收入]           ← 切換收入/支出    │
 * │                                              │
 * │  ┌─────────────────────────────────────┐    │
 * │  │               NT$                   │    │
 * │  │                150                  │ ← 金額顯示區
 * │  └─────────────────────────────────────┘    │
 * │                                              │
 * │  📅 日期：4/6                               │ ← 可點擊選日期
 * │  🏷️ 分類：餐飲                    ▼        │ ← 下拉選單
 * │  💳 帳戶：現金                    ▼        │ ← 下拉選單
 * │  📝 備註：跟同事吃午餐                     │ ← 備註欄
 * │                                              │
 * │  ┌───┬───┬───┐                            │
 * │  │ 1 │ 2 │ 3 │                            │
 * │  ├───┼───┼───┤                            │ ← 數字鍵盤
 * │  │ 4 │ 5 │ 6 │                            │
 * │  ├───┼───┼───┤                            │
 * │  │ 7 │ 8 │ 9 │                            │
 * ｜  ├───┼───┼───┤                            │
 * │  │⌫  │ 0 │ C │                            │
 * │  └───┴───┴───┘                            │
 * │                                              │
 * │  [取消]                      [儲存]          │
 * └─────────────────────────────────────────────┘
 *
 * 特色：
 * 1️⃣ 內建數字鍵盤（不用叫系統鍵盤，記帳超快）
 * 2️⃣ 支出/收入切換（顏色會變：紅色=支出，綠色=收入）
 * 3️⃣ 分類和帳戶下拉選單（自動從資料庫載入）
 * 4️⃣ 可選日期（預設今天）
 * 5️⃣ 支出時會檢查帳戶餘額是否足夠
 */
class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {

    // ========== 📱 畫面元件宣告 ==========

    // 基本控制項
    private lateinit var btnCancel: Button           // ❌ 取消按鈕（清空所有欄位）
    private lateinit var btnSave: Button             // 💾 儲存按鈕（記帳！）

    // 顯示區
    private lateinit var tvCurrentTime: TextView     // 📅 顯示當前選擇的日期
    private lateinit var tvNt: TextView              // 🇳🇹 顯示「NT$」符號
    private lateinit var tvType: TextView            // 🏷️ 顯示「支出」或「收入」文字
    private lateinit var tvAmount: TextView           // 💰 顯示金額數字（大號字體）

    // 輸入欄位
    private lateinit var etNote: EditText            // 📝 備註輸入框
    private lateinit var actCategory: AppCompatAutoCompleteTextView  // 🏷️ 分類下拉選單（可打字+選取）
    private lateinit var actAccount: AppCompatAutoCompleteTextView  // 💳 帳戶下拉選單
    private lateinit var rgType: RadioGroup          // 🔘 支出/收入切換按鈕組

    // ========== 📦 資料暫存區 ==========

    /**
     * 💵 當前輸入的金額（字串形式）
     *
     * 為什麼用字串？因為使用者是一格一格按的：
     * 按「1」→ "1"
     * 按「5」→ "15"
     * 按「0」→ "150"
     *
     * 用字串比較容易組裝，最後再轉成數字
     */
    private var currentAmount = ""

    /**
     * 📋 暫存帳戶列表
     * 從資料庫讀取後存在這裡，用來檢查餘額是否足夠
     */
    private val accountList = mutableListOf<AssetAccount>()

    /**
     * 🏷️ 暫存分類列表
     */
    private var currentCategories = emptyList<TransactionCategoryDefinition>()

    /**
     * 🗄️ 資料倉庫（存取資料庫）
     * lazy：只有第一次用到時才會建立
     */
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    /**
     * 📅 選擇的交易日期（預設為今天）
     * Calendar 物件，可以輕鬆處理日期運算
     */
    private val selectedTransactionCalendar = Calendar.getInstance()

    /**
     * ⏰ 時間格式化工具
     *
     * transactionDateFormat：完整格式，存到資料庫用
     * 例如：「2026/04/06 14:30:00」
     *
     * transactionDisplayFormat：簡短格式，顯示給使用者看
     * 例如：「4/6」
     */
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    private val transactionDisplayFormat = SimpleDateFormat("M/d", Locale.getDefault())

    // ========== 🎬 畫面建立 ==========

    /**
     * 🎨 當畫面建立完成時呼叫
     *
     * 這裡要做的事情：
     * 1. 綁定畫面元件（牽紅線）
     * 2. 設定預設值（日期=今天、類型=支出、金額=0）
     * 3. 設定所有按鈕的點擊事件
     * 4. 從資料庫載入分類和帳戶選單
     *
     * @param view 畫面的根視圖
     * @param savedInstanceState 之前儲存的狀態（手機旋轉時用）
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---------- 🔗 綁定畫面元件 ----------

        // 顯示區
        tvType = view.findViewById(R.id.tvType)
        tvAmount = view.findViewById(R.id.tvAmount)
        tvNt = view.findViewById(R.id.tvNt)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)

        // 輸入區
        etNote = view.findViewById(R.id.etNote)
        actCategory = view.findViewById(R.id.actCategory)
        actAccount = view.findViewById(R.id.actAccount)
        rgType = view.findViewById(R.id.rgType)

        // 按鈕區
        btnCancel = view.findViewById(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSave)

        // ---------- 🔢 數字鍵盤按鈕 ----------
        val btn1 = view.findViewById<Button>(R.id.btn1)
        val btn2 = view.findViewById<Button>(R.id.btn2)
        val btn3 = view.findViewById<Button>(R.id.btn3)
        val btn4 = view.findViewById<Button>(R.id.btn4)
        val btn5 = view.findViewById<Button>(R.id.btn5)
        val btn6 = view.findViewById<Button>(R.id.btn6)
        val btn7 = view.findViewById<Button>(R.id.btn7)
        val btn8 = view.findViewById<Button>(R.id.btn8)
        val btn9 = view.findViewById<Button>(R.id.btn9)
        val btn0 = view.findViewById<Button>(R.id.btn0)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)  // ⌫ 倒退鍵
        val btnClear = view.findViewById<Button>(R.id.btnClear)    // C 清除鍵

        // ---------- 🎨 初始化畫面 ----------
        updateDisplayedTransactionTime()  // 📅 顯示預設日期（今天）
        updateTypeUI(isIncome = false)    // 🔴 預設為支出模式（紅色）
        clearAmount()                     // 💰 金額歸零

        // ---------- 📥 載入資料 ----------
        // 等待資料庫初始化完成後，載入帳戶和分類
        repository.ensureInitialized {
            loadAccountsToDropdown()                      // 💳 載入帳戶選單
            loadCategoriesToDropdown(selectedType())      // 🏷️ 載入分類選單（根據支出/收入類型）
        }

        // ---------- 🎮 設定事件監聽 ----------

        // 🔘 切換支出/收入
        rgType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbExpense -> {  // 支出模式
                    clearInputFields()                                    // 🧹 清空輸入欄位
                    updateTypeUI(isIncome = false)                        // 🔴 更新 UI（紅色）
                    loadCategoriesToDropdown(TransactionCategoryCatalog.TYPE_EXPENSE)  // 🏷️ 載入支出分類
                    loadAccountsToDropdown()                              // 💳 重新載入帳戶
                }
                R.id.rbIncome -> {   // 收入模式
                    clearInputFields()                                    // 🧹 清空輸入欄位
                    updateTypeUI(isIncome = true)                         // 🟢 更新 UI（綠色）
                    loadCategoriesToDropdown(TransactionCategoryCatalog.TYPE_INCOME)   // 🏷️ 載入收入分類
                    loadAccountsToDropdown()                              // 💳 重新載入帳戶
                }
            }
        }

        // 🔢 數字鍵盤按鈕事件（每個數字都做一樣的事）
        btn1.setOnClickListener { appendNumber("1") }
        btn2.setOnClickListener { appendNumber("2") }
        btn3.setOnClickListener { appendNumber("3") }
        btn4.setOnClickListener { appendNumber("4") }
        btn5.setOnClickListener { appendNumber("5") }
        btn6.setOnClickListener { appendNumber("6") }
        btn7.setOnClickListener { appendNumber("7") }
        btn8.setOnClickListener { appendNumber("8") }
        btn9.setOnClickListener { appendNumber("9") }
        btn0.setOnClickListener { appendNumber("0") }

        // ⌫ 倒退鍵：刪除最後一個數字
        btnDelete.setOnClickListener {
            if (currentAmount.isNotEmpty()) {
                currentAmount = currentAmount.dropLast(1)  // 砍掉最後一個字符
                syncAmountViews()  // 更新畫面
            }
        }

        // C 清除鍵：把金額歸零
        btnClear.setOnClickListener {
            clearAmount()
        }

        // ❌ 取消按鈕：清空所有輸入欄位
        btnCancel.setOnClickListener {
            clearInputFields()
        }

        // 📅 點擊日期 → 打開日期選擇器
        tvCurrentTime.setOnClickListener {
            showTransactionDatePicker()
        }

        // 💾 儲存按鈕 → 檢查資料並儲存
        btnSave.setOnClickListener {
            saveTransaction()
        }
    }

    // ========== 📅 日期相關函式 ==========

    /**
     * 📅 更新畫面上顯示的日期
     *
     * 白話：把 Calendar 物件變成「4/6」這種簡短格式顯示出來
     */
    private fun updateDisplayedTransactionTime() {
        tvCurrentTime.text = "日期：${transactionDisplayFormat.format(selectedTransactionCalendar.time)}"
    }

    /**
     * 🔄 將日期重置為今天
     *
     * 使用情境：切換支出/收入後，也順便重置日期？
     * （目前沒用到，但留著以備不時之需）
     */
    private fun resetTransactionDateToNow() {
        selectedTransactionCalendar.time = Date()
        updateDisplayedTransactionTime()
    }

    /**
     * 📅 顯示日期選擇器（讓使用者挑選日期）
     *
     * 白話：跳出一個日曆小視窗，使用者可以選年、月、日
     *
     * 流程：
     * 1. 彈出 DatePickerDialog
     * 2. 使用者選好日期
     * 3. 更新 selectedTransactionCalendar 和畫面顯示
     */
    private fun showTransactionDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // ✅ 使用者選好日期後執行
                selectedTransactionCalendar.set(Calendar.YEAR, year)
                selectedTransactionCalendar.set(Calendar.MONTH, month)
                selectedTransactionCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDisplayedTransactionTime()
            },
            selectedTransactionCalendar.get(Calendar.YEAR),
            selectedTransactionCalendar.get(Calendar.MONTH),
            selectedTransactionCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ========== 💰 金額相關函式 ==========

    /**
     * 🔢 追加數字到金額
     *
     * 白話：使用者按了數字鍵，就把那個數字拼到金額後面
     *
     * 例如：currentAmount = "15"，appendNumber("0") → "150"
     *
     * @param number 要追加的數字（"0" 到 "9"）
     */
    private fun appendNumber(number: String) {
        currentAmount += number
        syncAmountViews()
    }

    /**
     * 🔄 同步金額顯示（把 currentAmount 顯示到畫面上）
     *
     * 白話：把內部存的金額字串，顯示到大大的金額 TextView 上
     *
     * 特別處理：如果沒有輸入任何數字，顯示「0」
     */
    private fun syncAmountViews() {
        tvAmount.text = if (currentAmount.isEmpty()) "0" else currentAmount
    }
    // ========== 🏷️ 載入分類下拉選單 ==========

    /**
     * 🏷️ 載入分類下拉選單
     *
     * 白話：財務管家去倉庫把所有分類搬出來，
     *       做成下拉選單讓使用者選擇～
     *
     * 分類會根據「支出」或「收入」顯示不同的內容：
     * - 支出模式：餐飲、交通、購物、娛樂...
     * - 收入模式：薪水、獎金、紅包、其他...
     *
     * 顯示格式：在分類名稱前面加上可愛的 emoji 圖示
     * 例如：「🍽 餐飲」、「🚗 交通」、「💰 薪水」
     *
     * @param type 類型（「支出」或「收入」）
     * @param selectedName 預選的分類名稱（編輯時使用，新增時為 null）
     */
    private fun loadCategoriesToDropdown(
        type: String,
        selectedName: String? = null
    ) {
        // 🔍 檢查 Fragment 是否還活著（避免畫面關了還在做事）
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 📖 從資料庫讀取分類（在背景執行）
            val categories = withContext(Dispatchers.IO) {
                repository.getCategories(type)
            }

            // 🔍 再次檢查 Fragment 是否還活著
            if (view == null || !isAdded) return@launch

            // 💾 存到暫存區（後面 resolveName 會用到）
            currentCategories = categories

            // 🎨 把分類轉成「🍽 餐飲」這樣的顯示文字
            val labels = categories.map(TransactionCategoryFormatter::toDisplayLabel)

            // 📋 建立下拉選單的轉接器（ArrayAdapter）
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,  // Android 內建的選單樣式
                labels
            )
            actCategory.setAdapter(adapter)

            // ✅ 設定預設選中的分類
            // 優先順序：
            // 1. 如果有指定 selectedName，就選那個
            // 2. 否則選第一個分類
            val selectedCategory = selectedName
                ?.let { name -> categories.firstOrNull { it.name == name } }
                ?: categories.firstOrNull()

            // 把選中的分類顯示在輸入框（不加 emoji 的話會解析失敗）
            actCategory.setText(
                selectedCategory?.let(TransactionCategoryFormatter::toDisplayLabel).orEmpty(),
                false  // false = 不觸發自動完成
            )
        }
    }

    // ========== 💳 載入帳戶下拉選單 ==========

    /**
     * 💳 載入帳戶下拉選單
     *
     * 白話：財務管家去倉庫把所有錢包（帳戶）搬出來，
     *       做成下拉選單讓使用者選擇要從哪個錢包扣錢～
     *
     * 特別處理：
     * - 如果沒有任何帳戶，顯示「未設定帳戶」提示
     * - 使用者需要先去「資產」頁面新增帳戶才能記帳
     */
    private fun loadAccountsToDropdown() {
        // 🔍 檢查 Fragment 是否還活著
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 📖 從資料庫讀取帳戶列表（在背景執行）
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            // 🔍 再次檢查 Fragment 是否還活著
            if (view == null || !isAdded) return@launch

            // 💾 存到暫存區（餘額檢查會用到）
            accountList.clear()
            accountList.addAll(accounts)

            // 🚨 如果沒有帳戶，顯示「未設定帳戶」提示
            // 白話：使用者還沒新增任何錢包，要提醒他去資產頁面新增
            val accountNames = if (accountList.isEmpty()) {
                listOf("未設定帳戶")
            } else {
                accountList.map { it.name }
            }

            // 📋 建立下拉選單的轉接器
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                accountNames
            )
            actAccount.setAdapter(adapter)

            // ✅ 預設選中第一個帳戶（如果有）
            actAccount.setText(accountNames.firstOrNull() ?: "", false)
        }
    }

    // ========== 🎨 UI 樣式更新 ==========

    /**
     * 🎨 更新 UI 顏色（支出紅色，收入綠色）
     *
     * 白話：根據是「支出」還是「收入」，改變文字的顏色
     *
     * 顏色心理學：
     * - 🔴 紅色：警告、花錢的感覺（支出）
     * - 🟢 綠色：開心、賺錢的感覺（收入）
     *
     * 這樣使用者一眼就能看出現在是什麼模式！
     *
     * @param isIncome true = 收入模式（綠色），false = 支出模式（紅色）
     */
    private fun updateTypeUI(isIncome: Boolean) {
        if (isIncome) {
            // 💚 收入模式：綠色
            tvType.text = "收入"
            tvType.setTextColor(Color.parseColor("#2E7D32"))  // 深綠色
            tvNt.setTextColor(Color.parseColor("#2E7D32"))
            tvAmount.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            // ❤️ 支出模式：紅色
            tvType.text = "支出"
            tvType.setTextColor(Color.parseColor("#C62828"))  // 深紅色
            tvNt.setTextColor(Color.parseColor("#C62828"))
            tvAmount.setTextColor(Color.parseColor("#C62828"))
        }
        tvAmount.text = "0"  // 切換時金額歸零（貼心）
    }

    // ========== 💰 金額相關輔助函式 ==========

    /**
     * 🧹 清除金額
     *
     * 白話：把內部存的金額字串清空，畫面上的金額顯示「0」
     */
    private fun clearAmount() {
        currentAmount = ""   // 清空內部字串
        tvAmount.text = "0"  // 畫面顯示 0
    }

    /**
     * 🧹 清除所有輸入欄位（金額、備註、分類、日期重置）
     *
     * 白話：按下「取消」按鈕時，把所有欄位恢復成初始狀態
     *
     * 清除項目：
     * - 金額 → 0
     * - 備註 → 空白
     * - 分類 → 清空
     * - 日期 → 重置為今天
     * - 帳戶 → 重新載入（確保是最新的）
     */
    private fun clearInputFields() {
        clearAmount()                    // 💰 金額歸零
        etNote.setText("")              // 📝 清空備註
        actCategory.setText("", false)   // 🏷️ 清空分類
        resetTransactionDateToNow()      // 📅 重置日期為今天
        loadAccountsToDropdown()         // 💳 重新載入帳戶（確保是最新的）
    }

    // ========== 💰 餘額檢查相關 ==========

    /**
     * 🔍 取得指定帳戶的餘額
     *
     * @param accountName 帳戶名稱（例如「現金」）
     * @return 該帳戶的餘額，如果找不到則回傳 null
     */
    private fun getAccountBalance(accountName: String): Int? {
        return accountList.firstOrNull { it.name == accountName }?.balance
    }

    /**
     * ✅ 檢查帳戶餘額是否足夠（支出才需要檢查）
     *
     * 白話：要花錢之前，先看看錢包裡有沒有那麼多錢！
     *
     * @param accountName 帳戶名稱
     * @param amount 要花費的金額
     * @return true = 餘額足夠，false = 錢不夠
     */
    private fun hasEnoughBalance(accountName: String, amount: Int): Boolean {
        val currentBalance = getAccountBalance(accountName) ?: return false
        return currentBalance >= amount
    }

    // ========== 💾 儲存交易（核心中的核心！）==========

    /**
     * 💾 儲存交易
     *
     * 這是整個頁面最重要的方法！
     *
     * 流程（像飛機起飛前的檢查清單）：
     * 1️⃣ 取得使用者輸入的所有資料
     * 2️⃣ 檢查分類有沒有選
     * 3️⃣ 檢查金額有沒有輸入、是不是正整數
     * 4️⃣ 檢查帳戶有沒有選
     * 5️⃣ 如果是支出，檢查帳戶餘額夠不夠
     * 6️⃣ 全部通過！存到資料庫
     * 7️⃣ 儲存成功後回到首頁
     *
     * 就像飛機駕駛員起飛前要檢查一堆項目，
     * 全部 ✅ 才能起飛（儲存）！
     */
    private fun saveTransaction() {
        // ========== 📝 步驟 1：取得使用者輸入的資料 ==========

        // 🔘 取得類型（支出/收入）
        val type = if (rgType.checkedRadioButtonId == R.id.rbIncome) "收入" else "支出"

        // 🏷️ 取得分類（把「🍽 餐飲」還原成「餐飲」）
        val category = TransactionCategoryFormatter.resolveName(
            actCategory.text.toString(),
            currentCategories
        )

        // 📝 取得備註（去前後空白）
        val note = etNote.text.toString().trim()

        // 📅 取得時間（完整格式：yyyy/MM/dd HH:mm）
        // 注意：分鐘部分會用當下的時間（因為 DatePicker 只能選日期）
        val time = transactionDateFormat.format(selectedTransactionCalendar.time)

        // 💳 取得帳戶名稱
        val accountName = actAccount.text.toString().trim()

        // ========== 🚨 步驟 2：檢查分類 ==========
        if (category.isEmpty()) {
            Toast.makeText(requireContext(), "請先選擇分類", Toast.LENGTH_SHORT).show()
            return  // ❌ 停機！不能起飛！
        }

        // ========== 🚨 步驟 3：檢查金額 ==========
        if (currentAmount.isEmpty()) {
            Toast.makeText(requireContext(), "請先輸入金額", Toast.LENGTH_SHORT).show()
            return  // ❌ 停機！
        }

        val amountInt = currentAmount.toIntOrNull()
        if (amountInt == null || amountInt <= 0) {
            Toast.makeText(requireContext(), "請輸入正確金額", Toast.LENGTH_SHORT).show()
            return  // ❌ 停機！
        }

        // ========== 🚨 步驟 4：檢查帳戶 ==========
        if (accountName.isEmpty() || accountName == "未設定帳戶") {
            Toast.makeText(requireContext(), "請先到資產頁新增帳戶", Toast.LENGTH_SHORT).show()
            return  // ❌ 停機！
        }

        // ========== 🚨 步驟 5：如果是支出，檢查餘額是否足夠 ==========
        if (type == "支出") {
            val currentBalance = getAccountBalance(accountName)

            if (currentBalance == null) {
                Toast.makeText(requireContext(), "找不到所選帳戶", Toast.LENGTH_SHORT).show()
                return  // ❌ 停機！
            }

            if (!hasEnoughBalance(accountName, amountInt)) {
                // 💸 錢不夠！顯示友善的錯誤訊息
                Toast.makeText(
                    requireContext(),
                    "$accountName 餘額不足，目前只剩 NT$ $currentBalance",
                    Toast.LENGTH_LONG
                ).show()
                return  // ❌ 停機！
            }
        }

        // ========== ✅ 步驟 6：全部檢查通過！儲存到資料庫 ==========
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 🏃 派小弟去背景執行儲存任務
            val result = withContext(Dispatchers.IO) {
                repository.addTransaction(
                    type = type,
                    category = category,
                    amount = amountInt,
                    note = note,
                    time = time,
                    accountName = accountName
                )
            }

            // 🔍 檢查 Fragment 是否還活著
            if (view == null || !isAdded) return@launch

            // 📢 顯示結果訊息
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "儲存成功" else "儲存失敗",
                Toast.LENGTH_SHORT
            ).show()

            // 🎉 步驟 7：儲存成功後回到首頁
            if (result.success) {
                (requireActivity() as? MainActivity)?.showHomeFragment()
                // 白話：跳回「首頁」頁面，讓使用者看到新記的帳
            }
        }
    }

    // ========== 🔍 輔助函式 ==========

    /**
     * 🔍 取得目前選中的類型（支出或收入）
     *
     * @return "expense" 或 "income"（符合 TransactionCategoryCatalog 的常數）
     */
    private fun selectedType(): String {
        return if (rgType.checkedRadioButtonId == R.id.rbIncome) {
            TransactionCategoryCatalog.TYPE_INCOME   // "income"
        } else {
            TransactionCategoryCatalog.TYPE_EXPENSE  // "expense"
        }
    }
}