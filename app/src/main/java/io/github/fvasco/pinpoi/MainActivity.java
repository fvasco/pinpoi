package io.github.fvasco.pinpoi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.BackupManager;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Coordinates;
import io.github.fvasco.pinpoi.util.DebugUtil;
import io.github.fvasco.pinpoi.util.DismissOnClickListener;
import io.github.fvasco.pinpoi.util.LocationUtil;
import io.github.fvasco.pinpoi.util.Util;


public class MainActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener, LocationListener {

    private static final int LOCATION_RANGE_ACCURACY = 100;
    private static final int LOCATION_TIME_ACCURACY = 2 * 60_000;
    private static final String PREFEFERNCE_LATITUDE = "latitude";
    private static final String PREFEFERNCE_LONGITUDE = "longitude";
    private static final String PREFEFERNCE_NAME_FILTER = "nameFilter";
    private static final String PREFEFERNCE_RANGE = "range";
    private static final String PREFEFERNCE_FAVOURITE = "favourite";
    private static final String PREFEFERNCE_CATEGORY = "category";
    private static final String PREFEFERNCE_COLLECTION = "collection";
    private static final String PREFEFERNCE_GPS = "gps";
    private static final String PREFEFERNCE_ADDRESS = "address";
    private static final String PREFEFERNCE_SHOW_MAP = "displayMap";
    private static final int PERMISSION_GPS_ON = 1;
    private static final int PERMISSION_CREATE_BACKUP = 10;
    private static final int PERMISSION_RESTORE_BACKUP = 11;
    /**
     * Smallest searchable range
     */
    private static final int RANGE_MIN = 5;
    /**
     * Greatest {@linkplain #rangeSeek} value,
     * searchable range value is this plus {@linkplain #RANGE_MIN}
     */
    private static final int RANGE_MAX_SHIFT = 195;
    private String selectedPlacemarkCategory;
    private PlacemarkCollection selectedPlacemarkCollection;
    private LocationManager locationManager;
    private Button categoryButton;
    private Button collectionButton;
    private SeekBar rangeSeek;
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView nameFilterText;
    private CheckBox favouriteCheck;
    private CheckBox showMapCheck;
    private TextView rangeLabel;
    private Switch switchGps;
    private Geocoder geocoder;
    private Location lastLocation;
    private Future<?> futureSearchAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Util.initApplicationContext(getApplicationContext());
        geocoder = LocationUtil.getGeocoder();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // widget
        categoryButton = (Button) findViewById(R.id.categoryButton);
        collectionButton = (Button) findViewById(R.id.collectionButton);
        rangeLabel = (TextView) findViewById(R.id.rangeLabel);
        rangeSeek = (SeekBar) findViewById(R.id.rangeSeek);
        latitudeText = (TextView) findViewById(R.id.latitudeText);
        longitudeText = (TextView) findViewById(R.id.longitudeText);
        nameFilterText = (TextView) findViewById(R.id.name_filter_text);
        favouriteCheck = (CheckBox) findViewById(R.id.favouriteCheck);
        showMapCheck = (CheckBox) findViewById(R.id.showMapCheck);
        switchGps = (Switch) findViewById(R.id.switchGps);
        switchGps.setOnCheckedChangeListener(this);
        final Button searchAddressButton = (Button) findViewById(R.id.search_address_button);
        if (geocoder == null) {
            searchAddressButton.setVisibility(View.GONE);
        }

        // setup range seek
        rangeSeek.setMax(RANGE_MAX_SHIFT);
        rangeSeek.setOnSeekBarChangeListener(this);
        onProgressChanged(rangeSeek, rangeSeek.getProgress(), false);

        // restore preference
        final SharedPreferences preference = getPreferences(MODE_PRIVATE);
        switchGps.setChecked(preference.getBoolean(PREFEFERNCE_GPS, false));
        latitudeText.setText(preference.getString(PREFEFERNCE_LATITUDE, "0"));
        longitudeText.setText(preference.getString(PREFEFERNCE_LONGITUDE, "0"));
        nameFilterText.setText(preference.getString(PREFEFERNCE_NAME_FILTER, null));
        favouriteCheck.setChecked(preference.getBoolean(PREFEFERNCE_FAVOURITE, false));
        showMapCheck.setChecked(preference.getBoolean(PREFEFERNCE_SHOW_MAP, false));
        rangeSeek.setProgress(Math.min(preference.getInt(PREFEFERNCE_RANGE, RANGE_MAX_SHIFT), RANGE_MAX_SHIFT));
        setPlacemarkCategory(preference.getString(PREFEFERNCE_CATEGORY, null));
        setPlacemarkCollection(preference.getLong(PREFEFERNCE_COLLECTION, 0));

        // load intent parameters for geo scheme
        final Uri intentUri = getIntent().getData();
        if (intentUri != null) {
            Pattern coordinatePattern = Pattern.compile("([+-]?\\d+\\.\\d+),([+-]?\\d+\\.\\d+)(?:\\D.*)?");
            Matcher matcher = coordinatePattern.matcher(intentUri.getQueryParameter("q"));
            if (!matcher.matches()) {
                matcher = coordinatePattern.matcher(intentUri.getAuthority());
            }
            if (matcher.matches()) {
                switchGps.setChecked(false);
                latitudeText.setText(matcher.group(1));
                longitudeText.setText(matcher.group(2));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        setUseLocationManagerListener(switchGps.isChecked());
    }

    @Override
    protected void onPause() {
        getPreferences(MODE_PRIVATE).edit()
                .putBoolean(PREFEFERNCE_GPS, switchGps.isChecked())
                .putString(PREFEFERNCE_LATITUDE, latitudeText.getText().toString())
                .putString(PREFEFERNCE_LONGITUDE, longitudeText.getText().toString())
                .putString(PREFEFERNCE_NAME_FILTER, nameFilterText.getText().toString())
                .putBoolean(PREFEFERNCE_FAVOURITE, favouriteCheck.isChecked())
                .putBoolean(PREFEFERNCE_SHOW_MAP, showMapCheck.isChecked())
                .putInt(PREFEFERNCE_RANGE, rangeSeek.getProgress())
                .putString(PREFEFERNCE_CATEGORY, selectedPlacemarkCategory)
                .putLong(PREFEFERNCE_COLLECTION, selectedPlacemarkCollection == null ? 0 : selectedPlacemarkCollection.getId())
                .apply();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // on debug show debug menu
        menu.findItem(R.id.menu_debug).setVisible(BuildConfig.DEBUG);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify restoreBackup parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_placemark_collections:
                onManagePlacemarkCollections(null);
                return true;
            case R.id.create_backup:
                showCreateBackupConfirm();
                return true;
            case R.id.restore_backup:
                showRestoreBackupConfirm();
                return true;
            case R.id.debug_create_db:
                DebugUtil.setUpDebugDatabase(this);
                return true;
            case R.id.debug_import_collection:
                debugImportCollection();
                return true;
            case R.id.action_web_site:
                // branch gh-pages
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://fvasco.github.io/pinpoi"));
                startActivity(i);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setPlacemarkCategory(String placemarkCategory) {
        if (Util.isEmpty(placemarkCategory)) {
            placemarkCategory = null;
        }
        selectedPlacemarkCategory = placemarkCategory;
        categoryButton.setText(placemarkCategory);
        if (placemarkCategory != null
                && selectedPlacemarkCollection != null && !placemarkCategory.equals(selectedPlacemarkCollection.getCategory())) {
            setPlacemarkCollection(null);
        }
    }

    private void setPlacemarkCollection(final long placemarkCollectionId) {
        final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();
        try {
            setPlacemarkCollection(placemarkCollectionDao.findPlacemarkCollectionById(placemarkCollectionId));
        } finally {
            placemarkCollectionDao.close();
        }
    }

    private void setPlacemarkCollection(PlacemarkCollection placemarkCollection) {
        selectedPlacemarkCollection = placemarkCollection;
        collectionButton.setText(placemarkCollection == null ? null : placemarkCollection.getName());
    }

    public void openPlacemarkCategoryChooser(View view) {
        final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance().open();
        try {
            final List<String> categories = collectionDao.findAllPlacemarkCollectionCategory();
            categories.add(0, getString(R.string.any_filter));
            new AlertDialog.Builder(view.getContext())
                    .setTitle(getString(R.string.collection))
                    .setItems(categories.toArray(new String[categories.size()]),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    setPlacemarkCategory(which == 0 ? null : categories.get(which));
                                }
                            }).show();
        } finally {
            collectionDao.close();
        }
    }

    public void openPlacemarkCollectionChooser(View view) {
        final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance().open();
        try {
            final List<PlacemarkCollection> placemarkCollections = selectedPlacemarkCategory == null
                    ? collectionDao.findAllPlacemarkCollection()
                    : collectionDao.findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory);
            final List<String> placemarkCollectionNames = new ArrayList<>(placemarkCollections.size());
            // skip empty collections
            for (final Iterator<PlacemarkCollection> iterator = placemarkCollections.iterator(); iterator.hasNext(); ) {
                final PlacemarkCollection placemarkCollection = iterator.next();
                if (placemarkCollection.getPoiCount() == 0) {
                    iterator.remove();
                } else {
                    placemarkCollectionNames.add(
                            selectedPlacemarkCategory != null || Util.isEmpty(placemarkCollection.getCategory())
                                    ? placemarkCollection.getName()
                                    : placemarkCollection.getCategory() + " / " + placemarkCollection.getName());
                }
            }
            if (selectedPlacemarkCategory == null && placemarkCollections.isEmpty()) {
                onManagePlacemarkCollections(view);
            } else if (placemarkCollections.size() == 1) {
                setPlacemarkCollection(placemarkCollections.get(0));
            } else {
                placemarkCollections.add(0, null);
                placemarkCollectionNames.add(0, getString(R.string.any_filter));
                new AlertDialog.Builder(view.getContext())
                        .setTitle(getString(R.string.collection))
                        .setItems(placemarkCollectionNames.toArray(new String[placemarkCollectionNames.size()]),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        setPlacemarkCollection(
                                                which == 0 ? null : placemarkCollections.get(which));
                                    }
                                }).show();
            }
        } finally {
            collectionDao.close();
        }
    }

    public void onSearchAddress(final View view) {
        if (futureSearchAddress != null) {
            futureSearchAddress.cancel(true);
        }
        if (switchGps.isChecked()) {
            // if gps on toast address
            futureSearchAddress = LocationUtil.getAddressStringAsync(new Coordinates(Float.parseFloat(latitudeText.getText().toString()),
                    Float.parseFloat(longitudeText.getText().toString())), new Consumer<String>() {
                @Override
                public void accept(String address) {
                    if (address != null) {
                        Util.showToast(address, Toast.LENGTH_LONG);
                    }
                }
            });
        } else {
            // no gps open search dialog
            final Context context = view.getContext();
            final SharedPreferences preference = getPreferences(MODE_PRIVATE);

            final EditText editText = new EditText(context);
            editText.setMaxLines(6);
            editText.setText(preference.getString(PREFEFERNCE_ADDRESS, ""));
            editText.selectAll();

            new AlertDialog.Builder(context)
                    .setMessage(R.string.insert_address)
                    .setView(editText)
                    .setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                // clear old coordinates
                                onLocationChanged(null);
                                // search new location;
                                final String address = editText.getText().toString();
                                preference.edit().putString(PREFEFERNCE_ADDRESS, address).apply();
                                searchAddress(address, view.getContext());
                            } finally {
                                dialog.dismiss();
                            }
                        }
                    })
                    .setNegativeButton(R.string.close, DismissOnClickListener.INSTANCE)
                    .show();
        }
    }

    private void searchAddress(final String searchAddress, Context context) {
        try {
            final List<Address> addresses = geocoder.getFromLocationName(searchAddress, 15);
            for (final Iterator<Address> iterator = addresses.iterator(); iterator.hasNext(); ) {
                final Address a = iterator.next();
                if (!a.hasLatitude() || !a.hasLongitude()) {
                    iterator.remove();
                }
            }
            if (Util.isEmpty(addresses)) {
                Util.showToast(getString(R.string.error_no_address_found), Toast.LENGTH_LONG);
            } else {
                final String[] options = new String[addresses.size()];
                for (int i = options.length - 1; i >= 0; --i) {
                    final Address a = addresses.get(i);
                    options[i] = LocationUtil.toString(a);
                }
                new AlertDialog.Builder(context)
                        .setItems(options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                final Address a = addresses.get(which);
                                onLocationChanged(LocationUtil.newLocation(a.getLatitude(), a.getLongitude()));
                            }
                        }).show();
            }
        } catch (IOException e) {
            Log.e(MainActivity.class.getSimpleName(), "searchAddress", e);
            Util.showToast(getString(R.string.error_network), Toast.LENGTH_LONG);
        }
    }

    public void onSearchPoi(View view) {
        try {
            long[] collectionsIds;
            if (selectedPlacemarkCollection == null) {
                final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();
                try {
                    final List<PlacemarkCollection> collections = selectedPlacemarkCategory == null
                            ? placemarkCollectionDao.findAllPlacemarkCollection()
                            : placemarkCollectionDao.findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory);
                    collectionsIds = new long[collections.size()];
                    int i = 0;
                    for (final PlacemarkCollection placemarkCollection : collections) {
                        collectionsIds[i] = placemarkCollection.getId();
                        ++i;
                    }
                } finally {
                    placemarkCollectionDao.close();
                }
            } else {
                collectionsIds = new long[]{selectedPlacemarkCollection.getId()};
            }
            if (collectionsIds.length == 0) {
                Util.showToast(getString(R.string.n_placemarks_found, 0), Toast.LENGTH_LONG);
            } else {
                Context context = view.getContext();
                final Intent intent = new Intent(context, PlacemarkListActivity.class);
                intent.putExtra(PlacemarkListActivity.ARG_LATITUDE, Float.parseFloat(latitudeText.getText().toString()));
                intent.putExtra(PlacemarkListActivity.ARG_LONGITUDE, Float.parseFloat(longitudeText.getText().toString()));
                intent.putExtra(PlacemarkListActivity.ARG_NAME_FILTER, nameFilterText.getText().toString());
                intent.putExtra(PlacemarkListActivity.ARG_FAVOURITE, favouriteCheck.isChecked());
                intent.putExtra(PlacemarkListActivity.ARG_SHOW_MAP, showMapCheck.isChecked());
                intent.putExtra(PlacemarkListActivity.ARG_RANGE, (rangeSeek.getProgress() + RANGE_MIN) * 1000);
                intent.putExtra(PlacemarkListActivity.ARG_COLLECTION_IDS, collectionsIds);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(MainActivity.class.getSimpleName(), "onSearchPoi", e);
            Toast.makeText(MainActivity.this, R.string.validation_error, Toast.LENGTH_SHORT).show();
        }
    }

    public void onManagePlacemarkCollections(View view) {
        startActivity(new Intent(this, PlacemarkCollectionListActivity.class));
    }

    private void showCreateBackupConfirm() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.action_create_backup))
                .setMessage(getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.getAbsolutePath()))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        createBackup();
                    }
                })
                .setNegativeButton(R.string.no, DismissOnClickListener.INSTANCE)
                .show();
    }

    private void createBackup() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Util.showProgressDialog(
                    getString(R.string.action_create_backup),
                    getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.getAbsolutePath()),
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                final BackupManager backupManager = new BackupManager(PlacemarkCollectionDao.getInstance(), PlacemarkDao.getInstance());
                                backupManager.create(BackupManager.DEFAULT_BACKUP_FILE);
                            } catch (Exception e) {
                                Log.w(MainActivity.class.getSimpleName(), "create backup failed", e);
                                Util.showToast(e);
                            }
                        }
                    }, this);
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CREATE_BACKUP);
        }
    }

    private void showRestoreBackupConfirm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            Util.openFileChooser(BackupManager.DEFAULT_BACKUP_FILE,
                    new Consumer<File>() {
                        @Override
                        public void accept(final File file) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle(getString(R.string.action_restore_backup))
                                    .setMessage(getString(R.string.backup_file, file.getAbsolutePath()))
                                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            dialogInterface.dismiss();
                                            restoreBackup(file);
                                        }
                                    })
                                    .setNegativeButton(R.string.no, DismissOnClickListener.INSTANCE)
                                    .show();
                        }
                    }, this);
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_RESTORE_BACKUP);
        }
    }

    private void restoreBackup(@NonNull final File file) {
        Util.showProgressDialog(getString(R.string.action_restore_backup),
                getString(R.string.backup_file, BackupManager.DEFAULT_BACKUP_FILE.getAbsolutePath()),
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final BackupManager backupManager = new BackupManager(PlacemarkCollectionDao.getInstance(), PlacemarkDao.getInstance());
                            backupManager.restore(file);
                            Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                                @Override
                                public void run() {
                                    setPlacemarkCollection(null);
                                }
                            });
                        } catch (Exception e) {
                            Log.w(MainActivity.class.getSimpleName(), "restore backup failed", e);
                            Util.showToast(e);
                        }
                    }
                }, this);
    }

    private void debugImportCollection() {
        if (!BuildConfig.DEBUG) throw new Error();

        Uri uri = new Uri.Builder().scheme("http").authority("my.poi.server")
                .appendEncodedPath("/dir/subdir/poisource.ov2")
                .appendQueryParameter("q", "customValue")
                .build();
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    /**
     * Init search range label
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        rangeLabel.setText(getString(R.string.search_range, progress + RANGE_MIN));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (switchGps == buttonView) {
            setUseLocationManagerListener(isChecked);
        }
    }

    private void setUseLocationManagerListener(final boolean on) {
        boolean locationManagerListenerEnabled = false;
        try {
            if (on) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    setUseLocationManagerListener(false);
                    onLocationChanged(null);
                    for (final String provider : locationManager.getAllProviders()) {
                        Log.i(MainActivity.class.getSimpleName(), "provider " + provider);
                        // search updated location
                        onLocationChanged(locationManager.getLastKnownLocation(provider));
                        locationManager.requestLocationUpdates(provider, LOCATION_TIME_ACCURACY, LOCATION_RANGE_ACCURACY, this);
                        locationManagerListenerEnabled = true;
                    }
                } else {
                    // request permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_GPS_ON);
                }
            } else {
                locationManager.removeUpdates(this);
            }
        } catch (SecurityException se) {
            Util.showToast(se);
        }
        Log.i(MainActivity.class.getSimpleName(), "setUseLocationManagerListener.status " + locationManagerListenerEnabled);
        switchGps.setChecked(locationManagerListenerEnabled);
        latitudeText.setEnabled(!locationManagerListenerEnabled);
        longitudeText.setEnabled(!locationManagerListenerEnabled);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        final boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        switch (requestCode) {
            case PERMISSION_GPS_ON:
                setUseLocationManagerListener(granted);
                break;
            case PERMISSION_CREATE_BACKUP:
                if (granted) createBackup();
                break;
            case PERMISSION_RESTORE_BACKUP:
                if (granted) showRestoreBackupConfirm();
                break;
        }
    }

    /**
     * Manage location update
     *
     * @param location new location
     */
    @SuppressLint("SetTextI18n")
    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            lastLocation = null;
            latitudeText.setText(null);
            longitudeText.setText(null);
        } else {
            long minTime = System.currentTimeMillis() - LOCATION_TIME_ACCURACY;
            if (location.getTime() >= minTime
                    && (location.getAccuracy() <= LOCATION_RANGE_ACCURACY
                    || lastLocation == null || lastLocation.getTime() <= minTime
                    || lastLocation.getAccuracy() < location.getAccuracy())) {
                lastLocation = location;
                latitudeText.setText(Double.toString(location.getLatitude()));
                longitudeText.setText(Double.toString(location.getLongitude()));
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }
}
