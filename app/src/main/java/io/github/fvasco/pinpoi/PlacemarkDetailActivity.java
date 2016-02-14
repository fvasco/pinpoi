package io.github.fvasco.pinpoi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
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
        Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);

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
    }

    @Override
    protected void onStart() {
        super.onStart();
        resetStarFabIcon();
        fragment.getView().setOnTouchListener(new OnSwipeTouchListener(this, getBaseContext()));
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
        final boolean flagged;
        if (fragment != null) {
            flagged = fragment.getPlacemarkAnnotation().isFlagged();
        } else {
            final PlacemarkAnnotation placemarkAnnotation = placemarkDao.loadPlacemarkAnnotation(placemarkDao.getPlacemark(placemarkId));
            flagged = placemarkAnnotation.isFlagged();
        }
        final int drawable = flagged
                ? R.drawable.abc_btn_rating_star_on_mtrl_alpha
                : R.drawable.abc_btn_rating_star_off_mtrl_alpha;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            starFab.setImageDrawable(getResources().getDrawable(drawable, getBaseContext().getTheme()));
        } else {
            //noinspection deprecation
            starFab.setImageDrawable(getResources().getDrawable(drawable));
        }
    }

    public void onStarClick(final View view) {
        PlacemarkAnnotation placemarkAnnotation = fragment.getPlacemarkAnnotation();
        placemarkAnnotation.setFlagged(!placemarkAnnotation.isFlagged());
        resetStarFabIcon();
    }

    public void onMapClick(final View view) {
        try (final PlacemarkDao placemarkDao = PlacemarkDao.getInstance().open()) {
            final Placemark placemark = placemarkDao.getPlacemark(placemarkId);
            String coordinateFormatted = Util.formatCoordinate(placemark);
            final Uri uri = new Uri.Builder().scheme("geo").authority(coordinateFormatted)
                    .appendQueryParameter("q", coordinateFormatted + '(' + placemark.getName() + ')')
                    .build();
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            view.getContext().startActivity(intent);
        } catch (Exception e) {
            Log.e(PlacemarkDetailActivity.class.getSimpleName(), "Error on map click", e);
            Util.showToast(e.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    public void onCoordinateClick(final View view) {
        fragment.onCoordinateClick(view);
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
