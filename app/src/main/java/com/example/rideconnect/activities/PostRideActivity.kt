
package com.example.rideconnect.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    private var selectedDate: String? = null
    private var selectedTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_ride)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        pickupInput = findViewById(R.id.pickupInput)
        destinationInput = findViewById(R.id.destinationInput)
        seatsInput = findViewById(R.id.seatsInput)
        priceInput = findViewById(R.id.priceInput)

        dateText = findViewById(R.id.dateText)
        timeText = findViewById(R.id.timeText)

        val selectDateButton =
            findViewById<Button>(R.id.selectDateButton)

        val selectTimeButton =
            findViewById<Button>(R.id.selectTimeButton)

        postRideButton =
            findViewById(R.id.postRideButton)

        selectDateButton.setOnClickListener {
            showDatePicker()
        }

        selectTimeButton.setOnClickListener {
            showTimePicker()
        }

        postRideButton.setOnClickListener {
            postRide()
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

                selectedDate = formatter.format(selectedCalendar.time)
                dateText.text = "Date: $selectedDate"
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Prevent selecting dates before today.
        datePicker.datePicker.minDate = System.currentTimeMillis()
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

        val pickup = pickupInput.text.toString().trim()
        val destination = destinationInput.text.toString().trim()
        val seatsText = seatsInput.text.toString().trim()
        val priceText = priceInput.text.toString().trim()

        // --------------------------------------------------------
        // VALIDATION
        // --------------------------------------------------------

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

        if (seatsText.isEmpty()) {
            showValidationMessage("Please enter the number of available seats")
            return
        }

        val availableSeats = seatsText.toIntOrNull()

        if (availableSeats == null || availableSeats <= 0) {
            showValidationMessage("Available seats must be greater than 0")
            return
        }

        if (availableSeats > 20) {
            showValidationMessage("Maximum 20 seats allowed")
            return
        }

        if (priceText.isEmpty()) {
            showValidationMessage("Please enter the price per passenger")
            return
        }

        val price = priceText.toDoubleOrNull()

        if (price == null || !price.isFinite() || price < 0) {
            showValidationMessage("Please enter a valid price")
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
                    handlePostFailure("Driver profile not found")
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

                    "date" to selectedDate!!,
                    "time" to selectedTime!!,

                    "availableSeats" to availableSeats,
                    "totalSeats" to availableSeats,
                    "pricePerPassenger" to price,

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
}
