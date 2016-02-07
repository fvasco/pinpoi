package io.github.fvasco.pinpoi;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
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
    private static final String PREFEFERNCE_COLLECTION = "collection";
    private static final String PREFEFERNCE_GPS = "gps";
    private static final String PREFEFERNCE_ADDRESS = "address";
    private LocationManager locationManager;
    private Spinner collectionSpinner;
    private SeekBar rangeSeek;
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView nameFilterText;
    private Switch favouriteSwitch;
    private TextView rangeLabel;
    private Switch switchGps;
    private Geocoder geocoder;
    private Button searchAddressButton;
    private Button manageCollectionButton;
    private Button searchPlacemarkButton;
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
        collectionSpinner = (Spinner) findViewById(R.id.collectionSpinner);
        rangeLabel = (TextView) findViewById(R.id.rangeLabel);
        rangeSeek = (SeekBar) findViewById(R.id.rangeSeek);
        latitudeText = (TextView) findViewById(R.id.latitudeText);
        longitudeText = (TextView) findViewById(R.id.longitudeText);
        nameFilterText = (TextView) findViewById(R.id.name_filter_text);
        favouriteSwitch = (Switch) findViewById(R.id.favouriteSwitch);
        switchGps = (Switch) findViewById(R.id.switchGps);
        switchGps.setOnCheckedChangeListener(this);
        searchAddressButton = (Button) findViewById(R.id.search_address_button);
        manageCollectionButton = (Button) findViewById(R.id.manage_placemark_collections_button);
        searchPlacemarkButton = (Button) findViewById(R.id.search_placemark_button);
        if (geocoder == null) {
            searchAddressButton.setVisibility(View.GONE);
        }

        // setup range seek
        rangeSeek.setOnSeekBarChangeListener(this);
        onProgressChanged(rangeSeek, rangeSeek.getProgress(), false);

        // setup collection spinner
        final List<PlacemarkCollection> placemarkCollections;
        ArrayAdapter<PlacemarkCollection> dataAdapter;
        try (final PlacemarkCollectionDao collectionDao = PlacemarkCollectionDao.getInstance().open()) {
            placemarkCollections = collectionDao.findAllPlacemarkCollection();
            // skip empty collections
            for (final Iterator<PlacemarkCollection> iterator = placemarkCollections.iterator(); iterator.hasNext(); ) {
                if (iterator.next().getPoiCount() == 0) {
                    iterator.remove();
                }
            }
            dataAdapter = new ArrayAdapter<PlacemarkCollection>
                    (this, R.layout.placemarkcollection_list_content, placemarkCollections) {
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    return getView(position, convertView, parent);
                }

                @Override
                public View getView(int position, View view, ViewGroup parent) {
                    PlacemarkCollection pc = getItem(position);
                    if (view == null) {
                        view = getLayoutInflater().inflate(R.layout.placemarkcollection_list_content, parent, false);
                    }
                    final TextView contentText = (TextView) view.findViewById(R.id.content);
                    contentText.setText((Util.isEmpty(pc.getCategory()) ? "" : pc.getCategory())
                            + "/\n   " + pc.getName());

                    return view;
                }
            };

            collectionSpinner.setAdapter(dataAdapter);
        }

        // no collection -> no search
        if (placemarkCollections.isEmpty()) {
            collectionSpinner.setVisibility(View.GONE);
            searchPlacemarkButton.setEnabled(false);
        } else {
            manageCollectionButton.setVisibility(View.GONE);
        }

        // restore preference
        final SharedPreferences preference = getPreferences(MODE_PRIVATE);
        final boolean useGpsPreference = preference.getBoolean(PREFEFERNCE_GPS, false);
        switchGps.setChecked(useGpsPreference);
        latitudeText.setText(preference.getString(PREFEFERNCE_LATITUDE, ""));
        longitudeText.setText(preference.getString(PREFEFERNCE_LONGITUDE, ""));
        nameFilterText.setText(preference.getString(PREFEFERNCE_NAME_FILTER, ""));
        favouriteSwitch.setChecked(preference.getBoolean(PREFEFERNCE_FAVOURITE, false));
        rangeSeek.setProgress(Math.min(preference.getInt(PREFEFERNCE_RANGE, rangeSeek.getMax() * 2 / 3), rangeSeek.getMax()));
        if (dataAdapter.getCount() > 1) {
            collectionSpinner.setSelection(Math.min(preference.getInt(PREFEFERNCE_COLLECTION, 0), dataAdapter.getCount() - 1));
        }

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
                .putBoolean(PREFEFERNCE_FAVOURITE, favouriteSwitch.isChecked())
                .putInt(PREFEFERNCE_RANGE, rangeSeek.getProgress())
                .putInt(PREFEFERNCE_COLLECTION, collectionSpinner.getSelectedItemPosition())
                .commit();

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public void onSearchAddress(final View view) {
        final Context context = view.getContext();
        final SharedPreferences preference = getPreferences(MODE_PRIVATE);

        final EditText editText = new EditText(context);
        editText.setTextIsSelectable(true);
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
            Context context = view.getContext();
            final Intent intent = new Intent(context, PlacemarkListActivity.class);
            intent.putExtra(PlacemarkListActivity.ARG_LATITUDE, Float.parseFloat(latitudeText.getText().toString()));
            intent.putExtra(PlacemarkListActivity.ARG_LONGITUDE, Float.parseFloat(longitudeText.getText().toString()));
            intent.putExtra(PlacemarkListActivity.ARG_NAME_FILTER, nameFilterText.getText().toString());
            intent.putExtra(PlacemarkListActivity.ARG_FAVOURITE, favouriteSwitch.isChecked());
            intent.putExtra(PlacemarkListActivity.ARG_RANGE, rangeSeek.getProgress() * 1000);
            intent.putExtra(PlacemarkListActivity.ARG_COLLECTION_ID, ((PlacemarkCollection) collectionSpinner.getSelectedItem()).getId());
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(MainActivity.class.getSimpleName(), "onSearchPoi", e);
            Toast.makeText(MainActivity.this, R.string.validation_error, Toast.LENGTH_SHORT).show();
        }
    }

    public void onManagePlacemarkCollections(View view) {
        startActivity(new Intent(this, PlacemarkCollectionListActivity.class));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                return true;
            case R.id.action_placemark_collections:
                onManagePlacemarkCollections(null);
                return true;
        }

        return super.onOptionsItemSelected(item);
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
        assert switchGps == buttonView;
        setUseLocationManagerListener(isChecked);
        searchAddressButton.setEnabled(!isChecked);
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
