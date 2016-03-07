package io.github.fvasco.pinpoi;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import io.github.fvasco.pinpoi.util.DismissOnClickListener;
import io.github.fvasco.pinpoi.util.Util;

/**
 * An activity representing a single Placemark Collection detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link PlacemarkCollectionListActivity}.
 */
public class PlacemarkCollectionDetailActivity extends AppCompatActivity {

    private static final int PERMISSION_UPDATE = 1;
    private PlacemarkCollectionDetailFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placemarkcollection_detail);
        Util.initApplicationContext(getApplicationContext());

        Toolbar toolbar = (Toolbar) findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);

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
            arguments.putLong(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID,
                    getIntent().getLongExtra(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, 0));
            fragment = new PlacemarkCollectionDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.placemarkcollection_detail_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_collection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    navigateUpTo(new Intent(this, PlacemarkListActivity.class));
                    return true;
                }
            case R.id.action_rename:
                renameCollection();
                return true;
            case R.id.action_delete:
                deleteCollection();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
                            onBackPressed();
                        }
                    })
                    .setNegativeButton(R.string.no, DismissOnClickListener.INSTANCE)
                    .show();
        }
    }
}
