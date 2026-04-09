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

// 資產頁面：
// 管理預算和帳戶的頁面，可以在這裡設定每月預算、新增/編輯/刪除帳戶
class AssetsFragment : Fragment(R.layout.fragment_assets) {

    // 預算相關元件
    private lateinit var tvCurrentBudget: TextView     // 顯示目前的預算金額
    private lateinit var etMonthlyBudget: EditText     // 輸入新預算的輸入框
    private lateinit var btnSaveBudget: Button         // 儲存預算按鈕
    private lateinit var tvBudgetMonth: TextView       // 顯示目前是哪個月的預算（例如「2026年4月預算設定」）

    // 帳戶相關元件
    private lateinit var tvTotalAssets: TextView       // 顯示總資產（所有帳戶餘額加總）
    private lateinit var tvEmptyAssets: TextView       // 沒有帳戶時顯示的提示文字
    private lateinit var layoutAssetAccounts: LinearLayout  // 放帳戶列表的容器
    private lateinit var etAccountName: EditText       // 新增帳戶的名稱輸入框
    private lateinit var etAccountBalance: EditText    // 新增帳戶的餘額輸入框
    private lateinit var btnAddAccount: Button         // 新增帳戶按鈕

    // 暫存帳戶列表（從資料庫讀取後存在這裡）
    private val accountList = mutableListOf<AssetAccount>()

    // 資料倉庫（存取資料庫）
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 當畫面建立完成時呼叫
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvBudgetMonth = view.findViewById(R.id.tvBudgetMonth)
        tvCurrentBudget = view.findViewById(R.id.tvCurrentBudget)
        etMonthlyBudget = view.findViewById(R.id.etMonthlyBudget)
        btnSaveBudget = view.findViewById(R.id.btnSaveBudget)

        tvTotalAssets = view.findViewById(R.id.tvTotalAssets)
        tvEmptyAssets = view.findViewById(R.id.tvEmptyAssets)
        layoutAssetAccounts = view.findViewById(R.id.layoutAssetAccounts)

        etAccountName = view.findViewById(R.id.etAccountName)
        // 點擊帳戶名稱輸入框時，讓它獲得焦點並彈出鍵盤
        etAccountName.setOnClickListener {
            etAccountName.isFocusableInTouchMode = true
            etAccountName.requestFocus()

            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etAccountName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        etAccountBalance = view.findViewById(R.id.etAccountBalance)
        btnAddAccount = view.findViewById(R.id.btnAddAccount)

        // 顯示當前月份的文字（例如「2026年4月預算設定」）
        showCurrentMonthText()

        // 設定按鈕點擊事件
        btnSaveBudget.setOnClickListener {
            saveBudget()  // 儲存預算
        }

        btnAddAccount.setOnClickListener {
            addAccount()  // 新增帳戶
        }

        // 等待資料庫初始化完成後，載入所有資料
        repository.ensureInitialized {
            loadAllData()
        }
    }

    // 當畫面重新顯示時（例如從其他頁面返回），重新載入資料
    override fun onResume() {
        super.onResume()
        loadAllData()
    }

    // 取得當前月份的顯示文字（例如「2026年4月」）
    private fun getCurrentMonthLabel(): String {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1  // 月份從 0 開始，所以要 +1
        return "${year}年${month}月"
    }

    // 載入所有資料（帳戶列表、當月預算）
    private fun loadAllData() {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val now = Calendar.getInstance()

            // 從資料庫讀取帳戶列表（在背景執行）
            val accounts = withContext(Dispatchers.IO) {
                repository.getAccounts()
            }

            // 從資料庫讀取當月預算
            val budget = withContext(Dispatchers.IO) {
                repository.getMonthlyBudget(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1
                )
            }

            if (view == null || !isAdded) return@launch

            // 更新暫存列表
            accountList.clear()
            accountList.addAll(accounts)

            // 更新畫面
            updateAccountList()      // 顯示帳戶列表
            updateAccountView()      // 顯示/隱藏空狀態提示
            updateTotalAssets()      // 計算並顯示總資產
            showBudget(budget)       // 顯示當月預算
        }
    }

    // 顯示當前月份的標題文字
    private fun showCurrentMonthText() {
        tvBudgetMonth.text = "${getCurrentMonthLabel()}預算設定"
    }

    // 顯示預算金額
    private fun showBudget(budget: Int) {
        tvCurrentBudget.text = if (budget > 0) {
            "NT$ $budget"
        } else {
            "尚未設定"  // 沒設預算就顯示「尚未設定」
        }
        etMonthlyBudget.setText("")  // 清空輸入框
    }

    // 儲存預算
    private fun saveBudget() {
        val budgetText = etMonthlyBudget.text.toString().trim()

        // 檢查有沒有輸入金額
        if (budgetText.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入預算金額", Toast.LENGTH_SHORT).show()
            return
        }

        // 檢查金額是否合法（正整數）
        val budget = budgetText.toIntOrNull()
        if (budget == null || budget < 0) {
            Toast.makeText(requireContext(), "請輸入正確的預算金額", Toast.LENGTH_SHORT).show()
            return
        }

        // 儲存到資料庫
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val now = Calendar.getInstance()
            withContext(Dispatchers.IO) {
                repository.saveMonthlyBudget(
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH) + 1,
                    amount = budget
                )
            }

            if (view == null || !isAdded) return@launch

            // 更新畫面並顯示成功訊息
            showBudget(budget)
            etMonthlyBudget.clearFocus()  // 讓鍵盤收起來
            Toast.makeText(
                requireContext(),
                "${getCurrentMonthLabel()}預算已儲存",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 新增帳戶
    private fun addAccount() {
        val name = etAccountName.text.toString().trim()
        val balanceText = etAccountBalance.text.toString().trim()

        // 檢查帳戶名稱
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入帳戶名稱", Toast.LENGTH_SHORT).show()
            return
        }

        // 檢查餘額
        if (balanceText.isEmpty()) {
            Toast.makeText(requireContext(), "請輸入帳戶餘額", Toast.LENGTH_SHORT).show()
            return
        }

        val balance = balanceText.toIntOrNull()
        if (balance == null || balance < 0) {
            Toast.makeText(requireContext(), "請輸入正確的帳戶餘額", Toast.LENGTH_SHORT).show()
            return
        }

        // 檢查是否已有同名帳戶
        if (accountList.any { it.name == name }) {
            Toast.makeText(requireContext(), "已存在相同名稱的帳戶", Toast.LENGTH_SHORT).show()
            return
        }

        // 儲存到資料庫
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.addAccount(name, balance)
            }

            if (view == null || !isAdded) return@launch

            Toast.makeText(
                requireContext(),
                result.message ?: if (result.success) "帳戶已新增" else "新增失敗",
                Toast.LENGTH_SHORT
            ).show()

            // 新增成功就清空輸入框並重新載入資料
            if (result.success) {
                etAccountName.setText("")
                etAccountBalance.setText("")
                loadAllData()
            }
        }
    }

    // 顯示編輯帳戶的對話框
    private fun showEditAccountDialog(account: AssetAccount) {
        // 載入 dialog_edit_account.xml 這個畫面
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_account, null)

        val etEditAccountName = dialogView.findViewById<EditText>(R.id.etEditAccountName)
        val etEditAccountBalance = dialogView.findViewById<EditText>(R.id.etEditAccountBalance)

        // 填入原本的資料
        etEditAccountName.setText(account.name)
        etEditAccountBalance.setText(account.balance.toString())

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("編輯帳戶")
            .setView(dialogView)
            .setPositiveButton("儲存", null)  // 先設 null，等等再覆寫
            .setNegativeButton("取消", null)
            .create()

        // 當對話框顯示時，設定儲存按鈕的行為
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = etEditAccountName.text.toString().trim()
                val newBalance = etEditAccountBalance.text.toString().trim().toIntOrNull()

                // 檢查資料是否正確
                if (newName.isEmpty() || newBalance == null || newBalance < 0) {
                    Toast.makeText(requireContext(), "請輸入正確資料", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 檢查是否與其他帳戶同名（排除自己）
                if (accountList.any { it.name == newName && it.id != account.id }) {
                    Toast.makeText(requireContext(), "已存在相同名稱的帳戶", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 儲存修改
                val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@setOnClickListener
                lifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.updateAccount(
                            accountId = account.id,
                            newName = newName,
                            newBalance = newBalance
                        )
                    }

                    if (view == null || !isAdded) return@launch

                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳戶已更新" else "更新失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (result.success) {
                        dialog.dismiss()  // 關閉對話框
                        loadAllData()     // 重新載入資料
                    }
                }
            }
        }

        dialog.show()
    }

    // 刪除帳戶（會先跳出確認視窗）
    private fun deleteAccount(accountId: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("刪除帳戶")
            .setMessage("確定要刪除這個帳戶嗎？")
            .setPositiveButton("刪除") { _, _ ->
                val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return@setPositiveButton
                lifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.deleteAccount(accountId)
                    }

                    if (view == null || !isAdded) return@launch

                    Toast.makeText(
                        requireContext(),
                        result.message ?: if (result.success) "帳戶已刪除" else "刪除失敗",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (result.success) {
                        loadAllData()  // 刪除成功，重新載入資料
                    } else if (result.message?.contains("交易紀錄") == true) {
                        // 如果帳戶還有交易紀錄，顯示更詳細的提示
                        AlertDialog.Builder(requireContext())
                            .setTitle("無法刪除帳戶")
                            .setMessage(result.message)
                            .setPositiveButton("知道了", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 根據是否有帳戶，顯示或隱藏空狀態提示
    private fun updateAccountView() {
        if (accountList.isEmpty()) {
            tvEmptyAssets.visibility = View.VISIBLE      // 顯示「尚未新增帳戶」
            layoutAssetAccounts.visibility = View.GONE   // 隱藏帳戶列表
        } else {
            tvEmptyAssets.visibility = View.GONE         // 隱藏空狀態提示
            layoutAssetAccounts.visibility = View.VISIBLE // 顯示帳戶列表
        }
    }

    // 計算並顯示總資產（所有帳戶餘額加總）
    private fun updateTotalAssets() {
        val total = accountList.sumOf { it.balance }
        tvTotalAssets.text = "NT$ $total"
    }

    // 動態建立帳戶列表（每個帳戶顯示成一個項目）
    private fun updateAccountList() {
        // 清空舊的內容
        layoutAssetAccounts.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        // 逐個帳戶建立項目
        accountList.forEach { account ->
            // 載入 item_asset_account.xml 這個項目模板
            val itemView = inflater.inflate(R.layout.item_asset_account, layoutAssetAccounts, false)

            // 取得項目中的元件
            val tvAccountIcon = itemView.findViewById<TextView>(R.id.tvAccountIcon)
            val tvAccountName = itemView.findViewById<TextView>(R.id.tvAccountName)
            val tvAccountBalance = itemView.findViewById<TextView>(R.id.tvAccountBalance)
            val btnEditAccount = itemView.findViewById<TextView>(R.id.btnEditAccount)
            val btnDeleteAccount = itemView.findViewById<TextView>(R.id.btnDeleteAccount)

            // 設定帳戶圖示的樣式（根據帳戶名稱決定用什麼顏色/圖案）
            AccountVisualStyleResolver.applyIconBadge(
                tvAccountIcon,
                AccountVisualStyleResolver.resolve(account.name)
            )

            // 設定帳戶名稱和餘額
            tvAccountName.text = account.name
            tvAccountBalance.text = "NT$ ${account.balance}"

            // 設定編輯按鈕的點擊事件
            btnEditAccount.setOnClickListener {
                showEditAccountDialog(account)
            }

            // 設定刪除按鈕的點擊事件
            btnDeleteAccount.setOnClickListener {
                deleteAccount(account.id)
            }

            // 把做好的項目加到列表中
            layoutAssetAccounts.addView(itemView)
        }
    }
}
