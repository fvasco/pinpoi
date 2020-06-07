package io.github.fvasco.pinpoi.util

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun openFileChooser(dir: File, context: Context, fileConsumer: (File) -> Unit) {
    val parentDir = dir.parentFile
    if (parentDir == null || dir.isDirectory && dir.canRead()) {
        val files = dir.listFiles { pathname ->
            pathname.canRead()
                    && !pathname.name.startsWith(".")
                    && (pathname.isFile || pathname.list()?.isEmpty() == false)
        } ?: emptyArray()
        val fileNames = ArrayList<String>(files.size + 1)
        // check permission/readability of parent file
        if (parentDir?.list()?.isEmpty() == false) {
            fileNames += ".."
        }
        for (file in files) {
            fileNames +=
                    if (file.isDirectory)
                        file.name + '/'
                    else
                        file.name
        }
        fileNames.sort()
        AlertDialog.Builder(context).setTitle(dir.absolutePath).setItems(fileNames.toTypedArray()) { dialog, which ->
            dialog.tryDismiss()
            val file = File(dir, fileNames[which]).absoluteFile.canonicalFile
            if (file.isDirectory) {
                openFileChooser(file, context, fileConsumer)
            } else {
                fileConsumer(file)
            }
        }.show()
    } else {
        openFileChooser(parentDir, context, fileConsumer)
    }
}

/**
 * Show indeterminate progress dialog and execute runnable in background
 * @param title    progress dialog title
 * @param context  dialog context
 * @param runnable task to execute in background
 */
fun Context.showProgressDialog(title: CharSequence, context: Context, runnable: () -> Unit) {
    val progressDialog = ProgressDialog(context)
    progressDialog.setTitle(title)
    progressDialog.show()
    doAsync {
        try {
            Log.i("showProgressDialog", "showProgressDialog begin: $title")
            runnable()
        } catch (e: Exception) {
            Log.e("showProgressDialog", "showProgressDialog error $title", e)
            showToast(e)
        } finally {
            Log.i("showProgressDialog", "showProgressDialog end: $title")
            runOnUiThread { progressDialog.tryDismiss() }
        }
    }
}

fun DialogInterface.tryDismiss() {
    if (this !is Dialog || isShowing) try {
        dismiss()
    } catch (e: Exception) {
        Log.w(DialogInterface::tryDismiss.javaClass.canonicalName, "Error on dialog dismiss", e)
        e.printStackTrace()
    }
}

fun showLongToast(text: CharSequence, context: Context?) {
    if (context == null) return
    val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
    toast.show()
}

fun showLongToast(resId: Int, context: Context?) {
    if (context == null) return
    val toast = Toast.makeText(context, resId, Toast.LENGTH_LONG)
    toast.show()
}

fun showToast(text: CharSequence, context: Context?) {
    if (context == null) return
    val toast = Toast.makeText(context, text, Toast.LENGTH_SHORT)
    toast.show()
}

fun showToast(resId: Int, context: Context?) {
    if (context == null) return
    val toast = Toast.makeText(context, resId, Toast.LENGTH_SHORT)
    toast.show()
}

fun Context.showToast(throwable: Throwable) {
    runOnUiThread {
        showLongToast(throwable.message ?: "Error ${throwable.javaClass.simpleName}", this)
    }
}

private val uiHandler = Handler(Looper.getMainLooper())
fun runOnUiThread(block: () -> Unit) {
    uiHandler.post { block() }
}

private val EXECUTOR = Executors.newCachedThreadPool()
fun <T> doAsync(block: () -> T): Future<T> = EXECUTOR.submit(Callable { block() })
