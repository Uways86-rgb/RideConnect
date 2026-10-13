
package com.example.rideconnect.activities

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DriverAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText

    private lateinit var carMakeInput: EditText
    private lateinit var carModelInput: EditText
    private lateinit var carColorInput: EditText
    private lateinit var carRegistrationInput: EditText

    private lateinit var assignedRegionSpinner: Spinner
    private lateinit var saveButton: Button

    private val regions = listOf(
        "Select assigned region",
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
        setContentView(R.layout.activity_driver_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        phoneInput = findViewById(R.id.phoneInput)

        carMakeInput = findViewById(R.id.carMakeInput)
        carModelInput = findViewById(R.id.carModelInput)
        carColorInput = findViewById(R.id.carColorInput)
        carRegistrationInput = findViewById(R.id.carRegistrationInput)

        assignedRegionSpinner =
            findViewById(R.id.assignedRegionSpinner)

        saveButton = findViewById(R.id.saveButton)

        val regionAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            regions
        )
        regionAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        assignedRegionSpinner.adapter = regionAdapter

        loadDriverAccount()

        saveButton.setOnClickListener {
            saveDriverAccount()
        }
    }

    private fun loadDriverAccount() {
        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    Toast.makeText(
                        this,
                        "Driver account not found",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                nameInput.setText(document.getString("name") ?: "")
                emailInput.setText(document.getString("email") ?: "")
                phoneInput.setText(document.getString("phone") ?: "")

                carMakeInput.setText(
                    document.getString("carMake") ?: ""
                )
                carModelInput.setText(
                    document.getString("carModel") ?: ""
                )
                carColorInput.setText(
                    document.getString("carColor") ?: ""
                )
                carRegistrationInput.setText(
                    document.getString("carRegistration") ?: ""
                )

                val savedRegion =
                    document.getString("assignedRegion")

                val regionPosition = regions.indexOf(savedRegion)

                assignedRegionSpinner.setSelection(
                    if (regionPosition >= 0) regionPosition else 0
                )
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Failed to load account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun saveDriverAccount() {
        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val carMake = carMakeInput.text.toString().trim()
        val carModel = carModelInput.text.toString().trim()
        val carColor = carColorInput.text.toString().trim()
        val carRegistration =
            carRegistrationInput.text.toString().trim()

        val assignedRegion =
            assignedRegionSpinner.selectedItem?.toString() ?: ""

        if (name.isEmpty()) {
            nameInput.error = "Please enter your name"
            nameInput.requestFocus()
            return
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Please enter your phone number"
            phoneInput.requestFocus()
            return
        }

        if (assignedRegion == regions.first()) {
            Toast.makeText(
                this,
                "Please select your assigned region",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (carMake.isEmpty()) {
            carMakeInput.error = "Please enter the car make"
            carMakeInput.requestFocus()
            return
        }

        if (carModel.isEmpty()) {
            carModelInput.error = "Please enter the car model"
            carModelInput.requestFocus()
            return
        }

        if (carColor.isEmpty()) {
            carColorInput.error = "Please enter the car colour"
            carColorInput.requestFocus()
            return
        }

        if (carRegistration.isEmpty()) {
            carRegistrationInput.error =
                "Please enter the car registration number"
            carRegistrationInput.requestFocus()
            return
        }

        saveButton.isEnabled = false

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "carMake" to carMake,
            "carModel" to carModel,
            "carColor" to carColor,
            "carRegistration" to carRegistration,
            "assignedRegion" to assignedRegion
        )

        db.collection("users")
            .document(driverId)
            .update(updates)
            .addOnSuccessListener {
                saveButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Account and assigned region saved successfully!",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { exception ->
                saveButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Failed to update account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
