package com.example.cococoin

import android.app.AlertDialog          // 📢 呼叫「小喇叭」：用來跳出警告或通知的小視窗
import android.app.Dialog               // 🏠 視窗的地基：所有對話框的祖先
import android.os.Bundle                // ✉️ 資料信封：專門用來裝資料，在不同的畫面之間寄來寄去
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf        // 📦 打包小助手：讓你幾秒鐘就把資料塞進信封
import androidx.fragment.app.DialogFragment  // 🖼️ 彈窗小隊員：可以優雅地漂浮在主畫面上的小視窗

/**
 * 【TransactionDetailDialogFragment：交易詳情小卡】
 * 當你在記帳首頁點了一下某筆紀錄，這個「小分身」就會跳出來，把這筆帳的底細全招了！
 */
class TransactionDetailDialogFragment : DialogFragment() {

    // 🏗️ 視窗動工中：當系統準備要把這個小視窗蓋出來的時候會執行這裡
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // 🎨 拿設計圖：把「dialog_transaction_detail.xml」這張圖紙，變成我們手摸得到的畫面
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_transaction_detail, null)

        // 📬 拆信時間：從信封（arguments）裡把這筆交易的「身世背景」通通挖出來
        val id = requireArguments().getInt(ARG_ID)                    // 這筆帳的身份證字號
        val type = requireArguments().getString(ARG_TYPE, "")         // 是「領錢」還是「噴錢」？
        val category = requireArguments().getString(ARG_CATEGORY, "") // 是拿去「吃好料」還是「繳房租」？
        val amount = requireArguments().getInt(ARG_AMOUNT)            // 到底噴了幾張小朋友？
        val note = requireArguments().getString(ARG_NOTE, "")         // 當時有沒有寫什麼抱怨的話？
        val time = requireArguments().getString(ARG_TIME, "")         // 案發的確切時間
        val accountName = requireArguments().getString(ARG_ACCOUNT, "未指定帳戶") // 錢是從哪個錢包溜走的？

        // ✒️ 填寫資訊：把挖出來的資料，一個一個填到畫面上的空格裡
        view.findViewById<TextView>(R.id.tvDetailType).text = "類型：$type"
        view.findViewById<TextView>(R.id.tvDetailCategory).text = "分類：$category"
        view.findViewById<TextView>(R.id.tvDetailAmount).text = "金額：NT$ $amount"
        view.findViewById<TextView>(R.id.tvDetailAccount).text = "帳戶：$accountName"

        // 📝 檢查備註：如果沒寫備註，就體貼地顯示「無」，不要讓畫面留白看起來很尷尬
        view.findViewById<TextView>(R.id.tvDetailNote).text =
            if (note.isBlank()) "備註：無" else "備註：$note"

        view.findViewById<TextView>(R.id.tvDetailDateTime).text = "時間：$time"

        // 🛠️ 組合視窗：拿「AlertDialog」這套積木，把剛剛填好資料的畫面裝進去
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)      // 將設計好的「詳情畫面」塞入視窗
            .create()           // 叮！視窗做好了

        // 🗑️ 刪除按鈕（悔不當初鍵）：
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {

            // ⚠️ 二次確認：怕你手滑誤刪，多問一句「你真的不要這段回憶了嗎？」
            AlertDialog.Builder(requireContext())
                .setTitle("刪除交易")
                .setMessage("確定要刪除這筆資料嗎？（刪了就回不來囉！）")
                .setPositiveButton("狠心刪除") { _, _ ->
                    // 📡 發射廣播：大喊「我要刪掉編號 $id 的這筆帳！」讓外面的畫面收到消息去處理
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,  // 廣播頻道：詳情小卡專用
                        bundleOf(
                            KEY_ACTION to ACTION_DELETE,  // 傳達指令：我要「刪除」
                            ARG_ID to id                    // 告訴對方：是這一個 ID 喔！
                        )
                    )
                    dismiss()  // 指令傳達完畢，關閉視窗，揮揮衣袖不帶走雲彩
                }
                .setNegativeButton("點錯了", null)  // 點錯的話就當作沒這回事
                .show()  // 讓確認視窗跳出來
        }

        // ✏️ 編輯按鈕（時光機鍵）：
        view.findViewById<Button>(R.id.btnEdit).setOnClickListener {

            // 🛠️ 變身：把這些舊資料重新打包，交給「編輯專用的小視窗」去處理
            val editDialog = EditTransactionDialogFragment.newInstance(
                Transaction(
                    id = id,
                    type = type,
                    category = category,
                    amount = amount,
                    note = note,
                    time = time,
                    accountName = accountName
                )
            )
            // 🎬 換場：把編輯視窗秀出來
            editDialog.show(parentFragmentManager, "EditTransactionDialog")

            dismiss()  // 舊的詳情畫面就功成身退，先行告退了
        }

        return dialog  // 最後把這個精心打造的視窗交給系統：請顯示！
    }

    // 🏢 辦公室小後勤（靜態區）：存放一些大家通用的暗號和工具
    companion object {
        // 📡 通訊暗號：讓外面的人知道是誰在發消息
        const val REQUEST_KEY = "transaction_detail_result"
        const val KEY_ACTION = "action"      // 詢問：「要做啥？」
        const val ACTION_DELETE = "delete"   // 回答：「要刪除！」

        // 🔑 鑰匙名稱：幫信封裡的每一張紙取個名字，才不會拿錯
        private const val ARG_ID = "id"
        private const val ARG_TYPE = "type"
        private const val ARG_CATEGORY = "category"
        private const val ARG_AMOUNT = "amount"
        private const val ARG_NOTE = "note"
        private const val ARG_TIME = "time"
        private const val ARG_ACCOUNT = "account"

        /**
         * 🎁 快速打包工具：
         * 只要把整筆「交易」丟進來，我就會幫你寫好信封、裝好包裹，直接給你一個準備好顯示的視窗！
         */
        fun newInstance(transaction: Transaction): TransactionDetailDialogFragment {
            val fragment = TransactionDetailDialogFragment()  // 準備一個新視窗殼子
            val bundle = Bundle().apply {                     // 拿出一張大信封
                putInt(ARG_ID, transaction.id)                // 塞入 ID
                putString(ARG_TYPE, transaction.type)         // 塞入類型
                putString(ARG_CATEGORY, transaction.category) // 塞入分類
                putInt(ARG_AMOUNT, transaction.amount)        // 塞入錢錢
                putString(ARG_NOTE, transaction.note)         // 塞入心底話
                putString(ARG_TIME, transaction.time)         // 塞入時間
                putString(ARG_ACCOUNT, transaction.accountName) // 塞入錢包名字
            }
            fragment.arguments = bundle  // 把信封黏到視窗殼子上
            return fragment              // 搞定，把這包禮物還給你
        }
    }
}