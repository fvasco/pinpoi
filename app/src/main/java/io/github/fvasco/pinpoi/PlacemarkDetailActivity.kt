package io.github.fvasco.pinpoi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.util.OnSwipeTouchListener
import io.github.fvasco.pinpoi.util.Util
import kotlinx.android.synthetic.main.activity_placemark_detail.*

/**
 * An activity representing a single Placemark detail screen. This
 * activity is only used narrow width devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [PlacemarkListActivity].
 */
class PlacemarkDetailActivity : AppCompatActivity(), OnSwipeTouchListener.SwipeTouchListener {
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
        Util.applicationContext = applicationContext
        setContentView(R.layout.activity_placemark_detail)
        placemarkDao = PlacemarkDao.instance
        placemarkDao.open()
        val mapFab = findViewById(R.id.fabMap) as FloatingActionButton
        val toolbar = findViewById(R.id.detailToolbar) as Toolbar
        setSupportActionBar(toolbar)
        placemarkDetailContainer.setOnTouchListener(OnSwipeTouchListener(this, placemarkDetailContainer.context))

        preferences = getPreferences(Context.MODE_PRIVATE)
        placemarkId = intent.getLongExtra(PlacemarkDetailFragment.ARG_PLACEMARK_ID,
                preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0))
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
            supportFragmentManager.beginTransaction().add(R.id.placemarkDetailContainer, fragment).commit()
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
        placemarkId = savedInstanceState.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID,
                preferences.getLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, 0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        placemarkDao.close()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        resetStarFabIcon()
    }

    private fun resetStarFabIcon() {
        fragment.resetStarFabIcon(fabStar)
    }

    fun onStarClick(view: View) {
        fragment.onStarClick(fabStar)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                navigateUpTo(Intent(this, PlacemarkListActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSwipe(direction: Boolean) {
        placemarkIdArray?.let { placemarkIdArray ->
            var i = 0
            while (placemarkIdArray[i] != placemarkId && i < placemarkIdArray.size) {
                ++i
            }
            //noinspection PointlessBooleanExpression
            if (direction == OnSwipeTouchListener.SWIPE_LEFT) {
                ++i
            } else {
                --i
            }
            if (i >= 0 && i < placemarkIdArray.size) {
                placemarkId = placemarkIdArray[i]
                fragment.placemark = placemarkDao.getPlacemark(placemarkId)
                preferences.edit().putLong(PlacemarkDetailFragment.ARG_PLACEMARK_ID, placemarkId).apply()
                resetStarFabIcon()
            }
        }
    }

    companion object {
        const val ARG_PLACEMARK_LIST_ID = "placemarkListId"
    }
}
