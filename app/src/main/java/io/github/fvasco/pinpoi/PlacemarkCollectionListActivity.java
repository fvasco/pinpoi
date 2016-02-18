package io.github.fvasco.pinpoi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.TextView;

import java.util.List;

import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao;
import io.github.fvasco.pinpoi.model.PlacemarkCollection;
import io.github.fvasco.pinpoi.util.DismissOnClickListener;
import io.github.fvasco.pinpoi.util.Util;

/**
 * An activity representing a list of Placemark Collections. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link PlacemarkCollectionDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class PlacemarkCollectionListActivity extends AppCompatActivity {

    private static final int PERMISSION_UPDATE = 1;
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    /* only for two pane view */
    private PlacemarkCollectionDetailFragment fragment;
    /* only for two pane view */
    private FloatingActionButton fabUpdate;
    private PlacemarkCollectionDao placemarkCollectionDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemarkcollection_list);
        Util.initApplicationContext(getApplicationContext());
        placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();
        fabUpdate = (FloatingActionButton) findViewById(R.id.fabUpdate);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (findViewById(R.id.placemarkcollection_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        // load intent parameters to create a new collection
        final Uri intentUri = getIntent().getData();
        if (intentUri != null) {
            createPlacemarkCollection(getBaseContext(), intentUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupRecyclerView();
    }

    @Override
    protected void onDestroy() {
        placemarkCollectionDao.close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mTwoPane) {
            // Inflate the menu; this adds items to the action bar if it is present.
            getMenuInflater().inflate(R.menu.menu_collection, menu);
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. Use NavUtils to allow users
                // to navigate up one level in the application structure. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.action_rename:
                renameCollection();
                return true;
            case R.id.action_delete:
                deleteCollection();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.placemarkcollection_list);
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(placemarkCollectionDao.findAllPlacemarkCollection()));
    }

    public void createPlacemarkCollection(final View view) {
        createPlacemarkCollection(view.getContext(), null);
    }

    private void createPlacemarkCollection(@NonNull final Context context, @Nullable final Uri sourceUri) {
        // Set an EditText view to get user input
        final EditText input = new EditText(this);
        if (sourceUri != null) {
            String fileName = sourceUri.getLastPathSegment();
            final String extension = MimeTypeMap.getFileExtensionFromUrl(sourceUri.toString());
            if (!Util.isEmpty(extension)) {
                fileName = fileName.substring(0, fileName.length() - extension.length() - 1);
            }
            input.setText(fileName);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_placemarkcollection_detail))
                .setMessage(getString(R.string.placemark_collection_name))
                .setView(input)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            String placemarkCollectionName = input.getText().toString();
                            final PlacemarkCollection placemarkCollection = new PlacemarkCollection();
                            placemarkCollection.setName(placemarkCollectionName);
                            placemarkCollection.setSource(sourceUri == null ? "" : sourceUri.toString());
                            placemarkCollection.setCategory(sourceUri == null ? "" : sourceUri.getHost());
                            placemarkCollectionDao.insert(placemarkCollection);

                            // edit placemark collection
                            dialog.dismiss();
                            Intent intent = new Intent(context, PlacemarkCollectionDetailActivity.class);
                            intent.putExtra(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, placemarkCollection.getId());
                            startActivity(intent);
                        } catch (final IllegalArgumentException e) {
                            // cannot insert collection
                            Util.showToast(e);
                        }
                    }
                })
                .setNegativeButton("Cancel", DismissOnClickListener.INSTANCE)
                .show();
    }

    public void updatePlacemarkCollection(final View view) {
        if (fragment != null) {
            final String permission = fragment.getRequiredPermissionToUpdatePlacemarkCollection();
            if (ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED) {
                fragment.updatePlacemarkCollection();
            } else {
                // request permission
                ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_UPDATE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_UPDATE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updatePlacemarkCollection(null);
        }
    }

    private void renameCollection() {
        if (fragment != null) {
            final EditText editText = new EditText(getBaseContext());
            editText.setText(fragment.getPlacemarkCollection().getName());
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_rename)
                    .setView(editText)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            fragment.renamePlacemarkCollection(editText.getText().toString());
                            setupRecyclerView();
                        }
                    })
                    .setNegativeButton(R.string.no, DismissOnClickListener.INSTANCE)
                    .show();
        }
    }

    private void deleteCollection() {
        if (fragment != null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_delete)
                    .setMessage(R.string.delete_placemark_collection_confirm)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            fragment.deletePlacemarkCollection();
                            fabUpdate.setVisibility(View.GONE);
                            getSupportFragmentManager().beginTransaction()
                                    .remove(fragment)
                                    .commit();
                            fragment = null;
                            setupRecyclerView();
                        }
                    })
                    .setNegativeButton(R.string.no, DismissOnClickListener.INSTANCE)
                    .show();
        }
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<PlacemarkCollection> mValues;
        private final StringBuilder stringBuilder = new StringBuilder();

        public SimpleItemRecyclerViewAdapter(List<PlacemarkCollection> items) {
            mValues = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final PlacemarkCollection pc = mValues.get(position);
            holder.mItem = pc;
            if (Util.isEmpty(pc.getCategory())) {
                holder.view.setText(pc.getName());
            } else {
                stringBuilder.setLength(0);
                stringBuilder.append(pc.getCategory()).append(" / ").append(pc.getName());
                holder.view.setText(stringBuilder);
            }
            if (pc.getPoiCount() == 0) {
                holder.view.setPaintFlags(holder.view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                holder.view.setPaintFlags(holder.view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            holder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putLong(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, pc.getId());
                        fragment = new PlacemarkCollectionDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.placemarkcollection_detail_container, fragment)
                                .commit();
                        // show update button
                        fabUpdate.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                fragment.updatePlacemarkCollection();
                            }
                        });
                        fabUpdate.setVisibility(View.VISIBLE);
                    } else {
                        Context context = view.getContext();
                        Intent intent = new Intent(context, PlacemarkCollectionDetailActivity.class);
                        intent.putExtra(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, holder.mItem.getId());
                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView view;
            public PlacemarkCollection mItem;

            public ViewHolder(View view) {
                super(view);
                this.view = (TextView) view.findViewById(android.R.id.text1);
            }
        }
    }
}
