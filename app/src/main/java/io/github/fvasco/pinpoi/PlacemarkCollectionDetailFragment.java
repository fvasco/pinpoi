package io.github.fvasco.pinpoi;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.importer.ImporterFacade;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.Consumer;
import io.github.fvasco.pinpoi.util.Util;

/**
 * A fragment representing a single Placemark Collection detail screen.
 * This fragment is either contained in a {@link PlacemarkCollectionListActivity}
 * in two-pane mode (on tablets) or a {@link PlacemarkCollectionDetailActivity}
 * on handsets.
 */
public class PlacemarkCollectionDetailFragment extends Fragment {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_PLACEMARK_COLLECTION_ID = "placemarkCollectionId";
    private static final int FILE_SELECT_CODE = 1;
    private final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance();
    private PlacemarkCollection placemarkCollection;
    private EditText descriptionText;
    private TextView sourceText;
    private AutoCompleteTextView categoryText;
    private TextView lastUpdateText;
    private TextView poiCountText;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PlacemarkCollectionDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        placemarkCollectionDao.open();

        if (getArguments().containsKey(ARG_PLACEMARK_COLLECTION_ID)) {
            placemarkCollection = placemarkCollectionDao.findPlacemarkCollectionById(
                    savedInstanceState == null
                            ? getArguments().getLong(ARG_PLACEMARK_COLLECTION_ID)
                            : savedInstanceState.getLong(ARG_PLACEMARK_COLLECTION_ID));

            Activity activity = this.getActivity();
            CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) activity.findViewById(R.id.toolbar_layout);
            if (appBarLayout != null && placemarkCollection != null) {
                appBarLayout.setTitle(placemarkCollection.getName());
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.placemarkcollection_detail, container, false);
        descriptionText = ((EditText) rootView.findViewById(R.id.description));
        sourceText = ((TextView) rootView.findViewById(R.id.source));
        lastUpdateText = ((TextView) rootView.findViewById(R.id.last_update));
        poiCountText = ((TextView) rootView.findViewById(R.id.poi_count));
        categoryText = ((AutoCompleteTextView) rootView.findViewById(R.id.category));
        categoryText.setAdapter(new ArrayAdapter<>(container.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                placemarkCollectionDao.findAllPlacemarkCollectionCategory()));
        rootView.findViewById(R.id.browseBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser(v);
            }
        });


        if (placemarkCollection != null) {
            descriptionText.setText(placemarkCollection.getDescription());
            sourceText.setText(placemarkCollection.getSource());
            categoryText.setText(placemarkCollection.getCategory());
            showUpdatedCollectionInfo();
        }

        return rootView;
    }

    @Override
    public void onPause() {
        savePlacemarkCollection();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (placemarkCollection != null) {
            outState.putLong(ARG_PLACEMARK_COLLECTION_ID, placemarkCollection.getId());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        placemarkCollectionDao.close();
        super.onDestroy();
    }

    public void savePlacemarkCollection() {
        if (placemarkCollection == null) {
            placemarkCollection = new PlacemarkCollection();
        }
        placemarkCollection.setDescription(descriptionText.getText().toString());
        placemarkCollection.setSource(sourceText.getText().toString());
        placemarkCollection.setCategory(categoryText.getText().toString());

        try {
            if (placemarkCollection.getId() == 0) {
                placemarkCollectionDao.insert(placemarkCollection);
            } else {
                placemarkCollectionDao.update(placemarkCollection);
            }
        } catch (Exception e) {
            Log.e(PlacemarkCollectionDetailFragment.class.getSimpleName(), "savePlacemarkCollection", e);
            Toast.makeText(getActivity(), R.string.validation_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update screen with poi count and last update
     */
    private void showUpdatedCollectionInfo() {
        Activity activity = this.getActivity();
        CollapsingToolbarLayout appBarLayout = (CollapsingToolbarLayout) activity.findViewById(R.id.toolbar_layout);
        if (appBarLayout != null) {
            appBarLayout.setTitle(placemarkCollection.getName());
        }
        final int poiCount = placemarkCollection.getPoiCount();
        poiCountText.setText(getString(R.string.poi_count, poiCount));
        lastUpdateText.setText(getString(R.string.last_update, placemarkCollection.getLastUpdate()));
        lastUpdateText.setVisibility(poiCount == 0 ? View.GONE : View.VISIBLE);
    }

    public String getRequiredPermissionToUpdatePlacemarkCollection() {
        final String url = sourceText.getText().toString();
        return url.startsWith("/") || url.startsWith("file:/") ? Manifest.permission.READ_EXTERNAL_STORAGE
                : Manifest.permission.INTERNET;
    }

    public void updatePlacemarkCollection() {
        final ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setTitle(getString(R.string.update, placemarkCollection.getName()));
        progressDialog.setMessage(sourceText.getText());
        Util.EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    savePlacemarkCollection();
                    final ImporterFacade importerFacade = new ImporterFacade();
                    importerFacade.setProgressDialog(progressDialog);
                    importerFacade.setProgressDialogMessageFormat(getString(R.string.poi_count));
                    final int count = importerFacade.importPlacemarks(placemarkCollection);
                    if (count == 0) {
                        Util.showToast(getString(R.string.error_update, placemarkCollection.getName(), getString(R.string.n_placemarks_found, 0)), Toast.LENGTH_LONG);
                    } else {
                        Util.showToast(getString(R.string.update_collection_success, placemarkCollection.getName(), count), Toast.LENGTH_LONG);
                    }
                } catch (Exception e) {
                    Log.e(PlacemarkCollectionDetailFragment.class.getSimpleName(), "updatePlacemarkCollection", e);
                    Util.showToast(getString(R.string.error_update, placemarkCollection.getName(), e.getLocalizedMessage()), Toast.LENGTH_LONG);
                } finally {
                    // update placemark collection info
                    Util.MAIN_LOOPER_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            showUpdatedCollectionInfo();
                        }
                    });
                }
            }
        });
    }

    public void renamePlacemarkCollection(String newPlacemarkCollectionName) {
        if (!Util.isEmpty(newPlacemarkCollectionName)
                && placemarkCollectionDao.findPlacemarkCollectionByName(newPlacemarkCollectionName) == null) {
            placemarkCollection.setName(newPlacemarkCollectionName);
            try {
                savePlacemarkCollection();
            } catch (Exception e) {
                Util.showToast(e);
            } finally {
                showUpdatedCollectionInfo();
            }
        }
    }

    public void deletePlacemarkCollection() {
        try (final PlacemarkDao placemarkDao = PlacemarkDao.getInstance().open()) {
            placemarkDao.deleteByCollectionId(placemarkCollection.getId());
        }
        placemarkCollectionDao.delete(placemarkCollection);
    }

    public void showFileChooser(View view) {
        Util.openFileChooser(Environment.getExternalStorageDirectory(),
                new Consumer<File>() {
                    @Override
                    public void accept(File file) {
                        sourceText.setText(file.getAbsolutePath());
                    }
                }, view.getContext());
    }

    public PlacemarkCollection getPlacemarkCollection() {
        return placemarkCollection;
    }
}
