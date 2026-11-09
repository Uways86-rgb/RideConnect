package com.example.rideconnect.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.URLEncoder
import java.util.Date


import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.rideconnect.notifications.NotificationPermissionHelper

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


class PassengerDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay

    private var rideListener: ListenerRegistration? = null
    private var driverLocationListener: ListenerRegistration? = null
    private var driverLocationMarker: Marker? = null
    private var currentDriverId: String? = null
    private var currentRideId: String? = null

    private var currentPickupLatitude: Double? = null
    private var currentPickupLongitude: Double? = null
    private var destinationLatitude: Double? = null
    private var destinationLongitude: Double? = null
    private var destinationMarker: Marker? = null

    private var activeLocationListener: LocationListener? = null
    private val locationHandler = Handler(Looper.getMainLooper())
    private var locationTimeout: Runnable? = null
    private var locationRequestInProgress = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationPermissionHelper.requestIfNeeded(
            this,
            notificationPermissionLauncher
        )

        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue =
            "RideConnect/1.0 (Android; educational project)"

        setContentView(R.layout.activity_passenger_dashboard)

        // ---------------------------------------------------------
        // HANDLE ANDROID SYSTEM NAVIGATION BAR INSETS
        // ---------------------------------------------------------

        val bottomNavigation = findViewById<View>(
            R.id.passengerBottomNavigation
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

        // Request the current system navigation bar dimensions.
        ViewCompat.requestApplyInsets(bottomNavigation)



        map = findViewById(R.id.map)
        map.setTileSource(
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

        map.setMultiTouchControls(true)
        map.controller.setZoom(9.0)
        map.controller.setCenter(GeoPoint(-20.1609, 57.5012))

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val pickupInput = findViewById<EditText>(R.id.pickupInput)
        val destinationInput = findViewById<EditText>(R.id.destinationInput)

        findViewById<Button>(R.id.useCurrentLocationButton)
            .setOnClickListener {
                requestCurrentLocation(pickupInput)
            }

        findViewById<Button>(R.id.searchDestinationButton)
            .setOnClickListener {
                val destination = destinationInput.text.toString().trim()

                if (destination.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Please enter a destination",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                searchDestination(destination)
            }

        findViewById<Button>(R.id.rideHistoryButton)
            .setOnClickListener {
                startActivity(
                    Intent(this, RideHistoryActivity::class.java)
                )
            }

        findViewById<Button>(R.id.myBookingsButton)
            .setOnClickListener {
                startActivity(
                    Intent(this, MyBookingsActivity::class.java)
                )
            }

        findViewById<Button>(R.id.searchRidesButton)
            .setOnClickListener {
                startActivity(
                    Intent(this, RideSearchActivity::class.java)
                )
            }

        // ---------------------------------------------------------
        // PASSENGER BOTTOM NAVIGATION
        // ---------------------------------------------------------

        // HOME
        findViewById<TextView>(R.id.homeNavItem)
            .setOnClickListener {
                // Already on the Passenger Dashboard.
            }

        // EXPLORE
        findViewById<TextView>(R.id.exploreNavItem)
            .setOnClickListener {
                startActivity(
                    Intent(this, RideSearchActivity::class.java)
                )
            }

        // BOOKINGS
        findViewById<TextView>(R.id.bookingsNavItem)
            .setOnClickListener {
                startActivity(
                    Intent(this, MyBookingsActivity::class.java)
                )
            }

        // ACCOUNT
        findViewById<TextView>(R.id.accountNavItem)
            .setOnClickListener {
                startActivity(
                    Intent(this, PassengerAccountActivity::class.java)
                )
            }


        loadPassengerName()

        checkForExistingRide(
            findViewById(R.id.statusText),
            findViewById(R.id.driverNameText),
            findViewById(R.id.driverPhoneText),
            findViewById(R.id.driverCarText),
            findViewById(R.id.rideDetailsText)
        )

        findViewById<Button>(R.id.findRideButton)
            .setOnClickListener { button ->

                val findRideButton = button as Button
                val pickup = pickupInput.text.toString().trim()
                val destination = destinationInput.text.toString().trim()

                if (pickup.isEmpty() || destination.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Please enter pickup and destination",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val userId = auth.currentUser?.uid

                if (userId == null) {
                    Toast.makeText(
                        this,
                        "User is not logged in",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val ride = hashMapOf(
                    "passengerId" to userId,
                    "pickup" to pickup,
                    "destination" to destination,
                    "status" to "Searching",
                    "timestamp" to Date()
                )

                findRideButton.isEnabled = false

                db.collection("rides")
                    .add(ride)
                    .addOnSuccessListener { documentReference ->

                        currentRideId = documentReference.id

                        Toast.makeText(
                            this,
                            "Ride request sent!",
                            Toast.LENGTH_SHORT
                        ).show()

                        val statusText =
                            findViewById<TextView>(R.id.statusText)
                        val driverNameText =
                            findViewById<TextView>(R.id.driverNameText)
                        val driverPhoneText =
                            findViewById<TextView>(R.id.driverPhoneText)
                        val driverCarText =
                            findViewById<TextView>(R.id.driverCarText)
                        val rideDetailsText =
                            findViewById<TextView>(R.id.rideDetailsText)

                        statusText.text = "🔍 Searching for a driver..."
                        driverNameText.text = ""
                        driverPhoneText.text = ""
                        driverCarText.text = ""
                        rideDetailsText.text =
                            "Pickup: $pickup\nDestination: $destination"

                        findRideButton.isEnabled = true

                        listenForRideStatus(
                            documentReference.id,
                            statusText,
                            driverNameText,
                            driverPhoneText,
                            driverCarText,
                            rideDetailsText
                        )
                    }
                    .addOnFailureListener { exception ->
                        findRideButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Error creating ride: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }

        checkLocationPermission()
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

    // ---------------------------------------------------------
    // LOCATION PERMISSIONS AND SETTINGS
    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun checkLocationPermission() {
        if (hasFineLocationPermission()) {
            setupLocation()
        } else if (hasCoarseLocationPermission()) {
            Toast.makeText(
                this,
                "Approximate location is allowed. Enable precise location for GPS accuracy.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun openDeviceLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Please enable Location in your phone settings.",
                Toast.LENGTH_LONG
            ).show()
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
            if (hasFineLocationPermission()) {
                setupLocation()
            } else if (hasCoarseLocationPermission()) {
                Toast.makeText(
                    this,
                    "Approximate location allowed. You can still try Use Current Location.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Please allow location access to use this feature.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------
    // CURRENT LOCATION
    private fun requestCurrentLocation(pickupInput: EditText) {
        val fineGranted = hasFineLocationPermission()
        val coarseGranted = hasCoarseLocationPermission()

        if (!fineGranted && !coarseGranted) {
            checkLocationPermission()
            return
        }

        if (locationRequestInProgress) {
            Toast.makeText(
                this,
                "Already detecting your location. Please wait.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val manager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        val providers = mutableListOf<String>()

        try {
            if (
                fineGranted &&
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                providers.add(LocationManager.GPS_PROVIDER)
            }

            if (
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to check location providers.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (providers.isEmpty()) {
            Toast.makeText(
                this,
                "Please switch on Location on your phone.",
                Toast.LENGTH_LONG
            ).show()
            openDeviceLocationSettings()
            return
        }

        var lastKnown: Location? = null

        try {
            for (provider in providers) {
                val candidate = manager.getLastKnownLocation(provider)

                if (
                    candidate != null &&
                    (lastKnown == null || candidate.time > lastKnown.time)
                ) {
                    lastKnown = candidate
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Location permission is required.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Use a recent cached fix immediately instead of waiting for GPS.
        val cached = lastKnown
        if (
            cached != null &&
            System.currentTimeMillis() - cached.time < 120_000L
        ) {
            showCurrentLocation(cached, pickupInput)
            return
        }

        locationRequestInProgress = true

        Toast.makeText(
            this,
            "Detecting your location...",
            Toast.LENGTH_SHORT
        ).show()

        var completed = false
        lateinit var listener: LocationListener

        fun finish(location: Location) {
            if (completed) return

            completed = true
            locationRequestInProgress = false
            locationTimeout?.let { locationHandler.removeCallbacks(it) }
            locationTimeout = null

            try {
                manager.removeUpdates(listener)
            } catch (_: Exception) {
                // Location updates may already have stopped.
            }

            activeLocationListener = null
            showCurrentLocation(location, pickupInput)
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                finish(location)
            }

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?
            ) {
                // Not required.
            }

            override fun onProviderEnabled(provider: String) {
                // Wait for a location update.
            }

            override fun onProviderDisabled(provider: String) {
                // Another enabled provider may still return a location.
            }
        }

        activeLocationListener = listener

        val timeout = Runnable {
            if (!completed) {
                val fallback = lastKnown

                if (fallback != null) {
                    finish(fallback)
                } else {
                    completed = true
                    locationRequestInProgress = false

                    try {
                        manager.removeUpdates(listener)
                    } catch (_: Exception) {
                        // Location updates may already have stopped.
                    }

                    activeLocationListener = null
                    locationTimeout = null

                    Toast.makeText(
                        this,
                        "No location fix received. Try outdoors and try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        locationTimeout = timeout

        try {
            for (provider in providers) {
                manager.requestLocationUpdates(
                    provider,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }

            locationHandler.postDelayed(timeout, 15_000L)
        } catch (e: SecurityException) {
            completed = true
            locationRequestInProgress = false
            locationHandler.removeCallbacks(timeout)
            locationTimeout = null
            activeLocationListener = null

            try {
                manager.removeUpdates(listener)
            } catch (_: Exception) {
                // Updates may already have stopped.
            }

            Toast.makeText(
                this,
                "Please check RideConnect's location permissions.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            completed = true
            locationRequestInProgress = false
            locationHandler.removeCallbacks(timeout)
            locationTimeout = null
            activeLocationListener = null

            try {
                manager.removeUpdates(listener)
            } catch (_: Exception) {
                // Updates may already have stopped.
            }

            Toast.makeText(
                this,
                "Could not start location detection: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showCurrentLocation(
        location: Location,
        pickupInput: EditText
    ) {
        currentPickupLatitude = location.latitude
        currentPickupLongitude = location.longitude

        val point = GeoPoint(location.latitude, location.longitude)
        map.controller.animateTo(point)
        map.controller.setZoom(15.0)
        map.invalidate()

        getAddressFromLocation(
            location.latitude,
            location.longitude,
            pickupInput
        )

        Toast.makeText(
            this,
            "Location coordinates detected.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupLocation() {
        if (!hasFineLocationPermission()) return

        val manager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        val enabled = try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }

        if (!enabled) {
            return
        }

        if (::locationOverlay.isInitialized) {
            map.overlays.remove(locationOverlay)
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
        }

        locationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this),
            map
        )

        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
        map.invalidate()

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                val location = locationOverlay.myLocation

                if (location != null && !isFinishing) {
                    map.controller.animateTo(location)
                    map.controller.setZoom(15.0)

                    getAddressFromLocation(
                        location.latitude,
                        location.longitude
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // REVERSE GEOCODING
    // ---------------------------------------------------------

    private fun getAddressFromLocation(
        latitude: Double,
        longitude: Double,
        pickupInput: EditText? = null
    ) {
        val locationText =
            findViewById<TextView>(R.id.currentLocationText)

        locationText.text = "📍 Finding your address..."

        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val url =
            "https://nominatim.openstreetmap.org/reverse" +
                    "?format=jsonv2" +
                    "&lat=$latitude" +
                    "&lon=$longitude" +
                    "&zoom=18" +
                    "&addressdetails=1" +
                    "&accept-language=en"

        val request = okhttp3.Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "RideConnect/1.0 (Android; educational project)"
            )
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            locationText.text =
                                "📍 Unable to determine address"
                        }
                        return@Thread
                    }

                    val body = response.body?.string()

                    if (body.isNullOrEmpty()) {
                        runOnUiThread {
                            locationText.text = "📍 Address unavailable"
                        }
                        return@Thread
                    }

                    val displayName = org.json.JSONObject(body)
                        .optString("display_name", "")

                    runOnUiThread {
                        if (displayName.isNotEmpty()) {
                            locationText.text =
                                "📍 Current location:\n$displayName"
                            pickupInput?.setText(displayName)
                        } else {
                            locationText.text =
                                "📍 Location found, but address unavailable"
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    locationText.text = "📍 Unable to determine address"
                }
            }
        }.start()
    }

    // ---------------------------------------------------------
    // DESTINATION SEARCH
    // ---------------------------------------------------------

    private fun searchDestination(destination: String) {
        val searchButton =
            findViewById<Button>(R.id.searchDestinationButton)

        searchButton.isEnabled = false

        Toast.makeText(
            this,
            "Searching for $destination...",
            Toast.LENGTH_SHORT
        ).show()

        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val encoded = URLEncoder.encode(destination, "UTF-8")

        val url =
            "https://nominatim.openstreetmap.org/search" +
                    "?format=jsonv2" +
                    "&q=$encoded" +
                    "&countrycodes=mu" +
                    "&limit=1" +
                    "&addressdetails=1" +
                    "&accept-language=en"

        val request = okhttp3.Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "RideConnect/1.0 (Android; educational project)"
            )
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            searchButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Unable to search destination",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@Thread
                    }

                    val body = response.body?.string()

                    if (body.isNullOrEmpty()) {
                        runOnUiThread {
                            searchButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Destination not found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@Thread
                    }

                    val results = org.json.JSONArray(body)

                    if (results.length() == 0) {
                        runOnUiThread {
                            searchButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Destination not found in Mauritius",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@Thread
                    }

                    val result = results.getJSONObject(0)
                    val latitude = result.getDouble("lat")
                    val longitude = result.getDouble("lon")
                    val displayName =
                        result.optString("display_name", destination)

                    runOnUiThread {
                        destinationLatitude = latitude
                        destinationLongitude = longitude

                        destinationMarker?.let {
                            map.overlays.remove(it)
                        }

                        val point = GeoPoint(latitude, longitude)
                        destinationMarker = Marker(map).apply {
                            position = point
                            title = "Destination"
                            snippet = displayName
                        }

                        map.overlays.add(destinationMarker)
                        map.controller.animateTo(point)
                        map.controller.setZoom(15.0)
                        map.invalidate()

                        searchButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Destination found!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    searchButton.isEnabled = true
                    Toast.makeText(
                        this,
                        "Error searching destination",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    // PASSENGER NAME
    private fun loadPassengerName() {
        val userId = auth.currentUser?.uid ?: return
        val welcomeText = findViewById<TextView>(R.id.welcomeText)

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name")
                    if (!name.isNullOrEmpty()) {
                        welcomeText.text = "Hello $name 👋"
                    }
                }
            }
    }

    // EXISTING ACTIVE RIDE
    private fun checkForExistingRide(
        statusText: TextView,
        driverNameText: TextView,
        driverPhoneText: TextView,
        driverCarText: TextView,
        rideDetailsText: TextView
    ) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("rides")
            .whereEqualTo("passengerId", userId)
            .whereIn(
                "status",
                listOf("Searching", "Accepted", "In Progress")
            )
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (currentRideId != null) return@addOnSuccessListener

                if (!documents.isEmpty) {
                    val rideId = documents.documents[0].id
                    currentRideId = rideId

                    listenForRideStatus(
                        rideId,
                        statusText,
                        driverNameText,
                        driverPhoneText,
                        driverCarText,
                        rideDetailsText
                    )
                }
            }
    }

    // ---------------------------------------------------------
    // RIDE STATUS LISTENER
    // ---------------------------------------------------------

    private fun listenForRideStatus(
        rideId: String,
        statusText: TextView,
        driverNameText: TextView,
        driverPhoneText: TextView,
        driverCarText: TextView,
        rideDetailsText: TextView
    ) {
        if (currentRideId != null && currentRideId != rideId) return

        currentRideId = rideId
        rideListener?.remove()

        rideListener = db.collection("rides")
            .document(rideId)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    statusText.text =
                        "Unable to check ride status: ${error.message}"
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    statusText.text = "Ride no longer exists"
                    stopDriverLocationTracking()
                    return@addSnapshotListener
                }

                val status = snapshot.getString("status")
                val pickup = snapshot.getString("pickup")
                val destination = snapshot.getString("destination")

                rideDetailsText.text =
                    "Pickup: $pickup\nDestination: $destination"

                when (status) {
                    "Searching" -> {
                        statusText.text = "🔍 Searching for a driver..."
                        driverNameText.text = ""
                        driverPhoneText.text = ""
                        driverCarText.text = ""
                        stopDriverLocationTracking()
                    }

                    "Accepted", "In Progress" -> {
                        statusText.text =
                            if (status == "Accepted") {
                                "🚘 Driver Found!"
                            } else {
                                "🚗 Ride is in progress"
                            }

                        val driverId = snapshot.getString("driverId")

                        if (driverId != null) {
                            getDriverInformation(
                                driverId,
                                driverNameText,
                                driverPhoneText,
                                driverCarText
                            )
                            startDriverLocationTracking(driverId)
                        } else {
                            driverNameText.text =
                                "🚘 Driver information unavailable"
                            driverPhoneText.text = ""
                            driverCarText.text = ""
                            stopDriverLocationTracking()
                        }
                    }

                    "Completed" -> {
                        statusText.text = "✅ Ride Completed"
                        driverNameText.text =
                            "Your ride has been completed."
                        driverPhoneText.text =
                            "View it in Ride History."
                        driverCarText.text = ""
                        stopDriverLocationTracking()
                    }

                    else -> {
                        statusText.text = "Ride status: $status"
                        driverNameText.text = ""
                        driverPhoneText.text = ""
                        driverCarText.text = ""
                        stopDriverLocationTracking()
                    }
                }
            }
    }

    // ---------------------------------------------------------
    // DRIVER LOCATION TRACKING
    // ---------------------------------------------------------

    private fun startDriverLocationTracking(driverId: String) {
        if (
            currentDriverId == driverId &&
            driverLocationListener != null
        ) {
            return
        }

        stopDriverLocationTracking()
        currentDriverId = driverId

        driverLocationListener =
            db.collection("driverLocations")
                .document(driverId)
                .addSnapshotListener { document, error ->

                    if (error != null) {
                        Toast.makeText(
                            this,
                            "Unable to track driver location: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addSnapshotListener
                    }

                    if (document == null || !document.exists()) return@addSnapshotListener

                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")

                    if (latitude != null && longitude != null) {
                        updateDriverLocationMarker(latitude, longitude)
                    }
                }
    }

    private fun updateDriverLocationMarker(
        latitude: Double,
        longitude: Double
    ) {
        runOnUiThread {
            val point = GeoPoint(latitude, longitude)

            if (driverLocationMarker == null) {
                driverLocationMarker = Marker(map).apply {
                    title = "🚗 Driver"
                    snippet = "Live driver location"
                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )
                }
                map.overlays.add(driverLocationMarker)
            }

            driverLocationMarker?.position = point
            driverLocationMarker?.title = "🚗 Driver"
            driverLocationMarker?.snippet = "Live location"
            map.invalidate()
        }
    }

    private fun stopDriverLocationTracking() {
        driverLocationListener?.remove()
        driverLocationListener = null
        currentDriverId = null

        driverLocationMarker?.let {
            map.overlays.remove(it)
            driverLocationMarker = null
            map.invalidate()
        }
    }

    // ---------------------------------------------------------
    // DRIVER INFORMATION
    // ---------------------------------------------------------

    private fun getDriverInformation(
        driverId: String,
        driverNameText: TextView,
        driverPhoneText: TextView,
        driverCarText: TextView
    ) {
        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    driverNameText.text =
                        "🚘 Driver: User profile not found"
                    driverPhoneText.text = "📞 Phone: Unavailable"
                    driverCarText.text =
                        "🚗 Vehicle information unavailable"
                    return@addOnSuccessListener
                }

                val driverName =
                    document.getString("name") ?: "Name not available"
                val driverPhone =
                    document.getString("phone") ?: "Phone not available"
                val carRegistration =
                    document.getString("carRegistration")
                        ?: "Registration not available"
                val carMake =
                    document.getString("carMake") ?: "Make not available"
                val carModel =
                    document.getString("carModel") ?: "Model not available"
                val carColor =
                    document.getString("carColor") ?: "Colour not available"

                driverNameText.text = "🚘 Driver: $driverName"
                driverPhoneText.text = "📞 Phone: $driverPhone"
                driverCarText.text =
                    "🚗 Vehicle: $carMake $carModel\n" +
                            "🎨 Colour: $carColor\n" +
                            "🔢 Registration: $carRegistration"
            }
            .addOnFailureListener { exception ->
                driverNameText.text = "🚘 Driver: Unavailable"
                driverPhoneText.text = "📞 Phone: Unavailable"
                driverCarText.text =
                    "🚗 Vehicle information unavailable"

                Toast.makeText(
                    this,
                    "Could not load driver profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // ACTIVITY LIFECYCLE
    // ---------------------------------------------------------

    override fun onResume() {
        super.onResume()

        if (::map.isInitialized) {
            map.onResume()

            if (
                hasFineLocationPermission() &&
                !::locationOverlay.isInitialized
            ) {
                setupLocation()
            }
        }
    }

    override fun onPause() {
        if (::map.isInitialized) {
            map.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        rideListener?.remove()
        driverLocationListener?.remove()

        locationTimeout?.let {
            locationHandler.removeCallbacks(it)
        }

        activeLocationListener?.let { listener ->
            try {
                val manager =
                    getSystemService(LOCATION_SERVICE) as LocationManager
                manager.removeUpdates(listener)
            } catch (_: Exception) {
                // Updates may already have stopped.
            }
        }

        if (::locationOverlay.isInitialized) {
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
            map.overlays.remove(locationOverlay)
        }

        super.onDestroy()
    }
}
