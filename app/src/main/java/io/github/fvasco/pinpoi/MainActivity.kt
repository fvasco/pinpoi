package io.github.fvasco.pinpoi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.ads.AdSize
import com.google.openlocationcode.OpenLocationCode
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.dao.use
import io.github.fvasco.pinpoi.databinding.ActivityMainBinding
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.BackupManager
import io.github.fvasco.pinpoi.util.DEBUG
import io.github.fvasco.pinpoi.util.DismissOnClickListener
import io.github.fvasco.pinpoi.util.LocationUtil
import io.github.fvasco.pinpoi.util.initAdMob
import io.github.fvasco.pinpoi.util.setUpDebugDatabase
import io.github.fvasco.pinpoi.util.showLongToast
import io.github.fvasco.pinpoi.util.showProgressDialog
import io.github.fvasco.pinpoi.util.showToast
import io.github.fvasco.pinpoi.util.tryDismiss
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Future
import java.util.regex.Pattern
import kotlin.math.min

class MainActivity
    : AppCompatActivity(), SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener,
    LocationListener {

    private lateinit var binding: ActivityMainBinding
    private var selectedPlacemarkCategory: String = ""
    private var selectedPlacemarkCollection: PlacemarkCollection? = null
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var futureSearchAddress: Future<*>? = null
    private lateinit var locationUtil: LocationUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationUtil = LocationUtil(applicationContext)

        // widget
        binding.switchGps.isEnabled = locationManager.allProviders.isNotEmpty()
        if (locationUtil.geocoder == null) {
            binding.searchAddressButton.visibility = View.GONE
        }

        // setup range seek
        binding.rangeSeek.max = RANGE_MAX_SHIFT
        binding.rangeSeek.setOnSeekBarChangeListener(this)
        onProgressChanged(binding.rangeSeek, binding.rangeSeek.progress, false)

        // restore preference
        val preference = getPreferences(Context.MODE_PRIVATE)
        binding.switchGps.isChecked = preference.getBoolean(PREFERENCE_GPS, false)
        binding.latitudeText.setText(preference.getString(PREFERENCE_LATITUDE, "50"))
        binding.longitudeText.setText(preference.getString(PREFERENCE_LONGITUDE, "10"))
        binding.nameFilterText.setText(preference.getString(PREFERENCE_NAME_FILTER, null))
        binding.favouriteCheck.isChecked = preference.getBoolean(PREFERENCE_FAVOURITE, false)
        binding.showMapCheck.isChecked = preference.getBoolean(PREFERENCE_SHOW_MAP, false)
        binding.rangeSeek.progress =
            min(preference.getInt(PREFERENCE_RANGE, RANGE_MAX_SHIFT), RANGE_MAX_SHIFT)
        setPlacemarkCategory(preference.getString(PREFERENCE_CATEGORY, "") ?: "")
        setPlacemarkCollection(preference.getLong(PREFERENCE_COLLECTION, 0))

        // load intent parameters for geo scheme (if present)
        intent.data
            ?.takeIf { it.scheme == "geo" }
            ?.let { intentUri ->
                Log.d(MainActivity::class.java.simpleName, "Intent data $intentUri")
                val coordinatePattern =
                    Pattern.compile("([+-]?\\d+\\.\\d+)[, ]([+-]?\\d+\\.\\d+)(?:\\D.*)?")
                val paramQ: String? =
                    if (intentUri.isHierarchical) {
                        intentUri
                    } else {
                        Uri.parse(intentUri.toString().replaceFirst("geo:", "geo://"))
                    }
                        .getQueryParameter("q")

                val matcher =
                    paramQ?.let { coordinatePattern.matcher(it) }
                        ?: intentUri.host?.let { coordinatePattern.matcher(it) }
                        ?: Pattern.compile("""geo:(?://)?([+-]?\d+\.\d+),([+-]?\d+\.\d+)(?:\D.*)?""")
                            .matcher(intentUri.toString())

                if (matcher.matches()) {
                    binding.switchGps.isChecked = false
                    binding.latitudeText.setText(matcher.group(1))
                    binding.longitudeText.setText(matcher.group(2))
                } else if (paramQ != null)
                    openSearchAddress(this, paramQ)
            }
        initAdMob(
            adViewContainer = binding.adViewContainer,
            adUnitId = "ca-app-pub-9366320490821807/9506426799",
            adSize = AdSize.LARGE_BANNER
        )
    }

    override fun onResume() {
        binding.switchGps.setOnCheckedChangeListener(this)
        setUseLocationManagerListener(
            binding.switchGps.isChecked && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
                    == PackageManager.PERMISSION_GRANTED
        )
        super.onResume()
    }

    override fun onPause() {
        setUseLocationManagerListener(false)
        getPreferences(Context.MODE_PRIVATE).edit {
            putBoolean(PREFERENCE_GPS, binding.switchGps.isChecked)
            putString(PREFERENCE_LATITUDE, binding.latitudeText.text.toString())
            putString(PREFERENCE_LONGITUDE, binding.longitudeText.text.toString())
            putString(PREFERENCE_NAME_FILTER, binding.nameFilterText.text.toString())
            putBoolean(PREFERENCE_FAVOURITE, binding.favouriteCheck.isChecked)
            putBoolean(PREFERENCE_SHOW_MAP, binding.showMapCheck.isChecked)
            putInt(PREFERENCE_RANGE, binding.rangeSeek.progress)
            putString(PREFERENCE_CATEGORY, selectedPlacemarkCategory)
            putLong(PREFERENCE_COLLECTION, selectedPlacemarkCollection?.id ?: 0)
        }
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        // on debug show debug menu
        menu.findItem(R.id.menu_debug).isVisible = DEBUG
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify restoreBackup parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_placemark_collections -> {
                onManagePlacemarkCollections()
                return true
            }

            R.id.create_backup -> {
                showCreateBackupConfirm()
                return true
            }

            R.id.restore_backup -> {
                showRestoreBackupConfirm()
                return true
            }

            R.id.debug_create_db -> {
                setUpDebugDatabase(this)
                return true
            }

            R.id.debug_import_collection -> {
                debugImportCollection()
                return true
            }

            R.id.action_web_site -> {
                // see branch gh-pages
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://fvasco.github.io/pinpoi")
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setPlacemarkCategory(placemarkCategory: String) {
        selectedPlacemarkCategory = placemarkCategory
        binding.categoryButton.text = selectedPlacemarkCategory
        if (placemarkCategory != "" && placemarkCategory != selectedPlacemarkCollection?.category) {
            setPlacemarkCollection(null)
        }
    }

    private fun setPlacemarkCollection(placemarkCollectionId: Long) {
        PlacemarkCollectionDao(applicationContext).use { placemarkCollectionDao ->
            setPlacemarkCollection(
                placemarkCollectionDao.findPlacemarkCollectionById(
                    placemarkCollectionId
                )
            )
        }
    }

    private fun setPlacemarkCollection(placemarkCollection: PlacemarkCollection?) {
        selectedPlacemarkCollection = placemarkCollection
        binding.collectionButton.text = placemarkCollection?.name
    }

    fun openPlacemarkCategoryChooser(view: View) {
        PlacemarkCollectionDao(applicationContext).use { placemarkCollectionDao ->
            val categories = placemarkCollectionDao.findAllPlacemarkCollectionCategory()
            AlertDialog.Builder(view.context)
                .setTitle(getString(R.string.category))
                .setItems(
                    arrayOf(
                        getString(R.string.any_filter),
                        *categories.toTypedArray()
                    )
                ) { dialog, which ->
                    dialog.tryDismiss()
                    setPlacemarkCategory(if (which == 0) "" else categories[which - 1])
                }
                .show()
        }
    }

    fun openPlacemarkCollectionChooser(view: View) {
        PlacemarkCollectionDao(applicationContext).use { placemarkCollectionDao ->
            val placemarkCollections = ArrayList<PlacemarkCollection?>()
            val placemarkCollectionNames = ArrayList<String>()

            // skip empty collections
            for (placemarkCollection in if (selectedPlacemarkCategory.isEmpty())
                placemarkCollectionDao.findAllPlacemarkCollection()
            else
                placemarkCollectionDao.findAllPlacemarkCollectionInCategory(
                    selectedPlacemarkCategory
                )) {
                if (placemarkCollection.poiCount > 0) {
                    placemarkCollections.add(placemarkCollection)
                    placemarkCollectionNames.add(
                        if (selectedPlacemarkCategory == placemarkCollection.category)
                            placemarkCollection.name
                        else
                            placemarkCollection.category + " / " + placemarkCollection.name
                    )
                }
            }
            if (selectedPlacemarkCategory.isEmpty() && placemarkCollections.isEmpty()) {
                onManagePlacemarkCollections()
            } else if (placemarkCollections.size == 1) {
                setPlacemarkCollection(placemarkCollections[0])
            } else {
                placemarkCollections.add(0, null)
                placemarkCollectionNames.add(0, getString(R.string.any_filter))
                AlertDialog.Builder(view.context)
                    .setTitle(getString(R.string.collection))
                    .setItems(placemarkCollectionNames.toTypedArray()) { dialog, which ->
                        dialog.tryDismiss()
                        setPlacemarkCollection(
                            if (which == 0) null else placemarkCollections[which]
                        )
                    }
                    .show()
            }
        }
    }

    fun onSearchAddress(view: View) {
        val preference = getPreferences(Context.MODE_PRIVATE)
        openSearchAddress(view.context, preference.getString(PREFERENCE_ADDRESS, ""))
    }

    private fun openSearchAddress(context: Context, suggestedText: String?) {
        futureSearchAddress?.cancel(true)
        // no gps open search dialog
        val preference = getPreferences(Context.MODE_PRIVATE)

        val editText = EditText(context)
        editText.maxLines = 6
        editText.inputType = InputType.TYPE_CLASS_TEXT
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        editText.setText(suggestedText)
        editText.selectAll()

        var pasteButton: Button? = null
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager?.hasPrimaryClip() == true) {
            pasteButton = Button(this)
            pasteButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            pasteButton.text = "\uD83D\uDCCB"
            pasteButton.setOnClickListener {
                val text = clipboardManager.primaryClip?.getItemAt(0)?.text
                editText.setText(text ?: "")
            }
        }

        val dialogView = LinearLayout(context)
        with(dialogView) {
            //gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            orientation = LinearLayout.HORIZONTAL
            val layoutPadding = 20
            setPadding(layoutPadding, layoutPadding, layoutPadding, layoutPadding)
            addView(editText)
            if (pasteButton != null) addView(pasteButton)
        }

        AlertDialog.Builder(context)
            .setMessage(R.string.insert_address)
            .setView(dialogView)
            .setPositiveButton(R.string.search) { dialog, _ ->
                dialog.tryDismiss()
                binding.switchGps.isChecked = false
                // clear old coordinates
                clearLocation()
                // search new location;
                val address = editText.text.toString()
                if (address.isNotBlank()) {
                    preference.edit { putString(PREFERENCE_ADDRESS, address) }
                    // check if is a coordinate text, WGS84 or geo uri
                    val coordinateMatcherResult =
                        """\s*(?:geo:)?([+-]?\d+\.\d+)(?:,|,?\s+)([+-]?\d+\.\d+)(?:\?.*)?\s*""".toRegex()
                            .matchEntire(address)
                    if (coordinateMatcherResult == null) {
                        try {
                            // check OLC
                            var olc = OpenLocationCode(address)
                            // if required try to recover location
                            runCatching {
                                olc = olc.recover(
                                    binding.latitudeText.text.toString().toDouble(),
                                    binding.longitudeText.text.toString().toDouble()
                                )
                                // ignore error
                            }
                            val codeArea = olc.decode()
                            onLocationChanged(
                                LocationUtil.newLocation(
                                    latitude = codeArea.centerLatitude,
                                    longitude = codeArea.centerLongitude
                                )
                            )
                        } catch (e: Exception) {
                            showProgressDialog(address, context) {
                                val addresses =
                                    locationUtil.geocoder?.getFromLocationName(address, 25)
                                        ?.filter { it.hasLatitude() && it.hasLongitude() }
                                        ?: listOf()
                                runOnUiThread {
                                    chooseAddress(addresses, context)
                                }
                            }
                        }
                    } else {
                        // parse coordinate
                        binding.latitudeText.setText(coordinateMatcherResult.groupValues[1])
                        binding.longitudeText.setText(coordinateMatcherResult.groupValues[2])
                    }
                }
            }
            .setNegativeButton(R.string.close, DismissOnClickListener)
            .show()

        editText.postDelayed({
            editText.requestFocus()
            with(this@MainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager) {
                showSoftInput(editText, 0)
            }
        }, 300)
    }

    private fun chooseAddress(addresses: List<Address>, context: Context) {
        try {
            if (addresses.isEmpty()) {
                showLongToast(getString(R.string.error_no_address_found), context)
            } else {
                val options = addresses.map { LocationUtil.toString(it) }.toTypedArray()
                AlertDialog.Builder(context).setItems(options) { dialog, which ->
                    dialog.tryDismiss()
                    val a = addresses[which]
                    onLocationChanged(LocationUtil.newLocation(a.latitude, a.longitude))
                }.show()
            }
        } catch (e: IOException) {
            Log.e(MainActivity::class.java.simpleName, "searchAddress", e)
            showLongToast(getString(R.string.error_network), context)
        }
    }

    fun onSearchPoi(view: View) {
        try {
            val collectionsIds: LongArray =
                if (selectedPlacemarkCollection == null) {
                    PlacemarkCollectionDao(applicationContext).use { placemarkCollectionDao ->
                        val collections = if (selectedPlacemarkCategory.isEmpty())
                            placemarkCollectionDao.findAllPlacemarkCollection()
                        else
                            placemarkCollectionDao.findAllPlacemarkCollectionInCategory(
                                selectedPlacemarkCategory
                            )
                        collections.filter { it.poiCount > 0 }.map { it.id }.toLongArray()
                    }
                } else {
                    longArrayOf(selectedPlacemarkCollection!!.id)
                }
            Log.d(
                MainActivity::class.java.simpleName,
                "onSearchPoi selectedPlacemarkCategory=$selectedPlacemarkCategory, collectionsIds=${collectionsIds.contentToString()}"
            )
            if (collectionsIds.isEmpty()) {
                showLongToast(getString(R.string.n_placemarks_found, 0), view.context)
                onManagePlacemarkCollections()
            } else {
                val context = view.context
                val intent = Intent(context, PlacemarkListActivity::class.java).apply {
                    try {
                        putExtra(
                            PlacemarkListActivity.ARG_LATITUDE,
                            binding.latitudeText.text.toString().replace(',', '.').toFloat()
                        )
                    } catch (e: Exception) {
                        binding.latitudeText.requestFocus()
                        throw e
                    }
                    try {
                        putExtra(
                            PlacemarkListActivity.ARG_LONGITUDE,
                            binding.longitudeText.text.toString().replace(',', '.').toFloat()
                        )
                    } catch (e: Exception) {
                        binding.longitudeText.requestFocus()
                        throw e
                    }
                    putExtra(
                        PlacemarkListActivity.ARG_NAME_FILTER,
                        binding.nameFilterText.text.toString()
                    )
                    putExtra(PlacemarkListActivity.ARG_FAVOURITE, binding.favouriteCheck.isChecked)
                    putExtra(PlacemarkListActivity.ARG_SHOW_MAP, binding.showMapCheck.isChecked)
                    putExtra(
                        PlacemarkListActivity.ARG_RANGE,
                        (binding.rangeSeek.progress + RANGE_MIN) * 1000
                    )
                    putExtra(PlacemarkListActivity.ARG_COLLECTION_IDS, collectionsIds)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            showLongToast(R.string.validation_error, view.context)
            Log.e(MainActivity::class.java.simpleName, "onSearchPoi", e)
        }

    }

    fun onManagePlacemarkCollections() {
        startActivity(Intent(this, PlacemarkCollectionListActivity::class.java))
    }

    private fun showCreateBackupConfirm() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "pinpoi.backup")
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivityForResult(intent, BACKUP_CREATE_RESULT_ID)
    }

    private fun createBackup(outputStream: OutputStream) {
        showProgressDialog(getString(R.string.action_create_backup), this) {
            try {
                val backupManager =
                    BackupManager(
                        PlacemarkCollectionDao(applicationContext),
                        PlacemarkDao(applicationContext)
                    )
                backupManager.create(outputStream)
            } catch (e: Exception) {
                Log.w(MainActivity::class.java.simpleName, "create backup failed", e)
                showToast(e)
            }
        }
    }

    private fun showRestoreBackupConfirm() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, BACKUP_RESTORE_RESULT_ID)
    }

    private fun restoreBackup(inputStream: InputStream) {
        showProgressDialog(getString(R.string.action_restore_backup), this) {
            try {
                val backupManager =
                    BackupManager(
                        PlacemarkCollectionDao(applicationContext),
                        PlacemarkDao(applicationContext)
                    )
                backupManager.restore(inputStream)
                runOnUiThread { setPlacemarkCollection(null) }
            } catch (e: Exception) {
                Log.w(MainActivity::class.java.simpleName, "restore backup failed", e)
                showToast(e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == BACKUP_CREATE_RESULT_ID) {
            val uri = data?.data ?: return
            val outputStream = contentResolver?.openOutputStream(uri) ?: return
            createBackup(outputStream)
        }

        if (requestCode == BACKUP_RESTORE_RESULT_ID) {
            val uri = data?.data ?: return
            val outputStream = contentResolver?.openInputStream(uri) ?: return
            restoreBackup(outputStream)
        }
    }

    private fun debugImportCollection() {
        if (!DEBUG) throw AssertionError()

        val uri = Uri.Builder().scheme("http").authority("my.poi.server")
            .appendEncodedPath("/dir/subdir/poisource.ov2")
            .appendQueryParameter("q", "customValue").build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    /**
     * Init search range label
     */
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        binding.rangeLabel.text = getString(R.string.search_range, progress + RANGE_MIN)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (binding.switchGps === buttonView) {
            setUseLocationManagerListener(isChecked)
        }
    }

    private fun setUseLocationManagerListener(on: Boolean) {
        var locationManagerListenerEnabled = false
        try {
            if (on) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    clearLocation()
                    for (provider in locationManager.allProviders) {
                        Log.i(MainActivity::class.java.simpleName, "provider $provider")
                        // search updated location
                        locationManager.getLastKnownLocation(provider)?.let(::onLocationChanged)
                        locationManager.requestLocationUpdates(
                            provider,
                            LOCATION_TIME_ACCURACY.toLong(),
                            LOCATION_RANGE_ACCURACY.toFloat(),
                            this
                        )
                        locationManagerListenerEnabled = true
                    }
                } else {
                    // request permission
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_GPS_ON
                    )
                }
            } else {
                locationManager.removeUpdates(this)
            }
        } catch (se: SecurityException) {
            showToast(se)
        }

        Log.i(
            MainActivity::class.java.simpleName,
            "setUseLocationManagerListener.status $locationManagerListenerEnabled"
        )
        binding.latitudeText.isEnabled = !locationManagerListenerEnabled
        binding.longitudeText.isEnabled = !locationManagerListenerEnabled
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            PERMISSION_GPS_ON -> {
                binding.switchGps.isChecked = granted
                setUseLocationManagerListener(granted)
            }
        }
    }

    /**
     * Update current location
     *
     * @param location new location
     */
    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location) {
        val minTime = System.currentTimeMillis() - LOCATION_TIME_ACCURACY
        if (location.time >= minTime && (location.accuracy <= LOCATION_RANGE_ACCURACY
                    || lastLocation == null || lastLocation!!.time <= minTime
                    || lastLocation!!.accuracy < location.accuracy)
        ) {
            lastLocation = location
            binding.latitudeText.setText(location.latitude.toString())
            binding.longitudeText.setText(location.longitude.toString())
        }
    }

    private fun clearLocation() {
        lastLocation = null
        binding.latitudeText.text = null
        binding.longitudeText.text = null
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    companion object {
        private const val LOCATION_RANGE_ACCURACY = 100
        private const val LOCATION_TIME_ACCURACY = 2 * 60000

        private const val PREFERENCE_LATITUDE = "latitude"
        private const val PREFERENCE_LONGITUDE = "longitude"
        private const val PREFERENCE_NAME_FILTER = "nameFilter"
        private const val PREFERENCE_RANGE = "range"
        private const val PREFERENCE_FAVOURITE = "favourite"
        private const val PREFERENCE_CATEGORY = "category"
        private const val PREFERENCE_COLLECTION = "collection"
        private const val PREFERENCE_GPS = "gps"
        private const val PREFERENCE_ADDRESS = "address"
        private const val PREFERENCE_SHOW_MAP = "showMap"
        private const val PERMISSION_GPS_ON = 1

        private const val BACKUP_CREATE_RESULT_ID = 2
        private const val BACKUP_RESTORE_RESULT_ID = 3

        /**
         * Smallest searchable range
         */
        private const val RANGE_MIN = 5

        /**
         * Greatest `rangeSeek` value,
         * searchable range value is this plus [RANGE_MIN]
         */
        private const val RANGE_MAX_SHIFT = 195
    }
}
