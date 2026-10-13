
package com.example.rideconnect.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenManager {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun refreshAndSaveToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                saveToken(token)
            }
            .addOnFailureListener { exception ->
                android.util.Log.e(
                    "FcmTokenManager",
                    "Failed to retrieve FCM token",
                    exception
                )
            }
    }

    fun saveToken(token: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(userId)
            .set(
                mapOf(
                    "fcmTokens" to FieldValue.arrayUnion(token)
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                android.util.Log.d(
                    "FcmTokenManager",
                    "FCM token saved successfully"
                )
            }
            .addOnFailureListener { exception ->
                android.util.Log.e(
                    "FcmTokenManager",
                    "Failed to save FCM token",
                    exception
                )
            }
    }
}
