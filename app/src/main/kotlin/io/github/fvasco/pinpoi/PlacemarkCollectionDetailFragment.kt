package io.github.fvasco.pinpoi

import android.Manifest
import android.app.ProgressDialog
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
import io.github.fvasco.pinpoi.importer.ImporterFacade
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.Util
import io.github.fvasco.pinpoi.util.openFileChooser
import io.github.fvasco.pinpoi.util.showToast
import kotlinx.android.synthetic.main.placemarkcollection_detail.*
import org.jetbrains.anko.onClick

/**
 * A fragment representing a single Placemark Collection detail screen.
 * This fragment is either contained in a [PlacemarkCollectionListActivity]
 * in two-pane mode (on tablets) or a [PlacemarkCollectionDetailActivity]
 * on handsets.
 */
class PlacemarkCollectionDetailFragment : Fragment() {
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    var placemarkCollection: PlacemarkCollection? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkCollectionDao.open()

        if (arguments.containsKey(ARG_PLACEMARK_COLLECTION_ID)) {
            placemarkCollection = placemarkCollectionDao.findPlacemarkCollectionById(
                    if (savedInstanceState == null)
                        arguments.getLong(ARG_PLACEMARK_COLLECTION_ID)
                    else
                        savedInstanceState.getLong(ARG_PLACEMARK_COLLECTION_ID))

            val activity = this.activity
            val appBarLayout = activity.findViewById(R.id.toolbarLayout) as? CollapsingToolbarLayout
            if (appBarLayout != null && placemarkCollection != null) {
                appBarLayout.title = placemarkCollection!!.name
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater!!.inflate(R.layout.placemarkcollection_detail, container, false)
        return rootView
    }

    override fun onStart() {
        super.onStart()
        categoryText.setAdapter(ArrayAdapter(getContext(),
                android.R.layout.simple_dropdown_item_1line,
                placemarkCollectionDao.findAllPlacemarkCollectionCategory()))
        browseButton.onClick { showFileChooser(it) }


        placemarkCollection?.let { placemarkCollection ->
            descriptionText.setText(placemarkCollection.description)
            sourceText.setText(placemarkCollection.source)
            categoryText.setText(placemarkCollection.category)
            showUpdatedCollectionInfo()

            if (placemarkCollection.poiCount == 0) {
                showToast(getString(R.string.poi_count, 0), Toast.LENGTH_SHORT)
            }
        }

    }

    override fun onPause() {
        savePlacemarkCollection()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        if (placemarkCollection != null) {
            outState!!.putLong(ARG_PLACEMARK_COLLECTION_ID, placemarkCollection!!.id)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        placemarkCollectionDao.close()
        super.onDestroy()
    }

    fun savePlacemarkCollection() {
        if (placemarkCollection == null) {
            placemarkCollection = PlacemarkCollection()
        }
        placemarkCollection!!.description = descriptionText!!.text.toString()
        placemarkCollection!!.source = sourceText!!.text.toString()
        placemarkCollection!!.category = categoryText!!.text.toString()

        try {
            if (placemarkCollection!!.id == 0L) {
                placemarkCollectionDao.insert(placemarkCollection!!)
            } else {
                placemarkCollectionDao.update(placemarkCollection!!)
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
        val activity = this.activity
        val appBarLayout = activity.findViewById(R.id.toolbarLayout) as? CollapsingToolbarLayout
        if (appBarLayout != null) {
            appBarLayout.title = placemarkCollection!!.name
        }
        val poiCount = placemarkCollection!!.poiCount
        poiCountText!!.text = getString(R.string.poi_count, poiCount)
        lastUpdateText!!.text = getString(R.string.last_update, placemarkCollection!!.lastUpdate)
        lastUpdateText!!.visibility = if (poiCount == 0) View.GONE else View.VISIBLE
    }

    val requiredPermissionToUpdatePlacemarkCollection: String
        get() {
            val url = sourceText!!.text.toString()
            return if (url.startsWith("/") || url.startsWith("file:/"))
                Manifest.permission.READ_EXTERNAL_STORAGE
            else
                Manifest.permission.INTERNET
        }

    fun updatePlacemarkCollection() {
        val progressDialog = ProgressDialog(activity)
        progressDialog.setTitle(getString(R.string.update, placemarkCollection!!.name))
        progressDialog.setMessage(sourceText!!.text)
        Util.EXECUTOR.submit {
            try {
                savePlacemarkCollection()
                val importerFacade = ImporterFacade()
                importerFacade.setProgressDialog(progressDialog)
                importerFacade.setProgressDialogMessageFormat(getString(R.string.poi_count))
                val count = importerFacade.importPlacemarks(placemarkCollection!!)
                if (count == 0) {
                    showToast(getString(R.string.error_update, placemarkCollection!!.name, getString(R.string.n_placemarks_found, 0)), Toast.LENGTH_LONG)
                } else {
                    showToast(getString(R.string.update_collection_success, placemarkCollection!!.name, count), Toast.LENGTH_LONG)
                }
            } catch (e: Exception) {
                Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "updatePlacemarkCollection", e)
                showToast(getString(R.string.error_update, placemarkCollection!!.name, e.message), Toast.LENGTH_LONG)
            } finally {
                // update placemark collection info
                Util.MAIN_LOOPER_HANDLER.post { showUpdatedCollectionInfo() }
            }
        }
    }

    fun renamePlacemarkCollection(newPlacemarkCollectionName: String) {
        if (newPlacemarkCollectionName.isNotEmpty() && placemarkCollectionDao.findPlacemarkCollectionByName(newPlacemarkCollectionName) == null) {
            placemarkCollection!!.name = newPlacemarkCollectionName
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
            placemarkDao.deleteByCollectionId(placemarkCollection!!.id)
        } finally {
            placemarkDao.close()
        }
        placemarkCollectionDao.delete(placemarkCollection!!)
    }

    fun showFileChooser(view: View?) {
        openFileChooser(Environment.getExternalStorageDirectory(), view?.getContext() ?: getContext()) {
            sourceText.setText(it.absolutePath)
        }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        val ARG_PLACEMARK_COLLECTION_ID = "placemarkCollectionId"
    }
}