package com.example.cococoin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 💰【資產頁面】
 *
 * 想像你請了一個「財務管家」，這個頁面就是管家的辦公桌：
 *
 * ┌─────────────────────────────────────────────┐
 * │ 📊 預算管理                                  │
 * │ ┌─────────────────────────────────────────┐ │
 * │ │ 2026年4月預算設定                        │ │
 * │ │ 目前預算：NT$ 15,000                    │ │
 * │ │ [          ] 輸入新預算                  │ │
 * │ │ [儲存預算]                               │ │
 * │ └─────────────────────────────────────────┘ │
 * │                                              │
 * │ 💳 帳戶管理                                 │
 * │ ┌─────────────────────────────────────────┐ │
 * │ │ 總資產：NT$ 50,000                      │ │
 * │ │                                          │ │
 * │ │ 現金            NT$ 10,000    [編輯][刪除]│ │
 * │ │ 信用卡          NT$ 30,000    [編輯][刪除]│ │
 * │ │ Line Pay        NT$ 10,000    [編輯][刪除]│ │
 * │ │                                          │ │
 * │ │ 新增帳戶：                               │ │
 * │ │ [帳戶名稱] [初始餘額] [新增]              │ │
 * │ └─────────────────────────────────────────┘ │
 * └─────────────────────────────────────────────┘
 *
 * 功能：
 * 1️⃣ 設定每月預算（控制開銷）
 * 2️⃣ 新增/編輯/刪除帳戶（現金、信用卡、電子支付...）
 * 3️⃣ 顯示總資產（所有帳戶餘額加總）
 * 4️⃣ 帳戶餘額會即時反映記帳的變化
 */
class AssetsFragment : Fragment(R.layout.fragment_assets) {

    // ========== 📊 預算相關元件 ==========

    private lateinit var tvCurrentBudget: TextView     // 💰 顯示目前的預算金額
    private lateinit var etMonthlyBudget: EditText     // ✏️ 輸入新預算的輸入框
    private lateinit var btnSaveBudget: Button         // 💾 儲存預算按鈕
    private lateinit var tvBudgetMonth: TextView       // 📅 顯示目前是哪個月的預算（例如「2026年4月預算設定」）

    // ========== 💳 帳戶相關元件 ==========

    private lateinit var tvTotalAssets: TextView       // 📊 顯示總資產（所有帳戶餘額加總）
    private lateinit var tvEmptyAssets: TextView       // 🦗 沒有帳戶時顯示的提示文字
    private lateinit var layoutAssetAccounts: LinearLayout  // 📋 放帳戶列表的容器（動態新增帳戶卡片）
    private lateinit var etAccountName: EditText       // 🏷️ 新增帳戶的名稱輸入框
    private lateinit var etAccountBalance: EditText    // 💰 新增帳戶的餘額輸入框
    private lateinit var btnAddAccount: Button         // ➕ 新增帳戶按鈕

    // ========== 📦 資料暫存區 ==========

    /**
     * 📋 暫存帳戶列表
     * 從資料庫讀取後存在這裡，避免一直讀取資料庫
     * 也會用來更新畫面
     */
    private val accountList = mutableListOf<AssetAccount>()

    /**
     * 🗄️ 資料倉庫（存取資料庫）
     * lazy：只有第一次用到時才會建立（省記憶體）
     * LazyThreadSafetyMode.NONE：不特別做執行緒安全檢查（因為只在主執行緒用）
     */
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // ========== 🎬 畫面生命週期 ==========

    /**
     * 🎨 當畫面建立完成時呼叫
     *
     * 這裡要做的事情：
     * 1. 把 XML 裡的元件跟程式碼變數牽紅線
     * 2. 設定按鈕的點擊事件
     * 3. 從資料庫載入資料並顯示
     *
     * @param view 畫面的根視圖（整張餐桌）
     * @param savedInstanceState 之前儲存的狀態（手機旋轉時用）
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---------- 🔗 綁定元件（牽紅線）----------

        // 預算區元件
        tvBudgetMonth = view.findViewById(R.id.tvBudgetMonth)
        tvCurrentBudget = view.findViewById(R.id.tvCurrentBudget)
        etMonthlyBudget = view.findViewById(R.id.etMonthlyBudget)
        btnSaveBudget = view.findViewById(R.id.btnSaveBudget)

        // 帳戶區元件
        tvTotalAssets = view.findViewById(R.id.tvTotalAssets)
        tvEmptyAssets = view.findViewById(R.id.tvEmptyAssets)
        layoutAssetAccounts = view.findViewById(R.id.layoutAssetAccounts)

        // 新增帳戶輸入框
        etAccountName = view.findViewById(R.id.etAccountName)

        // 🎯 特殊處理：點擊帳戶名稱輸入框時，讓它獲得焦點並彈出鍵盤
        // 為什麼需要這個？因為有時候點擊 EditText 不會自動彈出鍵盤
        // 這個強制讓它彈出來，提升使用者體驗
        etAccountName.setOnClickListener {
            etAccountName.isFocusableInTouchMode = true  // 允許觸控模式獲得焦點
            etAccountName.requestFocus()                 // 請求焦點（讓游標出現）

            // 📱 強制彈出鍵盤
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etAccountName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        etAccountBalance = view.findViewById(R.id.etAccountBalance)
        btnAddAccount = view.findViewById(R.id.btnAddAccount)

        // ---------- 📅 顯示當前月份 ----------
        showCurrentMonthText()

        // ---------- 🎮 設定按鈕點擊事件 ----------

        // 💾 儲存預算按鈕
        btnSaveBudget.setOnClickListener {
            saveBudget()  // 叫財務管家記住這個月的預算
        }

        // ➕ 新增帳戶按鈕
        btnAddAccount.setOnClickListener {
            addAccount()  // 新增一個錢包（現金、信用卡...）
        }

        // ---------- 📥 載入資料 ----------
        // 等待資料庫初始化完成後，載入所有資料
        // 為什麼要等？因為可能還在跟雲端同步
        repository.ensureInitialized {
            loadAllData()  // 載入預算和帳戶資料
        }
    }

    /**
     * 🔄 當畫面重新顯示時（例如從其他頁面返回），重新載入資料
     *
     * 為什麼需要這個？
     * - 使用者在首頁記了一筆帳，帳戶餘額變了
     * - 回到資產頁面時，要顯示最新的餘額！
     */
    override fun onResume() {
        super.onResume()
        loadAllData()  // 重新載入最新資料
    }

    // ========== 🛠️ 輔助函式 ==========

    /**
     * 📅 取得當前月份的顯示文字（例如「2026年4月」）
     *
     * @return 格式化的月份文字
     */
    private fun getCurrentMonthLabel(): String {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1  // ⚠️ Calendar 的月份從 0 開始（1月=0），所以要 +1
        return "${year}年${month}月"
    }

    // ========== 📥 資料載入區 ==========

    /**
     * 📥 載入所有資料（帳戶列表、當月預算）
     *
     * 這個方法會在：
     * - 畫面初次載入時
     * - 從其他頁面返回時
     * - 新增/編輯/刪除帳戶後
     *
     * 被呼叫，確保畫面永遠顯示最新資料！
     */
    private fun loadAllData() {
        // 🔍 檢查 Fragment 是否還活著（避免畫面關閉了還在做事）
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val now = Calendar.getInstance()

            // 📖 步驟 1：從資料庫讀取帳戶列表（在背景執行）
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            // 📖 步驟 2：從資料庫讀取當月預算
            val budget = withContext(Dispatchers.IO) {
                repository.getMonthlyBudget(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1
                )
            }

            // 🔍 步驟 3：檢查 Fragment 是否還活著（避免畫面關了還更新 UI）
            if (view == null || !isAdded) return@launch

            // 💾 步驟 4：更新暫存列表
            accountList.clear()
            accountList.addAll(accounts)

            // 🎨 步驟 5：更新畫面
            updateAccountList()      // 📋 顯示帳戶列表
            updateAccountView()      // 🦗 顯示/隱藏空狀態提示
            updateTotalAssets()      // 📊 計算並顯示總資產
            showBudget(budget)       // 💰 顯示當月預算
        }
    }

    // ========== 📅 預算相關函式 ==========

    /**
     * 📅 顯示當前月份的標題文字
     *
     * 白話：在畫面上寫「2026年4月預算設定」
     */
    private fun showCurrentMonthText() {
        tvBudgetMonth.text = "${getCurrentMonthLabel()}預算設定"
    }

    /**
     * 💰 顯示預算金額
     *
     * @param budget 預算金額（如果是 0 或負數，顯示「尚未設定」）
     */
    private fun showBudget(budget: Int) {
        tvCurrentBudget.text = if (budget > 0) {
            "NT$ $budget"
        } else {
            "尚未設定"  // 沒設預算就顯示貼心的提示
        }
        etMonthlyBudget.setText("")  // ✨ 清空輸入框，讓使用者可以輸入新的
    }

    /**
     * 💾 儲存預算
     *
     * 流程：
     * 1. 檢查使用者有沒有輸入金額
     * 2. 檢查金額是不是正整數
     * 3. 存到資料庫
     * 4. 更新畫面 + 顯示成功訊息
     */
    private fun saveBudget() {
        val budgetText = etMonthlyBudget.text.toString().trim()

        // 🚨 步驟 1：檢查有沒有輸入金額
        if (budgetText.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入預算金額", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚨 步驟 2：檢查金額是否合法（正整數）
        val budget = budgetText.toIntOrNull()
        if (budget == null || budget < 0) {
            Toast.makeText(requireContext(), "請輸入正確的預算金額", Toast.LENGTH_SHORT).show()
            return
        }

        // 💾 步驟 3：儲存到資料庫
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val now = Calendar.getInstance()

            // 切換到 IO 跑道，執行資料庫操作
            withContext(Dispatchers.IO) {
                repository.saveMonthlyBudget(
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH) + 1,
                    amount = budget
                )
            }

            // 🔍 檢查 Fragment 是否還活著
            if (view == null || !isAdded) return@launch

            // 🎉 步驟 4：更新畫面並顯示成功訊息
            showBudget(budget)
            etMonthlyBudget.clearFocus()  // 讓鍵盤收起來（貼心）
            Toast.makeText(
                requireContext(),
                "${getCurrentMonthLabel()}預算已儲存",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // ========== 💳 帳戶管理相關函式 ==========

    /**
     * ➕ 新增帳戶
     *
     * 白話：財務管家幫你新增一個錢包（現金、信用卡、Line Pay...）
     *
     * 流程：
     * 1️⃣ 檢查帳戶名稱有沒有填
     * 2️⃣ 檢查餘額有沒有填、是不是正整數
     * 3️⃣ 檢查有沒有同名的帳戶（不能有兩個「現金」）
     * 4️⃣ 存到資料庫
     * 5️⃣ 清空輸入框、重新載入畫面
     *
     * 使用範例：
     * 使用者輸入「悠遊卡」、餘額「500」，按下「新增」
     * → 畫面出現「悠遊卡 NT$ 500」
     */
    private fun addAccount() {
        // 📝 步驟 1：取得使用者輸入的內容（去掉前後空白）
        val name = etAccountName.text.toString().trim()
        val balanceText = etAccountBalance.text.toString().trim()

        // 🚨 步驟 2：檢查帳戶名稱不能空白
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入帳戶名稱", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚨 步驟 3：檢查餘額不能空白
        if (balanceText.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入帳戶餘額", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚨 步驟 4：檢查餘額是不是合法的正整數
        val balance = balanceText.toIntOrNull()
        if (balance == null || balance < 0) {
            Toast.makeText(requireContext(), "請輸入正確的帳戶餘額", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚨 步驟 5：檢查是否已有同名帳戶
        // 白話：不能有兩個都叫「現金」的錢包，會搞混！
        if (accountList.any { it.name == name }) {
            Toast.makeText(requireContext(), "已存在相同名稱的帳戶", Toast.LENGTH_SHORT).show()
            return
        }

        // 💾 步驟 6：儲存到資料庫
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 切換到 IO 跑道，執行資料庫操作
            val result = withContext(Dispatchers.IO) {
                repository.addAccount(name, balance)
            }

            // 🔍 檢查 Fragment 是否還活著（避免畫面關了還在做事）
            if (view == null || !isAdded) return@launch

            // 📢 顯示結果訊息
            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "帳戶已新增" else "新增失敗",
                Toast.LENGTH_SHORT
            ).show()

            // ✅ 新增成功就清空輸入框並重新載入資料
            if (result.success) {
                etAccountName.setText("")      // 清空名稱輸入框
                etAccountBalance.setText("")   // 清空餘額輸入框
                loadAllData()                  // 重新載入（畫面會出現新帳戶）
            }
        }
    }

    /**
     * ✏️ 顯示編輯帳戶的對話框
     *
     * 白話：跳出一個小視窗，讓使用者修改錢包的名稱或餘額
     *
     * 流程：
     * 1️⃣ 載入 dialog_edit_account.xml 設計圖
     * 2️⃣ 把原本的帳戶資料填進去（名稱、餘額）
     * 3️⃣ 使用者修改後按「儲存」
     * 4️⃣ 檢查資料是否正確（名稱不能空白、餘額要正數）
     * 5️⃣ 檢查是否與其他帳戶同名（不能跟別人撞名）
     * 6️⃣ 儲存到資料庫、關閉對話框、重新載入畫面
     *
     * @param account 要編輯的帳戶物件
     *
     * 使用範例：
     * 使用者點擊「現金」旁邊的「編輯」按鈕
     * → 跳出小視窗，裡面已經有「現金」和餘額
     * → 改成「新台幣現金」，按儲存
     * → 帳戶名稱變成「新台幣現金」✨
     */
    private fun showEditAccountDialog(account: AssetAccount) {
        // 🎨 步驟 1：載入對話框的設計圖
        // LayoutInflater 就像 3D 列印機，把 XML 變成實際的畫面
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_account, null)

        // 🔗 步驟 2：綁定對話框裡的元件
        val etEditAccountName = dialogView.findViewById<EditText>(R.id.etEditAccountName)
        val etEditAccountBalance = dialogView.findViewById<EditText>(R.id.etEditAccountBalance)

        // 📝 步驟 3：填入原本的資料（預先填好，使用者不用重打）
        etEditAccountName.setText(account.name)
        etEditAccountBalance.setText(account.balance.toString())

        // 🏗️ 步驟 4：建立對話框
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("編輯帳戶")           // 標題
            .setView(dialogView)            // 放入內容畫面
            .setPositiveButton("儲存", null)  // 先設 null，等等再設定點擊行為
            .setNegativeButton("取消", null)  // 取消按鈕（什麼都不做）
            .create()

        // ⚙️ 步驟 5：設定對話框顯示時的額外行為
        // 為什麼要用 setOnShowListener？
        // 因為 AlertDialog 的 PositiveButton 預設會自動關閉對話框
        // 但我們想要「檢查資料正確才關閉」，所以要自己控制
        dialog.setOnShowListener {
            // 取得「儲存」按鈕，覆蓋他的點擊行為
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // 📝 讀取使用者輸入的新值
                val newName = etEditAccountName.text.toString().trim()
                val newBalance = etEditAccountBalance.text.toString().trim().toIntOrNull()

                // 🚨 步驟 6：檢查資料是否正確
                if (newName.isEmpty() || newBalance == null || newBalance < 0) {
                    Toast.makeText(requireContext(), "請輸入正確資料", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener  // 不關閉對話框，讓使用者修正
                }

                // 🚨 步驟 7：檢查是否與其他帳戶同名（排除自己）
                // 白話：不能把「現金」改成「信用卡」，如果已經有「信用卡」了
                if (accountList.any { it.name == newName && it.id != account.id }) {
                    Toast.makeText(requireContext(), "已存在相同名稱的帳戶", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener  // 不關閉對話框，讓使用者修正
                }

                // 💾 步驟 8：儲存修改到資料庫
                val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@setOnClickListener
                lifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.updateAccount(
                            accountId = account.id,
                            newName = newName,
                            newBalance = newBalance
                        )
                    }

                    // 🔍 檢查 Fragment 是否還活著
                    if (view == null || !isAdded) return@launch

                    // 📢 顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳戶已更新" else "更新失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // ✅ 成功就關閉對話框並重新載入資料
                    if (result.success) {
                        dialog.dismiss()  // 關閉對話框
                        loadAllData()     // 重新載入（更新畫面）
                    }
                }
            }
        }

        // 🎬 顯示對話框
        dialog.show()
    }

    /**
     * 🗑️ 刪除帳戶（會先跳出確認視窗）
     *
     * 白話：把一個錢包刪掉（例如不再使用的信用卡）
     *
     * 注意：如果這個帳戶還有交易紀錄，就不能刪除！
     * 因為刪掉後那些交易會不知道從哪個錢包付的～
     *
     * @param accountId 要刪除的帳戶 ID
     *
     * 使用範例：
     * 使用者點擊某個帳戶旁邊的「刪除」按鈕
     * → 跳出確認視窗：「確定要刪除嗎？」
     * → 按「刪除」→ 從畫面消失
     */
    private fun deleteAccount(accountId: Int) {
        // ⚠️ 步驟 1：先跳出確認視窗（避免手滑誤刪）
        AlertDialog.Builder(requireContext())
            .setTitle("刪除帳戶")
            .setMessage("確定要刪除這個帳戶嗎？")
            .setPositiveButton("刪除") { _, _ ->
                // ✅ 使用者確認刪除，開始執行
                val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@setPositiveButton
                lifecycleOwner.lifecycleScope.launch {
                    // 💾 步驟 2：呼叫 Repository 刪除
                    val result = withContext(Dispatchers.IO) {
                        repository.deleteAccount(accountId)
                    }

                    // 🔍 檢查 Fragment 是否還活著
                    if (view == null || !isAdded) return@launch

                    // 📢 步驟 3：顯示結果訊息
                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳戶已刪除" else "刪除失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    // ✅ 步驟 4：刪除成功就重新載入資料
                    if (result.success) {
                        loadAllData()  // 重新載入（畫面會移除被刪除的帳戶）
                    }
                    // 🚨 步驟 5：如果因為還有交易紀錄而失敗，顯示更詳細的提示
                    else if (result.message?.contains("交易紀錄") == true) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("無法刪除帳戶")
                            .setMessage(result.message)  // 顯示：「此帳戶已有交易紀錄使用...」
                            .setPositiveButton("知道了", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)  // 取消按鈕：什麼都不做
            .show()
    }

    // ========== 📋 畫面更新相關函式 ==========

    /**
     * # 🦗 根據是否有帳戶，顯示或隱藏空狀態提示
     *
     * 白話：
     * - 沒有錢包 → 顯示「尚無資產帳戶，請新增」的貼心提示
     * - 有錢包 → 顯示帳戶列表
     */
    private fun updateAccountView() {
        if (accountList.isEmpty()) {
            // 🦗 沒有帳戶：顯示空狀態
            tvEmptyAssets.visibility = View.VISIBLE      // 顯示「尚未新增帳戶」的文字
            layoutAssetAccounts.visibility = View.GONE   // 隱藏帳戶列表（反正空的）
        } else {
            // 💳 有帳戶：顯示列表
            tvEmptyAssets.visibility = View.GONE         // 隱藏空狀態提示
            layoutAssetAccounts.visibility = View.VISIBLE // 顯示帳戶列表
        }
    }

    /**
     * # 📊 計算並顯示總資產（所有帳戶餘額加總）
     *
     * 白話：把所有錢包的錢加起來，告訴你總共存了多少！
     *
     * 公式：總資產 = 現金 + 信用卡餘額 + Line Pay + ... 所有帳戶
     *
     * 注意：信用卡的餘額通常是「你欠銀行多少」（負債）
     *       但在這個 App 裡，我們用「剩餘額度」的概念來算
     *       所以還是正數相加～
     */
    private fun updateTotalAssets() {
        // 💰 把所有帳戶的餘額加起來
        val total = accountList.sumOf { it.balance }
        // 📝 顯示在畫面上
        tvTotalAssets.text = "NT$ $total"
    }

    /**
     * 📋 動態建立帳戶列表（每個帳戶顯示成一個項目）
     *
     * 白話：財務管家把每個錢包製作成一張「錢包卡片」，
     *       然後整齊地排列在畫面上～
     *
     * 每張卡片包含：
     * - 圖示（根據帳戶名稱自動配顏色和圖案）
     * - 帳戶名稱（例如「現金」）
     * - 餘額（例如「NT$ 10,000」）
     * - 編輯按鈕（✏️）
     * - 刪除按鈕（🗑️）
     *
     * 為什麼要「動態建立」？
     * 因為帳戶數量會變動（新增、刪除、編輯），
     * 每次重新建立可以確保畫面跟資料同步～
     */
    private fun updateAccountList() {
        // 🗑️ 步驟 1：清空舊的內容（避免重複顯示）
        layoutAssetAccounts.removeAllViews()

        // 🔨 步驟 2：準備 LayoutInflater（3D 列印機）
        val inflater = LayoutInflater.from(requireContext())

        // 🔄 步驟 3：逐個帳戶建立項目
        accountList.forEach { account ->
            // 🎨 3.1 載入「帳戶卡片」的設計圖
            val itemView = inflater.inflate(R.layout.item_asset_account, layoutAssetAccounts, false)

            // 🔗 3.2 取得卡片裡的元件
            val tvAccountIcon = itemView.findViewById<TextView>(R.id.tvAccountIcon)          // 🖼️ 圖示
            val tvAccountName = itemView.findViewById<TextView>(R.id.tvAccountName)          // 🏷️ 名稱
            val tvAccountBalance = itemView.findViewById<TextView>(R.id.tvAccountBalance)    // 💰 餘額
            val btnEditAccount = itemView.findViewById<TextView>(R.id.btnEditAccount)        // ✏️ 編輯按鈕
            val btnDeleteAccount = itemView.findViewById<TextView>(R.id.btnDeleteAccount)    // 🗑️ 刪除按鈕

            // 🎨 3.3 設定帳戶圖示的樣式
            // AccountVisualStyleResolver 會根據帳戶名稱決定用什麼顏色和圖案
            // 例如：「現金」→ 綠色錢包圖案，「信用卡」→ 藍色卡片圖案
            AccountVisualStyleResolver.applyIconBadge(
                tvAccountIcon,
                AccountVisualStyleResolver.resolve(account.name)
            )

            // 📝 3.4 設定帳戶名稱和餘額
            tvAccountName.text = account.name
            tvAccountBalance.text = "NT$ ${account.balance}"

            // 🎮 3.5 設定編輯按鈕的點擊事件
            btnEditAccount.setOnClickListener {
                showEditAccountDialog(account)  // 跳出編輯對話框
            }

            // 🎮 3.6 設定刪除按鈕的點擊事件
            btnDeleteAccount.setOnClickListener {
                deleteAccount(account.id)  // 刪除帳戶（會先確認）
            }

            // 📦 3.7 把做好的卡片加到列表中
            layoutAssetAccounts.addView(itemView)
        }

        // 💡 補充說明：
        // 為什麼不用 RecyclerView？
        // 因為帳戶數量通常很少（5-10 個），
        // 用 LinearLayout 動態加入 View 比較簡單，
        // 不需要處理 ViewHolder 的複雜邏輯～
    }
}
