package com.example.rideconnect.activities

import android.content.Intent

import android.app.DatePickerDialog
import java.util.Calendar

import android.app.AlertDialog
import android.text.InputType
import com.google.firebase.auth.EmailAuthProvider

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


    // Driving information
    private lateinit var registrationDateInput: EditText
    private lateinit var drivingExperienceInput: EditText


    // Vehicle information
    private lateinit var carMakeInput: EditText
    private lateinit var carModelInput: EditText
    private lateinit var carColorInput: EditText
    private lateinit var carRegistrationInput: EditText

    // Buttons
    private lateinit var saveButton: Button
    private lateinit var selectedLocationsButton: Button
    private lateinit var logoutButton: Button

    private lateinit var deleteAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Personal information
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        phoneInput = findViewById(R.id.phoneInput)

        // Driving information
        registrationDateInput =
            findViewById(R.id.registrationDateInput)

        drivingExperienceInput =
            findViewById(R.id.drivingExperienceInput)

        // Vehicle information
        carMakeInput = findViewById(R.id.carMakeInput)
        carModelInput = findViewById(R.id.carModelInput)
        carColorInput = findViewById(R.id.carColorInput)
        carRegistrationInput = findViewById(R.id.carRegistrationInput)

        // Buttons
        saveButton = findViewById(R.id.saveButton)
        selectedLocationsButton = findViewById(R.id.selectedLocationsButton)
        logoutButton = findViewById(R.id.driverLogoutButton)

        deleteAccountButton =
            findViewById(R.id.deleteDriverAccountButton)

        // Open the separate Selected Locations page
        selectedLocationsButton.setOnClickListener {
            startActivity(
                Intent(this, SelectedLocationsActivity::class.java)
            )
        }

        // Allow the driver to select a registration date
        registrationDateInput.setOnClickListener {

            val calendar = Calendar.getInstance()

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->

                    val formattedDate = String.format(
                        "%02d/%02d/%04d",
                        selectedDay,
                        selectedMonth + 1,
                        selectedYear
                    )

                    registrationDateInput.setText(formattedDate)
                },
                year,
                month,
                day
            )

            datePicker.datePicker.maxDate = calendar.timeInMillis
            datePicker.show()
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

        deleteAccountButton.setOnClickListener {
            confirmDeleteDriverAccount()
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

                    // Load driving information
                    registrationDateInput.setText(
                        document.getString("registrationDate") ?: ""
                    )

                    val experienceYears =
                        document.getLong("drivingExperienceYears")

                    drivingExperienceInput.setText(
                        experienceYears?.toString() ?: ""
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

        // Get driving information
        val registrationDate =
            registrationDateInput.text.toString().trim()

        val drivingExperienceYears =
            drivingExperienceInput.text.toString().trim().toIntOrNull()

        // Get vehicle information
        val carMake = carMakeInput.text.toString().trim()
        val carModel = carModelInput.text.toString().trim()
        val carColor = carColorInput.text.toString().trim()
        val carRegistration = carRegistrationInput.text.toString().trim()

        // Validate required fields
        if (
            name.isEmpty() ||
            registrationDate.isEmpty() ||
            drivingExperienceYears == null ||
            drivingExperienceYears < 0 ||
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
            "registrationDate" to registrationDate,
            "drivingExperienceYears" to drivingExperienceYears!!,
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

    private fun confirmDeleteDriverAccount() {

        AlertDialog.Builder(this)
            .setTitle("Delete Driver Account")
            .setMessage(
                "Are you sure you want to permanently delete your driver account? " +
                        "This action cannot be undone."
            )
            .setPositiveButton("Continue") { _, _ ->
                showDeletePasswordDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showDeletePasswordDialog() {

        val passwordInput = EditText(this).apply {
            hint = "Enter your account password"
            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Confirm Your Identity")
            .setMessage("Enter your password to permanently delete your account.")
            .setView(passwordInput)
            .setPositiveButton("Delete Account", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val password =
                        passwordInput.text.toString()

                    if (password.isBlank()) {
                        passwordInput.error = "Password is required"
                        return@setOnClickListener
                    }

                    val currentUser = auth.currentUser

                    if (currentUser == null || currentUser.email == null) {
                        Toast.makeText(
                            this,
                            "Please log in again.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setOnClickListener
                    }

                    val credential = EmailAuthProvider.getCredential(
                        currentUser.email!!,
                        password
                    )

                    dialog.dismiss()

                    currentUser.reauthenticate(credential)
                        .addOnSuccessListener {

                            db.collection("users")
                                .document(currentUser.uid)
                                .delete()
                                .addOnSuccessListener {

                                    db.collection("driverLocations")
                                        .document(currentUser.uid)
                                        .delete()
                                        .addOnSuccessListener {

                                            currentUser.delete()
                                                .addOnSuccessListener {

                                                    Toast.makeText(
                                                        this,
                                                        "Driver account deleted successfully.",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                    logoutDriver()
                                                }
                                                .addOnFailureListener { exception ->
                                                    Toast.makeText(
                                                        this,
                                                        "Authentication deletion failed: ${exception.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                        }
                                        .addOnFailureListener { exception ->
                                            Toast.makeText(
                                                this,
                                                "Failed to delete driver location: ${exception.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                                .addOnFailureListener { exception ->
                                    Toast.makeText(
                                        this,
                                        "Failed to delete driver profile: ${exception.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                this,
                                "Incorrect password or reauthentication failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
        }

        dialog.show()
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
