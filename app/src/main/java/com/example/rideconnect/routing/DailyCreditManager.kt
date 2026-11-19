package com.example.rideconnect.routing

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.util.Locale

class DailyCreditManager(context: Context) {

    companion object {
        // RideConnect's internal daily safeguard.
        // This is NOT an account-wide GraphHopper limit.
        const val DAILY_LIMIT = 400.0

        private const val PREFS_NAME = "rideconnect_graphhopper_credits"
        private const val KEY_DATE = "usage_date"
        private const val KEY_USED = "credits_used"

        private val PROCESS_LOCK = Any()
    }

    data class CreditReservation(
        val date: String,
        val credits: Double
    )

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Reserves estimated credits before making an API request.
     *
     * Returns null when the request would exceed the 400-credit
     * daily safeguard or when the local preference write fails.
     */
    fun reserveCredits(credits: Double): CreditReservation? {
        if (!credits.isFinite() || credits <= 0.0) {
            return null
        }

        synchronized(PROCESS_LOCK) {
            val today = getToday()
            resetIfNewDay(today)

            val currentUsage =
                preferences.getFloat(KEY_USED, 0f).toDouble()

            if (currentUsage + credits > DAILY_LIMIT) {
                return null
            }

            val newUsage = currentUsage + credits

            val saved = preferences.edit()
                .putString(KEY_DATE, today)
                .putFloat(KEY_USED, newUsage.toFloat())
                .commit()

            if (!saved) {
                return null
            }

            return CreditReservation(
                date = today,
                credits = credits
            )
        }
    }

    /**
     * Replaces the estimated reservation with the actual
     * credit cost reported by GraphHopper.
     *
     * If GraphHopper does not provide a credit header,
     * the original reservation remains in place.
     */
    fun recordActualCredits(
        reservation: CreditReservation,
        actualCredits: Double
    ) {
        if (!actualCredits.isFinite() || actualCredits < 0.0) {
            return
        }

        synchronized(PROCESS_LOCK) {
            val today = getToday()
            resetIfNewDay(today)

            // Do not apply yesterday's request to today's usage.
            if (reservation.date != today) {
                return
            }

            val currentUsage =
                preferences.getFloat(KEY_USED, 0f).toDouble()

            val correctedUsage = (
                    currentUsage - reservation.credits + actualCredits
                    ).coerceAtLeast(0.0)

            preferences.edit()
                .putFloat(KEY_USED, correctedUsage.toFloat())
                .commit()
        }
    }

    /**
     * Returns today's locally tracked credit usage.
     */
    fun getUsedCreditsToday(): Double {
        synchronized(PROCESS_LOCK) {
            val today = getToday()
            resetIfNewDay(today)

            return preferences.getFloat(KEY_USED, 0f).toDouble()
        }
    }

    /**
     * Returns the remaining local allowance.
     */
    fun getRemainingCreditsToday(): Double {
        return (
                DAILY_LIMIT - getUsedCreditsToday()
                ).coerceAtLeast(0.0)
    }

    /**
     * Checks whether the requested credits fit within
     * the remaining local allowance.
     */
    fun canMakeRequest(credits: Double): Boolean {
        if (!credits.isFinite() || credits <= 0.0) {
            return false
        }

        return getUsedCreditsToday() + credits <= DAILY_LIMIT
    }

    private fun getToday(): String {
        return LocalDate.now().toString()
    }

    private fun resetIfNewDay(today: String) {
        val savedDate = preferences.getString(KEY_DATE, null)

        if (savedDate != today) {
            preferences.edit()
                .putString(KEY_DATE, today)
                .putFloat(KEY_USED, 0f)
                .commit()
        }
    }
}
