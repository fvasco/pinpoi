package io.github.fvasco.pinpoi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.BackupManager;
import io.github.fvasco.pinpoi.util.Util;


public class MainActivity extends AppCompatActivity
        implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener, LocationListener {

    private static final int LOCATION_RANGE_ACCURACY = 100;
    private static final int LOCATION_TIME_ACCURACY = 5 * 60_000;
    private static final String PREFEFERNCE_LATITUDE = "latitude";
    private static final String PREFEFERNCE_LONGITUDE = "longitude";
    private static final String PREFEFERNCE_NAME_FILTER = "nameFilter";
    private static final String PREFEFERNCE_RANGE = "range";
    private static final String PREFEFERNCE_FAVOURITE = "favourite";
    private static final String PREFEFERNCE_CATEGORY = "category";
    private static final String PREFEFERNCE_COLLECTION = "collection";
    private static final String PREFEFERNCE_GPS = "gps";
    private static final String PREFEFERNCE_ADDRESS = "address";
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
    private TextView rangeLabel;
    private Switch switchGps;
    private Geocoder geocoder;
    private Button searchAddressButton;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Util.initApplicationContext(getApplicationContext());
        if (Geocoder.isPresent()) {
            geocoder = new Geocoder(getApplicationContext());
        }
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
        switchGps = (Switch) findViewById(R.id.switchGps);
        switchGps.setOnCheckedChangeListener(this);
        searchAddressButton = (Button) findViewById(R.id.search_address_button);
        if (geocoder == null) {
            searchAddressButton.setVisibility(View.GONE);
        }

        // setup range seek
        rangeSeek.setOnSeekBarChangeListener(this);
        onProgressChanged(rangeSeek, rangeSeek.getProgress(), false);

        // restore preference
        final SharedPreferences preference = getPreferences(MODE_PRIVATE);
        final boolean useGpsPreference = preference.getBoolean(PREFEFERNCE_GPS, false);
        switchGps.setChecked(useGpsPreference);
        latitudeText.setText(preference.getString(PREFEFERNCE_LATITUDE, "0"));
        longitudeText.setText(preference.getString(PREFEFERNCE_LONGITUDE, "0"));
        nameFilterText.setText(preference.getString(PREFEFERNCE_NAME_FILTER, null));
        favouriteCheck.setChecked(preference.getBoolean(PREFEFERNCE_FAVOURITE, false));
        rangeSeek.setProgress(Math.min(preference.getInt(PREFEFERNCE_RANGE, rangeSeek.getMax() * 2 / 3), rangeSeek.getMax()));
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
        searchAddressButton.setEnabled(!switchGps.isChecked());
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
        try (final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open()) {
            setPlacemarkCollection(placemarkCollectionDao.findPlacemarkCollectionById(placemarkCollectionId));
        }
    }

    private void setPlacemarkCollection(PlacemarkCollection placemarkCollection) {
        selectedPlacemarkCollection = placemarkCollection;
        collectionButton.setText(placemarkCollection == null ? null : placemarkCollection.getName());
    }

    public void openPlacemarkCategoryChooser(View view) {
        try (final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance().open()) {
            final List<String> categories = collectionDao.findAllPlacemarkCollectionCategory();
            categories.add(0, "");
            new AlertDialog.Builder(view.getContext())
                    .setTitle(getString(R.string.collection))
                    .setItems(categories.toArray(new String[categories.size()]),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    setPlacemarkCategory(categories.get(which));
                                }
                            }).show();
        }
    }

    public void openPlacemarkCollectionChooser(View view) {
        try (final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance().open()) {
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
                    placemarkCollectionNames.add(placemarkCollection.getCategory() + " / " + placemarkCollection.getName());
                }
            }
            if (selectedPlacemarkCategory == null && placemarkCollections.isEmpty()) {
                onManagePlacemarkCollections(view);
            } else if (placemarkCollections.size() == 1) {
                setPlacemarkCollection(placemarkCollections.get(0));
            } else {
                placemarkCollections.add(0, null);
                placemarkCollectionNames.add(0, "");
                new AlertDialog.Builder(view.getContext())
                        .setTitle(getString(R.string.collection))
                        .setItems(placemarkCollectionNames.toArray(new String[placemarkCollectionNames.size()]),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        setPlacemarkCollection(placemarkCollections.get(which));
                                    }
                                }).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUseLocationManagerListener(switchGps.isChecked());
    }

    @Override
    protected void onStop() {
        setUseLocationManagerListener(false);

        getPreferences(MODE_PRIVATE).edit()
                .putBoolean(PREFEFERNCE_GPS, switchGps.isChecked())
                .putString(PREFEFERNCE_LATITUDE, latitudeText.getText().toString())
                .putString(PREFEFERNCE_LONGITUDE, longitudeText.getText().toString())
                .putString(PREFEFERNCE_NAME_FILTER, nameFilterText.getText().toString())
                .putBoolean(PREFEFERNCE_FAVOURITE, favouriteCheck.isChecked())
                .putInt(PREFEFERNCE_RANGE, rangeSeek.getProgress())
                .putString(PREFEFERNCE_CATEGORY, selectedPlacemarkCategory)
                .putLong(PREFEFERNCE_COLLECTION, selectedPlacemarkCollection == null ? 0 : selectedPlacemarkCollection.getId())
                .commit();

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.create_backup).setEnabled(BackupManager.isCreateBackupSupported());
        menu.findItem(R.id.restore_backup).setEnabled(BackupManager.isRestoreBackupSupported());
        // on debug show debug menu
        menu.findItem(R.id.menu_debug).setVisible(BuildConfig.DEBUG);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_placemark_collections:
                onManagePlacemarkCollections(null);
                return true;
            case R.id.create_backup:
                createBackup();
                return true;
            case R.id.restore_backup:
                restoreBackup();
                return true;
            case R.id.debug_create_db:
                setUpDebugDatabase();
                return true;
            case R.id.debug_import_collection:
                debugImportCollection();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSearchAddress(final View view) {
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
                            final String address = editText.getText().toString();
                            preference.edit().putString(PREFEFERNCE_ADDRESS, address).apply();
                            searchAddress(address, view.getContext());
                        } finally {
                            dialog.dismiss();
                        }
                    }
                })
                .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
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
                    options[i] = Util.toString(a);
                }
                new AlertDialog.Builder(context)
                        .setItems(options, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                final Address a = addresses.get(which);
                                onLocationChanged(Util.newLocation(a.getLatitude(), a.getLongitude()));
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
                try (final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open()) {
                    final List<PlacemarkCollection> collections = selectedPlacemarkCategory == null
                            ? placemarkCollectionDao.findAllPlacemarkCollection()
                            : placemarkCollectionDao.findAllPlacemarkCollectionInCategory(selectedPlacemarkCategory);
                    collectionsIds = new long[collections.size()];
                    int i = 0;
                    for (final PlacemarkCollection placemarkCollection : collections) {
                        collectionsIds[i] = placemarkCollection.getId();
                        ++i;
                    }
                }
            } else {
                collectionsIds = new long[]{selectedPlacemarkCollection.getId()};
            }
            if (collectionsIds.length == 0) {
                Toast.makeText(MainActivity.this, R.string.error_no_placemark, Toast.LENGTH_LONG).show();
            } else {
                Context context = view.getContext();
                final Intent intent = new Intent(context, PlacemarkListActivity.class);
                intent.putExtra(PlacemarkListActivity.ARG_LATITUDE, Float.parseFloat(latitudeText.getText().toString()));
                intent.putExtra(PlacemarkListActivity.ARG_LONGITUDE, Float.parseFloat(longitudeText.getText().toString()));
                intent.putExtra(PlacemarkListActivity.ARG_NAME_FILTER, nameFilterText.getText().toString());
                intent.putExtra(PlacemarkListActivity.ARG_FAVOURITE, favouriteCheck.isChecked());
                intent.putExtra(PlacemarkListActivity.ARG_RANGE, rangeSeek.getProgress() * 1000);
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

    private void createBackup() {
        final BackupManager backupManager = new BackupManager(getApplicationContext());
        try {
            Util.showToast(getString(R.string.action_create_backup), Toast.LENGTH_SHORT);
            backupManager.create();
            Util.showToast(getString(R.string.backup_file, BackupManager.BACKUP_FILE.getAbsolutePath()), Toast.LENGTH_LONG);
        } catch (Exception e) {
            Log.w(MainActivity.class.getSimpleName(), "create backup failed", e);
            Util.showToast(e);
        }
    }

    private void restoreBackup() {
        final BackupManager backupManager = new BackupManager(getApplicationContext());
        try {
            Util.showToast(getString(R.string.action_restore_backup), Toast.LENGTH_SHORT);
            backupManager.restore();
            Util.showToast(getString(R.string.backup_file, BackupManager.BACKUP_FILE.getAbsolutePath()), Toast.LENGTH_LONG);
            setPlacemarkCollection(null);
        } catch (Exception e) {
            Log.w(MainActivity.class.getSimpleName(), "restore backup failed", e);
            Util.showToast(e);
        }
    }

    private void setUpDebugDatabase() {
        if (!BuildConfig.DEBUG) throw new Error();

        try (final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();
             final PlacemarkDao placemarkDao = PlacemarkDao.getInstance().open()) {
            final SQLiteDatabase placemarkCollectionDatabase = placemarkCollectionDao.getDatabase();
            final SQLiteDatabase placemarkDatabase = placemarkDao.getDatabase();
            placemarkCollectionDatabase.beginTransaction();
            placemarkDatabase.beginTransaction();
            try {
                // clear all database
                for (final PlacemarkCollection placemarkCollection : placemarkCollectionDao.findAllPlacemarkCollection()) {
                    placemarkDao.deleteByCollectionId(placemarkCollection.getId());
                    placemarkCollectionDao.delete(placemarkCollection);
                }

                // recreate test database
                final PlacemarkCollection placemarkCollection = new PlacemarkCollection();
                for (int pci = 15; pci >= 0; --pci) {
                    placemarkCollection.setId(0);
                    placemarkCollection.setName("Placemark Collection " + pci);
                    placemarkCollection.setCategory(pci == 0 ? null : "Category " + (pci % 7));
                    placemarkCollection.setDescription("Placemark Collection long long long description");
                    placemarkCollection.setSource("source " + pci);
                    placemarkCollection.setPoiCount(pci);
                    placemarkCollection.setLastUpdate(pci * 10_000_000);
                    placemarkCollectionDao.insert(placemarkCollection);
                    Log.i(MainActivity.class.getSimpleName(), "inserted " + placemarkCollection);

                    final Placemark placemark = new Placemark();
                    for (int lat = -60; lat < 60; ++lat) {
                        for (int lon = -90; lon < 90; lon += 2) {
                            placemark.setId(0);
                            placemark.setName("Placemark " + lat + "," + lon + "/" + pci);
                            placemark.setDescription((lat + lon) % 10 == 0 ? null : "Placemark description");
                            placemark.setLatitude((float) (lat + Math.sin(lat + pci)));
                            placemark.setLongitude((float) (lon + Math.sin(lon - pci)));
                            placemark.setCollectionId(placemarkCollection.getId());
                            placemarkDao.insert(placemark);
                        }
                    }
                }

                placemarkDatabase.setTransactionSuccessful();
                placemarkCollectionDatabase.setTransactionSuccessful();
            } finally {
                placemarkDatabase.endTransaction();
                placemarkCollectionDatabase.endTransaction();
            }
        }
    }

    private void debugImportCollection() {
        if (!BuildConfig.DEBUG) throw new Error();

         Uri uri = new Uri.Builder().scheme("http").authority("my.poi.server")
                .appendEncodedPath("/dir/subdir/poisource.ov2")
                .appendQueryParameter("q", "customValue")
                .build();
    uri=    Uri.parse("http://womo-sp.lima-city.de/womo_SP_A.asc");
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    /**
     * Init search range label
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        rangeLabel.setText(getString(R.string.search_range, progress));
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
            searchAddressButton.setEnabled(!isChecked);
        }
    }

    private void setUseLocationManagerListener(final boolean on) {
        boolean locationManagerListenerEnabled = false;
        if (on) {
            setUseLocationManagerListener(false);
            onLocationChanged(lastLocation);
            for (final String provider : locationManager.getAllProviders()) {
                Log.i(MainActivity.class.getSimpleName(), "provider " + provider);
                // search updated location
                onLocationChanged(locationManager.getLastKnownLocation(provider));
                locationManager.requestLocationUpdates(provider, LOCATION_TIME_ACCURACY, LOCATION_RANGE_ACCURACY, this);
                locationManagerListenerEnabled = true;
            }
        } else {
            locationManager.removeUpdates(this);
        }
        Log.i(MainActivity.class.getSimpleName(), "status " + locationManagerListenerEnabled);
        latitudeText.setEnabled(!locationManagerListenerEnabled);
        longitudeText.setEnabled(!locationManagerListenerEnabled);
    }

    /**
     * Manage location update
     *
     * @param location new location
     */
    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
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
