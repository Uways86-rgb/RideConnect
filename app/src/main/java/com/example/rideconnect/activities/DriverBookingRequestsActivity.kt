package com.example.rideconnect.activities

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.Locale

class DriverBookingRequestsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var bookingsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_driver_booking_requests
        )

        db =
            FirebaseFirestore.getInstance()

        auth =
            FirebaseAuth.getInstance()

        bookingsContainer =
            findViewById(
                R.id.bookingsContainer
            )

        loadBookingRequests()
    }

    // ============================================================
    // LOAD BOOKING REQUESTS
    // ============================================================

    private fun loadBookingRequests() {

        val driverId =
            auth.currentUser?.uid

        if (driverId == null) {

            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        bookingsContainer.removeAllViews()

        val loadingText =
            TextView(this)

        loadingText.text =
            "Loading booking requests..."

        loadingText.textSize =
            18f

        bookingsContainer.addView(
            loadingText
        )

        db.collection("bookings")
            .whereEqualTo(
                "driverId",
                driverId
            )
            .whereEqualTo(
                "status",
                "Pending"
            )
            .get()
            .addOnSuccessListener { documents ->

                bookingsContainer.removeAllViews()

                if (documents.isEmpty) {

                    showNoBookings()

                    return@addOnSuccessListener
                }

                for (document in documents) {

                    addBookingCard(
                        document
                    )
                }
            }
            .addOnFailureListener { exception ->

                bookingsContainer.removeAllViews()

                Toast.makeText(
                    this,
                    "Could not load bookings: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // DISPLAY BOOKING
    // ============================================================

    private fun addBookingCard(
        document: DocumentSnapshot
    ) {

        val bookingId =
            document.id

        val card =
            LinearLayout(this)

        card.orientation =
            LinearLayout.VERTICAL

        card.setPadding(
            25,
            25,
            25,
            25
        )

        card.setBackgroundColor(
            0xFFEEEEEE.toInt()
        )

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------

        val title =
            TextView(this)

        title.text =
            "🎫 Booking Request"

        title.textSize =
            21f

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        card.addView(title)

        // PASSENGER
        val passengerName =
            document.getString(
                "passengerName"
            ) ?: "Unknown passenger"

        val passengerPhone =
            document.getString(
                "passengerPhone"
            ) ?: "Not available"

        val passengerText =
            TextView(this)

        passengerText.text =
            "👤 Passenger: $passengerName\n📞 Phone: $passengerPhone"

        passengerText.textSize =
            17f

        passengerText.setPadding(
            0,
            15,
            0,
            0
        )

        card.addView(
            passengerText
        )

        // --------------------------------------------------------
        // ROUTE
        // --------------------------------------------------------

        val pickup =
            document.getString(
                "pickup"
            ) ?: ""

        val destination =
            document.getString(
                "destination"
            ) ?: ""

        val routeText =
            TextView(this)

        routeText.text =
            "📍 Route:\n$pickup → $destination"

        routeText.textSize =
            17f

        routeText.setPadding(
            0,
            12,
            0,
            0
        )

        card.addView(
            routeText
        )

        // --------------------------------------------------------
        // DATE / TIME
        // --------------------------------------------------------

        val date =
            document.getString(
                "date"
            ) ?: ""

        val time =
            document.getString(
                "time"
            ) ?: ""

        val dateTimeText =
            TextView(this)

        dateTimeText.text =
            "📅 Date: $date\n🕐 Time: $time"

        dateTimeText.textSize =
            17f

        dateTimeText.setPadding(
            0,
            12,
            0,
            0
        )

        card.addView(
            dateTimeText
        )

        // --------------------------------------------------------
        // PASSENGER COUNT AND FARE
        // --------------------------------------------------------

        val numberOfPassengers =
            (document.get("numberOfPassengers") as? Number)?.toInt() ?: 1

        val price =
            (document.get("pricePerPassenger") as? Number)?.toDouble() ?: 0.0

        val totalPrice =
            (document.get("totalPrice") as? Number)?.toDouble()
                ?: (price * numberOfPassengers)

        val fareText =
            TextView(this)

        fareText.text =
            "👥 Passengers: $numberOfPassengers\n" +
                    "💰 Fare per passenger: Rs ${
                        String.format(
                            Locale.getDefault(),
                            "%.2f",
                            price
                        )
                    }\n" +
                    "💰 Total group fare: Rs ${
                        String.format(
                            Locale.getDefault(),
                            "%.2f",
                            totalPrice
                        )
                    }"

        fareText.textSize =
            18f

        fareText.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        fareText.setPadding(
            0,
            12,
            0,
            0
        )

        card.addView(
            fareText
        )

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        val statusText =
            TextView(this)

        statusText.text =
            "Status: Pending"

        statusText.textSize =
            17f

        statusText.setPadding(
            0,
            12,
            0,
            0
        )

        card.addView(
            statusText
        )

        // --------------------------------------------------------
        // APPROVE BUTTON
        // --------------------------------------------------------

        val approveButton =
            Button(this)

        approveButton.text =
            "✅ APPROVE BOOKING"

        approveButton.setOnClickListener {

            approveBooking(
                bookingId,
                approveButton,
                rejectButton = null
            )
        }

        card.addView(
            approveButton
        )

        // --------------------------------------------------------
        // REJECT BUTTON
        // --------------------------------------------------------

        val rejectButton =
            Button(this)

        rejectButton.text =
            "❌ REJECT BOOKING"

        rejectButton.setOnClickListener {

            rejectBooking(
                bookingId,
                approveButton,
                rejectButton
            )
        }

        card.addView(
            rejectButton
        )

        // --------------------------------------------------------
        // CARD MARGIN
        // --------------------------------------------------------

        val layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        layoutParams.setMargins(
            0,
            0,
            0,
            25
        )

        card.layoutParams =
            layoutParams

        bookingsContainer.addView(
            card
        )
    }

    // ============================================================
    // APPROVE BOOKING
    // ============================================================

    private fun approveBooking(
        bookingId: String,
        approveButton: Button,
        rejectButton: Button?
    ) {

        val currentDriverId = auth.currentUser?.uid

        if (currentDriverId.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        approveButton.isEnabled = false
        rejectButton?.isEnabled = false

        val bookingReference =
            db.collection("bookings")
                .document(bookingId)

        db.runTransaction { transaction ->

            // ----------------------------------------------------
            // 1. READ BOOKING
            // ----------------------------------------------------

            val bookingSnapshot =
                transaction.get(bookingReference)

            if (!bookingSnapshot.exists()) {
                throw Exception("Booking no longer exists.")
            }

            val currentStatus =
                bookingSnapshot.getString("status") ?: ""

            if (currentStatus != "Pending") {
                throw Exception(
                    "This booking is no longer pending."
                )
            }

            // Verify that the logged-in driver owns this booking.
            val bookingDriverId =
                bookingSnapshot.getString("driverId") ?: ""

            if (bookingDriverId != currentDriverId) {
                throw Exception(
                    "You are not authorised to approve this booking."
                )
            }

            val rideId =
                bookingSnapshot.getString("rideId")

            if (rideId.isNullOrBlank()) {
                throw Exception("Ride information is missing.")
            }

            // ----------------------------------------------------
            // 2. GET NUMBER OF PASSENGERS
            // ----------------------------------------------------

            val numberOfPassengers =
                (bookingSnapshot.get("numberOfPassengers") as? Number)
                    ?.toInt() ?: 1

            if (numberOfPassengers <= 0) {
                throw Exception(
                    "Invalid number of passengers."
                )
            }

            // ----------------------------------------------------
            // 3. READ RIDE
            // ----------------------------------------------------

            val rideReference =
                db.collection("rides")
                    .document(rideId)

            val rideSnapshot =
                transaction.get(rideReference)

            if (!rideSnapshot.exists()) {
                throw Exception("Ride no longer exists.")
            }

            val rideDriverId =
                rideSnapshot.getString("driverId") ?: ""

            if (rideDriverId != currentDriverId) {
                throw Exception(
                    "This ride does not belong to your account."
                )
            }

            val rideStatus =
                rideSnapshot.getString("status") ?: ""

            if (rideStatus != "Available") {
                throw Exception(
                    "This ride is no longer available."
                )
            }

            // ----------------------------------------------------
            // 4. CHECK AVAILABLE SEATS
            // ----------------------------------------------------

            val availableSeats =
                (rideSnapshot.get("availableSeats") as? Number)
                    ?.toInt() ?: 0

            if (availableSeats < numberOfPassengers) {
                throw Exception(
                    "Not enough seats remain. " +
                            "Required: $numberOfPassengers, " +
                            "Available: $availableSeats."
                )
            }

            val newAvailableSeats =
                availableSeats - numberOfPassengers

            // ----------------------------------------------------
            // 5. UPDATE BOOKING AND RIDE ATOMICALLY
            // ----------------------------------------------------

            transaction.update(
                bookingReference,
                mapOf(
                    "status" to "Approved",
                    "approvedAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.update(
                rideReference,
                "availableSeats",
                newAvailableSeats
            )

            null

        }.addOnSuccessListener {

            Toast.makeText(
                this,
                "Booking approved successfully!",
                Toast.LENGTH_LONG
            ).show()

            loadBookingRequests()

        }.addOnFailureListener { exception ->

            approveButton.isEnabled = true
            rejectButton?.isEnabled = true

            Toast.makeText(
                this,
                "Could not approve booking: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ============================================================
    // REJECT BOOKING
    // ============================================================

    private fun rejectBooking(
        bookingId: String,
        approveButton: Button,
        rejectButton: Button
    ) {

        approveButton.isEnabled =
            false

        rejectButton.isEnabled =
            false

        db.collection("bookings")
            .document(bookingId)
            .update(
                mapOf(
                    "status" to "Rejected",
                    "rejectedAt" to
                            FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Booking rejected",
                    Toast.LENGTH_SHORT
                ).show()

                loadBookingRequests()
            }
            .addOnFailureListener { exception ->

                approveButton.isEnabled =
                    true

                rejectButton.isEnabled =
                    true

                Toast.makeText(
                    this,
                    "Could not reject booking: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // NO BOOKINGS
    // ============================================================

    private fun showNoBookings() {

        val noBookingsText =
            TextView(this)

        noBookingsText.text =
            "No pending booking requests."

        noBookingsText.textSize =
            18f

        noBookingsText.setPadding(
            10,
            30,
            10,
            30
        )

        bookingsContainer.addView(
            noBookingsText
        )
    }
}