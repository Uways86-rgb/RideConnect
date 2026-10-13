
package com.example.rideconnect.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RideSearchActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var pickupInput: EditText
    private lateinit var destinationInput: EditText
    private lateinit var minSeatsInput: EditText
    private lateinit var maxPriceInput: EditText
    private lateinit var dateText: TextView
    private lateinit var regionSpinner: Spinner
    private lateinit var resultsContainer: LinearLayout

    private var selectedDate: String? = null

    private val regions = listOf(
        "All regions",
        "Black River",
        "Flacq",
        "Grand Port",
        "Moka",
        "Pamplemousses",
        "Plaines Wilhems",
        "Port Louis",
        "Rivière du Rempart",
        "Savanne",
        "Rodrigues"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_search)

        db = FirebaseFirestore.getInstance()

        pickupInput = findViewById(R.id.searchPickupInput)
        destinationInput = findViewById(R.id.searchDestinationInput)
        minSeatsInput = findViewById(R.id.minSeatsInput)
        maxPriceInput = findViewById(R.id.maxPriceInput)
        dateText = findViewById(R.id.searchDateText)
        regionSpinner = findViewById(R.id.searchRegionSpinner)
        resultsContainer = findViewById(R.id.resultsContainer)

        val regionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            regions
        )

        regionAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        regionSpinner.adapter = regionAdapter
        regionSpinner.setSelection(0)

        val selectDateButton =
            findViewById<Button>(R.id.selectSearchDateButton)

        val searchButton =
            findViewById<Button>(R.id.searchRidesButton)

        val clearButton =
            findViewById<Button>(R.id.clearFiltersButton)

        selectDateButton.setOnClickListener {
            showDatePicker()
        }

        searchButton.setOnClickListener {
            searchRides()
        }

        clearButton.setOnClickListener {
            clearFilters()
        }

        // Load all available rides when the screen opens.
        searchRides()
    }

    // ------------------------------------------------------------
    // DATE PICKER
    // ------------------------------------------------------------

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->

                val selectedCalendar = Calendar.getInstance()

                selectedCalendar.set(
                    year,
                    month,
                    dayOfMonth
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

        datePicker.datePicker.minDate = System.currentTimeMillis()
        datePicker.show()
    }

    // ------------------------------------------------------------
    // SEARCH RIDES
    // ------------------------------------------------------------

    private fun searchRides() {

        val pickup = pickupInput.text.toString().trim()
        val destination = destinationInput.text.toString().trim()
        val minSeatsText = minSeatsInput.text.toString().trim()
        val maxPriceText = maxPriceInput.text.toString().trim()

        val selectedRegion = regionSpinner.selectedItem?.toString()
            ?: "All regions"

        val minSeats = if (minSeatsText.isEmpty()) {
            null
        } else {
            minSeatsText.toIntOrNull()
        }

        val maxPrice = if (maxPriceText.isEmpty()) {
            null
        } else {
            maxPriceText.toDoubleOrNull()
        }

        if (
            minSeatsText.isNotEmpty() &&
            (minSeats == null || minSeats <= 0)
        ) {
            showMessage("Please enter a valid minimum number of seats")
            return
        }

        if (
            maxPriceText.isNotEmpty() &&
            (maxPrice == null || !maxPrice.isFinite() || maxPrice < 0)
        ) {
            showMessage("Please enter a valid maximum price")
            return
        }

        resultsContainer.removeAllViews()

        val loadingText = TextView(this).apply {
            text = "Searching for available rides..."
            textSize = 18f
        }

        resultsContainer.addView(loadingText)

        db.collection("rides")
            .whereEqualTo("status", "Available")
            .get()
            .addOnSuccessListener { documents ->

                resultsContainer.removeAllViews()

                var resultCount = 0

                for (document in documents) {

                    val ridePickup =
                        document.getString("pickup") ?: ""

                    val rideDestination =
                        document.getString("destination") ?: ""

                    val rideDate =
                        document.getString("date") ?: ""

                    val rideRegion =
                        document.getString("region") ?: ""

                    val availableSeats =
                        document.getLong("availableSeats")?.toInt() ?: 0

                    val price =
                        (document.get("pricePerPassenger") as? Number)
                            ?.toDouble() ?: 0.0

                    // REGION FILTER
                    // Older rides without a region are shown only when
                    // "All regions" is selected.

                    if (
                        selectedRegion != "All regions" &&
                        !rideRegion.equals(
                            selectedRegion,
                            ignoreCase = true
                        )
                    ) {
                        continue
                    }

                    // PICKUP FILTER

                    if (
                        pickup.isNotEmpty() &&
                        !ridePickup.contains(
                            pickup,
                            ignoreCase = true
                        )
                    ) {
                        continue
                    }

                    // DESTINATION FILTER

                    if (
                        destination.isNotEmpty() &&
                        !rideDestination.contains(
                            destination,
                            ignoreCase = true
                        )
                    ) {
                        continue
                    }

                    // DATE FILTER

                    if (
                        selectedDate != null &&
                        rideDate != selectedDate
                    ) {
                        continue
                    }

                    // SEAT FILTER

                    if (
                        minSeats != null &&
                        availableSeats < minSeats
                    ) {
                        continue
                    }

                    // PRICE FILTER

                    if (
                        maxPrice != null &&
                        price > maxPrice
                    ) {
                        continue
                    }

                    addRideResult(document.id, document)
                    resultCount++
                }

                if (resultCount == 0) {
                    val noResultsText = TextView(this).apply {
                        text = "No available rides match your search."
                        textSize = 18f
                        setPadding(10, 30, 10, 30)
                    }

                    resultsContainer.addView(noResultsText)
                }
            }
            .addOnFailureListener { exception ->

                resultsContainer.removeAllViews()

                showMessage(
                    "Could not search rides: ${exception.message}",
                    Toast.LENGTH_LONG
                )
            }
    }

    // ------------------------------------------------------------
    // DISPLAY RIDE
    // ------------------------------------------------------------

    private fun addRideResult(
        rideId: String,
        document: DocumentSnapshot
    ) {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 25, 25, 25)
            setBackgroundColor(0xFFEEEEEE.toInt())
        }

        val driverName =
            document.getString("driverName") ?: "Unknown driver"

        val carMake =
            document.getString("carMake") ?: ""

        val carModel =
            document.getString("carModel") ?: ""

        val carColor =
            document.getString("carColor") ?: ""

        val carRegistration =
            document.getString("carRegistration") ?: ""

        val pickup =
            document.getString("pickup") ?: ""

        val destination =
            document.getString("destination") ?: ""

        val region =
            document.getString("region") ?: "Not specified"

        val date =
            document.getString("date") ?: ""

        val time =
            document.getString("time") ?: ""

        val seats =
            document.getLong("availableSeats")?.toInt() ?: 0

        val price =
            (document.get("pricePerPassenger") as? Number)
                ?.toDouble() ?: 0.0

        fun addText(
            text: String,
            size: Float,
            bold: Boolean = false,
            topPadding: Int = 8
        ) {
            val textView = TextView(this).apply {
                this.text = text
                textSize = size

                if (bold) {
                    setTypeface(null, Typeface.BOLD)
                }

                setPadding(0, topPadding, 0, 0)
            }

            card.addView(textView)
        }

        addText(
            "🚗 $pickup → $destination",
            20f,
            bold = true,
            topPadding = 0
        )

        addText("Operational region: $region", 16f)
        addText("Driver: $driverName", 17f)
        addText("Vehicle: $carMake $carModel ($carColor)", 16f)
        addText("🚘 Car Number: $carRegistration", 16f)
        addText("📅 $date    🕐 $time", 16f)
        addText("💺 Available seats: $seats", 16f)

        addText(
            "💰 Price: Rs ${
                String.format(Locale.getDefault(), "%.2f", price)
            } per passenger",
            17f,
            bold = true
        )

        val bookButton = Button(this).apply {
            text = "BOOK RIDE"

            setOnClickListener {
                val intent = Intent(
                    this@RideSearchActivity,
                    BookingConfirmationActivity::class.java
                )

                intent.putExtra("RIDE_ID", rideId)
                startActivity(intent)
            }
        }

        card.addView(bookButton)

        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 25)
        }

        card.layoutParams = layoutParams
        resultsContainer.addView(card)
    }

    // ------------------------------------------------------------
    // CLEAR FILTERS
    // ------------------------------------------------------------

    private fun clearFilters() {

        pickupInput.text.clear()
        destinationInput.text.clear()
        minSeatsInput.text.clear()
        maxPriceInput.text.clear()

        selectedDate = null
        dateText.text = "Date: Not selected"

        regionSpinner.setSelection(0)

        searchRides()
    }

    // ------------------------------------------------------------
    // MESSAGE HELPER
    // ------------------------------------------------------------

    private fun showMessage(
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        Toast.makeText(this, message, duration).show()
    }
}
