package com.example.rideconnect.activities

import android.os.Bundle
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.rideconnect.R
import com.example.rideconnect.models.OperatingAreaRepository
import com.example.rideconnect.models.OperatingLocality
import com.example.rideconnect.models.OperatingSection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SelectedLocationsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var operatingAreasContainer: LinearLayout
    private lateinit var selectedOperatingAreasContainer: LinearLayout
    private lateinit var operatingAreasSummary: TextView
    private lateinit var saveOperatingAreasButton: Button

    private var operatingSections: List<OperatingSection> = emptyList()

    private val selectedSublocalities = linkedSetOf<String>()
    private val expandedSections = linkedSetOf<String>()
    private val expandedLocalities = linkedSetOf<String>()

    private var entireMauritiusSelected = false

    private var accountLoaded = false

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

        setContentView(R.layout.activity_selected_locations)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        operatingAreasContainer =
            findViewById(R.id.operatingAreasContainer)

        selectedOperatingAreasContainer =
            findViewById(R.id.selectedOperatingAreasContainer)

        operatingAreasSummary =
            findViewById(R.id.operatingAreasSummary)

        saveOperatingAreasButton =
            findViewById(R.id.saveOperatingAreasButton)

        findViewById<Button>(R.id.backToDriverAccountButton)
            .setOnClickListener {
                finish()
            }

        saveOperatingAreasButton.setOnClickListener {
            saveOperatingAreas()
        }

        initializeOperatingAreaSelector()
    }

    // ---------------------------------------------------------
    // INITIALIZE OPERATING AREA SELECTOR
    // ---------------------------------------------------------

    private fun initializeOperatingAreaSelector() {

        try {

            operatingSections = OperatingAreaRepository.load(this)

            allAreaEntries.clear()

            operatingSections.forEach { section ->

                section.localities.forEach { locality ->

                    locality.sublocalities.forEach { sublocality ->

                        val key = createAreaKey(
                            section.name,
                            locality.name,
                            sublocality.postcode,
                            sublocality.name
                        )

                        allAreaEntries.add(
                            AreaEntry(
                                key = key,
                                section = section.name,
                                locality = locality.name,
                                sublocality = sublocality.name,
                                postcode = sublocality.postcode
                            )
                        )
                    }
                }
            }

            loadOperatingAreas()

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Failed to load operating areas: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createAreaKey(
        section: String,
        locality: String,
        postcode: String,
        sublocality: String
    ): String {

        return "$section|$locality|$postcode|$sublocality"
    }

    // ---------------------------------------------------------
    // LOAD SAVED OPERATING AREAS
    // ---------------------------------------------------------

    private fun loadOperatingAreas() {

        val driverId = auth.currentUser?.uid

        if (driverId == null) {

            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {

                    Toast.makeText(
                        this,
                        "Driver account not found",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                    return@addOnSuccessListener
                }

                entireMauritiusSelected =
                    document.getBoolean("operatesThroughoutMauritius")
                        ?: (
                                document.getString("operatingAreaMode")
                                        == "ALL_MAURITIUS"
                                )

                selectedSublocalities.clear()

                if (!entireMauritiusSelected) {

                    val savedAreas =
                        document.get("operatingAreas") as? List<*>

                    savedAreas?.forEach { item ->

                        val area = item as? Map<*, *>
                            ?: return@forEach

                        val section =
                            area["section"] as? String
                                ?: return@forEach

                        val locality =
                            area["locality"] as? String
                                ?: return@forEach

                        val sublocality =
                            area["sublocality"] as? String
                                ?: return@forEach

                        val postcode =
                            area["postcode"] as? String
                                ?: return@forEach

                        val key = createAreaKey(
                            section,
                            locality,
                            postcode,
                            sublocality
                        )

                        if (allAreaEntries.any { it.key == key }) {
                            selectedSublocalities.add(key)
                        }
                    }
                }

                accountLoaded = true

                displayOperatingAreas()
                updateSelectedAreasDisplay()
            }
            .addOnFailureListener { exception ->

                Toast.makeText(
                    this,
                    "Failed to load operating areas: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // DISPLAY OPERATING AREAS
    // ---------------------------------------------------------

    private fun displayOperatingAreas() {

        operatingAreasContainer.removeAllViews()

        // Entire Mauritius option
        val nationwideCheckBox = CheckBox(this).apply {

            text = "Entire Mauritius"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            isChecked = entireMauritiusSelected

            setPadding(
                dp(8),
                dp(10),
                dp(8),
                dp(10)
            )
        }

        operatingAreasContainer.addView(nationwideCheckBox)

        nationwideCheckBox.setOnClickListener {

            entireMauritiusSelected =
                nationwideCheckBox.isChecked

            if (entireMauritiusSelected) {
                selectedSublocalities.clear()
            }

            displayOperatingAreas()
            updateSelectedAreasDisplay()
        }

        val nationwideDescription = TextView(this).apply {

            text = "Selecting this allows you to operate throughout Mauritius for shared rides."
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))

            setPadding(
                dp(36),
                0,
                dp(8),
                dp(12)
            )
        }

        operatingAreasContainer.addView(nationwideDescription)

        // Five geographical sections
        operatingSections.forEach { section ->

            val sectionEntries = allAreaEntries.filter {
                it.section == section.name
            }

            val selectedCount = if (entireMauritiusSelected) {

                sectionEntries.size

            } else {

                sectionEntries.count {
                    selectedSublocalities.contains(it.key)
                }
            }

            val sectionRow = LinearLayout(this).apply {

                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )

                background = createAreaBackground()
            }

            val sectionCheckBox = CheckBox(this).apply {

                text = section.name
                textSize = 15f
                setTypeface(null, Typeface.BOLD)

                isChecked = sectionEntries.isNotEmpty() &&
                        selectedCount == sectionEntries.size

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            sectionRow.addView(sectionCheckBox)

            val expandButton = TextView(this).apply {

                text = if (expandedSections.contains(section.name)) {
                    "Hide ▲"
                } else {
                    "View areas ▼"
                }

                textSize = 12f
                setTextColor(getColor(R.color.teal))

                setPadding(
                    dp(8),
                    dp(12),
                    dp(8),
                    dp(12)
                )
            }

            sectionRow.addView(expandButton)

            operatingAreasContainer.addView(
                sectionRow,
                areaLayoutParams(bottom = 6)
            )

            sectionCheckBox.setOnClickListener {

                setSectionSelection(
                    section.name,
                    sectionCheckBox.isChecked
                )
            }

            expandButton.setOnClickListener {

                if (expandedSections.contains(section.name)) {
                    expandedSections.remove(section.name)
                } else {
                    expandedSections.add(section.name)
                }

                displayOperatingAreas()
            }

            val statusText = TextView(this).apply {

                text = "$selectedCount of ${sectionEntries.size} locations selected"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))

                setPadding(
                    dp(44),
                    0,
                    dp(8),
                    dp(8)
                )
            }

            operatingAreasContainer.addView(statusText)

            if (expandedSections.contains(section.name)) {

                section.localities.forEach { locality ->

                    displayLocality(
                        section,
                        locality
                    )
                }
            }
        }

        updateOperatingAreasSummary()
    }

    // ---------------------------------------------------------
    // DISPLAY LOCALITIES AND SUBLOCALITIES
    // ---------------------------------------------------------

    private fun displayLocality(
        section: OperatingSection,
        locality: OperatingLocality
    ) {

        val localityEntries = allAreaEntries.filter {

            it.section == section.name &&
                    it.locality == locality.name
        }

        val selectedCount = if (entireMauritiusSelected) {

            localityEntries.size

        } else {

            localityEntries.count {
                selectedSublocalities.contains(it.key)
            }
        }

        val localityRow = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            setPadding(
                dp(12),
                dp(6),
                dp(8),
                dp(6)
            )
        }

        val localityCheckBox = CheckBox(this).apply {

            text = locality.name
            textSize = 14f

            isChecked = localityEntries.isNotEmpty() &&
                    selectedCount == localityEntries.size

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        localityRow.addView(localityCheckBox)

        val localityKey = "${section.name}|${locality.name}"

        val expandButton = TextView(this).apply {

            text = if (expandedLocalities.contains(localityKey)) {
                "Hide ▲"
            } else {
                "View ▼"
            }

            textSize = 12f
            setTextColor(getColor(R.color.teal))

            setPadding(
                dp(8),
                dp(12),
                dp(8),
                dp(12)
            )
        }

        localityRow.addView(expandButton)

        operatingAreasContainer.addView(localityRow)

        localityCheckBox.setOnClickListener {

            setLocalitySelection(
                section.name,
                locality.name,
                localityCheckBox.isChecked
            )
        }

        val statusText = TextView(this).apply {

            text = "$selectedCount of ${localityEntries.size} sublocalities selected"
            textSize = 11f
            setTextColor(getColor(R.color.text_secondary))

            setPadding(
                dp(48),
                0,
                dp(8),
                dp(4)
            )
        }

        operatingAreasContainer.addView(statusText)

        expandButton.setOnClickListener {

            if (expandedLocalities.contains(localityKey)) {
                expandedLocalities.remove(localityKey)
            } else {
                expandedLocalities.add(localityKey)
            }

            displayOperatingAreas()
        }

        if (expandedLocalities.contains(localityKey)) {

            locality.sublocalities.forEach { sublocality ->

                val areaKey = createAreaKey(
                    section.name,
                    locality.name,
                    sublocality.postcode,
                    sublocality.name
                )

                val sublocalityCheckBox = CheckBox(this).apply {

                    text = "${sublocality.name} (${sublocality.postcode})"
                    textSize = 13f

                    isChecked = entireMauritiusSelected ||
                            selectedSublocalities.contains(areaKey)

                    setPadding(
                        dp(48),
                        dp(3),
                        dp(8),
                        dp(3)
                    )
                }

                operatingAreasContainer.addView(
                    sublocalityCheckBox
                )

                sublocalityCheckBox.setOnClickListener {

                    setIndividualSublocalitySelection(
                        areaKey,
                        sublocalityCheckBox.isChecked
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // SELECTION LOGIC
    // ---------------------------------------------------------

    private fun setSectionSelection(
        sectionName: String,
        isSelected: Boolean
    ) {

        if (entireMauritiusSelected && !isSelected) {

            entireMauritiusSelected = false

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        val sectionKeys = allAreaEntries
            .filter {
                it.section == sectionName
            }
            .map {
                it.key
            }

        if (isSelected) {
            selectedSublocalities.addAll(sectionKeys)
        } else {
            selectedSublocalities.removeAll(sectionKeys.toSet())
        }

        displayOperatingAreas()
        updateSelectedAreasDisplay()
    }

    private fun setLocalitySelection(
        sectionName: String,
        localityName: String,
        isSelected: Boolean
    ) {

        if (entireMauritiusSelected && !isSelected) {

            entireMauritiusSelected = false

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        val localityKeys = allAreaEntries
            .filter {
                it.section == sectionName &&
                        it.locality == localityName
            }
            .map {
                it.key
            }

        if (isSelected) {
            selectedSublocalities.addAll(localityKeys)
        } else {
            selectedSublocalities.removeAll(localityKeys.toSet())
        }

        displayOperatingAreas()
        updateSelectedAreasDisplay()
    }

    private fun setIndividualSublocalitySelection(
        areaKey: String,
        isSelected: Boolean
    ) {

        if (entireMauritiusSelected) {

            entireMauritiusSelected = false

            allAreaEntries.forEach {
                selectedSublocalities.add(it.key)
            }
        }

        if (isSelected) {
            selectedSublocalities.add(areaKey)
        } else {
            selectedSublocalities.remove(areaKey)
        }

        displayOperatingAreas()
        updateSelectedAreasDisplay()
    }

    // ---------------------------------------------------------
    // SELECTED AREA SUMMARY
    // ---------------------------------------------------------

    private fun updateOperatingAreasSummary() {

        operatingAreasSummary.text = if (entireMauritiusSelected) {

            "Entire Mauritius selected. You can operate throughout Mauritius."

        } else {

            "${selectedSublocalities.size} individual locations selected."
        }
    }

    private fun updateSelectedAreasDisplay() {

        selectedOperatingAreasContainer.removeAllViews()

        if (entireMauritiusSelected) {

            addSummaryText(
                "Entire Mauritius",
                true
            )

            updateOperatingAreasSummary()
            return
        }

        val selectedEntries = allAreaEntries.filter {
            selectedSublocalities.contains(it.key)
        }

        if (selectedEntries.isEmpty()) {

            addSummaryText(
                "No operating areas selected.",
                false
            )

            updateOperatingAreasSummary()
            return
        }

        val groupedAreas = selectedEntries.groupBy {
            it.section
        }

        groupedAreas.forEach { (sectionName, entries) ->

            addSummaryText(
                "$sectionName (${entries.size})",
                true
            )

            entries.groupBy {
                it.locality
            }.forEach { (locality, locations) ->

                addSummaryText(
                    "  $locality",
                    true
                )

                locations.forEach { entry ->

                    addSummaryText(
                        "      • ${entry.sublocality} (${entry.postcode})",
                        false
                    )
                }
            }
        }

        updateOperatingAreasSummary()
    }

    private fun addSummaryText(
        value: String,
        bold: Boolean
    ) {

        val textView = TextView(this).apply {

            text = value
            textSize = 13f
            setTextColor(getColor(R.color.text_primary))

            setPadding(
                dp(4),
                dp(3),
                dp(4),
                dp(3)
            )

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }

        selectedOperatingAreasContainer.addView(textView)
    }

    // ---------------------------------------------------------
    // SAVE OPERATING AREAS ONLY
    // ---------------------------------------------------------

    private fun saveOperatingAreas() {

        if (!accountLoaded) {

            Toast.makeText(
                this,
                "Please wait until your saved locations have loaded.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val driverId = auth.currentUser?.uid

        if (driverId == null) {

            Toast.makeText(
                this,
                "Driver is not logged in",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (!entireMauritiusSelected &&
            selectedSublocalities.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Please select Entire Mauritius or at least one operating location.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        saveOperatingAreasButton.isEnabled = false

        val selectedAreaDocuments =
            if (entireMauritiusSelected) {

                emptyList<Map<String, String>>()

            } else {

                allAreaEntries
                    .filter {
                        selectedSublocalities.contains(it.key)
                    }
                    .map { entry ->

                        hashMapOf(
                            "section" to entry.section,
                            "locality" to entry.locality,
                            "sublocality" to entry.sublocality,
                            "postcode" to entry.postcode
                        )
                    }
            }

        val updates = hashMapOf<String, Any>(

            "operatesThroughoutMauritius" to entireMauritiusSelected,

            "operatingAreaMode" to if (entireMauritiusSelected) {
                "ALL_MAURITIUS"
            } else {
                "SELECTED_AREAS"
            },

            "operatingAreas" to selectedAreaDocuments,

            "operatingSections" to if (entireMauritiusSelected) {
                emptyList<String>()
            } else {
                selectedAreaDocuments
                    .map {
                        it["section"] ?: ""
                    }
                    .distinct()
            }
        )

        db.collection("users")
            .document(driverId)
            .update(updates)
            .addOnSuccessListener {

                saveOperatingAreasButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Operating locations saved successfully!",
                    Toast.LENGTH_LONG
                ).show()

                updateSelectedAreasDisplay()
            }
            .addOnFailureListener { exception ->

                saveOperatingAreasButton.isEnabled = true

                Toast.makeText(
                    this,
                    "Failed to save operating locations: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private fun createAreaBackground(): GradientDrawable {

        return GradientDrawable().apply {

            setColor(getColor(R.color.screen_background))
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun areaLayoutParams(
        bottom: Int = 0
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {

            bottomMargin = dp(bottom)
        }
    }

    private fun dp(value: Int): Int {

        return (
                value * resources.displayMetrics.density
                ).toInt()
    }
}
