package io.github.fvasco.pinpoi.util

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class ProgressDialog(context: Context) : DialogInterface {

    private val dialog: AlertDialog

    private val tvText = TextView(context)

    init {
        val llPadding = 20
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.setPadding(llPadding, llPadding, llPadding, llPadding)
        ll.gravity = Gravity.CENTER
        val llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        ll.layoutParams = llParam

        val progressBar = ProgressBar(context)
        progressBar.isIndeterminate = true
        progressBar.setPadding(0, 0, llPadding, 0)
        progressBar.layoutParams = llParam
        ll.addView(progressBar)

        tvText.layoutParams = llParam
        ll.addView(tvText)

        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        builder.setView(ll)
        dialog = builder.create()
    }

    fun setTitle(title: CharSequence?) {
        tvText.text = title
    }

    fun show() {
        dialog.show()
        val window = dialog.window
        if (window != null) {
            val layoutParams = WindowManager.LayoutParams()
            layoutParams.copyFrom(window.attributes)
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
            window.attributes = layoutParams
        }
    }

    override fun dismiss() {
        dialog.dismiss()
    }

    override fun cancel() {
        dialog.cancel()
    }
}
