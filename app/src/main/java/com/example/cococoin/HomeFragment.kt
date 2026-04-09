package com.example.cococoin

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlin.math.abs

// 首頁畫面：
// 打開 App 後看到的第一個畫面，顯示當月收入、支出、結餘、預算進度、交易列表
class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var tvMonthlyIncome: TextView      // 當月收入金額
    private lateinit var tvMonthlyExpense: TextView     // 當月支出金額
    private lateinit var tvMonthlyBalance: TextView     // 當月結餘
    private lateinit var btnPreviousMonth: TextView     // 上個月按鈕
    private lateinit var btnNextMonth: TextView         // 下個月按鈕
    private lateinit var tvSelectedMonth: TextView      // 顯示目前選中的月份
    private lateinit var tvTransactionsMonth: TextView  // 交易列表標題的月份
    private lateinit var tvEmptyTransaction: TextView   // 沒有交易時顯示的文字
    private lateinit var rvTransactions: RecyclerView   // 交易列表
    private lateinit var adapter: HomeTransactionAdapter  // 交易列表的轉接器
    private lateinit var homeScrollView: NestedScrollView  // 可捲動的容器
    private lateinit var btnScrollToTop: ImageButton    // 回到頂端的按鈕

    // 預算相關元件
    private lateinit var tvMonthlyBudget: TextView      // 當月預算總額
    private lateinit var tvRemainingBudget: TextView    // 剩餘預算
    private lateinit var tvBudgetPercent: TextView      // 預算剩餘百分比文字
    private lateinit var progressBudget: ProgressBar    // 預算進度條

    private var isScrollToTopVisible = false  // 回到頂端按鈕是否顯示

    private lateinit var homeViewModel: HomeViewModel  // 資料管家

    // 當畫面建立完成時呼叫
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 綁定畫面元件
        bindViews(view)

        // 建立 ViewModel
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                CocoCoinRepository.getInstance(requireContext().applicationContext)
            )
        )[HomeViewModel::class.java]

        // 建立 Adapter，並設定點擊交易時的行為
        adapter = HomeTransactionAdapter { transaction ->
            // 點擊交易 → 打開交易詳情對話框
            val dialog = TransactionDetailDialogFragment.newInstance(transaction)
            dialog.show(parentFragmentManager, "TransactionDetailDialog")
        }

        // 設定 RecyclerView
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = adapter

        // 設定按鈕點擊事件
        btnPreviousMonth.setOnClickListener {
            homeViewModel.moveToPreviousMonth()  // 切換到上個月
        }

        btnNextMonth.setOnClickListener {
            homeViewModel.moveToNextMonth()      // 切換到下個月
        }

        btnScrollToTop.setOnClickListener {
            homeScrollView.smoothScrollTo(0, 0)  // 平滑滾動到頂端
        }

        // 監聽捲動事件，決定是否顯示「回到頂端」按鈕
        homeScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { scrollView, _, scrollY, _, _ ->
                val contentView = scrollView.getChildAt(0) ?: return@OnScrollChangeListener
                val distanceToBottom = contentView.bottom - (scrollView.height + scrollY)
                // 快要滾到底部時顯示回到頂端按鈕
                updateScrollToTopVisibility(distanceToBottom <= 160)
            }
        )

        // 監聽交易詳情對話框的結果（刪除交易）
        parentFragmentManager.setFragmentResultListener(
            TransactionDetailDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(TransactionDetailDialogFragment.KEY_ACTION)) {
                TransactionDetailDialogFragment.ACTION_DELETE -> {
                    homeViewModel.deleteTransaction(bundle.getInt("id"))
                }
            }
        }

        // 監聽編輯交易對話框的結果（更新交易）
        parentFragmentManager.setFragmentResultListener(
            EditTransactionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            homeViewModel.updateTransaction(
                Transaction(
                    id = bundle.getInt("id"),
                    type = bundle.getString("type").orEmpty(),
                    category = bundle.getString("category").orEmpty(),
                    amount = bundle.getInt("amount"),
                    note = bundle.getString("note").orEmpty(),
                    time = bundle.getString("time").orEmpty(),
                    accountName = bundle.getString("account").orEmpty()
                )
            )
        }

        // 觀察 ViewModel 的狀態變化
        observeHomeState()
    }

    // 當畫面重新顯示時，重新載入資料
    override fun onResume() {
        super.onResume()
        homeViewModel.reload()
    }

    // 綁定畫面元件（把 XML 裡的元件跟程式碼變數連起來）
    private fun bindViews(view: View) {
        tvMonthlyIncome = view.findViewById(R.id.tvMonthlyIncome)
        tvMonthlyExpense = view.findViewById(R.id.tvMonthlyExpense)
        tvMonthlyBalance = view.findViewById(R.id.tvMonthlyBalance)
        btnPreviousMonth = view.findViewById(R.id.btnPreviousMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        tvSelectedMonth = view.findViewById(R.id.tvSelectedMonth)
        tvTransactionsMonth = view.findViewById(R.id.tvTransactionsMonth)
        tvEmptyTransaction = view.findViewById(R.id.tvEmptyTransaction)
        rvTransactions = view.findViewById(R.id.rvTransactions)
        homeScrollView = view.findViewById(R.id.homeScrollView)
        btnScrollToTop = view.findViewById(R.id.btnScrollToTop)
        tvMonthlyBudget = view.findViewById(R.id.tvMonthlyBudget)
        tvRemainingBudget = view.findViewById(R.id.tvRemainingBudget)
        tvBudgetPercent = view.findViewById(R.id.tvBudgetPercent)
        progressBudget = view.findViewById(R.id.progressBudget)

        // 讓 RecyclerView 自己處理滾動（不要跟外面的 ScrollView 打架）
        rvTransactions.isNestedScrollingEnabled = false
    }

    // 觀察 ViewModel 的狀態變化
    private fun observeHomeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 只在畫面處於 STARTED 狀態時才收集（省電、避免記憶體洩漏）
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 同時收集兩個 Flow
                launch {
                    homeViewModel.uiState.collect { state ->
                        renderState(state)  // 畫面狀態變化 → 更新 UI
                    }
                }
                launch {
                    homeViewModel.events.collect { event ->
                        when (event) {
                            is HomeUiEvent.ShowMessage -> showToast(event.message)        // 顯示 Toast
                            is HomeUiEvent.ShowUndoDelete -> showUndoDeleteSnackbar(event.transaction)  // 顯示復原 Snackbar
                        }
                    }
                }
            }
        }
    }

    // 根據狀態更新畫面
    private fun renderState(state: HomeUiState) {
        // 月份文字
        tvSelectedMonth.text = state.selectedMonthText
        tvTransactionsMonth.text = state.selectedMonthText

        // 下個月按鈕：如果已經是當月就不能再往後
        btnNextMonth.isEnabled = !state.isViewingCurrentMonth
        btnNextMonth.alpha = if (btnNextMonth.isEnabled) 1f else 0.35f

        // 收入、支出、結餘
        tvMonthlyIncome.text = "NT$ ${state.monthlyIncome}"
        tvMonthlyExpense.text = "NT$ ${state.monthlyExpense}"
        tvMonthlyBalance.text = "NT$ ${state.monthlyBalance}"

        // 結餘顏色：正數綠色、負數紅色、零深色
        tvMonthlyBalance.setTextColor(
            when {
                state.monthlyBalance > 0 -> Color.parseColor("#2E7D32")
                state.monthlyBalance < 0 -> Color.parseColor("#C62828")
                else -> Color.parseColor("#1E1E2A")
            }
        )

        // 交易列表：有資料就顯示列表，沒資料就顯示空狀態
        if (state.transactions.isEmpty()) {
            tvEmptyTransaction.visibility = View.VISIBLE
            rvTransactions.visibility = View.GONE
            tvEmptyTransaction.text = state.emptyMessage
        } else {
            tvEmptyTransaction.visibility = View.GONE
            rvTransactions.visibility = View.VISIBLE
            adapter.submitTransactions(state.transactions)
        }

        // 預算相關
        tvMonthlyBudget.text = "NT$ ${state.monthlyBudget}"
        tvRemainingBudget.text = "NT$ ${state.remainingBudget}"
        tvBudgetPercent.text = state.budgetMessage
        progressBudget.progress = state.budgetPercent

        // 根據預算情況設定顏色
        when {
            state.monthlyBudget <= 0 -> {
                tvRemainingBudget.setTextColor(Color.parseColor("#8C8B97"))
                progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#B7A79B"))
            }
            state.remainingBudget < 0 -> {
                tvRemainingBudget.setTextColor(Color.parseColor("#C62828"))  // 超支 → 紅色
                progressBudget.progress = 0
                progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
            }
            else -> {
                if (state.budgetPercent <= 20) {
                    tvRemainingBudget.setTextColor(Color.parseColor("#C62828"))  // 快用完了 → 紅色警告
                    progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#D07A57"))
                } else {
                    tvRemainingBudget.setTextColor(Color.parseColor("#2E7D32"))   // 還有預算 → 綠色
                    progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
                }
            }
        }
    }

    // 顯示「復原」Snackbar（從底部滑出的提示條）
    private fun showUndoDeleteSnackbar(transaction: Transaction) {
        val snackbar = Snackbar.make(
            requireView(),
            "已刪除 ${transaction.category}，可直接復原",
            Snackbar.LENGTH_LONG
        )
        // 讓 Snackbar 顯示在底部導覽列的上方
        activity?.findViewById<View>(R.id.bottomNav)?.let { snackbar.anchorView = it }
        snackbar.setAction("復原") {
            homeViewModel.restoreDeletedTransaction(transaction)
        }
        snackbar.show()
    }

    // 顯示短暫提示訊息
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // 更新「回到頂端」按鈕的顯示/隱藏（帶淡入淡出動畫）
    private fun updateScrollToTopVisibility(shouldShow: Boolean) {
        if (shouldShow == isScrollToTopVisible) return
        isScrollToTopVisible = shouldShow

        btnScrollToTop.animate().cancel()
        if (shouldShow) {
            // 顯示：淡入 + 稍微放大
            btnScrollToTop.isVisible = true
            btnScrollToTop.alpha = 0f
            btnScrollToTop.scaleX = 0.92f
            btnScrollToTop.scaleY = 0.92f
            btnScrollToTop.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start()
        } else {
            // 隱藏：淡出 + 稍微縮小
            btnScrollToTop.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(180)
                .withEndAction {
                    btnScrollToTop.isVisible = false
                }
                .start()
        }
    }
}