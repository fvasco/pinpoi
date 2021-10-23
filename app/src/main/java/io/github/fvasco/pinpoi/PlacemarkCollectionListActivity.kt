package io.github.fvasco.pinpoi

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.DismissOnClickListener
import io.github.fvasco.pinpoi.util.showToast
import io.github.fvasco.pinpoi.util.tryDismiss
import kotlinx.android.synthetic.main.activity_placemarkcollection_list.*

/**
 * An activity representing a list of Placemark Collections. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [PlacemarkCollectionDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class PlacemarkCollectionListActivity : AppCompatActivity() {

    /**
     * Whether the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane: Boolean = false

    /* only for two pane view */
    private var fragment: PlacemarkCollectionDetailFragment? = null

    /* only for two pane view */
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placemarkcollection_list)
        placemarkCollectionDao = PlacemarkCollectionDao(applicationContext)
        placemarkCollectionDao.open()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = title

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        if (findViewById<FrameLayout>(R.id.placemarkcollectionDetailContainer) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true
        }

        // load intent parameters to create a new collection
        intent.data?.let { intentUri ->
            createPlacemarkCollection(baseContext, intentUri)
        }
    }

    override fun onResume() {
        super.onResume()
        setupRecyclerView()
    }

    override fun onDestroy() {
        placemarkCollectionDao.close()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mTwoPane) {
            // Inflate the menu; this adds items to the action bar if it is present.
            menuInflater.inflate(R.menu.menu_collection, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. Use NavUtils to allow users
                // to navigate up one level in the application structure. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                NavUtils.navigateUpFromSameTask(this)
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

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.placemarkcollectionList)
        recyclerView.adapter = SimpleItemRecyclerViewAdapter(placemarkCollectionDao.findAllPlacemarkCollection())
    }

    fun createPlacemarkCollection(view: View) {
        createPlacemarkCollection(view.context, null)
    }

    private fun createPlacemarkCollection(context: Context, sourceUri: Uri?) {
        // Set an EditText view to get user input
        val input = EditText(this)
        if (sourceUri != null) {
            var fileName = sourceUri.lastPathSegment
            if (fileName != null) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(sourceUri.toString())
                if (!extension.isNullOrEmpty()) {
                    fileName = fileName.substring(0, fileName.length - extension.length - 1)
                }
                input.setText(fileName)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_placemarkcollection_detail))
            .setMessage(getString(R.string.placemark_collection_name))
            .setView(input).setPositiveButton(R.string.ok) { dialog, _ ->
                val placemarkCollectionName = input.text.toString().trim()
                if (placemarkCollectionName.isBlank()) {
                    Toast.makeText(context, R.string.validation_error, Toast.LENGTH_SHORT).show()
                } else try {
                    val placemarkCollection = PlacemarkCollection()
                    placemarkCollection.name = placemarkCollectionName
                    placemarkCollection.source = sourceUri?.toString() ?: ""
                    placemarkCollection.category = sourceUri?.host ?: ""
                    placemarkCollectionDao.insert(placemarkCollection)

                    // edit placemark collection
                    dialog.tryDismiss()
                    val intent = Intent(context, PlacemarkCollectionDetailActivity::class.java)
                    intent.putExtra(
                        PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID,
                        placemarkCollection.id
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    // cannot insert collection
                    showToast(e)
                }
            }
            .setNegativeButton("Cancel", DismissOnClickListener)
            .show()
    }

    fun openFileChooser(view: View?) {
        fragment?.openFileChooser()
    }

    fun pasteUrl(view: View?) {
        fragment?.pasteUrl(view)
    }

    fun updatePlacemarkCollection() {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                PERMISSION_UPDATE -> updatePlacemarkCollection()
            }
        }
    }

    private fun renameCollection() {
        fragment?.let { fragment ->
            val editText = EditText(baseContext)
            editText.setText(fragment.placemarkCollection.name)
            AlertDialog.Builder(this)
                .setTitle(R.string.action_rename)
                .setView(editText)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.tryDismiss()
                    fragment.renamePlacemarkCollection(editText.text.toString())
                    setupRecyclerView()
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
                    fabUpdate.hide()
                    supportFragmentManager.beginTransaction().remove(fragment).commit()
                    this.fragment = null
                    setupRecyclerView()
                }
                .setNegativeButton(R.string.cancel, DismissOnClickListener)
                .show()
        }
    }

    inner class SimpleItemRecyclerViewAdapter(private val mValues: List<PlacemarkCollection>) :
        RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val stringBuilder = StringBuilder()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pc = mValues[position]
            holder.mItem = pc
            if (pc.category.isEmpty()) {
                holder.view.text = pc.name
            } else {
                stringBuilder.setLength(0)
                stringBuilder.append(pc.category).append(" / ").append(pc.name)
                holder.view.text = stringBuilder
            }
            if (pc.poiCount == 0) {
                holder.view.paintFlags = holder.view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.view.paintFlags = holder.view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            holder.view.setOnClickListener { view ->
                if (mTwoPane) {
                    val arguments = Bundle()
                    arguments.putLong(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, pc.id)
                    fragment = PlacemarkCollectionDetailFragment().apply {
                        this.arguments = arguments
                        supportFragmentManager.beginTransaction().replace(R.id.placemarkcollectionDetailContainer, this)
                            .commit()
                        this@PlacemarkCollectionListActivity.fabUpdate.setOnClickListener { this.updatePlacemarkCollection() }
                    }
                    // show update button
                    fabUpdate.show()
                } else {
                    val context = view.context
                    val intent = Intent(context, PlacemarkCollectionDetailActivity::class.java)
                    intent.putExtra(PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID, holder.mItem!!.id)
                    context.startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int {
            return mValues.size
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val view: TextView = view.findViewById(android.R.id.text1) as TextView
            var mItem: PlacemarkCollection? = null
        }
    }

    companion object {
        private const val PERMISSION_UPDATE = 1
    }
}
