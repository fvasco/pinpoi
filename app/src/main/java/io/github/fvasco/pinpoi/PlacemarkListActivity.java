package io.github.fvasco.pinpoi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.PlacemarkSearchResult;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Coordinates;
import io.github.fvasco.pinpoi.util.LocationUtil;
import io.github.fvasco.pinpoi.util.Util;

/**
 * An activity representing a list of Placemarks. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link PlacemarkDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class PlacemarkListActivity extends AppCompatActivity {
    public static final String ARG_LATITUDE = "latitude";
    public static final String ARG_LONGITUDE = "longitude";
    public static final String ARG_RANGE = "range";
    public static final String ARG_FAVOURITE = "favourite";
    public static final String ARG_COLLECTION_IDS = "collectionIds";
    public static final String ARG_NAME_FILTER = "nameFilter";
    public static final String ARG_SHOW_MAP = "showMap";
    // clockwise arrow
    private static final char[] ARROWS = new char[]{
           /*N*/ '\u2191', /*NE*/ '\u2197', /*E*/ '\u2192', /*SE*/ '\u2198',
           /*S*/ '\u2193', /*SW*/ '\u2199', /*W*/ '\u2190', /*NW*/ '\u2196'
    };
    private WebView mapWebView;
    private FloatingActionButton starFab;
    private FloatingActionButton mapFab;
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private PlacemarkDao placemarkDao;
    private long[] placemarkIdArray;
    private PlacemarkDetailFragment fragment;
    private Coordinates searchCoordinate;
    private int range;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemark_list);
        Util.initApplicationContext(getApplicationContext());
        placemarkDao = PlacemarkDao.getInstance().open();
        starFab = (FloatingActionButton) findViewById(R.id.fabStar);
        mapFab = (FloatingActionButton) findViewById(R.id.fabMap);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        // Show the Up button in the action bar.
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        final View recyclerView = findViewById(R.id.placemark_list);
        mapWebView = (WebView) findViewById(R.id.mapWebView);
        if (savedInstanceState == null
                ? getIntent().getBooleanExtra(ARG_SHOW_MAP, false)
                : savedInstanceState.getBoolean(ARG_SHOW_MAP)) {
            recyclerView.setVisibility(View.GONE);
            setupWebView(mapWebView);
        } else {
            setupRecyclerView((RecyclerView) recyclerView);
            mapWebView.setVisibility(View.GONE);
        }

        if (findViewById(R.id.placemark_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_SHOW_MAP, mapWebView.getVisibility() == View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        placemarkDao.close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView(final @NonNull RecyclerView recyclerView) {
        final RecyclerView.Adapter oldAdapter = recyclerView.getAdapter();
        if (oldAdapter == null || oldAdapter.getItemCount() == 0) {
            final SimpleItemRecyclerViewAdapter adapter = new SimpleItemRecyclerViewAdapter();
            recyclerView.setAdapter(adapter);
            searchPoi(new Consumer<Collection<PlacemarkSearchResult>>() {
                @Override
                public void accept(Collection<PlacemarkSearchResult> placemarks) {
                    // create array in background thread
                    final PlacemarkSearchResult[] placemarksArray =
                            placemarks.toArray(new PlacemarkSearchResult[placemarks.size()]);
                    Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setPlacemarks(placemarksArray);
                        }
                    });
                }
            });
        }
    }

    private void searchPoi(final @NonNull Consumer<Collection<PlacemarkSearchResult>> placemarksConsumer) {
        // load parameters
        final SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        final float latitude = getIntent().getFloatExtra(ARG_LATITUDE, preferences.getFloat(ARG_LATITUDE, Float.NaN));
        final float longitude = getIntent().getFloatExtra(ARG_LONGITUDE, preferences.getFloat(ARG_LONGITUDE, Float.NaN));
        searchCoordinate = new Coordinates(latitude, longitude);
        range = getIntent().getIntExtra(ARG_RANGE, preferences.getInt(ARG_RANGE, 0));
        String nameFilter = getIntent().getStringExtra(ARG_NAME_FILTER);
        if (nameFilter == null) {
            nameFilter = preferences.getString(ARG_NAME_FILTER, null);
        }
        final boolean favourite = getIntent().getBooleanExtra(ARG_FAVOURITE, preferences.getBoolean(ARG_FAVOURITE, false));
        final String nameFilterFinal = nameFilter;

        // read collections id or parse from preference
        long[] collectionIds = getIntent().getLongArrayExtra(ARG_COLLECTION_IDS);
        if (collectionIds == null) {
            final Set<String> stringIds = preferences.getStringSet(ARG_COLLECTION_IDS, Collections.EMPTY_SET);
            collectionIds = new long[stringIds.size()];
            int i = 0;
            for (final String id : stringIds) {
                collectionIds[i] = Long.parseLong(id);
                ++i;
            }
        }

        // create string set of collection id
        // for preference
        // and deferred job
        final Set<String> collectionIdSet = new TreeSet<>();
        final List<Long> collectionIdList = new ArrayList<>(collectionIds.length);
        for (final long id : collectionIds) {
            collectionIdList.add(id);
            collectionIdSet.add(String.valueOf(id));
        }

        // save parameters in preferences
        preferences.edit()
                .putFloat(ARG_LATITUDE, latitude)
                .putFloat(ARG_LONGITUDE, longitude)
                .putInt(ARG_RANGE, range)
                .putBoolean(ARG_FAVOURITE, favourite)
                .putString(ARG_NAME_FILTER, nameFilter)
                .putStringSet(ARG_COLLECTION_IDS, collectionIdSet)
                .apply();

        Util.showProgressDialog(getString(R.string.title_placemark_list), null,
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Collection<PlacemarkSearchResult> placemarks =
                                    placemarkDao.findAllPlacemarkNear(searchCoordinate,
                                            range, nameFilterFinal, favourite, collectionIdList);
                            Log.d(PlacemarkListActivity.class.getSimpleName(), "placemarks.size()=" + placemarks.size());
                            Util.showToast(getString(R.string.n_placemarks_found, placemarks.size()), Toast.LENGTH_LONG);
                            placemarksConsumer.accept(placemarks);

                            // set up placemark id list for left/right swipe in placemark detail
                            placemarkIdArray = new long[placemarks.size()];
                            int i = 0;
                            for (final PlacemarkSearchResult p : placemarks) {
                                placemarkIdArray[i] = p.getId();
                                ++i;
                            }
                        } catch (Exception e) {
                            Log.e(PlacemarkCollectionDetailFragment.class.getSimpleName(), "updatePlacemarkCollection", e);
                            Util.showToast(getString(R.string.error_search, e.getLocalizedMessage()), Toast.LENGTH_LONG);
                        }
                    }
                }, this);
    }

    @JavascriptInterface
    public void openPlacemark(final long placemarkId) {
        if (mTwoPane) {
            Bundle arguments = new Bundle();
            arguments.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId);
            arguments.putLongArray(PlacemarkDetailActivity.ARG_PLACEMARK_LIST_ID, placemarkIdArray);
            fragment = new PlacemarkDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.placemark_detail_container, fragment)
                    .commit();

            // show fab
            starFab.setVisibility(View.VISIBLE);
            mapFab.setVisibility(View.VISIBLE);
            mapFab.setOnLongClickListener(fragment.longClickListener);
        } else {
            Intent intent = new Intent(this, PlacemarkDetailActivity.class);
            intent.putExtra(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId);
            intent.putExtra(PlacemarkDetailActivity.ARG_PLACEMARK_LIST_ID, placemarkIdArray);
            this.startActivity(intent);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetStarFabIcon();
    }

    private void resetStarFabIcon() {
        fragment.resetStarFabIcon(starFab);
    }

    public void onStarClick(final View view) {
        fragment.onStarClick(starFab);
    }

    public void onMapClick(final View view) {
        fragment.onMapClick(view);
    }

    private void setupWebView(final WebView mapWebView) {
        mapWebView.addJavascriptInterface(this, "pinpoi");
        final WebSettings webSettings = mapWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setAllowContentAccess(false);
        webSettings.setAllowFileAccessFromFileURLs(false);
        webSettings.setAllowUniversalAccessFromFileURLs(false);
        webSettings.setGeolocationEnabled(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setSupportMultipleWindows(false);

        searchPoi(new Consumer<Collection<PlacemarkSearchResult>>() {
            @Override
            public void accept(Collection<PlacemarkSearchResult> placemarksSearchResult) {
                final StringBuilder html = new StringBuilder(1024 + placemarksSearchResult.size() * 256);
                final String leafletVersion = "0.7.7";
                int zoom = (int) (Math.log(40_000_000 / range) / Math.log(2));
                if (zoom < 0) zoom = 0;
                else if (zoom > 18) zoom = 18;
                html.append("<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />")
                        .append("<style>\n" + "body {padding: 0; margin: 0;}\n" + "html, body, #map {height: 100%;}\n" + "</style>")
                        .append("<link rel=\"stylesheet\" href=\"http://cdn.leafletjs.com/leaflet/v" + leafletVersion + "/leaflet.css\" />")
                        .append("<script src=\"http://cdn.leafletjs.com/leaflet/v" + leafletVersion + "/leaflet.js\"></script>")
                        .append("</html>");
                html.append("<body>\n" + "<div id=\"map\"></div>\n" + "<script>");
                html.append("var map = L.map('map').setView([" + searchCoordinate.getLatitude() + "," + searchCoordinate.getLongitude() + "], " + zoom + ");");
                html.append("L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {" +
                        "attribution: '&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors'" +
                        "}).addTo(map);\n");
                // search center marker
                html.append("L.circle([" + searchCoordinate.getLatitude() + "," + searchCoordinate.getLongitude() + "], 1, {\n" +
                        "color: 'blue',fillOpacity: 1}).addTo(map);\n");
                // search limit circle
                html.append("L.circle([" + searchCoordinate.getLatitude() + "," + searchCoordinate.getLongitude() + "], " + range + "," +
                        " {color: 'red',fillOpacity: 0}).addTo(map);\n");
                for (final PlacemarkSearchResult psr : placemarksSearchResult) {
                    html.append("L.marker([" + psr.getLatitude() + "," + psr.getLongitude() + "]).addTo(map)")
                            .append(".bindPopup(\"")
                            .append("<a href='javascript:pinpoi.openPlacemark(" + psr.getId() + ")'>");
                    if (psr.isFlagged()) html.append("<b>");
                    html.append(Util.escapeJavascript(psr.getName()));
                    if (psr.isFlagged()) html.append("</b>");
                    html.append("</a>")
                            .append("\");\n");
                }
                html.append("</script>\n" + "</body>\n" + "</html>");
                if (BuildConfig.DEBUG)
                    Log.i(PlacemarkListActivity.class.getSimpleName(), "Map HTML " + html);
                final String htmlText = html.toString();
                Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mapWebView.loadData(htmlText, "text/html", null);
                        } catch (final Throwable e) {
                            Log.e(PlacemarkListActivity.class.getSimpleName(), "mapWebView.loadData", e);
                            Util.showToast(e);
                        }
                    }
                });
            }
        });
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final DecimalFormat decimalFormat = new DecimalFormat();
        private final StringBuilder stringBuilder = new StringBuilder();
        private final float[] floatArray = new float[2];
        private PlacemarkSearchResult[] placemarks;

        public SimpleItemRecyclerViewAdapter() {
            decimalFormat.setMinimumFractionDigits(1);
            decimalFormat.setMaximumFractionDigits(1);
        }

        public void setPlacemarks(@NonNull final PlacemarkSearchResult[] placemarks) {
            Objects.requireNonNull(placemarks);
            this.placemarks = placemarks;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final PlacemarkSearchResult placemark = placemarks[position];
            holder.placemark = placemark;
            Location.distanceBetween(searchCoordinate.latitude, searchCoordinate.longitude,
                    placemark.getLatitude(), placemark.getLongitude(),
                    floatArray);
            final float distance = floatArray[0];
            // calculate index for arrow
            int arrowIndex = Math.round(floatArray[1] + 45F / 2F);
            if (arrowIndex < 0) {
                arrowIndex += 360;
            }
            arrowIndex = (arrowIndex / 45);
            if (arrowIndex < 0 || arrowIndex >= ARROWS.length) {
                arrowIndex = 0;
            }

            stringBuilder.setLength(0);
            if (distance < 1_000F) {
                stringBuilder.append(Integer.toString((int) distance)).append(" m");
            } else {
                stringBuilder.append(distance < 10_000F
                        ? decimalFormat.format(distance / 1_000F)
                        : Integer.toString((int) distance / 1_000))
                        .append(" ㎞");
            }
            stringBuilder.append(' ').append(ARROWS[arrowIndex])
                    .append("  ").append(placemark.getName());
            holder.view.setText(stringBuilder.toString());
            holder.view.setTypeface(placemark.isFlagged() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

            holder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openPlacemark(holder.placemark.getId());
                }
            });
            holder.view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    LocationUtil.openExternalMap(holder.placemark, false, view.getContext());
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return placemarks == null ? 0 : placemarks.length;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView view;
            public PlacemarkSearchResult placemark;

            public ViewHolder(View view) {
                super(view);
                this.view = (TextView) view.findViewById(android.R.id.text1);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + placemark + "'";
            }
        }
    }

}
