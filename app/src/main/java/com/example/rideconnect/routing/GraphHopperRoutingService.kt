package com.example.rideconnect.routing

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.rideconnect.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)


data class GeocodingResult(
    val displayName: String,
    val point: GeoPoint,
    val name: String = "",
    val city: String = "",
    val county: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = ""
)


data class RouteOption(
    val routeIndex: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val points: List<GeoPoint>
) {
    val distanceKm: Double
        get() = distanceMeters / 1000.0

    val durationMinutes: Long
        get() = durationMillis / 60000L
}

class GraphHopperRoutingService(context: Context) {

    companion object {
        private const val GEOCODING_URL =
            "https://graphhopper.com/api/1/geocode"

        private const val ROUTING_URL =
            "https://graphhopper.com/api/1/route"

        // Published GraphHopper credit estimates.
        private const val GEOCODING_CREDITS = 0.3
        private const val ALTERNATIVE_ROUTE_CREDITS = 2.0

        // Mauritius map centre used to bias location searches.
        private const val MAURITIUS_LATITUDE = -20.20
        private const val MAURITIUS_LONGITUDE = 57.50
    }

    private val apiKey: String =
        BuildConfig.GRAPHHOPPER_API_KEY.trim()

    private val creditManager = DailyCreditManager(context)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Searches for places in Mauritius.
     *
     * Example:
     * geocodeMauritius("Ebene") { result -> ... }
     *
     * The callback is delivered on the main thread.
     */
    fun geocodeMauritius(
        query: String,
        callback: (Result<List<GeocodingResult>>) -> Unit
    ) {
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            deliver(
                callback,
                Result.failure(
                    IllegalArgumentException("Please enter a location.")
                )
            )
            return
        }

        if (!hasGeocodingApiKey(callback)) return

        val reservation =
            creditManager.reserveCredits(GEOCODING_CREDITS)

        if (reservation == null) {
            deliver(
                callback,
                Result.failure(
                    IllegalStateException(
                        "RideConnect's daily 400-credit safeguard " +
                                "has been reached. Try again after the daily reset."
                    )
                )
            )
            return
        }

        val searchQuery =
            if (cleanQuery.contains("Mauritius", ignoreCase = true)) {
                cleanQuery
            } else {
                "$cleanQuery, Mauritius"
            }

        val url = GEOCODING_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", searchQuery)
            .addQueryParameter("locale", "en")
            .addQueryParameter("limit", "5")
            .addQueryParameter("provider", "default")
            .addQueryParameter(
                "point",
                "$MAURITIUS_LATITUDE,$MAURITIUS_LONGITUDE"
            )
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    // Keep the reservation because the request may
                    // have reached GraphHopper before the connection failed.
                    deliver(
                        callback,
                        Result.failure(
                            IOException(
                                "Location search failed: ${e.message}",
                                e
                            )
                        )
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use { res ->

                        recordServerCredits(res, reservation)

                        val body = res.body?.string().orEmpty()

                        if (!res.isSuccessful) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "GraphHopper location search failed " +
                                                "(HTTP ${res.code}): $body"
                                    )
                                )
                            )
                            return
                        }

                        try {
                            val results = parseGeocodingResults(body)

                            if (results.isEmpty()) {
                                deliver(
                                    callback,
                                    Result.failure(
                                        NoSuchElementException(
                                            "No matching Mauritius locations found. " +
                                                    "Try a more specific location name."
                                        )
                                    )
                                )
                            } else {
                                deliver(
                                    callback,
                                    Result.success(results)
                                )
                            }
                        } catch (e: Exception) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "Could not read location results: " +
                                                e.message,
                                        e
                                    )
                                )
                            )
                        }
                    }
                }
            }
        )
    }



    /**
     * Reverse geocodes coordinates to identify the actual location.
     *
     * Used to verify locations selected directly on the map.
     * The callback is delivered on the main thread.
     */
    fun reverseGeocodeMauritius(
        point: GeoPoint,
        callback: (Result<List<GeocodingResult>>) -> Unit
    ) {

        if (!isValidPoint(point)) {
            deliver(
                callback,
                Result.failure(
                    IllegalArgumentException(
                        "Invalid coordinates supplied for reverse geocoding."
                    )
                )
            )
            return
        }

        // Restrict reverse geocoding to the approximate
        // main-island bounding area of Mauritius.
        if (
            point.latitude !in -20.65..-19.95 ||
            point.longitude !in 57.25..57.85
        ) {
            deliver(
                callback,
                Result.failure(
                    IllegalArgumentException(
                        "Please select a location within Mauritius."
                    )
                )
            )
            return
        }

        if (!hasGeocodingApiKey(callback)) return

        val reservation =
            creditManager.reserveCredits(GEOCODING_CREDITS)

        if (reservation == null) {
            deliver(
                callback,
                Result.failure(
                    IllegalStateException(
                        "RideConnect's daily 400-credit safeguard " +
                                "has been reached. Try again after the daily reset."
                    )
                )
            )
            return
        }

        val url = GEOCODING_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter(
                "point",
                "${point.latitude},${point.longitude}"
            )
            .addQueryParameter("reverse", "true")
            .addQueryParameter("locale", "en")
            .addQueryParameter("limit", "5")
            .addQueryParameter("provider", "default")
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    deliver(
                        callback,
                        Result.failure(
                            IOException(
                                "Reverse location search failed: ${e.message}",
                                e
                            )
                        )
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use { res ->

                        recordServerCredits(res, reservation)

                        val body = res.body?.string().orEmpty()

                        if (!res.isSuccessful) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "GraphHopper reverse geocoding failed " +
                                                "(HTTP ${res.code}): $body"
                                    )
                                )
                            )
                            return
                        }

                        try {
                            val results = parseGeocodingResults(body)

                            if (results.isEmpty()) {
                                deliver(
                                    callback,
                                    Result.failure(
                                        NoSuchElementException(
                                            "GraphHopper could not identify " +
                                                    "the selected location."
                                        )
                                    )
                                )
                            } else {
                                deliver(
                                    callback,
                                    Result.success(results)
                                )
                            }

                        } catch (e: Exception) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "Could not read reverse location results: " +
                                                e.message,
                                        e
                                    )
                                )
                            )
                        }
                    }
                }
            }
        )
    }



    /**
     * Calculates driving routes between two coordinates.
     *
     * Requests up to two alternative routes.
     *
     * The callback is delivered on the main thread.
     */
    fun calculateAlternativeRoutes(
        pickup: GeoPoint,
        destination: GeoPoint,
        callback: (Result<List<RouteOption>>) -> Unit
    ) {
        if (!isValidPoint(pickup) || !isValidPoint(destination)) {
            deliver(
                callback,
                Result.failure(
                    IllegalArgumentException(
                        "Pickup or destination coordinates are invalid."
                    )
                )
            )
            return
        }

        if (
            pickup.latitude == destination.latitude &&
            pickup.longitude == destination.longitude
        ) {
            deliver(
                callback,
                Result.failure(
                    IllegalArgumentException(
                        "Pickup and destination cannot be the same location."
                    )
                )
            )
            return
        }

        if (!hasRoutingApiKey(callback)) return

        val reservation =
            creditManager.reserveCredits(ALTERNATIVE_ROUTE_CREDITS)

        if (reservation == null) {
            deliver(
                callback,
                Result.failure(
                    IllegalStateException(
                        "RideConnect's daily 400-credit safeguard " +
                                "has been reached. No route request was sent."
                    )
                )
            )
            return
        }

        val urlBuilder = ROUTING_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter(
                "point",
                "${pickup.latitude},${pickup.longitude}"
            )
            .addQueryParameter(
                "point",
                "${destination.latitude},${destination.longitude}"
            )
            .addQueryParameter("profile", "car")
            .addQueryParameter("locale", "en")
            .addQueryParameter("instructions", "false")
            .addQueryParameter("calc_points", "true")
            .addQueryParameter("points_encoded", "false")
            .addQueryParameter("algorithm", "alternative_route")
            .addQueryParameter("alternative_route.max_paths", "2")
            .addQueryParameter("key", apiKey)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    // Keep the reservation conservatively.
                    deliver(
                        callback,
                        Result.failure(
                            IOException(
                                "Route calculation failed: ${e.message}",
                                e
                            )
                        )
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use { res ->

                        recordServerCredits(res, reservation)

                        val body = res.body?.string().orEmpty()

                        if (!res.isSuccessful) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "GraphHopper route calculation failed " +
                                                "(HTTP ${res.code}): $body"
                                    )
                                )
                            )
                            return
                        }

                        try {
                            val routes = parseRoutes(body)

                            if (routes.isEmpty()) {
                                deliver(
                                    callback,
                                    Result.failure(
                                        NoSuchElementException(
                                            "GraphHopper did not return any routes."
                                        )
                                    )
                                )
                            } else {
                                deliver(
                                    callback,
                                    Result.success(routes)
                                )
                            }
                        } catch (e: Exception) {
                            deliver(
                                callback,
                                Result.failure(
                                    IOException(
                                        "Could not read route results: " +
                                                e.message,
                                        e
                                    )
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    /**
     * Reads the GraphHopper credit header when available.
     * If it is absent, the estimated reservation is retained.
     */
    private fun recordServerCredits(
        response: Response,
        reservation: DailyCreditManager.CreditReservation
    ) {
        val actualCredits = response
            .header("X-RateLimit-Credits")
            ?.toDoubleOrNull()

        if (actualCredits != null && actualCredits >= 0.0) {
            creditManager.recordActualCredits(
                reservation,
                actualCredits
            )
        }
    }

    /**
     * Converts GraphHopper geocoding JSON into Kotlin objects.
     */

    private fun parseGeocodingResults(
        responseBody: String
    ): List<GeocodingResult> {

        val json = JSONObject(responseBody)
        val hits = json.optJSONArray("hits") ?: return emptyList()

        val results = mutableListOf<GeocodingResult>()

        for (i in 0 until hits.length()) {

            val hit = hits.optJSONObject(i) ?: continue
            val pointJson = hit.optJSONObject("point") ?: continue

            val latitude = pointJson.optDouble("lat", Double.NaN)
            val longitude = pointJson.optDouble("lng", Double.NaN)

            val point = GeoPoint(latitude, longitude)

            if (!isValidPoint(point)) continue

            val name = hit.optString("name", "").trim()
            val city = hit.optString("city", "").trim()
            val county = hit.optString("county", "").trim()
            val state = hit.optString("state", "").trim()
            val postcode = hit.optString("postcode", "").trim()
            val country = hit.optString("country", "").trim()

            val displayName = listOf(
                name,
                city,
                county,
                state,
                country
            )
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank { "Location result ${i + 1}" }

            results.add(
                GeocodingResult(
                    displayName = displayName,
                    point = point,
                    name = name,
                    city = city,
                    county = county,
                    state = state,
                    postcode = postcode,
                    country = country
                )
            )
        }

        return results
    }


    /**
     * Converts GraphHopper route JSON into RouteOption objects.
     *
     * points_encoded=false means the geometry is returned
     * as coordinate pairs: [longitude, latitude].
     */
    private fun parseRoutes(
        responseBody: String
    ): List<RouteOption> {

        val json = JSONObject(responseBody)
        val paths = json.optJSONArray("paths") ?: return emptyList()

        val routes = mutableListOf<RouteOption>()

        for (i in 0 until paths.length()) {
            val path = paths.optJSONObject(i) ?: continue

            val distance = path.optDouble("distance", Double.NaN)
            val duration = path.optLong("time", -1L)

            if (
                !distance.isFinite() ||
                distance <= 0.0 ||
                duration < 0L
            ) {
                continue
            }

            val geometry = path.optJSONObject("points")
                ?: continue

            val coordinates =
                geometry.optJSONArray("coordinates")
                    ?: continue

            val routePoints = mutableListOf<GeoPoint>()

            for (j in 0 until coordinates.length()) {
                val coordinate =
                    coordinates.optJSONArray(j) ?: continue

                if (coordinate.length() < 2) continue

                // GeoJSON order is longitude, latitude.
                val longitude = coordinate.optDouble(0, Double.NaN)
                val latitude = coordinate.optDouble(1, Double.NaN)

                val point = GeoPoint(
                    latitude = latitude,
                    longitude = longitude
                )

                if (isValidPoint(point)) {
                    routePoints.add(point)
                }
            }

            if (routePoints.size < 2) continue

            routes.add(
                RouteOption(
                    routeIndex = i,
                    distanceMeters = distance,
                    durationMillis = duration,
                    points = routePoints
                )
            )
        }

        return routes
    }

    private fun isValidPoint(point: GeoPoint): Boolean {
        return point.latitude.isFinite() &&
                point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 &&
                point.longitude in -180.0..180.0
    }

    private fun hasGeocodingApiKey(
        callback: (Result<List<GeocodingResult>>) -> Unit
    ): Boolean {
        if (apiKey.isNotBlank()) {
            return true
        }

        deliver(
            callback,
            Result.failure(
                IllegalStateException(
                    "GraphHopper API key is missing. " +
                            "Check GRAPHHOPPER_API_KEY in local.properties."
                )
            )
        )

        return false
    }

    private fun hasRoutingApiKey(
        callback: (Result<List<RouteOption>>) -> Unit
    ): Boolean {
        if (apiKey.isNotBlank()) {
            return true
        }

        deliver(
            callback,
            Result.failure(
                IllegalStateException(
                    "GraphHopper API key is missing. " +
                            "Check GRAPHHOPPER_API_KEY in local.properties."
                )
            )
        )

        return false
    }

    private fun <T> deliver(
        callback: (Result<T>) -> Unit,
        result: Result<T>
    ) {
        mainHandler.post {
            callback(result)
        }
    }
}

