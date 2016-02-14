package io.github.fvasco.pinpoi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
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

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private PlacemarkCollectionDao placemarkCollectionDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemarkcollection_list);
        Util.initApplicationContext(getApplicationContext());
        placemarkCollectionDao = PlacemarkCollectionDao.getInstance().open();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createPlacemarkCollection(view.getContext(), null);
            }
        });
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
        View recyclerView = findViewById(R.id.placemarkcollection_list);
        setupRecyclerView((RecyclerView) recyclerView);
    }

    @Override
    protected void onDestroy() {
        placemarkCollectionDao.close();
        super.onDestroy();
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

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(placemarkCollectionDao.findAllPlacemarkCollection()));
    }

    private void createPlacemarkCollection(final Context context, final Uri sourceUri) {
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
                .setNegativeButton("Cancel", new DismissOnClickListener())
                .show();
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
                    .inflate(R.layout.placemarkcollection_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final PlacemarkCollection pc = mValues.get(position);
            holder.mItem = pc;
            if (Util.isEmpty(pc.getCategory())) {
                holder.mContentView.setText(pc.getName());
            } else {
                stringBuilder.setLength(0);
                stringBuilder.append(pc.getCategory()).append(" / ").append(pc.getName());
                holder.mContentView.setText(stringBuilder);
            }
            if (pc.getPoiCount() == 0) {
                holder.mContentView.setPaintFlags(holder.mContentView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                holder.mContentView.setPaintFlags(holder.mContentView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putLong(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, pc.getId());
                        PlacemarkCollectionDetailFragment fragment = new PlacemarkCollectionDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.placemarkcollection_detail_container, fragment)
                                .commit();
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
            public final View mView;
            public final TextView mContentView;
            public PlacemarkCollection mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mContentView = (TextView) view.findViewById(R.id.content);
            }
        }
    }
}
