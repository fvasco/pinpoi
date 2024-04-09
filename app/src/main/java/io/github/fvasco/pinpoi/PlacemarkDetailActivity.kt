package io.github.fvasco.pinpoi

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.databinding.ActivityPlacemarkDetailBinding

/**
 * An activity representing a single Placemark detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [PlacemarkListActivity].
 */
class PlacemarkDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlacemarkDetailBinding
    private var placemarkId: Long = 0
    private lateinit var fragment: PlacemarkDetailFragment
    private lateinit var placemarkDao: PlacemarkDao
    private lateinit var preferences: SharedPreferences

    /**
     * Placemark id for swipe
     */
    private var placemarkIdArray: LongArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacemarkDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        placemarkDao = PlacemarkDao(applicationContext)
        placemarkDao.open()
        val mapFab = findViewById<FloatingActionButton>(R.id.fabMap)
        val toolbar = findViewById<Toolbar>(R.id.detailToolbar)
        setSupportActionBar(toolbar)

        preferences = getPreferences(Context.MODE_PRIVATE)
        placemarkId = intent.getLongExtra(
            PlacemarkDetailFragment.ARG_PLACEMARK_ID,
            preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0)
        )
        preferences.edit().putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId).apply()
        placemarkIdArray = intent.getLongArrayExtra(ARG_PLACEMARK_LIST_ID)

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
            arguments.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
            fragment = PlacemarkDetailFragment()
            fragment.arguments = arguments
            supportFragmentManager.beginTransaction().add(R.id.placemarkDetailContainer, fragment)
                .commit()
        } else {
            fragment = supportFragmentManager.fragments[0] as PlacemarkDetailFragment
        }
        mapFab.setOnLongClickListener(fragment.longClickListener)
    }

    override fun onStart() {
        super.onStart()
        resetStarFabIcon()
    }


    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        placemarkId = savedInstanceState.getLong(
            PlacemarkDetailFragment.ARG_PLACEMARK_ID,
            preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
    }

    override fun onDestroy() {
        super.onDestroy()
        placemarkDao.close()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetStarFabIcon()
    }

    private fun resetStarFabIcon() {
        fragment.resetStarFabIcon(binding.fabStar)
    }

    fun onStarClick(view: View) {
        fragment.onStarClick(binding.fabStar)
    }

    fun onMapClick(view: View) {
        fragment.onMapClick(view)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            // navigateUpTo(Intent(this, PlacemarkListActivity::class.java))
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val ARG_PLACEMARK_LIST_ID = "placemarkListId"
    }
}
