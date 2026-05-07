package com.example.cococoin

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 📊【CSV 報表匯出器】
 *
 * 想像你是公司會計師，老闆突然說：
 * 「把這個月的記帳資料整理成 Excel 給我看！」
 *
 * 你的任務：
 * 1. 把所有交易紀錄（午餐、晚餐、購物...）整理成表格
 * 2. 用 CSV 格式（Excel 最愛吃的格式）打包
 * 3. 讓使用者可以匯出、寄送、或存檔
 *
 * CSV 是什麼？
 * - CSV = Comma-Separated Values（逗號分隔值）
 * - 就像一個試算表，用「逗號」當作欄位的分隔線
 * - Excel、Google 試算表、Numbers 都能輕鬆打開
 *
 * 📋 產生的報表長這樣（用 Excel 打開的樣子）：
 *
 * ┌──────────┬────────┬────┬────┬──────┬────────┬────────────┐
 * │ 日期時間  │ 類型   │分類│金額│帳戶  │備註    │ 更新時間    │
 * ├──────────┼────────┼────┼────┼──────┼────────┼────────────┤
 * │2026/04/07│支出   │餐飲│100 │現金  │午餐    │2026/04/07 12:30│
 * │12:30     │        │    │    │      │        │            │
 * ├──────────┼────────┼────┼────┼──────┼────────┼────────────┤
 * │2026/04/07│收入   │薪水│5000│銀行  │月薪    │2026/04/07 10:00│
 * │10:00     │        │    │    │      │        │            │
 * └──────────┴────────┴────┴────┴──────┴────────┴────────────┘
 *
 * 🔧 特別處理：
 * - 如果內容裡有逗號或雙引號，會自動「轉義」（escape）
 * - 確保 Excel 不會把欄位拆錯
 */
object CocoCoinCsvExporter {

    /**
     * 📤 匯出交易紀錄為 CSV 字串
     *
     * 這是整個匯出器的核心方法！
     *
     * 步驟說明：
     * 1️⃣ 先寫入報表資訊（匯出時間、總共有幾筆交易）
     * 2️⃣ 寫入欄位名稱（Excel 的第一行標題）
     * 3️⃣ 一筆一筆把交易資料寫進去
     * 4️⃣ 回傳完整的 CSV 字串
     *
     * @param snapshot 資料快照（裡面有所有交易紀錄）
     * @return CSV 格式的字串（可以直接存成 .csv 檔案）
     *
     * 使用範例：
     * val csvData = CocoCoinCsvExporter.exportTransactionsCsv(snapshot)
     * val file = File("我的記帳報表.csv")
     * file.writeText(csvData)
     * shareFile(file)
     */
    fun exportTransactionsCsv(snapshot: CocoCoinSnapshot): String {
        // 🏗️ 建立一個字串建造者（像拼圖一樣慢慢拼出 CSV 內容）
        // 為什麼用 StringBuilder？因為一直拼字串比較有效率
        // 就像先準備一個大桌子，再把東西一個一個放上去
        val builder = StringBuilder()

        // ========== 📄 第 1 部分：報表頭部資訊 ==========
        // 這一區像報紙的「頭版頭條」，告訴你這份報表的概況

        // 匯出時間：記錄這份報表是什麼時候產生的
        builder.appendLine("匯出時間,${escapeCsv(formatExportedAt(System.currentTimeMillis()))}")

        // 總筆數：總共匯出了幾筆交易
        builder.appendLine("交易筆數,${snapshot.transactions.size}")

        // 空行分隔：增加一個空行，讓視覺上更好區分（像文章換段）
        builder.appendLine()

        // ========== 📋 第 2 部分：欄位名稱（標題列） ==========
        // 這是 Excel 的第一行，告訴你每一欄代表什麼
        //
        // 欄位說明：
        // ┌──────┬──────┬──────┬──────┬──────┬──────┬──────────┐
        // │日期時間│類型  │分類  │金額  │帳戶  │備註  │更新時間   │
        // └──────┴──────┴──────┴──────┴──────┴──────┴──────────┘
        builder.appendLine("日期時間,類型,分類,金額,帳戶,備註,更新時間")

        // ========== ✍️ 第 3 部分：逐筆寫入交易資料 ==========
        // 這是最重要的一區！每一筆記帳變成 Excel 的一行

        snapshot.transactions.forEach { transaction ->
            // 把一筆交易的各個欄位打包成一個列表
            val row = listOf(
                transaction.time,              // 📅 日期時間（例如：2026/04/07 12:30）
                transaction.type,              // 🏷️ 類型（支出 或 收入）
                transaction.category,          // 🍔 分類（餐飲、交通、薪水...）
                transaction.amount.toString(), // 💰 金額（100、5000...）
                transaction.accountName,       // 💳 帳戶名稱（現金、信用卡...）
                transaction.note,              // 📝 備註（午餐、買衣服...）
                formatExportedAt(transaction.updatedAt)  // 🕐 最後修改時間
            )

            // 🔧 把這一行轉成 CSV 格式：
            // - 用 joinToString(",") 把欄位用逗號連接起來
            // - 對每個欄位執行 escapeCsv() 處理特殊符號
            builder.appendLine(
                row.joinToString(",") { value -> escapeCsv(value) }
            )
        }

        // 🎉 回傳完成的 CSV 字串
        return builder.toString()
    }

    // ========== 🛠️ 工具函數區（小幫手） ==========

    /**
     * 🕐 把時間戳記（毫秒）轉成可讀的日期時間格式
     *
     * 白話：把電腦看得懂的數字，變成阿嬤也看得懂的文字
     *
     * 魔法公式：
     * - 輸入：1744012800000（一串神秘數字）
     * - 輸出：「2026/04/07 14:30:00」（人類看得懂的時間）
     *
     * 為什麼需要這個？
     * 電腦喜歡用數字存時間（從 1970/01/01 開始算的毫秒數）
     * 但人類喜歡看「2026 年 4 月 7 日」這種格式～
     *
     * @param timestamp 時間戳記（毫秒），例如 System.currentTimeMillis()
     * @return 格式化的日期時間字串，如「2026/04/07 14:30:00」
     *
     * 小知識：
     * 1970/01/01 這一天在電腦界叫做「Unix 紀元」（電腦的創世紀）
     * 就像西元元年是耶穌出生的那一年一樣～
     */
    private fun formatExportedAt(timestamp: Long): String {
        // SimpleDateFormat：時間格式化魔法師
        // "yyyy/MM/dd HH:mm:ss" 是他的咒語：
        // - yyyy：四位數年份（2026）
        // - MM：兩位數月份（04）
        // - dd：兩位數日期（07）
        // - HH：24 小時制的小時（14）
        // - mm：分鐘（30）
        // - ss：秒鐘（00）
        return SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * 🛡️ 轉義 CSV 特殊字符
     *
     * 為什麼需要這個？因為 CSV 有幾個「敏感字符」：
     * - 逗號（,）：CSV 用它來分隔欄位
     * - 雙引號（"）：CSV 用它來包裝文字
     * - 換行符號：可能讓你資料長到第二行去
     *
     * 策略：把所有內容用雙引號包起來，裡面的雙引號變成兩個
     *
     * 舉例說明：
     * ┌─────────────────┬──────────────────────────────────┐
     * │ 原始內容        │ 轉義後                            │
     * ├─────────────────┼──────────────────────────────────┤
     * │ 午餐            │ "午餐"                            │
     * │ 內容有逗號,對吧  │ "內容有逗號,對吧"                  │
     * │ 他說"好"        │ "他說""好"""                      │
     * │  他  說  好     │ "  他  說  好  "                  │
     * └─────────────────┴──────────────────────────────────┘
     *
     * 白話：不管內容是什麼，都給他穿上一件「雙引號外套」
     *       如果內容已經有雙引號，就改成兩個雙引號（代表裡面的雙引號沒殺傷力）
     *
     * @param value 原始字串（可能包含逗號或雙引號）
     * @return 包好雙引號的安全版字串
     */
    private fun escapeCsv(value: String): String {
        // 步驟 1：把內容裡的「"」變成「""」
        // 為什麼是兩個雙引號？這是 CSV 的規則：
        // 一個雙引號代表「要被轉義的雙引號」
        //
        // 例如：他說"好" → 他說""好""（CSV 讀取時會自動還原成他說"好"）
        val escaped = value.replace("\"", "\"\"")

        // 步驟 2：前後加上雙引號包起來
        // 為什麼要用雙引號包？
        // 1. 確保內容裡的逗號不會被當成分隔符
        // 2. 確保內容裡的換行不會被當成換行
        // 3. 確保什麼妖魔鬼怪都能正確處理
        return "\"$escaped\""
    }
}

/**
 * 📚【CSV 小教室】
 *
 * CSV 檔案範例（用記事本打開的樣子）：
 *
 * 匯出時間,"2026/04/07 14:30:00"
 * 交易筆數,3
 *
 * 日期時間,類型,分類,金額,帳戶,備註,更新時間
 * "2026/04/07 12:30","支出","餐飲","100","現金","午餐","2026/04/07 12:30:00"
 * "2026/04/07 13:00","支出","交通","50","現金","搭捷運","2026/04/07 13:00:00"
 * "2026/04/07 10:00","收入","薪水","5000","銀行","月薪","2026/04/07 10:00:00"
 *
 * 特色：
 * - 第一行：匯出時間
 * - 第二行：總筆數
 * - 第三行：空行（漂亮分隔）
 * - 第四行：標題列
 * - 後面：一筆一筆的資料
 *
 * 用 Excel 打開後，會自動變成漂亮的表格！
 * 而且可以直接匯入到 Google 試算表、Numbers 等其他軟體～
 */

/**
 * 💡【為什麼用 object 而不用 class？】
 *
 * CocoCoinCsvExporter 是一個「工具類」
 * 他沒有自己的狀態（沒有需要記憶的東西）
 * 不需要建立多個副本（CSV 轉換器不需要兩個）
 *
 * 所以用 object 最適合！
 * 白話：這個轉換器就像「印表機」，全世界有一台就夠了～
 */

/**
 * 🎭【使用情境 - 劇場版】
 *
 * 情境：使用者想把自己的記帳資料備份到電腦
 *
 * 第 1 幕：按下「匯出 CSV」
 *    ↓
 * 第 2 幕：收集資料快照
 *    val snapshot = repository.buildSnapshot()
 *    ↓
 * 第 3 幕：呼叫 CSV 匯出器
 *    val csvData = CocoCoinCsvExporter.exportTransactionsCsv(snapshot)
 *    ↓
 * 第 4 幕：儲存成檔案
 *    val file = File(getExternalFilesDir(null), "記帳報表_20260407.csv")
 *    file.writeText(csvData)
 *    ↓
 * 第 5 幕：分享檔案
 *    val intent = Intent(Intent.ACTION_SEND).apply {
 *        type = "text/csv"
 *        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(...))
 *    }
 *    startActivity(intent)
 *    ↓
 * 第 6 幕：使用者把檔案傳到電腦
 *    ↓
 * 第 7 幕：用 Excel 打開，看到精美表格 ✨
 *
 * 成果：使用者可以自己用 Excel 做更進階的分析！
 */