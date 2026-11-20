
package com.example.rideconnect.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.example.rideconnect.models.OperatingAreaMatch
import com.example.rideconnect.models.OperatingAreaMatcher
import com.example.rideconnect.models.OperatingAreaRepository
import com.example.rideconnect.routing.GeoPoint
import com.example.rideconnect.routing.GeocodingResult
import com.example.rideconnect.routing.GraphHopperRoutingService
import com.example.rideconnect.routing.RouteOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.osmdroid.util.GeoPoint as OsmGeoPoint

class PostRideActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var pickupInput: EditText
    private lateinit var destinationInput: EditText
    private lateinit var seatsInput: EditText
    private lateinit var priceInput: EditText

    private lateinit var dateText: TextView
    private lateinit var timeText: TextView
    private lateinit var postRideButton: Button

    private lateinit var graphHopperService: GraphHopperRoutingService

    private lateinit var calculateRouteButton: Button
    private lateinit var routeStatusText: TextView
    private lateinit var routeSummaryText: TextView
    private lateinit var routeOptionsGroup: RadioGroup
    private lateinit var postRideMap: MapView

    private lateinit var locationSelectionGroup: RadioGroup
    private lateinit var pickupPointText: TextView
    private lateinit var destinationPointText: TextView

    private enum class MapTarget {
        PICKUP,
        DESTINATION
    }

    private var activeMapTarget = MapTarget.PICKUP
    private var mapEventsOverlay: MapEventsOverlay? = null

    private var routeCalculationToken = 0

    private var pickupManuallyAdjusted = false
    private var destinationManuallyAdjusted = false

    private var selectedDate: String? = null
    private var selectedTime: String? = null

    private var selectedPickup: GeocodingResult? = null
    private var selectedDestination: GeocodingResult? = null

    private var availableRoutes: List<RouteOption> = emptyList()
    private var selectedRoute: RouteOption? = null

    private var calculatedPickupText: String? = null
    private var calculatedDestinationText: String? = null

    companion object {
        // Reverse-geocoded results farther than this from the
        // manually selected map point are rejected.
        private const val MAX_REVERSE_GEOCODE_DISTANCE_METERS = 1000.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_ride)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        graphHopperService = GraphHopperRoutingService(this)

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )

        Configuration.getInstance().userAgentValue =
            "RideConnect/1.0 (Android; educational project)"

        pickupInput = findViewById(R.id.pickupInput)
        destinationInput = findViewById(R.id.destinationInput)
        seatsInput = findViewById(R.id.seatsInput)
        priceInput = findViewById(R.id.priceInput)

        dateText = findViewById(R.id.dateText)
        timeText = findViewById(R.id.timeText)
        postRideButton = findViewById(R.id.postRideButton)

        calculateRouteButton = findViewById(R.id.calculateRouteButton)
        routeStatusText = findViewById(R.id.routeStatusText)
        routeSummaryText = findViewById(R.id.routeSummaryText)
        routeOptionsGroup = findViewById(R.id.routeOptionsGroup)
        postRideMap = findViewById(R.id.postRideMap)

        locationSelectionGroup = findViewById(R.id.locationSelectionGroup)
        pickupPointText = findViewById(R.id.pickupPointText)
        destinationPointText = findViewById(R.id.destinationPointText)

        setupMap()

        locationSelectionGroup.setOnCheckedChangeListener { _, checkedId ->
            activeMapTarget = when (checkedId) {
                R.id.selectPickupRadio -> MapTarget.PICKUP
                R.id.selectDestinationRadio -> MapTarget.DESTINATION
                else -> MapTarget.PICKUP
            }

            routeStatusText.text = when (activeMapTarget) {
                MapTarget.PICKUP ->
                    "Pickup selected. Tap the map to adjust the exact pickup point."

                MapTarget.DESTINATION ->
                    "Destination selected. Tap the map to adjust the exact destination."
            }
        }

        findViewById<Button>(R.id.selectDateButton).setOnClickListener {
            showDatePicker()
        }

        findViewById<Button>(R.id.selectTimeButton).setOnClickListener {
            showTimePicker()
        }

        calculateRouteButton.setOnClickListener {
            calculateRoute()
        }

        postRideButton.setOnClickListener {
            postRide()
        }

        pickupInput.addTextChangedListener(createLocationTextWatcher(true))
        destinationInput.addTextChangedListener(createLocationTextWatcher(false))
    }

    // ------------------------------------------------------------
    // MAP
    // ------------------------------------------------------------

    private fun setupMap() {

        postRideMap.setTileSource(
            object : OnlineTileSourceBase(
                "OpenTopoMap",
                1,
                17,
                256,
                ".png",
                arrayOf(
                    "https://a.tile.opentopomap.org/",
                    "https://b.tile.opentopomap.org/",
                    "https://c.tile.opentopomap.org/"
                )
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String {
                    return baseUrl +
                            MapTileIndex.getZoom(pMapTileIndex) + "/" +
                            MapTileIndex.getX(pMapTileIndex) + "/" +
                            MapTileIndex.getY(pMapTileIndex) +
                            mImageFilenameEnding
                }
            }
        )

        postRideMap.setMultiTouchControls(true)
        postRideMap.controller.setZoom(9.0)
        postRideMap.controller.setCenter(OsmGeoPoint(-20.1609, 57.5012))

        postRideMap.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        val mapReceiver = object : MapEventsReceiver {

            override fun singleTapConfirmedHelper(point: OsmGeoPoint): Boolean {
                selectMapLocation(point)
                return true
            }

            override fun longPressHelper(point: OsmGeoPoint): Boolean {
                selectMapLocation(point)
                return true
            }
        }

        mapEventsOverlay = MapEventsOverlay(mapReceiver)
        postRideMap.overlays.add(mapEventsOverlay)
    }

    private fun selectMapLocation(point: OsmGeoPoint) {

        val latitude = point.latitude
        val longitude = point.longitude

        if (
            latitude !in -20.65..-19.95 ||
            longitude !in 57.25..57.85
        ) {
            Toast.makeText(
                this,
                "Please select a location within Mauritius.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val coordinates = String.format(
            Locale.US,
            "%.6f, %.6f",
            latitude,
            longitude
        )

        val exactPoint = GeoPoint(latitude, longitude)

        when (activeMapTarget) {

            MapTarget.PICKUP -> {
                val locality = pickupInput.text.toString().trim()

                if (locality.isEmpty()) {
                    showValidationMessage(
                        "Enter the pickup locality before selecting its exact point."
                    )
                    return
                }

                selectedPickup = GeocodingResult(
                    displayName = "Map-selected pickup near $locality ($coordinates)",
                    point = exactPoint
                )

                pickupManuallyAdjusted = true
                pickupPointText.text = "Exact pickup coordinates:\n$coordinates"
            }

            MapTarget.DESTINATION -> {
                val locality = destinationInput.text.toString().trim()

                if (locality.isEmpty()) {
                    showValidationMessage(
                        "Enter the destination locality before selecting its exact point."
                    )
                    return
                }

                selectedDestination = GeocodingResult(
                    displayName = "Map-selected destination near $locality ($coordinates)",
                    point = exactPoint
                )

                destinationManuallyAdjusted = true
                destinationPointText.text = "Exact destination coordinates:\n$coordinates"
            }
        }

        invalidateCalculatedRoute(clearLocations = false)
        renderLocationMarkers()

        postRideMap.controller.animateTo(point)
        postRideMap.controller.setZoom(17.0)

        routeStatusText.text =
            "Location point updated. Select the other point if necessary, then calculate the route again."
    }

    private fun renderLocationMarkers() {

        postRideMap.overlays.clear()

        mapEventsOverlay?.let {
            postRideMap.overlays.add(it)
        }

        addLocationMarkers()
        postRideMap.invalidate()
    }

    private fun addLocationMarkers() {

        selectedPickup?.let { pickup ->
            val marker = Marker(postRideMap)

            marker.position = OsmGeoPoint(
                pickup.point.latitude,
                pickup.point.longitude
            )

            marker.title = "Pickup point"
            marker.snippet = pickup.displayName
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            postRideMap.overlays.add(marker)
        }

        selectedDestination?.let { destination ->
            val marker = Marker(postRideMap)

            marker.position = OsmGeoPoint(
                destination.point.latitude,
                destination.point.longitude
            )

            marker.title = "Destination point"
            marker.snippet = destination.displayName
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            postRideMap.overlays.add(marker)
        }
    }

    // ------------------------------------------------------------
    // DATE AND TIME
    // ------------------------------------------------------------

    private fun showDatePicker() {

        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedCalendar = Calendar.getInstance()

                selectedCalendar.set(year, month, day)

                val formatter = SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.getDefault()
                )

                selectedDate = formatter.format(selectedCalendar.time)
                dateText.text = "Date: $selectedDate"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.datePicker.minDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun showTimePicker() {

        val calendar = Calendar.getInstance()

        val timePicker = TimePickerDialog(
            this,
            { _, hour, minute ->
                val selectedCalendar = Calendar.getInstance()

                selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                selectedCalendar.set(Calendar.MINUTE, minute)

                val formatter = SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
                )

                selectedTime = formatter.format(selectedCalendar.time)
                timeText.text = "Time: $selectedTime"
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )

        timePicker.show()
    }

    // ------------------------------------------------------------
    // GEOCODING RESULT SELECTION
    // ------------------------------------------------------------

    private fun normalizeLocationText(value: String): String {
        return Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it.isLetterOrDigit() }
    }

    private fun selectBestGeocodingResult(
        query: String,
        locations: List<GeocodingResult>
    ): GeocodingResult? {

        val normalizedQuery = normalizeLocationText(query)
        if (normalizedQuery.isBlank()) return null

        // Mauritius is appended to many queries. Remove it when
        // comparing the actual locality requested by the user.
        val queryCore = normalizedQuery
            .removeSuffix("republicofmauritius")
            .removeSuffix("mauritius")

        val comparisonQuery =
            if (queryCore.length >= 3) queryCore else normalizedQuery

        val scoredResults = locations
            .distinctBy {
                Pair(it.point.latitude, it.point.longitude)
            }
            .mapNotNull { location ->

                val fields = listOf(
                    location.name,
                    location.city,
                    location.county,
                    location.state,
                    location.postcode
                )
                    .map { normalizeLocationText(it) }
                    .filter { it.isNotBlank() }

                val displayName =
                    normalizeLocationText(location.displayName)

                val score = when {
                    fields.any { it == comparisonQuery } -> 100

                    fields.any {
                        it.length >= 4 &&
                                (
                                        it.startsWith(comparisonQuery) ||
                                                comparisonQuery.startsWith(it)
                                        )
                    } -> 80

                    displayName.contains(comparisonQuery) -> 60

                    fields.any {
                        comparisonQuery.length >= 4 &&
                                it.contains(comparisonQuery)
                    } -> 50

                    else -> 0
                }

                if (score == 0) null else Pair(location, score)
            }

        if (scoredResults.isEmpty()) return null

        val highestScore = scoredResults.maxOf { it.second }

        val bestResults = scoredResults.filter {
            it.second == highestScore
        }

        // Do not arbitrarily choose between equally ranked results.
        return bestResults.singleOrNull()?.first
    }

    // ------------------------------------------------------------
    // ROUTE CALCULATION
    // ------------------------------------------------------------

    private fun calculateRoute() {

        val pickupText = pickupInput.text.toString().trim()
        val destinationText = destinationInput.text.toString().trim()

        if (pickupText.isEmpty()) {
            showValidationMessage("Please enter the pickup location.")
            return
        }

        if (destinationText.isEmpty()) {
            showValidationMessage("Please enter the destination.")
            return
        }

        if (pickupText.equals(destinationText, ignoreCase = true)) {
            showValidationMessage(
                "Pickup and destination cannot be the same locality."
            )
            return
        }

        invalidateCalculatedRoute(clearLocations = false)

        val requestToken = routeCalculationToken

        calculateRouteButton.isEnabled = false
        routeStatusText.text = "Preparing pickup and destination coordinates..."
        routeSummaryText.text = ""

        fun calculateUsingSelectedPoints() {

            if (requestToken != routeCalculationToken) return

            val pickup = selectedPickup
            val destination = selectedDestination

            if (pickup == null || destination == null) {
                calculateRouteButton.isEnabled = true
                routeStatusText.text =
                    "Please select both pickup and destination points."
                return
            }

            if (
                pickup.point.latitude == destination.point.latitude &&
                pickup.point.longitude == destination.point.longitude
            ) {
                calculateRouteButton.isEnabled = true
                routeStatusText.text =
                    "Pickup and destination cannot be the same point."
                return
            }

            routeStatusText.text =
                "Calculating driving route using your selected coordinates..."

            graphHopperService.calculateAlternativeRoutes(
                pickup = pickup.point,
                destination = destination.point
            ) { routeResult ->

                if (requestToken != routeCalculationToken) {
                    return@calculateAlternativeRoutes
                }

                calculateRouteButton.isEnabled = true

                routeResult.fold(
                    onSuccess = { routes ->

                        if (routes.isEmpty()) {
                            routeStatusText.text =
                                "No driving routes were returned."
                            return@fold
                        }

                        availableRoutes = routes
                        calculatedPickupText = pickupText
                        calculatedDestinationText = destinationText

                        routeStatusText.text =
                            "Route calculation successful. ${routes.size} route(s) found."

                        displayRouteOptions(routes)
                        selectRoute(0)
                    },

                    onFailure = { exception ->

                        invalidateCalculatedRoute(clearLocations = false)

                        routeStatusText.text =
                            "Route calculation failed:\n${exception.message}"
                    }
                )
            }
        }

        fun findDestination() {

            if (requestToken != routeCalculationToken) return

            if (selectedDestination != null) {
                calculateUsingSelectedPoints()
                return
            }

            routeStatusText.text = "Finding destination location..."

            graphHopperService.geocodeMauritius(destinationText) { result ->

                if (requestToken != routeCalculationToken) {
                    return@geocodeMauritius
                }

                result.fold(
                    onSuccess = { locations ->

                        val location = selectBestGeocodingResult(
                            destinationText,
                            locations
                        )

                        if (location == null) {
                            calculateRouteButton.isEnabled = true
                            routeStatusText.text =
                                "Destination could not be matched unambiguously. Please refine the location."
                            return@fold
                        }

                        selectedDestination = location
                        destinationPointText.text =
                            "Destination point:\n${location.displayName}"

                        destinationManuallyAdjusted = false
                        calculateUsingSelectedPoints()
                    },

                    onFailure = { exception ->
                        calculateRouteButton.isEnabled = true
                        routeStatusText.text =
                            "Destination search failed:\n${exception.message}"
                    }
                )
            }
        }

        if (selectedPickup != null) {
            findDestination()
        } else {

            routeStatusText.text = "Finding pickup location..."

            graphHopperService.geocodeMauritius(pickupText) { result ->

                if (requestToken != routeCalculationToken) {
                    return@geocodeMauritius
                }

                result.fold(
                    onSuccess = { locations ->

                        val location = selectBestGeocodingResult(
                            pickupText,
                            locations
                        )

                        if (location == null) {
                            calculateRouteButton.isEnabled = true
                            routeStatusText.text =
                                "Pickup location could not be matched unambiguously. Please refine the location."
                            return@fold
                        }

                        selectedPickup = location
                        pickupPointText.text =
                            "Pickup point:\n${location.displayName}"

                        pickupManuallyAdjusted = false
                        findDestination()
                    },

                    onFailure = { exception ->
                        calculateRouteButton.isEnabled = true
                        routeStatusText.text =
                            "Pickup search failed:\n${exception.message}"
                    }
                )
            }
        }
    }

    // ------------------------------------------------------------
    // ROUTE OPTIONS
    // ------------------------------------------------------------

    private fun displayRouteOptions(routes: List<RouteOption>) {

        routeOptionsGroup.removeAllViews()

        routes.forEachIndexed { index, route ->

            val radioButton = RadioButton(this)

            radioButton.id = android.view.View.generateViewId()

            radioButton.text =
                "Route ${index + 1}  |  " +
                        String.format(Locale.US, "%.2f km", route.distanceKm) +
                        "  |  ${route.durationMinutes} min"

            radioButton.textSize = 15f
            radioButton.setPadding(8, 12, 8, 12)

            routeOptionsGroup.addView(radioButton)
        }

        routeOptionsGroup.setOnCheckedChangeListener { _, checkedId ->

            val selectedIndex =
                (0 until routeOptionsGroup.childCount).firstOrNull { index ->
                    routeOptionsGroup.getChildAt(index).id == checkedId
                }

            if (selectedIndex != null) {
                selectRoute(selectedIndex)
            }
        }

        if (routes.isNotEmpty()) {
            routeOptionsGroup.check(routeOptionsGroup.getChildAt(0).id)
        }
    }

    private fun selectRoute(index: Int) {

        val route = availableRoutes.getOrNull(index) ?: return

        selectedRoute = route

        routeSummaryText.text =
            "Selected Route ${index + 1}\n\n" +
                    "Distance: " +
                    String.format(Locale.US, "%.2f km", route.distanceKm) +
                    "\nEstimated driving time: ${route.durationMinutes} minutes\n\n" +
                    "From: ${selectedPickup?.displayName}\n" +
                    "To: ${selectedDestination?.displayName}"

        displayRouteOnMap(route)
    }

    private fun displayRouteOnMap(route: RouteOption) {

        val osmPoints = route.points.map { point ->
            OsmGeoPoint(point.latitude, point.longitude)
        }

        if (osmPoints.size < 2) {
            routeStatusText.text =
                "The selected route does not contain enough map points."
            return
        }

        postRideMap.overlays.clear()

        mapEventsOverlay?.let {
            postRideMap.overlays.add(it)
        }

        val routeLine = Polyline().apply {
            setPoints(osmPoints)
            title = "Selected driving route"
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 9f
        }

        postRideMap.overlays.add(routeLine)
        addLocationMarkers()

        val boundingBox = BoundingBox.fromGeoPoints(osmPoints)

        postRideMap.zoomToBoundingBox(
            boundingBox,
            true,
            80
        )

        postRideMap.invalidate()
    }

    // ------------------------------------------------------------
    // INPUT CHANGE HANDLING
    // ------------------------------------------------------------

    private fun createLocationTextWatcher(
        isPickup: Boolean
    ): TextWatcher {

        return object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                if (isPickup) {
                    selectedPickup = null
                    pickupManuallyAdjusted = false
                    pickupPointText.text = "Pickup point: Not selected"
                } else {
                    selectedDestination = null
                    destinationManuallyAdjusted = false
                    destinationPointText.text =
                        "Destination point: Not selected"
                }

                invalidateCalculatedRoute(clearLocations = false)
                renderLocationMarkers()

                routeStatusText.text =
                    "Location search changed. Calculate the route again."
            }

            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
    }

    private fun invalidateCalculatedRoute(
        clearLocations: Boolean = true
    ) {

        routeCalculationToken++

        if (clearLocations) {
            selectedPickup = null
            selectedDestination = null

            pickupManuallyAdjusted = false
            destinationManuallyAdjusted = false

            pickupPointText.text = "Pickup point: Not selected"
            destinationPointText.text = "Destination point: Not selected"
        }

        availableRoutes = emptyList()
        selectedRoute = null

        calculatedPickupText = null
        calculatedDestinationText = null

        routeOptionsGroup.removeAllViews()

        renderLocationMarkers()

        postRideMap.controller.setZoom(9.0)
        postRideMap.controller.setCenter(OsmGeoPoint(-20.1609, 57.5012))
        postRideMap.invalidate()
    }

    // ------------------------------------------------------------
    // DISTANCE CALCULATION
    // ------------------------------------------------------------

    private fun distanceBetweenMeters(
        first: GeoPoint,
        second: GeoPoint
    ): Double {

        val earthRadiusMeters = 6371000.0

        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)

        val deltaLat = Math.toRadians(
            second.latitude - first.latitude
        )

        val deltaLon = Math.toRadians(
            second.longitude - first.longitude
        )

        val a =
            sin(deltaLat / 2) * sin(deltaLat / 2) +
                    cos(lat1) * cos(lat2) *
                    sin(deltaLon / 2) * sin(deltaLon / 2)

        val safeA = a.coerceIn(0.0, 1.0)

        return 2 * earthRadiusMeters * asin(sqrt(safeA))
    }

    // ------------------------------------------------------------
    // REVERSE GEOCODING FOR MAP-SELECTED POINTS
    // ------------------------------------------------------------

    private fun reverseGeocodeSelectedLocation(
        location: GeocodingResult,
        callback: (Result<GeocodingResult>) -> Unit
    ) {

        graphHopperService.reverseGeocodeMauritius(location.point) { result ->

            result.fold(
                onSuccess = { locations ->

                    if (locations.isEmpty()) {
                        callback(
                            Result.failure(
                                Exception(
                                    "No address details were returned for the selected map location."
                                )
                            )
                        )
                        return@fold
                    }

                    val nearestLocation = locations.minByOrNull { candidate ->
                        distanceBetweenMeters(
                            location.point,
                            candidate.point
                        )
                    }

                    if (nearestLocation == null) {
                        callback(
                            Result.failure(
                                Exception(
                                    "No address details were found for the selected map location."
                                )
                            )
                        )
                        return@fold
                    }

                    val distance = distanceBetweenMeters(
                        location.point,
                        nearestLocation.point
                    )

                    if (distance > MAX_REVERSE_GEOCODE_DISTANCE_METERS) {
                        callback(
                            Result.failure(
                                Exception(
                                    "The nearest address result is too far from your selected map point (${distance.toInt()} metres). Please select a more precise point."
                                )
                            )
                        )
                        return@fold
                    }

                    // Preserve the exact coordinates selected by the driver.
                    // The reverse-geocoded result supplies address information only.
                    val enrichedLocation = nearestLocation.copy(
                        point = location.point
                    )

                    callback(Result.success(enrichedLocation))
                },

                onFailure = { exception ->
                    callback(Result.failure(exception))
                }
            )
        }
    }

    // ------------------------------------------------------------
    // RESOLVE BOTH LOCATIONS
    // ------------------------------------------------------------

    private fun resolveRideLocations(
        pickupLocation: GeocodingResult,
        destinationLocation: GeocodingResult,
        callback: (Result<Pair<GeocodingResult, GeocodingResult>>) -> Unit
    ) {

        fun resolveDestination(
            resolvedPickup: GeocodingResult
        ) {

            if (!destinationManuallyAdjusted) {
                callback(
                    Result.success(
                        Pair(resolvedPickup, destinationLocation)
                    )
                )
                return
            }

            reverseGeocodeSelectedLocation(destinationLocation) { result ->

                result.fold(
                    onSuccess = { resolvedDestination ->
                        callback(
                            Result.success(
                                Pair(resolvedPickup, resolvedDestination)
                            )
                        )
                    },

                    onFailure = { exception ->
                        callback(Result.failure(exception))
                    }
                )
            }
        }

        if (!pickupManuallyAdjusted) {
            resolveDestination(pickupLocation)
            return
        }

        reverseGeocodeSelectedLocation(pickupLocation) { result ->

            result.fold(
                onSuccess = { resolvedPickup ->
                    resolveDestination(resolvedPickup)
                },

                onFailure = { exception ->
                    callback(Result.failure(exception))
                }
            )
        }
    }

    // ------------------------------------------------------------
    // DRIVER OPERATING-AREA VALIDATION
    // ------------------------------------------------------------

    private fun validateDriverOperatingAreas(
        driverDocument: DocumentSnapshot,
        pickupLocation: GeocodingResult,
        destinationLocation: GeocodingResult,
        callback: (
            Boolean,
            String,
            GeocodingResult,
            GeocodingResult
        ) -> Unit
    ) {

        val operatesThroughoutMauritius =
            driverDocument.getBoolean("operatesThroughoutMauritius") == true ||
                    driverDocument.getString("operatingAreaMode") == "ALL_MAURITIUS"

        // Resolve manually selected map points even for nationwide drivers.
        // Nationwide drivers are not restricted by selected operating areas.
        if (operatesThroughoutMauritius) {

            resolveRideLocations(
                pickupLocation,
                destinationLocation
            ) { result ->

                result.fold(
                    onSuccess = { locations ->
                        callback(
                            true,
                            "",
                            locations.first,
                            locations.second
                        )
                    },

                    onFailure = { exception ->
                        callback(
                            false,
                            "Could not verify the selected map location: ${exception.message}",
                            pickupLocation,
                            destinationLocation
                        )
                    }
                )
            }

            return
        }

        // Read the driver's saved operating areas.
        val savedAreas =
            driverDocument.get("operatingAreas") as? List<*>
                ?: emptyList<Any>()

        val selectedAreas = savedAreas.mapNotNull { item ->

            val area = item as? Map<*, *> ?: return@mapNotNull null

            val section = area["section"] as? String ?: ""
            val locality = area["locality"] as? String ?: ""
            val sublocality = area["sublocality"] as? String ?: ""
            val postcode = area["postcode"] as? String ?: ""

            if (
                section.isBlank() ||
                locality.isBlank() ||
                sublocality.isBlank()
            ) {
                null
            } else {
                OperatingAreaMatch(
                    section = section,
                    locality = locality,
                    sublocality = sublocality,
                    postcode = postcode
                )
            }
        }

        if (selectedAreas.isEmpty()) {
            callback(
                false,
                "Please select and save your operating areas in Driver Account before posting a shared ride.",
                pickupLocation,
                destinationLocation
            )
            return
        }

        // Load the Mauritius locality database.
        val sections = try {
            OperatingAreaRepository.load(this)
        } catch (exception: Exception) {
            callback(
                false,
                "Could not load the Mauritius locality database: ${exception.message}",
                pickupLocation,
                destinationLocation
            )
            return
        }

        // Resolve map-selected points before checking their operating areas.
        resolveRideLocations(
            pickupLocation,
            destinationLocation
        ) { result ->

            result.fold(
                onSuccess = { locations ->

                    val verifiedPickup = locations.first
                    val verifiedDestination = locations.second

                    val pickupAllowed =
                        OperatingAreaMatcher.isWithinSelectedAreas(
                            location = verifiedPickup,
                            sections = sections,
                            selectedAreas = selectedAreas
                        )

                    if (!pickupAllowed) {
                        callback(
                            false,
                            "Your pickup location is outside your selected operating areas. Please update your selections in Driver Account.",
                            verifiedPickup,
                            verifiedDestination
                        )
                        return@fold
                    }

                    val destinationAllowed =
                        OperatingAreaMatcher.isWithinSelectedAreas(
                            location = verifiedDestination,
                            sections = sections,
                            selectedAreas = selectedAreas
                        )

                    if (!destinationAllowed) {
                        callback(
                            false,
                            "Your destination is outside your selected operating areas. Please update your selections in Driver Account.",
                            verifiedPickup,
                            verifiedDestination
                        )
                        return@fold
                    }

                    callback(
                        true,
                        "",
                        verifiedPickup,
                        verifiedDestination
                    )
                },

                onFailure = { exception ->
                    callback(
                        false,
                        "Could not verify the selected map location: ${exception.message}",
                        pickupLocation,
                        destinationLocation
                    )
                }
            )
        }
    }

    // ------------------------------------------------------------
    // POST RIDE
    // ------------------------------------------------------------

    private fun postRide() {

        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            showValidationMessage("Driver is not logged in")
            return
        }

        val pickup = pickupInput.text.toString().trim()
        val destination = destinationInput.text.toString().trim()
        val seatsText = seatsInput.text.toString().trim()
        val priceText = priceInput.text.toString().trim()

        // Required text fields.
        if (pickup.isEmpty()) {
            showValidationMessage("Please enter the pickup location")
            return
        }

        if (destination.isEmpty()) {
            showValidationMessage("Please enter the destination")
            return
        }

        if (selectedDate == null) {
            showValidationMessage("Please select a date")
            return
        }

        if (selectedTime == null) {
            showValidationMessage("Please select a time")
            return
        }

        // Seat validation.
        val availableSeats = seatsText.toIntOrNull()

        if (availableSeats == null || availableSeats <= 0) {
            showValidationMessage(
                "Available seats must be greater than 0"
            )
            return
        }

        if (availableSeats > 20) {
            showValidationMessage("Maximum 20 seats allowed")
            return
        }

        // Price validation.
        val price = priceText.toDoubleOrNull()

        if (
            price == null ||
            !price.isFinite() ||
            price < 0
        ) {
            showValidationMessage("Please enter a valid price")
            return
        }

        // A current route must exist.
        val route = selectedRoute
        val pickupLocation = selectedPickup
        val destinationLocation = selectedDestination

        if (
            route == null ||
            pickupLocation == null ||
            destinationLocation == null
        ) {
            showValidationMessage(
                "Please calculate and select a route before posting."
            )
            return
        }

        // Do not post a route calculated for older text values.
        if (
            calculatedPickupText != pickup ||
            calculatedDestinationText != destination
        ) {
            showValidationMessage(
                "Your locations have changed. Please calculate the route again."
            )
            return
        }

        postRideButton.isEnabled = false

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { driverDocument ->

                if (!driverDocument.exists()) {
                    handlePostFailure("Driver profile not found")
                    return@addOnSuccessListener
                }

                validateDriverOperatingAreas(
                    driverDocument = driverDocument,
                    pickupLocation = pickupLocation,
                    destinationLocation = destinationLocation
                ) { isAllowed, validationMessage, verifiedPickup, verifiedDestination ->

                    if (!isAllowed) {
                        handlePostFailure(validationMessage)
                        return@validateDriverOperatingAreas
                    }

                    val driverName =
                        driverDocument.getString("name") ?: ""

                    val driverPhone =
                        driverDocument.getString("phone") ?: ""

                    val carMake =
                        driverDocument.getString("carMake") ?: ""

                    val carModel =
                        driverDocument.getString("carModel") ?: ""

                    val carColor =
                        driverDocument.getString("carColor") ?: ""

                    val carRegistration =
                        driverDocument.getString("carRegistration") ?: ""

                    val operatesThroughoutMauritius =
                        driverDocument.getBoolean("operatesThroughoutMauritius") == true ||
                                driverDocument.getString("operatingAreaMode") == "ALL_MAURITIUS"

                    val operatingAreas =
                        driverDocument.get("operatingAreas") as? List<*>
                            ?: emptyList<Any>()

                    val operatingSections =
                        driverDocument.get("operatingSections") as? List<*>
                            ?: emptyList<Any>()

                    val legacyRegion =
                        if (operatesThroughoutMauritius) {
                            "ALL_MAURITIUS"
                        } else {
                            operatingSections
                                .filterIsInstance<String>()
                                .joinToString(", ")
                        }

                    val routePoints = route.points.map { point ->
                        mapOf(
                            "latitude" to point.latitude,
                            "longitude" to point.longitude
                        )
                    }

                    val pickupCoordinates = mapOf(
                        "latitude" to verifiedPickup.point.latitude,
                        "longitude" to verifiedPickup.point.longitude
                    )

                    val destinationCoordinates = mapOf(
                        "latitude" to verifiedDestination.point.latitude,
                        "longitude" to verifiedDestination.point.longitude
                    )

                    val ride = hashMapOf<String, Any>(

                        "driverId" to driverId,
                        "driverName" to driverName,
                        "driverPhone" to driverPhone,

                        "carMake" to carMake,
                        "carModel" to carModel,
                        "carColor" to carColor,
                        "carRegistration" to carRegistration,

                        "pickup" to pickup,
                        "destination" to destination,
                        "region" to legacyRegion,

                        "pickupSearchQuery" to pickup,
                        "destinationSearchQuery" to destination,

                        "pickupManuallyAdjusted" to pickupManuallyAdjusted,
                        "destinationManuallyAdjusted" to destinationManuallyAdjusted,

                        "date" to selectedDate!!,
                        "time" to selectedTime!!,

                        "availableSeats" to availableSeats,
                        "totalSeats" to availableSeats,
                        "pricePerPassenger" to price,

                        "pickupDisplayName" to verifiedPickup.displayName,
                        "destinationDisplayName" to verifiedDestination.displayName,

                        "pickupCoordinates" to pickupCoordinates,
                        "destinationCoordinates" to destinationCoordinates,

                        "operatesThroughoutMauritius" to operatesThroughoutMauritius,

                        "operatingAreaMode" to
                                if (operatesThroughoutMauritius) {
                                    "ALL_MAURITIUS"
                                } else {
                                    "SELECTED_AREAS"
                                },

                        "operatingAreas" to operatingAreas,
                        "operatingSections" to operatingSections,

                        "routeDistanceMeters" to route.distanceMeters,
                        "routeDistanceKm" to route.distanceKm,
                        "routeDurationMillis" to route.durationMillis,
                        "routeDurationMinutes" to route.durationMinutes,
                        "selectedRouteIndex" to route.routeIndex,
                        "routePoints" to routePoints,

                        "status" to "Available",
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("rides")
                        .add(ride)
                        .addOnSuccessListener {

                            Toast.makeText(
                                this,
                                "Ride posted successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            finish()
                        }
                        .addOnFailureListener { exception ->
                            handlePostFailure(
                                "Failed to post ride: ${exception.message}"
                            )
                        }
                }
            }
            .addOnFailureListener { exception ->
                handlePostFailure(
                    "Could not load driver profile: ${exception.message}"
                )
            }
    }

    // ------------------------------------------------------------
    // UI HELPERS
    // ------------------------------------------------------------

    private fun showValidationMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handlePostFailure(message: String) {

        postRideButton.isEnabled = true

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    // ------------------------------------------------------------
    // MAP LIFECYCLE
    // ------------------------------------------------------------

    override fun onResume() {
        super.onResume()

        if (::postRideMap.isInitialized) {
            postRideMap.onResume()
        }
    }

    override fun onPause() {

        if (::postRideMap.isInitialized) {
            postRideMap.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {

        if (::postRideMap.isInitialized) {
            postRideMap.onDetach()
        }

        super.onDestroy()
    }
}
