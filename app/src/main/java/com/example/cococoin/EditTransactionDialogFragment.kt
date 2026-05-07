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

/**
 * ✏️【編輯交易的對話框】
 *
 * 想像你在記帳 App 裡點擊一筆「午餐 150 元」，想要改成「晚餐 200 元」
 * 這時候就會跳出這個對話框，就像一個「修改訂單的小櫃檯」：
 *
 * ┌─────────────────────────────┐
 * │  ✏️ 編輯交易                  │
 * │  ┌─────────────────────┐    │
 * │  │ 分類：🍽 餐飲        │ ▼  │  ← 可以改類別
 * │  │ 金額：150           │    │  ← 可以改數字
 * │  │ 備註：跟同事吃飯     │    │  ← 可以改備忘錄
 * │  │ 時間：2026/04/06    │ 📅 │  ← 可以改日期
 * │  │ 帳戶：現金          │ ▼  │  ← 可以改從哪個錢包扣
 * │  └─────────────────────┘    │
 * │  [取消]           [儲存]     │
 * └─────────────────────────────┘
 *
 * 貼心功能：改了金額還會自動檢查錢包餘額夠不夠！
 */
class EditTransactionDialogFragment : DialogFragment() {

    // ---------- 📦 資料暫存區（編輯時的筆記本）----------

    /**
     * 📒 帳戶列表暫存區
     * 從資料庫讀出來後先放這裡，用來檢查餘額夠不夠
     * 就像你錢包裡有幾個皮夾：現金、信用卡、Line Pay...
     */
    private val accountList = mutableListOf<AssetAccount>()

    /**
     * 🏷️ 分類列表暫存區
     * 用來顯示下拉選單（餐飲、交通、購物...）
     * 而且會根據「支出」或「收入」顯示不同的分類
     */
    private var currentCategories = emptyList<TransactionCategoryDefinition>()

    /**
     * 🗄️ 資料倉庫（存取資料庫）
     * lazy 的意思是：只有當「第一次用到」的時候才會建立
     * 就像你家的儲藏室，要用才開門，平常不浪費力氣
     *
     * NONE 表示：不特別做執行緒安全檢查（因為我們有把握只在主執行緒用）
     */
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    /**
     * ⏰ 時間轉換魔法師
     * 把日期變成「2026/04/06 14:30」這種人類看得懂的格式
     */
    private val transactionDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    // ---------- 🎬 建立對話框（開店營業）----------

    /**
     * 建立對話框（像「對話框的出生證明」）
     *
     * 當你呼叫 newInstance() 時，Android 會自動呼叫這個方法
     * 這裡是整個對話框的核心：把設計圖（XML）變成實際的畫面
     *
     * @param savedInstanceState 之前儲存的狀態（手機旋轉時可能用到）
     * @return 一個華麗的對話框
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // 🎨 載入對話框的設計圖（dialog_edit_transaction.xml）
        // 就像請設計師畫好的裝修圖，現在要拿來蓋房子了
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_edit_transaction, null)

        // ---------- 📦 從包裹裡取出原本的交易資料 ----------
        // 這些資料是透過 newInstance() 放進來的，現在要拿出來用
        val id = requireArguments().getInt(ARG_ID)                          // 交易編號（身分證字號）
        val type = requireArguments().getString(ARG_TYPE, "")               // 支出 / 收入
        val category = requireArguments().getString(ARG_CATEGORY, "")       // 分類（餐飲、交通...）
        val amount = requireArguments().getInt(ARG_AMOUNT)                  // 金額
        val note = requireArguments().getString(ARG_NOTE, "")               // 備註（吃什麼、買什麼）
        val time = requireArguments().getString(ARG_TIME, "")               // 時間
        val accountName = requireArguments().getString(ARG_ACCOUNT, "未指定帳戶")  // 從哪個帳戶扣錢

        // ---------- 🎮 跟畫面上的元件牽紅線 ----------
        // 從設計圖裡找到對應的元件，準備操控它們

        // 📝 輸入框們
        val actEditCategory = view.findViewById<AutoCompleteTextView>(R.id.actEditCategory)  // 分類下拉選單（可以打字+選取）
        val etAmount = view.findViewById<EditText>(R.id.etAmount)                           // 金額輸入框
        val etNote = view.findViewById<EditText>(R.id.etNote)                               // 備註輸入框
        val tvEditTime = view.findViewById<TextView>(R.id.tvEditTime)                       // 時間顯示（可點擊打開日曆）
        val actEditAccount = view.findViewById<AutoCompleteTextView>(R.id.actEditAccount)   // 帳戶下拉選單
        val btnSave = view.findViewById<Button>(R.id.btnSave)                               // 💾 儲存按鈕

        // 🗓️ 把原本的時間字串轉成 Calendar 物件（方便修改日期）
        // Calendar 就像一台萬年曆機器，可以輕鬆加減天數、月份
        val selectedTransactionCalendar = parseTransactionCalendar(time)

        // ---------- 📋 載入選單資料（從資料庫讀取）----------

        // 載入分類選單（根據支出/收入類型顯示對應的分類）
        // 例如：支出模式會顯示「餐飲、交通、購物...」
        //      收入模式會顯示「薪水、獎金、紅包...」
        loadCategories(actEditCategory, type, category)

        // 把原本的資料填到畫面上（像預先填好的表單）
        etAmount.setText(amount.toString())
        etNote.setText(note)
        tvEditTime.text = formatTransactionTime(selectedTransactionCalendar)

        // ---------- 📅 點擊時間 → 打開日期選擇器 ----------
        // 白話：使用者點一下時間文字，就會跳出一個日曆小視窗讓選日期
        tvEditTime.setOnClickListener {
            showTransactionDatePicker(selectedTransactionCalendar, tvEditTime)
        }

        // 載入帳戶選單（現金、信用卡、銀行帳戶...）
        loadAccounts(actEditAccount, accountName)

        // ---------- 🏗️ 建立並顯示對話框 ----------
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // ---------- 💾 儲存按鈕的點擊事件（最核心的邏輯！）----------
        btnSave.setOnClickListener {
            // 1️⃣ 取得使用者輸入的新值（從畫面上的欄位讀取）
            val newCategory = TransactionCategoryFormatter.resolveName(
                actEditCategory.text.toString(),
                currentCategories
            )
            val newAmount = etAmount.text.toString().trim().toIntOrNull() ?: 0  // 如果沒填就當作 0
            val newNote = etNote.text.toString().trim()
            val newAccountName = actEditAccount.text.toString().trim()

            // 2️⃣ 檢查分類是否有選（就像網購下訂單前要選尺寸）
            if (newCategory.isEmpty()) {
                actEditCategory.error = "請選擇分類"  // 顯示紅字錯誤提示
                return@setOnClickListener
            }

            // 3️⃣ 檢查金額是否正確（必須大於 0）
            if (newAmount <= 0) {
                etAmount.error = "請輸入正確金額"
                return@setOnClickListener
            }

            // 4️⃣ 檢查帳戶是否有選（要從哪個錢包扣錢？）
            if (newAccountName.isEmpty() || newAccountName == "未設定帳戶") {
                actEditAccount.error = "請選擇帳戶"
                return@setOnClickListener
            }

            // 5️⃣ 建立「原本的」交易物件（用來計算餘額變動）
            // 白話：把修改前的資料打包起來，等等用來算「原本扣了多少錢」
            val oldTransaction = Transaction(
                id = id,
                type = type,
                category = category,
                amount = amount,
                note = note,
                time = time,
                accountName = accountName
            )

            // 6️⃣ 💰 如果是「支出」，要檢查帳戶餘額是否足夠（這是很貼心的功能！）
            if (type == "支出") {
                // 計算修改後帳戶的「有效餘額」（考慮原本交易的影響）
                // 白話：如果同一筆交易從同一個帳戶修改，要先把原本扣掉的錢加回去再判斷
                val effectiveBalance = getEffectiveBalanceForEdit(oldTransaction, newAccountName)

                if (effectiveBalance == null) {
                    Toast.makeText(requireContext(), "找不到所選帳戶", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 如果餘額不足，顯示錯誤訊息（像 ATM 跟你說錢不夠）
                if (effectiveBalance < newAmount) {
                    Toast.makeText(
                        requireContext(),
                        "$newAccountName 餘額不足，可用 NT$ $effectiveBalance",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
            }

            // 7️⃣ 建立「更新後的」交易物件（包裝修改後的資料）
            val updatedTransaction = Transaction(
                id = id,
                type = type,
                category = newCategory,
                amount = newAmount,
                note = newNote,
                time = formatTransactionTime(selectedTransactionCalendar),
                accountName = newAccountName
            )

            // 8️⃣ 透過 FragmentResult 把修改後的資料傳回去給 HomeFragment
            // 白話：把修改結果放進一個「結果包裹」，寄回給呼叫它的畫面
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
            dismiss()  // 關閉對話框（就像辦完事關上門）
        }

        return dialog
    }

    // ---------- 🛠️ 工具函數區（小幫手們）----------

    /**
     * 🗓️ 把時間字串轉成 Calendar 物件
     *
     * 為什麼要轉？因為 Calendar 很好用，可以輕鬆加減天數、比較日期
     *
     * @param time 時間字串，例如「2026/04/06 14:30」
     * @return Calendar 物件，裡面裝著同樣的時間
     */
    private fun parseTransactionCalendar(time: String): Calendar {
        val calendar = Calendar.getInstance()
        runCatching {
            transactionDateFormat.parse(time)
        }.getOrNull()?.let { parsed ->
            calendar.time = parsed
        }
        return calendar
    }

    /**
     * 📝 把 Calendar 物件轉成「yyyy/MM/dd HH:mm」格式的字串
     *
     * @param calendar 要轉換的日期物件
     * @return 漂亮的時間字串，例如「2026/04/06 14:30」
     */
    private fun formatTransactionTime(calendar: Calendar): String {
        return transactionDateFormat.format(calendar.time)
    }

    /**
     * 📅 顯示日期選擇器（讓使用者挑選日期）
     *
     * 白話：跳出一個日曆小視窗，使用者可以滑動選年、月、日
     *
     * @param calendar 要修改的 Calendar 物件（選完後會更新它）
     * @param timeView 畫面上的 TextView（選完後會更新顯示的文字）
     */
    private fun showTransactionDatePicker(calendar: Calendar, timeView: TextView) {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // ✅ 使用者選好日期後，更新 Calendar 物件
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                // ✨ 更新畫面上顯示的時間
                timeView.text = formatTransactionTime(calendar)
            },
            calendar.get(Calendar.YEAR),      // 預設顯示的年
            calendar.get(Calendar.MONTH),     // 預設顯示的月
            calendar.get(Calendar.DAY_OF_MONTH) // 預設顯示的日
        ).show()
    }

    /**
     * 💳 載入帳戶選單
     *
     * 從資料庫讀取所有帳戶（現金、信用卡、銀行...），顯示在下拉選單
     *
     * @param actEditAccount 下拉選單元件
     * @param selectedAccountName 目前選中的帳戶名稱（要預先選好）
     */
    private fun loadAccounts(
        actEditAccount: AutoCompleteTextView,
        selectedAccountName: String
    ) {
        lifecycleScope.launch {
            // 📖 從資料庫讀取帳戶列表（在背景執行，不卡畫面）
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            // 💾 存到暫存區
            accountList.clear()
            accountList.addAll(accounts)

            // 📋 準備顯示用的帳戶名稱清單（例如：「現金」、「信用卡」）
            val accountNames = if (accountList.isEmpty()) {
                listOf("未設定帳戶")  // 如果沒有帳戶，顯示這個選項
            } else {
                accountList.map { it.name }
            }

            // 🎨 建立下拉選單的轉接器（把資料變成漂亮的選項）
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,  // Android 內建的選單樣式
                accountNames
            )
            actEditAccount.setAdapter(adapter)

            // ✅ 設定目前選中的帳戶（預先選好原本的那個）
            val valueToShow = if (accountNames.contains(selectedAccountName)) {
                selectedAccountName
            } else {
                accountNames.firstOrNull().orEmpty()
            }
            actEditAccount.setText(valueToShow, false)
        }
    }

    /**
     * 🏷️ 載入分類選單
     *
     * 從資料庫讀取分類（餐飲、交通、薪水...），顯示在下拉選單
     * 而且會根據「支出」或「收入」顯示不同的分類
     *
     * @param actEditCategory 下拉選單元件
     * @param type 類型（「支出」或「收入」）
     * @param selectedCategoryName 目前選中的分類名稱
     */
    private fun loadCategories(
        actEditCategory: AutoCompleteTextView,
        type: String,
        selectedCategoryName: String
    ) {
        lifecycleScope.launch {
            // 📖 從資料庫讀取分類（根據支出/收入類型過濾）
            val categories = withContext(Dispatchers.IO) {
                repository.getCategories(type)
            }

            currentCategories = categories

            // 📝 把分類轉成「🍽 餐飲」這樣的顯示文字（加 emoji 更可愛）
            val categoryLabels = categories.map(TransactionCategoryFormatter::toDisplayLabel)

            // 🎨 建立下拉選單的轉接器
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                categoryLabels
            )
            actEditCategory.setAdapter(adapter)

            // ✅ 設定目前選中的分類
            val selectedCategory = categories.firstOrNull { it.name == selectedCategoryName }
            actEditCategory.setText(
                selectedCategory?.let(TransactionCategoryFormatter::toDisplayLabel)
                    ?: selectedCategoryName,
                false
            )
        }
    }

    /**
     * 💰 取得指定帳戶的餘額
     *
     * @param accountName 帳戶名稱（例如「現金」）
     * @return 該帳戶的餘額，如果找不到則回傳 null
     */
    private fun getAccountBalance(accountName: String): Int? {
        return accountList.firstOrNull { it.name == accountName }?.balance
    }

    /**
     * 🧮 計算修改交易後的「有效餘額」（這是精算師等級的功能！）
     *
     * 為什麼需要這個？舉個例子：
     *
     * 原本：從「現金」帳戶支出 100 元買午餐
     * 現在：想要改成支出 200 元買晚餐
     *
     * 如果不考慮原本的 100 元，直接檢查餘額可能會出錯：
     * - 當前餘額：900 元
     * - 新金額：200 元
     * - 900 >= 200 ✅ 顯示可以
     *
     * 但原本已經扣過一次 100 元了！
     * 實際情況應該是：900 + 100（還原）= 1000，再扣 200 = 800
     * 所以要判斷的有效餘額是：1000 元
     *
     * @param oldTransaction 原本的交易（修改前）
     * @param selectedAccountName 新選擇的帳戶名稱
     * @return 該帳戶的有效餘額（考慮原本交易後的結果），如果找不到帳戶則回傳 null
     */
    private fun getEffectiveBalanceForEdit(
        oldTransaction: Transaction,
        selectedAccountName: String
    ): Int? {
        val currentBalance = getAccountBalance(selectedAccountName) ?: return null

        return if (oldTransaction.accountName == selectedAccountName) {
            // 🎯 情況 1：同一個帳戶
            // 白話：原本從這個錢包扣錢，現在還是同一個錢包
            // 要先「還原」原本的扣款，再檢查新金額夠不夠

            if (oldTransaction.type == "支出") {
                currentBalance + oldTransaction.amount  // 支出：把扣掉的加回去
            } else {
                currentBalance - oldTransaction.amount  // 收入：把加進來的扣掉
            }
        } else {
            // 🎯 情況 2：不同帳戶
            // 白話：原本從錢包 A 扣，現在要改成錢包 B 扣
            // 直接用錢包 B 的當前餘額判斷即可
            currentBalance
        }
    }

    // ---------- 📦 常數和工廠方法（對話框的生產線）----------

    companion object {
        const val REQUEST_KEY = "edit_transaction_result"  // 📡 回傳結果的頻道名稱

        // 🔑 資料鑰匙名稱（像包裹上的標籤）
        private const val ARG_ID = "id"
        private const val ARG_TYPE = "type"
        private const val ARG_CATEGORY = "category"
        private const val ARG_AMOUNT = "amount"
        private const val ARG_NOTE = "note"
        private const val ARG_TIME = "time"
        private const val ARG_ACCOUNT = "account"

        /**
         * 🏭 建立對話框的工廠方法
         *
         * 把交易資料包裝成一個「包裹」，放進對話框的信封（Bundle）裡
         * 這樣對話框打開後，就可以從包裹裡拿出原本的資料
         *
         * @param transaction 要編輯的交易物件
         * @return 一個已經裝好資料的 EditTransactionDialogFragment
         *
         * 使用範例：
         * val dialog = EditTransactionDialogFragment.newInstance(myTransaction)
         * dialog.show(parentFragmentManager, "edit_dialog")
         */
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