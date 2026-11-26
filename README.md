# 🚗 RideConnect

**A Real-Time Ride-Sharing: Carpool & Ride Sharing App Android Application for Mauritius**

RideConnect is a feature-rich Android application developed to modernise transportation and ride-sharing in Mauritius. It connects passengers and drivers through an interactive platform that supports shared carpooling, private ride bookings, GPS-based location services, route calculation and real-time location tracking.

Built using Kotlin and Firebase, RideConnect provides separate interfaces and functionalities for passengers and drivers, offering a complete ride management experience from booking and acceptance to journey tracking and completion.

---

## 📋 List of Tables
- **Table 2.1:** Key RideConnect Passenger Features
- **Table 2.2:** Key RideConnect Driver Features
- **Table 4.3:** Technologies Used
- **Table 10.1:** RideConnect Firestore Database

---

## 📑 Table of Contents
1. [Project Overview](#1-project-overview)
   - [1.1 Main Objectives](#11-main-objectives)
2. [Key Features](#2-key-features)
   - [2.1 Passenger Features](#21-passenger-features)
   - [2.2 Driver Features](#22-driver-features)
   - [2.3 Real-Time Bidirectional GPS Tracking](#23-real-time-bidirectional-gps-tracking)
     - [2.3.1 Passenger Live Map](#231-passenger-live-map)
     - [2.3.2 Driver Live Map](#232-driver-live-map)
     - [2.3.3 Real-Time Synchronisation](#233-real-time-synchronisation)
   - [2.4 System Features](#24-system-features)
3. [Ride Services](#3-ride-services)
   - [3.1 Shared Rides (Carpooling)](#31-shared-rides-carpooling)
   - [3.2 Private Ride Bookings](#32-private-ride-bookings)
4. [Technologies Used](#4-technologies-used)
5. [Application Screenshots](#5-application-screenshots)
6. [System Requirements](#6-system-requirements)
   - [6.1 Development Environment](#61-development-environment)
   - [6.2 Testing Environment](#62-testing-environment)
7. [Firebase Configuration](#7-firebase-configuration)
8. [GraphHopper API Integration](#8-graphhopper-api-integration)
   - [8.1 Main Functionalities](#81-main-functionalities)
   - [8.2 API Key Configuration](#82-api-key-configuration)
   - [8.3 API Usage Management](#83-api-usage-management)
9. [Live Tracking Architecture](#9-live-tracking-architecture)
   - [9.1 Driver Location](#91-driver-location)
   - [9.2 Passenger Location](#92-passenger-location)
   - [9.3 Real-Time Map Updates](#93-real-time-map-updates)
   - [9.4 Tracking Workflow](#94-tracking-workflow)
10. [Firestore Database Structure](#10-firestore-database-structure)
    - [10.1 Users Collection](#101-users-collection)
    - [10.2 Rides Collection](#102-rides-collection)
    - [10.3 Bookings Collection](#103-bookings-collection)
    - [10.4 Driver Locations Collection](#104-driver-locations-collection)
    - [10.5 Passenger Location Records](#105-passenger-location-records)
11. [Project Structure](#11-project-structure)
12. [Developer Information](#12-developer-information)

---

## 1. Project Overview
RideConnect was designed to provide a practical transportation solution for Mauritius by bringing drivers and passengers together through a centralised mobile application.

The application supports two main user roles:
- **Passengers:** Search for available shared rides, request private transportation, manage bookings and track journeys.
- **Drivers:** Publish available rides, manage vehicles, accept private ride requests, manage passenger bookings and share their live location.

The application incorporates GPS technology, interactive maps, cloud-based data management and real-time communication to improve the overall transportation experience.

### 1.1 Main Objectives
- Simplify the process of finding and booking transportation.
- Encourage carpooling by allowing multiple passengers to share journeys.
- Provide private transportation booking facilities.
- Improve journey visibility through live GPS tracking.
- Facilitate communication between passengers and drivers through real-time notifications.
- Support location-based ride matching across Mauritius.

---

## 2. Key Features

### 2.1 Passenger Features
#### Table 2.1: Key RideConnect Passenger Features
| Feature | Description |
|---|---|
| **User Authentication** | Register and log in using Firebase Authentication. |
| **Passenger Profile** | Manage personal account information. |
| **Shared Ride Search** | Search for available rides using pickup locations, destinations and travel dates. |
| **Advanced Ride Filtering** | Filter rides according to location, date, time, operating areas and available seats. |
| **Flexible Pickup and Drop-off** | Select suitable pickup and destination points along supported driver routes. |
| **Booking Management** | Submit booking requests and monitor their approval status. |
| **Private Ride Requests** | Request dedicated transportation from available drivers. |
| **Live Driver Tracking** | View the driver's current GPS location on an interactive map. |
| **Own Location Display** | View personal GPS position alongside the driver's location. |
| **Booking History** | Access previous and completed journeys. |
| **Ride Status Monitoring** | Follow ride progress and booking status updates. |
| **Push Notifications** | Receive notifications about booking approvals and ride updates. |

### 2.2 Driver Features
#### Table 2.2: Key RideConnect Driver Features
| Feature | Description |
|---|---|
| **Driver Registration** | Create an account with driver and vehicle information. |
| **Vehicle Management** | Maintain vehicle details, including make, model, colour, registration number and vehicle category. |
| **Operating Area Configuration** | Select operating sections, localities and sublocalities or configure island-wide operations. |
| **Publish Shared Rides** | Create scheduled rides with pickup points, destinations, available seats and passenger pricing. |
| **Route Selection** | View calculated driving routes and select preferred alternatives. |
| **Passenger Booking Management** | Review, approve or decline passenger booking requests. |
| **Private Ride Requests** | View incoming private transportation requests and accept or decline them. |
| **Live GPS Broadcasting** | Share the driver's current GPS coordinates with passengers during an active journey. |
| **Passenger Location Tracking** | View passengers' live locations on the driver's interactive map. |
| **Passenger Pickup Markers** | Display approved passengers' pickup locations on the map. |
| **Seat Management** | Monitor available seats and passenger bookings. |
| **Ride Lifecycle Management** | Manage ride progress from acceptance and journey commencement to completion. |
| **Push Notifications** | Receive updates about incoming ride requests and passenger bookings. |

### 2.3 Real-Time Bidirectional GPS Tracking
One of RideConnect's key features is its bidirectional live location tracking system.  
Unlike basic ride-sharing applications that only allow passengers to view their driver's location, RideConnect supports location visibility for both sides of the journey.

#### 2.3.1 Passenger Live Map
Passengers can:
- View their own current GPS location.
- View the assigned driver's live location.
- Observe the driver's movement on an interactive map.
- Monitor the journey while the ride is active.

#### 2.3.2 Driver Live Map
Drivers can:
- View their own current GPS location.
- View passengers' live locations.
- Monitor passenger positions during the journey.
- View approved passenger pickup locations on the map.

#### 2.3.3 Real-Time Synchronisation
Firebase Cloud Firestore is used to synchronise location information between the driver and passenger applications. Location changes are reflected through real-time database updates, allowing both parties to monitor their respective positions.  
This functionality improves journey visibility and coordination between drivers and passengers.

### 2.4 System Features
- Firebase Authentication and cloud-based user management.
- Firebase Cloud Firestore real-time database synchronisation.
- Firebase Cloud Messaging (FCM) push notifications.
- GPS and network-based location services.
- Interactive OpenStreetMap integration.
- OpenTopoMap map tiles.
- GraphHopper route calculation and geocoding.
- Alternative driving route selection.
- Mauritius-specific location and operating-area matching.
- Dynamic ride and booking status updates.
- Transaction-based ride request acceptance to prevent multiple drivers from accepting the same request.
- Android runtime permission management.
- Modern Material Design interface.
- Edge-to-edge layouts and Android system bar integration.

---

## 3. Ride Services
RideConnect supports two distinct transportation models.

### 3.1 Shared Rides (Carpooling)
Drivers can publish scheduled journeys with multiple available seats.  
Passengers travelling along compatible routes can:
- Search for available journeys.
- Select suitable pickup and destination locations.
- Book individual seats.
- Select intermediate pickup and drop-off points along the driver's route.
- Submit booking requests for driver approval.
- Monitor booking and journey status.

This allows multiple unrelated passengers to share a vehicle and journey.

### 3.2 Private Ride Bookings
Passengers can request private transportation for exclusive use of a driver's vehicle.  
The workflow includes:
- Passenger submits a private ride request.
- Available drivers receive the request.
- A driver can accept or decline the request.
- The accepted driver is assigned to the passenger's request.
- The passenger can view the assigned driver's information.
- Both parties can monitor their live locations.
- The journey progresses through its different ride statuses until completion.

Private bookings are handled separately from scheduled shared rides.

---

## 4. Technologies Used
#### Table 4.3: Technologies Used
| Category | Technology | Purpose |
|---|---|---|
| **Programming Language** | Kotlin | Android application development |
| **Development Environment** | Android Studio | Application development and debugging |
| **UI Framework** | Android XML Layouts | User interface design |
| **UI Components** | Material Components, AppCompat, ConstraintLayout | Modern Android interfaces |
| **Authentication** | Firebase Authentication | User registration and login |
| **Cloud Database** | Firebase Cloud Firestore | Real-time data storage and synchronisation |
| **Notifications** | Firebase Cloud Messaging | Push notifications |
| **Map Rendering** | osmdroid | Interactive map rendering |
| **Map Data** | OpenStreetMap | Geographic map data |
| **Map Tiles** | OpenTopoMap | Map visualisation |
| **Routing** | GraphHopper API | Driving route calculation and alternatives |
| **Geocoding** | GraphHopper API | Address and location search |
| **Reverse Geocoding** | Nominatim | Location-to-address lookup |
| **Location Services** | Android LocationManager / GPS | Device location tracking |
| **HTTP Requests** | OkHttp | Asynchronous API communication |
| **Local Storage** | SharedPreferences | Local application preferences and API usage tracking |
| **Build System** | Gradle Kotlin DSL | Dependency and project configuration |

---

## 5. Application Screenshots
> **Note:** Please note that I could not put any image in the README file, I directly uploaded every screenshots of the whole 'RideConnect' app in the folder 'RideConnect Screens Screenshots' in the main root directory.

---

## 6. System Requirements

### 6.1 Development Environment
- Android Studio (Ladybug or newer recommended).
- JDK 11 or compatible version required by the configured Android Gradle Plugin.
- Android SDK API Level 37.
- Minimum Android SDK: API 26 (Android 8.0).
- Gradle with Kotlin DSL support.
- Internet connection for Firebase and external API services.

### 6.2 Testing Environment
- Android smartphone or compatible Android emulator.
- GPS/location services.
- Internet connectivity.
- Google Play Services where required by the device configuration.

---

## 7. Firebase Configuration
RideConnect uses Firebase as its backend infrastructure.

The following Firebase services are integrated:
- Firebase Authentication
- Cloud Firestore
- Firebase Cloud Messaging

### Step 1: Create a Firebase Project
1. Visit the Firebase Console.
2. Create a Firebase project.
3. Add an Android application to the Firebase project.
4. Use the following application package name: `com.example.rideconnect`

### Step 2: Download Firebase Configuration
Download the `google-services.json` configuration file generated by Firebase.  
Place it inside: `RideConnect/app/google-services.json`

### Step 3: Enable Authentication
In Firebase Console:
1. Open Authentication.
2. Navigate to Sign-in Method.
3. Enable Email/Password authentication.

### Step 4: Configure Cloud Firestore
1. Open Firestore Database.
2. Create a Firestore database.
3. Configure appropriate Firestore security rules.
4. Ensure authenticated users have the required permissions to access their relevant application data.

### Step 5: Configure Firebase Cloud Messaging
Configure Firebase Cloud Messaging to support notifications for ride requests, booking updates and ride status changes.

*Important: Never publish private credentials, API keys or sensitive Firebase configuration files unnecessarily. Configure your own Firebase project when setting up the application.*

---

## 8. GraphHopper API Integration
RideConnect integrates the [GraphHopper API](https://www.graphhopper.com/) to support location search and driving route calculations.

### 8.1 Main Functionalities
- Geocoding and location search.
- Reverse geocoding support.
- Driving route calculation.
- Alternative route generation.
- Route distance and estimated travel duration.
- Route geometry for interactive map display.
- Location search prioritised towards Mauritius.

### 8.2 API Key Configuration
1. Create a GraphHopper developer account.
2. Generate an API key.
3. Open the root `local.properties` file.
4. Add the following configuration:
   ```properties
   GRAPHHOPPER_API_KEY=your_actual_api_key_here
   ```
The project uses Gradle configuration to expose the key through: `BuildConfig.GRAPHHOPPER_API_KEY`

### 8.3 API Usage Management
RideConnect also includes a local daily API credit management mechanism through `DailyCreditManager`.  
Its purpose is to:
- Monitor estimated daily API credit consumption.
- Track usage locally using `SharedPreferences`.
- Reset the local usage counter daily.
- Maintain a configurable usage threshold.
- Reduce unnecessary API requests.

Saved route information can be reused to avoid recalculating the same journey unnecessarily.

*Note: The local credit safeguard is an application-side usage control. It is not an account-wide restriction and does not replace quota management through the GraphHopper dashboard. API keys included in an Android application should also be treated as extractable.*

---

## 9. Live Tracking Architecture
RideConnect uses GPS location services and Firebase Cloud Firestore to facilitate real-time location synchronisation.

### 9.1 Driver Location
The driver's device obtains GPS or network-based location updates and publishes location information to Firestore.

### 9.2 Passenger Location
Passenger location information is made available to the driver during the relevant active journey, allowing the driver to monitor passenger positions on the map.

### 9.3 Real-Time Map Updates
Both applications use Firestore real-time listeners to receive relevant location changes and update their interactive map interfaces.

### 9.4 Tracking Workflow
Location tracking depends on the relevant device permissions, connectivity and active ride state.

---

## 10. Firestore Database Structure
RideConnect uses Cloud Firestore to store application data.

The main collections include:

#### Table 10.1: RideConnect Firestore Database
| Collection | Purpose |
|---|---|
| `users` | Passenger and driver account information |
| `rides` | Published rides and private ride requests |
| `bookings` | Passenger booking requests and booking details |
| `driverLocations` | Driver GPS location information |
| `passengerLocations` | Passenger GPS location information |

### 10.1 Users Collection
Stores user account information, including:
- User ID
- Name
- Email
- Phone number
- Account type / role
- Driver vehicle information
- Operating areas and location preferences
- Notification tokens

### 10.2 Rides Collection
Stores ride information, including:
- Ride ID
- Driver or passenger identification
- Pickup and destination
- Travel date and time
- Available and total seats
- Price per passenger
- Ride status
- Route information
- GPS coordinates
- Creation timestamp

### 10.3 Bookings Collection
Stores passenger booking information, including:
- Booking ID
- Ride ID
- Passenger ID
- Passenger details
- Number of seats booked
- Total booking price
- Booking status
- Pickup and destination information
- Pickup coordinates
- Booking timestamp

### 10.4 Driver Locations Collection
Stores the driver's current location information, including:
- Driver ID
- Latitude
- Longitude
- Last location update timestamp

### 10.5 Passenger Location Records
Supports the passenger's live GPS position and its synchronisation with the driver's application during the relevant journey.

---

## 11. Project Structure
The project follows a feature-based Android application structure.

```
RideConnect/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
├── local.properties (user-created)
│
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
└── app/
    ├── build.gradle.kts
    ├── google-services.json (user-created)
    │
    └── src/
        ├── androidTest/
        │   └── java/com/example/rideconnect/
        │       └── ExampleInstrumentedTest.kt
        │
        ├── test/
        │   └── java/com/example/rideconnect/
        │       └── ExampleUnitTest.kt
        │
        └── main/
            ├── AndroidManifest.xml
            ├── ic_launcher-playstore.png
            │
            ├── assets/
            │   ├── postcodemauritius.csv
            │   └── rideconnect_localities.json
            │
            ├── java/com/example/rideconnect/
            │   ├── MainActivity.kt
            │   │
            │   ├── activities/
            │   │   ├── BookingConfirmationActivity.kt
            │   │   ├── DriverAccountActivity.kt
            │   │   ├── DriverBookingRequestsActivity.kt
            │   │   ├── DriverDashboardActivity.kt
            │   │   ├── LoginActivity.kt
            │   │   ├── MyBookingsActivity.kt
            │   │   ├── PassengerAccountActivity.kt
            │   │   ├── PassengerDashboardActivity.kt
            │   │   ├── PostRideActivity.kt
            │   │   ├── RegisterActivity.kt
            │   │   ├── RideHistoryActivity.kt
            │   │   ├── RideSearchActivity.kt
            │   │   ├── SelectedLocationsActivity.kt
            │   │   └── SplashActivity.kt
            │   │
            │   ├── models/
            │   │   ├── OperatingArea.kt
            │   │   ├── OperatingAreaMatcher.kt
            │   │   └── RideConnectConstants.kt
            │   │
            │   ├── notifications/
            │   │   ├── FcmTokenManager.kt
            │   │   ├── NotificationPermissionHelper.kt
            │   │   └── RideConnectFirebaseMessagingService.kt
            │   │
            │   └── routing/
            │       ├── DailyCreditManager.kt
            │       └── GraphHopperRoutingService.kt
            │
            └── res/
                ├── drawable/
                │   ├── bg_btn_driver.xml
                │   ├── bg_btn_passenger.xml
                │   ├── bg_gold_glow_line.xml
                │   ├── bg_hero_scrim.xml
                │   ├── ic_bottom_curve_footer.xml
                │   ├── ic_car_front_white.xml
                │   ├── ic_hero_curve_top.xml
                │   ├── ic_hero_curve_transition.xml
                │   ├── ic_launcher_background.xml
                │   ├── ic_launcher_foreground.xml
                │   ├── ic_logo_car_roof.xml
                │   ├── ic_logo_speed_lines.xml
                │   ├── ic_person_white.xml
                │   ├── ic_visibility.xml
                │   ├── ic_visibility_off.xml
                │   ├── rideconnect_logo.jpg
                │   └── rideconnect_splash.jpg
                │
                ├── layout/
                │   ├── activity_booking_confirmation.xml
                │   ├── activity_driver_account.xml
                │   ├── activity_driver_booking_requests.xml
                │   ├── activity_driver_dashboard.xml
                │   ├── activity_login.xml
                │   ├── activity_main.xml
                │   ├── activity_my_bookings.xml
                │   ├── activity_passenger_account.xml
                │   ├── activity_passenger_dashboard.xml
                │   ├── activity_post_ride.xml
                │   ├── activity_register.xml
                │   ├── activity_ride_history.xml
                │   ├── activity_ride_search.xml
                │   ├── activity_selected_locations.xml
                │   └── activity_splash.xml
                │
                ├── values/
                │   ├── colors.xml
                │   ├── strings.xml
                │   └── themes.xml
                │
                ├── values-night/
                │   └── themes.xml
                │
                ├── xml/
                │   ├── backup_rules.xml
                │   └── data_extraction_rules.xml
                │
                └── mipmap-*/
                    ├── ic_launcher.xml
                    └── ic_launcher_round.xml
```

---

## 12. Developer Information
Developed by: **Muhammad Uways Ahseek** & **Vanshika Lokhan**
- **GitHub:** [Uways86-rgb](https://github.com/Uways86-rgb), [vanshika487](https://github.com/vanshika487)
- **Project Repository:** [https://github.com/Uways86-rgb/RideConnect](https://github.com/Uways86-rgb/RideConnect)
- **Project:** RideConnect - Carpool & Ride Sharing App
- **Platform:** Android
- **Primary Language:** Kotlin
- **Backend:** Firebase
- **Target Market:** Mauritius
