package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val selectedRole =
            intent.getStringExtra("SELECTED_ROLE")

        if (
            selectedRole != "Passenger" &&
            selectedRole != "Driver"
        ) {
            Toast.makeText(
                this,
                "Please select Passenger or Driver first",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        val nameInput =
            findViewById<EditText>(R.id.nameInput)

        val emailInput =
            findViewById<EditText>(R.id.emailInput)

        val phoneInput =
            findViewById<EditText>(R.id.phoneInput)

        val passwordInput =
            findViewById<EditText>(R.id.passwordInput)

        val vehicleInformationTitle =
            findViewById<TextView>(
                R.id.vehicleInformationTitle
            )

        val carRegistrationInput =
            findViewById<EditText>(
                R.id.carRegistrationInput
            )

        val carMakeInput =
            findViewById<EditText>(
                R.id.carMakeInput
            )

        val carModelInput =
            findViewById<EditText>(
                R.id.carModelInput
            )

        val carColorInput =
            findViewById<EditText>(
                R.id.carColorInput
            )

        val selectedRoleText =
            findViewById<TextView>(
                R.id.selectedRoleText
            )

        val registerButton =
            findViewById<Button>(
                R.id.registerButton
            )

        selectedRoleText.text =
            "Registering as: $selectedRole"

        /*
         * Show vehicle information only
         * when registering as a Driver.
         */
        if (selectedRole == "Driver") {

            vehicleInformationTitle.visibility =
                View.VISIBLE


            carRegistrationInput.visibility =
                View.VISIBLE

            carMakeInput.visibility =
                View.VISIBLE

            carModelInput.visibility =
                View.VISIBLE

            carColorInput.visibility =
                View.VISIBLE


        } else {

            vehicleInformationTitle.visibility =
                View.GONE

            carRegistrationInput.visibility =
                View.GONE

            carMakeInput.visibility =
                View.GONE

            carModelInput.visibility =
                View.GONE

            carColorInput.visibility =
                View.GONE
        }

        registerButton.setOnClickListener {

            val name =
                nameInput.text.toString().trim()

            val email =
                emailInput.text.toString().trim()

            val phone =
                phoneInput.text.toString().trim()

            val password =
                passwordInput.text.toString().trim()

            val carRegistration =
                carRegistrationInput.text.toString().trim()

            val carMake =
                carMakeInput.text.toString().trim()

            val carModel =
                carModelInput.text.toString().trim()

            val carColor =
                carColorInput.text.toString().trim()

            if (
                name.isEmpty() ||
                email.isEmpty() ||
                password.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "Please fill all required fields",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            /*
             * Driver vehicle fields are required.
             */
            if (selectedRole == "Driver") {

                if (carRegistration.isEmpty()) {

                    Toast.makeText(
                        this,
                        "Please enter your car registration number",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (carMake.isEmpty()) {

                    Toast.makeText(
                        this,
                        "Please enter the car make",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (carModel.isEmpty()) {

                    Toast.makeText(
                        this,
                        "Please enter the car model",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (carColor.isEmpty()) {

                    Toast.makeText(
                        this,
                        "Please enter the car colour",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }
            }

            registerButton.isEnabled = false

            auth.createUserWithEmailAndPassword(
                email,
                password
            )
                .addOnSuccessListener {

                    val userId =
                        auth.currentUser?.uid

                    if (userId == null) {

                        registerButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Unable to create user",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    val user =
                        hashMapOf<String, Any>(
                            "name" to name,
                            "email" to email,
                            "phone" to phone,
                            "accountType" to selectedRole
                        )

                    /*
                     * Save vehicle information
                     * only for Drivers.
                     */
                    if (selectedRole == "Driver") {

                        user["carRegistration"] =
                            carRegistration

                        user["carMake"] =
                            carMake

                        user["carModel"] =
                            carModel

                        user["carColor"] =
                            carColor
                    }

                    firestore.collection("users")
                        .document(userId)
                        .set(user)
                        .addOnSuccessListener {

                            Toast.makeText(
                                this,
                                "$selectedRole account created successfully!",
                                Toast.LENGTH_SHORT
                            ).show()

                            val intent =
                                Intent(
                                    this,
                                    LoginActivity::class.java
                                )

                            intent.putExtra(
                                "SELECTED_ROLE",
                                selectedRole
                            )

                            intent.flags =
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { exception ->

                            registerButton.isEnabled = true

                            Toast.makeText(
                                this,
                                "Failed to save user data: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { exception ->

                    registerButton.isEnabled = true

                    Toast.makeText(
                        this,
                        "Registration failed: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}