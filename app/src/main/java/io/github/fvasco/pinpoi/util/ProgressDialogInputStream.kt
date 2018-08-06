package io.github.fvasco.pinpoi.util

import android.app.ProgressDialog
import android.content.DialogInterface
import org.jetbrains.anko.doAsync

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Input stream that control a progress dialog

 * @author Francesco Vasco
 */
class ProgressDialogInputStream(private val input: InputStream, private val progressDialog: ProgressDialog) : FilterInputStream(input), DialogInterface.OnCancelListener {

    init {
        progressDialog.setProgressNumberFormat("%1$,d / %2$,d")
        progressDialog.setCancelable(true)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setOnCancelListener(this)
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val b = super.read()
        if (b >= 0) progressDialog.incrementProgressBy(1)
        return b
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
        val count = super.read(buffer, byteOffset, byteCount)
        if (count >= 0) progressDialog.incrementProgressBy(count)
        return count
    }

    @Throws(IOException::class)
    override fun skip(byteCount: Long): Long {
        val count = super.skip(byteCount)
        if (count >= 0) progressDialog.incrementProgressBy(count.toInt())
        return count
    }

    override fun markSupported(): Boolean = false

    override fun onCancel(dialog: DialogInterface) {
        doAsync {
            input.close()
        }
    }
}
