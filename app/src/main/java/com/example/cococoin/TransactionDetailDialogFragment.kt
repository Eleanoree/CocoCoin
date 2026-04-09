package com.example.cococoin

import android.app.AlertDialog          // 對話框工具（跳出小視窗）
import android.app.Dialog               // 對話框的基礎類別
import android.os.Bundle                // 用來傳遞資料的包裹（像信封）
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf        // 方便建立資料包裹的工具
import androidx.fragment.app.DialogFragment  // 對話框 Fragment（可以顯示在畫面上的小視窗）

// 交易詳情對話框：當使用者點擊某筆交易時，跳出這個小視窗顯示詳細資訊
class TransactionDetailDialogFragment : DialogFragment() {

    // 在對話框被建立時呼叫
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // 把「dialog_transaction_detail.xml」這個設計圖變成真正看得到的畫面
        val view = requireActivity().layoutInflater
            .inflate(R.layout.dialog_transaction_detail, null)

        // 從信封（arguments）裡拿出外面傳進來的資料
        val id = requireArguments().getInt(ARG_ID)                    // 交易編號
        val type = requireArguments().getString(ARG_TYPE, "")         // 類型：支出／收入
        val category = requireArguments().getString(ARG_CATEGORY, "") // 分類：餐飲、交通...
        val amount = requireArguments().getInt(ARG_AMOUNT)            // 金額
        val note = requireArguments().getString(ARG_NOTE, "")         // 備註（使用者寫的額外說明）
        val time = requireArguments().getString(ARG_TIME, "")         // 時間（幾月幾日幾點）
        val accountName = requireArguments().getString(ARG_ACCOUNT, "未指定帳戶") // 從哪個帳戶扣款

        // 把拿到的資料填到畫面上的對應位置
        view.findViewById<TextView>(R.id.tvDetailType).text = "類型：$type"
        view.findViewById<TextView>(R.id.tvDetailCategory).text = "分類：$category"
        view.findViewById<TextView>(R.id.tvDetailAmount).text = "金額：NT$ $amount"
        view.findViewById<TextView>(R.id.tvDetailAccount).text = "帳戶：$accountName"

        // 備註如果空白，就顯示「無」，否則顯示使用者寫的內容
        view.findViewById<TextView>(R.id.tvDetailNote).text =
            if (note.isBlank()) "備註：無" else "備註：$note"

        view.findViewById<TextView>(R.id.tvDetailDateTime).text = "時間：$time"

        // 開始建造對話框（用 AlertDialog）
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)      // 把剛剛做好的畫面放進去
            .create()           // 完成

        // 「刪除」按鈕：
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {

            // 跳出一個「確定要刪除嗎？」的小確認視窗
            AlertDialog.Builder(requireContext())
                .setTitle("刪除交易")
                .setMessage("確定要刪除這筆資料嗎？")
                .setPositiveButton("刪除") { _, _ ->
                    // 如果使用者按「刪除」，就透過廣播（FragmentResult）通知外面的畫面
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,  // 廣播代號
                        bundleOf(
                            KEY_ACTION to ACTION_DELETE,  // 動作：刪除
                            ARG_ID to id                    // 順便告訴外面是哪一筆要刪
                        )
                    )
                    dismiss()  // 關閉此對話框
                }
                .setNegativeButton("取消", null)  // 按取消就什麼都不做
                .show()  // 顯示確認視窗
        }

        // 「編輯」按鈕：
        view.findViewById<Button>(R.id.btnEdit).setOnClickListener {

            // 建立一個「編輯交易對話框」，並把這筆交易的資料傳進去
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
            // 顯示編輯對話框
            editDialog.show(parentFragmentManager, "EditTransactionDialog")

            dismiss()  // 關閉目前這個詳情對話框（要換成編輯畫面）
        }

        return dialog  // 把做好的對話框還回去，讓系統顯示
    }

    // 「靜態工具」： 不需建立物件就能使用
    companion object {
        // 廣播用的頻道名稱
        const val REQUEST_KEY = "transaction_detail_result"
        const val KEY_ACTION = "action"      // 要做什麼動作（刪除、編輯...）
        const val ACTION_DELETE = "delete"   // 動作：刪除

        // 資料的「鑰匙名稱」
        private const val ARG_ID = "id"
        private const val ARG_TYPE = "type"
        private const val ARG_CATEGORY = "category"
        private const val ARG_AMOUNT = "amount"
        private const val ARG_NOTE = "note"
        private const val ARG_TIME = "time"
        private const val ARG_ACCOUNT = "account"

        // 工廠方法：用來建立這個對話框，並把資料裝進信封（Bundle）
        fun newInstance(transaction: Transaction): TransactionDetailDialogFragment {
            val fragment = TransactionDetailDialogFragment()  // 建立一個空的對話框
            val bundle = Bundle().apply {                     // 建立一個信封
                putInt(ARG_ID, transaction.id)                // 把交易編號放進去
                putString(ARG_TYPE, transaction.type)         // 把類型放進去
                putString(ARG_CATEGORY, transaction.category) // 把分類放進去
                putInt(ARG_AMOUNT, transaction.amount)        // 把金額放進去
                putString(ARG_NOTE, transaction.note)         // 把備註放進去
                putString(ARG_TIME, transaction.time)         // 把時間放進去
                putString(ARG_ACCOUNT, transaction.accountName) // 把帳戶名稱放進去
            }
            fragment.arguments = bundle  // 把信封貼到對話框上
            return fragment              // 把準備好的對話框還回去
        }
    }
}