
package com.example.rideconnect.activities

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class RideHistoryActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var historyContainer: LinearLayout
    private lateinit var emptyHistoryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_ride_history)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        historyContainer = findViewById(R.id.historyContainer)
        emptyHistoryText = findViewById(R.id.emptyHistoryText)

        loadRideHistory()
    }

    // ============================================================
    // LOAD COMPLETED AND REJECTED RIDES
    // ============================================================

    private fun loadRideHistory() {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            emptyHistoryText.text = "User is not logged in."
            emptyHistoryText.visibility = View.VISIBLE
            return
        }

        db.collection("rides")
            .whereEqualTo("passengerId", userId)
            .get()
            .addOnSuccessListener { documents ->

                historyContainer.removeAllViews()

                // Keep completed and rejected instant ride requests.
                val rides = documents.documents
                    .filter { document ->
                        val status = document.getString("status")

                        status == "Completed" || status == "Rejected"
                    }
                    .sortedByDescending { document ->
                        getHistoryTimestamp(document)
                    }

                if (rides.isEmpty()) {

                    emptyHistoryText.text =
                        "No completed rides or rejected requests yet."

                    emptyHistoryText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                emptyHistoryText.visibility = View.GONE

                for (ride in rides) {
                    createRideHistoryCard(ride)
                }
            }
            .addOnFailureListener { exception ->

                emptyHistoryText.text =
                    "Unable to load ride history."

                emptyHistoryText.visibility = View.VISIBLE

                Toast.makeText(
                    this,
                    "Error: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ============================================================
    // GET TIMESTAMP FOR HISTORY SORTING
    // ============================================================

    private fun getHistoryTimestamp(
        ride: DocumentSnapshot
    ): Long {

        val status = ride.getString("status")

        val timestamp = if (status == "Completed") {
            ride.getTimestamp("endTime")
                ?: ride.getTimestamp("timestamp")
        } else {
            ride.getTimestamp("rejectedAt")
                ?: ride.getTimestamp("timestamp")
        }

        return timestamp?.toDate()?.time ?: 0L
    }

    // ============================================================
    // CREATE COMPLETED OR REJECTED HISTORY CARD
    // ============================================================

    private fun createRideHistoryCard(
        ride: DocumentSnapshot
    ) {

        when (ride.getString("status")) {

            "Completed" -> createCompletedRideCard(ride)

            "Rejected" -> createRejectedRideCard(ride)
        }
    }

    // ============================================================
    // COMPLETED RIDE CARD
    // ============================================================

    private fun createCompletedRideCard(
        ride: DocumentSnapshot
    ) {

        val rideCard = LinearLayout(this)

        rideCard.orientation = LinearLayout.VERTICAL

        rideCard.setPadding(
            30,
            25,
            30,
            25
        )

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        cardParams.setMargins(
            20,
            10,
            20,
            20
        )

        rideCard.layoutParams = cardParams

        rideCard.setBackgroundResource(
            android.R.drawable.dialog_holo_light_frame
        )

        val pickup = ride.getString("pickup") ?: "Unknown"

        val destination =
            ride.getString("destination") ?: "Unknown"

        val driverName =
            ride.getString("driverName") ?: "Unknown driver"

        val driverPhone =
            ride.getString("driverPhone") ?: "Phone unavailable"

        val carMake =
            ride.getString("carMake") ?: "Unknown"

        val carModel =
            ride.getString("carModel") ?: "Unknown"

        val carColor =
            ride.getString("carColor") ?: "Unknown"

        val carRegistration =
            ride.getString("carRegistration") ?: "Unknown"

        val startTime = ride.getTimestamp("startTime")
        val endTime = ride.getTimestamp("endTime")

        val duration = calculateDuration(
            startTime,
            endTime
        )

        addText(
            rideCard,
            "🏁 COMPLETED RIDE",
            20,
            true
        )

        addText(
            rideCard,
            "📍 Pickup: $pickup",
            17,
            false
        )

        addText(
            rideCard,
            "📍 Destination: $destination",
            17,
            false
        )

        addText(
            rideCard,
            "🚘 Driver: $driverName",
            17,
            true
        )

        addText(
            rideCard,
            "📞 Phone: $driverPhone",
            17,
            false
        )

        addText(
            rideCard,
            "🚗 Vehicle: $carMake $carModel",
            17,
            true
        )

        addText(
            rideCard,
            "🎨 Colour: $carColor",
            17,
            false
        )

        addText(
            rideCard,
            "🔢 Registration: $carRegistration",
            17,
            true
        )

        addText(
            rideCard,
            "🕐 Start Time: ${formatTimestamp(startTime)}",
            17,
            false
        )

        addText(
            rideCard,
            "🕐 End Time: ${formatTimestamp(endTime)}",
            17,
            false
        )

        addText(
            rideCard,
            "⏱ Duration: $duration",
            17,
            true
        )

        addDeleteButton(
            rideCard,
            ride.id
        )

        historyContainer.addView(rideCard)
    }

    // ============================================================
    // REJECTED RIDE REQUEST CARD
    // ============================================================

    private fun createRejectedRideCard(
        ride: DocumentSnapshot
    ) {

        val rideCard = LinearLayout(this)

        rideCard.orientation = LinearLayout.VERTICAL

        rideCard.setPadding(
            30,
            25,
            30,
            25
        )

        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        cardParams.setMargins(
            20,
            10,
            20,
            20
        )

        rideCard.layoutParams = cardParams

        rideCard.setBackgroundResource(
            android.R.drawable.dialog_holo_light_frame
        )

        val pickup =
            ride.getString("pickup") ?: "Unknown"

        val destination =
            ride.getString("destination") ?: "Unknown"

        val requestTime =
            ride.getTimestamp("timestamp")
                ?: ride.getTimestamp("rejectedAt")

        addText(
            rideCard,
            "❌ REJECTED RIDE REQUEST",
            20,
            true
        )

        addText(
            rideCard,
            "Status: Rejected",
            17,
            true
        )

        addText(
            rideCard,
            "📍 Pickup: $pickup",
            17,
            false
        )

        addText(
            rideCard,
            "📍 Destination: $destination",
            17,
            false
        )

        addText(
            rideCard,
            "🕐 Request Date: ${formatTimestamp(requestTime)}",
            17,
            false
        )

        addText(
            rideCard,
            "Unfortunately, the driver declined your ride request.",
            16,
            true
        )

        addText(
            rideCard,
            "You can submit another ride request.",
            16,
            false
        )

        addDeleteButton(
            rideCard,
            ride.id
        )

        historyContainer.addView(rideCard)
    }

    // ============================================================
    // DELETE BUTTON
    // ============================================================

    private fun addDeleteButton(
        rideCard: LinearLayout,
        rideId: String
    ) {

        val deleteButton = Button(this)

        deleteButton.text = "🗑 DELETE RIDE"

        deleteButton.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        deleteButton.setOnClickListener {
            showDeleteConfirmation(rideId)
        }

        rideCard.addView(deleteButton)
    }

    // ============================================================
    // ADD TEXT TO CARD
    // ============================================================

    private fun addText(
        container: LinearLayout,
        text: String,
        size: Int,
        bold: Boolean
    ) {

        val textView = TextView(this)

        textView.text = text
        textView.textSize = size.toFloat()

        if (bold) {
            textView.setTypeface(
                null,
                android.graphics.Typeface.BOLD
            )
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(
            0,
            6,
            0,
            6
        )

        textView.layoutParams = params

        container.addView(textView)
    }

    // ============================================================
    // FORMAT TIMESTAMP
    // ============================================================

    private fun formatTimestamp(
        timestamp: Timestamp?
    ): String {

        if (timestamp == null) {
            return "Not available"
        }

        val formatter = SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        )

        return formatter.format(timestamp.toDate())
    }

    // ============================================================
    // CALCULATE COMPLETED RIDE DURATION
    // ============================================================

    private fun calculateDuration(
        startTime: Timestamp?,
        endTime: Timestamp?
    ): String {

        if (startTime == null || endTime == null) {
            return "Not available"
        }

        val difference =
            endTime.toDate().time -
                    startTime.toDate().time

        if (difference < 0) {
            return "Not available"
        }

        val totalMinutes = difference / (1000 * 60)

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) {
            "${hours}h ${minutes}min"
        } else {
            "${minutes} min"
        }
    }

    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    private fun showDeleteConfirmation(
        rideId: String
    ) {

        AlertDialog.Builder(this)
            .setTitle("Delete Ride")
            .setMessage(
                "Are you sure you want to delete this ride from your history?"
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "DELETE"
            ) { _, _ ->
                deleteRide(rideId)
            }
            .show()
    }

    // ============================================================
    // DELETE RIDE FROM FIRESTORE
    // ============================================================

    private fun deleteRide(
        rideId: String
    ) {

        db.collection("rides")
            .document(rideId)
            .delete()
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Ride deleted from history",
                    Toast.LENGTH_SHORT
                ).show()

                loadRideHistory()
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to delete ride: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
