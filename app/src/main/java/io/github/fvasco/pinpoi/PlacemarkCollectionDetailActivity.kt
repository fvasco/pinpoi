package io.github.fvasco.pinpoi

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import io.github.fvasco.pinpoi.util.DismissOnClickListener
import io.github.fvasco.pinpoi.util.Util
import io.github.fvasco.pinpoi.util.tryDismiss

/**
 * An activity representing a single Placemark Collection detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [PlacemarkCollectionListActivity].
 */
class PlacemarkCollectionDetailActivity : AppCompatActivity() {
    private var fragment: PlacemarkCollectionDetailFragment? = null
    private val PERMISSION_UPDATE = 1
    private val PERMISSION_CHOOSE_FILE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.applicationContext = applicationContext
        setContentView(R.layout.activity_placemarkcollection_detail)

        val toolbar = findViewById(R.id.detailToolbar) as Toolbar
        setSupportActionBar(toolbar)

        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
            val arguments = Bundle()
            arguments.putLong(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID,
                    intent.getLongExtra(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, 0))
            fragment = PlacemarkCollectionDetailFragment().apply {
                this.arguments = arguments
            }
            supportFragmentManager.beginTransaction().add(R.id.placemarkcollectionDetailContainer, fragment).commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    navigateUpTo(Intent(this, PlacemarkListActivity::class.java))
                    return true
                }
                renameCollection()
                return true
            }
            R.id.action_rename -> {
                renameCollection()
                return true
            }
            R.id.action_delete -> {
                deleteCollection()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * @param view required for view binding
     */
    fun openFileChooser(view: View?) {
        fragment?.let { fragment ->
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                fragment.openFileChooser(view)
            } else {
                // request permission
                ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_CHOOSE_FILE)
            }
        }
    }

    /**
     * Update placemark collection
     *
     * @param view required for view binding
     */
    fun updatePlacemarkCollection(@Suppress("UNUSED_PARAMETER") view: View?) {
        fragment?.let { fragment ->
            val permission = fragment.requiredPermissionToUpdatePlacemarkCollection
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                fragment.updatePlacemarkCollection()
            } else {
                // request permission
                ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_UPDATE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                PERMISSION_UPDATE -> updatePlacemarkCollection(null)
                PERMISSION_CHOOSE_FILE -> openFileChooser(null)
            }
        }
    }

    private fun renameCollection() {
        fragment?.let { fragment ->
            val editText = EditText(baseContext)
            editText.setText(fragment.placemarkCollection.name)
            AlertDialog.Builder(this)
                    .setTitle(R.string.action_rename)
                    .setView(editText).setPositiveButton(R.string.ok) { dialog, _ ->
                        dialog.tryDismiss()
                        fragment.renamePlacemarkCollection(editText.text.toString())
                    }
                    .setNegativeButton(R.string.cancel, DismissOnClickListener)
                    .show()
        }
    }

    private fun deleteCollection() {
        fragment?.let { fragment ->
            AlertDialog.Builder(this)
                    .setTitle(R.string.action_delete)
                    .setMessage(R.string.delete_placemark_collection_confirm)
                    .setPositiveButton(R.string.ok) { dialog, _ ->
                        dialog.tryDismiss()
                        fragment.deletePlacemarkCollection()
                        onBackPressed()
                    }
                    .setNegativeButton(R.string.cancel, DismissOnClickListener)
                    .show()
        }
    }

}
