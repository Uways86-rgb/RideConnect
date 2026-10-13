package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_splash)

        val passengerButton =
            findViewById<Button>(R.id.passengerButton)

        val driverButton =
            findViewById<Button>(R.id.driverButton)

        // PASSENGER BUTTON
        passengerButton.setOnClickListener {

            val intent = Intent(
                this,
                LoginActivity::class.java
            )

            intent.putExtra("SELECTED_ROLE", "Passenger")

            startActivity(intent)
        }

        // DRIVER BUTTON
        driverButton.setOnClickListener {

            val intent = Intent(
                this,
                LoginActivity::class.java
            )

            intent.putExtra("SELECTED_ROLE", "Driver")

            startActivity(intent)
        }
    }
}