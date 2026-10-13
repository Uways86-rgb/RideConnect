package com.example.rideconnect.activities

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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

        auth =
            FirebaseAuth.getInstance()

        db =
            FirebaseFirestore.getInstance()

        historyContainer =
            findViewById(R.id.historyContainer)

        emptyHistoryText =
            findViewById(R.id.emptyHistoryText)

        loadRideHistory()
    }

    private fun loadRideHistory() {

        val userId =
            auth.currentUser?.uid

        if (userId == null) {

            emptyHistoryText.text =
                "User is not logged in."

            emptyHistoryText.visibility =
                android.view.View.VISIBLE

            return
        }

        db.collection("rides")
            .whereEqualTo(
                "passengerId",
                userId
            )
            .whereEqualTo(
                "status",
                "Completed"
            )
            .get()
            .addOnSuccessListener { documents ->

                historyContainer.removeAllViews()

                if (documents.isEmpty) {

                    emptyHistoryText.text =
                        "No completed rides yet."

                    emptyHistoryText.visibility =
                        android.view.View.VISIBLE

                    return@addOnSuccessListener
                }

                emptyHistoryText.visibility =
                    android.view.View.GONE

                val rides =
                    documents.documents.sortedByDescending {

                        it.getTimestamp("endTime")
                            ?.seconds
                            ?: 0
                    }

                for (ride in rides) {
                    createRideHistoryCard(ride)
                }
            }
            .addOnFailureListener { exception ->

                emptyHistoryText.text =
                    "Unable to load ride history."

                emptyHistoryText.visibility =
                    android.view.View.VISIBLE

                Toast.makeText(
                    this,
                    "Error: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun createRideHistoryCard(
        ride: com.google.firebase.firestore.DocumentSnapshot
    ) {

        val rideCard =
            LinearLayout(this)

        rideCard.orientation =
            LinearLayout.VERTICAL

        rideCard.setPadding(
            30,
            25,
            30,
            25
        )

        val cardParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.setMargins(
            20,
            10,
            20,
            20
        )

        rideCard.layoutParams =
            cardParams

        rideCard.setBackgroundResource(
            android.R.drawable.dialog_holo_light_frame
        )

        val pickup =
            ride.getString("pickup")
                ?: "Unknown"

        val destination =
            ride.getString("destination")
                ?: "Unknown"

        val driverName =
            ride.getString("driverName")
                ?: "Unknown driver"

        val driverPhone =
            ride.getString("driverPhone")
                ?: "Phone unavailable"

        val carMake =
            ride.getString("carMake")
                ?: "Unknown"

        val carModel =
            ride.getString("carModel")
                ?: "Unknown"

        val carColor =
            ride.getString("carColor")
                ?: "Unknown"

        val carRegistration =
            ride.getString("carRegistration")
                ?: "Unknown"

        val startTime =
            ride.getTimestamp("startTime")

        val endTime =
            ride.getTimestamp("endTime")

        val duration =
            calculateDuration(
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

        val deleteButton =
            Button(this)

        deleteButton.text =
            "🗑 DELETE RIDE"

        deleteButton.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        deleteButton.setOnClickListener {

            showDeleteConfirmation(
                ride.id
            )
        }

        rideCard.addView(
            deleteButton
        )

        historyContainer.addView(
            rideCard
        )
    }

    private fun addText(
        container: LinearLayout,
        text: String,
        size: Int,
        bold: Boolean
    ) {

        val textView =
            TextView(this)

        textView.text =
            text

        textView.textSize =
            size.toFloat()

        if (bold) {
            textView.setTypeface(
                null,
                android.graphics.Typeface.BOLD
            )
        }

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            0,
            6,
            0,
            6
        )

        textView.layoutParams =
            params

        container.addView(
            textView
        )
    }


    private fun formatTimestamp(
        timestamp: Timestamp?
    ): String {

        if (timestamp == null) {
            return "Not available"
        }

        val formatter =
            SimpleDateFormat(
                "dd MMM yyyy, HH:mm",
                Locale.getDefault()
            )

        return formatter.format(
            timestamp.toDate()
        )
    }

    private fun calculateDuration(
        startTime: Timestamp?,
        endTime: Timestamp?
    ): String {

        if (startTime == null ||
            endTime == null
        ) {
            return "Not available"
        }

        val difference =
            endTime.toDate().time -
                    startTime.toDate().time

        if (difference < 0) {
            return "Not available"
        }

        val totalMinutes =
            difference / (1000 * 60)

        val hours =
            totalMinutes / 60

        val minutes =
            totalMinutes % 60

        return if (hours > 0) {
            "${hours}h ${minutes}min"
        } else {
            "${minutes} min"
        }
    }

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

                deleteRide(
                    rideId
                )
            }
            .show()
    }

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