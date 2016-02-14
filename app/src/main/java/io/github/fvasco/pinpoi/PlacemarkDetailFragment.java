package io.github.fvasco.pinpoi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.model.Placemark;
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
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
    private static final String PREFERENCE_COORDINATE_FORMAT = "coordinateFormat";

    private EditText noteText;
    private Placemark placemark;
    private PlacemarkCollection placemarkCollection;
    private PlacemarkDao placemarkDao;
    private PlacemarkCollectionDao placemarkCollectionDao;
    private PlacemarkAnnotation placemarkAnnotation;
    private SharedPreferences preferences;
    private int coordinateFormat;
    private TextView placemarkDetail;
    private TextView coordinateText;
    private TextView collectionDescriptionTitle;
    private TextView collectionDescriptionText;

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
        coordinateFormat = preferences.getInt(PREFERENCE_COORDINATE_FORMAT, 0);
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
        coordinateText = ((TextView) rootView.findViewById(R.id.coordinate));
        setPlacemark(placemark);
        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (placemark != null) {
            outState.putLong(ARG_PLACEMARK_ID, placemark.getId());
        }
        super.onSaveInstanceState(outState);
    }

    public void setPlacemark(final Placemark placemark) {
        if (placemarkAnnotation != null) {
            placemarkAnnotation.setNote(noteText.getText().toString());
            placemarkDao.update(placemarkAnnotation);
        }
        this.placemark = placemark;
        placemarkAnnotation = placemark == null ? null : placemarkDao.loadPlacemarkAnnotation(placemark);
        placemarkCollection = placemark == null ? null : placemarkCollectionDao.findPlacemarkCollectionById(placemark.getCollectionId());
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
                        : placemark.getName() + "\n\n" + placemark.getDescription());
        noteText.setText(placemarkAnnotation == null ? null : placemarkAnnotation.getNote());
        showCoordinate(coordinateText);
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

    private void showCoordinate(TextView textView) {
        textView.setText(placemark == null ? null :
                getString(R.string.location,
                        Location.convert(placemark.getLatitude(), coordinateFormat),
                        Location.convert(placemark.getLongitude(), coordinateFormat)));

    }

    public void onCoordinateClick(final View view) {
        coordinateFormat = (coordinateFormat + 1) % 3;
        preferences.edit().putInt(PREFERENCE_COORDINATE_FORMAT, coordinateFormat).apply();
        showCoordinate((TextView) view);
    }

    public PlacemarkAnnotation getPlacemarkAnnotation() {
        return placemarkAnnotation;
    }
}
