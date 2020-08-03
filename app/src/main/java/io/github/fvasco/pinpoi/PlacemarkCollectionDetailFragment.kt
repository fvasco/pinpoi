package io.github.fvasco.pinpoi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.importer.AbstractImporter
import io.github.fvasco.pinpoi.importer.FileFormatFilter
import io.github.fvasco.pinpoi.importer.ImporterFacade
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.*
import kotlinx.android.synthetic.main.placemarkcollection_detail.*
import java.io.InputStream
import java.text.DecimalFormat


/**
 * A fragment representing a single Placemark Collection detail screen.
 * This fragment is either contained in a [PlacemarkCollectionListActivity]
 * in two-pane mode (on tablets) or a [PlacemarkCollectionDetailActivity]
 * on handsets.
 */
class PlacemarkCollectionDetailFragment : Fragment() {
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    lateinit var placemarkCollection: PlacemarkCollection
        private set

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        placemarkCollectionDao = PlacemarkCollectionDao(context!!)
        placemarkCollectionDao.open()

        val arguments = arguments
        placemarkCollection = if (arguments?.containsKey(ARG_PLACEMARK_COLLECTION_ID) == true) {
            placemarkCollectionDao.findPlacemarkCollectionById(
                    savedInstanceState?.getLong(ARG_PLACEMARK_COLLECTION_ID)
                            ?: arguments.getLong(ARG_PLACEMARK_COLLECTION_ID))
                    ?: PlacemarkCollection()
        } else {
            PlacemarkCollection()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.placemarkcollection_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        categoryText.setAdapter(ArrayAdapter(context!!,
                android.R.layout.simple_dropdown_item_1line,
                placemarkCollectionDao.findAllPlacemarkCollectionCategory()))

        descriptionText.setText(placemarkCollection.description)
        sourceText.setText(placemarkCollection.source)
        categoryText.setText(placemarkCollection.category)
        fileFormatFilterButton.text = placemarkCollection.fileFormatFilter.toString()
        showUpdatedCollectionInfo()

        if (placemarkCollection.poiCount == 0) {
            showLongToast(getString(R.string.poi_count, 0), context)
        }
    }

    override fun onResume() {
        super.onResume()
        fileFormatFilterButton.setOnClickListener { openFileFormatFilterChooser() }
    }

    override fun onPause() {
        savePlacemarkCollection()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(ARG_PLACEMARK_COLLECTION_ID, placemarkCollection.id)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        try {
            placemarkCollectionDao.close()
        } finally {
            super.onDestroy()
        }
    }

    fun savePlacemarkCollection() {
        placemarkCollection.description = descriptionText.text.toString()
        placemarkCollection.source = sourceText.text.toString()
        placemarkCollection.category = categoryText.text.toString()

        try {
            if (placemarkCollection.id == 0L) {
                placemarkCollectionDao.insert(placemarkCollection)
            } else {
                placemarkCollectionDao.update(placemarkCollection)
            }
        } catch (e: Exception) {
            Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "savePlacemarkCollection", e)
            Toast.makeText(activity, R.string.validation_error, Toast.LENGTH_SHORT).show()
        }

    }

    /**
     * Update screen with poi count and last update
     */
    private fun showUpdatedCollectionInfo() {
        collectionNameText.text = placemarkCollection.name
        val poiCount = placemarkCollection.poiCount
        poiCountText.text = getString(R.string.poi_count, poiCount)
        lastUpdateText.text = getString(R.string.last_update, placemarkCollection.lastUpdate)
        lastUpdateText.visibility = if (poiCount == 0) View.GONE else View.VISIBLE
    }

    val requiredPermissionToUpdatePlacemarkCollection: String
        get() {
            val url = sourceText.text.toString()
            return if (url.startsWith("/") || url.startsWith("file:/"))
                Manifest.permission.READ_EXTERNAL_STORAGE
            else
                Manifest.permission.INTERNET
        }

    fun updatePlacemarkCollection() {
        val progressDialog = ProgressDialog(context!!)
        doAsync { updatePlacemarkCollectionImpl(progressDialog) }
    }

    private fun updatePlacemarkCollectionImpl(progressDialog: ProgressDialog, importerAndInputStream: Pair<AbstractImporter, InputStream>? = null) {
        try {
            progressDialog.setTitle(getString(R.string.update, placemarkCollection.name))
            savePlacemarkCollection()
            val oldCount = placemarkCollection.poiCount
            val importerFacade = ImporterFacade(checkNotNull(context))
            importerFacade.setProgressDialog(progressDialog)
            importerFacade.setProgressDialogMessageFormat(getString(R.string.poi_count))
            importerFacade.fileFormatFilter = placemarkCollection.fileFormatFilter
            val count =
                    if (importerAndInputStream == null) importerFacade.importPlacemarks(placemarkCollection)
                    else importerFacade.importPlacemarks(placemarkCollection, importerAndInputStream.first, importerAndInputStream.second)

            runOnUiThread {
                if (count == 0) {
                    showLongToast(getString(R.string.error_update, placemarkCollection.name, getString(R.string.n_placemarks_found, 0)), context)
                } else {
                    showLongToast(getString(R.string.update_collection_success, placemarkCollection.name, count, DecimalFormat("+0;-0").format(count - oldCount)), context)
                }
            }
        } catch (e: Exception) {
            Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "updatePlacemarkCollection", e)
            runOnUiThread {
                AlertDialog.Builder(this@PlacemarkCollectionDetailFragment.context)
                        .setMessage(getString(R.string.error_update, placemarkCollection.name, e.message))
                        .show()
            }
        } finally {
            // update placemark collection info
            runOnUiThread { showUpdatedCollectionInfo() }
        }
    }

    fun renamePlacemarkCollection(newPlacemarkCollectionName: String) {
        if (newPlacemarkCollectionName.isNotEmpty() && placemarkCollectionDao.findPlacemarkCollectionByName(newPlacemarkCollectionName) == null) {
            placemarkCollection.name = newPlacemarkCollectionName
            try {
                savePlacemarkCollection()
            } catch (e: Exception) {
                context?.showToast(e)
            } finally {
                showUpdatedCollectionInfo()
            }
        }
    }

    fun deletePlacemarkCollection() {
        val placemarkDao = PlacemarkDao(context!!)
        placemarkDao.open()
        try {
            placemarkDao.deleteByCollectionId(placemarkCollection.id)
        } finally {
            placemarkDao.close()
        }
        placemarkCollectionDao.delete(placemarkCollection)
    }

    fun openFileFormatFilterChooser() {
        AlertDialog.Builder(context)
                .setTitle(getString(R.string.fileFormatFilter))
                .setItems(FileFormatFilter.values().map { fileFormatFilter ->
                    "${fileFormatFilter.name} ${if (fileFormatFilter == FileFormatFilter.NONE) getString(R.string.any_filter) else fileFormatFilter.validExtensions.joinToString(prefix = "(", postfix = ")") { ".$it" }}"
                }.toTypedArray()) { dialog, which ->
                    dialog.tryDismiss()
                    setFileFormatFilter(FileFormatFilter.values()[which])
                }
                .show()
    }

    fun setFileFormatFilter(fileFormatFilter: FileFormatFilter) {
        placemarkCollection.fileFormatFilter = fileFormatFilter
        fileFormatFilterButton.text = fileFormatFilter.toString()
    }

    fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, CHOOSE_FILE_RESULT_ID)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == CHOOSE_FILE_RESULT_ID) {
            val uri = data?.data ?: return
            val inputStream = context?.contentResolver?.openInputStream(uri) ?: return
            val importer =
                    context?.contentResolver?.getType(uri)
                            ?.let { ImporterFacade.createImporterFromMimeType(it) }
            if (importer == null) {
                AlertDialog.Builder(this@PlacemarkCollectionDetailFragment.context)
                        .setMessage(getString(R.string.error_update, placemarkCollection.name, "Mime-Type: " + context?.contentResolver?.getType(uri)))
                        .show()
            } else {
                sourceText.setText("")
                val progressDialog = ProgressDialog(context!!)
                doAsync {
                    updatePlacemarkCollectionImpl(progressDialog, importer to inputStream)
                }
            }
        }
    }

    fun pasteUrl(view: View?) {
        val item = (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.takeIf { it.hasPrimaryClip() }
                ?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
        if (item != null) sourceText.setText(item.text)
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_PLACEMARK_COLLECTION_ID = "placemarkCollectionId"
        private const val CHOOSE_FILE_RESULT_ID = 1
    }
}
