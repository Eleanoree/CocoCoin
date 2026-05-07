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

/**
 * 🏠【首頁畫面】
 *
 * 想像你走進一間超棒的記帳餐廳：
 * - 最上面顯示「這個月賺多少、花多少、存多少」
 * - 中間有一個「預算進度條」，提醒你還剩多少錢可以花
 * - 下面是「交易清單」，每一筆吃喝玩樂都記錄在這裡
 * - 你可以按「上個月 / 下個月」翻頁，看看以前花了什麼
 * - 點任何一筆交易，可以編輯或刪除
 * - 滾到最下面時，還會出現「回到頂端」的小火箭按鈕 🚀
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    // ---------- 📱 畫面上的元件（餐廳裡的各種餐具）----------

    // 💰 金額顯示區（三大金剛：收入、支出、結餘）
    private lateinit var tvMonthlyIncome: TextView      // 這個月賺多少（收入）
    private lateinit var tvMonthlyExpense: TextView     // 這個月花多少（支出）
    private lateinit var tvMonthlyBalance: TextView     // 這個月存多少（結餘 = 收入 - 支出）

    // 📅 月份切換按鈕（時間旅行控制器）
    private lateinit var btnPreviousMonth: TextView     // 「◀ 上個月」按鈕
    private lateinit var btnNextMonth: TextView         // 「下個月 ▶」按鈕（如果是當月會變灰色）
    private lateinit var tvSelectedMonth: TextView      // 顯示目前在看哪個月（例如「2026 年 4 月」）
    private lateinit var tvTransactionsMonth: TextView  // 交易列表標題上的月份（跟上面一樣）

    // 📋 交易列表相關
    private lateinit var tvEmptyTransaction: TextView   // 沒記帳時顯示：「這個月空空如也～」
    private lateinit var rvTransactions: RecyclerView   // 交易列表（可上下滑動的帳單條）
    private lateinit var adapter: HomeTransactionAdapter  // 轉接器：把資料變成畫面上的每一筆帳單

    // 📜 滾動相關（讓長長的頁面可以滑來滑去）
    private lateinit var homeScrollView: NestedScrollView  // 可捲動的容器（像一張很長的紙）
    private lateinit var btnScrollToTop: ImageButton    // 「回到頂端」的漂浮按鈕（懶人救星）

    // 💰💰 預算相關元件（預算功能的小幫手）
    private lateinit var tvMonthlyBudget: TextView      // 這個月的預算是多少（例如「NT$ 15000」）
    private lateinit var tvRemainingBudget: TextView    // 還可以花多少（剩餘預算）
    private lateinit var tvBudgetPercent: TextView      // 預算剩餘百分比文字（例如「當月剩餘 30%」）
    private lateinit var progressBudget: ProgressBar    // 預算進度條（視覺化還剩多少）

    private var isScrollToTopVisible = false  // 回到頂端按鈕目前是顯示還是隱藏？（預設隱藏）

    private lateinit var homeViewModel: HomeViewModel  // 📦 資料管家（負責算數學、存資料）

    // ---------- 🎬 畫面建立完成時（餐廳開門營業）----------

    /**
     * 當畫面建立完成時呼叫（像餐廳把桌椅都擺好了，準備接客）
     *
     * @param view 畫面的根布局（整張餐桌）
     * @param savedInstanceState 之前儲存的狀態（如果手機旋轉過，可以拿來恢復）
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1️⃣ 先把畫面上的元件一個一個認出來（綁定）
        bindViews(view)

        // 2️⃣ 聘請資料管家（ViewModel）
        // 注意：要給管家一個「倉庫」的鑰匙（Repository），他才能去搬資料
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                CocoCoinRepository.getInstance(requireContext().applicationContext)
            )
        )[HomeViewModel::class.java]

        // 3️⃣ 建立「交易列表」的轉接器（Adapter）
        // 這個 Adapter 負責把每一筆交易資料，變成畫面上的一條一條帳單
        // 同時設定：當使用者「點擊」某一筆交易時，要跳出詳細資訊對話框
        adapter = HomeTransactionAdapter { transaction ->
            // 🖱️ 點擊交易 → 打開交易詳情對話框（可以編輯或刪除）
            val dialog = TransactionDetailDialogFragment.newInstance(transaction)
            dialog.show(parentFragmentManager, "TransactionDetailDialog")
        }

        // 4️⃣ 設定 RecyclerView（交易列表）
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())  // 線性排列（一條一條往下）
        rvTransactions.adapter = adapter

        // 5️⃣ 設定按鈕的點擊事件（告訴按鈕：你被戳的時候要做什麼）

        // 上個月按鈕：叫管家把月份往前調
        btnPreviousMonth.setOnClickListener {
            homeViewModel.moveToPreviousMonth()
        }

        // 下個月按鈕：叫管家把月份往後調（如果已經是當月，管家會自動拒絕）
        btnNextMonth.setOnClickListener {
            homeViewModel.moveToNextMonth()
        }

        // 回到頂端按鈕：讓 ScrollView 平滑地滾到最上面
        btnScrollToTop.setOnClickListener {
            homeScrollView.smoothScrollTo(0, 0)  // (x, y) → 滾到 (0, 0) 就是左上角
        }

        // 6️⃣ 監聽使用者的滾動行為（決定要不要顯示「回到頂端」按鈕）
        homeScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { scrollView, _, scrollY, _, _ ->
                // 取得 ScrollView 裡面的內容（那個很長的紙）
                val contentView = scrollView.getChildAt(0) ?: return@OnScrollChangeListener
                // 計算：內容底部 減掉 (螢幕高度 + 已經滾多遠) = 離底部還有多遠
                val distanceToBottom = contentView.bottom - (scrollView.height + scrollY)
                // 如果快要滾到底部（距離 ≤ 160 像素），就顯示回到頂端按鈕
                updateScrollToTopVisibility(distanceToBottom <= 160)
            }
        )

        // 7️⃣ 監聽「交易詳情對話框」傳回來的結果
        // 白話：當使用者在對話框裡按了「刪除」，我們要收到通知並執行刪除
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

        // 8️⃣ 監聽「編輯交易對話框」傳回來的結果（使用者修改了一筆交易）
        parentFragmentManager.setFragmentResultListener(
            EditTransactionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            // 把使用者修改後的資料打包成 Transaction 物件，叫管家更新
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

        // 9️⃣ 開始觀察 ViewModel 的狀態變化（也就是「盯著管家，看他有沒有新消息」）
        observeHomeState()
    }

    /**
     * 🔄 當畫面重新顯示時呼叫
     *
     * 什麼時候會發生？
     * - 從其他畫面切回來（例如從設定頁面回來）
     * - 蓋住的對話框關掉後
     *
     * 這時候要重新載入資料，確保畫面是最新的！
     */
    override fun onResume() {
        super.onResume()
        homeViewModel.reload()  // 叫管家去倉庫搬最新資料
    }

    // ---------- 🔗 綁定元件（把程式變數跟 XML 元件牽紅線）----------

    /**
     * 把程式碼裡的變數跟 activity_main.xml 裡的元件綁定在一起
     *
     * 白話：每個變數就像一個遙控器按鈕，這裡告訴它「你要控制螢幕上的哪個東西」
     */
    private fun bindViews(view: View) {
        // 💰 金額顯示區
        tvMonthlyIncome = view.findViewById(R.id.tvMonthlyIncome)
        tvMonthlyExpense = view.findViewById(R.id.tvMonthlyExpense)
        tvMonthlyBalance = view.findViewById(R.id.tvMonthlyBalance)

        // 📅 月份控制區
        btnPreviousMonth = view.findViewById(R.id.btnPreviousMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        tvSelectedMonth = view.findViewById(R.id.tvSelectedMonth)
        tvTransactionsMonth = view.findViewById(R.id.tvTransactionsMonth)

        // 📋 交易列表區
        tvEmptyTransaction = view.findViewById(R.id.tvEmptyTransaction)
        rvTransactions = view.findViewById(R.id.rvTransactions)

        // 📜 滾動區
        homeScrollView = view.findViewById(R.id.homeScrollView)
        btnScrollToTop = view.findViewById(R.id.btnScrollToTop)

        // 💰💰 預算區
        tvMonthlyBudget = view.findViewById(R.id.tvMonthlyBudget)
        tvRemainingBudget = view.findViewById(R.id.tvRemainingBudget)
        tvBudgetPercent = view.findViewById(R.id.tvBudgetPercent)
        progressBudget = view.findViewById(R.id.progressBudget)

        // 🔧 小設定：讓 RecyclerView 自己處理滾動，不要跟外面的 ScrollView 打架
        // 如果不關掉，兩個捲動區會搶控制權，滑起來會卡卡的
        rvTransactions.isNestedScrollingEnabled = false
    }

    // ---------- 👀 觀察 ViewModel（盯著管家，看他有沒有新消息）----------

    /**
     * 觀察 ViewModel 的狀態變化
     *
     * 這是 Fragment 跟 ViewModel 的「無線電連線」：
     * - uiState：持續性的資料（收入、支出、交易列表）
     * - events：一次性事件（Toast 訊息、復原 Snackbar）
     *
     * 白話：叫兩個小弟分別盯著不同的消息管道，一有消息就回報
     */
    private fun observeHomeState() {
        // 使用 lifecycleScope 和 repeatOnLifecycle 來確保「只在畫面活著時才收消息」
        // 為什麼要這樣？省電 + 避免記憶體洩漏（畫面關了就不浪費力氣）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 👇 兩個偵查兵同時出動
                launch {
                    // 偵查兵 1：盯著 uiState（畫面資料）
                    homeViewModel.uiState.collect { state ->
                        renderState(state)  // 資料變了 → 更新畫面
                    }
                }
                launch {
                    // 偵查兵 2：盯著 events（一次性事件）
                    homeViewModel.events.collect { event ->
                        when (event) {
                            is HomeUiEvent.ShowMessage -> showToast(event.message)        // 📢 顯示短暫提示
                            is HomeUiEvent.ShowUndoDelete -> showUndoDeleteSnackbar(event.transaction)  // ↩️ 顯示復原條
                        }
                    }
                }
            }
        }
    }

    // ---------- 🎨 更新畫面（把管家算好的數字填上去）----------

    /**
     * 根據 ViewModel 給的狀態，更新畫面上的所有元件
     *
     * 這是整個 Fragment 的核心！負責把「資料」變成「漂亮的畫面」
     *
     * @param state 從 ViewModel 送過來的狀態包裹（裡面有所有需要的數字）
     */
    private fun renderState(state: HomeUiState) {
        // ---------- 📅 月份相關 ----------
        tvSelectedMonth.text = state.selectedMonthText        // 例如「2026 年 4 月」
        tvTransactionsMonth.text = state.selectedMonthText    // 交易列表標題的月份

        // 下個月按鈕：如果「正在看當月」，就不能按（不能穿越到未來）
        btnNextMonth.isEnabled = !state.isViewingCurrentMonth
        btnNextMonth.alpha = if (btnNextMonth.isEnabled) 1f else 0.35f  // 不能按的時候變半透明

        // ---------- 💰 收入、支出、結餘 ----------
        tvMonthlyIncome.text = "NT$ ${state.monthlyIncome}"
        tvMonthlyExpense.text = "NT$ ${state.monthlyExpense}"
        tvMonthlyBalance.text = "NT$ ${state.monthlyBalance}"

        // 結餘的顏色：心情好（正數）→ 綠色，心情差（負數）→ 紅色，平平（0）→ 深灰色
        tvMonthlyBalance.setTextColor(
            when {
                state.monthlyBalance > 0 -> Color.parseColor("#2E7D32")   // 🟢 綠色（有存錢）
                state.monthlyBalance < 0 -> Color.parseColor("#C62828")   // 🔴 紅色（透支了）
                else -> Color.parseColor("#1E1E2A")                        // ⚫ 深色（收支平衡）
            }
        )

        // ---------- 📋 交易列表 ----------
        if (state.transactions.isEmpty()) {
            // 沒有交易紀錄 → 顯示「空空如也」的訊息
            tvEmptyTransaction.visibility = View.VISIBLE
            rvTransactions.visibility = View.GONE
            tvEmptyTransaction.text = state.emptyMessage
        } else {
            // 有交易紀錄 → 顯示列表，並把資料丟給 Adapter
            tvEmptyTransaction.visibility = View.GONE
            rvTransactions.visibility = View.VISIBLE
            adapter.submitTransactions(state.transactions)  // Adapter 會自動更新畫面
        }

        // ---------- 💰💰 預算相關（進階功能）----------
        tvMonthlyBudget.text = "NT$ ${state.monthlyBudget}"      // 預算總額
        tvRemainingBudget.text = "NT$ ${state.remainingBudget}" // 還可以花多少
        tvBudgetPercent.text = state.budgetMessage              // 預算提示文字（例如「還剩 30%」）
        progressBudget.progress = state.budgetPercent           // 進度條的百分比

        // 🎨 根據預算情況設定不同的顏色（視覺警告）
        when {
            // 情況 1：沒設定預算
            state.monthlyBudget <= 0 -> {
                tvRemainingBudget.setTextColor(Color.parseColor("#8C8B97"))  // 灰色（無關緊要）
                progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#B7A79B"))
            }
            // 情況 2：超支了（花超過預算）→ 紅色警告！
            state.remainingBudget < 0 -> {
                tvRemainingBudget.setTextColor(Color.parseColor("#C62828"))  // 🔴 大紅色
                progressBudget.progress = 0
                progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
            }
            // 情況 3：還有預算 → 根據剩餘比例決定顏色
            else -> {
                if (state.budgetPercent <= 20) {
                    // 剩下不到 20% → 橘紅色警告（快沒錢了）
                    tvRemainingBudget.setTextColor(Color.parseColor("#C62828"))
                    progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#D07A57"))
                } else {
                    // 還有足夠預算 → 綠色安心
                    tvRemainingBudget.setTextColor(Color.parseColor("#2E7D32"))
                    progressBudget.progressTintList = ColorStateList.valueOf(Color.parseColor("#8D6E63"))
                }
            }
        }
    }

    // ---------- ↩️ 復原 Snackbar（不小心刪掉可以救回來）----------

    /**
     * 顯示「復原」Snackbar（從底部滑出的提示條）
     *
     * 當使用者刪除一筆交易後，這個小紙條會從底部滑出來：
     * 「已刪除 午餐，可直接復原」  [復原]
     *
     * 如果使用者點「復原」，就把刪掉的資料救回來～
     *
     * @param transaction 剛剛被刪掉的那筆交易（先偷偷記下來，以備不時之需）
     */
    private fun showUndoDeleteSnackbar(transaction: Transaction) {
        val snackbar = Snackbar.make(
            requireView(),
            "已刪除 ${transaction.category}，可直接復原",  // 例如：「已刪除 午餐，可直接復原」
            Snackbar.LENGTH_LONG  // 顯示久一點（讓使用者有時間按復原）
        )
        // 讓 Snackbar 顯示在底部導覽列的「上面」（不要蓋到按鈕）
        activity?.findViewById<View>(R.id.bottomNav)?.let { snackbar.anchorView = it }
        snackbar.setAction("復原") {
            homeViewModel.restoreDeletedTransaction(transaction)  // 叫管家把資料救回來
        }
        snackbar.show()
    }

    // ---------- 📢 顯示短暫提示（Toast）----------

    /**
     * 顯示一個短暫的小提示（像氣泡一樣，幾秒後消失）
     *
     * 例如：「刪除成功」、「網路不穩」、「找不到資料」
     *
     * @param message 要顯示的文字
     */
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // ---------- 🚀 回到頂端按鈕的動畫（淡入淡出 + 縮放）----------

    /**
     * 更新「回到頂端」按鈕的顯示/隱藏（帶淡入淡出 + 縮放動畫）
     *
     * 為什麼要做動畫？因為有動畫比較 Q 彈，使用者體驗更好～
     *
     * @param shouldShow true 表示要讓按鈕出現，false 表示要讓它消失
     */
    private fun updateScrollToTopVisibility(shouldShow: Boolean) {
        // 如果狀態沒有改變，就不用做事（避免重複執行）
        if (shouldShow == isScrollToTopVisible) return
        isScrollToTopVisible = shouldShow

        // 先把之前的動畫取消（避免動畫衝突）
        btnScrollToTop.animate().cancel()

        if (shouldShow) {
            // 🎬 顯示動畫：淡入 + 微微放大（從 92% 放大到 100%）
            btnScrollToTop.isVisible = true
            btnScrollToTop.alpha = 0f      // 一開始完全透明
            btnScrollToTop.scaleX = 0.92f  // 一開始縮小 8%
            btnScrollToTop.scaleY = 0.92f
            btnScrollToTop.animate()
                .alpha(1f)                 // 淡入到完全不透明
                .scaleX(1f)               // 放大回正常大小
                .scaleY(1f)
                .setDuration(200)          // 動畫持續 0.2 秒
                .start()
        } else {
            // 🎬 隱藏動畫：淡出 + 微微縮小（反向操作）
            btnScrollToTop.animate()
                .alpha(0f)                 // 淡出到透明
                .scaleX(0.92f)            // 稍微縮小
                .scaleY(0.92f)
                .setDuration(180)          // 0.18 秒
                .withEndAction {
                    btnScrollToTop.isVisible = false  // 動畫結束後才真正隱藏
                }
                .start()
        }
    }
}
/*
程式碼元件	        白話比喻
==========================================================
Fragment	        手機畫面裡的一塊區域（像拼圖的一片）
onViewCreated	    畫面準備好的那一刻（像是舞台搭好了）
bindViews()	        把遙控器按鈕跟實際功能配對
RecyclerView	    會自動重複使用的列表（省電省記憶體）
Adapter	            轉接器，把資料變成畫面上的一條一條項目
ViewModelProvider	聘請資料管家
Flow / StateFlow	無線電頻道，一有變化就通知畫面
collect	            收聽頻道（像守在收音機旁等消息）
snackbar	        從底部滑出來的小紙條（可以按「復原」）
Toast	            跳一下就不見的小氣泡（只能看不能按）
ScrollView	        可以上下滑動的長紙張
NestedScrollView	加強版 ScrollView，裡面還可以放另一個滑動區域
smoothScrollTo(0,0)	平滑滾動到最上面（像電梯緩緩上升）
 */