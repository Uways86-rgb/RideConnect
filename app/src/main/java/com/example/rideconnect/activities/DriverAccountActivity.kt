package com.example.rideconnect.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent

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

    private lateinit var choosePreferredAreasButton: Button
    private lateinit var preferredAreasSummary: TextView
    private lateinit var saveButton: Button

    // Districts available for optional operating-area preferences.
    private val operatingAreas = listOf(
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

    // Stores multiple selected districts.
    private val selectedPreferredAreas =
        linkedSetOf<String>()

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
        carRegistrationInput =
            findViewById(R.id.carRegistrationInput)

        choosePreferredAreasButton =
            findViewById(R.id.choosePreferredAreasButton)

        preferredAreasSummary =
            findViewById(R.id.preferredAreasSummary)

        saveButton = findViewById(R.id.saveButton)

        loadDriverAccount()

        choosePreferredAreasButton.setOnClickListener {
            showPreferredAreasDialog()
        }

        saveButton.setOnClickListener {
            saveDriverAccount()
        }

        findViewById<Button>(R.id.driverLogoutButton)
            .setOnClickListener {
                logoutDriver()
            }
    }

    private fun showPreferredAreasDialog() {

        val checkedItems = operatingAreas.map { area ->
            selectedPreferredAreas.contains(area)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("Choose Preferred Operating Areas")
            .setMultiChoiceItems(
                operatingAreas.toTypedArray(),
                checkedItems
            ) { _, which, isChecked ->

                val selectedArea = operatingAreas[which]

                if (isChecked) {
                    selectedPreferredAreas.add(selectedArea)
                } else {
                    selectedPreferredAreas.remove(selectedArea)
                }
            }
            .setPositiveButton("Done") { _, _ ->
                updatePreferredAreasSummary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePreferredAreasSummary() {

        preferredAreasSummary.text =
            if (selectedPreferredAreas.isEmpty()) {

                "No preferred areas selected. You can still accept rides anywhere in Mauritius."

            } else {

                "Your preferred areas:\n\n" +
                        selectedPreferredAreas.joinToString(
                            separator = "\n"
                        ) { "• $it" }
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

                nameInput.setText(
                    document.getString("name") ?: ""
                )

                emailInput.setText(
                    document.getString("email") ?: ""
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

                // Load previously saved preferred areas.
                selectedPreferredAreas.clear()

                val savedAreas =
                    document.get("preferredAreas") as? List<*>

                savedAreas
                    ?.filterIsInstance<String>()
                    ?.filter { it in operatingAreas }
                    ?.forEach { selectedPreferredAreas.add(it) }

                updatePreferredAreasSummary()
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
            "travelCoverage" to "Entire Mauritius",
            "preferredAreas" to selectedPreferredAreas.toList()
        )

        db.collection("users")
            .document(driverId)
            .update(updates)
            .addOnSuccessListener {

                saveButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Account and travel preferences saved successfully!",
                    Toast.LENGTH_LONG
                ).show()

                updatePreferredAreasSummary()
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

    private fun logoutDriver() {
        auth.signOut()

        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}
