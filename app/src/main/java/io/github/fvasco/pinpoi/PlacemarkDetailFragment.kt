package io.github.fvasco.pinpoi

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdSize
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.openlocationcode.OpenLocationCode
import io.github.fvasco.pinpoi.dao.PlacemarkCollectionDao
import io.github.fvasco.pinpoi.dao.PlacemarkDao
import io.github.fvasco.pinpoi.databinding.PlacemarkDetailBinding
import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.model.PlacemarkAnnotation
import io.github.fvasco.pinpoi.util.LocationUtil
import io.github.fvasco.pinpoi.util.doAsync
import io.github.fvasco.pinpoi.util.initAdMob
import io.github.fvasco.pinpoi.util.isHtml
import io.github.fvasco.pinpoi.util.showToast
import io.github.fvasco.pinpoi.util.tryDismiss
import java.util.concurrent.Future

/**
 * A fragment representing a single Placemark detail screen.
 * This fragment is either contained in a [PlacemarkListActivity]
 * in two-pane mode (on tablets) or a [PlacemarkDetailActivity]
 * on handsets.
 */
class PlacemarkDetailFragment : Fragment() {

    private lateinit var binding: PlacemarkDetailBinding

    // show coordinates
    // show address
    // show placemark collection details
    var placemark: Placemark? = null
        set(value) {
            saveData()
            field = value
            Log.i(PlacemarkDetailFragment::class.java.simpleName, "open placemark ${value?.id}")
            placemarkAnnotation =
                if (value == null) null else placemarkDao.loadPlacemarkAnnotation(value)
            val placemarkCollection =
                if (value == null) null else placemarkCollectionDao.findPlacemarkCollectionById(
                    value.collectionId
                )
            if (value != null) {
                preferences?.edit { putLong(ARG_PLACEMARK_ID, value.id) }
            }

            binding.placemarkNameText.text = value?.name
            binding.placemarkDetailText.text = when {
                value == null -> null
                value.description.isBlank() -> value.name
                value.description.isHtml() ->
                    "<p>${Html.escapeHtml(value.name)}</p>${value.description}".let { html ->
                        Html.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    }

                else -> "${value.name}\n\n${value.description}"
            }
            binding.noteText.setText(placemarkAnnotation?.note)
            binding.coordinatesText.text =
                if (value == null) null
                else getString(
                    R.string.location,
                    Location.convert(
                        value.coordinates.latitude.toDouble(),
                        Location.FORMAT_DEGREES
                    ),
                    Location.convert(
                        value.coordinates.longitude.toDouble(),
                        Location.FORMAT_DEGREES
                    )
                )
            binding.plusCodeText.visibility = View.GONE
            if (value != null) {
                val plusCode = OpenLocationCode.encode(
                    value.coordinates.latitude.toDouble(),
                    value.coordinates.longitude.toDouble()
                )
                binding.plusCodeText.text = "Plus Code: $plusCode"
                binding.plusCodeText.visibility = View.VISIBLE
            }
            searchAddressFuture?.cancel(true)
            binding.addressText.text = null
            binding.addressText.visibility = View.GONE
            if (value != null) {
                searchAddressFuture =
                    LocationUtil(requireContext()).getAddressStringAsync(value.coordinates) { address ->
                        if (!address.isNullOrEmpty()) {
                            binding.addressText.visibility = View.VISIBLE
                            binding.addressText.text = address
                        }
                    }
            }
            if (placemarkCollection == null) {
                binding.collectionDescriptionTitle.visibility = View.GONE
                binding.collectionDescriptionText.visibility = View.GONE
            } else {
                binding.collectionDescriptionTitle.visibility = View.VISIBLE
                binding.collectionDescriptionText.visibility = View.VISIBLE
                binding.collectionDescriptionTitle.text = placemarkCollection.name
                binding.collectionDescriptionText.text = placemarkCollection.description
            }
        }
    val longClickListener: View.OnLongClickListener = View.OnLongClickListener {
        LocationUtil(requireContext()).openExternalMap(placemark!!, true)
        true
    }
    private lateinit var placemarkDao: PlacemarkDao
    private lateinit var placemarkCollectionDao: PlacemarkCollectionDao
    private var placemarkAnnotation: PlacemarkAnnotation? = null
    private var preferences: SharedPreferences? = null
    private var searchAddressFuture: Future<Unit>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = PlacemarkDetailBinding.inflate(layoutInflater, container, false)
        requireContext().initAdMob(
            adViewContainer = binding.adViewContainer,
            adUnitId = "ca-app-pub-9366320490821807/8666389355",
            adSize = AdSize.LARGE_BANNER
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences =
            activity?.getSharedPreferences(
                PlacemarkDetailFragment::class.java.simpleName,
                Context.MODE_PRIVATE
            )
        placemarkDao = PlacemarkDao(requireContext())
        placemarkCollectionDao = PlacemarkCollectionDao(requireContext())
        placemarkDao.open()
        placemarkCollectionDao.open()

        val id = savedInstanceState?.getLong(ARG_PLACEMARK_ID)
            ?: arguments?.getLong(
                ARG_PLACEMARK_ID, preferences?.getLong(ARG_PLACEMARK_ID, 0)
                    ?: 0
            ) ?: 0
        preferences?.edit { putLong(ARG_PLACEMARK_ID, id) }
    }

    override fun onPause() {
        saveData()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        placemarkDao.close()
        placemarkCollectionDao.close()
    }

    override fun onStart() {
        super.onStart()
        // By default, these links will appear but not respond to user input.
        binding.placemarkDetailText.movementMethod = LinkMovementMethod.getInstance()
        placemark = placemarkDao.getPlacemark(preferences?.getLong(ARG_PLACEMARK_ID, 0) ?: 0)
    }

    override fun onResume() {
        super.onResume()
        binding.shareButton.setOnClickListener { onShare() }
        resetStarFabIcon(requireActivity().findViewById(R.id.fabStar) as FloatingActionButton)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        placemark?.let { placemark ->
            outState.putLong(ARG_PLACEMARK_ID, placemark.id)
        }
    }

    fun onMapClick(view: View) {
        placemark?.apply {
            LocationUtil(requireContext()).openExternalMap(this, false)
        }
    }

    fun onShare() {
        val placemark = placemark ?: return
        val view = view ?: return
        val places = mutableListOf<String?>(placemark.name)
        if (placemark.description.length in 1..100)
            places.add(placemark.description)
        places.add(placemarkAnnotation?.note)
        places.add(binding.addressText.text?.toString())
        with(placemark.coordinates) {
            places.add(this.toString())
            places.add(
                Location.convert(latitude.toDouble(), Location.FORMAT_DEGREES)
                        + ' '
                        + Location.convert(longitude.toDouble(), Location.FORMAT_DEGREES)
            )
            places.add(
                Location.convert(latitude.toDouble(), Location.FORMAT_MINUTES)
                        + ' '
                        + Location.convert(longitude.toDouble(), Location.FORMAT_MINUTES)
            )
            places.add(
                Location.convert(latitude.toDouble(), Location.FORMAT_SECONDS)
                        + ' '
                        + Location.convert(longitude.toDouble(), Location.FORMAT_SECONDS)
            )
            places.add(OpenLocationCode.encode(latitude.toDouble(), longitude.toDouble()))
        }
        // remove empty lines
        places.removeAll { it.isNullOrBlank() }

        // open chooser and share
        AlertDialog.Builder(view.context)
            .setTitle(getString(R.string.share))
            .setItems(places.toTypedArray()) { dialog, which ->
                dialog.tryDismiss()
                try {
                    val text = places[which]
                    var intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, text)
                    intent = Intent.createChooser(intent, text)
                    requireContext().startActivity(intent)
                } catch (e: Exception) {
                    Log.e(PlacemarkDetailActivity::class.java.simpleName, "Error on map click", e)
                    context?.showToast(e)
                }
            }
            .show()
    }

    fun resetStarFabIcon(starFab: FloatingActionButton) {
        val drawable = if (placemarkAnnotation?.flagged == true)
            R.drawable.ic_bookmark_white
        else
            R.drawable.ic_bookmark_border_white
        starFab.setImageDrawable(
            resources.getDrawable(drawable, requireActivity().baseContext.theme)
        )
    }

    fun onStarClick(starFab: FloatingActionButton) {
        placemarkAnnotation?.flagged = !placemarkAnnotation!!.flagged
        resetStarFabIcon(starFab)
        doAsync {
            saveData()
        }
    }

    private fun saveData() {
        // save previous annotation
        placemarkAnnotation?.apply {
            note = binding.noteText.text.toString()
            placemarkDao.update(this)
        }
    }

    companion object {
        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_PLACEMARK_ID = "placemarkId"
    }

}
