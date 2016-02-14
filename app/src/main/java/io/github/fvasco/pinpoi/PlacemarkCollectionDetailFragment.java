package io.github.fvasco.pinpoi;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.dao.PlacemarkDao;
import io.github.fvasco.pinpoi.importer.ImporterFacade;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
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
    public static final String ARG_ITEM_ID = "item_id";
    private static final int FILE_SELECT_CODE = 1;
    private final PlacemarkCollectionDao placemarkCollectionDao = PlacemarkCollectionDao.getInstance();
    private PlacemarkCollection placemarkCollection;
    private EditText descriptionText;
    private TextView sourceText;
    private AutoCompleteTextView cateogoryText;
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

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            placemarkCollection = placemarkCollectionDao.findPlacemarkCollectionById(getArguments().getLong(ARG_ITEM_ID));

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
        cateogoryText = ((AutoCompleteTextView) rootView.findViewById(R.id.category));
        cateogoryText.setAdapter(new ArrayAdapter<>(container.getContext(),
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
            cateogoryText.setText(placemarkCollection.getCategory());
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
        placemarkCollection.setCategory(cateogoryText.getText().toString());

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
        int poiCount = placemarkCollection.getPoiCount();
        poiCountText.setText(getString(R.string.poi_count, poiCount));
        lastUpdateText.setText(getString(R.string.last_update, placemarkCollection.getLastUpdate()));
        lastUpdateText.setVisibility(poiCount == 0 ? View.INVISIBLE : View.VISIBLE);
    }

    public void updatePlacemarkCollection() {
        final ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage(getString(R.string.update, placemarkCollection.getName()));
        progressDialog.setProgressStyle(placemarkCollection.getPoiCount() > 0
                ? ProgressDialog.STYLE_HORIZONTAL
                : ProgressDialog.STYLE_SPINNER);
        progressDialog.show();
        Util.EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    savePlacemarkCollection();
                    final ImporterFacade importerFacade = new ImporterFacade();
                    importerFacade.setProgressDialog(progressDialog);
                    final int count = importerFacade.importPlacemarks(placemarkCollection);
                    if (count == 0) {
                        Util.showToast(getString(R.string.error_update, placemarkCollection.getName(), getString(R.string.error_no_placemark)), Toast.LENGTH_LONG);
                    } else {
                        Util.showToast(getString(R.string.update_collection_success, placemarkCollection.getName(), count), Toast.LENGTH_LONG);
                    }
                } catch (Exception e) {
                    Log.e(PlacemarkCollectionDetailFragment.class.getSimpleName(), "updatePlacemarkCollection", e);
                    Util.showToast(getString(R.string.error_update, placemarkCollection.getName(), e.getLocalizedMessage()), Toast.LENGTH_LONG);
                } finally {
                    // update placemark collection info
                    showUpdatedCollectionInfo();
                }
            }
        });
    }

    public void deletePlacemarkCollection() {
        try (final PlacemarkDao placemarkDao = PlacemarkDao.getInstance().open()) {
            placemarkDao.deleteByCollectionId(placemarkCollection.getId());
        }
        placemarkCollectionDao.delete(placemarkCollection);
    }

    public void showFileChooser(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");      //all files
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent,
                    placemarkCollection == null ? getString(R.string.collection) : placemarkCollection.getName()),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            Util.showToast("Please install a File Manager.", Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE && resultCode == Activity.RESULT_OK) {
            final Uri result = data.getData();
            if (result != null) {
                sourceText.setText(result.toString());
            }
        }
    }
}
