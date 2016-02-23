package io.github.fvasco.pinpoi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.Future;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Coordinates;
import io.github.fvasco.pinpoi.util.LocationUtil;
import io.github.fvasco.pinpoi.util.Util;

/**
 * A fragment representing a single Placemark detail screen.
 * This fragment is either contained in a {@link PlacemarkListActivity}
 * in two-pane mode (on tablets) or a {@link PlacemarkDetailActivity}
 * on handsets.
 */
public class PlacemarkDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_PLACEMARK_ID = "placemarkId";
    private EditText noteText;
    private Placemark placemark;
    public final View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            LocationUtil.openExternalMap(placemark, true, view.getContext());
            return true;
        }
    };
    private PlacemarkDao placemarkDao;
    private PlacemarkCollectionDao placemarkCollectionDao;
    private PlacemarkAnnotation placemarkAnnotation;
    private SharedPreferences preferences;
    private TextView placemarkDetail;
    private TextView coordinateText;
    private TextView collectionDescriptionTitle;
    private TextView collectionDescriptionText;
    private TextView addressText;
    private Future<String> searchAddressFuture;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PlacemarkDetailFragment() {
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        placemarkDao = PlacemarkDao.getInstance().open();
        placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();

        preferences = getActivity().getSharedPreferences(PlacemarkDetailFragment.class.getSimpleName(), Context.MODE_PRIVATE);
        final long id = savedInstanceState == null
                ? getArguments().getLong(ARG_PLACEMARK_ID, preferences.getLong(ARG_PLACEMARK_ID, 0))
                : savedInstanceState.getLong(ARG_PLACEMARK_ID);
        Log.i(PlacemarkDetailFragment.class.getSimpleName(), "open placemark " + id);
        placemark = placemarkDao.getPlacemark(id);
    }

    @Override
    public void onDestroy() {
        setPlacemark(null);
        placemarkDao.close();
        placemarkCollectionDao.close();
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.placemark_detail, container, false);
        collectionDescriptionTitle = (TextView) rootView.findViewById(R.id.placemark_collection_description_title);
        collectionDescriptionText = (TextView) rootView.findViewById(R.id.placemark_collection_description);
        noteText = ((EditText) rootView.findViewById(R.id.note));
        placemarkDetail = ((TextView) rootView.findViewById(R.id.placemark_detail));
        // By default these links will appear but not respond to user input.
        placemarkDetail.setMovementMethod(LinkMovementMethod.getInstance());
        coordinateText = ((TextView) rootView.findViewById(R.id.coordinates));
        addressText = ((TextView) rootView.findViewById(R.id.address));
        setPlacemark(placemark);
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        resetStarFabIcon((FloatingActionButton) getActivity().findViewById(R.id.fabStar));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (placemark != null) {
            outState.putLong(ARG_PLACEMARK_ID, placemark.getId());
        }
        super.onSaveInstanceState(outState);
    }

    public PlacemarkAnnotation getPlacemarkAnnotation() {
        return placemarkAnnotation;
    }

    public Placemark getPlacemark() {
        return placemark;
    }

    public void setPlacemark(final Placemark placemark) {
        if (placemarkAnnotation != null) {
            placemarkAnnotation.setNote(noteText.getText().toString());
            placemarkDao.update(placemarkAnnotation);
        }
        this.placemark = placemark;
        placemarkAnnotation = placemark == null ? null : placemarkDao.loadPlacemarkAnnotation(placemark);
        final PlacemarkCollection placemarkCollection = placemark == null ? null : placemarkCollectionDao.findPlacemarkCollectionById(placemark.getCollectionId());
        if (placemark != null) {
            preferences.edit().putLong(ARG_PLACEMARK_ID, placemark.getId()).apply();
        }

        Activity activity = this.getActivity();
        CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) activity.findViewById(R.id.toolbar_layout);
        if (appBarLayout != null) {
            appBarLayout.setTitle(placemark == null ? null : placemark.getName());
        }
        placemarkDetail
                .setText(placemark == null ? null
                        : Util.isEmpty(placemark.getDescription())
                        ? placemark.getName()
                        : Util.isHtml(placemark.getDescription())
                        ? Html.fromHtml("<p>" + Html.escapeHtml(placemark.getName()) + "</p>" + placemark.getDescription())
                        : placemark.getName() + "\n\n" + placemark.getDescription());
        noteText.setText(placemarkAnnotation == null ? null : placemarkAnnotation.getNote());
        // show coordinates
        coordinateText.setText(placemark == null ? null :
                getString(R.string.location,
                        Location.convert(placemark.getLatitude(), Location.FORMAT_DEGREES),
                        Location.convert(placemark.getLongitude(), Location.FORMAT_DEGREES)));
        // show address
        if (searchAddressFuture != null) {
            searchAddressFuture.cancel(true);
        }
        addressText.setText(null);
        addressText.setVisibility(View.GONE);
        if (placemark != null) {
            searchAddressFuture = LocationUtil.getAddressStringAsync(Coordinates.fromPlacemark(placemark), new Consumer<String>() {
                @Override
                public void accept(String address) {
                    if (!Util.isEmpty(address)) {
                        addressText.setVisibility(View.VISIBLE);
                        addressText.setText(address);
                    }
                }
            });
        }

        // show placemark collection details
        if (placemarkCollection != null) {
            collectionDescriptionTitle.setVisibility(View.VISIBLE);
            collectionDescriptionText.setVisibility(View.VISIBLE);
            collectionDescriptionTitle.setText(placemarkCollection.getName());
            collectionDescriptionText.setText(placemarkCollection.getDescription());
        } else {
            collectionDescriptionTitle.setVisibility(View.GONE);
            collectionDescriptionText.setVisibility(View.GONE);
        }
    }

    public void onMapClick(final View view) {
        LocationUtil.openExternalMap(placemark, false, view.getContext());
    }


    public void resetStarFabIcon(FloatingActionButton starFab) {
        final int drawable = getPlacemarkAnnotation().isFlagged()
                ? R.drawable.abc_btn_rating_star_on_mtrl_alpha
                : R.drawable.abc_btn_rating_star_off_mtrl_alpha;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            starFab.setImageDrawable(getResources().getDrawable(drawable, getActivity().getBaseContext().getTheme()));
        } else {
            //noinspection deprecation
            starFab.setImageDrawable(getResources().getDrawable(drawable));
        }
    }

    public void onStarClick(FloatingActionButton starFab) {
        placemarkAnnotation.setFlagged(!placemarkAnnotation.isFlagged());
        resetStarFabIcon(starFab);
    }

}
