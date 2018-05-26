package io.github.fvasco.pinpoi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.NavUtils
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult
import io.github.fvasco.pinpoi.util.*
import kotlinx.android.synthetic.main.activity_placemark_list.*
import kotlinx.android.synthetic.main.placemark_list.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.onUiThread
import org.jetbrains.anko.toast
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

/**
 * An activity representing a list of Placemarks. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [PlacemarkDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class PlacemarkListActivity : AppCompatActivity() {
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane: Boolean = false
    private var showMap: Boolean = false
    private var placemarkIdArray: LongArray? = null
    private var fragment: PlacemarkDetailFragment? = null
    private lateinit var searchCoordinate: Coordinates
    private var range: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.applicationContext = applicationContext
        setContentView(R.layout.activity_placemark_list)
        val preference = getPreferences(Context.MODE_PRIVATE)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        toolbar.title = title

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        showMap = savedInstanceState?.getBoolean(ARG_SHOW_MAP, intent.getBooleanExtra(ARG_SHOW_MAP, preference.getBoolean(PREFEFERNCE_SHOW_MAP, false)))
                ?: intent.getBooleanExtra(ARG_SHOW_MAP, preference.getBoolean(PREFEFERNCE_SHOW_MAP, false))
        if (showMap) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
                setupWebView(mapWebView)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), PERMISSION_SHOW_MAP)
            }
        } else {
            setupRecyclerView(placemarkList)
        }
        preference.edit().putBoolean(PREFEFERNCE_SHOW_MAP, showMap).apply()

        if (findViewById(R.id.placemarkDetailContainer) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_SHOW_MAP && grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupWebView(mapWebView)
        } else {
            setupRecyclerView(placemarkList)
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
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
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
                onUiThread { adapter.setPlacemarks(placemarksArray) }
            }
        }
    }

    private fun setupWebView(mapWebView: WebView) {
        mapWebView.visibility = View.VISIBLE
        mapWebView.addJavascriptInterface(this, "pinpoi")
        mapWebView.settings.apply {
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }

        searchPoi { placemarksSearchResult ->
            val leafletVersion = "1.0.3"
            val zoom: Int = ((Math.log((40_000_000.0 / range)) / Math.log(2.0)).toInt()).coerceIn(0, 18)
            // map each collection id to color name
            val collectionNameMap: Map<Long, String> =
                    PlacemarkCollectionDao.instance.let { placemarkCollectionDao ->
                        placemarkCollectionDao.open()
                        try {
                            placemarkCollectionDao
                                    .findAllPlacemarkCollection()
                                    .map { it.id to it.name }
                                    .toMap()
                        } catch (e: Exception) {
                            Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "searchPoi progress", e)
                            mapOf()
                        } finally {
                            placemarkCollectionDao.close()
                        }
                    }

            val floatArray = FloatArray(1)
            val integerFormat = NumberFormat.getIntegerInstance()
            val markers = placemarksSearchResult.withIndex().map { (index, psr) ->
                Location.distanceBetween(searchCoordinate.latitude.toDouble(), searchCoordinate.longitude.toDouble(),
                        psr.coordinates.latitude.toDouble(), psr.coordinates.longitude.toDouble(),
                        floatArray)
                val distance = floatArray[0].toInt()
                Marker(
                        index = index + 1,
                        id = psr.id,
                        name = psr.name,
                        collectionName = collectionNameMap[psr.collectionId] ?: "",
                        coordinates = psr.coordinates,
                        distance = integerFormat.format(distance.toLong()),
                        flagged = psr.flagged
                )
            }
                    // reversed: "1" on top
                    .asReversed()
                    // flagged on top
                    .partition { it.flagged }
                    .let { it.second + it.first }

            val maptilerMapKey: String? = BuildConfig.MAPTILER_MAP_KEY.takeIf { it.isNotBlank() }
            val htmlText = StringBuilder(1024 + placemarksSearchResult.size * 256).apply {
                append("""<html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />""")
                append("""<meta http-equiv="Content-Type" content="text/html;charset=UTF-8" />""")
                append("""<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />""")
                append("""<style>
html, body {padding: 0; margin: 0; height: 100%;}
#map {position:absolute; top:0; right:0; bottom:0; left:0;}
</style>""")
                if (maptilerMapKey == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {

                    append("""<link rel="stylesheet" href="http://cdn.leafletjs.com/leaflet/v$leafletVersion/leaflet.css" />""")
                    append("""<script src="http://cdn.leafletjs.com/leaflet/v$leafletVersion/leaflet.js"></script>""")
                    // add tiles fallback https://github.com/ghybs/Leaflet.TileLayer.Fallback
                    append("""<script src="https://fvasco.github.io/pinpoi/app-lib/Leaflet.TileLayer.Fallback.js"></script>""")
                    // add icon to map https://github.com/IvanSanchez/Leaflet.Icon.Glyph
                    append("""<script src="https://fvasco.github.io/pinpoi/app-lib/Leaflet.Icon.Glyph.js"></script>""")
                    append("</html>")
                    append("""<body> <div id="map"></div> <script>""")
                    append("""var map = L.map('map').setView([$searchCoordinate], $zoom);""")
                    append("""L.tileLayer.fallback('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);""")
                    // search center marker
                    append("L.circle([$searchCoordinate], 1, {color: 'red', fillOpacity: 1}).addTo(map);")
                    // search limit circle
                    append("L.circle([$searchCoordinate], $range, {color: 'red',fillOpacity: 0}).addTo(map);")
                    append("L.circle([$searchCoordinate], ${range / 2}, {color: 'orange',fillOpacity: 0}).addTo(map);\n")

                    val collectionColors = HashMap<String, String>()
                    for (marker in markers) {
                        val markerColor = collectionColors.getOrPut(marker.collectionName) { colorFor(collectionColors.size) }
                        val glyph = StringBuilder().apply {
                            if (marker.flagged) append("<i><b>")
                            append(marker.index)
                            if (marker.flagged) append("</b></i>")
                        }

                        append("L.marker([${marker.coordinates}],{")
                        append("icon:L.icon.glyph({glyph:'$glyph', glyphColor:'$markerColor'})")
                        append("""}).addTo(map).bindPopup("""")
                        append("<a href='javascript:pinpoi.openPlacemark(${marker.id})'>")
                        if (marker.flagged) append("<b>")
                        append(escapeJavascript(marker.name))
                        if (marker.flagged) append("</b>")
                        append("</a>")
                        append("<br>${marker.distance}&nbsp;m - ")
                        append(escapeJavascript(marker.collectionName))
                        append("\");\n")
                    }
                } else {
                    append("""<style>.mapboxgl-marker {
 cursor:pointer;
 font-family: monospace;
 background-color: rgba(255, 255, 255, 0.8);
 border-radius:50%;
 border: 1px solid black;
 width: 4ex;
 height: 4ex;
 line-height: 4ex;
 vertical-align: middle;
 text-align: center;
}</style>""")
                    append("""<script src="https://api.tiles.mapbox.com/mapbox-gl-js/v0.45.0/mapbox-gl.js"></script>""")
                    append("""<script src="https://cdn.klokantech.com/openmaptiles-language/v1.0/openmaptiles-language.js"></script>""")
                    append("""<link href="https://api.tiles.mapbox.com/mapbox-gl-js/v0.45.0/mapbox-gl.css" rel="stylesheet" />""")
                    append("</html>")
                    append("""<body> <div id="map"></div> <script>""")
                    val markerHeight = 50
                    val markerRadius = 10
                    val linearOffset = 25;
                    append("""
var map = new mapboxgl.Map({
  container: 'map',
  center: [${searchCoordinate.longitude},${searchCoordinate.latitude}],
  zoom: ${zoom - 1},
  style: 'https://maps.tilehosting.com/styles/bright/style.json?key=$maptilerMapKey',
  attribution: '<a href="http://www.openmaptiles.org/" target="_blank">© OpenMapTiles</a> <a href="http://www.openstreetmap.org/about/" target="_blank">© OpenStreetMap contributors</a>'
});
map.autodetectLanguage();

var markerCenter = document.createElement('div');
markerCenter.innerText = "◉";
markerCenter.style.borderWidth = '0';
new mapboxgl.Marker(markerCenter)
 .setLngLat([${searchCoordinate.longitude},${searchCoordinate.latitude}])
 .addTo(map);

var popupOffsets = {
 'top': [0, 0],
 'top-left': [0,0],
 'top-right': [0,0],
 'bottom': [0, -$markerHeight],
 'bottom-left': [$linearOffset, ${-(markerHeight - markerRadius + linearOffset)}],
 'bottom-right': [-$linearOffset, ${-(markerHeight - markerRadius + linearOffset)}],
 'left': [$markerRadius, ${-(markerHeight - markerRadius)}],
 'right': [-$markerRadius, ${-(markerHeight - markerRadius)}]
 };
""")
                    for (marker in markers) {
                        append("""
var markerEl${marker.index} = document.createElement('div');
        markerEl${marker.index}.innerHTML = '${marker.index}';
        markerEl${marker.index}.style.borderColor = '#${stringToColorCode(marker.collectionName)}';
""")
                        if (marker.flagged)
                            append("""markerEl${marker.index}.style.borderWidth = '2px';""")

                        append("""
new mapboxgl.Marker(markerEl${marker.index})
  .setLngLat([${marker.coordinates.longitude},${marker.coordinates.latitude}])
  .setPopup(new mapboxgl.Popup({offset:popupOffsets}).setHTML("""")
                        append("<a href='javascript:pinpoi.openPlacemark(${marker.id})'>")
                        if (marker.flagged) append("<b>")
                        append(escapeJavascript(marker.name))
                        if (marker.flagged) append("</b>")
                        append("</a>")
                        append("<br>${marker.distance}&nbsp;m - ")
                        append(escapeJavascript(marker.collectionName))
                        append("\"))")
                        append(".addTo(map);\n")
                    }
                }
                append("</script> </body> </html>")
            }.toString()

            if (BuildConfig.DEBUG)
                Log.i(PlacemarkListActivity::class.java.simpleName, "Map HTML $htmlText")

            onUiThread {
                try {
                    mapWebView.loadData(htmlText, "text/html; charset=UTF-8", null)
                } catch (e: Throwable) {
                    Log.e(PlacemarkListActivity::class.java.simpleName, "mapWebView.loadData", e)
                    showToast(e)
                }
            }
        }
    }

    private data class Marker(
            val index: Int,
            val id: Long,
            val name: String,
            val collectionName: String,
            val coordinates: Coordinates,
            val distance: String,
            val flagged: Boolean
    )

    private fun searchPoi(placemarksConsumer: (Collection<PlacemarkSearchResult>) -> Unit) {
        // load parameters
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val latitude = intent.getFloatExtra(ARG_LATITUDE, preferences.getFloat(ARG_LATITUDE, Float.NaN))
        val longitude = intent.getFloatExtra(ARG_LONGITUDE, preferences.getFloat(ARG_LONGITUDE, Float.NaN))
        searchCoordinate = Coordinates(latitude, longitude)
        range = intent.getIntExtra(ARG_RANGE, preferences.getInt(ARG_RANGE, 0))
        val nameFilter: String = intent.getStringExtra(ARG_NAME_FILTER) ?: preferences.getString(ARG_NAME_FILTER, null)
        ?: ""
        val favourite = intent.getBooleanExtra(ARG_FAVOURITE, preferences.getBoolean(ARG_FAVOURITE, false))

        // read collections id or parse from preference
        val collectionIds = intent.getLongArrayExtra(ARG_COLLECTION_IDS)?.toSet()
                ?: preferences.getStringSet(ARG_COLLECTION_IDS, setOf()).map(String::toLong)

        // save parameters in preferences
        preferences.edit()
                .putFloat(ARG_LATITUDE, latitude)
                .putFloat(ARG_LONGITUDE, longitude)
                .putInt(ARG_RANGE, range).putBoolean(ARG_FAVOURITE, favourite)
                .putString(ARG_NAME_FILTER, nameFilter)
                .putStringSet(ARG_COLLECTION_IDS, collectionIds.map(Long::toString).toSet())
                .apply()

        showProgressDialog(getString(R.string.title_placemark_list), null, this) {
            val placemarkDao = PlacemarkDao.instance
            placemarkDao.open()
            try {
                val placemarks = placemarkDao.findAllPlacemarkNear(searchCoordinate,
                        range.toDouble(), collectionIds, nameFilter, favourite)
                Log.d(PlacemarkListActivity::class.java.simpleName, "searchPoi progress placemarks.size()=${placemarks.size}")
                onUiThread { toast(getString(R.string.n_placemarks_found, placemarks.size)) }
                placemarksConsumer(placemarks)

                // set up placemark id list for left/right swipe in placemark detail
                placemarkIdArray = placemarks.map { it.id }.toLongArray()
            } catch (e: Exception) {
                Log.e(PlacemarkCollectionDetailFragment::class.java.simpleName, "searchPoi progress", e)
                onUiThread { longToast(getString(R.string.error_search, e.message)) }
            } finally {
                placemarkDao.close()
            }
        }
    }

    @JavascriptInterface
    fun openPlacemark(placemarkId: Long) {
        if (mTwoPane) {
            val arguments = Bundle()
            arguments.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
            arguments.putLongArray(PlacemarkDetailActivity.ARG_PLACEMARK_LIST_ID, placemarkIdArray)
            fragment = PlacemarkDetailFragment().also { f ->
                f.arguments = arguments
            }
            supportFragmentManager.beginTransaction().replace(R.id.placemarkDetailContainer, fragment).commit()

            // show fab
            fabStar.visibility = View.VISIBLE
            fabMap.visibility = View.VISIBLE
            fabMap.setOnLongClickListener(fragment!!.longClickListener)
        } else {
            val intent = Intent(this, PlacemarkDetailActivity::class.java)
            intent.putExtra(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
            intent.putExtra(PlacemarkDetailActivity.ARG_PLACEMARK_LIST_ID, placemarkIdArray)
            this.startActivity(intent)
        }
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

    inner class SimpleItemRecyclerViewAdapter : RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {

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
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val placemark = placemarks!![position]
            holder.placemark = placemark
            Location.distanceBetween(searchCoordinate.latitude.toDouble(), searchCoordinate.longitude.toDouble(),
                    placemark.coordinates.latitude.toDouble(), placemark.coordinates.longitude.toDouble(),
                    floatArray)
            val distance = floatArray[0]
            // calculate index for arrow
            var arrowIndex = Math.round(floatArray[1] + 45f / 2f)
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
                stringBuilder.append(Integer.toString(distance.toInt())).append(" m")
            } else {
                stringBuilder.append(if (distance < 10000f)
                    decimalFormat.format((distance / 1000f).toDouble())
                else
                    Integer.toString(distance.toInt() / 1000)).append(" ㎞")
            }
            stringBuilder.append(' ')
                    .append(if (distance <= 1F) CENTER else ARROWS[arrowIndex])
                    .append("  ")
                    .append(placemark.name)
            holder.view.text = stringBuilder.toString()
            holder.view.typeface = if (placemark.flagged) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            holder.view.setOnClickListener { openPlacemark(holder.placemark!!.id) }
            holder.view.setOnLongClickListener { view ->
                LocationUtil.openExternalMap(holder.placemark!!, false, view.context)
                true
            }
        }

        override fun getItemCount() = placemarks?.size ?: 0

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val view = view.findViewById(android.R.id.text1) as TextView
            var placemark: PlacemarkSearchResult? = null
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
        private const val PREFEFERNCE_SHOW_MAP = "showMap"
        private const val PERMISSION_SHOW_MAP = 1
        // clockwise arrow
        private val ARROWS = charArrayOf(/*N*/ '\u2191', /*NE*/ '\u2197', /*E*/ '\u2192', /*SE*/ '\u2198', /*S*/ '\u2193', /*SW*/ '\u2199', /*W*/ '\u2190', /*NW*/ '\u2196')
        // white flag
        private val CENTER = '\u2690'
        private val markerColors = arrayOf("white", "orange", "cyan", "red", "yellow", "black", "magenta", "lime", "pink")

        private fun colorFor(value: Int) = markerColors[Math.abs(value) % markerColors.size]

        private fun stringToColorCode(txt: String): String =
                MessageDigest.getInstance("MD5").digest(txt.toByteArray()).let { a ->
                    buildString(6) {
                        for (i in 0..2)
                            append("%02x".format(a[i]))
                    }
                }
    }
}
