
package com.example.rideconnect.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class BookingConfirmationActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var rideId: String? = null
    private var numberOfPassengers: Int = 1

    private lateinit var driverText: TextView
    private lateinit var vehicleText: TextView
    private lateinit var routeText: TextView
    private lateinit var dateTimeText: TextView
    private lateinit var seatsText: TextView
    private lateinit var priceText: TextView

    private lateinit var confirmButton: Button
    private lateinit var cancelButton: Button

    private lateinit var pickupMap: MapView
    private lateinit var pickupAddressText: TextView

    private var selectedPickupPoint: GeoPoint? = null
    private var selectedPickupAddress: String? = null
    private var pickupMarker: Marker? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_confirmation)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        driverText = findViewById(R.id.bookingDriverText)
        vehicleText = findViewById(R.id.bookingVehicleText)
        routeText = findViewById(R.id.bookingRouteText)
        dateTimeText = findViewById(R.id.bookingDateTimeText)
        seatsText = findViewById(R.id.bookingSeatsText)
        priceText = findViewById(R.id.bookingPriceText)

        confirmButton = findViewById(R.id.confirmBookingButton)
        cancelButton = findViewById(R.id.cancelBookingButton)

        pickupMap = findViewById(R.id.passengerPickupMap)
        pickupAddressText = findViewById(R.id.passengerPickupAddressText)

        rideId = intent.getStringExtra("RIDE_ID")

        numberOfPassengers = intent.getIntExtra(
            "NUMBER_OF_PASSENGERS",
            1
        )

        if (numberOfPassengers <= 0) {
            Toast.makeText(
                this,
                "Invalid number of passengers",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        if (rideId.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Ride information is missing",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        setupPickupMap()
        loadRideDetails()

        cancelButton.setOnClickListener {
            finish()
        }

        confirmButton.setOnClickListener {
            confirmBooking()
        }
    }

    // ------------------------------------------------------------
    // PICKUP MAP
    // ------------------------------------------------------------

    private fun setupPickupMap() {
        Configuration.getInstance().userAgentValue =
            "RideConnect/1.0 (Android; educational project)"

        pickupMap.setTileSource(
            XYTileSource(
                "OpenTopoMap",
                0,
                17,
                256,
                ".png",
                arrayOf(
                    "https://a.tile.opentopomap.org/",
                    "https://b.tile.opentopomap.org/",
                    "https://c.tile.opentopomap.org/"
                )
            )
        )

        pickupMap.setMultiTouchControls(true)
        pickupMap.controller.setZoom(9.0)
        pickupMap.controller.setCenter(
            GeoPoint(-20.1609, 57.5012)
        )

        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(
                point: GeoPoint
            ): Boolean {
                selectPickupPoint(point)
                return true
            }

            override fun longPressHelper(
                point: GeoPoint
            ): Boolean {
                selectPickupPoint(point)
                return true
            }
        }

        pickupMap.overlays.add(MapEventsOverlay(receiver))
        pickupMap.invalidate()
    }

    private fun selectPickupPoint(point: GeoPoint) {
        selectedPickupPoint = point

        // A valid fallback address is retained if geocoding fails.
        selectedPickupAddress = String.format(
            Locale.US,
            "Map point: %.6f, %.6f",
            point.latitude,
            point.longitude
        )

        pickupAddressText.text =
            "Selected pickup:\n$selectedPickupAddress\nLooking up address..."

        if (pickupMarker == null) {
            pickupMarker = Marker(pickupMap).apply {
                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )
                title = "Your pickup point"
            }
            pickupMap.overlays.add(pickupMarker!!)
        }

        pickupMarker?.position = point
        pickupMarker?.title = "Your pickup point"

        pickupMap.controller.animateTo(point)
        pickupMap.invalidate()

        reverseGeocodePickup(point)
    }

    private fun reverseGeocodePickup(point: GeoPoint) {
        Thread {
            var connection: HttpURLConnection? = null

            try {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse" +
                            "?format=jsonv2" +
                            "&lat=${point.latitude}" +
                            "&lon=${point.longitude}" +
                            "&zoom=18" +
                            "&addressdetails=1"
                )

                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "User-Agent",
                    "RideConnect/1.0 (Android; educational project)"
                )
                connection.setRequestProperty("Accept", "application/json")

                if (connection.responseCode !in 200..299) {
                    throw Exception(
                        "Address lookup returned HTTP ${connection.responseCode}"
                    )
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val address = JSONObject(response)
                    .optString("display_name")
                    .takeIf { it.isNotBlank() }

                mainHandler.post {
                    val current = selectedPickupPoint

                    // Do not overwrite a newer selection.
                    if (
                        current != null &&
                        current.latitude == point.latitude &&
                        current.longitude == point.longitude &&
                        !address.isNullOrBlank()
                    ) {
                        selectedPickupAddress = address
                        pickupAddressText.text =
                            "Selected pickup:\n$address"
                    }
                }
            } catch (exception: Exception) {
                mainHandler.post {
                    val current = selectedPickupPoint

                    if (
                        current != null &&
                        current.latitude == point.latitude &&
                        current.longitude == point.longitude
                    ) {
                        pickupAddressText.text =
                            "Selected pickup:\n$selectedPickupAddress\n" +
                                    "Address lookup failed; coordinates are retained."
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    // ------------------------------------------------------------
    // LOAD RIDE DETAILS
    // ------------------------------------------------------------

    private fun loadRideDetails() {
        val id = rideId ?: return

        db.collection("rides")
            .document(id)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Toast.makeText(
                        this,
                        "This ride no longer exists",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }

                if (document.getString("status") != "Available") {
                    Toast.makeText(
                        this,
                        "This ride is no longer available",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }

                val driverName =
                    document.getString("driverName") ?: "Unknown driver"
                val carMake = document.getString("carMake") ?: ""
                val carModel = document.getString("carModel") ?: ""
                val carColor = document.getString("carColor") ?: ""
                val pickup = document.getString("pickup") ?: ""
                val destination = document.getString("destination") ?: ""
                val date = document.getString("date") ?: ""
                val time = document.getString("time") ?: ""
                val availableSeats =
                    document.getLong("availableSeats")?.toInt() ?: 0
                val price =
                    (document.get("pricePerPassenger") as? Number)?.toDouble() ?: 0.0

                driverText.text = "Driver: $driverName"
                vehicleText.text =
                    "Vehicle: $carMake $carModel ($carColor)"
                routeText.text = "Driver's posted route:\n$pickup → $destination"
                dateTimeText.text = "Date: $date\nTime: $time"
                seatsText.text =
                    "Available seats: $availableSeats\n" +
                            "Passengers requested: $numberOfPassengers"

                val totalPrice = price * numberOfPassengers

                priceText.text = String.format(
                    Locale.getDefault(),
                    "Fare per passenger: Rs %.2f\nTotal group fare: Rs %.2f",
                    price,
                    totalPrice
                )

                if (availableSeats < numberOfPassengers) {
                    confirmButton.isEnabled = false
                    Toast.makeText(
                        this,
                        "Not enough seats are available for your group",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Could not load ride: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
    }

    // ------------------------------------------------------------
    // CONFIRM BOOKING
    // ------------------------------------------------------------

    private fun confirmBooking() {
        val passengerId = auth.currentUser?.uid
        val id = rideId

        // Validate authentication and ride ID first.
        if (passengerId == null || id.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Unable to create booking. Please sign in again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // These variables must be outside the validation block.
        // They remain accessible inside the Firebase callbacks below.
        val pickupPoint = selectedPickupPoint

        if (pickupPoint == null) {
            Toast.makeText(
                this,
                "Please select your pickup point on the map first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val individualPickupAddress =
            selectedPickupAddress
                ?: String.format(
                    Locale.US,
                    "Map point: %.6f, %.6f",
                    pickupPoint.latitude,
                    pickupPoint.longitude
                )

        confirmButton.isEnabled = false

        db.collection("users")
            .document(passengerId)
            .get()
            .addOnSuccessListener { passengerDocument ->
                if (!passengerDocument.exists()) {
                    confirmButton.isEnabled = true
                    Toast.makeText(
                        this,
                        "Passenger profile not found",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val passengerName =
                    passengerDocument.getString("name") ?: ""
                val passengerPhone =
                    passengerDocument.getString("phone") ?: ""

                db.collection("rides")
                    .document(id)
                    .get()
                    .addOnSuccessListener { rideDocument ->
                        if (!rideDocument.exists()) {
                            confirmButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Ride no longer exists",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        if (rideDocument.getString("status") != "Available") {
                            confirmButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Sorry, this ride is no longer available",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        val availableSeats =
                            rideDocument.getLong("availableSeats")?.toInt() ?: 0

                        if (availableSeats < numberOfPassengers) {
                            confirmButton.isEnabled = true
                            Toast.makeText(
                                this,
                                "Not enough seats are available for your group",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        val driverId =
                            rideDocument.getString("driverId") ?: ""
                        val driverName =
                            rideDocument.getString("driverName") ?: ""
                        val driverPhone =
                            rideDocument.getString("driverPhone") ?: ""
                        val carMake =
                            rideDocument.getString("carMake") ?: ""
                        val carModel =
                            rideDocument.getString("carModel") ?: ""
                        val carColor =
                            rideDocument.getString("carColor") ?: ""
                        val carRegistration =
                            rideDocument.getString("carRegistration") ?: ""
                        val pickup =
                            rideDocument.getString("pickup") ?: ""
                        val destination =
                            rideDocument.getString("destination") ?: ""
                        val date =
                            rideDocument.getString("date") ?: ""
                        val time =
                            rideDocument.getString("time") ?: ""
                        val price =
                            (rideDocument.get("pricePerPassenger") as? Number)?.toDouble() ?: 0.0

                        val totalPrice =
                            price * numberOfPassengers

                        val booking = hashMapOf<String, Any>(
                            "rideId" to id,
                            "passengerId" to passengerId,
                            "passengerName" to passengerName,
                            "passengerPhone" to passengerPhone,

                            "driverId" to driverId,
                            "driverName" to driverName,
                            "driverPhone" to driverPhone,

                            "carMake" to carMake,
                            "carModel" to carModel,
                            "carColor" to carColor,
                            "carRegistration" to carRegistration,

                            // Preserve the driver's original posted route.
                            "pickup" to pickup,
                            "destination" to destination,

                            // Store this passenger's individual pickup point.
                            "pickupAddress" to individualPickupAddress,
                            "pickupLatitude" to pickupPoint.latitude,
                            "pickupLongitude" to pickupPoint.longitude,
                            "pickupUpdatedAt" to FieldValue.serverTimestamp(),

                            "date" to date,
                            "time" to time,

                            // Passenger group and fare information.
                            "numberOfPassengers" to numberOfPassengers,
                            "pricePerPassenger" to price,
                            "totalPrice" to totalPrice,

                            // Keep existing notification backend statuses.
                            "status" to "Pending",
                            "bookedAt" to FieldValue.serverTimestamp()
                        )

                        db.collection("bookings")
                            .add(booking)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Booking request sent successfully!",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                            .addOnFailureListener { exception ->
                                confirmButton.isEnabled = true
                                Toast.makeText(
                                    this,
                                    "Booking failed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        confirmButton.isEnabled = true
                        Toast.makeText(
                            this,
                            "Could not check ride: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { exception ->
                confirmButton.isEnabled = true
                Toast.makeText(
                    this,
                    "Could not load passenger profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ------------------------------------------------------------
    // MAP LIFECYCLE
    // ------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        if (::pickupMap.isInitialized) {
            pickupMap.onResume()
        }
    }

    override fun onPause() {
        if (::pickupMap.isInitialized) {
            pickupMap.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)

        if (::pickupMap.isInitialized) {
            pickupMap.onDetach()
        }

        super.onDestroy()
    }
}
