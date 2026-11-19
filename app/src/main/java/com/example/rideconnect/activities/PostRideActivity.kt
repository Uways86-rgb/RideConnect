package com.example.rideconnect.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.preference.PreferenceManager
import com.example.rideconnect.R
import com.example.rideconnect.routing.GeoPoint
import com.example.rideconnect.routing.GeocodingResult
import com.example.rideconnect.routing.GraphHopperRoutingService
import com.example.rideconnect.routing.RouteOption
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Alias OSMDroid's GeoPoint to avoid clashing with GraphHopper's GeoPoint.
import org.osmdroid.util.GeoPoint as OsmGeoPoint

import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

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

    // Geocoded locations for the current route.
    private var selectedPickup: GeocodingResult? = null
    private var selectedDestination: GeocodingResult? = null

    // All routes returned by GraphHopper.
    private var availableRoutes: List<RouteOption> = emptyList()

    // The route currently selected by the driver.
    private var selectedRoute: RouteOption? = null

    // Input values used when the route was calculated.
    private var calculatedPickupText: String? = null
    private var calculatedDestinationText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_ride)

        // --------------------------------------------------------
        // INITIALISE FIREBASE AND GRAPHHOPPER
        // --------------------------------------------------------

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        graphHopperService = GraphHopperRoutingService(this)

        // --------------------------------------------------------
        // INITIALISE MAP CONFIGURATION
        // --------------------------------------------------------

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )

        Configuration.getInstance().userAgentValue =
            "RideConnect/1.0 (Android; educational project)"

        // --------------------------------------------------------
        // INITIALISE VIEWS
        // --------------------------------------------------------

        pickupInput = findViewById(R.id.pickupInput)
        destinationInput = findViewById(R.id.destinationInput)
        seatsInput = findViewById(R.id.seatsInput)
        priceInput = findViewById(R.id.priceInput)

        dateText = findViewById(R.id.dateText)
        timeText = findViewById(R.id.timeText)

        postRideButton = findViewById(R.id.postRideButton)

        calculateRouteButton =
            findViewById(R.id.calculateRouteButton)

        routeStatusText =
            findViewById(R.id.routeStatusText)

        routeSummaryText =
            findViewById(R.id.routeSummaryText)

        routeOptionsGroup =
            findViewById(R.id.routeOptionsGroup)

        postRideMap =
            findViewById(R.id.postRideMap)

        locationSelectionGroup =
            findViewById(R.id.locationSelectionGroup)

        pickupPointText =
            findViewById(R.id.pickupPointText)

        destinationPointText =
            findViewById(R.id.destinationPointText)

        // --------------------------------------------------------
        // INITIALISE MAP
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // DATE AND TIME BUTTONS
        // --------------------------------------------------------

        findViewById<Button>(R.id.selectDateButton)
            .setOnClickListener {
                showDatePicker()
            }

        findViewById<Button>(R.id.selectTimeButton)
            .setOnClickListener {
                showTimePicker()
            }

        // --------------------------------------------------------
        // ROUTE CALCULATION
        // --------------------------------------------------------

        calculateRouteButton.setOnClickListener {
            calculateRoute()
        }

        // --------------------------------------------------------
        // POST RIDE
        // --------------------------------------------------------

        postRideButton.setOnClickListener {
            postRide()
        }

        // --------------------------------------------------------
        // INVALIDATE ROUTE WHEN LOCATIONS CHANGE
        // --------------------------------------------------------

        pickupInput.addTextChangedListener(
            createLocationTextWatcher(true)
        )

        destinationInput.addTextChangedListener(
            createLocationTextWatcher(false)
        )
    }

    // ------------------------------------------------------------
    // MAP CONFIGURATION
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
                override fun getTileURLString(
                    pMapTileIndex: Long
                ): String {
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

        postRideMap.controller.setCenter(
            OsmGeoPoint(-20.1609, 57.5012)
        )

        // Allow map gestures to work inside the ScrollView.
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

            override fun singleTapConfirmedHelper(
                point: OsmGeoPoint
            ): Boolean {

                selectMapLocation(point)
                return true
            }

            override fun longPressHelper(
                point: OsmGeoPoint
            ): Boolean {

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

        // Restrict selection to the approximate main-island
        // bounding area of Mauritius.
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

        val exactPoint = GeoPoint(
            latitude = latitude,
            longitude = longitude
        )

        when (activeMapTarget) {

            MapTarget.PICKUP -> {

                val locality =
                    pickupInput.text.toString().trim()

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

                pickupPointText.text =
                    "Exact pickup coordinates:\n$coordinates"
            }

            MapTarget.DESTINATION -> {

                val locality =
                    destinationInput.text.toString().trim()

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

                destinationPointText.text =
                    "Exact destination coordinates:\n$coordinates"
            }
        }

        // Any previous route is now invalid because a point changed.
        invalidateCalculatedRoute(clearLocations = false)

        renderLocationMarkers()

        postRideMap.controller.animateTo(point)
        postRideMap.controller.setZoom(17.0)

        routeStatusText.text =
            "Location point updated. Select the other point if necessary, " +
                    "then calculate the route again."
    }


    private fun renderLocationMarkers() {

        postRideMap.overlays.clear()

        // Keep the map tap listener active after refreshing overlays.
        mapEventsOverlay?.let {
            postRideMap.overlays.add(it)
        }

        addLocationMarkers()

        postRideMap.invalidate()
    }

    private fun addLocationMarkers() {

        val pickup = selectedPickup

        if (pickup != null) {

            val marker = Marker(postRideMap)

            marker.position = OsmGeoPoint(
                pickup.point.latitude,
                pickup.point.longitude
            )

            marker.title = "Pickup point"
            marker.snippet = pickup.displayName

            marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            postRideMap.overlays.add(marker)
        }

        val destination = selectedDestination

        if (destination != null) {

            val marker = Marker(postRideMap)

            marker.position = OsmGeoPoint(
                destination.point.latitude,
                destination.point.longitude
            )

            marker.title = "Destination point"
            marker.snippet = destination.displayName

            marker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )

            postRideMap.overlays.add(marker)
        }
    }


    // ------------------------------------------------------------
    // DATE PICKER
    // ------------------------------------------------------------

    private fun showDatePicker() {

        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->

                val selectedCalendar = Calendar.getInstance()

                selectedCalendar.set(
                    selectedYear,
                    selectedMonth,
                    selectedDay
                )

                val formatter = SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.getDefault()
                )

                selectedDate =
                    formatter.format(selectedCalendar.time)

                dateText.text = "Date: $selectedDate"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Prevent selecting dates before today.
        datePicker.datePicker.minDate =
            System.currentTimeMillis()

        datePicker.show()
    }

    // ------------------------------------------------------------
    // TIME PICKER
    // ------------------------------------------------------------

    private fun showTimePicker() {

        val calendar = Calendar.getInstance()

        val timePicker = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->

                val selectedCalendar = Calendar.getInstance()

                selectedCalendar.set(
                    Calendar.HOUR_OF_DAY,
                    selectedHour
                )

                selectedCalendar.set(
                    Calendar.MINUTE,
                    selectedMinute
                )

                val formatter = SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
                )

                selectedTime =
                    formatter.format(selectedCalendar.time)

                timeText.text = "Time: $selectedTime"
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )

        timePicker.show()
    }

    // ------------------------------------------------------------
    // CALCULATE ROUTE
    // ------------------------------------------------------------

    private fun calculateRoute() {

        val pickupText =
            pickupInput.text.toString().trim()

        val destinationText =
            destinationInput.text.toString().trim()

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

        routeStatusText.text =
            "Preparing pickup and destination coordinates..."

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

                if (requestToken != routeCalculationToken) return@calculateAlternativeRoutes

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

                        invalidateCalculatedRoute(
                            clearLocations = false
                        )

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

            routeStatusText.text =
                "Finding destination location..."

            graphHopperService.geocodeMauritius(destinationText) { result ->

                if (requestToken != routeCalculationToken) return@geocodeMauritius

                result.fold(

                    onSuccess = { locations ->

                        val location = locations.firstOrNull()

                        if (location == null) {
                            calculateRouteButton.isEnabled = true
                            routeStatusText.text =
                                "Destination could not be found."
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

            routeStatusText.text =
                "Finding pickup location..."

            graphHopperService.geocodeMauritius(pickupText) { result ->

                if (requestToken != routeCalculationToken) return@geocodeMauritius

                result.fold(

                    onSuccess = { locations ->

                        val location = locations.firstOrNull()

                        if (location == null) {
                            calculateRouteButton.isEnabled = true
                            routeStatusText.text =
                                "Pickup location could not be found."
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
    // DISPLAY AVAILABLE ROUTES
    // ------------------------------------------------------------

    private fun displayRouteOptions(
        routes: List<RouteOption>
    ) {

        routeOptionsGroup.removeAllViews()

        routes.forEachIndexed { index, route ->

            val radioButton = RadioButton(this)

            radioButton.id = android.view.View.generateViewId()

            radioButton.text =
                "Route ${index + 1}  |  " +
                        String.format(
                            Locale.US,
                            "%.2f km",
                            route.distanceKm
                        ) +
                        "  |  " +
                        "${route.durationMinutes} min"

            radioButton.textSize = 15f

            radioButton.setPadding(
                8,
                12,
                8,
                12
            )

            routeOptionsGroup.addView(radioButton)
        }

        routeOptionsGroup.setOnCheckedChangeListener { _, checkedId ->

            val selectedIndex =
                (0 until routeOptionsGroup.childCount)
                    .firstOrNull { index ->
                        routeOptionsGroup.getChildAt(index).id == checkedId
                    }

            if (selectedIndex != null) {
                selectRoute(selectedIndex)
            }
        }

        if (routes.isNotEmpty()) {
            routeOptionsGroup.check(
                routeOptionsGroup.getChildAt(0).id
            )
        }
    }

    // ------------------------------------------------------------
    // SELECT A ROUTE
    // ------------------------------------------------------------

    private fun selectRoute(index: Int) {

        val route = availableRoutes.getOrNull(index)

        if (route == null) {
            return
        }

        selectedRoute = route

        routeSummaryText.text =
            "Selected Route ${index + 1}\n\n" +
                    "Distance: " +
                    String.format(
                        Locale.US,
                        "%.2f km",
                        route.distanceKm
                    ) +
                    "\nEstimated driving time: " +
                    "${route.durationMinutes} minutes\n\n" +
                    "From: ${selectedPickup?.displayName}\n" +
                    "To: ${selectedDestination?.displayName}"

        displayRouteOnMap(route)
    }

    // ------------------------------------------------------------
    // DISPLAY ROUTE ON MAP
    // ------------------------------------------------------------

    private fun displayRouteOnMap(route: RouteOption) {

        val osmPoints = route.points.map { point ->

            OsmGeoPoint(
                point.latitude,
                point.longitude
            )
        }

        if (osmPoints.size < 2) {
            routeStatusText.text =
                "The selected route does not contain enough map points."
            return
        }

        postRideMap.overlays.clear()

        // Keep map selection enabled.
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

        // Display both exact location markers.
        addLocationMarkers()

        val boundingBox =
            BoundingBox.fromGeoPoints(osmPoints)

        postRideMap.zoomToBoundingBox(
            boundingBox,
            true,
            80
        )

        postRideMap.invalidate()
    }

    // ------------------------------------------------------------
    // INVALIDATE ROUTE WHEN INPUT CHANGES
    // ------------------------------------------------------------

    private fun createLocationTextWatcher(
        isPickup: Boolean
    ): android.text.TextWatcher {

        return object : android.text.TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {

                if (isPickup) {

                    selectedPickup = null
                    pickupManuallyAdjusted = false

                    pickupPointText.text =
                        "Pickup point: Not selected"

                } else {

                    selectedDestination = null
                    destinationManuallyAdjusted = false

                    destinationPointText.text =
                        "Destination point: Not selected"
                }

                invalidateCalculatedRoute(
                    clearLocations = false
                )

                renderLocationMarkers()

                routeStatusText.text =
                    "Location search changed. Calculate the route again."
            }

            override fun afterTextChanged(
                s: android.text.Editable?
            ) {
            }
        }
    }

    private fun invalidateCalculatedRoute(
        clearLocations: Boolean = true
    ) {

        // Cancel any older asynchronous route calculation.
        routeCalculationToken++

        if (clearLocations) {

            selectedPickup = null
            selectedDestination = null

            pickupManuallyAdjusted = false
            destinationManuallyAdjusted = false

            pickupPointText.text =
                "Pickup point: Not selected"

            destinationPointText.text =
                "Destination point: Not selected"
        }

        availableRoutes = emptyList()
        selectedRoute = null

        calculatedPickupText = null
        calculatedDestinationText = null

        routeOptionsGroup.removeAllViews()

        renderLocationMarkers()

        postRideMap.controller.setZoom(9.0)

        postRideMap.controller.setCenter(
            OsmGeoPoint(-20.1609, 57.5012)
        )

        postRideMap.invalidate()
    }

    // ------------------------------------------------------------
    // POST RIDE
    // ------------------------------------------------------------

    private fun postRide() {

        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val pickup =
            pickupInput.text.toString().trim()

        val destination =
            destinationInput.text.toString().trim()

        val seatsText =
            seatsInput.text.toString().trim()

        val priceText =
            priceInput.text.toString().trim()

        // --------------------------------------------------------
        // VALIDATION
        // --------------------------------------------------------

        if (pickup.isEmpty()) {
            showValidationMessage(
                "Please enter the pickup location"
            )
            return
        }

        if (destination.isEmpty()) {
            showValidationMessage(
                "Please enter the destination"
            )
            return
        }

        if (selectedDate == null) {
            showValidationMessage(
                "Please select a date"
            )
            return
        }

        if (selectedTime == null) {
            showValidationMessage(
                "Please select a time"
            )
            return
        }

        if (seatsText.isEmpty()) {
            showValidationMessage(
                "Please enter the number of available seats"
            )
            return
        }

        val availableSeats =
            seatsText.toIntOrNull()

        if (
            availableSeats == null ||
            availableSeats <= 0
        ) {
            showValidationMessage(
                "Available seats must be greater than 0"
            )
            return
        }

        if (availableSeats > 20) {
            showValidationMessage(
                "Maximum 20 seats allowed"
            )
            return
        }

        if (priceText.isEmpty()) {
            showValidationMessage(
                "Please enter the price per passenger"
            )
            return
        }

        val price =
            priceText.toDoubleOrNull()

        if (
            price == null ||
            !price.isFinite() ||
            price < 0
        ) {
            showValidationMessage(
                "Please enter a valid price"
            )
            return
        }

        // --------------------------------------------------------
        // REQUIRE A CURRENT CALCULATED ROUTE
        // --------------------------------------------------------

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

        // --------------------------------------------------------
        // LOAD DRIVER PROFILE AND ASSIGNED REGION
        // --------------------------------------------------------

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { driverDocument ->

                if (!driverDocument.exists()) {
                    handlePostFailure(
                        "Driver profile not found"
                    )
                    return@addOnSuccessListener
                }

                val assignedRegion =
                    driverDocument.getString("assignedRegion")
                        ?.trim()
                        .orEmpty()

                if (
                    assignedRegion.isEmpty() ||
                    assignedRegion.equals(
                        "Select assigned region",
                        ignoreCase = true
                    )
                ) {
                    handlePostFailure(
                        "Please select and save your assigned region " +
                                "in Driver Account before posting a ride."
                    )
                    return@addOnSuccessListener
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

                // ------------------------------------------------
                // CONVERT ROUTE GEOMETRY FOR FIRESTORE
                // ------------------------------------------------

                val routePoints = route.points.map { point ->

                    mapOf(
                        "latitude" to point.latitude,
                        "longitude" to point.longitude
                    )
                }

                val pickupCoordinates = mapOf(
                    "latitude" to pickupLocation.point.latitude,
                    "longitude" to pickupLocation.point.longitude
                )

                val destinationCoordinates = mapOf(
                    "latitude" to destinationLocation.point.latitude,
                    "longitude" to destinationLocation.point.longitude
                )

                // ------------------------------------------------
                // CREATE POSTED RIDE
                // ------------------------------------------------

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
                    "region" to assignedRegion,

                    "pickupSearchQuery" to pickup,
                    "destinationSearchQuery" to destination,

                    "pickupManuallyAdjusted" to pickupManuallyAdjusted,
                    "destinationManuallyAdjusted" to destinationManuallyAdjusted,

                    "date" to selectedDate!!,
                    "time" to selectedTime!!,

                    "availableSeats" to availableSeats,
                    "totalSeats" to availableSeats,
                    "pricePerPassenger" to price,

                    // Geocoded locations.
                    "pickupDisplayName" to pickupLocation.displayName,
                    "destinationDisplayName" to destinationLocation.displayName,

                    "pickupCoordinates" to pickupCoordinates,
                    "destinationCoordinates" to destinationCoordinates,

                    // Selected GraphHopper route.
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
            .addOnFailureListener { exception ->

                handlePostFailure(
                    "Could not load driver profile: ${exception.message}"
                )
            }
    }

    // ------------------------------------------------------------
    // HELPERS
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