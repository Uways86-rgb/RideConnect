package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DriverAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Personal information
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText

    // Vehicle information
    private lateinit var carMakeInput: EditText
    private lateinit var carModelInput: EditText
    private lateinit var carColorInput: EditText
    private lateinit var carRegistrationInput: EditText

    // Buttons
    private lateinit var saveButton: Button
    private lateinit var selectedLocationsButton: Button
    private lateinit var logoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Personal information
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        phoneInput = findViewById(R.id.phoneInput)

        // Vehicle information
        carMakeInput = findViewById(R.id.carMakeInput)
        carModelInput = findViewById(R.id.carModelInput)
        carColorInput = findViewById(R.id.carColorInput)
        carRegistrationInput = findViewById(R.id.carRegistrationInput)

        // Buttons
        saveButton = findViewById(R.id.saveButton)
        selectedLocationsButton = findViewById(R.id.selectedLocationsButton)
        logoutButton = findViewById(R.id.driverLogoutButton)

        // Open the separate Selected Locations page
        selectedLocationsButton.setOnClickListener {
            startActivity(
                Intent(this, SelectedLocationsActivity::class.java)
            )
        }

        // Load existing driver information
        loadDriverAccount()

        // Save personal and vehicle information
        saveButton.setOnClickListener {
            saveDriverAccount()
        }

        // Logout
        logoutButton.setOnClickListener {
            logoutDriver()
        }
    }

    private fun loadDriverAccount() {

        val driverId = auth.currentUser?.uid

        if (driverId == null) {
            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->

                if (document.exists()) {

                    nameInput.setText(
                        document.getString("name") ?: ""
                    )

                    emailInput.setText(
                        document.getString("email")
                            ?: auth.currentUser?.email
                            ?: ""
                    )

                    phoneInput.setText(
                        document.getString("phone") ?: ""
                    )

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

                } else {
                    Toast.makeText(
                        this,
                        "Driver account information not found.",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                "Please log in again.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Get personal information
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        // Get vehicle information
        val carMake = carMakeInput.text.toString().trim()
        val carModel = carModelInput.text.toString().trim()
        val carColor = carColorInput.text.toString().trim()
        val carRegistration = carRegistrationInput.text.toString().trim()

        // Validate required fields
        if (
            name.isEmpty() ||
            phone.isEmpty() ||
            carMake.isEmpty() ||
            carModel.isEmpty() ||
            carColor.isEmpty() ||
            carRegistration.isEmpty()
        ) {
            Toast.makeText(
                this,
                "Please complete all personal and vehicle information.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Update only personal and vehicle information.
        // Operating-area fields are managed separately by SelectedLocationsActivity.
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "carMake" to carMake,
            "carModel" to carModel,
            "carColor" to carColor,
            "carRegistration" to carRegistration
        )

        db.collection("users")
            .document(driverId)
            .update(updates)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Account information updated successfully.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to update account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun logoutDriver() {

        auth.signOut()

        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }
}
