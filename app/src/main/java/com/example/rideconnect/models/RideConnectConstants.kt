
package com.example.rideconnect.models

/**
 * Identifies the two separate RideConnect services.
 */
object RideTypes {
    const val PRIVATE_BOOKING = "PRIVATE_BOOKING"
    const val SHARED_RIDE = "SHARED_RIDE"
}

/**
 * Vehicle categories and their maximum passenger capacities.
 * The driver is excluded from these capacities.
 */
object VehicleCategories {

    const val STANDARD_CAR = "STANDARD_CAR"
    const val SEVEN_SEATER = "SEVEN_SEATER"
    const val FIFTEEN_SEATER = "FIFTEEN_SEATER"

    fun getPassengerCapacity(category: String): Int {
        return when (category) {
            STANDARD_CAR -> 4
            SEVEN_SEATER -> 6
            FIFTEEN_SEATER -> 14
            else -> 0
        }
    }

    fun getDisplayName(category: String): String {
        return when (category) {
            STANDARD_CAR -> "Standard Car"
            SEVEN_SEATER -> "7-Seater Vehicle"
            FIFTEEN_SEATER -> "15-Seater Van"
            else -> "Unknown Vehicle"
        }
    }
}

/**
 * Private booking departure options.
 */
object DepartureTypes {
    const val IMMEDIATE = "IMMEDIATE"
    const val SCHEDULED = "SCHEDULED"
}

/**
 * Shared and private ride statuses.
 * Existing Firestore status values are preserved.
 */
object RideStatuses {
    const val SEARCHING = "Searching"
    const val AVAILABLE = "Available"
    const val ACCEPTED = "Accepted"
    const val REJECTED = "Rejected"
    const val IN_PROGRESS = "In Progress"
    const val COMPLETED = "Completed"
    const val CANCELLED = "Cancelled"
}
