package io.github.fvasco.pinpoi.util

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.util.Log
import io.github.fvasco.pinpoi.PlacemarkDetailActivity
import io.github.fvasco.pinpoi.model.PlacemarkBase
import java.io.*
import java.util.*
import kotlin.math.min

/**
 * Location related utility

 * @author Francesco Vasco
 */
class LocationUtil(private val context: Context) {

    /**
     * Store resolved address
     */
    private val ADDRESS_CACHE = LinkedHashMap<Coordinates, String>(ADDRESS_CACHE_SIZE * 2, .75f, true)
    private val addressCacheFile by lazy { File(context.cacheDir, "addressCache") }

    // avoid geocoder cache to reset it on initialization error
    val geocoder: Geocoder?
        get() =
            if (Geocoder.isPresent())
                Geocoder(context)
            else null

    /**
     * Get address and call optional addressConsumer in main looper
     */
    fun getAddressStringAsync(
        coordinates: Coordinates,
        addressConsumer: ((String?) -> Unit)?
    ) = doAsync {
        var addressString: String? = synchronized(ADDRESS_CACHE) {
            if (ADDRESS_CACHE.isEmpty()) restoreAddressCache()
            ADDRESS_CACHE[coordinates]
        }
        if (addressString == null) {
            val addresses = try {
                geocoder?.getFromLocation(coordinates.latitude.toDouble(), coordinates.longitude.toDouble(), 1)
                    ?: listOf()
            } catch (e: Exception) {
                listOf<Address>()
            }

            if (addresses.isNotEmpty()) {
                addressString = toString(addresses.first())
                // save result in cache
                synchronized(ADDRESS_CACHE) {
                    ADDRESS_CACHE[coordinates] = addressString
                    if (Thread.interrupted()) {
                        throw InterruptedException()
                    }
                    saveAddressCache()
                }
            }
        }
        if (addressConsumer != null) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            runOnUiThread { addressConsumer(addressString) }
        }
    }

    /**
     * Open external map app

     * @param placemark       placemark to open
     * *
     * @param forceAppChooser if true show always app chooser
     */
    fun openExternalMap(placemark: PlacemarkBase, forceAppChooser: Boolean) {
        try {
            // use simple intent (no description) for max compatibility
            var intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${placemark.coordinates}?q=${placemark.coordinates}"))
            if (forceAppChooser) {
                intent = Intent.createChooser(intent, placemark.name)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(PlacemarkDetailActivity::class.java.simpleName, "Error on map click", e)
            context.showToast(e)
        }
    }

    private fun restoreAddressCache() {
        assertDebug(Thread.holdsLock(ADDRESS_CACHE))
        if (addressCacheFile.canRead()) {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(addressCacheFile))).use { inputStream ->
                    // first item is entry count
                    repeat(inputStream.readShort().toInt()) {
                        val latitude = inputStream.readFloat()
                        val longitude = inputStream.readFloat()
                        val address = inputStream.readUTF()
                        ADDRESS_CACHE[Coordinates(latitude, longitude)] = address
                    }
                }
            } catch (e: IOException) {
                Log.w(LocationUtil::class.java.simpleName, e)
                //noinspection ResultOfMethodCallIgnored
                addressCacheFile.delete()
            }

        }
    }

    private fun saveAddressCache() {
        assertDebug(Thread.holdsLock(ADDRESS_CACHE))
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(addressCacheFile))).use { outputStream ->
                // first item is entry count
                outputStream.writeShort(min(ADDRESS_CACHE_SIZE, ADDRESS_CACHE.size))

                val iterator = ADDRESS_CACHE.entries.iterator()
                // if (ADDRESS_CACHE.size > ADDRESS_CACHE_SIZE) skip entries
                repeat(ADDRESS_CACHE.size - ADDRESS_CACHE_SIZE) {
                    iterator.next()
                }
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    val coordinates = entry.key
                    outputStream.writeFloat(coordinates.latitude)
                    outputStream.writeFloat(coordinates.longitude)
                    outputStream.writeUTF(entry.value)
                }
            }
        } catch (e: IOException) {
            Log.w(LocationUtil::class.java.simpleName, e)
            //noinspection ResultOfMethodCallIgnored
            addressCacheFile.delete()
        }
    }

    companion object {
        private const val ADDRESS_CACHE_SIZE = 512

        fun newLocation(latitude: Double, longitude: Double): Location {
            val location = Location(LocationUtil::class.java.simpleName)
            location.latitude = latitude
            location.longitude = longitude
            location.accuracy = 0f
            location.time = System.currentTimeMillis()
            return location
        }

        /**
         * Convert an [Address] to address string
         */
        fun toString(address: Address): String {
            val separator = ", "
            return when {
                address.maxAddressLineIndex == 0 -> address.getAddressLine(0)
                address.maxAddressLineIndex > 0 -> {
                    val stringBuilder = StringBuilder(address.getAddressLine(0))
                    for (i in 1..address.maxAddressLineIndex) {
                        stringBuilder.append(separator).append(address.getAddressLine(i))
                    }
                    stringBuilder.toString()
                }
                else -> try {
                    val stringBuilder = StringBuilder()
                    append(address.featureName, separator, stringBuilder)
                    append(address.locality, separator, stringBuilder)
                    append(address.adminArea, separator, stringBuilder)
                    append(address.countryCode, separator, stringBuilder)
                    if (stringBuilder.isEmpty())
                        Coordinates(address.latitude.toFloat(), address.longitude.toFloat()).toString()
                    else
                        stringBuilder.toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                    address.toString()
                }
            }
        }
    }
}
