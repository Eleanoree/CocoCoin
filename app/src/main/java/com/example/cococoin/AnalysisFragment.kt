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

// 分析頁面：
// 用圖表分析花費和收入，包含圓餅圖（分類佔比）和長條圖（每日收支）
// 使用者可以選擇日期區間，點擊圖塊可以查看詳細明細
class AnalysisFragment : Fragment(R.layout.fragment_analysis) {

    // 每日明細相關元件
    private lateinit var tvSelectedDaySummary: TextView           // 顯示選中日期的小結
    private lateinit var rvDailyDetails: androidx.recyclerview.widget.RecyclerView  // 每日明細列表
    private lateinit var dailyDetailAdapter: DailyDetailAdapter   // 每日明細的轉接器

    // 分類明細相關元件
    private lateinit var tvSelectedCategorySummary: TextView      // 顯示選中分類的小結
    private lateinit var rvCategoryDetails: androidx.recyclerview.widget.RecyclerView  // 分類明細列表
    private lateinit var categoryDetailAdapter: CategoryDetailAdapter  // 分類明細的轉接器

    // 目前篩選後的交易（根據選中的日期區間）
    private var currentFilteredTransactions: List<Transaction> = emptyList()

    // 日期區間選擇相關元件
    private lateinit var tvRangeIncome: TextView      // 區間總收入
    private lateinit var tvRangeExpense: TextView     // 區間總支出
    private lateinit var tvRangeBalance: TextView     // 區間淨額
    private lateinit var tvStartDate: TextView        // 開始日期顯示
    private lateinit var tvEndDate: TextView          // 結束日期顯示
    private lateinit var layoutStartDate: View        // 開始日期選擇器容器
    private lateinit var layoutEndDate: View          // 結束日期選擇器容器
    private lateinit var btnAnalyze: Button           // 分析按鈕
    private lateinit var layoutAnalyzeLoading: View   // 分析中的 loading 畫面

    // 圖表相關元件
    private lateinit var pieChart: PieChart           // 圓餅圖
    private lateinit var barChart: BarChart           // 長條圖
    private lateinit var tvPieSummary: TextView       // 圓餅圖摘要文字
    private lateinit var rgPieType: RadioGroup        // 切換支出/收入的單選按鈕組
    private lateinit var rbPieExpense: RadioButton    // 支出單選按鈕
    private lateinit var rbPieIncome: RadioButton     // 收入單選按鈕
    private lateinit var chipGroupPieCategories: ChipGroup  // 圓餅圖的分類標籤群組

    // 所有交易（從資料庫讀取後暫存）
    private val allTransactions = mutableListOf<Transaction>()

    // 日期格式化工具
    private val displayDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())  // 顯示用
    private val fullDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())  // 完整格式

    // 資料倉庫
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        CocoCoinRepository.getInstance(requireContext().applicationContext)
    }

    // 選中的日期區間
    private var startCalendar: Calendar? = null   // 開始日期
    private var endCalendar: Calendar? = null     // 結束日期

    // 圓餅圖狀態
    private var selectedPieType: String = "支出"   // 目前選中的類型（支出/收入）
    private var currentPieCategoryBreakdown: List<Pair<String, Int>> = emptyList()  // 分類明細
    private var currentPieTotalAmount: Float = 0f  // 總金額
    private var suppressChipCallback = false       // 防止 Chip 回調循環

    // 當畫面建立完成時呼叫
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 綁定畫面元件
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

        // 設定分類明細列表
        categoryDetailAdapter = CategoryDetailAdapter(mutableListOf())
        rvCategoryDetails.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvCategoryDetails.adapter = categoryDetailAdapter

        // 設定每日明細列表
        tvSelectedDaySummary = view.findViewById(R.id.tvSelectedDaySummary)
        rvDailyDetails = view.findViewById(R.id.rvDailyDetails)

        dailyDetailAdapter = DailyDetailAdapter(mutableListOf())
        rvDailyDetails.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvDailyDetails.adapter = dailyDetailAdapter

        // 設定點擊事件
        layoutStartDate.setOnClickListener {
            showDatePicker(isStart = true)   // 選擇開始日期
        }

        layoutEndDate.setOnClickListener {
            showDatePicker(isStart = false)  // 選擇結束日期
        }

        // 切換支出/收入時，重新分析
        rgPieType.setOnCheckedChangeListener { _, checkedId ->
            selectedPieType = if (checkedId == R.id.rbPieIncome) "收入" else "支出"
            analyzeSelectedRange()  // 重新分析
        }

        // 點擊分析按鈕
        btnAnalyze.setOnClickListener {
            analyzeSelectedRange(showLoading = true)
        }

        // 設定預設日期區間（當月1號到今天）
        setDefaultDateRange()

        // 等待資料庫初始化完成後，載入資料並分析
        repository.ensureInitialized {
            loadTransactionsAndAnalyze()
        }
    }

    // 當畫面重新顯示時，重新載入資料
    override fun onResume() {
        super.onResume()
        loadTransactionsAndAnalyze()
    }

    // 設定預設日期區間（當月1號到今天）
    private fun setDefaultDateRange() {
        val today = Calendar.getInstance()
        val firstDay = Calendar.getInstance()
        firstDay.set(Calendar.DAY_OF_MONTH, 1)  // 當月第一天

        startCalendar = firstDay
        endCalendar = today

        tvStartDate.text = displayDateFormat.format(firstDay.time)
        tvEndDate.text = displayDateFormat.format(today.time)
    }

    // 顯示日期選擇器
    private fun showDatePicker(isStart: Boolean) {
        val calendar = if (isStart) {
            startCalendar ?: Calendar.getInstance()
        } else {
            endCalendar ?: Calendar.getInstance()
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance()
                selected.set(year, month, dayOfMonth, 0, 0, 0)
                selected.set(Calendar.MILLISECOND, 0)

                if (isStart) {
                    startCalendar = selected
                    tvStartDate.text = displayDateFormat.format(selected.time)
                } else {
                    // 結束日期設定為當天的最後一刻（23:59:59）
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

    // 更新區間統計（總收入、總支出、淨額）
    private fun updateRangeSummary(transactions: List<Transaction>) {
        val totalIncome = transactions
            .filter { it.type == "收入" }
            .sumOf { it.amount }

        val totalExpense = transactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        val balance = totalIncome - totalExpense

        tvRangeIncome.text = "NT$ $totalIncome"
        tvRangeExpense.text = "NT$ $totalExpense"
        tvRangeBalance.text = "NT$ $balance"

        // 根據淨額正負設定顏色
        if (balance > 0) {
            tvRangeBalance.setTextColor(Color.parseColor("#2E7D32"))  // 正數：綠色
        } else if (balance < 0) {
            tvRangeBalance.setTextColor(Color.parseColor("#C62828"))  // 負數：紅色
        } else {
            tvRangeBalance.setTextColor(Color.parseColor("#1E1E2A"))  // 零：深色
        }
    }

    // 載入交易資料並分析
    private fun loadTransactionsAndAnalyze() {
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return
        lifecycleOwner.lifecycleScope.launch {
            val transactions = withContext(Dispatchers.IO) {
                repository.getTransactions()
            }

            if (view == null || !isAdded) return@launch

            allTransactions.clear()
            allTransactions.addAll(transactions)
            analyzeSelectedRange()
        }
    }

    // 分析選中的日期區間
    private fun analyzeSelectedRange(showLoading: Boolean = false) {
        val start = startCalendar
        val end = endCalendar

        if (start == null || end == null) return

        // 檢查開始日期是否大於結束日期
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
            if (showLoading) {
                setAnalyzeLoading(true)  // 顯示 loading 動畫
            }

            try {
                // 過濾出日期區間內的交易
                val filtered = withContext(Dispatchers.Default) {
                    transactionsSnapshot.filter { transaction ->
                        try {
                            val date = fullDateFormat.parse(transaction.time) ?: return@filter false
                            date.time in startMillis..endMillis
                        } catch (_: Exception) {
                            false
                        }
                    }
                }

                if (view == null || !isAdded) return@launch

                currentFilteredTransactions = filtered
                updateRangeSummary(filtered)      // 更新統計數字
                setupPieChart(filtered)           // 繪製圓餅圖
                setupBarChart(filtered, startCopy, endCopy)  // 繪製長條圖

                // 重置明細顯示
                tvSelectedCategorySummary.text = "請點選上方圓餅圖查看分類明細"
                categoryDetailAdapter.updateData(emptyList())
                tvSelectedDaySummary.text = "請點選上方長條圖查看當日明細"
                dailyDetailAdapter.updateData(emptyList())
            } finally {
                if (showLoading) {
                    setAnalyzeLoading(false)  // 隱藏 loading 動畫
                }
            }
        }
    }

    // 顯示無效日期區間的狀態
    private fun showInvalidDateState() {
        currentFilteredTransactions = emptyList()
        tvPieSummary.text = "開始日期不能大於結束日期"
        currentPieCategoryBreakdown = emptyList()
        currentPieTotalAmount = 0f
        chipGroupPieCategories.removeAllViews()
        pieChart.clear()
        barChart.clear()
        tvRangeIncome.text = "NT$ 0"
        tvRangeExpense.text = "NT$ 0"
        tvRangeBalance.text = "NT$ 0"
        tvSelectedCategorySummary.text = "請重新調整日期區間"
        tvSelectedDaySummary.text = "請重新調整日期區間"
        categoryDetailAdapter.updateData(emptyList())
        dailyDetailAdapter.updateData(emptyList())
    }

    // 設定分析按鈕的 loading 狀態
    private fun setAnalyzeLoading(isLoading: Boolean) {
        btnAnalyze.isEnabled = !isLoading
        btnAnalyze.alpha = if (isLoading) 0.92f else 1f
        btnAnalyze.text = if (isLoading) "" else "開始分析"
        layoutAnalyzeLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        layoutStartDate.isEnabled = !isLoading
        layoutEndDate.isEnabled = !isLoading
        layoutStartDate.alpha = if (isLoading) 0.7f else 1f
        layoutEndDate.alpha = if (isLoading) 0.7f else 1f
    }

    // 繪製圓餅圖（分類佔比）
    private fun setupPieChart(transactions: List<Transaction>) {
        // 1.根據選中的類型過濾（只拿「支出」或只拿「收入」）
        val filteredPieTransactions = transactions.filter { it.type == selectedPieType }

        // 2.如果沒有資料，就顯示「無資料」訊息
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

        // 3.按分類統計金額（例如：餐飲總共花了 5000 元，交通花了 2000 元）
        val categoryMap = filteredPieTransactions
            .groupBy { it.category }                    // 依分類分組
            .mapValues { (_, items) -> items.sumOf { it.amount } }  // 計算每組的總金額
            .toList()                                    // 轉成 List
            .sortedByDescending { (_, amount) -> amount } // 金額大的排前面

        // 4.計算總金額（用來算百分比）
        val total = categoryMap.sumOf { (_, amount) -> amount }.toFloat()
        currentPieCategoryBreakdown = categoryMap
        currentPieTotalAmount = total

        // 5.建立圓餅圖的資料條目（每個分類一個 PieEntry）
        val entries = categoryMap.map { (category, amount) ->
            PieEntry(amount.toFloat(), category)
        }

        // 6.設定圓餅圖的顏色（固定幾個顏色輪流使用）
        val dataSet = PieDataSet(entries, "${selectedPieType}分類")
        dataSet.colors = listOf(
            Color.parseColor("#EF5350"),  // 紅
            Color.parseColor("#FF7043"),  // 橙
            Color.parseColor("#FFA726"),  // 橘
            Color.parseColor("#AB47BC"),  // 紫
            Color.parseColor("#5C6BC0"),  // 靛
            Color.parseColor("#29B6F6"),  // 藍
            Color.parseColor("#66BB6A"),  // 綠
            Color.parseColor("#8D6E63")   // 棕
        )
        dataSet.sliceSpace = 2f          // 區塊之間留一點間隔（看起來更清楚）
        dataSet.selectionShift = 14f     // 點擊時扇形會突出來 14dp
        dataSet.setDrawValues(false)     // 不顯示數字標籤（太擠了）

        // 7.把資料設定到圓餅圖
        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(pieChart))  // 顯示百分比
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
        pieChart.isRotationEnabled = true            // 可以旋轉圖表
        pieChart.legend.isEnabled = false            // 隱藏圖例（用底下的 Chip 代替）
        pieChart.invalidate()                        // 重新繪製

        // 8.設定點擊圓餅圖的監聽器：
        // 當使用者點擊某個扇形時，顯示該分類的詳細明細
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

        // 9.繪製底下的分類 Chip 按鈕（讓使用者也可以點擊分類名稱）
        renderPieCategoryChips(categoryMap)

        // 10.顯示圓餅圖的摘要文字
        renderPieSummary(categoryMap)
    }

    // 顯示圓餅圖摘要文字
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

    // 繪製圓餅圖的分類 Chip 按鈕：
    // 在圓餅圖下面畫一排可點擊的標籤按鈕
    private fun renderPieCategoryChips(categoryMap: List<Pair<String, Int>>) {
        suppressChipCallback = true  // 暫時關閉回調，避免循環觸發
        chipGroupPieCategories.removeAllViews()

        categoryMap.forEachIndexed { index, (category, amount) ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true      // 可以被選中
                isClickable = true      // 可以被點擊
                checkedIcon = null      // 不顯示選中時的勾勾圖示
                tag = category          // 用分類名稱當作標籤
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

    // 選中圓餅圖的某個分類 Chip：
    // 當使用者點擊圓餅圖的扇形時，同時把對應的 Chip 按鈕標記為「選中」
    private fun selectPieCategoryChip(category: String) {
        suppressChipCallback = true
        for (index in 0 until chipGroupPieCategories.childCount) {
            val chip = chipGroupPieCategories.getChildAt(index) as? Chip ?: continue
            chip.isChecked = chip.tag == category
            stylePieCategoryChip(chip, chip.isChecked)  // 更新樣式
        }
        suppressChipCallback = false
    }

    // 清除圓餅圖分類 Chip 的選中狀態
    private fun clearPieCategoryChipSelection() {
        suppressChipCallback = true
        chipGroupPieCategories.clearCheck()
        for (index in 0 until chipGroupPieCategories.childCount) {
            val chip = chipGroupPieCategories.getChildAt(index) as? Chip ?: continue
            stylePieCategoryChip(chip, false)
        }
        suppressChipCallback = false
    }

    // 設定分類 Chip 的樣式（選中/未選中）：
    // 選中的 Chip 變成金色背景+深色字，未選中的是米色背景+淺色字
    private fun stylePieCategoryChip(chip: Chip, isSelected: Boolean) {
        if (isSelected) {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DBBB80"))  // 金色
            chip.setTextColor(Color.parseColor("#3E2C1F"))
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1E6DA"))  // 米色
            chip.setTextColor(Color.parseColor("#5B534E"))
        }
    }

    // 顯示選中的分類摘要：
    // 在圓餅圖中央顯示該分類的名稱和佔比
    private fun showSelectedPieCategorySummary(category: String) {
        val amount = currentPieCategoryBreakdown.firstOrNull { it.first == category }?.second ?: return
        val percent = if (currentPieTotalAmount > 0f) amount / currentPieTotalAmount * 100f else 0f
        pieChart.centerText = "$category\n${String.format(Locale.getDefault(), "%.1f", percent)}%"
        tvPieSummary.text = "$category\n佔比 ${String.format(Locale.getDefault(), "%.1f", percent)}% ・ 總金額 NT$ $amount"
    }

    // 顯示分類的詳細交易明細：
    // 當使用者點擊某個分類時，右邊會列出該分類下的每一筆交易
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

    // 繪製長條圖（每日收支）：用長條圖顯示每一天的收入和支出，紅色是支出，綠色是收入
    private fun setupBarChart(
        transactions: List<Transaction>,
        start: Calendar,
        end: Calendar
    ) {
        // 1.建立每日支出和收入的地圖
        val dailyExpenseMap = linkedMapOf<String, Int>()
        val dailyIncomeMap = linkedMapOf<String, Int>()

        // 2.產生日期區間內的所有日期（從開始日期到結束日期，每一天都列出來）
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

        val dateKeys = mutableListOf<String>()
        while (!tempCalendar.after(endDateOnly)) {
            val key = displayDateFormat.format(tempCalendar.time)  // 例如「2026/04/06」
            dateKeys.add(key)
            dailyExpenseMap[key] = 0  // 預設支出為 0
            dailyIncomeMap[key] = 0   // 預設收入為 0
            tempCalendar.add(Calendar.DAY_OF_MONTH, 1)  // 往後一天
        }

        // 3.把交易資料填入地圖（加總每一天的支出和收入）
        for (transaction in transactions) {
            try {
                val parsed = fullDateFormat.parse(transaction.time) ?: continue
                val key = displayDateFormat.format(parsed)  // 取日期部分

                if (transaction.type == "收入") {
                    dailyIncomeMap[key] = (dailyIncomeMap[key] ?: 0) + transaction.amount
                } else {
                    dailyExpenseMap[key] = (dailyExpenseMap[key] ?: 0) + transaction.amount
                }
            } catch (_: Exception) {
                // 日期解析失敗就跳過
            }
        }

        // 4.建立長條圖的資料條目（每一天一組長條：支出和收入並排）
        val expenseEntries = mutableListOf<BarEntry>()
        val incomeEntries = mutableListOf<BarEntry>()

        dateKeys.forEachIndexed { index, date ->
            expenseEntries.add(
                BarEntry(index.toFloat(), (dailyExpenseMap[date] ?: 0).toFloat())
            )
            incomeEntries.add(
                BarEntry(index.toFloat(), (dailyIncomeMap[date] ?: 0).toFloat())
            )
        }

        // 5.設定支出長條（紅色）
        val expenseDataSet = BarDataSet(expenseEntries, "支出")
        expenseDataSet.color = Color.parseColor("#C62828")  // 紅色
        expenseDataSet.setDrawValues(false)  // 不顯示數值標籤

        // 6.設定收入長條（綠色）
        val incomeDataSet = BarDataSet(incomeEntries, "收入")
        incomeDataSet.color = Color.parseColor("#2E7D32")  // 綠色
        incomeDataSet.setDrawValues(false)

        // 7.設定長條寬度和間隔
        val barWidth = 0.35f      // 每根長條的寬度
        val barSpace = 0.02f      // 同組內兩根長條之間的間隔
        val groupSpace = 0.26f    // 不同日期之間的間隔

        val barData = BarData(expenseDataSet, incomeDataSet)
        barData.barWidth = barWidth

        // 8.把資料設定到長條圖
        barChart.data = barData
        barChart.setBackgroundColor(Color.parseColor("#FFFDFB"))
        barChart.description.isEnabled = false           // 移除描述文字
        barChart.legend.isEnabled = true                 // 顯示圖例（紅色=支出，綠色=收入）
        barChart.setDrawGridBackground(false)
        barChart.setScaleEnabled(true)                   // 可縮放
        barChart.setPinchZoom(true)                      // 可捏合縮放
        barChart.isDragEnabled = true                    // 可拖曳
        barChart.setScaleMinima(1f, 1f)

        // 9.右側 Y 軸隱藏（我們只看左邊就好）
        barChart.axisRight.isEnabled = false

        // 10.左側 Y 軸設定（顯示金額）
        val leftAxis = barChart.axisLeft
        leftAxis.axisMinimum = 0f                       // 最小從 0 開始
        leftAxis.textColor = Color.parseColor("#8C8B97")
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#EEE7DF")

        // 11. X 軸設定（顯示日期）
        val xAxis = barChart.xAxis
        xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#8C8B97")
        xAxis.granularity = 1f

        // 12. 設定 X 軸的標籤格式（根據縮放程度決定顯示「日」還是「月/日」）
        val groupWidth = barData.getGroupWidth(groupSpace, barSpace)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                if (index !in dateKeys.indices) return ""

                // 計算目前可見的天數
                val visibleDays = ((barChart.highestVisibleX - barChart.lowestVisibleX) / groupWidth)
                    .toInt()
                    .coerceAtLeast(1)

                // 根據可見天數決定顯示間隔（天數越多，標籤越稀疏）
                val labelStep = when {
                    visibleDays <= 7 -> 1    // 少於 7 天：每天顯示
                    visibleDays <= 14 -> 2   // 7~14 天：每 2 天顯示一個
                    visibleDays <= 21 -> 3   // 14~21 天：每 3 天顯示一個
                    visibleDays <= 31 -> 5   // 21~31 天：每 5 天顯示一個
                    else -> 7                // 超過 31 天：每 7 天顯示一個
                }

                return if (index % labelStep == 0 || index == dateKeys.lastIndex) {
                    formatBarChartDateLabel(dateKeys[index], visibleDays)
                } else {
                    ""  // 不顯示
                }
            }
        }

        // 13.設定 X 軸範圍並分組長條
        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = groupWidth * dateKeys.size
        barChart.groupBars(0f, groupSpace, barSpace)
        barChart.animateY(500)  // 動畫效果（500 毫秒）

        // 14.設定點擊長條圖的監聽器：
        // 點擊某一天的長條時，顯示該天的詳細交易明細
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
                // 什麼都不做
            }
        })

        barChart.invalidate()  // 重新繪製
    }

    // 格式化長條圖的日期標籤：
    // 把「2026/04/06」變成「4/6」或「6」（根據縮放程度決定）
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

    // 顯示某一天的詳細明細：
    // 當使用者點擊長條圖的某一天時，右邊會列出該天的所有交易
    private fun showDailyDetails(selectedDate: String) {
        // 1.找出該日期的所有交易
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

        // 2.計算該日總收入和總支出
        val totalIncome = matchedTransactions
            .filter { it.type == "收入" }
            .sumOf { it.amount }

        val totalExpense = matchedTransactions
            .filter { it.type == "支出" }
            .sumOf { it.amount }

        // 3.格式化日期顯示（例如「4/6」）
        val summaryDate = try {
            val parsed = displayDateFormat.parse(selectedDate)
            SimpleDateFormat("M/d", Locale.getDefault()).format(parsed!!)
        } catch (_: Exception) {
            selectedDate
        }

        // 4.顯示當日摘要
        tvSelectedDaySummary.text =
            "$summaryDate ・ 收入 NT$ $totalIncome ／ 支出 NT$ $totalExpense"

        // 5.建立明細項目（每筆交易一個項目）
        val detailItems = matchedTransactions.map { transaction ->
            val title = if (transaction.note.isNotBlank()) transaction.note else transaction.category
            val subTitle = "${transaction.category} ・ ${transaction.accountName}"

            DailyDetailItem(
                title = title,
                subTitle = subTitle,
                amount = transaction.amount,
                type = transaction.type
            )
        }

        // 6.更新畫面
        dailyDetailAdapter.updateData(detailItems)
    }
}
