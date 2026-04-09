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

// 新增交易頁面：
// 讓使用者新增一筆記帳（收入或支出），包含金額鍵盤、分類選擇、帳戶選擇、備註、日期
class AddTransactionFragment : Fragment(R.layout.fragment_add_transaction) {

    // 畫面元件宣告
    private lateinit var btnCancel: Button           // 取消按鈕
    private lateinit var btnSave: Button             // 儲存按鈕
    private lateinit var tvCurrentTime: TextView     // 顯示當前選擇的日期
    private lateinit var tvNt: TextView              // 顯示「NT$」符號
    private lateinit var tvType: TextView            // 顯示「支出」或「收入」
    private lateinit var tvAmount: TextView          // 顯示金額數字
    private lateinit var etNote: EditText            // 備註輸入框
    private lateinit var actCategory: AppCompatAutoCompleteTextView  // 分類下拉選單
    private lateinit var actAccount: AppCompatAutoCompleteTextView  // 帳戶下拉選單
    private lateinit var rgType: RadioGroup          // 支出/收入切換按鈕組

    // 當前輸入的金額（字串形式，因為要逐步組裝）
    private var currentAmount = ""

    // 暫存帳戶列表（從資料庫讀取後存在這裡，用來檢查餘額）
    private val accountList = mutableListOf<AssetAccount>()

    // 暫存分類列表
    private var currentCategories = emptyList<TransactionCategoryDefinition>()

    // 資料倉庫
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 選擇的交易日期（預設為今天）
    private val selectedTransactionCalendar = Calendar.getInstance()

    // 日期格式化工具
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())  // 完整格式
    private val transactionDisplayFormat = SimpleDateFormat("M/d", Locale.getDefault())           // 顯示用（例如「4/6」）

    // 當畫面建立完成時呼叫
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 綁定畫面元件
        tvType = view.findViewById(R.id.tvType)
        tvAmount = view.findViewById(R.id.tvAmount)
        tvNt = view.findViewById(R.id.tvNt)
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime)

        etNote = view.findViewById(R.id.etNote)
        actCategory = view.findViewById(R.id.actCategory)
        actAccount = view.findViewById(R.id.actAccount)
        rgType = view.findViewById(R.id.rgType)

        btnCancel = view.findViewById(R.id.btnCancel)
        btnSave = view.findViewById(R.id.btnSave)

        // 數字鍵盤按鈕
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
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
        val btnClear = view.findViewById<Button>(R.id.btnClear)

        // 初始化畫面
        updateDisplayedTransactionTime()  // 顯示預設日期
        updateTypeUI(isIncome = false)    // 預設為支出模式（紅色）
        clearAmount()                     // 金額歸零

        // 等待資料庫初始化完成後，載入帳戶和分類
        repository.ensureInitialized {
            loadAccountsToDropdown()
            loadCategoriesToDropdown(selectedType())
        }

        // 切換支出/收入
        rgType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbExpense -> {
                    clearInputFields()
                    updateTypeUI(isIncome = false)
                    loadCategoriesToDropdown(TransactionCategoryCatalog.TYPE_EXPENSE)
                    loadAccountsToDropdown()
                }
                R.id.rbIncome -> {
                    clearInputFields()
                    updateTypeUI(isIncome = true)
                    loadCategoriesToDropdown(TransactionCategoryCatalog.TYPE_INCOME)
                    loadAccountsToDropdown()
                }
            }
        }

        // 數字鍵盤
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

        // 刪除最後一個數字（倒退鍵）
        btnDelete.setOnClickListener {
            if (currentAmount.isNotEmpty()) {
                currentAmount = currentAmount.dropLast(1)
                syncAmountViews()
            }
        }

        // 清除所有數字（C 鍵）
        btnClear.setOnClickListener {
            clearAmount()
        }

        // 取消按鈕：清空所有輸入欄位
        btnCancel.setOnClickListener {
            clearInputFields()
        }

        // 點擊日期 → 打開日期選擇器
        tvCurrentTime.setOnClickListener {
            showTransactionDatePicker()
        }

        // 儲存按鈕 → 檢查資料並儲存
        btnSave.setOnClickListener {
            saveTransaction()
        }
    }

    // 更新畫面上顯示的日期
    private fun updateDisplayedTransactionTime() {
        tvCurrentTime.text = "日期：${transactionDisplayFormat.format(selectedTransactionCalendar.time)}"
    }

    // 將日期重置為今天
    private fun resetTransactionDateToNow() {
        selectedTransactionCalendar.time = Date()
        updateDisplayedTransactionTime()
    }

    // 顯示日期選擇器（讓使用者挑選日期）
    private fun showTransactionDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
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

    // 追加數字到金額
    private fun appendNumber(number: String) {
        currentAmount += number
        syncAmountViews()
    }

    // 同步金額顯示（把 currentAmount 顯示到畫面上）
    private fun syncAmountViews() {
        tvAmount.text = if (currentAmount.isEmpty()) "0" else currentAmount
    }

    // 載入分類下拉選單
    private fun loadCategoriesToDropdown(
        type: String,
        selectedName: String? = null
    ) {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) {
                repository.getCategories(type)
            }

            if (view == null || !isAdded) return@launch

            currentCategories = categories
            // 把分類轉成「🍽 餐飲」這樣的顯示文字
            val labels = categories.map(TransactionCategoryFormatter::toDisplayLabel)
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                labels
            )
            actCategory.setAdapter(adapter)

            // 設定預設選中的分類（如果沒有指定就選第一個）
            val selectedCategory = selectedName
                ?.let { name -> categories.firstOrNull { it.name == name } }
                ?: categories.firstOrNull()

            actCategory.setText(
                selectedCategory?.let(TransactionCategoryFormatter::toDisplayLabel).orEmpty(),
                false
            )
        }
    }

    // 載入帳戶下拉選單
    private fun loadAccountsToDropdown() {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            if (view == null || !isAdded) return@launch

            accountList.clear()
            accountList.addAll(accounts)

            // 如果沒有帳戶，顯示「未設定帳戶」提示
            val accountNames = if (accountList.isEmpty()) {
                listOf("未設定帳戶")
            } else {
                accountList.map { it.name }
            }

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                accountNames
            )
            actAccount.setAdapter(adapter)
            actAccount.setText(accountNames.firstOrNull() ?: "", false)
        }
    }

    // 更新 UI 顏色（支出紅色，收入綠色）
    private fun updateTypeUI(isIncome: Boolean) {
        if (isIncome) {
            tvType.text = "收入"
            tvType.setTextColor(Color.parseColor("#2E7D32"))  // 綠色
            tvNt.setTextColor(Color.parseColor("#2E7D32"))
            tvAmount.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvType.text = "支出"
            tvType.setTextColor(Color.parseColor("#C62828"))  // 紅色
            tvNt.setTextColor(Color.parseColor("#C62828"))
            tvAmount.setTextColor(Color.parseColor("#C62828"))
        }
        tvAmount.text = "0"
    }

    // 清除金額
    private fun clearAmount() {
        currentAmount = ""
        tvAmount.text = "0"
    }

    // 清除所有輸入欄位（金額、備註、分類、日期重置）
    private fun clearInputFields() {
        clearAmount()
        etNote.setText("")
        actCategory.setText("", false)
        resetTransactionDateToNow()
        loadAccountsToDropdown()
    }

    // 取得指定帳戶的餘額
    private fun getAccountBalance(accountName: String): Int? {
        return accountList.firstOrNull { it.name == accountName }?.balance
    }

    // 檢查帳戶餘額是否足夠（支出才需要檢查）
    private fun hasEnoughBalance(accountName: String, amount: Int): Boolean {
        val currentBalance = getAccountBalance(accountName) ?: return false
        return currentBalance >= amount
    }

    // 儲存交易
    private fun saveTransaction() {
        // 1.取得使用者輸入的資料
        val type = if (rgType.checkedRadioButtonId == R.id.rbIncome) "收入" else "支出"
        val category = TransactionCategoryFormatter.resolveName(
            actCategory.text.toString(),
            currentCategories
        )
        val note = etNote.text.toString().trim()
        val time = transactionDateFormat.format(selectedTransactionCalendar.time)
        val accountName = actAccount.text.toString().trim()

        // 2.檢查分類
        if (category.isEmpty()) {
            Toast.makeText(requireContext(), "請先選擇分類", Toast.LENGTH_SHORT).show()
            return
        }

        // 3.檢查金額
        if (currentAmount.isEmpty()) {
            Toast.makeText(requireContext(), "請先輸入金額", Toast.LENGTH_SHORT).show()
            return
        }

        val amountInt = currentAmount.toIntOrNull()
        if (amountInt == null || amountInt <= 0) {
            Toast.makeText(requireContext(), "請輸入正確金額", Toast.LENGTH_SHORT).show()
            return
        }

        // 4.檢查帳戶
        if (accountName.isEmpty() || accountName == "未設定帳戶") {
            Toast.makeText(requireContext(), "請先到資產頁新增帳戶", Toast.LENGTH_SHORT).show()
            return
        }

        // 5.如果是支出，檢查餘額是否足夠
        if (type == "支出") {
            val currentBalance = getAccountBalance(accountName)

            if (currentBalance == null) {
                Toast.makeText(requireContext(), "找不到所選帳戶", Toast.LENGTH_SHORT).show()
                return
            }

            if (!hasEnoughBalance(accountName, amountInt)) {
                Toast.makeText(
                    requireContext(),
                    "$accountName 餘額不足，目前只剩 NT$ $currentBalance",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // 6.儲存到資料庫
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
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

            if (view == null || !isAdded) return@launch

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "儲存成功" else "儲存失敗",
                Toast.LENGTH_SHORT
            ).show()

            // 儲存成功後回到首頁
            if (result.success) {
                (requireActivity() as? MainActivity)?.showHomeFragment()
            }
        }
    }

    // 取得目前選中的類型（支出或收入）
    private fun selectedType(): String {
        return if (rgType.checkedRadioButtonId == R.id.rbIncome) {
            TransactionCategoryCatalog.TYPE_INCOME
        } else {
            TransactionCategoryCatalog.TYPE_EXPENSE
        }
    }
}
