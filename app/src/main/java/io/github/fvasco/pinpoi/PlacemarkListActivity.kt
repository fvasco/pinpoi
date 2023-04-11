package io.github.fvasco.pinpoi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.RecyclerView
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.dao.use
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult
import io.github.fvasco.pinpoi.util.*
import kotlinx.android.synthetic.main.activity_placemark_list.*
import kotlinx.android.synthetic.main.placemark_list.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt


/**
 * An activity representing a list of Placemarks. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [PlacemarkDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class PlacemarkListActivity : AppCompatActivity() {

    private var showMap: Boolean = false
    private var placemarkIdArray: LongArray? = null
    private var fragment: PlacemarkDetailFragment? = null
    private lateinit var searchCoordinate: Coordinates
    private var range: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placemark_list)
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val latitude =
            intent.getFloatExtra(ARG_LATITUDE, preferences.getFloat(ARG_LATITUDE, Float.NaN))
        val longitude =
            intent.getFloatExtra(ARG_LONGITUDE, preferences.getFloat(ARG_LONGITUDE, Float.NaN))
        searchCoordinate = Coordinates(latitude, longitude)
        range = intent.getIntExtra(ARG_RANGE, preferences.getInt(ARG_RANGE, 0))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = title

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        showMap = savedInstanceState?.getBoolean(
            ARG_SHOW_MAP,
            intent.getBooleanExtra(ARG_SHOW_MAP, preferences.getBoolean(PREFERENCE_SHOW_MAP, false))
        )
            ?: intent.getBooleanExtra(
                ARG_SHOW_MAP,
                preferences.getBoolean(PREFERENCE_SHOW_MAP, false)
            )
        if (showMap) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                == PackageManager.PERMISSION_GRANTED
            ) {
                setupWebView(map)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.INTERNET),
                    PERMISSION_SHOW_MAP
                )
            }
        } else {
            setupRecyclerView(placemarkList as RecyclerView)
        }
        preferences.edit { putBoolean(PREFERENCE_SHOW_MAP, showMap) }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_SHOW_MAP && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            setupWebView(map)
        } else {
            setupRecyclerView(placemarkList as RecyclerView)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_SHOW_MAP, showMap)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // https://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        recyclerView.visibility = View.VISIBLE
        val oldAdapter = recyclerView.adapter
        if (oldAdapter == null || oldAdapter.itemCount == 0) {
            val adapter = SimpleItemRecyclerViewAdapter()
            recyclerView.adapter = adapter
            searchPoi { placemarks ->
                // create array in background thread
                val placemarksArray = placemarks.toTypedArray()
                runOnUiThread { adapter.setPlacemarks(placemarksArray) }
            }
        }
    }

    private fun setupWebView(map: MapView) {
        val context = map.context
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName;
        map.visibility = View.VISIBLE
        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE)
        map.controller.setCenter(searchCoordinate.toGeoPoint())
        map.controller.setZoom((ln((40_000_000.0 / range)) / ln(2.0)).coerceIn(0.0, 18.0))
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.setMultiTouchControls(true)

        searchPoi { placemarksSearchResult ->
            // search center
            map.overlays.add(Marker(map).apply {
                position = searchCoordinate.toGeoPoint()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true } // do nothing on click
                icon = resources.getDrawable(R.drawable.map_marker_here, context.theme)
            })

            val collectionNameMap: Map<Long, String> =
                PlacemarkCollectionDao(applicationContext).use { placemarkCollectionDao ->
                    try {
                        placemarkCollectionDao.findAllPlacemarkCollection()
                            .associate { it.id to it.name }
                    } catch (e: Exception) {
                        Log.e(
                            PlacemarkCollectionDetailFragment::class.java.simpleName,
                            "searchPoi progress",
                            e
                        )
                        mapOf()
                    }
                }


            placemarksSearchResult
                .sortedWith(compareBy(PlacemarkSearchResult::flagged).thenBy { !it.note.isNullOrEmpty() })
                .forEach { placemark ->
                    val collectionName = collectionNameMap[placemark.collectionId] ?: ""
                    val marker = Marker(map)
                    marker.id = placemark.id.toString()
                    marker.title = placemark.name
                    marker.snippet = collectionName
                    marker.subDescription = placemark.note
                    marker.position = placemark.coordinates.toGeoPoint()
                    marker.relatedObject = placemark
                    val iconId = when {
                        placemark.flagged -> R.drawable.map_marker_favourite
                        placemark.note != null -> R.drawable.map_marker_note
                        else -> R.drawable.map_marker
                    }
                    val iconResource =
                        resources.getDrawable(iconId, context.theme)
                            .apply {
                                colorFilter = PorterDuffColorFilter(
                                    colorFor(collectionName.hashCode()),
                                    PorterDuff.Mode.SRC_ATOP
                                )
                            }
                    marker.icon = iconResource
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.infoWindow = PoiMarker(map)
                    map.overlays.add(marker)
                }
        }
    }

    private fun searchPoi(placemarksConsumer: (Collection<PlacemarkSearchResult>) -> Unit) {
        // load parameters
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val nameFilter: String = intent.getStringExtra(ARG_NAME_FILTER)
            ?: preferences.getString(ARG_NAME_FILTER, null)
            ?: ""
        val favourite =
            intent.getBooleanExtra(ARG_FAVOURITE, preferences.getBoolean(ARG_FAVOURITE, false))

        // read collections id or parse from preference
        val collectionIds = intent.getLongArrayExtra(ARG_COLLECTION_IDS)?.toSet()
            ?: preferences.getStringSet(ARG_COLLECTION_IDS, setOf())?.map(String::toLong)
            ?: emptySet()

        // save parameters in preferences
        preferences.edit {
            putFloat(ARG_LATITUDE, searchCoordinate.latitude)
            putFloat(ARG_LONGITUDE, searchCoordinate.longitude)
            putInt(ARG_RANGE, range).putBoolean(ARG_FAVOURITE, favourite)
            putString(ARG_NAME_FILTER, nameFilter)
            putStringSet(ARG_COLLECTION_IDS, collectionIds.map(Long::toString).toSet())
        }

        showProgressDialog(getString(R.string.title_placemark_list), this) {
            PlacemarkDao(applicationContext).use { placemarkDao ->
                try {
                    val placemarks = placemarkDao.findAllPlacemarkNear(
                        searchCoordinate,
                        range.toDouble(), collectionIds, nameFilter, favourite
                    )
                    Log.d(
                        PlacemarkListActivity::class.java.simpleName,
                        "searchPoi progress placemarks.size()=${placemarks.size}"
                    )
                    runOnUiThread {
                        showToast(
                            getString(R.string.n_placemarks_found, placemarks.size),
                            applicationContext
                        )
                        placemarksConsumer(placemarks)
                    }

                    // set up placemark id list for left/right swipe in placemark detail
                    placemarkIdArray = placemarks.map { it.id }.toLongArray()
                } catch (e: Exception) {
                    Log.e(
                        PlacemarkCollectionDetailFragment::class.java.simpleName,
                        "searchPoi progress",
                        e
                    )
                    runOnUiThread {
                        showLongToast(
                            getString(R.string.error_search, e.message),
                            applicationContext
                        )
                    }
                }
            }
        }
    }

    fun openPlacemark(placemarkId: Long) {
        val intent = Intent(this, PlacemarkDetailActivity::class.java)
        intent.putExtra(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
        intent.putExtra(PlacemarkDetailActivity.ARG_PLACEMARK_LIST_ID, placemarkIdArray)
        this.startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetStarFabIcon()
    }

    private fun resetStarFabIcon() {
        fragment?.resetStarFabIcon(fabStar)
    }

    fun onStarClick(@Suppress("UNUSED_PARAMETER") view: View) {
        fragment?.onStarClick(fabStar)
    }

    fun onMapClick(view: View) {
        fragment?.onMapClick(view)
    }

    inner class SimpleItemRecyclerViewAdapter :
        RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

        private val decimalFormat = DecimalFormat()
        private val stringBuilder = StringBuilder()
        private val floatArray = FloatArray(2)
        private var placemarks: Array<PlacemarkSearchResult>? = null

        init {
            decimalFormat.minimumFractionDigits = 1
            decimalFormat.maximumFractionDigits = 1
        }

        fun setPlacemarks(placemarks: Array<PlacemarkSearchResult>) {
            this.placemarks = placemarks
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val placemark = placemarks!![position]
            holder.placemark = placemark
            Location.distanceBetween(
                searchCoordinate.latitude.toDouble(),
                searchCoordinate.longitude.toDouble(),
                placemark.coordinates.latitude.toDouble(),
                placemark.coordinates.longitude.toDouble(),
                floatArray
            )
            val distance = floatArray[0]
            // calculate index for arrow
            var arrowIndex = (floatArray[1] + 45f / 2f).roundToInt()
            if (arrowIndex < 0) {
                arrowIndex += 360
            }
            arrowIndex /= 45
            if (arrowIndex < 0 || arrowIndex >= ARROWS.size) {
                arrowIndex = 0
            }

            stringBuilder.setLength(0)
            stringBuilder.append(position + 1).append(") ")
            if (distance < 1000f) {
                stringBuilder.append(distance.toInt().toString()).append(" m")
            } else {
                stringBuilder.append(
                    if (distance < 10000f)
                        decimalFormat.format((distance / 1000f).toDouble())
                    else
                        (distance.toInt() / 1000).toString()
                ).append(" ㎞")
            }
            stringBuilder.append(' ')
                .append(if (distance <= 1F) CENTER else ARROWS[arrowIndex])
                .append("  ")
                .append(placemark.name)
            holder.view.text = stringBuilder.toString()
            holder.view.typeface = when {
                placemark.flagged && placemark.note != null ->
                    Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
                placemark.flagged -> Typeface.DEFAULT_BOLD
                placemark.note != null -> Typeface.defaultFromStyle(Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }

            holder.view.setOnClickListener { openPlacemark(holder.placemark!!.id) }
            holder.view.setOnLongClickListener {
                LocationUtil(applicationContext).openExternalMap(holder.placemark!!, false)
                true
            }
        }

        override fun getItemCount() = placemarks?.size ?: 0

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val view = view.findViewById(android.R.id.text1) as TextView
            var placemark: PlacemarkSearchResult? = null
        }
    }

    private inner class PoiMarker(mapView: MapView) :
        MarkerInfoWindow(R.layout.map_placemark_bublle, mapView) {

        init {
            val btn = mView.findViewById<View>(R.id.bubble_moreinfo) as Button
            btn.background = resources.getDrawable(R.drawable.information, view.context.theme)
            btn.visibility = View.VISIBLE
            btn.setOnClickListener {
                val psr = markerReference.relatedObject as PlacemarkSearchResult
                openPlacemark(psr.id)
            }
        }
    }

    companion object {
        const val ARG_LATITUDE = "latitude"
        const val ARG_LONGITUDE = "longitude"
        const val ARG_RANGE = "range"
        const val ARG_FAVOURITE = "favourite"
        const val ARG_COLLECTION_IDS = "collectionIds"
        const val ARG_NAME_FILTER = "nameFilter"
        const val ARG_SHOW_MAP = "showMap"
        private const val PREFERENCE_SHOW_MAP = "showMap"
        private const val PERMISSION_SHOW_MAP = 1

        // clockwise arrow
        private val ARROWS = charArrayOf(
            '\u2191', // N
            '\u2197', // NE
            '\u2192', // E
            '\u2198', // SE
            '\u2193', // S
            '\u2199', // SW
            '\u2190', // W
            '\u2196' // NW
        )

        // white flag
        private const val CENTER = '\u2690'

        private val markerColors =
            arrayOf(
                0x000080, // navy
                0x0000FF, // blue
                0x008000, // green
                0x008080, // teal
                0x800000, // maroon
                0x800080, // purple
                0x808000, // olive
                0x808080, // gray
                0xA52A2A, // brown
                0xFF0000, // red
                0xFF00FF, // magenta
                0xFFA500, // orange
                0xFFFF00, // yellow
            ).map { it xor 0xFF000000u.toInt() }

        private fun colorFor(value: Int) =
            markerColors[abs(value) % markerColors.size]
    }
}
