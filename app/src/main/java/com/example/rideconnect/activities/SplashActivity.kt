package com.example.rideconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rideconnect.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status/navigation bars blend smoothly with the top hero
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_splash)

        val rootScroll = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        val carDriftImage = findViewById<View>(R.id.carDriftImage)
        val brandTitleRow = findViewById<View>(R.id.brandTitleRow)
        val logoCarRoof = findViewById<View>(R.id.logoCarRoof)
        val logoSpeedLines = findViewById<View>(R.id.logoSpeedLines)
        val taglineText = findViewById<View>(R.id.taglineText)
        val goldDivider = findViewById<View>(R.id.goldDivider)
        val journeySubtitle = findViewById<View>(R.id.journeySubtitle)
        val welcomeHeaderGroup = findViewById<View>(R.id.welcomeHeaderGroup)
        val passengerButton = findViewById<Button>(R.id.passengerButton)
        val driverButton = findViewById<Button>(R.id.driverButton)
        val footerMottoGroup = findViewById<View>(R.id.footerMottoGroup)

        // Prepare initial states for staggered text & button transitions
        prepareStaggeredViews(
            logoCarRoof,
            logoSpeedLines,
            brandTitleRow,
            taglineText,
            goldDivider,
            journeySubtitle,
            welcomeHeaderGroup,
            passengerButton,
            driverButton,
            footerMottoGroup
        )

        // 1. BMW drift entrance animation
        carDriftImage.post {
            carDriftImage.visibility = View.VISIBLE

            val slide = TranslateAnimation(
                -600f, 0f,
                0f, 0f
            ).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }

            val rotate = RotateAnimation(
                -8f, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }

            val scale = ScaleAnimation(
                0.78f, 1f,
                0.78f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 1500
                interpolator = AccelerateDecelerateInterpolator()
            }

            val fade = AlphaAnimation(0f, 1f).apply {
                duration = 650
            }

            val animationSet = AnimationSet(true).apply {
                addAnimation(slide)
                addAnimation(rotate)
                addAnimation(scale)
                addAnimation(fade)
                fillAfter = true
            }

            carDriftImage.startAnimation(animationSet)
        }

        // 2. Staggered logo, text & button transitions
        runStaggeredTransitions(
            logoCarRoof = logoCarRoof,
            logoSpeedLines = logoSpeedLines,
            brandTitleRow = brandTitleRow,
            taglineText = taglineText,
            goldDivider = goldDivider,
            journeySubtitle = journeySubtitle,
            welcomeHeaderGroup = welcomeHeaderGroup,
            passengerButton = passengerButton,
            driverButton = driverButton,
            footerMottoGroup = footerMottoGroup
        )

        // PASSENGER BUTTON
        passengerButton.setOnClickListener {
            animateButtonPress(passengerButton) {
                navigateToLogin("Passenger")
            }
        }

        // DRIVER BUTTON
        driverButton.setOnClickListener {
            animateButtonPress(driverButton) {
                navigateToLogin("Driver")
            }
        }
    }

    private fun prepareStaggeredViews(vararg views: View) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = if (index < 6) 24f else 32f
            if (view.id == R.id.goldDivider) {
                view.scaleX = 0.2f
            }
        }
    }

    private fun runStaggeredTransitions(
        logoCarRoof: View,
        logoSpeedLines: View,
        brandTitleRow: View,
        taglineText: View,
        goldDivider: View,
        journeySubtitle: View,
        welcomeHeaderGroup: View,
        passengerButton: View,
        driverButton: View,
        footerMottoGroup: View
    ) {
        val easeOut = DecelerateInterpolator(1.8f)

        // Car roof & speed lines slide in
        logoCarRoof.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(220)
            .setDuration(450)
            .setInterpolator(easeOut)
            .start()

        logoSpeedLines.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(280)
            .setDuration(450)
            .setInterpolator(easeOut)
            .start()

        // "RideConnect" title entrance
        brandTitleRow.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(340)
            .setDuration(520)
            .setInterpolator(easeOut)
            .start()

        // Gold tagline
        taglineText.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(520)
            .setDuration(450)
            .setInterpolator(easeOut)
            .start()

        // Glowing gold divider expands across
        goldDivider.animate()
            .alpha(1f)
            .scaleX(1f)
            .translationY(0f)
            .setStartDelay(640)
            .setDuration(500)
            .setInterpolator(easeOut)
            .start()

        // "Your journey starts here."
        journeySubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(760)
            .setDuration(480)
            .setInterpolator(easeOut)
            .start()

        // "Welcome to RideConnect" header
        welcomeHeaderGroup.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(880)
            .setDuration(480)
            .setInterpolator(easeOut)
            .start()

        // Passenger button
        passengerButton.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(1020)
            .setDuration(420)
            .setInterpolator(easeOut)
            .start()

        // Driver button
        driverButton.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(1140)
            .setDuration(420)
            .setInterpolator(easeOut)
            .start()

        // Footer motto
        footerMottoGroup.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(1260)
            .setDuration(400)
            .setInterpolator(easeOut)
            .start()
    }

    private fun animateButtonPress(button: View, onComplete: () -> Unit) {
        button.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(90)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(90)
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }

    private fun navigateToLogin(selectedRole: String) {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra("SELECTED_ROLE", selectedRole)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}