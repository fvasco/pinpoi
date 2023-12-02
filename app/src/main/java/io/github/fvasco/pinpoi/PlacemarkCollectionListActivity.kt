package io.github.fvasco.pinpoi

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.databinding.ActivityPlacemarkcollectionListBinding
import io.github.fvasco.pinpoi.model.PlacemarkCollection
import io.github.fvasco.pinpoi.util.DismissOnClickListener
import io.github.fvasco.pinpoi.util.showToast
import io.github.fvasco.pinpoi.util.tryDismiss

/**
 * An activity representing a list of Placemark Collections. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [PlacemarkCollectionDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class PlacemarkCollectionListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlacemarkcollectionListBinding
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacemarkcollectionListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        placemarkCollectionDao = PlacemarkCollectionDao(applicationContext)
        placemarkCollectionDao.open()

        binding.toolbar.title = title

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

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

    private fun setupRecyclerView() {
        val recyclerView = binding.placemarkcollectionList.placemarkcollectionList
        recyclerView.adapter =
            SimpleItemRecyclerViewAdapter(placemarkCollectionDao.findAllPlacemarkCollection())
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

    inner class SimpleItemRecyclerViewAdapter(private val mValues: List<PlacemarkCollection>) :
        RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder>() {
        private val stringBuilder = StringBuilder()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
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
                holder.view.paintFlags =
                    holder.view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            holder.view.setOnClickListener { view ->
                val context = view.context
                val intent = Intent(context, PlacemarkCollectionDetailActivity::class.java)
                intent.putExtra(
                    PlacemarkCollectionDetailFragment.ARG_PLACEMARK_COLLECTION_ID,
                    holder.mItem!!.id
                )
                context.startActivity(intent)
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
}
