
package com.example.rideconnect.activities

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.example.rideconnect.models.OperatingAreaRepository
import com.example.rideconnect.models.OperatingLocality
import com.example.rideconnect.models.OperatingSection
import com.example.rideconnect.models.OperatingSublocality
import com.example.rideconnect.models.VehicleCategories
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var operatingAreasContainer: LinearLayout
    private lateinit var selectedOperatingAreasContainer: LinearLayout
    private lateinit var operatingAreasSummary: TextView

    private var operatingSections: List<OperatingSection> = emptyList()

    private val selectedSublocalities =
        linkedSetOf<String>()

    private val expandedSections =
        linkedSetOf<String>()

    private val expandedLocalities =
        linkedSetOf<String>()

    private var entireMauritiusSelected = false

    private data class AreaEntry(
        val key: String,
        val section: String,
        val locality: String,
        val sublocality: String,
        val postcode: String
    )

    private val allAreaEntries = mutableListOf<AreaEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val selectedRole = intent.getStringExtra("SELECTED_ROLE")

        if (selectedRole != "Passenger" && selectedRole != "Driver") {
            Toast.makeText(
                this,
                "Please select Passenger or Driver first",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val phoneInput = findViewById<EditText>(R.id.phoneInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val vehicleInformationTitle =
            findViewById<TextView>(R.id.vehicleInformationTitle)

        val vehicleCategoryLabel =
            findViewById<TextView>(R.id.vehicleCategoryLabel)

        val vehicleCategorySpinner =
            findViewById<Spinner>(R.id.vehicleCategorySpinner)

        val vehicleCategories = listOf(
            VehicleCategories.STANDARD_CAR,
            VehicleCategories.SEVEN_SEATER,
            VehicleCategories.FIFTEEN_SEATER
        )

        val vehicleCategoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            vehicleCategories.map {
                VehicleCategories.getDisplayName(it)
            }
        )

        vehicleCategoryAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        vehicleCategorySpinner.adapter = vehicleCategoryAdapter

        val carRegistrationInput =
            findViewById<EditText>(R.id.carRegistrationInput)

        val carMakeInput =
            findViewById<EditText>(R.id.carMakeInput)

        val carModelInput =
            findViewById<EditText>(R.id.carModelInput)

        val carColorInput =
            findViewById<EditText>(R.id.carColorInput)

        val selectedRoleText =
            findViewById<TextView>(R.id.selectedRoleText)

        val registerButton =
            findViewById<Button>(R.id.registerButton)

        selectedRoleText.text = "Registering as: $selectedRole"

        if (selectedRole == "Driver") {

            vehicleInformationTitle.visibility = View.VISIBLE
            vehicleCategoryLabel.visibility = View.VISIBLE
            vehicleCategorySpinner.visibility = View.VISIBLE
            carRegistrationInput.visibility = View.VISIBLE
            carMakeInput.visibility = View.VISIBLE
            carModelInput.visibility = View.VISIBLE
            carColorInput.visibility = View.VISIBLE

            initializeOperatingAreaSelector()

        } else {

            vehicleInformationTitle.visibility = View.GONE
            vehicleCategoryLabel.visibility = View.GONE
            vehicleCategorySpinner.visibility = View.GONE
            carRegistrationInput.visibility = View.GONE
            carMakeInput.visibility = View.GONE
            carModelInput.visibility = View.GONE
            carColorInput.visibility = View.GONE
        }

        registerButton.setOnClickListener {

            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            val carRegistration =
                carRegistrationInput.text.toString().trim()

            val selectedVehicleCategory =
                if (selectedRole == "Driver") {
                    vehicleCategories[
                        vehicleCategorySpinner.selectedItemPosition
                    ]
                } else {
                    ""
                }

            val passengerCapacity =
                if (selectedRole == "Driver") {
                    VehicleCategories.getPassengerCapacity(
                        selectedVehicleCategory
                    )
                } else {
                    0
                }

            val carMake = carMakeInput.text.toString().trim()
            val carModel = carModelInput.text.toString().trim()
            val carColor = carColorInput.text.toString().trim()

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

            if (selectedRole == "Driver") {

                if (
                    carRegistration.isEmpty() ||
                    carMake.isEmpty() ||
                    carModel.isEmpty() ||
                    carColor.isEmpty()
                ) {
                    Toast.makeText(
                        this,
                        "Please complete all vehicle information",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                if (
                    !entireMauritiusSelected &&
                    selectedSublocalities.isEmpty()
                ) {
                    Toast.makeText(
                        this,
                        "Please select at least one operating area",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }
            }

            registerButton.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {

                    val userId = auth.currentUser?.uid

                    if (userId == null) {

                        registerButton.isEnabled = true

                        Toast.makeText(
                            this,
                            "Unable to create user",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    val user = hashMapOf<String, Any>(
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "accountType" to selectedRole
                    )


                    if (selectedRole == "Driver") {

                        user["carRegistration"] = carRegistration
                        user["carMake"] = carMake
                        user["carModel"] = carModel
                        user["carColor"] = carColor
                        user["vehicleCategory"] = selectedVehicleCategory
                        user["passengerCapacity"] = passengerCapacity

                        user["operatesThroughoutMauritius"] =
                            entireMauritiusSelected

                        user["operatingAreaMode"] =
                            if (entireMauritiusSelected) {
                                "ALL_MAURITIUS"
                            } else {
                                "SELECTED_AREAS"
                            }

                        val selectedAreaDocuments = if (entireMauritiusSelected) {
                            emptyList()
                        } else {
                            allAreaEntries
                                .filter { selectedSublocalities.contains(it.key) }
                                .map { area ->
                                    hashMapOf(
                                        "section" to area.section,
                                        "locality" to area.locality,
                                        "sublocality" to area.sublocality,
                                        "postcode" to area.postcode
                                    )
                                }
                        }

                        user["operatingAreas"] = selectedAreaDocuments

                        user["operatingSections"] = if (entireMauritiusSelected) {
                            emptyList<String>()
                        } else {
                            selectedAreaDocuments
                                .map { it["section"] ?: "" }
                                .distinct()
                        }
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

                            val intent = Intent(
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

    // ---------------------------------------------------------
    // INITIALIZE OPERATING AREA SELECTOR
    // ---------------------------------------------------------

    private fun initializeOperatingAreaSelector() {

        val title =
            findViewById<TextView>(R.id.driverLocationTitle)

        val description =
            findViewById<TextView>(R.id.operatingAreasDescription)

        operatingAreasContainer =
            findViewById(R.id.operatingAreasContainer)

        selectedOperatingAreasContainer =
            findViewById(R.id.selectedOperatingAreasContainer)

        operatingAreasSummary =
            findViewById(R.id.operatingAreasSummary)

        title.visibility = View.VISIBLE
        description.visibility = View.VISIBLE
        operatingAreasContainer.visibility = View.VISIBLE
        selectedOperatingAreasContainer.visibility = View.VISIBLE
        operatingAreasSummary.visibility = View.VISIBLE

        try {

            operatingSections = OperatingAreaRepository.load(this)

            allAreaEntries.clear()

            operatingSections.forEach { section ->

                section.localities.forEach { locality ->

                    locality.sublocalities.forEach { sublocality ->

                        allAreaEntries.add(
                            AreaEntry(
                                key = createAreaKey(
                                    section.name,
                                    locality.name,
                                    sublocality
                                ),
                                section = section.name,
                                locality = locality.name,
                                sublocality = sublocality.name,
                                postcode = sublocality.postcode
                            )
                        )
                    }
                }
            }

            displayOperatingAreas()
            updateSelectedAreasDisplay()

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Unable to load operating areas: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---------------------------------------------------------
    // MAIN OPERATING AREA DISPLAY
    // ---------------------------------------------------------

    private fun displayOperatingAreas() {

        operatingAreasContainer.removeAllViews()

        addEntireMauritiusOption()

        operatingSections.forEach { section ->

            addSectionOption(section)
        }
    }

    // ---------------------------------------------------------
    // ENTIRE MAURITIUS
    // ---------------------------------------------------------

    private fun addEntireMauritiusOption() {

        val container = createSectionContainer()

        val checkbox = CheckBox(this).apply {

            text = "Entire Mauritius — Everywhere"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)

            isChecked = entireMauritiusSelected

            setOnClickListener {

                entireMauritiusSelected = isChecked

                if (entireMauritiusSelected) {
                    selectedSublocalities.clear()
                }

                updateSelectedAreasDisplay()
                displayOperatingAreas()
            }
        }

        val description = createTextView(
            "Select this option for unrestricted coverage throughout Mauritius.",
            13f,
            false
        )

        description.setTextColor(
            getColor(R.color.text_secondary)
        )

        container.addView(checkbox)
        container.addView(description)

        operatingAreasContainer.addView(container)
    }

    // ---------------------------------------------------------
    // REGION / SECTION DISPLAY
    // ---------------------------------------------------------

    private fun addSectionOption(section: OperatingSection) {

        val sectionEntries = getSectionEntries(section.name)

        val selectedCount = sectionEntries.count {
            entireMauritiusSelected ||
                    selectedSublocalities.contains(it.key)
        }

        val totalCount = sectionEntries.size

        val isFullySelected =
            totalCount > 0 && selectedCount == totalCount

        val isPartiallySelected =
            selectedCount > 0 && selectedCount < totalCount

        val container = createSectionContainer()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val checkbox = CheckBox(this).apply {

            text = section.name
            textSize = 16f
            setTypeface(null, Typeface.BOLD)

            isChecked = isFullySelected

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnClickListener {

                setSectionSelection(
                    section,
                    isChecked
                )
            }
        }

        val expandButton = TextView(this).apply {

            text = if (expandedSections.contains(section.name)) {
                "Hide  ▲"
            } else {
                "View areas  ▼"
            }

            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.teal))
            setPadding(dp(12), dp(10), dp(8), dp(10))

            setOnClickListener {

                if (expandedSections.contains(section.name)) {
                    expandedSections.remove(section.name)
                } else {
                    expandedSections.add(section.name)
                }

                displayOperatingAreas()
            }
        }

        header.addView(checkbox)
        header.addView(expandButton)

        container.addView(header)

        val statusText = when {
            isFullySelected -> "All areas selected"
            isPartiallySelected ->
                "$selectedCount of $totalCount locations selected"
            else ->
                "$totalCount locations available"
        }

        val status = createTextView(
            statusText,
            12f,
            false
        )

        status.setTextColor(
            getColor(R.color.text_secondary)
        )

        container.addView(status)

        if (expandedSections.contains(section.name)) {

            val selectAll = CheckBox(this).apply {

                text = "Select entire ${section.name}"
                textSize = 14f
                isChecked = isFullySelected

                setOnClickListener {

                    setSectionSelection(
                        section,
                        isChecked
                    )
                }
            }

            selectAll.setPadding(
                dp(20),
                dp(8),
                dp(8),
                dp(8)
            )

            container.addView(selectAll)

            section.localities.forEach { locality ->

                addLocalityOption(
                    container,
                    section,
                    locality
                )
            }
        }

        operatingAreasContainer.addView(container)
    }

    // ---------------------------------------------------------
    // LOCALITY DISPLAY
    // ---------------------------------------------------------

    private fun addLocalityOption(
        parent: LinearLayout,
        section: OperatingSection,
        locality: OperatingLocality
    ) {

        val localityEntries = getLocalityEntries(
            section.name,
            locality.name
        )

        val selectedCount = localityEntries.count {
            entireMauritiusSelected ||
                    selectedSublocalities.contains(it.key)
        }

        val totalCount = localityEntries.size

        val isFullySelected =
            totalCount > 0 && selectedCount == totalCount

        val isPartiallySelected =
            selectedCount > 0 && selectedCount < totalCount

        val localityContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(6), dp(8), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val checkbox = CheckBox(this).apply {

            text = locality.name
            textSize = 14f
            isChecked = isFullySelected

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnClickListener {

                setLocalitySelection(
                    section.name,
                    locality,
                    isChecked
                )
            }
        }

        val expandButton = TextView(this).apply {

            text = if (
                expandedLocalities.contains(
                    localityKey(section.name, locality.name)
                )
            ) {
                "▲"
            } else {
                "▼"
            }

            textSize = 13f
            setTextColor(getColor(R.color.teal))
            setPadding(dp(12), dp(10), dp(12), dp(10))

            setOnClickListener {

                val key = localityKey(
                    section.name,
                    locality.name
                )

                if (expandedLocalities.contains(key)) {
                    expandedLocalities.remove(key)
                } else {
                    expandedLocalities.add(key)
                }

                displayOperatingAreas()
            }
        }

        header.addView(checkbox)
        header.addView(expandButton)

        localityContainer.addView(header)

        if (isPartiallySelected) {

            val partialText = createTextView(
                "$selectedCount of $totalCount sublocalities selected",
                11f,
                false
            )

            partialText.setTextColor(
                getColor(R.color.text_secondary)
            )

            partialText.setPadding(
                dp(40),
                0,
                0,
                dp(4)
            )

            localityContainer.addView(partialText)
        }

        if (
            expandedLocalities.contains(
                localityKey(section.name, locality.name)
            )
        ) {

            locality.sublocalities.forEach { sublocality ->

                val key = createAreaKey(
                    section.name,
                    locality.name,
                    sublocality
                )

                val sublocalityCheckbox = CheckBox(this).apply {

                    text = "${sublocality.name} (${sublocality.postcode})"
                    textSize = 12f

                    isChecked =
                        entireMauritiusSelected ||
                                selectedSublocalities.contains(key)

                    setPadding(
                        dp(40),
                        dp(3),
                        dp(8),
                        dp(3)
                    )

                    setOnClickListener {

                        setIndividualSublocalitySelection(
                            key,
                            isChecked
                        )
                    }
                }

                localityContainer.addView(
                    sublocalityCheckbox
                )
            }
        }

        parent.addView(localityContainer)
    }

    // ---------------------------------------------------------
    // REGION SELECTION
    // ---------------------------------------------------------

    private fun setSectionSelection(
        section: OperatingSection,
        selected: Boolean
    ) {

        if (entireMauritiusSelected) {

            if (selected) {
                return
            }

            entireMauritiusSelected = false
            selectedSublocalities.clear()

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        val sectionKeys = getSectionEntries(section.name)
            .map { it.key }

        if (selected) {
            selectedSublocalities.addAll(sectionKeys)
        } else {
            selectedSublocalities.removeAll(sectionKeys.toSet())
        }

        updateSelectedAreasDisplay()
        displayOperatingAreas()
    }

    // ---------------------------------------------------------
    // LOCALITY SELECTION
    // ---------------------------------------------------------

    private fun setLocalitySelection(
        sectionName: String,
        locality: OperatingLocality,
        selected: Boolean
    ) {

        if (entireMauritiusSelected) {

            if (selected) {
                return
            }

            entireMauritiusSelected = false
            selectedSublocalities.clear()

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        val localityKeys = getLocalityEntries(
            sectionName,
            locality.name
        ).map { it.key }

        if (selected) {
            selectedSublocalities.addAll(localityKeys)
        } else {
            selectedSublocalities.removeAll(localityKeys.toSet())
        }

        updateSelectedAreasDisplay()
        displayOperatingAreas()
    }

    // ---------------------------------------------------------
    // INDIVIDUAL SUBLOCALITY SELECTION
    // ---------------------------------------------------------

    private fun setIndividualSublocalitySelection(
        key: String,
        selected: Boolean
    ) {

        if (entireMauritiusSelected) {

            entireMauritiusSelected = false
            selectedSublocalities.clear()

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        if (selected) {
            selectedSublocalities.add(key)
        } else {
            selectedSublocalities.remove(key)
        }

        updateSelectedAreasDisplay()
        displayOperatingAreas()
    }

    // ---------------------------------------------------------
    // SELECTED AREA SUMMARY
    // ---------------------------------------------------------

    private fun updateSelectedAreasDisplay() {

        selectedOperatingAreasContainer.removeAllViews()

        if (entireMauritiusSelected) {

            operatingAreasSummary.text =
                "Entire Mauritius selected — unrestricted coverage everywhere."

            val item = createTextView(
                "Entire Mauritius",
                14f,
                true
            )

            item.setTextColor(
                getColor(R.color.teal)
            )

            selectedOperatingAreasContainer.addView(item)

            return
        }

        val selectedEntries = allAreaEntries.filter {
            selectedSublocalities.contains(it.key)
        }

        operatingAreasSummary.text =
            "${selectedEntries.size} individual location(s) selected"

        if (selectedEntries.isEmpty()) {
            return
        }

        val groupedEntries = selectedEntries.groupBy {
            it.section
        }

        groupedEntries.forEach { (sectionName, sectionEntries) ->

            val sectionTitle = createTextView(
                sectionName,
                14f,
                true
            )

            sectionTitle.setTextColor(
                getColor(R.color.navy)
            )

            sectionTitle.setPadding(
                0,
                dp(8),
                0,
                dp(4)
            )

            selectedOperatingAreasContainer.addView(
                sectionTitle
            )

            sectionEntries.forEach { entry ->

                val areaText = createTextView(
                    "${entry.locality} — ${entry.sublocality}",
                    12f,
                    false
                )

                areaText.setTextColor(
                    getColor(R.color.text_primary)
                )

                selectedOperatingAreasContainer.addView(
                    areaText
                )
            }
        }
    }

    // ---------------------------------------------------------
    // DATA HELPERS
    // ---------------------------------------------------------

    private fun getSectionEntries(
        sectionName: String
    ): List<AreaEntry> {

        return allAreaEntries.filter {
            it.section == sectionName
        }
    }

    private fun getLocalityEntries(
        sectionName: String,
        localityName: String
    ): List<AreaEntry> {

        return allAreaEntries.filter {
            it.section == sectionName &&
                    it.locality == localityName
        }
    }

    private fun createAreaKey(
        sectionName: String,
        localityName: String,
        sublocality: OperatingSublocality
    ): String {

        return "$sectionName|$localityName|${sublocality.postcode}|${sublocality.name}"
    }

    private fun localityKey(
        sectionName: String,
        localityName: String
    ): String {

        return "$sectionName|$localityName"
    }

    private fun createSectionContainer(): LinearLayout {

        return LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )

            setBackgroundColor(
                getColor(R.color.screen_background)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun createTextView(
        text: String,
        size: Float,
        bold: Boolean
    ): TextView {

        return TextView(this).apply {

            this.text = text
            textSize = size

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dp(value: Int): Int {

        return (
                value * resources.displayMetrics.density
                ).toInt()
    }
}
