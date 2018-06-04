package io.github.fvasco.pinpoi

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.importer.FileFormatFilter
import io.github.fvasco.pinpoi.importer.ImporterFacade
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.openFileChooser
import io.github.fvasco.pinpoi.util.showToast
import io.github.fvasco.pinpoi.util.tryDismiss
import kotlinx.android.synthetic.main.placemarkcollection_detail.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.longToast
import org.jetbrains.anko.support.v4.onUiThread
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
        placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkCollectionDao.open()

        if (arguments.containsKey(ARG_PLACEMARK_COLLECTION_ID)) {
            placemarkCollection = placemarkCollectionDao.findPlacemarkCollectionById(
                    if (savedInstanceState == null)
                        arguments.getLong(ARG_PLACEMARK_COLLECTION_ID)
                    else
                        savedInstanceState.getLong(ARG_PLACEMARK_COLLECTION_ID))
                    ?: PlacemarkCollection()

            val activity = this.activity
            val appBarLayout = activity.findViewById(R.id.toolbarLayout) as? CollapsingToolbarLayout
            if (appBarLayout != null) {
                appBarLayout.title = placemarkCollection.name
            }
        } else {
            placemarkCollection = PlacemarkCollection()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.placemarkcollection_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        categoryText.setAdapter(ArrayAdapter(context,
                android.R.layout.simple_dropdown_item_1line,
                placemarkCollectionDao.findAllPlacemarkCollectionCategory()))
        fileFormatFilterButton.setOnClickListener { openFileFormatFilterChooser() }

        descriptionText.setText(placemarkCollection.description)
        sourceText.setText(placemarkCollection.source)
        categoryText.setText(placemarkCollection.category)
        fileFormatFilterButton.text = placemarkCollection.fileFormatFilter.toString()
        showUpdatedCollectionInfo()

        if (placemarkCollection.poiCount == 0) {
            longToast(getString(R.string.poi_count, 0))
        }
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
        val appBarLayout = activity.findViewById(R.id.toolbarLayout) as? CollapsingToolbarLayout
        appBarLayout?.title = placemarkCollection.name
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
        val progressDialog = ProgressDialog(activity)
        progressDialog.setTitle(getString(R.string.update, placemarkCollection.name))
        progressDialog.setMessage(sourceText.text)
        doAsync {
            try {
                savePlacemarkCollection()
                val oldCount = placemarkCollection.poiCount
                val importerFacade = ImporterFacade()
                importerFacade.setProgressDialog(progressDialog)
                importerFacade.setProgressDialogMessageFormat(getString(R.string.poi_count))
                importerFacade.fileFormatFilter = placemarkCollection.fileFormatFilter
                val count = importerFacade.importPlacemarks(placemarkCollection)
                onUiThread {
                    if (count == 0) {
                        longToast(getString(R.string.error_update, placemarkCollection.name, getString(R.string.n_placemarks_found, 0)))
                    } else {
                        longToast(getString(R.string.update_collection_success, placemarkCollection.name, count, DecimalFormat("+0;-0").format(count - oldCount)))
                    }
                }
            } catch (e: Exception) {
                Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "updatePlacemarkCollection", e)
                onUiThread {
                    AlertDialog.Builder(this@PlacemarkCollectionDetailFragment.context)
                            .setMessage(getString(R.string.error_update, placemarkCollection.name, e.message))
                            .show()
                }
            } finally {
                // update placemark collection info
                onUiThread { showUpdatedCollectionInfo() }
            }
        }
    }

    fun renamePlacemarkCollection(newPlacemarkCollectionName: String) {
        if (newPlacemarkCollectionName.isNotEmpty() && placemarkCollectionDao.findPlacemarkCollectionByName(newPlacemarkCollectionName) == null) {
            placemarkCollection.name = newPlacemarkCollectionName
            try {
                savePlacemarkCollection()
            } catch (e: Exception) {
                showToast(e)
            } finally {
                showUpdatedCollectionInfo()
            }
        }
    }

    fun deletePlacemarkCollection() {
        val placemarkDao = PlacemarkDao.instance
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
                .setItems(FileFormatFilter.values().map {
                    "${it.name} ${if (it == FileFormatFilter.NONE) getString(R.string.any_filter) else it.validExtension.joinToString(prefix = "(", postfix = ")") { ".$it" }}"
                }.toTypedArray(), { dialog, which ->
                    dialog.tryDismiss()
                    setFileFormatFilter(FileFormatFilter.values()[which])
                })
                .show()
    }

    fun setFileFormatFilter(fileFormatFilter: FileFormatFilter) {
        placemarkCollection.fileFormatFilter = fileFormatFilter
        fileFormatFilterButton.text = fileFormatFilter.toString()
    }

    fun openFileChooser(view: View?) {
        openFileChooser(Environment.getExternalStorageDirectory(), view?.context ?: context) {
            sourceText.setText(it.absolutePath)
        }
    }

    fun pasteUrl(view: View?) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.takeIf { it.hasPrimaryClip() }
                ?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.let {
                    sourceText.setText(it.text)
                }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_PLACEMARK_COLLECTION_ID = "placemarkCollectionId"
    }
}
