// ============================================================
// 📊 分析頁面 — 白話文：記帳本的「數據分析室」或「花錢雷達站」
// ============================================================
//
// 情境劇：想像你打開記帳 App 的「分析」頁面，你會看到：
//           📈 圓餅圖：這個月餐飲花了多少？交通花了多少？（一目了然）
//           📊 長條圖：這週每天花多少錢？哪天花最多？
//           💰 總收入、總支出、淨額：這個月是賺還是賠？
//           🏷️ 點擊圓餅圖的某一塊 → 顯示該分類的詳細交易明細
//           📅 點擊長條圖的某一天 → 顯示那天的詳細交易明細
//           🗓️ 可以自己選日期區間（例如「3月1日 ~ 3月31日」）
//
// 這個 AnalysisFragment 就是那個「分析室」！
// 它使用 MPAndroidChart 這個開源圖表庫來畫出漂亮的圖表
// ============================================================
package com.example.cococoin

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalysisFragment : Fragment(R.layout.fragment_analysis) {

    // ============================================================
    // 📋 每日明細相關元件 — 白話文：點選長條圖某天後顯示的詳細清單
    // ============================================================
    private lateinit var tvSelectedDaySummary: TextView           // 顯示選中日期的小結（例如「2026/04/06 總支出 500 元」）
    private lateinit var rvDailyDetails: androidx.recyclerview.widget.RecyclerView  // 每日明細列表
    private lateinit var dailyDetailAdapter: DailyDetailAdapter   // 把交易資料變成列表項目的轉接器

    // ============================================================
    // 🏷️ 分類明細相關元件 — 白話文：點選圓餅圖某分類後顯示的詳細清單
    // ============================================================
    private lateinit var tvSelectedCategorySummary: TextView      // 顯示選中分類的小結（例如「餐飲 總支出 3,000 元」）
    private lateinit var rvCategoryDetails: androidx.recyclerview.widget.RecyclerView  // 分類明細列表
    private lateinit var categoryDetailAdapter: CategoryDetailAdapter  // 分類明細的轉接器

    // ============================================================
    // 🗄️ 目前篩選後的交易 — 白話文：根據選中的日期區間過濾後的資料
    // ============================================================
    private var currentFilteredTransactions: List<Transaction> = emptyList()

    // ============================================================
    // 📅 日期區間選擇相關元件 — 白話文：讓使用者選擇想看哪個時間範圍
    // ============================================================
    private lateinit var tvRangeIncome: TextView      // 區間總收入（例如「NT$ 50,000」）
    private lateinit var tvRangeExpense: TextView     // 區間總支出（例如「NT$ 30,000」）
    private lateinit var tvRangeBalance: TextView     // 區間淨額（收入 - 支出，例如「NT$ 20,000」）
    private lateinit var tvStartDate: TextView        // 開始日期顯示（例如「2026/04/01」）
    private lateinit var tvEndDate: TextView          // 結束日期顯示（例如「2026/04/30」）
    private lateinit var layoutStartDate: View        // 開始日期選擇器容器（點這裡選日期）
    private lateinit var layoutEndDate: View          // 結束日期選擇器容器（點這裡選日期）
    private lateinit var btnAnalyze: Button           // 分析按鈕（點擊後重新計算圖表）
    private lateinit var layoutAnalyzeLoading: View   // 分析中的 loading 畫面（轉圈圈）

    // ============================================================
    // 📊 圖表相關元件 — 白話文：畫圖的地方！
    // ============================================================
    private lateinit var pieChart: PieChart           // 圓餅圖（顯示分類佔比）
    private lateinit var barChart: BarChart           // 長條圖（顯示每日收支）
    private lateinit var tvPieSummary: TextView       // 圓餅圖摘要文字（例如「總支出 30,000 元」）
    private lateinit var rgPieType: RadioGroup        // 切換支出/收入的單選按鈕組（顯示支出圓餅圖還是收入圓餅圖？）
    private lateinit var rbPieExpense: RadioButton    // 支出單選按鈕
    private lateinit var rbPieIncome: RadioButton     // 收入單選按鈕
    private lateinit var chipGroupPieCategories: ChipGroup  // 圓餅圖的分類標籤群組（顯示在圖表下方）

    // ============================================================
    // 🗃️ 所有交易 — 白話文：從資料庫讀取後暫存在這裡
    // ============================================================
    private val allTransactions = mutableListOf<Transaction>()

    // ============================================================
    // 🕐 日期格式化工具 — 白話文：把時間數字轉成人類看得懂的文字
    // ============================================================
    private val displayDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())  // 顯示用：2026/04/06
    private val fullDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())  // 完整格式：2026/04/06 14:30

    // ============================================================
    // 🏭 資料倉庫 — 白話文：負責存取資料庫的管家
    // ============================================================
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // ============================================================
    // 📅 選中的日期區間 — 白話文：使用者選擇的開始和結束日期
    // ============================================================
    private var startCalendar: Calendar? = null   // 開始日期（例如 2026/04/01）
    private var endCalendar: Calendar? = null     // 結束日期（例如 2026/04/30）

    // ============================================================
    // 🥧 圓餅圖狀態 — 白話文：記錄圓餅圖目前顯示什麼
    // ============================================================
    private var selectedPieType: String = "支出"   // 目前選中的類型（「支出」或「收入」）
    private var currentPieCategoryBreakdown: List<Pair<String, Int>> = emptyList()  // 分類明細（例如 [("餐飲", 5000), ("交通", 3000)]）
    private var currentPieTotalAmount: Float = 0f  // 總金額（例如 30000.0）
    private var suppressChipCallback = false       // 防止 Chip 回調循環（避免點了 Chip 又觸發重複更新）

    // ============================================================
    // 🎬 當畫面建立完成時呼叫 — 白話文：佈置分析室的傢俱
    // ============================================================
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ----- 第一步：綁定畫面元件（找到所有的按鈕、文字、圖表）-----
        layoutStartDate = view.findViewById(R.id.layoutStartDate)
        layoutEndDate = view.findViewById(R.id.layoutEndDate)
        tvStartDate = view.findViewById(R.id.tvStartDate)
        tvEndDate = view.findViewById(R.id.tvEndDate)
        btnAnalyze = view.findViewById(R.id.btnAnalyze)
        layoutAnalyzeLoading = view.findViewById(R.id.layoutAnalyzeLoading)
        pieChart = view.findViewById(R.id.pieChart)
        barChart = view.findViewById(R.id.barChart)
        rgPieType = view.findViewById(R.id.rgPieType)
        rbPieExpense = view.findViewById(R.id.rbPieExpense)
        rbPieIncome = view.findViewById(R.id.rbPieIncome)
        chipGroupPieCategories = view.findViewById(R.id.chipGroupPieCategories)

        tvPieSummary = view.findViewById(R.id.tvPieSummary)
        tvRangeIncome = view.findViewById(R.id.tvRangeIncome)
        tvRangeExpense = view.findViewById(R.id.tvRangeExpense)
        tvRangeBalance = view.findViewById(R.id.tvRangeBalance)
        tvSelectedCategorySummary = view.findViewById(R.id.tvSelectedCategorySummary)
        rvCategoryDetails = view.findViewById(R.id.rvCategoryDetails)

        // ----- 第二步：設定分類明細列表（點擊圓餅圖後顯示）-----
        categoryDetailAdapter = CategoryDetailAdapter(mutableListOf())
        rvCategoryDetails.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvCategoryDetails.adapter = categoryDetailAdapter

        // ----- 第三步：設定每日明細列表（點擊長條圖後顯示）-----
        tvSelectedDaySummary = view.findViewById(R.id.tvSelectedDaySummary)
        rvDailyDetails = view.findViewById(R.id.rvDailyDetails)

        dailyDetailAdapter = DailyDetailAdapter(mutableListOf())
        rvDailyDetails.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvDailyDetails.adapter = dailyDetailAdapter

        // ----- 第四步：設定按鈕的點擊事件 -----

        // 點擊「開始日期」→ 跳出日期選擇器
        layoutStartDate.setOnClickListener {
            showDatePicker(isStart = true)
        }

        // 點擊「結束日期」→ 跳出日期選擇器
        layoutEndDate.setOnClickListener {
            showDatePicker(isStart = false)
        }

        // 切換「支出/收入」單選按鈕 → 重新分析（顯示支出的圓餅圖或收入的圓餅圖）
        rgPieType.setOnCheckedChangeListener { _, checkedId ->
            selectedPieType = if (checkedId == R.id.rbPieIncome) "收入" else "支出"
            analyzeSelectedRange()  // 重新分析並重畫圖表
        }

        // 點擊「分析」按鈕 → 手動重新分析（可能 loading 一下）
        btnAnalyze.setOnClickListener {
            analyzeSelectedRange(showLoading = true)
        }

        // ----- 第五步：設定預設日期區間（當月1號到今天）-----
        setDefaultDateRange()

        // ----- 第六步：等待資料庫初始化完成，然後載入資料並分析 -----
        repository.ensureInitialized {
            loadTransactionsAndAnalyze()
        }
    }

    // ============================================================
    // 🔄 當畫面重新顯示時 — 白話文：從其他頁面回來時，重新載入最新資料
    // ============================================================
    override fun onResume() {
        super.onResume()
        loadTransactionsAndAnalyze()  // 重新載入資料（可能有新增/編輯/刪除）
    }

    // ============================================================
    // 📅 設定預設日期區間 — 白話文：打開頁面時，自動選「這個月」
    // ============================================================
    private fun setDefaultDateRange() {
        val today = Calendar.getInstance()                    // 今天（例如 2026/04/06）
        val firstDay = Calendar.getInstance()                 // 這個月的第一天
        firstDay.set(Calendar.DAY_OF_MONTH, 1)                // 設為 1 號

        startCalendar = firstDay                              // 開始日期：4月1日
        endCalendar = today                                   // 結束日期：4月6日

        tvStartDate.text = displayDateFormat.format(firstDay.time)
        tvEndDate.text = displayDateFormat.format(today.time)
    }

    // ============================================================
    // 📅 顯示日期選擇器 — 白話文：跳出一個日曆讓使用者選日期
    // ============================================================
    private fun showDatePicker(isStart: Boolean) {
        // 決定要用哪個日期作為選擇器的預設值
        val calendar = if (isStart) {
            startCalendar ?: Calendar.getInstance()
        } else {
            endCalendar ?: Calendar.getInstance()
        }

        // 跳出 Android 內建的日期選擇對話框
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance()
                selected.set(year, month, dayOfMonth, 0, 0, 0)
                selected.set(Calendar.MILLISECOND, 0)

                if (isStart) {
                    // 設定開始日期
                    startCalendar = selected
                    tvStartDate.text = displayDateFormat.format(selected.time)
                } else {
                    // 設定結束日期（設為當天的最後一刻 23:59:59）
                    selected.set(Calendar.HOUR_OF_DAY, 23)
                    selected.set(Calendar.MINUTE, 59)
                    selected.set(Calendar.SECOND, 59)
                    endCalendar = selected
                    tvEndDate.text = displayDateFormat.format(selected.time)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ============================================================
    // 💰 更新區間統計 — 白話文：顯示「總收入、總支出、淨額」
    // ============================================================
    private fun updateRangeSummary(transactions: List<Transaction>) {
        // 計算總收入（所有「收入」類型的金額加總）
        val totalIncome = transactions
            .filter { it.type == "收入" }
            .sumOf { it.amount }

        // 計算總支出（所有「支出」類型的金額加總）
        val totalExpense = transactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        // 計算淨額（收入 - 支出）
        val balance = totalIncome - totalExpense

        // 更新畫面上的文字
        tvRangeIncome.text = "NT$ $totalIncome"
        tvRangeExpense.text = "NT$ $totalExpense"
        tvRangeBalance.text = "NT$ $balance"

        // 根據淨額正負設定顏色
        if (balance > 0) {
            tvRangeBalance.setTextColor(Color.parseColor("#2E7D32"))  // 賺錢 → 綠色（開心）
        } else if (balance < 0) {
            tvRangeBalance.setTextColor(Color.parseColor("#C62828"))  // 賠錢 → 紅色（警惕）
        } else {
            tvRangeBalance.setTextColor(Color.parseColor("#1E1E2A"))  // 持平 → 深色（普通）
        }
    }

    // ============================================================
    // 📥 載入交易資料並分析 — 白話文：從資料庫讀取最新資料，然後畫圖
    // ============================================================
    private fun loadTransactionsAndAnalyze() {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 在背景執行緒讀取資料庫（避免卡畫面）
            val transactions = withContext(Dispatchers.IO) {
                repository.getTransactions()
            }

            // 檢查畫面還在不在（避免關閉後還更新畫面導致 crash）
            if (view == null || !isAdded) return@launch

            // 儲存到暫存變數
            allTransactions.clear()
            allTransactions.addAll(transactions)

            // 開始分析並畫圖
            analyzeSelectedRange()
        }
    }

    // ============================================================
    // 📊 分析選中的日期區間 — 白話文：根據使用者選的日期，算出圖表
    // ============================================================
    // 這是整個分析頁面的「大腦中樞」！
    // 它會：
    //   1. 檢查日期區間是否有效（開始日期不能大於結束日期）
    //   2. 過濾出日期區間內的交易
    //   3. 更新統計數字（總收入、總支出、淨額）
    //   4. 繪製圓餅圖（分類佔比）
    //   5. 繪製長條圖（每日收支）
    //   6. 重置明細顯示（等使用者點擊圖塊時才顯示）
    private fun analyzeSelectedRange(showLoading: Boolean = false) {
        val start = startCalendar
        val end = endCalendar

        // 防呆：如果沒選日期就不做事
        if (start == null || end == null) return

        // ----- 檢查：開始日期不能大於結束日期 -----
        // 白話文：你不能選「從 4/6 到 4/1」，這樣會找不到資料
        if (start.timeInMillis > end.timeInMillis) {
            showInvalidDateState()
            return
        }

        val startMillis = start.timeInMillis
        val endMillis = end.timeInMillis
        val startCopy = start.clone() as Calendar
        val endCopy = end.clone() as Calendar
        val transactionsSnapshot = allTransactions.toList()

        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            // 如果需要顯示 loading，就打開轉圈圈畫面
            if (showLoading) {
                setAnalyzeLoading(true)
            }

            try {
                // ----- 步驟 1：過濾出日期區間內的交易 -----
                // 白話文：從所有交易中，只挑出使用者選的時間範圍內的
                val filtered = withContext(Dispatchers.Default) {
                    transactionsSnapshot.filter { transaction ->
                        try {
                            val date = fullDateFormat.parse(transaction.time) ?: return@filter false
                            date.time in startMillis..endMillis
                        } catch (_: Exception) {
                            false  // 時間格式有問題就跳過
                        }
                    }
                }

                // 檢查畫面還在不在（避免關閉後還更新畫面導致 crash）
                if (view == null || !isAdded) return@launch

                // ----- 步驟 2：更新畫面 -----
                currentFilteredTransactions = filtered
                updateRangeSummary(filtered)      // 更新「總收入/總支出/淨額」
                setupPieChart(filtered)           // 繪製圓餅圖
                setupBarChart(filtered, startCopy, endCopy)  // 繪製長條圖

                // ----- 步驟 3：重置明細顯示（等使用者點擊圖塊才顯示）-----
                tvSelectedCategorySummary.text = "請點選上方圓餅圖查看分類明細"
                categoryDetailAdapter.updateData(emptyList())
                tvSelectedDaySummary.text = "請點選上方長條圖查看當日明細"
                dailyDetailAdapter.updateData(emptyList())
            } finally {
                // 不管成功還是失敗，都要關閉 loading
                if (showLoading) {
                    setAnalyzeLoading(false)
                }
            }
        }
    }

    // ============================================================
    // ⚠️ 顯示無效日期區間的狀態 — 白話文：日期選錯了，清空畫面給你看
    // ============================================================
    private fun showInvalidDateState() {
        currentFilteredTransactions = emptyList()
        tvPieSummary.text = "開始日期不能大於結束日期"
        currentPieCategoryBreakdown = emptyList()
        currentPieTotalAmount = 0f
        chipGroupPieCategories.removeAllViews()  // 清空分類 Chip 按鈕
        pieChart.clear()      // 清空圓餅圖
        barChart.clear()      // 清空長條圖
        tvRangeIncome.text = "NT$ 0"
        tvRangeExpense.text = "NT$ 0"
        tvRangeBalance.text = "NT$ 0"
        tvSelectedCategorySummary.text = "請重新調整日期區間"
        tvSelectedDaySummary.text = "請重新調整日期區間"
        categoryDetailAdapter.updateData(emptyList())
        dailyDetailAdapter.updateData(emptyList())
    }

    // ============================================================
    // 🔄 設定分析按鈕的 loading 狀態 — 白話文：分析時按鈕變成轉圈圈
    // ============================================================
    private fun setAnalyzeLoading(isLoading: Boolean) {
        btnAnalyze.isEnabled = !isLoading                    // loading 時不能按
        btnAnalyze.alpha = if (isLoading) 0.92f else 1f      // 半透明效果
        btnAnalyze.text = if (isLoading) "" else "開始分析"   // loading 時文字清空
        layoutAnalyzeLoading.visibility = if (isLoading) View.VISIBLE else View.GONE  // 顯示轉圈圈
        layoutStartDate.isEnabled = !isLoading               // loading 時不能改日期
        layoutEndDate.isEnabled = !isLoading
        layoutStartDate.alpha = if (isLoading) 0.7f else 1f
        layoutEndDate.alpha = if (isLoading) 0.7f else 1f
    }

    // ============================================================
    // 🥧 繪製圓餅圖（分類佔比） — 白話文：畫出「錢都花到哪裡去」的圖
    // ============================================================
    private fun setupPieChart(transactions: List<Transaction>) {
        // ----- 步驟 1：根據選中的類型過濾 -----
        // 白話文：使用者想看「支出」圓餅圖，就只拿支出類型的交易
        val filteredPieTransactions = transactions.filter { it.type == selectedPieType }

        // ----- 步驟 2：如果沒有資料，顯示「無資料」訊息 -----
        if (filteredPieTransactions.isEmpty()) {
            pieChart.clear()
            pieChart.setNoDataText("此區間無${selectedPieType}資料")
            tvPieSummary.text = "此區間無${selectedPieType}資料可分析"
            tvSelectedCategorySummary.text = "此區間無${selectedPieType}明細"
            categoryDetailAdapter.updateData(emptyList())
            currentPieCategoryBreakdown = emptyList()
            currentPieTotalAmount = 0f
            chipGroupPieCategories.removeAllViews()
            return
        }

        // ----- 步驟 3：按分類統計金額 -----
        // 白話文：把所有「餐飲」的金額加起來，所有「交通」的金額加起來...
        val categoryMap = filteredPieTransactions
            .groupBy { it.category }                    // 依分類分組
            .mapValues { (_, items) -> items.sumOf { it.amount } }  // 計算每組總金額
            .toList()                                    // 轉成 List
            .sortedByDescending { (_, amount) -> amount } // 金額大的排前面（餐飲 5000 放第一個）

        // ----- 步驟 4：計算總金額（用來算百分比）-----
        val total = categoryMap.sumOf { (_, amount) -> amount }.toFloat()
        currentPieCategoryBreakdown = categoryMap
        currentPieTotalAmount = total

        // ----- 步驟 5：建立圓餅圖的資料條目 -----
        // 白話文：把資料轉成 MPAndroidChart 看得懂的格式
        val entries = categoryMap.map { (category, amount) ->
            PieEntry(amount.toFloat(), category)
        }

        // ----- 步驟 6：設定圓餅圖的顏色 -----
        val dataSet = PieDataSet(entries, "${selectedPieType}分類")
        dataSet.colors = listOf(
            Color.parseColor("#EF5350"),  // 紅（餐飲）
            Color.parseColor("#FF7043"),  // 橙（交通）
            Color.parseColor("#FFA726"),  // 橘（購物）
            Color.parseColor("#AB47BC"),  // 紫（娛樂）
            Color.parseColor("#5C6BC0"),  // 靛（醫療）
            Color.parseColor("#29B6F6"),  // 藍（學習）
            Color.parseColor("#66BB6A"),  // 綠（其他）
            Color.parseColor("#8D6E63")   // 棕（備用）
        )
        dataSet.sliceSpace = 2f          // 區塊之間留一點間隔（看起來更清楚）
        dataSet.selectionShift = 14f     // 點擊時扇形會突出來 14dp
        dataSet.setDrawValues(false)     // 不顯示數字標籤（太擠了，用百分比顯示）

        // ----- 步驟 7：把資料設定到圓餅圖 -----
        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(pieChart))  // 顯示百分比（例如「30%」）
        pieChart.data = pieData
        pieChart.setUsePercentValues(true)           // 使用百分比顯示
        pieChart.description = Description().apply { text = "" }  // 移除圖表底部的描述文字
        pieChart.centerText = "${selectedPieType}分類"  // 圓餅圖中心的文字
        pieChart.setDrawEntryLabels(false)           // 不顯示扇形旁邊的標籤（用底下的 Chip 代替）
        pieChart.setHoleColor(Color.parseColor("#FFFDFB"))  // 中心洞的顏色（白色）
        pieChart.holeRadius = 58f                    // 中心洞半徑（越大洞越大）
        pieChart.transparentCircleRadius = 62f       // 透明圓環半徑（製造陰影效果）
        pieChart.setCenterTextColor(Color.parseColor("#3E352F"))
        pieChart.setCenterTextSize(16f)
        pieChart.isHighlightPerTapEnabled = true     // 點擊時高亮
        pieChart.isRotationEnabled = true            // 可以旋轉圖表（用手指轉）
        pieChart.legend.isEnabled = false            // 隱藏圖例（用底下的 Chip 代替）
        pieChart.invalidate()                        // 重新繪製

        // ----- 步驟 8：設定點擊圓餅圖的監聽器 -----
        // 白話文：使用者點擊某個扇形時，顯示該分類的詳細明細
        pieChart.setOnChartValueSelectedListener(object :
            com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(
                e: com.github.mikephil.charting.data.Entry?,
                h: com.github.mikephil.charting.highlight.Highlight?
            ) {
                val pieEntry = e as? PieEntry ?: return
                val selectedCategory = pieEntry.label
                selectPieCategoryChip(selectedCategory)           // 同時選中底下的 Chip 按鈕
                showSelectedPieCategorySummary(selectedCategory)   // 顯示該分類的摘要
                showCategoryDetails(selectedCategory)              // 顯示該分類的詳細明細
            }

            override fun onNothingSelected() {
                // 點擊空白處時，清除所有選中狀態
                clearPieCategoryChipSelection()
                pieChart.centerText = "${selectedPieType}分類"
                renderPieSummary(categoryMap)
            }
        })

        // ----- 步驟 9：繪製底下的分類 Chip 按鈕 -----
        // 白話文：在圓餅圖下面畫一排可點擊的標籤按鈕
        renderPieCategoryChips(categoryMap)

        // ----- 步驟 10：顯示圓餅圖的摘要文字 -----
        renderPieSummary(categoryMap)
    }

    // ============================================================
    // 📝 顯示圓餅圖摘要文字 — 白話文：圓餅圖下面那行小字
    // ============================================================
    private fun renderPieSummary(categoryMap: List<Pair<String, Int>>) {
        if (categoryMap.isEmpty()) {
            tvPieSummary.text = "此區間無${selectedPieType}資料可分析"
            return
        }

        val previewText = buildString {
            append("點選圖塊可查看完整分類與佔比\n")
            // 只顯示前 4 個最大的分類（避免文字太長）
            categoryMap.take(4).forEach { (category, amount) ->
                val percent = amount / currentPieTotalAmount * 100f
                append("$category：${String.format(Locale.getDefault(), "%.1f", percent)}%\n")
            }
        }.trim()

        tvPieSummary.text = previewText
    }

    // ============================================================
    // 🏷️ 繪製圓餅圖的分類 Chip 按鈕 — 白話文：圓餅圖下面那排可點擊的標籤
    // ============================================================
    // Chip 就像一顆一顆的小藥丸按鈕，使用者可以直接點擊分類名稱
    private fun renderPieCategoryChips(categoryMap: List<Pair<String, Int>>) {
        suppressChipCallback = true  // 暫時關閉回調，避免循環觸發
        chipGroupPieCategories.removeAllViews()

        categoryMap.forEachIndexed { index, (category, amount) ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true      // 可以被選中（像單選按鈕）
                isClickable = true      // 可以被點擊
                checkedIcon = null      // 不顯示選中時的勾勾圖示
                tag = category          // 用分類名稱當作標籤（方便找是哪個分類）
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1E6DA"))  // 米色背景
                setTextColor(Color.parseColor("#5B534E"))
                textSize = 13f
                chipStrokeWidth = 0f    // 不要邊框
                contentDescription = "$category，${amount}元"

                // 點擊 Chip 時，高亮圓餅圖對應的扇形
                setOnClickListener {
                    if (!suppressChipCallback) {
                        pieChart.highlightValue(index.toFloat(), 0)  // 高亮第 index 個扇形
                        showSelectedPieCategorySummary(category)
                        showCategoryDetails(category)
                    }
                }
            }
            chipGroupPieCategories.addView(chip)
        }

        suppressChipCallback = false
    }

    // ============================================================
    // 🎯 選中圓餅圖的某個分類 Chip — 白話文：點扇形時，同時把對應的 Chip 亮起來
    // ============================================================
    private fun selectPieCategoryChip(category: String) {
        suppressChipCallback = true
        for (index in 0 until chipGroupPieCategories.childCount) {
            val chip = chipGroupPieCategories.getChildAt(index) as? Chip ?: continue
            chip.isChecked = chip.tag == category
            stylePieCategoryChip(chip, chip.isChecked)
        }
        suppressChipCallback = false
    }

    // ============================================================
    // 🧹 清除圓餅圖分類 Chip 的選中狀態 — 白話文：把所有 Chip 變回未選中
    // ============================================================
    private fun clearPieCategoryChipSelection() {
        suppressChipCallback = true
        chipGroupPieCategories.clearCheck()
        for (index in 0 until chipGroupPieCategories.childCount) {
            val chip = chipGroupPieCategories.getChildAt(index) as? Chip ?: continue
            stylePieCategoryChip(chip, false)
        }
        suppressChipCallback = false
    }

    // ============================================================
    // 🎨 設定分類 Chip 的樣式 — 白話文：選中的 Chip 變金色，未選中的是米色
    // ============================================================
    private fun stylePieCategoryChip(chip: Chip, isSelected: Boolean) {
        if (isSelected) {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DBBB80"))  // 金色
            chip.setTextColor(Color.parseColor("#3E2C1F"))  // 深色字
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1E6DA"))  // 米色
            chip.setTextColor(Color.parseColor("#5B534E"))  // 淺色字
        }
    }

    // ============================================================
    // 💬 顯示選中的分類摘要 — 白話文：圓餅圖中央顯示「餐飲 30%」
    // ============================================================
    private fun showSelectedPieCategorySummary(category: String) {
        val amount = currentPieCategoryBreakdown.firstOrNull { it.first == category }?.second ?: return
        val percent = if (currentPieTotalAmount > 0f) amount / currentPieTotalAmount * 100f else 0f
        pieChart.centerText = "$category\n${String.format(Locale.getDefault(), "%.1f", percent)}%"
        tvPieSummary.text = "$category\n佔比 ${String.format(Locale.getDefault(), "%.1f", percent)}% ・ 總金額 NT$ $amount"
    }

    // ============================================================
    // 📋 顯示分類的詳細交易明細 — 白話文：右邊列出該分類下的每一筆交易
    // ============================================================
    private fun showCategoryDetails(category: String) {
        val detailDateFormat = SimpleDateFormat("M/d", Locale.getDefault())  // 只顯示「4/6」

        // 找出該分類的所有交易，按金額從大到小排序
        val matchedTransactions = currentFilteredTransactions
            .filter { it.type == selectedPieType && it.category == category }
            .sortedWith(
                compareByDescending<Transaction> { it.amount }
                    .thenByDescending {
                        try {
                            fullDateFormat.parse(it.time)?.time ?: 0L
                        } catch (_: Exception) {
                            0L
                        }
                    }
            )

        val totalAmount = matchedTransactions.sumOf { it.amount }

        // 顯示摘要（例如「餐飲 ・ 共 NT$ 5000」）
        tvSelectedCategorySummary.text = "$category ・ 共 NT$ $totalAmount"

        // 建立明細列表（每個項目是一筆交易）
        val detailItems = matchedTransactions.map { transaction ->
            val displayDate = try {
                val parsedDate = fullDateFormat.parse(transaction.time)
                if (parsedDate != null) detailDateFormat.format(parsedDate) else transaction.time
            } catch (_: Exception) {
                transaction.time
            }

            CategoryDetailItem(
                date = displayDate,
                noteOrCategory = if (transaction.note.isNotBlank()) transaction.note else transaction.category,
                amount = transaction.amount
            )
        }

        categoryDetailAdapter.updateData(detailItems)
    }

    // ============================================================
    // 📊 繪製長條圖（每日收支） — 白話文：畫出「每一天花多少、賺多少」的圖
    // ============================================================
    // 這個函式會畫出一根根的長條：
    //   - 🔴 紅色長條：支出（扣錢，心痛）
    //   - 🟢 綠色長條：收入（賺錢，開心）
    //   - 每一天都有兩根長條並排（支出在左，收入在右）
    //
    // 使用 MPAndroidChart 這個開源圖表庫來畫圖
    private fun setupBarChart(
        transactions: List<Transaction>,
        start: Calendar,
        end: Calendar
    ) {
        // ----- 步驟 1：建立每日支出和收入的「記帳本」-----
        // 白話文：準備兩個空白的日曆，一個記支出，一個記收入
        val dailyExpenseMap = linkedMapOf<String, Int>()  // 支出地圖：日期 → 金額
        val dailyIncomeMap = linkedMapOf<String, Int>()   // 收入地圖：日期 → 金額

        // ----- 步驟 2：產生日期區間內的所有日期 -----
        // 白話文：從開始日期到結束日期，每一天都列出來（例如 4/1, 4/2, 4/3, ...）
        val tempCalendar = start.clone() as Calendar
        tempCalendar.set(Calendar.HOUR_OF_DAY, 0)
        tempCalendar.set(Calendar.MINUTE, 0)
        tempCalendar.set(Calendar.SECOND, 0)
        tempCalendar.set(Calendar.MILLISECOND, 0)

        val endDateOnly = end.clone() as Calendar
        endDateOnly.set(Calendar.HOUR_OF_DAY, 0)
        endDateOnly.set(Calendar.MINUTE, 0)
        endDateOnly.set(Calendar.SECOND, 0)
        endDateOnly.set(Calendar.MILLISECOND, 0)

        val dateKeys = mutableListOf<String>()  // 儲存所有日期的清單（例如 ["2026/04/01", "2026/04/02", ...]）
        while (!tempCalendar.after(endDateOnly)) {
            val key = displayDateFormat.format(tempCalendar.time)  // 例如「2026/04/06」
            dateKeys.add(key)
            dailyExpenseMap[key] = 0  // 預設支出為 0（還沒花錢）
            dailyIncomeMap[key] = 0   // 預設收入為 0（還沒賺錢）
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)  // 往後一天
        }

        // ----- 步驟 3：把交易資料填入地圖（加總每一天的支出和收入）-----
        // 白話文：把所有交易拿出來，看是哪一天的，加到那一天的總金額裡
        for (transaction in transactions) {
            try {
                val parsed = fullDateFormat.parse(transaction.time) ?: continue
                val key = displayDateFormat.format(parsed)  // 取日期部分（例如「2026/04/06」）

                if (transaction.type == "收入") {
                    // 收入：加到收入地圖
                    dailyIncomeMap[key] = (dailyIncomeMap[key] ?: 0) + transaction.amount
                } else {
                    // 支出：加到支出地圖
                    dailyExpenseMap[key] = (dailyExpenseMap[key] ?: 0) + transaction.amount
                }
            } catch (_: Exception) {
                // 日期解析失敗就跳過（可能是舊資料格式有問題）
            }
        }

        // ----- 步驟 4：建立長條圖的資料條目 -----
        // 白話文：把地圖轉換成 MPAndroidChart 看得懂的格式
        val expenseEntries = mutableListOf<BarEntry>()  // 支出長條的資料
        val incomeEntries = mutableListOf<BarEntry>()   // 收入長條的資料

        dateKeys.forEachIndexed { index, date ->
            // 每一天有兩筆資料：支出金額和收入金額
            expenseEntries.add(
                BarEntry(index.toFloat(), (dailyExpenseMap[date] ?: 0).toFloat())
            )
            incomeEntries.add(
                BarEntry(index.toFloat(), (dailyIncomeMap[date] ?: 0).toFloat())
            )
        }

        // ----- 步驟 5：設定支出長條（紅色）-----
        val expenseDataSet = BarDataSet(expenseEntries, "支出")
        expenseDataSet.color = Color.parseColor("#C62828")  // 深紅色（花錢的心痛感）
        expenseDataSet.setDrawValues(false)  // 不顯示數值標籤（避免畫面太擠）

        // ----- 步驟 6：設定收入長條（綠色）-----
        val incomeDataSet = BarDataSet(incomeEntries, "收入")
        incomeDataSet.color = Color.parseColor("#2E7D32")  // 深綠色（賺錢的愉悅感）
        incomeDataSet.setDrawValues(false)

        // ----- 步驟 7：設定長條寬度和間隔 -----
        // 白話文：決定長條要多寬、彼此之間要離多遠
        val barWidth = 0.35f      // 每根長條的寬度（0.35 表示佔 35% 的空間）
        val barSpace = 0.02f      // 同組內兩根長條之間的間隔（支出和收入之間）
        val groupSpace = 0.26f    // 不同日期之間的間隔（4/1 和 4/2 之間）

        val barData = BarData(expenseDataSet, incomeDataSet)
        barData.barWidth = barWidth

        // ----- 步驟 8：設定長條圖的外觀 -----
        barChart.data = barData
        barChart.setBackgroundColor(Color.parseColor("#FFFDFB"))      // 白色背景
        barChart.description.isEnabled = false                       // 移除底部的描述文字
        barChart.legend.isEnabled = true                             // 顯示圖例（紅色=支出，綠色=收入）
        barChart.setDrawGridBackground(false)                        // 不畫網格背景
        barChart.setScaleEnabled(true)                               // 可縮放（兩指放大）
        barChart.setPinchZoom(true)                                  // 可捏合縮放
        barChart.isDragEnabled = true                                // 可拖曳（左右滑動）
        barChart.setScaleMinima(1f, 1f)                              // 最小縮放比例為 1

        // ----- 步驟 9：右側 Y 軸隱藏（我們只看左邊就好）-----
        barChart.axisRight.isEnabled = false

        // ----- 步驟 10：左側 Y 軸設定（顯示金額）-----
        val leftAxis = barChart.axisLeft
        leftAxis.axisMinimum = 0f                       // 最小從 0 開始（金額不會負的）
        leftAxis.textColor = Color.parseColor("#8C8B97") // 淺灰色文字
        leftAxis.setDrawGridLines(true)                 // 畫網格線
        leftAxis.gridColor = Color.parseColor("#EEE7DF") // 淺米色網格線

        // ----- 步驟 11：X 軸設定（顯示日期）-----
        val xAxis = barChart.xAxis
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM  // 日期顯示在底部
        xAxis.setDrawGridLines(false)                   // 不畫網格線（保持乾淨）
        xAxis.textColor = Color.parseColor("#8C8B97")
        xAxis.granularity = 1f                         // 刻度間隔為 1（一天一格）

        // ----- 步驟 12：設定 X 軸的標籤格式（聰明的日期顯示）-----
        // 白話文：根據縮放程度決定顯示「日」還是「月/日」
        //   - 放很大（看 7 天內）→ 顯示「6」（節省空間）
        //   - 普通大小（看 7~31 天）→ 顯示「4/6」
        //   - 縮很小（看超過 31 天）→ 還是顯示「4/6」
        val groupWidth = barData.getGroupWidth(groupSpace, barSpace)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                if (index !in dateKeys.indices) return ""

                // 計算目前可見的天數（螢幕上能看到的天數）
                val visibleDays = ((barChart.highestVisibleX - barChart.lowestVisibleX) / groupWidth)
                    .toInt()
                    .coerceAtLeast(1)

                // 根據可見天數決定顯示間隔（天數越多，標籤越稀疏）
                val labelStep = when {
                    visibleDays <= 7 -> 1    // 少於 7 天：每天都顯示
                    visibleDays <= 14 -> 2   // 7~14 天：每 2 天顯示一個
                    visibleDays <= 21 -> 3   // 14~21 天：每 3 天顯示一個
                    visibleDays <= 31 -> 5   // 21~31 天：每 5 天顯示一個
                    else -> 7                // 超過 31 天：每 7 天顯示一個
                }

                return if (index % labelStep == 0 || index == dateKeys.lastIndex) {
                    formatBarChartDateLabel(dateKeys[index], visibleDays)
                } else {
                    ""  // 不顯示（避免標籤擠在一起）
                }
            }
        }

        // ----- 步驟 13：設定 X 軸範圍並分組長條 -----
        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = groupWidth * dateKeys.size
        barChart.groupBars(0f, groupSpace, barSpace)  // 把長條分組（每天兩根並排）
        barChart.animateY(500)  // 動畫效果：長條從底部長出來（500 毫秒）

        // ----- 步驟 14：設定點擊長條圖的監聽器 -----
        // 白話文：使用者點擊某一天的長條時，右邊列出該天的詳細交易明細
        barChart.setOnChartValueSelectedListener(object :
            com.github.mikephil.charting.listener.OnChartValueSelectedListener {
            override fun onValueSelected(
                e: com.github.mikephil.charting.data.Entry?,
                h: com.github.mikephil.charting.highlight.Highlight?
            ) {
                val index = e?.x?.toInt() ?: return
                if (index in dateKeys.indices) {
                    showDailyDetails(dateKeys[index])  // 顯示該日的詳細明細
                }
            }

            override fun onNothingSelected() {
                // 點擊空白處時，什麼都不做
            }
        })

        barChart.invalidate()  // 重新繪製（讓設定生效）
    }

    // ============================================================
    // 📅 格式化長條圖的日期標籤 — 白話文：決定日期要顯示「6」還是「4/6」
    // ============================================================
    // 這個函式會根據使用者縮放的程度，決定日期顯示的格式：
    //   - 放很大（visibleDays <= 7）→ 只顯示「6」（節省空間）
    //   - 正常大小 → 顯示「4/6」
    private fun formatBarChartDateLabel(dateKey: String, visibleDays: Int): String {
        val parsed = try {
            displayDateFormat.parse(dateKey)
        } catch (_: Exception) {
            null
        } ?: return dateKey

        return when {
            visibleDays <= 7 -> SimpleDateFormat("d", Locale.getDefault()).format(parsed)      // 只顯示「6」
            visibleDays <= 14 -> SimpleDateFormat("M/d", Locale.getDefault()).format(parsed)  // 顯示「4/6」
            else -> SimpleDateFormat("M/d", Locale.getDefault()).format(parsed)               // 顯示「4/6」
        }
    }

    // ============================================================
    // 📋 顯示某一天的詳細明細 — 白話文：點擊長條圖的那天，右邊列出當天的所有交易
    // ============================================================
    private fun showDailyDetails(selectedDate: String) {
        // ----- 步驟 1：找出該日期的所有交易 -----
        val matchedTransactions = currentFilteredTransactions.filter { transaction ->
            try {
                val parsedDate = fullDateFormat.parse(transaction.time) ?: return@filter false
                displayDateFormat.format(parsedDate) == selectedDate
            } catch (_: Exception) {
                false
            }
        }.sortedWith(
            compareByDescending<Transaction> { it.amount }
                .thenByDescending {
                    try {
                        fullDateFormat.parse(it.time)?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                }
        )

        // ----- 步驟 2：計算該日總收入和總支出 -----
        val totalIncome = matchedTransactions
            .filter { it.type == "收入" }
            .sumOf { it.amount }

        val totalExpense = matchedTransactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        // ----- 步驟 3：格式化日期顯示（例如「4/6」）-----
        val summaryDate = try {
            val parsed = displayDateFormat.parse(selectedDate)
            SimpleDateFormat("M/d", Locale.getDefault()).format(parsed!!)
        } catch (_: Exception) {
            selectedDate
        }

        // ----- 步驟 4：顯示當日摘要 -----
        tvSelectedDaySummary.text =
            "$summaryDate ・ 收入 NT$ $totalIncome ／ 支出 NT$ $totalExpense"

        // ----- 步驟 5：建立明細項目（每筆交易變成一個項目）-----
        val detailItems = matchedTransactions.map { transaction ->
            // 如果備註有寫東西，就用備註當標題（例如「雞排加辣」）
            // 如果沒寫備註，就用分類當標題（例如「餐飲」）
            val title = if (transaction.note.isNotBlank()) transaction.note else transaction.category
            val subTitle = "${transaction.category} ・ ${transaction.accountName}"

            DailyDetailItem(
                title = title,
                subTitle = subTitle,
                amount = transaction.amount,
                type = transaction.type
            )
        }

        // ----- 步驟 6：更新畫面（讓右邊的明細列表顯示出來）-----
        dailyDetailAdapter.updateData(detailItems)
    }
}
