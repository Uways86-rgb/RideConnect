
package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.example.rideconnect.notifications.FcmTokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // FIREBASE
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // GET SELECTED ROLE
        val selectedRole = intent.getStringExtra("SELECTED_ROLE")

        // UI ELEMENTS
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val togglePasswordVisibility =
            findViewById<ImageButton>(R.id.togglePasswordVisibility)

        var isPasswordVisible = false

        // SHOW / HIDE PASSWORD
        togglePasswordVisibility.setOnClickListener {

            isPasswordVisible = !isPasswordVisible

            if (isPasswordVisible) {

                passwordInput.transformationMethod =
                    HideReturnsTransformationMethod.getInstance()

                togglePasswordVisibility.setImageResource(
                    R.drawable.ic_visibility_off
                )

                togglePasswordVisibility.contentDescription = "Hide password"

            } else {

                passwordInput.transformationMethod =
                    PasswordTransformationMethod.getInstance()

                togglePasswordVisibility.setImageResource(
                    R.drawable.ic_visibility
                )

                togglePasswordVisibility.contentDescription = "Show password"
            }

            // Keep cursor at the end of the password
            passwordInput.setSelection(passwordInput.text.length)
        }

        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerButton = findViewById<Button>(R.id.registerButton)

        // FORGOT PASSWORD
        val forgotPasswordText =
            findViewById<TextView>(R.id.forgotPasswordText)

        forgotPasswordText.setOnClickListener {

            val email = emailInput.text.toString().trim()

            // CHECK EMPTY EMAIL
            if (email.isEmpty()) {
                emailInput.error = "Please enter your email address"
                emailInput.requestFocus()
                return@setOnClickListener
            }

            // VALIDATE EMAIL FORMAT
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Please enter a valid email address"
                emailInput.requestFocus()
                return@setOnClickListener
            }

            // PREVENT REPEATED REQUESTS
            forgotPasswordText.isEnabled = false

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "If an account exists for this email, a password reset link will be sent.",
                        Toast.LENGTH_LONG
                    ).show()

                    forgotPasswordText.isEnabled = true
                }
                .addOnFailureListener {

                    Toast.makeText(
                        this,
                        "Unable to send password reset email. Please try again later.",
                        Toast.LENGTH_LONG
                    ).show()

                    forgotPasswordText.isEnabled = true
                }
        }

        // REGISTER BUTTON
        registerButton.setOnClickListener {

            val registerIntent = Intent(
                this,
                RegisterActivity::class.java
            )

            registerIntent.putExtra(
                "SELECTED_ROLE",
                selectedRole
            )

            startActivity(registerIntent)
        }

        // LOGIN BUTTON
        loginButton.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // CHECK EMPTY FIELDS
            if (email.isEmpty() || password.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please enter email and password",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // CHECK SELECTED ROLE
            if (selectedRole == null) {

                Toast.makeText(
                    this,
                    "Please select Passenger or Driver first",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            if (selectedRole != "Passenger" &&
                selectedRole != "Driver"
            ) {

                Toast.makeText(
                    this,
                    "Invalid account type selected",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnClickListener
            }

            // DISABLE LOGIN BUTTON
            loginButton.isEnabled = false

            // FIREBASE LOGIN
            auth.signInWithEmailAndPassword(
                email,
                password
            )
                .addOnSuccessListener {

                    // REGISTER FCM TOKEN AFTER AUTHENTICATION
                    FcmTokenManager.refreshAndSaveToken()

                    // GET USER ID
                    val userId = auth.currentUser?.uid

                    if (userId == null) {

                        loginButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Unable to get user information",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    // LOAD USER PROFILE FROM FIRESTORE
                    firestore.collection("users")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { document ->

                            // USER PROFILE DOES NOT EXIST
                            if (!document.exists()) {

                                loginButton.isEnabled = true

                                Toast.makeText(
                                    this,
                                    "User profile not found in Firestore",
                                    Toast.LENGTH_LONG
                                ).show()

                                return@addOnSuccessListener
                            }

                            // GET REGISTERED ACCOUNT TYPE
                            val accountType =
                                document.getString("accountType")

                            // CHECK ROLE
                            if (selectedRole == accountType) {

                                // PASSENGER
                                if (selectedRole == "Passenger") {

                                    Toast.makeText(
                                        this,
                                        "Passenger login successful",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    val dashboardIntent = Intent(
                                        this,
                                        PassengerDashboardActivity::class.java
                                    )

                                    startActivity(dashboardIntent)
                                    finish()
                                }

                                // DRIVER
                                else if (selectedRole == "Driver") {

                                    Toast.makeText(
                                        this,
                                        "Driver login successful",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    val dashboardIntent = Intent(
                                        this,
                                        DriverDashboardActivity::class.java
                                    )

                                    startActivity(dashboardIntent)
                                    finish()
                                }

                                // INVALID ROLE
                                else {

                                    loginButton.isEnabled = true

                                    Toast.makeText(
                                        this,
                                        "Invalid account type",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }

                            // WRONG ROLE SELECTED
                            else {

                                loginButton.isEnabled = true

                                Toast.makeText(
                                    this,
                                    "This account is registered as $accountType. Please select $accountType.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener {

                            loginButton.isEnabled = true

                            Toast.makeText(
                                this,
                                "Failed to load user data. Please try again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener {

                    // RESTORE LOGIN BUTTON
                    loginButton.isEnabled = true

                    // FRIENDLY LOGIN ERROR MESSAGE
                    Toast.makeText(
                        this,
                        "Incorrect email or password. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
