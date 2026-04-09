package com.example.cococoin

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 編輯交易的對話框：
// 當使用者點擊「編輯」按鈕時，跳出這個對話框讓使用者修改交易內容
class EditTransactionDialogFragment : DialogFragment() {

    // 暫存帳戶列表（用來檢查餘額）
    private val accountList = mutableListOf<AssetAccount>()

    // 暫存分類列表（用來顯示分類下拉選單）
    private var currentCategories = emptyList<TransactionCategoryDefinition>()

    // 資料倉庫（存取資料庫）
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 時間格式化工具（把日期轉成「2026/04/06 14:30」這樣的格式）
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    // 建立對話框（像「對話框的出生證明」）
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // 載入 dialog_edit_transaction.xml 這個畫面設計圖
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_edit_transaction, null)

        // 從信封（arguments）裡拿出外面傳進來的交易資料
        val id = requireArguments().getInt(ARG_ID)
        val type = requireArguments().getString(ARG_TYPE, "")
        val category = requireArguments().getString(ARG_CATEGORY, "")
        val amount = requireArguments().getInt(ARG_AMOUNT)
        val note = requireArguments().getString(ARG_NOTE, "")
        val time = requireArguments().getString(ARG_TIME, "")
        val accountName = requireArguments().getString(ARG_ACCOUNT, "未指定帳戶")

        // 取得畫面中的元件
        val actEditCategory = view.findViewById<AutoCompleteTextView>(R.id.actEditCategory)  // 分類下拉選單
        val etAmount = view.findViewById<EditText>(R.id.etAmount)                           // 金額輸入框
        val etNote = view.findViewById<EditText>(R.id.etNote)                               // 備註輸入框
        val tvEditTime = view.findViewById<TextView>(R.id.tvEditTime)                       // 時間顯示（可點擊）
        val actEditAccount = view.findViewById<AutoCompleteTextView>(R.id.actEditAccount)   // 帳戶下拉選單
        val btnSave = view.findViewById<Button>(R.id.btnSave)                               // 儲存按鈕

        // 把原本的時間字串轉成 Calendar 物件（方便修改日期）
        val selectedTransactionCalendar = parseTransactionCalendar(time)

        // 載入分類選單（根據支出/收入類型顯示對應的分類）
        loadCategories(actEditCategory, type, category)

        // 把原本的資料填到畫面上
        etAmount.setText(amount.toString())
        etNote.setText(note)
        tvEditTime.text = formatTransactionTime(selectedTransactionCalendar)

        // 點擊時間 → 打開日期選擇器
        tvEditTime.setOnClickListener {
            showTransactionDatePicker(selectedTransactionCalendar, tvEditTime)
        }

        // 載入帳戶選單
        loadAccounts(actEditAccount, accountName)

        // 建立對話框
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // 點擊「儲存」按鈕 → 檢查資料並儲存修改
        btnSave.setOnClickListener {
            // 取得使用者輸入的新值
            val newCategory = TransactionCategoryFormatter.resolveName(
                actEditCategory.text.toString(),
                currentCategories
            )
            val newAmount = etAmount.text.toString().trim().toIntOrNull() ?: 0
            val newNote = etNote.text.toString().trim()
            val newAccountName = actEditAccount.text.toString().trim()

            // 檢查分類是否有選
            if (newCategory.isEmpty()) {
                actEditCategory.error = "請選擇分類"
                return@setOnClickListener
            }

            // 檢查金額是否正確（大於 0）
            if (newAmount <= 0) {
                etAmount.error = "請輸入正確金額"
                return@setOnClickListener
            }

            // 檢查帳戶是否有選
            if (newAccountName.isEmpty() || newAccountName == "未設定帳戶") {
                actEditAccount.error = "請選擇帳戶"
                return@setOnClickListener
            }

            // 建立原本的交易物件（用來計算餘額變動）
            val oldTransaction = Transaction(
                id = id,
                type = type,
                category = category,
                amount = amount,
                note = note,
                time = time,
                accountName = accountName
            )

            // 如果是支出，要檢查帳戶餘額是否足夠
            if (type == "支出") {
                // 計算修改後帳戶的有效餘額（考慮原本交易的影響）
                val effectiveBalance = getEffectiveBalanceForEdit(oldTransaction, newAccountName)

                if (effectiveBalance == null) {
                    Toast.makeText(requireContext(), "找不到所選帳戶", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 如果餘額不足，顯示錯誤訊息
                if (effectiveBalance < newAmount) {
                    Toast.makeText(
                        requireContext(),
                        "$newAccountName 餘額不足，可用 NT$ $effectiveBalance",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
            }

            // 建立更新後的交易物件
            val updatedTransaction = Transaction(
                id = id,
                type = type,
                category = newCategory,
                amount = newAmount,
                note = newNote,
                time = formatTransactionTime(selectedTransactionCalendar),
                accountName = newAccountName
            )

            // 透過 FragmentResult 把修改後的資料傳回去給 HomeFragment
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    ARG_ID to updatedTransaction.id,
                    ARG_TYPE to updatedTransaction.type,
                    ARG_CATEGORY to updatedTransaction.category,
                    ARG_AMOUNT to updatedTransaction.amount,
                    ARG_NOTE to updatedTransaction.note,
                    ARG_TIME to updatedTransaction.time,
                    ARG_ACCOUNT to updatedTransaction.accountName
                )
            )
            dismiss()  // 關閉對話框
        }

        return dialog
    }

    // 把時間字串轉成 Calendar 物件
    private fun parseTransactionCalendar(time: String): Calendar {
        val calendar = Calendar.getInstance()
        runCatching {
            transactionDateFormat.parse(time)
        }.getOrNull()?.let { parsed ->
            calendar.time = parsed
        }
        return calendar
    }

    // 把 Calendar 物件轉成「yyyy/MM/dd HH:mm」格式的字串
    private fun formatTransactionTime(calendar: Calendar): String {
        return transactionDateFormat.format(calendar.time)
    }

    // 顯示日期選擇器（讓使用者挑選日期）
    private fun showTransactionDatePicker(calendar: Calendar, timeView: TextView) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // 使用者選好日期後，更新 Calendar 物件
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                // 更新畫面上顯示的時間
                timeView.text = formatTransactionTime(calendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // 載入帳戶選單 ：
    // 從資料庫讀取所有帳戶，顯示在下拉選單讓使用者選擇
    private fun loadAccounts(
        actEditAccount: AutoCompleteTextView,
        selectedAccountName: String
    ) {
        lifecycleScope.launch {
            // 從資料庫讀取帳戶列表（在背景執行）
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            accountList.clear()
            accountList.addAll(accounts)

            // 準備顯示用的帳戶名稱清單
            val accountNames = if (accountList.isEmpty()) {
                listOf("未設定帳戶")
            } else {
                accountList.map { it.name }
            }

            // 建立下拉選單的轉接器
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                accountNames
            )
            actEditAccount.setAdapter(adapter)

            // 設定目前選中的帳戶
            val valueToShow = if (accountNames.contains(selectedAccountName)) {
                selectedAccountName
            } else {
                accountNames.firstOrNull().orEmpty()
            }
            actEditAccount.setText(valueToShow, false)
        }
    }

    // 載入分類選單 ：
    // 從資料庫讀取分類，顯示在下拉選單讓使用者選擇
    private fun loadCategories(
        actEditCategory: AutoCompleteTextView,
        type: String,
        selectedCategoryName: String
    ) {
        lifecycleScope.launch {
            // 從資料庫讀取分類（根據支出/收入類型過濾）
            val categories = withContext(Dispatchers.IO) {
                repository.getCategories(type)
            }

            currentCategories = categories

            // 把分類轉成「🍽 餐飲」這樣的顯示文字
            val categoryLabels = categories.map(TransactionCategoryFormatter::toDisplayLabel)

            // 建立下拉選單的轉接器
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                categoryLabels
            )
            actEditCategory.setAdapter(adapter)

            // 設定目前選中的分類
            val selectedCategory = categories.firstOrNull { it.name == selectedCategoryName }
            actEditCategory.setText(
                selectedCategory?.let(TransactionCategoryFormatter::toDisplayLabel)
                    ?: selectedCategoryName,
                false
            )
        }
    }

    // 取得指定帳戶的餘額
    private fun getAccountBalance(accountName: String): Int? {
        return accountList.firstOrNull { it.name == accountName }?.balance
    }

    // 計算修改交易後的「有效餘額」：
    // 如果要修改的交易原本就是從這個帳戶扣款，要把原本扣掉的金額加回去再判斷，這樣才能正確判斷餘額是否足夠
    private fun getEffectiveBalanceForEdit(
        oldTransaction: Transaction,
        selectedAccountName: String
    ): Int? {
        val currentBalance = getAccountBalance(selectedAccountName) ?: return null

        return if (oldTransaction.accountName == selectedAccountName) {
            // 同一個帳戶：先把原本的金額加回去（復原），再判斷新金額是否足夠
            if (oldTransaction.type == "支出") {
                currentBalance + oldTransaction.amount  // 支出：把扣掉的加回去
            } else {
                currentBalance - oldTransaction.amount  // 收入：把加進來的扣掉
            }
        } else {
            // 不同帳戶：直接用當前餘額判斷
            currentBalance
        }
    }

    // 存放常數和工廠方法
    companion object {
        const val REQUEST_KEY = "edit_transaction_result"  // 回傳結果的頻道名稱

        // 資料鑰匙名稱
        private const val ARG_ID = "id"
        private const val ARG_TYPE = "type"
        private const val ARG_CATEGORY = "category"
        private const val ARG_AMOUNT = "amount"
        private const val ARG_NOTE = "note"
        private const val ARG_TIME = "time"
        private const val ARG_ACCOUNT = "account"

        // 建立對話框並把交易資料裝進去
        fun newInstance(transaction: Transaction): EditTransactionDialogFragment {
            val fragment = EditTransactionDialogFragment()
            val bundle = Bundle().apply {
                putInt(ARG_ID, transaction.id)
                putString(ARG_TYPE, transaction.type)
                putString(ARG_CATEGORY, transaction.category)
                putInt(ARG_AMOUNT, transaction.amount)
                putString(ARG_NOTE, transaction.note)
                putString(ARG_TIME, transaction.time)
                putString(ARG_ACCOUNT, transaction.accountName)
            }
            fragment.arguments = bundle
            return fragment
        }
    }
}