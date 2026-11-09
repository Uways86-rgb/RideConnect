package com.example.rideconnect.activities

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Locale

class MyBookingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var bookingsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_my_bookings
        )

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        bookingsContainer =
            findViewById(
                R.id.bookingsContainer
            )

        loadMyBookings()
    }

    // ============================================================
    // LOAD PASSENGER BOOKINGS
    // ============================================================

    private fun loadMyBookings() {

        val userId =
            auth.currentUser?.uid

        if (userId == null) {

            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        bookingsContainer.removeAllViews()

        val loadingText =
            TextView(this)

        loadingText.text =
            "Loading your bookings..."

        loadingText.textSize =
            18f

        bookingsContainer.addView(
            loadingText
        )

        db.collection("bookings")
            .whereEqualTo(
                "passengerId",
                userId
            )
            .get()
            .addOnSuccessListener { documents ->

                bookingsContainer.removeAllViews()

                if (documents.isEmpty) {

                    val noBookingsText =
                        TextView(this)

                    noBookingsText.text =
                        "You have no bookings yet."

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
        document:
        com.google.firebase.firestore.DocumentSnapshot
    ) {

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

        val pickup =
            document.getString(
                "pickup"
            ) ?: ""

        val destination =
            document.getString(
                "destination"
            ) ?: ""

        val driverName =
            document.getString(
                "driverName"
            ) ?: "Unknown driver"

        val driverPhone =
            document.getString(
                "driverPhone"
            ) ?: ""

        val carMake =
            document.getString(
                "carMake"
            ) ?: ""

        val carModel =
            document.getString(
                "carModel"
            ) ?: ""

        val carColor =
            document.getString(
                "carColor"
            ) ?: ""

        val carRegistration =
            document.getString(
                "carRegistration"
            ) ?: ""

        val date =
            document.getString(
                "date"
            ) ?: ""

        val time =
            document.getString(
                "time"
            ) ?: ""

        val price =
            document.getDouble(
                "pricePerPassenger"
            ) ?: 0.0

        val status =
            document.getString(
                "status"
            ) ?: "Unknown"

        // --------------------------------------------------------
        // ROUTE
        // --------------------------------------------------------

        val routeText =
            TextView(this)

        routeText.text =
            "🚗 $pickup → $destination"

        routeText.textSize =
            20f

        routeText.setTypeface(
            null,
            Typeface.BOLD
        )

        card.addView(
            routeText
        )

        // --------------------------------------------------------
        // DRIVER
        // --------------------------------------------------------

        val driverText =
            TextView(this)

        driverText.text =
            "Driver: $driverName"

        driverText.textSize =
            17f

        driverText.setPadding(
            0,
            15,
            0,
            0
        )

        card.addView(
            driverText
        )

        // --------------------------------------------------------
        // DRIVER PHONE
        // --------------------------------------------------------

        if (driverPhone.isNotEmpty()) {

            val phoneText =
                TextView(this)

            phoneText.text =
                "📞 Phone: $driverPhone"

            phoneText.textSize =
                16f

            phoneText.setPadding(
                0,
                8,
                0,
                0
            )

            card.addView(
                phoneText
            )
        }

        // --------------------------------------------------------
        // VEHICLE
        // --------------------------------------------------------

        val vehicleText =
            TextView(this)

        vehicleText.text =
            "Vehicle: $carMake $carModel ($carColor)"

        vehicleText.textSize =
            16f

        vehicleText.setPadding(
            0,
            8,
            0,
            0
        )

        card.addView(
            vehicleText
        )

        // --------------------------------------------------------
        // CAR REGISTRATION
        // --------------------------------------------------------

        val registrationText =
            TextView(this)

        registrationText.text =
            "🚘 Car Number: $carRegistration"

        registrationText.textSize =
            16f

        registrationText.setPadding(
            0,
            8,
            0,
            0
        )

        card.addView(
            registrationText
        )

        // --------------------------------------------------------
        // DATE AND TIME
        // --------------------------------------------------------

        val dateTimeText =
            TextView(this)

        dateTimeText.text =
            "📅 $date    🕐 $time"

        dateTimeText.textSize =
            16f

        dateTimeText.setPadding(
            0,
            8,
            0,
            0
        )

        card.addView(
            dateTimeText
        )

        // --------------------------------------------------------
        // PRICE
        // --------------------------------------------------------

        val priceText =
            TextView(this)

        priceText.text =
            "💰 Price: Rs ${
                String.format(
                    Locale.getDefault(),
                    "%.2f",
                    price
                )
            } per passenger"

        priceText.textSize =
            17f

        priceText.setTypeface(
            null,
            Typeface.BOLD
        )

        priceText.setPadding(
            0,
            8,
            0,
            0
        )

        card.addView(
            priceText
        )

        // --------------------------------------------------------
        // STATUS
        // --------------------------------------------------------

        val statusText =
            TextView(this)

        statusText.text =
            when (status) {

                "Pending" ->
                    "🟡 Status: PENDING"

                "Approved" ->
                    "🟢 Status: APPROVED"

                "Rejected" ->
                    "🔴 Status: REJECTED"

                "Cancelled" ->
                    "⚫ Status: CANCELLED"

                else ->
                    "Status: $status"
            }

        statusText.textSize =
            17f

        statusText.setTypeface(
            null,
            Typeface.BOLD
        )

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
        // CANCEL BUTTON
        // --------------------------------------------------------

        if (
            status == "Pending" ||
            status == "Approved"
        ) {

            val cancelButton =
                Button(this)

            cancelButton.text =
                "CANCEL BOOKING"

            cancelButton.setOnClickListener {

                cancelBooking(
                    document.id
                )
            }

            card.addView(
                cancelButton
            )
        }

        // --------------------------------------------------------
        // CARD MARGIN
        // --------------------------------------------------------

        val layoutParams =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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
    // CANCEL BOOKING
    // ============================================================
    private fun cancelBooking(
        bookingId: String
    ) {

        val passengerId = auth.currentUser?.uid

        if (passengerId.isNullOrBlank()) {

            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

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
                throw Exception(
                    "Booking no longer exists."
                )
            }

            // Verify that this booking belongs to the logged-in passenger.
            val bookingPassengerId =
                bookingSnapshot.getString("passengerId") ?: ""

            if (bookingPassengerId != passengerId) {
                throw Exception(
                    "You are not authorised to cancel this booking."
                )
            }

            val currentStatus =
                bookingSnapshot.getString("status") ?: ""

            if (
                currentStatus != "Pending" &&
                currentStatus != "Approved"
            ) {
                throw Exception(
                    "This booking cannot be cancelled."
                )
            }

            val rideId =
                bookingSnapshot.getString("rideId")

            if (rideId.isNullOrBlank()) {
                throw Exception(
                    "Ride information is missing."
                )
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
            // 3. RESTORE SEATS ONLY FOR APPROVED BOOKINGS
            // ----------------------------------------------------

            if (currentStatus == "Approved") {

                val rideReference =
                    db.collection("rides")
                        .document(rideId)

                val rideSnapshot =
                    transaction.get(rideReference)

                if (!rideSnapshot.exists()) {
                    throw Exception(
                        "Ride no longer exists."
                    )
                }

                val availableSeats =
                    (rideSnapshot.get("availableSeats") as? Number)
                        ?.toInt() ?: 0

                val totalSeats =
                    (rideSnapshot.get("totalSeats") as? Number)
                        ?.toInt() ?: 0

                if (totalSeats <= 0) {
                    throw Exception(
                        "Invalid total seat capacity."
                    )
                }

                val newAvailableSeats =
                    minOf(
                        availableSeats + numberOfPassengers,
                        totalSeats
                    )

                transaction.update(
                    rideReference,
                    "availableSeats",
                    newAvailableSeats
                )
            }

            // ----------------------------------------------------
            // 4. UPDATE BOOKING STATUS
            // ----------------------------------------------------

            transaction.update(
                bookingReference,
                mapOf(
                    "status" to "Cancelled",
                    "cancelledAt" to
                            com.google.firebase.firestore.FieldValue
                                .serverTimestamp()
                )
            )

            null

        }.addOnSuccessListener {

            Toast.makeText(
                this,
                "Booking cancelled successfully.",
                Toast.LENGTH_SHORT
            ).show()

            loadMyBookings()

        }.addOnFailureListener { exception ->

            Toast.makeText(
                this,
                "Could not cancel booking: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


}