
package com.example.rideconnect.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rideconnect.R
import com.example.rideconnect.notifications.NotificationPermissionHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class DriverDashboardActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var pickupText: TextView
    private lateinit var destinationText: TextView

    private lateinit var acceptRideButton: Button
    private lateinit var startRideButton: Button
    private lateinit var completeRideButton: Button

    private lateinit var instantRideRequestsContainer: LinearLayout
    private lateinit var instantRideRequestsEmptyText: TextView

    private lateinit var driverMap: MapView
    private lateinit var locationManager: LocationManager

    private var driverLocationMarker: Marker? = null
    private var currentRideId: String? = null

    // Real-time listener for all available instant ride requests.
    private var instantRideRequestsListener: ListenerRegistration? = null

    // Listener for approved bookings on a posted ride.
    private var approvedBookingsListener: ListenerRegistration? = null

    // Individual pickup markers for approved passengers.
    private val passengerPickupMarkers =
        mutableMapOf<String, Marker>()

    private var postedRideBeingTrackedId: String? = null

    private var shouldRetryLocationOnResume = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    // ============================================================
    // ACTIVITY CREATION
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationPermissionHelper.requestIfNeeded(
            this,
            notificationPermissionLauncher
        )

        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        Configuration.getInstance().userAgentValue =
            "RideConnect/1.0 (Android; educational project)"

        setContentView(R.layout.activity_driver_dashboard)

        // SYSTEM NAVIGATION BAR INSETS
        val bottomNavigation = findViewById<View>(
            R.id.driverBottomNavigation
        )

        val initialBottomPadding = bottomNavigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
            val navigationBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottomPadding + navigationBarInsets.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(bottomNavigation)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // MAP
        driverMap = findViewById(R.id.driverMap)
        driverMap.setTileSource(TileSourceFactory.OpenTopo)
        driverMap.setMultiTouchControls(true)
        driverMap.controller.setZoom(10.5)
        driverMap.controller.setCenter(
            GeoPoint(-20.1609, 57.5012)
        )

        // LOCATION MANAGER
        locationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        // DRIVER NAME
        loadDriverName()

        // LEGACY NAVIGATION BUTTONS
        findViewById<Button>(R.id.accountButton).setOnClickListener {
            startActivity(Intent(this, DriverAccountActivity::class.java))
        }

        findViewById<Button>(R.id.postRideButton).setOnClickListener {
            startActivity(Intent(this, PostRideActivity::class.java))
        }

        findViewById<Button>(R.id.bookingRequestsButton).setOnClickListener {
            startActivity(
                Intent(this, DriverBookingRequestsActivity::class.java)
            )
        }

        // BOTTOM NAVIGATION
        findViewById<TextView>(R.id.driverHomeNavItem).setOnClickListener {
            // Already on this screen.
        }

        findViewById<TextView>(R.id.driverPostRideNavItem).setOnClickListener {
            startActivity(Intent(this, PostRideActivity::class.java))
        }

        findViewById<TextView>(R.id.driverRequestsNavItem).setOnClickListener {
            startActivity(
                Intent(this, DriverBookingRequestsActivity::class.java)
            )
        }

        findViewById<TextView>(R.id.driverAccountNavItem).setOnClickListener {
            startActivity(Intent(this, DriverAccountActivity::class.java))
        }

        // CURRENT RIDE VIEWS
        pickupText = findViewById(R.id.pickupText)
        destinationText = findViewById(R.id.destinationText)

        acceptRideButton = findViewById(R.id.acceptRideButton)
        startRideButton = findViewById(R.id.startRideButton)
        completeRideButton = findViewById(R.id.completeRideButton)

        // DYNAMIC REQUEST LIST VIEWS
        instantRideRequestsContainer =
            findViewById(R.id.instantRideRequestsContainer)

        instantRideRequestsEmptyText =
            findViewById(R.id.instantRideRequestsEmptyText)

        // APPROVED PASSENGER PICKUP MARKERS
        loadPostedRideForMarkers()

        // LOCATION
        checkLocationPermission()

        // LOAD CURRENT RIDE
        loadDriverRide()

        // LISTEN FOR ALL AVAILABLE INSTANT REQUESTS
        listenForAvailableInstantRideRequests()
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notifications are disabled. You can enable them in Android Settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // ============================================================
    // LOCATION PERMISSION
    // ============================================================

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun checkLocationPermission() {
        if (hasLocationPermission()) {
            checkLocationServicesEnabled()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission()) {
                checkLocationServicesEnabled()
            } else {
                Toast.makeText(
                    this,
                    "Location permission is required to share your location.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // START DRIVER LOCATION UPDATES
    // ============================================================

    private fun startDriverLocationUpdates() {
        if (!hasLocationPermission()) return

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    driverLocationListener
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    driverLocationListener
                )
            }
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                "Unable to start location tracking: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val driverLocationListener =
        object : LocationListener {

            override fun onLocationChanged(location: Location) {
                updateDriverMap(location)
                saveDriverLocation(location)
            }
        }

    // ============================================================
    // UPDATE DRIVER MAP
    // ============================================================

    private fun updateDriverMap(location: Location) {
        val firstDriverLocation = driverLocationMarker == null

        val driverPosition = GeoPoint(
            location.latitude,
            location.longitude
        )

        if (driverLocationMarker == null) {
            driverLocationMarker = Marker(driverMap).apply {
                title = "Driver location"
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )
            }

            driverMap.overlays.add(driverLocationMarker)
        }

        driverLocationMarker?.position = driverPosition
        driverLocationMarker?.title = "🚗 You are here"
        driverLocationMarker?.snippet = "Live driver location"

        if (firstDriverLocation) {
            driverMap.controller.animateTo(driverPosition)
        }

        driverMap.invalidate()
    }

    // ============================================================
    // SAVE DRIVER LOCATION TO FIRESTORE
    // ============================================================

    private fun saveDriverLocation(location: Location) {
        val driverId = auth.currentUser?.uid ?: return

        val locationData = hashMapOf<String, Any>(
            "driverId" to driverId,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("driverLocations")
            .document(driverId)
            .set(locationData)
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Could not update driver location: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ============================================================
    // LOAD DRIVER'S CURRENT RIDE
    // ============================================================

    private fun loadDriverRide() {
        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        db.collection("rides")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf("Accepted", "In Progress"))
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->

                if (!documents.isEmpty) {
                    displayRide(documents.documents[0])
                } else {
                    loadPostedRide()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error loading driver ride: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // REAL-TIME LISTENER FOR ALL AVAILABLE INSTANT REQUESTS
    // ============================================================

    private fun listenForAvailableInstantRideRequests() {
        instantRideRequestsListener?.remove()

        instantRideRequestsListener =
            db.collection("rides")
                .whereEqualTo("status", "Searching")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Error loading ride requests: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    val availableRequests = snapshot.documents
                        .filter { document ->
                            document.getString("driverId").isNullOrBlank()
                        }
                        .sortedByDescending { document ->
                            document.getTimestamp("timestamp")
                                ?.toDate()
                                ?.time
                                ?: document.getTimestamp("createdAt")
                                    ?.toDate()
                                    ?.time
                                ?: 0L
                        }

                    displayInstantRideRequests(availableRequests)
                }
    }

    // ============================================================
    // DISPLAY ALL NUMBERED INSTANT REQUEST CARDS
    // ============================================================

    private fun displayInstantRideRequests(
        requests: List<DocumentSnapshot>
    ) {
        instantRideRequestsContainer.removeAllViews()

        if (requests.isEmpty()) {
            instantRideRequestsEmptyText.visibility = View.VISIBLE
            instantRideRequestsEmptyText.text =
                "No available ride requests at the moment."
            return
        }

        instantRideRequestsEmptyText.visibility = View.GONE

        requests.forEachIndexed { index, document ->
            val requestCard = createInstantRideRequestCard(
                document,
                index + 1
            )

            instantRideRequestsContainer.addView(requestCard)
        }
    }

    // ============================================================
    // CREATE AN INDIVIDUAL NUMBERED REQUEST CARD
    // ============================================================

    private fun createInstantRideRequestCard(
        document: DocumentSnapshot,
        requestNumber: Int
    ): LinearLayout {

        val rideId = document.id

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(225, 232, 238))
            }
            elevation = dp(3).toFloat()
        }

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }

        card.layoutParams = cardParams

        // NUMBER AND TITLE
        val heading = TextView(this).apply {
            text = "#$requestNumber  |  Ride Request"
            textSize = 18f
            setTextColor(Color.rgb(20, 45, 65))
            setTypeface(null, Typeface.BOLD)
        }

        card.addView(heading)

        // PICKUP
        val pickup = TextView(this).apply {
            text = "Pickup: ${
                document.getString("pickup") ?: "Unavailable"
            }"
            textSize = 15f
            setTextColor(Color.rgb(65, 75, 85))
            setPadding(0, dp(14), 0, 0)
        }

        card.addView(pickup)

        // DESTINATION
        val destination = TextView(this).apply {
            text = "Destination: ${
                document.getString("destination") ?: "Unavailable"
            }"
            textSize = 15f
            setTextColor(Color.rgb(65, 75, 85))
            setPadding(0, dp(9), 0, dp(14))
        }

        card.addView(destination)

        // BUTTON ROW
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val acceptButton = Button(this).apply {
            text = "ACCEPT"
            isAllCaps = true
        }

        val declineButton = Button(this).apply {
            text = "DECLINE"
            isAllCaps = true
        }

        val buttonParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = dp(6)
        }

        val declineParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(6)
        }

        buttonRow.addView(acceptButton, buttonParams)
        buttonRow.addView(declineButton, declineParams)

        card.addView(buttonRow)

        // EACH BUTTON USES THIS CARD'S OWN FIRESTORE DOCUMENT ID.
        acceptButton.setOnClickListener {
            acceptRide(rideId, acceptButton, declineButton)
        }

        declineButton.setOnClickListener {
            declineRide(rideId, acceptButton, declineButton)
        }

        return card
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // ============================================================
    // ACCEPT A SPECIFIC INSTANT RIDE REQUEST
    // ============================================================

    private fun acceptRide(
        rideId: String,
        acceptButton: Button,
        declineButton: Button
    ) {
        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        acceptButton.isEnabled = false
        declineButton.isEnabled = false

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { driverDocument ->

                if (!driverDocument.exists()) {
                    acceptButton.isEnabled = true
                    declineButton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Driver profile not found",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val rideReference = db.collection("rides")
                    .document(rideId)

                db.runTransaction { transaction ->

                    val rideDocument = transaction.get(rideReference)

                    val status = rideDocument.getString("status")
                    val assignedDriver =
                        rideDocument.getString("driverId")

                    // Do not accept a request that has already been
                    // accepted, assigned or declined.
                    if (
                        status != "Searching" ||
                        !assignedDriver.isNullOrBlank()
                    ) {
                        return@runTransaction false
                    }

                    val rideUpdates = hashMapOf<String, Any>(
                        "driverId" to driverId,
                        "driverName" to (
                                driverDocument.getString("name") ?: ""
                                ),
                        "driverPhone" to (
                                driverDocument.getString("phone") ?: ""
                                ),
                        "carMake" to (
                                driverDocument.getString("carMake") ?: ""
                                ),
                        "carModel" to (
                                driverDocument.getString("carModel") ?: ""
                                ),
                        "carColor" to (
                                driverDocument.getString("carColor") ?: ""
                                ),
                        "carRegistration" to (
                                driverDocument.getString("carRegistration") ?: ""
                                ),
                        "status" to "Accepted"
                    )

                    transaction.update(rideReference, rideUpdates)

                    true
                }.addOnSuccessListener { accepted ->

                    if (accepted) {
                        Toast.makeText(
                            this,
                            "Ride accepted successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        loadDriverRide()
                    } else {
                        acceptButton.isEnabled = true
                        declineButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "This request is no longer available.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.addOnFailureListener { exception ->

                    acceptButton.isEnabled = true
                    declineButton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Failed to accept ride: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->

                acceptButton.isEnabled = true
                declineButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Could not load driver profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // DECLINE A SPECIFIC INSTANT RIDE REQUEST
    // ============================================================

    private fun declineRide(
        rideId: String,
        acceptButton: Button,
        declineButton: Button
    ) {
        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        acceptButton.isEnabled = false
        declineButton.isEnabled = false

        val rideReference = db.collection("rides")
            .document(rideId)

        db.runTransaction { transaction ->

            val rideDocument = transaction.get(rideReference)

            val status = rideDocument.getString("status")
            val assignedDriver = rideDocument.getString("driverId")

            // Only decline a request that is still available.
            if (
                status != "Searching" ||
                !assignedDriver.isNullOrBlank()
            ) {
                return@runTransaction false
            }

            transaction.update(
                rideReference,
                mapOf(
                    "status" to "Rejected",
                    "rejectedByDriverId" to driverId,
                    "rejectedAt" to FieldValue.serverTimestamp()
                )
            )

            true
        }.addOnSuccessListener { declined ->

            if (declined) {
                Toast.makeText(
                    this,
                    "Ride request declined.",
                    Toast.LENGTH_SHORT
                ).show()

                // The real-time listener will automatically remove
                // this request from the available request list.
            } else {
                acceptButton.isEnabled = true
                declineButton.isEnabled = true

                Toast.makeText(
                    this,
                    "This request is no longer available.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.addOnFailureListener { exception ->

            acceptButton.isEnabled = true
            declineButton.isEnabled = true

            Toast.makeText(
                this,
                "Failed to decline ride: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ============================================================
    // LOAD THE DRIVER'S POSTED RIDE FOR THE CURRENT RIDE CARD
    // ============================================================

    private fun loadPostedRide() {
        val driverId = auth.currentUser?.uid ?: return

        db.collection("rides")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "Available")
            .get()
            .addOnSuccessListener { documents ->

                if (documents.isEmpty) {
                    showNoRide()
                    return@addOnSuccessListener
                }

                val ride = documents.documents.maxByOrNull {
                    it.getTimestamp("createdAt")
                        ?.toDate()
                        ?.time ?: 0L
                }

                if (ride != null) {
                    displayPostedRide(ride)
                } else {
                    showNoRide()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error loading posted rides: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // DISPLAY A POSTED RIDE
    // ============================================================

    private fun displayPostedRide(document: DocumentSnapshot) {
        currentRideId = null

        pickupText.text =
            "Posted ride pickup: ${
                document.getString("pickup") ?: "Unavailable"
            }"

        destinationText.text =
            "Destination: ${
                document.getString("destination") ?: "Unavailable"
            }"

        acceptRideButton.isEnabled = false
        startRideButton.isEnabled = false
        completeRideButton.isEnabled = false

        listenForApprovedPassengerPickups(document.id)
    }

    // ============================================================
    // FIND THE DRIVER'S POSTED RIDE FOR MAP MARKERS
    // ============================================================

    private fun loadPostedRideForMarkers() {
        val driverId = auth.currentUser?.uid ?: return

        db.collection("rides")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("status", "Available")
            .get()
            .addOnSuccessListener { documents ->

                val postedRide = documents.documents.maxByOrNull {
                    it.getTimestamp("createdAt")
                        ?.toDate()
                        ?.time ?: 0L
                }

                if (postedRide == null) {
                    stopApprovedPassengerPickupTracking()
                } else {
                    listenForApprovedPassengerPickups(postedRide.id)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Unable to find your posted ride for pickup markers: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // LISTEN FOR APPROVED PASSENGER BOOKINGS
    // ============================================================

    private fun listenForApprovedPassengerPickups(rideId: String) {
        if (
            postedRideBeingTrackedId == rideId &&
            approvedBookingsListener != null
        ) {
            return
        }

        stopApprovedPassengerPickupTracking()
        postedRideBeingTrackedId = rideId

        approvedBookingsListener =
            db.collection("bookings")
                .whereEqualTo("rideId", rideId)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Unable to load passenger pickups: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    val approvedBookings =
                        snapshot.documents.filter {
                            it.getString("status") == "Approved"
                        }

                    val approvedBookingIds =
                        approvedBookings.map { it.id }.toSet()

                    val obsoleteMarkerIds =
                        passengerPickupMarkers.keys.filter {
                            it !in approvedBookingIds
                        }

                    obsoleteMarkerIds.forEach { bookingId ->
                        passengerPickupMarkers[bookingId]?.let { marker ->
                            driverMap.overlays.remove(marker)
                        }
                        passengerPickupMarkers.remove(bookingId)
                    }

                    for (booking in approvedBookings) {
                        val latitude =
                            booking.getDouble("pickupLatitude")
                                ?: continue

                        val longitude =
                            booking.getDouble("pickupLongitude")
                                ?: continue

                        if (
                            latitude !in -90.0..90.0 ||
                            longitude !in -180.0..180.0
                        ) {
                            continue
                        }

                        val passengerName =
                            booking.getString("passengerName")
                                ?: "Approved passenger"

                        val pickupAddress =
                            booking.getString("pickupAddress")
                                ?: booking.getString("pickup")
                                ?: "Pickup address unavailable"

                        val marker =
                            passengerPickupMarkers[booking.id]
                                ?: Marker(driverMap).also { newMarker ->
                                    newMarker.setAnchor(
                                        Marker.ANCHOR_CENTER,
                                        Marker.ANCHOR_BOTTOM
                                    )

                                    driverMap.overlays.add(newMarker)
                                    passengerPickupMarkers[booking.id] =
                                        newMarker
                                }

                        marker.position = GeoPoint(latitude, longitude)
                        marker.title = "Passenger: $passengerName"
                        marker.snippet = pickupAddress
                    }

                    driverMap.invalidate()
                }
    }

    // ============================================================
    // STOP TRACKING PASSENGER PICKUPS AND REMOVE THEIR MARKERS
    // ============================================================

    private fun stopApprovedPassengerPickupTracking() {
        approvedBookingsListener?.remove()
        approvedBookingsListener = null
        postedRideBeingTrackedId = null

        passengerPickupMarkers.values.forEach { marker ->
            driverMap.overlays.remove(marker)
        }

        passengerPickupMarkers.clear()

        if (::driverMap.isInitialized) {
            driverMap.invalidate()
        }
    }

    // ============================================================
    // DISPLAY AN ACCEPTED OR IN-PROGRESS INSTANT RIDE
    // ============================================================

    private fun displayRide(document: DocumentSnapshot) {
        currentRideId = document.id

        pickupText.text =
            "Pickup: ${document.getString("pickup") ?: "Unavailable"}"

        destinationText.text =
            "Destination: ${
                document.getString("destination") ?: "Unavailable"
            }"

        acceptRideButton.isEnabled = false

        when (document.getString("status")) {

            "Accepted" -> {
                startRideButton.isEnabled = true
                completeRideButton.isEnabled = false

                startRideButton.setOnClickListener {
                    startRide()
                }
            }

            "In Progress" -> {
                startRideButton.isEnabled = false
                completeRideButton.isEnabled = true

                completeRideButton.setOnClickListener {
                    completeRide()
                }
            }

            else -> {
                startRideButton.isEnabled = false
                completeRideButton.isEnabled = false
            }
        }
    }

    // ============================================================
    // START INSTANT RIDE
    // ============================================================


    private fun startRide() {
        val rideId = currentRideId
        val driverId = auth.currentUser?.uid

        if (rideId == null || driverId == null) {
            Toast.makeText(
                this,
                "No active ride found.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        startRideButton.isEnabled = false

        val rideReference = db.collection("rides")
            .document(rideId)

        db.runTransaction { transaction ->

            val rideDocument = transaction.get(rideReference)

            val assignedDriverId =
                rideDocument.getString("driverId")

            val status =
                rideDocument.getString("status")

            // Only the driver assigned to this ride can start it.
            if (
                assignedDriverId != driverId ||
                status != "Accepted"
            ) {
                return@runTransaction false
            }

            transaction.update(
                rideReference,
                mapOf(
                    "status" to "In Progress",
                    "startTime" to FieldValue.serverTimestamp()
                )
            )

            true

        }.addOnSuccessListener { started ->

            if (started) {
                Toast.makeText(
                    this,
                    "Ride started!",
                    Toast.LENGTH_SHORT
                ).show()

                loadDriverRide()

            } else {
                currentRideId = null

                Toast.makeText(
                    this,
                    "This ride has already been assigned to another driver or is no longer available.",
                    Toast.LENGTH_LONG
                ).show()

                showNoRide()
                loadDriverRide()
            }

        }.addOnFailureListener { exception ->

            startRideButton.isEnabled = true

            Toast.makeText(
                this,
                "Failed to start ride: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // ============================================================
    // COMPLETE INSTANT RIDE
    // ============================================================

    private fun completeRide() {
        val rideId = currentRideId

        if (rideId == null) {
            Toast.makeText(
                this,
                "No ride selected",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        completeRideButton.isEnabled = false

        db.collection("rides")
            .document(rideId)
            .update(
                mapOf(
                    "status" to "Completed",
                    "endTime" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Ride completed!",
                    Toast.LENGTH_SHORT
                ).show()

                currentRideId = null

                pickupText.text = "Pickup: No active ride"
                destinationText.text = "Destination: -"

                startRideButton.isEnabled = false
                completeRideButton.isEnabled = false

                loadDriverRide()
                loadPostedRideForMarkers()
            }
            .addOnFailureListener { exception ->
                completeRideButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Failed to complete ride: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // NO CURRENT RIDE
    // ============================================================

    private fun showNoRide() {
        currentRideId = null

        pickupText.text = "Pickup: No active ride"
        destinationText.text = "Destination: -"

        acceptRideButton.isEnabled = false
        startRideButton.isEnabled = false
        completeRideButton.isEnabled = false
    }

    // ============================================================
    // LOAD DRIVER NAME
    // ============================================================

    private fun loadDriverName() {
        val driverId = auth.currentUser?.uid ?: return

        val welcomeDriverText =
            findViewById<TextView>(R.id.welcomeDriverText)

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")

                    if (!name.isNullOrEmpty()) {
                        welcomeDriverText.text = "Welcome $name 👋"
                    }
                }
            }
    }

    // ============================================================
    // MAP AND LOCATION LIFECYCLE
    // ============================================================

    override fun onResume() {
        super.onResume()

        if (::driverMap.isInitialized) {
            driverMap.onResume()
        }

        if (!::locationManager.isInitialized) return

        if (shouldRetryLocationOnResume) {
            shouldRetryLocationOnResume = false
        }

        if (hasLocationPermission()) {
            try {
                val gpsEnabled = locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
                )

                val networkEnabled = locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                )

                if (gpsEnabled || networkEnabled) {
                    startDriverLocationUpdates()
                } else {
                    Toast.makeText(
                        this,
                        "Location is disabled. Driver location sharing is paused.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (exception: Exception) {
                Toast.makeText(
                    this,
                    "Unable to check location settings.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onPause() {
        if (::driverMap.isInitialized) {
            driverMap.onPause()
        }

        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(driverLocationListener)
        }

        super.onPause()
    }

    private fun checkLocationServicesEnabled() {
        if (!hasLocationPermission()) return

        val gpsEnabled: Boolean
        val networkEnabled: Boolean

        try {
            gpsEnabled = locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )

            networkEnabled = locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                "Unable to check location settings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!gpsEnabled && !networkEnabled) {
            shouldRetryLocationOnResume = true

            Toast.makeText(
                this,
                "Please enable Location to share your driver's location.",
                Toast.LENGTH_LONG
            ).show()

            startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            )
            return
        }

        shouldRetryLocationOnResume = false
        startDriverLocationUpdates()
    }

    // ============================================================
    // ACTIVITY CLEANUP
    // ============================================================

    override fun onDestroy() {
        instantRideRequestsListener?.remove()
        instantRideRequestsListener = null

        stopApprovedPassengerPickupTracking()

        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(driverLocationListener)
        }

        driverLocationMarker?.let { marker ->
            if (::driverMap.isInitialized) {
                driverMap.overlays.remove(marker)
                driverMap.invalidate()
            }
        }

        driverLocationMarker = null

        super.onDestroy()
    }
}
