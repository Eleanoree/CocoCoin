// ============================================================
// 📋 首頁交易列表的轉接器 — 白話文：記帳本頁面的「排版師」+「分類員」
// ============================================================
//
// 情境劇：想像你有一疊記帳小卡（交易資料），想要把它們貼到一面展示牆上
//         這個 Adapter 就是那個負責「排版」的人：
//           1. 先把小卡按照日期分類（3/27 的一疊，3/26 的一疊...）
//           2. 在每疊小卡上面貼一個日期標籤（例如「3/27」）
//           3. 然後一張一張貼到牆上（RecyclerView 的每個格子）
//           4. 點擊任何一張小卡，就會打開詳情對話框
//
// RecyclerView.Adapter 是 Android 的標準元件
// 白話文：它是「資料」和「畫面」之間的橋樑，負責把資料轉換成顯示項目
// ============================================================
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

// ============================================================
// 🏗️ HomeTransactionAdapter — 交易列表的「建築工人」
// ============================================================
// 參數說明：
//   onItemClick: (Transaction) -> Unit  → 點擊交易時要執行的事（通常是打開詳情對話框）
//   這是一個「回呼函式」（callback），白話文就是「設定一個電話號碼，有事就打給我」
class HomeTransactionAdapter(
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ============================================================
    // 📦 要顯示的項目清單 — 白話文：牆上要貼的所有東西（包含日期標籤和交易卡片）
    // ============================================================
    // 這個清單裡面的項目可能是兩種東西：
    //   1. DayHeader（日期標頭，例如「3/27」）
    //   2. TransactionRow（一筆交易，例如「餐飲 -NT$100 14:30」）
    private val items = mutableListOf<HomeTransactionListItem>()

    // ============================================================
    // 🕐 日期格式化工具 — 白話文：把時間轉換成「人類看得懂」的文字
    // ============================================================
    // SimpleDateFormat 就像一個「時間翻譯機」
    //   yyyy = 年份（2026）
    //   MM = 月份（04）
    //   dd = 日期（06）
    //   HH = 小時（14，24小時制）
    //   mm = 分鐘（30）
    private val inputDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    private val dayHeaderFormat = SimpleDateFormat("M/d", Locale.getDefault())  // 「3/27」（沒有年份）
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())     // 「14:30」（只有時間）

    // ============================================================
    // 🔢 根據項目類型回傳不同的 ViewType — 白話文：決定「這是哪一種卡片」
    // ============================================================
    // RecyclerView 需要知道每個位置是「日期標頭」還是「交易內容」
    // 這樣 onCreateViewHolder 才知道要用哪一種佈局來顯示
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HomeTransactionListItem.DayHeader -> VIEW_TYPE_HEADER      // 日期標頭 → 類型 0
            is HomeTransactionListItem.TransactionRow -> VIEW_TYPE_TRANSACTION  // 交易 → 類型 1
        }
    }

    // ============================================================
    // 🏭 建立 ViewHolder — 白話文：製造一個空白的「卡片框架」
    // ============================================================
    // ViewHolder 就像一個「空白的相框」，等著你把資料填進去
    // 根據不同的 ViewType，載入不同的佈局檔案：
    //   - 日期標頭 → item_transaction_day_header.xml
    //   - 交易內容 → item_transaction.xml
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                // 載入日期標頭的設計圖
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction_day_header, parent, false)
                DayHeaderViewHolder(view)
            }
            else -> {
                // 載入交易內容的設計圖
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_transaction, parent, false)
                TransactionViewHolder(view)
            }
        }
    }

    // 總共有多少個項目要顯示（包含日期標頭 + 交易）
    override fun getItemCount(): Int = items.size

    // ============================================================
    // 🔗 把資料綁定到 ViewHolder — 白話文：把內容填進空白的相框裡
    // ============================================================
    // 這個函式會在每個項目顯示到畫面上時被呼叫
    // 根據項目類型，把對應的資料填進去
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeTransactionListItem.DayHeader -> {
                // 這是日期標頭：把 label（例如「3/27」）填到 TextView 裡
                (holder as DayHeaderViewHolder).tvDayHeader.text = item.label
            }
            is HomeTransactionListItem.TransactionRow -> {
                // 這是一筆交易：把交易資料填到畫面上
                bindTransaction(holder as TransactionViewHolder, item.transaction)
            }
        }
    }

    // ============================================================
    // 📥 接收交易列表並處理 — 白話文：把原始資料整理成「可顯示」的格式
    // ============================================================
    // 這是整個 Adapter 最核心的函式！
    // 它做了三件事：
    //   1. 把所有交易按時間排序（最新的在上面）
    //   2. 把同一天的交易分組（例如 3/27 的所有交易放一起）
    //   3. 在每組前面加上一個日期標頭（例如「3/27」）
    fun submitTransactions(transactions: List<Transaction>) {
        items.clear()  // 先清空舊的內容

        // ----- 步驟 1：按時間排序（最新的在上面）-----
        // sortedByDescending 是「從大到小」排序，最新的交易時間數字最大
        val sortedTransactions = transactions.sortedByDescending {
            try {
                // 把時間字串（「2026/04/06 14:30」）轉換成毫秒數字
                // 數字越大表示時間越晚（越新）
                inputDateFormat.parse(it.time)?.time ?: 0L
            } catch (_: Exception) {
                0L  // 如果解析失敗（資料壞掉），就當作是最舊的（0）
            }
        }

        // ----- 步驟 2：按日期分組（同一天的交易放在一起）-----
        // LinkedHashMap 會記住加入的順序（普通的 HashMap 不會）
        // 我們希望日期從最新到最舊，所以要保留順序
        val grouped = LinkedHashMap<String, MutableList<Transaction>>()
        sortedTransactions.forEach { transaction ->
            // 取時間字串的前 10 個字元就是日期部分
            // 例如「2026/04/06 14:30」→「2026/04/06」
            val dateKey = transaction.time.take(10)
            // getOrPut：如果有這個 key 就回傳對應的 List，沒有的話就建立一個新的
            grouped.getOrPut(dateKey) { mutableListOf() }.add(transaction)
        }

        // ----- 步驟 3：轉換成「日期標頭 + 交易列表」的格式 -----
        grouped.forEach { (dateKey, dayTransactions) ->
            // 產生日期標頭的文字（例如「3/27」）
            val headerLabel = try {
                // 先解析成日期物件，再用 dayHeaderFormat 格式化
                val parsed = inputDateFormat.parse("${dateKey} 00:00")
                if (parsed != null) dayHeaderFormat.format(parsed) else dateKey
            } catch (_: Exception) {
                dateKey  // 如果出錯就用原本的格式（當作備案）
            }

            // 加入日期標頭
            items += HomeTransactionListItem.DayHeader(headerLabel)

            // 加入這一整天的所有交易
            items += dayTransactions.map { HomeTransactionListItem.TransactionRow(it) }
        }

        // ----- 步驟 4：通知 RecyclerView 資料已經更新，請重新顯示 -----
        notifyDataSetChanged()
    }

    // ============================================================
    // 🎨 把一筆交易的資料顯示到畫面上 — 白話文：填寫一張交易卡片的所有欄位
    // ============================================================
    private fun bindTransaction(holder: TransactionViewHolder, item: Transaction) {
        // ----- 基本資訊 -----
        holder.tvCategory.text = item.category                    // 分類名稱（「餐飲」）
        holder.tvTime.text = formatTransactionTime(item.time)     // 時間（只顯示「14:30」）
        holder.tvAccountName.text = item.accountName              // 帳戶名稱（「現金」）

        // ----- 設定帳戶標籤的樣式（不同帳戶不同顏色）-----
        // 例如「現金」可能是綠色，「信用卡」可能是藍色
        setAccountStyle(holder.layoutAccountTag, holder.tvAccountName, item.accountName)

        // ----- 備註：如果有就顯示，沒有就隱藏 -----
        if (item.note.isNotBlank()) {
            holder.tvNote.visibility = View.VISIBLE   // 顯示備註欄位
            holder.tvNote.text = item.note            // 填入備註內容
        } else {
            holder.tvNote.visibility = View.GONE      // 隱藏備註欄位（不佔空間）
        }

        // ----- 根據類型（收入/支出）設定不同的顏色和符號 -----
        if (item.type == "收入") {
            // 收入：綠色系，左邊顯示「收」，金額前面加 +
            holder.tvTypeIcon.text = "收"
            holder.tvTypeIcon.setTextColor(Color.parseColor("#2E7D32"))  // 深綠色
            holder.tvAmount.text = "+NT$ ${item.amount}"
            holder.tvAmount.setTextColor(Color.parseColor("#2E7D32"))    // 深綠色
        } else {
            // 支出：紅色系，左邊顯示「支」，金額前面加 -
            holder.tvTypeIcon.text = "支"
            holder.tvTypeIcon.setTextColor(Color.parseColor("#C62828"))  // 深紅色
            holder.tvAmount.text = "-NT$ ${item.amount}"
            holder.tvAmount.setTextColor(Color.parseColor("#C62828"))    // 深紅色
        }

        // ----- 點擊整張卡片 → 打開交易詳情對話框 -----
        holder.itemView.setOnClickListener {
            onItemClick(item)  // 打電話給外面設定的回呼函式
        }
    }

    // ============================================================
    // 🕐 格式化時間 — 白話文：從「2026/04/06 14:30」變成「14:30」
    // ============================================================
    // 為什麼要這樣做？
    //   首頁的交易列表空間有限，不需要顯示完整的日期（因為上面已經有日期標頭了）
    //   只需要顯示「幾點幾分」就好
    private fun formatTransactionTime(rawTime: String): String {
        return try {
            val parsed = inputDateFormat.parse(rawTime)
            if (parsed != null) timeFormat.format(parsed) else rawTime
        } catch (_: Exception) {
            rawTime  // 解析失敗就回傳原本的字串（當作備案）
        }
    }

    // ============================================================
    // 🎨 設定帳戶標籤的樣式 — 白話文：幫帳戶名稱穿上不同顏色的衣服
    // ============================================================
    // 為什麼要不同顏色？
    //   讓使用者一眼就能看出這筆錢是從「現金」還是「信用卡」花的
    //   例如「現金」用綠色，「信用卡」用藍色，「Line Pay」用粉色...
    private fun setAccountStyle(
        container: LinearLayout,
        tvName: TextView,
        accountName: String
    ) {
        // 從 AccountVisualStyleResolver 取得這個帳戶的樣式
        // 這就像一個「配色顧問」，根據帳戶名稱決定用什麼顏色
        val style = AccountVisualStyleResolver.resolve(accountName)

        // 設定背景顏色和邊框
        val bg = container.background.mutate() as GradientDrawable  // 取得背景並允許修改
        bg.setColor(style.tagFillColor.toColorInt())               // 設定背景色
        bg.setStroke(1, style.badgeStrokeColor.toColorInt())       // 設定邊框（寬度 1px）

        // 設定文字顏色
        tvName.setTextColor(style.tagTextColor.toColorInt())
    }

    // ============================================================
    // 🖼️ ViewHolder：日期標頭 — 白話文：一個只顯示日期的「小標籤」
    // ============================================================
    // 例如顯示「3/27」，放在同一天交易的最上面
    inner class DayHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
    }

    // ============================================================
    // 🖼️ ViewHolder：一筆交易 — 白話文：顯示完整交易資訊的「卡片」
    // ============================================================
    // 裡面包含了：
    //   - 收/支 小圓圈（左邊）
    //   - 分類名稱（例如「餐飲」）
    //   - 時間（例如「14:30」）
    //   - 帳戶標籤（例如「現金」）
    //   - 備註（如果有寫的話）
    //   - 金額（例如「-NT$ 100」）
    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTypeIcon: TextView = itemView.findViewById(R.id.tvTypeIcon)           // 收/支 小圓圈
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)           // 分類名稱
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)                   // 時間（HH:mm）
        val layoutAccountTag: LinearLayout = itemView.findViewById(R.id.layoutAccountTag)  // 帳戶標籤容器
        val tvAccountName: TextView = itemView.findViewById(R.id.tvAccountName)     // 帳戶名稱
        val tvNote: TextView = itemView.findViewById(R.id.tvNote)                   // 備註
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)               // 金額
    }

    // ============================================================
    // 📦 項目類型（密封類別）— 白話文：定義「這面牆上可以貼哪幾種東西」
    // ============================================================
    // 使用 sealed class（密封類別）的好處：
    //   1. 只有這兩種項目，不會有其他亂七八糟的類型
    //   2. 在 when 表達式中使用時，Kotlin 會知道「已經處理所有情況」，不用寫 else
    sealed class HomeTransactionListItem {
        data class DayHeader(val label: String) : HomeTransactionListItem()         // 日期標頭
        data class TransactionRow(val transaction: Transaction) : HomeTransactionListItem()  // 交易卡片
    }

    // ============================================================
    // 🧰 伴侶物件 — 白話文：工具箱，放一些固定不變的常數
    // ============================================================
    companion object {
        private const val VIEW_TYPE_HEADER = 0        // 日期標頭類型（代號 0）
        private const val VIEW_TYPE_TRANSACTION = 1   // 交易類型（代號 1）
    }
}