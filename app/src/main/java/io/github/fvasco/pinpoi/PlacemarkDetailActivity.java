package io.github.fvasco.pinpoi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.util.OnSwipeTouchListener;
import io.github.fvasco.pinpoi.util.Util;

/**
 * An activity representing a single Placemark detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link PlacemarkListActivity}.
 */
public class PlacemarkDetailActivity extends AppCompatActivity implements OnSwipeTouchListener.SwipeTouchListener {

    public static final String ARG_PLACEMARK_LIST_ID = "placemarkListId";
    private long placemarkId;
    private FloatingActionButton starFab;
    private FloatingActionButton mapFab;
    private PlacemarkDetailFragment fragment;
    private PlacemarkDao placemarkDao;
    private SharedPreferences preferences;
    /**
     * Placemark id for swipe
     */
    private long[] placemarkIdArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemark_detail);
        Util.initApplicationContext(getApplicationContext());
        placemarkDao = PlacemarkDao.getInstance().open();
        starFab = (FloatingActionButton) findViewById(R.id.fabStar);
        mapFab = (FloatingActionButton) findViewById(R.id.fabMap);
        Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        final View detailContainer = findViewById(R.id.placemark_detail_container);
        detailContainer.setOnTouchListener(new OnSwipeTouchListener(this, detailContainer.getContext()));

        preferences = getPreferences(MODE_PRIVATE);
        placemarkId = getIntent().getLongExtra(PlacemarkDetailFragment.ARG_PLACEMARK_ID,
                preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0));
        preferences.edit().putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId).apply();
        placemarkIdArray = getIntent().getLongArrayExtra(ARG_PLACEMARK_LIST_ID);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle arguments = new Bundle();
            arguments.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId);
            fragment = new PlacemarkDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.placemark_detail_container, fragment)
                    .commit();
        } else {
            fragment = (PlacemarkDetailFragment) getSupportFragmentManager().getFragments().get(0);
        }
        mapFab.setOnLongClickListener(fragment.longClickListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        resetStarFabIcon();
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        placemarkId = savedInstanceState.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID,
                preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        placemarkDao.close();
        super.onDestroy();
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(new Intent(this, PlacemarkListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSwipe(boolean direction) {
        if (placemarkIdArray != null) {
            int i = 0;
            while (placemarkIdArray[i] != placemarkId && i < placemarkIdArray.length) {
                ++i;
            }
            //noinspection PointlessBooleanExpression
            if (direction == OnSwipeTouchListener.SWIPE_LEFT) {
                ++i;
            } else {
                --i;
            }
            if (i >= 0 && i < placemarkIdArray.length) {
                placemarkId = placemarkIdArray[i];
                fragment.setPlacemark(placemarkDao.getPlacemark(placemarkId));
                preferences.edit().putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId).apply();
                resetStarFabIcon();
            }
        }
    }
}
