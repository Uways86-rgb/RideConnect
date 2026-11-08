
package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PassengerAccountActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText

    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_passenger_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Handle Android system navigation bar insets.
        val bottomNavigation = findViewById<View>(
            R.id.passengerAccountBottomNavigation
        )

        val initialBottomPadding = bottomNavigation.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(
            bottomNavigation
        ) { view, insets ->

            val navigationBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottomPadding + navigationBarInsets.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(bottomNavigation)

        // Initialise profile fields.
        nameInput = findViewById(R.id.passengerNameInput)
        emailInput = findViewById(R.id.passengerEmailInput)
        phoneInput = findViewById(R.id.passengerPhoneInput)

        saveButton = findViewById(
            R.id.savePassengerAccountButton
        )

        // Load the passenger's profile.
        loadPassengerProfile()

        // Save profile changes.
        saveButton.setOnClickListener {
            savePassengerProfile()
        }

        // Help and support.
        findViewById<Button>(R.id.passengerSupportButton)
            .setOnClickListener {
                Toast.makeText(
                    this,
                    "Please contact RideConnect support.",
                    Toast.LENGTH_LONG
                ).show()
            }

        // Log out.
        findViewById<Button>(R.id.passengerLogoutButton)
            .setOnClickListener {
                logoutPassenger()
            }

        // Bottom navigation.
        findViewById<TextView>(
            R.id.passengerAccountHomeNavItem
        ).setOnClickListener {
            navigateTo(PassengerDashboardActivity::class.java)
        }

        findViewById<TextView>(
            R.id.passengerAccountExploreNavItem
        ).setOnClickListener {
            navigateTo(RideSearchActivity::class.java)
        }

        findViewById<TextView>(
            R.id.passengerAccountBookingsNavItem
        ).setOnClickListener {
            navigateTo(MyBookingsActivity::class.java)
        }

        findViewById<TextView>(
            R.id.passengerAccountNavItem
        ).setOnClickListener {
            // Already on the Passenger Account screen.
        }
    }

    // ---------------------------------------------------------
    // LOAD PASSENGER PROFILE
    // ---------------------------------------------------------

    private fun loadPassengerProfile() {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            logoutPassenger()
            return
        }

        emailInput.setText(auth.currentUser?.email ?: "")

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->

                if (document.exists()) {

                    nameInput.setText(
                        document.getString("name") ?: ""
                    )

                    phoneInput.setText(
                        document.getString("phone") ?: ""
                    )

                    findViewById<TextView>(
                        R.id.passengerAccountGreetingText
                    ).text = "Manage your personal information"
                } else {
                    Toast.makeText(
                        this,
                        "Passenger profile not found.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Unable to load profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // SAVE PASSENGER PROFILE
    // ---------------------------------------------------------

    private fun savePassengerProfile() {

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Toast.makeText(
                this,
                "Please log in again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()

        if (name.isEmpty()) {
            nameInput.error = "Please enter your full name"
            nameInput.requestFocus()
            return
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Please enter your phone number"
            phoneInput.requestFocus()
            return
        }

        saveButton.isEnabled = false
        saveButton.text = "Saving..."

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone
        )

        db.collection("users")
            .document(userId)
            .update(updates)
            .addOnSuccessListener {

                saveButton.isEnabled = true
                saveButton.text = "Save Changes"

                Toast.makeText(
                    this,
                    "Profile updated successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { exception ->

                saveButton.isEnabled = true
                saveButton.text = "Save Changes"

                Toast.makeText(
                    this,
                    "Could not update profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // BOTTOM NAVIGATION
    // ---------------------------------------------------------

    private fun navigateTo(destination: Class<*>) {

        val intent = Intent(this, destination)

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

        startActivity(intent)
    }

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------

    private fun logoutPassenger() {
        auth.signOut()

        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }
}
