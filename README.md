# 🚗 RideConnect

<p align="center">
  <img src="app/src/main/res/drawable/ic_logo_car_roof.xml" width="120" height="120" alt="RideConnect Logo" />
</p>

<p align="center">
  <b>A Modern, Real-Time Ride-Sharing & Carpooling Android Application for Mauritius</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/Maps-osmdroid-0078D7?style=for-the-badge&logo=openstreetmap&logoColor=white" alt="osmdroid" />
  <img src="https://img.shields.io/badge/Routing-GraphHopper-000000?style=for-the-badge" alt="GraphHopper" />
</p>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
  - [Passenger Features](#-passenger-features)
  - [Driver Features](#-driver-features)
  - [System & Real-Time Features](#-system--real-time-features)
- [Tech Stack & Dependencies](#-tech-stack--dependencies)
- [Project Architecture & Directory Structure](#-project-architecture--directory-structure)
- [Setup & Installation Guide](#-setup--installation-guide)
  - [Prerequisites](#prerequisites)
  - [1. Clone Repository](#1-clone-repository)
  - [2. Firebase Setup](#2-firebase-setup)
  - [3. GraphHopper API Key Configuration](#3-graphhopper-api-key-configuration)
  - [4. Build & Run](#4-build--run)
- [Core Logic & Technical Modules](#-core-logic--technical-modules)
  - [GraphHopper Routing & Daily Credit Safeguard](#1-graphhopper-routing--daily-credit-safeguard)
  - [Mauritius Operating Area Matching](#2-mauritius-operating-area-matching)
  - [Live GPS Tracking & Firebase Firestore](#3-live-gps-tracking--firebase-firestore)
  - [Push Notifications](#4-push-notifications)
- [Firestore Database Schema](#-firestore-database-schema)
- [Screens & Navigation Flow](#-screens--navigation-flow)
- [Contributing](#-contributing)
- [License](#-license)

---

## 📱 Overview

**RideConnect** is a comprehensive Android application designed to facilitate carpooling and ride-hailing across Mauritius. Built natively using **Kotlin** and modern Android development practices, RideConnect addresses local transportation needs by connecting drivers with available vehicle seats to passengers traveling along similar routes.

The platform seamlessly supports two complementary transport models:
1. **Shared Rides (Carpooling):** Drivers post scheduled multi-seat journeys, allowing passengers to search, select preferred departure times, filter by region/locality, and book individual seats.
2. **Instant / Private Rides:** Passengers request immediate point-to-point rides, which nearby drivers can view, evaluate, and accept or decline in real time.

---

## ✨ Key Features

### 👤 Passenger Features
- **User Registration & Profile:** Simple sign-up and login with role selection (Passenger vs. Driver).
- **Search & Filter Shared Rides:** Search posted rides by pickup/destination, travel date, departure time, time flexibility (±15 min to ±60 min), operational region, and required seat count.
- **Interactive Route & Booking Confirmation:** View detailed ride summaries including price breakdown per seat, vehicle information, driver details, and total group cost before booking.
- **Instant Ride Requests:** Request immediate pickups with automated address detection using GPS and reverse geocoding.
- **Real-Time Driver Tracking:** Track the assigned driver's live location on an interactive OpenStreetMap view once a ride is accepted or in progress.
- **My Bookings & Ride History:** Monitor current booking statuses (Pending, Approved, Declined, Cancelled) and view historical trips.

### 🚘 Driver Features
- **Vehicle Profile Management:** Register vehicle details including Make, Model, Colour, Registration Number, and Category (Standard Car: 4 seats, 7-Seater: 6 seats, 15-Seater Van: 14 seats).
- **Post Shared Rides with Route Calculation:** Enter pickup and destination localities, interactively select custom map points, calculate driving routes via GraphHopper API with alternative path choices, and post available seats with custom pricing.
- **Operating Area Management:** Configure operating regions by Section, Locality, and Sublocality (powered by a dedicated Mauritius locality dataset) or opt to operate island-wide ("All Mauritius").
- **Instant Ride Request Feed:** Real-time feed of nearby instant ride requests with one-tap Accept or Decline actions secured by Firestore concurrency transactions.
- **Live Location Broadcasting:** Automatically stream real-time GPS location coordinates to Firestore while on an active ride.
- **Approved Passenger Pickup Markers:** Automatically render markers for all approved passengers' pickup locations directly on the driver's map.
- **Booking Management:** Review passenger seat requests, approve or decline pending bookings, and track seat capacity.

### ⚡ System & Real-Time Features
- **Edge-to-Edge Navigation UI:** Seamless integration with Android system bars using `WindowInsetsCompat`.
- **Firebase Push Notifications:** Integrated Firebase Cloud Messaging (FCM) for instant notification delivery on ride state changes, request approvals, and updates.
- **Daily API Safeguard:** Custom local preference credit tracker (`DailyCreditManager`) ensuring GraphHopper API quota usage remains within defined daily limits.

---

## 🛠 Tech Stack & Dependencies

| Category | Technology / Library | Purpose |
| :--- | :--- | :--- |
| **Language** | [Kotlin](https://kotlinlang.org/) | Core application programming language |
| **Target / Min SDK** | Android SDK 37 / Min SDK 26 (Android 8.0+) | Platform compatibility |
| **UI Components** | Material Components, ConstraintLayout, AppCompat | Modern Android layout design |
| **Backend / Auth** | [Firebase Authentication](https://firebase.google.com/docs/auth) | Secure user authentication |
| **Database** | [Firebase Cloud Firestore](https://firebase.google.com/docs/firestore) | Real-time NoSQL cloud database |
| **Push Notifications**| [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) | Real-time device notifications |
| **Map Rendering** | [osmdroid 6.1.20](https://github.com/osmdroid/osmdroid) | OpenStreetMap tile rendering & overlays (`OpenTopoMap`) |
| **Routing & Geocoding**| [GraphHopper API](https://www.graphhopper.com/) | Geocoding, reverse geocoding, and route calculation |
| **Reverse Geocoding** | [Nominatim / OpenStreetMap](https://nominatim.org/) | Location address lookup fallback |
| **Location Services** | Google Play Services Location (`FusedLocationProviderClient`) | GPS location tracking |
| **HTTP Client** | [OkHttp 4.12.0](https://square.github.io/okhttp/) | Asynchronous API network requests |

---

## 📁 Project Architecture & Directory Structure

RideConnect follows a clean feature-based package layout:

```
RideConnect/
├── app/
│   ├── build.gradle.kts                   # Module build script and dependencies
│   ├── google-services.json               # Firebase configuration file (User supplied)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml        # Application manifest & permissions
│           ├── assets/
│           │   └── rideconnect_localities.json # Mauritius locality hierarchy dataset
│           ├── java/com/example/rideconnect/
│           │   ├── MainActivity.kt        # Entry point / authentication check
│           │   ├── activities/            # UI Screen Activity controllers
│           │   │   ├── SplashActivity.kt
│           │   │   ├── LoginActivity.kt
│           │   │   ├── RegisterActivity.kt
│           │   │   ├── PassengerDashboardActivity.kt
│           │   │   ├── DriverDashboardActivity.kt
│           │   │   ├── PostRideActivity.kt
│           │   │   ├── RideSearchActivity.kt
│           │   │   ├── BookingConfirmationActivity.kt
│           │   │   ├── DriverBookingRequestsActivity.kt
│           │   │   ├── MyBookingsActivity.kt
│           │   │   ├── RideHistoryActivity.kt
│           │   │   ├── DriverAccountActivity.kt
│           │   │   ├── PassengerAccountActivity.kt
│           │   │   └── SelectedLocationsActivity.kt
│           │   ├── models/                # Domain models & constants
│           │   │   ├── OperatingArea.kt
│           │   │   ├── OperatingAreaMatcher.kt
│           │   │   └── RideConnectConstants.kt
│           │   ├── notifications/         # Firebase Cloud Messaging & notifications
│           │   │   ├── FcmTokenManager.kt
│           │   │   ├── NotificationPermissionHelper.kt
│           │   │   └── RideConnectFirebaseMessagingService.kt
│           │   └── routing/               # GraphHopper routing & API credit management
│           │       ├── DailyCreditManager.kt
│           │       └── GraphHopperRoutingService.kt
│           └── res/                       # XML layouts, drawables, strings, values
├── build.gradle.kts                       # Top-level Gradle script
├── settings.gradle.kts                    # Gradle settings & plugin management
└── local.properties                       # Local API keys (User supplied)
```

---

## 🚀 Setup & Installation Guide

### Prerequisites
- **Android Studio:** Ladybug (2024.2.1) or newer recommended.
- **JDK:** Version 11 or higher.
- **Android SDK:** API Level 37 (Build Tools installed).
- **Physical Device / Emulator:** Running Android 8.0 (API 26) or above with Google Play Services.

---

### 1. Clone Repository

```bash
git clone https://github.com/your-username/RideConnect.git
cd RideConnect
```

---

### 2. Firebase Setup

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project named **RideConnect**.
2. Add an **Android Application** with package name `com.example.rideconnect`.
3. Download the generated `google-services.json` file.
4. Place the `google-services.json` file inside the `app/` directory:
   ```text
   RideConnect/app/google-services.json
   ```
5. Enable the following services in Firebase Console:
   - **Authentication:** Enable *Email/Password* sign-in method.
   - **Cloud Firestore Database:** Create database in test mode or production mode with rules configured.
   - **Cloud Messaging (FCM):** Enabled by default.

---

### 3. GraphHopper API Key Configuration

RideConnect relies on GraphHopper for geocoding and routing calculations.

1. Create a free developer account at [GraphHopper](https://www.graphhopper.com/).
2. Generate an API Key.
3. Open or create the `local.properties` file in the root of the project.
4. Add your key as shown below:

```properties
GRAPHHOPPER_API_KEY=your_actual_graphhopper_api_key_here
```

*Note: `local.properties` is automatically read during the build process, injecting `BuildConfig.GRAPHHOPPER_API_KEY` into the application.*

---

### 4. Build & Run

1. Open the project in **Android Studio**.
2. Sync the project with Gradle Files (**File > Sync Project with Gradle Files**).
3. Connect an Android device via USB debugging or start an Android Virtual Device (AVD).
4. Click **Run** (`Shift + F10`) or select **Run 'app'** from the toolbar.

---

## 🧠 Core Logic & Technical Modules

### 1. GraphHopper Routing & Daily Credit Safeguard
- **`GraphHopperRoutingService`**: Manages asynchronous OkHttp calls for geocoding (`/api/1/geocode`) and alternative route calculation (`/api/1/route`). Biases location search towards Mauritius coordinates (`-20.20, 57.50`).
- **`DailyCreditManager`**: Implements a local daily rate-limiting safeguard (default: `400.0` credits/day) using `SharedPreferences`. Automatically tracks estimated credit usage per request, resets daily, and updates remaining balance when server headers (`X-RateLimit-Credits`) are returned.

### 2. Mauritius Operating Area Matching
- **`OperatingAreaRepository`**: Parses `rideconnect_localities.json` from app assets to build hierarchical models (`OperatingSection` -> `OperatingLocality` -> `OperatingSublocality`).
- **`OperatingAreaMatcher`**: Evaluates location query results against a driver's designated operating areas. Performs exact postcode matching or normalized string comparison across section, locality, and sublocality names to enforce geographic operational boundaries.

### 3. Live GPS Tracking & Firebase Firestore
- **Drivers:** Continuously broadcast latitude/longitude coordinates to the `driverLocations/{driverId}` document using `LocationManager` updates.
- **Passengers:** Listen via `SnapshotListener` to real-time coordinate changes, animating the driver's position on the osmdroid map while a ride is active.
- **Concurrency Control:** Critical actions like accepting or declining instant ride requests use `db.runTransaction` to prevent race conditions when multiple drivers attempt to claim the same request.

### 4. Push Notifications
- **`RideConnectFirebaseMessagingService`**: Configures an Android `NotificationChannel` (`rideconnect_ride_updates`) and handles incoming data messages.
- **`FcmTokenManager`**: Automatically updates user FCM registration tokens in Firestore (`users/{userId}/fcmTokens`) whenever tokens are generated or refreshed.

---

## 🗄 Firestore Database Schema

### `users` Collection
```json
{
  "userId": "String (Document ID)",
  "name": "String",
  "email": "String",
  "phone": "String",
  "role": "DRIVER | PASSENGER",
  "carMake": "String (Driver only)",
  "carModel": "String (Driver only)",
  "carColor": "String (Driver only)",
  "carRegistration": "String (Driver only)",
  "vehicleCategory": "STANDARD_CAR | SEVEN_SEATER | FIFTEEN_SEATER",
  "operatingAreaMode": "ALL_MAURITIUS | SELECTED_AREAS",
  "operatingAreas": [
    {
      "section": "String",
      "locality": "String",
      "sublocality": "String",
      "postcode": "String"
    }
  ],
  "fcmTokens": ["String"]
}
```

### `rides` Collection
```json
{
  "rideId": "String (Document ID)",
  "driverId": "String",
  "driverName": "String",
  "driverPhone": "String",
  "pickup": "String",
  "destination": "String",
  "date": "String (dd MMM yyyy)",
  "time": "String (HH:mm)",
  "availableSeats": 4,
  "totalSeats": 4,
  "pricePerPassenger": 150.0,
  "status": "Searching | Available | Accepted | In Progress | Completed | Cancelled",
  "pickupCoordinates": { "latitude": -20.1609, "longitude": 57.5012 },
  "destinationCoordinates": { "latitude": -20.2345, "longitude": 57.4981 },
  "routeDistanceKm": 18.5,
  "routeDurationMinutes": 25,
  "routePoints": [
    { "latitude": -20.1609, "longitude": 57.5012 }
  ],
  "createdAt": "Timestamp"
}
```

### `bookings` Collection
```json
{
  "bookingId": "String (Document ID)",
  "rideId": "String",
  "passengerId": "String",
  "passengerName": "String",
  "passengerPhone": "String",
  "seatsBooked": 2,
  "totalPrice": 300.0,
  "status": "Pending | Approved | Declined | Cancelled",
  "pickupAddress": "String",
  "pickupLatitude": -20.1650,
  "pickupLongitude": 57.5020,
  "timestamp": "Timestamp"
}
```

### `driverLocations` Collection
```json
{
  "driverId": "String (Document ID)",
  "latitude": -20.1609,
  "longitude": 57.5012,
  "updatedAt": "Timestamp"
}
```

---

## 🎨 Screens & Navigation Flow

```text
               +----------------------+
               |    SplashActivity    |
               +----------+-----------+
                          |
               +----------v-----------+
               |    LoginActivity     |
               +----------+-----------+
                          |
             +------------+------------+
             |                         |
+------------v------------+ +----------v------------+
| PassengerDashboardActivity | | DriverDashboardActivity|
+------------+------------+ +----------+------------+
             |                         |
             |-- RideSearchActivity    |-- PostRideActivity
             |-- MyBookingsActivity    |-- DriverBookingRequests
             |-- RideHistoryActivity   |-- DriverAccountActivity
             |-- PassengerAccount      |
```

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the Repository.
2. Create a feature branch: `git checkout -b feature/AmazingFeature`
3. Commit your changes: `git commit -m 'Add some AmazingFeature'`
4. Push to the branch: `git push origin feature/AmazingFeature`
5. Open a Pull Request.

---

## 📄 License

This project is created for educational and demonstration purposes. Distributed under the **MIT License**. See `LICENSE` for more information.

---

<p align="center">
  Crafted with ❤️ for modern mobility in Mauritius 🇲🇺
</p>
