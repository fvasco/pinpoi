package io.github.fvasco.pinpoi

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.SeekBar
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Future
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener, LocationListener, AnkoLogger {
    private var selectedPlacemarkCategory: String = ""
    private var selectedPlacemarkCollection: PlacemarkCollection? = null
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var futureSearchAddress: Future<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Util.applicationContext = applicationContext
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // widget
        switchGps.setOnCheckedChangeListener(this)
        switchGps.isEnabled = !locationManager.allProviders.isEmpty()
        if (LocationUtil.geocoder == null) {
            searchAddressButton.visibility = View.GONE
        }

        // setup range seek
        rangeSeek.max = RANGE_MAX_SHIFT
        rangeSeek.setOnSeekBarChangeListener(this)
        onProgressChanged(rangeSeek, rangeSeek.progress, false)

        // restore preference
        val preference = getPreferences(Context.MODE_PRIVATE)
        switchGps.isChecked = preference.getBoolean(PREFEFERNCE_GPS, false)
        latitudeText.setText(preference.getString(PREFEFERNCE_LATITUDE, "50"))
        longitudeText.setText(preference.getString(PREFEFERNCE_LONGITUDE, "10"))
        nameFilterText.setText(preference.getString(PREFEFERNCE_NAME_FILTER, null))
        favouriteCheck.isChecked = preference.getBoolean(PREFEFERNCE_FAVOURITE, false)
        showMapCheck.isChecked = preference.getBoolean(PREFEFERNCE_SHOW_MAP, false)
        rangeSeek.progress = Math.min(preference.getInt(PREFEFERNCE_RANGE, RANGE_MAX_SHIFT), RANGE_MAX_SHIFT)
        setPlacemarkCategory(preference.getString(PREFEFERNCE_CATEGORY, null) ?: "")
        setPlacemarkCollection(preference.getLong(PREFEFERNCE_COLLECTION, 0))

        // load intent parameters for geo scheme (if present)
        intent.data?.let { intentUri ->
            val coordinatePattern = Pattern.compile("([+-]?\\d+\\.\\d+),([+-]?\\d+\\.\\d+)(?:\\D.*)?")
            var matcher = coordinatePattern.matcher(intentUri.getQueryParameter("q") ?: "")
            if (!matcher.matches()) {
                matcher = coordinatePattern.matcher(intentUri.authority)
            }
            if (matcher.matches()) {
                switchGps.isChecked = false
                latitudeText.setText(matcher.group(1))
                longitudeText.setText(matcher.group(2))
            }
        }
    }

    override fun onResume() {
        setUseLocationManagerListener(switchGps.isChecked && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        super.onResume()
    }

    override fun onPause() {
        setUseLocationManagerListener(false)
        super.onPause()
    }

    override fun onStop() {
        getPreferences(Context.MODE_PRIVATE).edit().putBoolean(PREFEFERNCE_GPS, switchGps.isChecked).putString(PREFEFERNCE_LATITUDE, latitudeText.text.toString()).putString(PREFEFERNCE_LONGITUDE, longitudeText.text.toString()).putString(PREFEFERNCE_NAME_FILTER, nameFilterText.text.toString()).putBoolean(PREFEFERNCE_FAVOURITE, favouriteCheck.isChecked).putBoolean(PREFEFERNCE_SHOW_MAP, showMapCheck.isChecked).putInt(PREFEFERNCE_RANGE, rangeSeek.progress).putString(PREFEFERNCE_CATEGORY, selectedPlacemarkCategory).putLong(PREFEFERNCE_COLLECTION, if (selectedPlacemarkCollection == null) 0 else selectedPlacemarkCollection!!.id).apply()
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        // on debug show debug menu
        menu.findItem(R.id.menu_debug).isVisible = BuildConfig.DEBUG
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify restoreBackup parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_placemark_collections -> {
                onManagePlacemarkCollections(null)
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
                // branch gh-pages
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://fvasco.github.io/pinpoi")
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setPlacemarkCategory(placemarkCategory: String) {
        selectedPlacemarkCategory = placemarkCategory
        categoryButton.text = selectedPlacemarkCategory
        if (placemarkCategory != "" && placemarkCategory != selectedPlacemarkCollection?.category) {
            setPlacemarkCollection(null)
        }
    }

    private fun setPlacemarkCollection(placemarkCollectionId: Long) {
        val placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkCollectionDao.open()
        try {
            setPlacemarkCollection(placemarkCollectionDao.findPlacemarkCollectionById(placemarkCollectionId))
        } finally {
            placemarkCollectionDao.close()
        }
    }

    private fun setPlacemarkCollection(placemarkCollection: PlacemarkCollection?) {
        selectedPlacemarkCollection = placemarkCollection
        collectionButton.text = placemarkCollection?.name
    }

    fun openPlacemarkCategoryChooser(view: View) {
        val placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkCollectionDao.open()
        try {
            val categories = placemarkCollectionDao.findAllPlacemarkCollectionCategory()
            AlertDialog.Builder(view.context)
                    .setTitle(getString(R.string.collection))
                    .setItems(arrayOf(getString(R.string.any_filter), *categories.toTypedArray()), { dialog, which ->
                        dialog.dismiss()
                        setPlacemarkCategory(if (which == 0) "" else categories[which - 1])
                    })
                    .show()
        } finally {
            placemarkCollectionDao.close()
        }
    }

    fun openPlacemarkCollectionChooser(view: View) {
        val placemarkCollectionDao = PlacemarkCollectionDao.instance
        placemarkCollectionDao.open()
        try {
            val placemarkCollections = ArrayList<PlacemarkCollection?>()
            val placemarkCollectionNames = ArrayList<String>()

            // skip empty collections
            for (placemarkCollection in if (selectedPlacemarkCategory.isEmpty())
                placemarkCollectionDao.findAllPlacemarkCollection()
            else
                placemarkCollectionDao.findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory)) {
                if (placemarkCollection.poiCount > 0) {
                    placemarkCollections.add(placemarkCollection)
                    placemarkCollectionNames.add(
                            if (selectedPlacemarkCategory == placemarkCollection.category)
                                placemarkCollection.name
                            else
                                placemarkCollection.category + " / " + placemarkCollection.name)
                }
            }
            if (selectedPlacemarkCategory.isEmpty() && placemarkCollections.isEmpty()) {
                onManagePlacemarkCollections(view)
            } else if (placemarkCollections.size == 1) {
                setPlacemarkCollection(placemarkCollections[0])
            } else {
                placemarkCollections.add(0, null)
                placemarkCollectionNames.add(0, getString(R.string.any_filter))
                AlertDialog.Builder(view.context)
                        .setTitle(getString(R.string.collection))
                        .setItems(placemarkCollectionNames.toTypedArray()) { dialog, which ->
                            dialog.dismiss()
                            setPlacemarkCollection(
                                    if (which == 0) null else placemarkCollections[which])
                        }
                        .show()
            }
        } finally {
            placemarkCollectionDao.close()
        }
    }

    fun onSearchAddress(view: View) {
        futureSearchAddress?.cancel(true)
        // no gps open search dialog
        val context = view.context
        val preference = getPreferences(Context.MODE_PRIVATE)

        val editText = EditText(context)
        editText.maxLines = 6
        editText.setText(preference.getString(PREFEFERNCE_ADDRESS, ""))
        editText.selectAll()

        AlertDialog.Builder(context)
                .setMessage(R.string.insert_address)
                .setView(editText)
                .setPositiveButton(R.string.search) { dialog, which ->
                    dialog.dismiss()
                    switchGps.isChecked = false;
                    // clear old coordinates
                    onLocationChanged(null)
                    // search new location;
                    val address = editText.text.toString()
                    preference.edit().putString(PREFEFERNCE_ADDRESS, address).apply()
                    showProgressDialog(address, null, view.context) {
                        val addresses =
                                LocationUtil.geocoder?.getFromLocationName(address, 25)?.filter { it.hasLatitude() && it.hasLongitude() } ?: listOf()
                        onUiThread {
                            chooseAddress(addresses, view.context)
                        }
                    }
                }
                .setNegativeButton(R.string.close, DismissOnClickListener)
                .show()
    }

    private fun chooseAddress(addresses: List<Address>, context: Context) {
        try {
            if (addresses.isEmpty()) {
                toast(getString(R.string.error_no_address_found))
            } else {
                val options = addresses.map { LocationUtil.toString(it) }.toTypedArray()
                AlertDialog.Builder(context).setItems(options) { dialog, which ->
                    dialog.dismiss()
                    val a = addresses[which]
                    onLocationChanged(LocationUtil.newLocation(a.latitude, a.longitude))
                }.show()
            }
        } catch (e: IOException) {
            error("searchAddress", e)
            toast(getString(R.string.error_network))
        }
    }

    fun onSearchPoi(view: View) {
        try {
            val collectionsIds: LongArray
            if (selectedPlacemarkCollection == null) {
                val placemarkCollectionDao = PlacemarkCollectionDao.instance
                placemarkCollectionDao.open()
                try {
                    val collections = if (selectedPlacemarkCategory == "")
                        placemarkCollectionDao.findAllPlacemarkCollection()
                    else
                        placemarkCollectionDao.findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory)
                    collectionsIds = collections.filter { it.poiCount > 0 }.map { it.id }.toLongArray()
                } finally {
                    placemarkCollectionDao.close()
                }
            } else {
                collectionsIds = longArrayOf(selectedPlacemarkCollection!!.id)
            }
            debug { "onSearchPoi selectedPlacemarkCategory=${selectedPlacemarkCategory}, collectionsIds=${collectionsIds}" }
            if (collectionsIds.isEmpty()) {
                toast(getString(R.string.n_placemarks_found, 0))
                onManagePlacemarkCollections(view)
            } else {
                val context = view.context
                val intent = Intent(context, PlacemarkListActivity::class.java).apply {
                    putExtra(PlacemarkListActivity.ARG_LATITUDE, java.lang.Float.parseFloat(latitudeText.text.toString()))
                    putExtra(PlacemarkListActivity.ARG_LONGITUDE, java.lang.Float.parseFloat(longitudeText.text.toString()))
                    putExtra(PlacemarkListActivity.ARG_NAME_FILTER, nameFilterText.text.toString())
                    putExtra(PlacemarkListActivity.ARG_FAVOURITE, favouriteCheck.isChecked)
                    putExtra(PlacemarkListActivity.ARG_SHOW_MAP, showMapCheck.isChecked)
                    putExtra(PlacemarkListActivity.ARG_RANGE, (rangeSeek.progress + RANGE_MIN) * 1000)
                    putExtra(PlacemarkListActivity.ARG_COLLECTION_IDS, collectionsIds)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            error("onSearchPoi", e)
            toast(R.string.validation_error)
        }

    }

    fun onManagePlacemarkCollections(view: View?) {
        startActivity(Intent(this, PlacemarkCollectionListActivity::class.java))
    }

    private fun showCreateBackupConfirm() {
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.action_create_backup))
                .setMessage(getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.absolutePath))
                .setPositiveButton(R.string.yes) { dialogInterface, i ->
                    dialogInterface.dismiss()
                    createBackup()
                }
                .setNegativeButton(R.string.no, DismissOnClickListener)
                .show()
    }

    private fun createBackup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            showProgressDialog(
                    getString(R.string.action_create_backup),
                    getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.absolutePath), this) {
                try {
                    val backupManager = BackupManager(PlacemarkCollectionDao.instance, PlacemarkDao.instance)
                    backupManager.create(BackupManager.DEFAULT_BACKUP_FILE)
                } catch (e: Exception) {
                    Log.w(MainActivity::class.java.simpleName, "create backup failed", e)
                    showToast(e)
                }
            }
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_CREATE_BACKUP)
        }
    }

    private fun showRestoreBackupConfirm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            openFileChooser(BackupManager.DEFAULT_BACKUP_FILE, this) { file ->
                AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.action_restore_backup))
                        .setMessage(getString(R.string.backup_file, file.absolutePath))
                        .setPositiveButton(R.string.yes) { dialogInterface, i ->
                            dialogInterface.dismiss()
                            restoreBackup(file)
                        }
                        .setNegativeButton(R.string.no, DismissOnClickListener)
                        .show()
            }
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_RESTORE_BACKUP)
        }
    }

    private fun restoreBackup(file: File) {
        showProgressDialog(getString(R.string.action_restore_backup),
                getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.absolutePath), this) {
            try {
                val backupManager = BackupManager(PlacemarkCollectionDao.instance, PlacemarkDao.instance)
                backupManager.restore(file)
                onUiThread { setPlacemarkCollection(null) }
            } catch (e: Exception) {
                Log.w(MainActivity::class.java.simpleName, "restore backup failed", e)
                showToast(e)
            }
        }
    }

    private fun debugImportCollection() {
        if (!BuildConfig.DEBUG) throw AssertionError()

        val uri = Uri.Builder().scheme("http").authority("my.poi.server").appendEncodedPath("/dir/subdir/poisource.ov2").appendQueryParameter("q", "customValue").build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    /**
     * Init search range label
     */
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        rangeLabel.text = getString(R.string.search_range, progress + RANGE_MIN)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (switchGps === buttonView) {
            setUseLocationManagerListener(isChecked)
        }
    }

    private fun setUseLocationManagerListener(on: Boolean) {
        var locationManagerListenerEnabled = false
        try {
            if (on) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    onLocationChanged(null)
                    for (provider in locationManager.allProviders) {
                        Log.i(MainActivity::class.java.simpleName, "provider " + provider)
                        // search updated location
                        onLocationChanged(locationManager.getLastKnownLocation(provider))
                        locationManager.requestLocationUpdates(provider, LOCATION_TIME_ACCURACY.toLong(), LOCATION_RANGE_ACCURACY.toFloat(), this)
                        locationManagerListenerEnabled = true
                    }
                } else {
                    // request permission
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_GPS_ON)
                }
            } else {
                locationManager.removeUpdates(this)
            }
        } catch (se: SecurityException) {
            showToast(se)
        }

        Log.i(MainActivity::class.java.simpleName, "setUseLocationManagerListener.status " + locationManagerListenerEnabled)
        latitudeText.isEnabled = !locationManagerListenerEnabled
        longitudeText.isEnabled = !locationManagerListenerEnabled
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        val granted = grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            PERMISSION_GPS_ON -> {
                switchGps.isChecked = granted
                setUseLocationManagerListener(granted)
            }
            PERMISSION_CREATE_BACKUP -> if (granted) createBackup()
            PERMISSION_RESTORE_BACKUP -> if (granted) showRestoreBackupConfirm()
        }
    }

    /**
     * Manage location update

     * @param location new location
     */
    @SuppressLint("SetTextI18n")
    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            lastLocation = null
            latitudeText.text = null
            longitudeText.text = null
        } else {
            val minTime = System.currentTimeMillis() - LOCATION_TIME_ACCURACY
            if (location.time >= minTime && (location.accuracy <= LOCATION_RANGE_ACCURACY
                    || lastLocation == null || lastLocation!!.time <= minTime
                    || lastLocation!!.accuracy < location.accuracy)) {
                lastLocation = location
                latitudeText.setText(java.lang.Double.toString(location.latitude))
                longitudeText.setText(java.lang.Double.toString(location.longitude))
            }
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }

    companion object {

        private const val LOCATION_RANGE_ACCURACY = 100
        private const val LOCATION_TIME_ACCURACY = 2 * 60000
        private const val PREFEFERNCE_LATITUDE = "latitude"
        private const val PREFEFERNCE_LONGITUDE = "longitude"
        private const val PREFEFERNCE_NAME_FILTER = "nameFilter"
        private const val PREFEFERNCE_RANGE = "range"
        private const val PREFEFERNCE_FAVOURITE = "favourite"
        private const val PREFEFERNCE_CATEGORY = "category"
        private const val PREFEFERNCE_COLLECTION = "collection"
        private const val PREFEFERNCE_GPS = "gps"
        private const val PREFEFERNCE_ADDRESS = "address"
        private const val PREFEFERNCE_SHOW_MAP = "showMap"
        private const val PERMISSION_GPS_ON = 1
        private const val PERMISSION_CREATE_BACKUP = 10
        private const val PERMISSION_RESTORE_BACKUP = 11
        /**
         * Smallest searchable range
         */
        private const val RANGE_MIN = 5
        /**
         * Greatest [.rangeSeek] value,
         * searchable range value is this plus [.RANGE_MIN]
         */
        private const val RANGE_MAX_SHIFT = 195
    }
}
