package io.github.fvasco.pinpoi

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mapcode.MapcodeCodec
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation
import io.github.fvasco.pinpoi.util.LocationUtil
import io.github.fvasco.pinpoi.util.escapeHtml
import io.github.fvasco.pinpoi.util.isHtml
import io.github.fvasco.pinpoi.util.showToast
import kotlinx.android.synthetic.main.placemark_detail.*
import org.jetbrains.anko.onClick
import java.util.concurrent.Future

/**
 * A fragment representing a single Placemark detail screen.
 * This fragment is either contained in a [PlacemarkListActivity]
 * in two-pane mode (on tablets) or a [PlacemarkDetailActivity]
 * on handsets.
 */
class PlacemarkDetailFragment : Fragment() {
    // show coordinates
    // show address
    // show placemark collection details
    var placemark: Placemark? = null
        set(value) {
            saveData()
            field = value
            Log.i(PlacemarkDetailFragment::class.java.simpleName, "open placemark ${value?.id}")
            placemarkAnnotation = if (value == null) null else placemarkDao.loadPlacemarkAnnotation(value)
            val placemarkCollection = if (value == null) null else placemarkCollectionDao.findPlacemarkCollectionById(value.collectionId)
            if (value != null) {
                preferences.edit().putLong(ARG_PLACEMARK_ID, value.id).apply()
            }

            (activity.findViewById(R.id.toolbarLayout) as? CollapsingToolbarLayout)?.title = value?.name
            placemarkDetailText.text = when {
                value == null -> null
                value.description.isBlank() -> value.name
                value.description.isHtml() -> Html.fromHtml("<p>${escapeHtml(value.name)}</p>${value.description}")
                else -> "${value.name}\n\n${value.description}"
            }
            noteText.setText(placemarkAnnotation?.note)
            coordinatesText.text = if (value == null)
                null
            else
                getString(R.string.location,
                        Location.convert(value.coordinates.latitude.toDouble(), Location.FORMAT_DEGREES),
                        Location.convert(value.coordinates.longitude.toDouble(), Location.FORMAT_DEGREES))
            mapcodeText.visibility = View.GONE
            if (value != null) {
                MapcodeCodec.encode(value.coordinates.latitude.toDouble(), value.coordinates.longitude.toDouble()).firstOrNull()?.let { mapcode ->
                    mapcodeText.text = "MapCode: $mapcode"
                    mapcodeText.visibility = View.VISIBLE

                }
            }
            searchAddressFuture?.cancel(true)
            addressText.text = null
            addressText.visibility = View.GONE
            if (value != null) {
                searchAddressFuture = LocationUtil.getAddressStringAsync(value.coordinates) { address ->
                    if (!address.isNullOrEmpty()) {
                        addressText?.visibility = View.VISIBLE
                        addressText?.text = address
                    }
                }
            }
            if (placemarkCollection == null) {
                collectionDescriptionTitle.visibility = View.GONE
                collectionDescriptionText.visibility = View.GONE
            } else {
                collectionDescriptionTitle.visibility = View.VISIBLE
                collectionDescriptionText.visibility = View.VISIBLE
                collectionDescriptionTitle.text = placemarkCollection.name
                collectionDescriptionText.text = placemarkCollection.description
            }
        }
    val longClickListener: View.OnLongClickListener = View.OnLongClickListener { view ->
        LocationUtil.openExternalMap(placemark!!, true, view.context)
        true
    }
    private lateinit var placemarkDao: PlacemarkDao
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    var placemarkAnnotation: PlacemarkAnnotation? = null
        private set
    private lateinit var preferences: SharedPreferences
    private var searchAddressFuture: Future<Unit>? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        preferences = activity.getSharedPreferences(PlacemarkDetailFragment::class.java.simpleName, Context.MODE_PRIVATE)
        placemarkDao = PlacemarkDao.instance
        placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkDao.open()
        placemarkCollectionDao.open()

        val id = savedInstanceState?.getLong(ARG_PLACEMARK_ID) ?: arguments.getLong(ARG_PLACEMARK_ID, preferences.getLong(ARG_PLACEMARK_ID, 0))
        preferences.edit().putLong(ARG_PLACEMARK_ID, id).apply()
    }

    override fun onStop() {
        saveData()
        super.onStop()
    }

    override fun onDestroy() {
        placemarkDao.close()
        placemarkCollectionDao.close()
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.placemark_detail, container, false)
    }

    override fun onStart() {
        super.onStart()
        shareButton.onClick { onShare() }
        // By default these links will appear but not respond to user input.
        placemarkDetailText.movementMethod = LinkMovementMethod.getInstance()
        placemark = placemarkDao.getPlacemark(preferences.getLong(ARG_PLACEMARK_ID, 0))
    }

    override fun onResume() {
        super.onResume()
        resetStarFabIcon(activity.findViewById(R.id.fabStar) as FloatingActionButton)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        placemark?.let { placemark ->
            outState.putLong(ARG_PLACEMARK_ID, placemark.id)
        }
        super.onSaveInstanceState(outState)
    }

    fun onMapClick(view: View) {
        placemark?.apply {
            LocationUtil.openExternalMap(this, false, view.context)
        }
    }

    fun onShare() {
        val placemark = placemark ?: return
        val view = view ?: return
        val places = mutableListOf<String?>(placemark.name, placemark.description)
        places.add(placemarkAnnotation?.note)
        places.add(addressText.text?.toString())
        places.add(mapcodeText.text?.toString())
        with(placemark.coordinates) {
            places.add(this.toString())
            places.add(Location.convert(latitude.toDouble(), Location.FORMAT_DEGREES) + ' ' + Location.convert(longitude.toDouble(), Location.FORMAT_DEGREES))
            places.add(Location.convert(latitude.toDouble(), Location.FORMAT_MINUTES) + ' ' + Location.convert(longitude.toDouble(), Location.FORMAT_MINUTES))
            places.add(Location.convert(latitude.toDouble(), Location.FORMAT_SECONDS) + ' ' + Location.convert(longitude.toDouble(), Location.FORMAT_SECONDS))
        }
        // remove empty lines
        places.removeAll { it.isNullOrBlank() }

        // open chooser and share
        AlertDialog.Builder(view.context)
                .setTitle(getString(R.string.share))
                .setItems(places.toTypedArray()) { dialog, which ->
                    dialog.dismiss()
                    try {
                        val text = places[which]
                        var intent = Intent(Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(android.content.Intent.EXTRA_TEXT, text)
                        intent = Intent.createChooser(intent, text)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(PlacemarkDetailActivity::class.java.simpleName, "Error on map click", e)
                        showToast(e)
                    }
                }
                .show()
    }

    fun resetStarFabIcon(starFab: FloatingActionButton) {
        val drawable = if (placemarkAnnotation?.flagged ?: false)
            R.drawable.ic_bookmark_white
        else
            R.drawable.ic_bookmark_border_white
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            starFab.setImageDrawable(resources.getDrawable(drawable, activity.baseContext.theme))
        } else {
            //noinspection deprecation
            starFab.setImageDrawable(resources.getDrawable(drawable))
        }
    }

    fun onStarClick(starFab: FloatingActionButton) {
        placemarkAnnotation?.flagged = !placemarkAnnotation!!.flagged
        resetStarFabIcon(starFab)
    }

    private fun saveData() {
        // save previous annotation
        placemarkAnnotation?.apply {
            note = noteText.text.toString()
            placemarkDao.update(this)
        }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_PLACEMARK_ID = "placemarkId"
    }

}
