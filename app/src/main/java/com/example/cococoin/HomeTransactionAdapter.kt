package com.example.cococoin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.LinkedHashMap
import java.util.Locale

// 首頁交易列表的轉接器：
// 負責把「交易資料」變成「畫面上的一個個項目」
// 支援兩種項目類型：日期標頭（3/27）和交易內容（餐飲 -NT$100）
class HomeTransactionAdapter(
    private val onItemClick: (Transaction) -> Unit  // 點擊交易時要執行的事（打開詳情）
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 要顯示的項目清單（可能是日期標頭，也可能是交易）
    private val items = mutableListOf<HomeTransactionListItem>()

    // 日期格式化工具
    private val inputDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    private val dayHeaderFormat = SimpleDateFormat("M/d", Locale.getDefault())  // 「3/27」
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())     // 「14:30」

    // 根據項目類型回傳不同的 ViewType（用來決定用哪種佈局）
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HomeTransactionListItem.DayHeader -> VIEW_TYPE_HEADER      // 日期標頭
            is HomeTransactionListItem.TransactionRow -> VIEW_TYPE_TRANSACTION  // 交易
        }
    }

    // 建立 ViewHolder（根據類型載入不同的佈局）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction_day_header, parent, false)
                DayHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // 把資料綁定到 ViewHolder
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeTransactionListItem.DayHeader -> {
                (holder as DayHeaderViewHolder).tvDayHeader.text = item.label
            }
            is HomeTransactionListItem.TransactionRow -> {
                bindTransaction(holder as TransactionViewHolder, item.transaction)
            }
        }
    }

    // 接收交易列表，轉換成「有日期分組」的項目清單
    fun submitTransactions(transactions: List<Transaction>) {
        items.clear()

        // 1.先按時間排序（最新的在上面）
        val sortedTransactions = transactions.sortedByDescending {
            try {
                inputDateFormat.parse(it.time)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

        // 2.按日期分組（同一天的交易放在一起）
        val grouped = LinkedHashMap<String, MutableList<Transaction>>()
        sortedTransactions.forEach { transaction ->
            val dateKey = transaction.time.take(10)  // 取前10個字元 = 日期部分
            grouped.getOrPut(dateKey) { mutableListOf() }.add(transaction)
        }

        // 3.把分組後的資料轉成「日期標頭 + 交易列表」的順序
        grouped.forEach { (dateKey, dayTransactions) ->
            // 產生日期標頭的文字（例如「3/27」）
            val headerLabel = try {
                val parsed = inputDateFormat.parse("${dateKey} 00:00")
                if (parsed != null) dayHeaderFormat.format(parsed) else dateKey
            } catch (_: Exception) {
                dateKey
            }

            // 加入日期標頭
            items += HomeTransactionListItem.DayHeader(headerLabel)

            // 加入這一整天的交易
            items += dayTransactions.map { HomeTransactionListItem.TransactionRow(it) }
        }

        // 4.通知 RecyclerView 資料已經更新
        notifyDataSetChanged()
    }

    // 把一筆交易的資料顯示到畫面上
    private fun bindTransaction(holder: TransactionViewHolder, item: Transaction) {
        // 基本資訊
        holder.tvCategory.text = item.category                    // 分類名稱
        holder.tvTime.text = formatTransactionTime(item.time)     // 時間（只顯示 HH:mm）
        holder.tvAccountName.text = item.accountName              // 帳戶名稱

        // 設定帳戶標籤的樣式（不同帳戶不同顏色）
        setAccountStyle(holder.layoutAccountTag, holder.tvAccountName, item.accountName)

        // 備註：如果有就顯示，沒有就隱藏
        if (item.note.isNotBlank()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = item.note
        } else {
            holder.tvNote.visibility = View.GONE
        }

        // 根據類型（收入/支出）設定不同的顏色和符號
        if (item.type == "收入") {
            holder.tvTypeIcon.text = "收"                          // 左邊小圓圈顯示「收」
            holder.tvTypeIcon.setTextColor(Color.parseColor("#2E7D32"))  // 綠色
            holder.tvAmount.text = "+NT$ ${item.amount}"           // 金額前面加 +
            holder.tvAmount.setTextColor(Color.parseColor("#2E7D32"))    // 綠色
        } else {
            holder.tvTypeIcon.text = "支"                          // 左邊小圓圈顯示「支」
            holder.tvTypeIcon.setTextColor(Color.parseColor("#C62828"))  // 紅色
            holder.tvAmount.text = "-NT$ ${item.amount}"           // 金額前面加 -
            holder.tvAmount.setTextColor(Color.parseColor("#C62828"))    // 紅色
        }

        // 點擊整張卡片 → 打開交易詳情對話框
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    // 格式化時間（從「2026/04/06 14:30」變成「14:30」）
    private fun formatTransactionTime(rawTime: String): String {
        return try {
            val parsed = inputDateFormat.parse(rawTime)
            if (parsed != null) timeFormat.format(parsed) else rawTime
        } catch (_: Exception) {
            rawTime
        }
    }

    // 設定帳戶標籤的樣式（根據帳戶名稱決定顏色）
    private fun setAccountStyle(
        container: LinearLayout,
        tvName: TextView,
        accountName: String
    ) {
        // 從 AccountVisualStyleResolver 取得這個帳戶的樣式
        val style = AccountVisualStyleResolver.resolve(accountName)

        // 設定背景顏色和邊框
        val bg = container.background.mutate() as GradientDrawable
        bg.setColor(style.tagFillColor.toColorInt())
        bg.setStroke(1, style.badgeStrokeColor.toColorInt())

        // 設定文字顏色
        tvName.setTextColor(style.tagTextColor.toColorInt())
    }

    // ViewHolder：日期標頭（例如「3/27」）
    inner class DayHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
    }

    // ViewHolder：一筆交易（顯示分類、時間、金額...）
    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTypeIcon: TextView = itemView.findViewById(R.id.tvTypeIcon)           // 收/支 小圓圈
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)           // 分類名稱
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)                   // 時間
        val layoutAccountTag: LinearLayout = itemView.findViewById(R.id.layoutAccountTag)  // 帳戶標籤容器
        val tvAccountName: TextView = itemView.findViewById(R.id.tvAccountName)     // 帳戶名稱
        val tvNote: TextView = itemView.findViewById(R.id.tvNote)                   // 備註
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)               // 金額
    }

    // 項目類型（密封類別，只有兩種可能）
    sealed class HomeTransactionListItem {
        data class DayHeader(val label: String) : HomeTransactionListItem()         // 日期標頭
        data class TransactionRow(val transaction: Transaction) : HomeTransactionListItem()  // 交易
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0        // 日期標頭類型
        private const val VIEW_TYPE_TRANSACTION = 1   // 交易類型
    }
}