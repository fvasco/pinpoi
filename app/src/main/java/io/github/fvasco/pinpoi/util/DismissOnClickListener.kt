package io.github.fvasco.pinpoi.util

import android.content.DialogInterface

/**
 * Dismiss dialog

 * @author Francesco Vasco
 */
object DismissOnClickListener : DialogInterface.OnClickListener {

    override fun onClick(dialog: DialogInterface, which: Int) {
        dialog.dismiss()
    }
}
