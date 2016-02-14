package io.github.fvasco.pinpoi;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
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
    // clockwise arrow
    private static final char[] ARROWS = new char[]{
           /*N*/ '\u2191', /*NE*/ '\u2197', /*E*/ '\u2192', /*SE*/ '\u2198',
           /*S*/ '\u2193', /*SW*/ '\u2199', /*W*/ '\u2190', /*NW*/ '\u2196'
    };


    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private float latitude;
    private float longitude;
    private PlacemarkDao placemarkDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemark_list);
        Util.initApplicationContext(getApplicationContext());
        placemarkDao = PlacemarkDao.getInstance().open();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        View recyclerView = findViewById(R.id.placemark_list);
        setupRecyclerView((RecyclerView) recyclerView);

        if (findViewById(R.id.placemark_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }
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
        final SimpleItemRecyclerViewAdapter adapter = new SimpleItemRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);

        // load parameters
        final SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        latitude = getIntent().getFloatExtra(ARG_LATITUDE, preferences.getFloat(ARG_LATITUDE, Float.NaN));
        longitude = getIntent().getFloatExtra(ARG_LONGITUDE, preferences.getFloat(ARG_LONGITUDE, Float.NaN));
        final int range = getIntent().getIntExtra(ARG_RANGE, preferences.getInt(ARG_RANGE, 0));
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

        final ProgressDialog progress = new ProgressDialog(recyclerView.getContext());
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setIndeterminate(true);
        progress.show();
        Util.EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Collection<Placemark> placemarks =
                            placemarkDao.findAllPlacemarkNear(Util.newLocation(latitude, longitude),
                                    range, nameFilterFinal, favourite, collectionIdList);
                    Log.d(PlacemarkListActivity.class.getSimpleName(), "placemarks " + placemarks.size());
                    if (placemarks.isEmpty()) {
                        Util.showToast(getString(R.string.error_no_placemark), Toast.LENGTH_LONG);
                    } else {
                        Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                            @Override
                            public void run() {
                                adapter.setPlacemarks(placemarks);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(PlacemarkCollectionDetailFragment.class.getSimpleName(), "updatePlacemarkCollection", e);
                    Util.showToast(getString(R.string.error_search, e.getLocalizedMessage()), Toast.LENGTH_LONG);
                } finally {
                    progress.dismiss();
                }
            }
        });
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final DecimalFormat decimalFormat = new DecimalFormat();
        private final StringBuilder stringBuilder = new StringBuilder();
        private final float[] floatArray = new float[2];
        private Placemark[] mValues;

        public SimpleItemRecyclerViewAdapter() {
            decimalFormat.setMinimumFractionDigits(1);
            decimalFormat.setMaximumFractionDigits(1);
        }

        public void setPlacemarks(final Collection<Placemark> placemarks) {
            mValues = placemarks.toArray(new Placemark[placemarks.size()]);
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.placemark_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Placemark p = mValues[position];
            holder.mItem = p;
            Location.distanceBetween(latitude, longitude, p.getLatitude(), p.getLongitude(), floatArray);
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
                stringBuilder.append(Integer.toString((int) distance)).append(' ');
            } else {
                stringBuilder.append(distance < 10_000F
                        ? decimalFormat.format(distance / 1_000F)
                        : Integer.toString((int) distance / 1_000))
                        .append(" k");
            }
            stringBuilder.append("m ").append(ARROWS[arrowIndex]);
            holder.mInfoView.setText(stringBuilder);
            holder.mContentView.setText(p.getName());

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putLong(PlacemarkDetailFragment.ARG_ITEM_ID, holder.mItem.getId());
                        PlacemarkDetailFragment fragment = new PlacemarkDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.placemark_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, PlacemarkDetailActivity.class);
                        intent.putExtra(PlacemarkDetailFragment.ARG_ITEM_ID, holder.mItem.getId());
                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mValues == null ? 0 : mValues.length;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mInfoView;
            public final TextView mContentView;
            public Placemark mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mInfoView = (TextView) view.findViewById(R.id.info);
                mContentView = (TextView) view.findViewById(R.id.content);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }
}
