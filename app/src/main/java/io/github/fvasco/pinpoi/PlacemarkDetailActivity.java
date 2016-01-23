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
import io.github.fvasco.pinpoi.util.Util;

/**
 * An activity representing a single Placemark detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link PlacemarkListActivity}.
 */
public class PlacemarkDetailActivity extends AppCompatActivity {

    private long placemarkId;
    private FloatingActionButton starFab;
    private PlacemarkDetailFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemark_detail);
        Util.initApplicationContext(getApplicationContext());
        starFab = (FloatingActionButton) findViewById(R.id.fabStar);
        Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);

        final SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        placemarkId = getIntent().getLongExtra(PlacemarkDetailFragment.ARG_ITEM_ID, preferences.getLong(PlacemarkDetailFragment.ARG_ITEM_ID, 0));

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
            arguments.putLong(PlacemarkDetailFragment.ARG_ITEM_ID, placemarkId);
            fragment = new PlacemarkDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.placemark_detail_container, fragment)
                    .commit();
            preferences.edit()
                    .putLong(PlacemarkDetailFragment.ARG_ITEM_ID, placemarkId)
                    .apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        resetStarFabIcon();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetStarFabIcon();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void resetStarFabIcon() {
        final boolean flagged;
        if (fragment != null) {
            flagged = fragment.getPlacemarkAnnotation().isFlagged();
        } else try (final PlacemarkDao placemarkDao = PlacemarkDao.getInstance().open()) {
            final PlacemarkAnnotation placemarkAnnotation = placemarkDao.loadPlacemarkAnnotation(placemarkDao.getPlacemark(placemarkId));
            flagged = placemarkAnnotation.isFlagged();
        }
        final int drawable = flagged
                ? R.drawable.abc_btn_rating_star_on_mtrl_alpha
                : R.drawable.abc_btn_rating_star_off_mtrl_alpha;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            starFab.setImageDrawable(getResources().getDrawable(drawable, getBaseContext().getTheme()));
        } else {
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
            Log.e(PlacemarkDetailActivity.class.getSimpleName(), "Error on click", e);
            Util.showToast(e.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
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
}
