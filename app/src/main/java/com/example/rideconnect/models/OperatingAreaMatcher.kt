
package com.example.rideconnect.models

import com.example.rideconnect.routing.GeocodingResult
import java.util.Locale

data class OperatingAreaMatch(
    val section: String,
    val locality: String,
    val sublocality: String,
    val postcode: String
)

object OperatingAreaMatcher {

    fun findMatches(
        location: GeocodingResult,
        sections: List<OperatingSection>
    ): List<OperatingAreaMatch> {

        // Address fields returned by GraphHopper.
        val locationFields = listOf(
            location.name,
            location.city,
            location.county,
            location.state
        )
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()

        val locationPostcode = normalize(location.postcode)

        // Flatten the operating-area dataset.
        val allAreas = sections.flatMap { section ->

            section.localities.flatMap { locality ->

                locality.sublocalities.map { sublocality ->

                    OperatingAreaMatch(
                        section = section.name,
                        locality = locality.name,
                        sublocality = sublocality.name,
                        postcode = sublocality.postcode
                    )
                }
            }
        }.distinctBy {
            listOf(
                normalize(it.section),
                normalize(it.locality),
                normalize(it.sublocality),
                normalize(it.postcode)
            )
        }

        // ----------------------------------------------------
        // 1. TRY AN EXACT POSTCODE MATCH
        // ----------------------------------------------------

        if (locationPostcode.isNotBlank()) {

            val postcodeCandidates = allAreas.filter { area ->
                normalize(area.postcode) == locationPostcode
            }

            // Treat identical hierarchy entries as one candidate.
            val distinctCandidates = postcodeCandidates.distinctBy { area ->
                listOf(
                    normalize(area.section),
                    normalize(area.locality),
                    normalize(area.sublocality)
                )
            }

            // A postcode that identifies exactly one hierarchy
            // is sufficiently specific.
            if (distinctCandidates.size == 1) {
                return distinctCandidates
            }

            // If the postcode occurs in multiple hierarchies,
            // use GraphHopper's address fields to disambiguate.
            if (distinctCandidates.size > 1) {

                val narrowedCandidates = distinctCandidates.filter { area ->

                    val localityMatch =
                        locationFields.contains(normalize(area.locality))

                    val sublocalityMatch =
                        locationFields.contains(normalize(area.sublocality))

                    localityMatch || sublocalityMatch
                }

                // Only accept if the address fields identify
                // exactly one hierarchy.
                return if (narrowedCandidates.size == 1) {
                    narrowedCandidates
                } else {
                    emptyList()
                }
            }
        }

        // ----------------------------------------------------
        // 2. FALL BACK TO STRICT NAME MATCHING
        // ----------------------------------------------------

        // Without a usable postcode, BOTH locality and
        // sublocality must match. A sublocality name alone
        // is not sufficient because names can be repeated.
        return allAreas.filter { area ->

            val localityMatch =
                locationFields.contains(normalize(area.locality))

            val sublocalityMatch =
                locationFields.contains(normalize(area.sublocality))

            localityMatch && sublocalityMatch
        }
    }

    fun isWithinSelectedAreas(
        location: GeocodingResult,
        sections: List<OperatingSection>,
        selectedAreas: List<OperatingAreaMatch>
    ): Boolean {

        if (selectedAreas.isEmpty()) return false

        val actualMatches = findMatches(location, sections)

        // An unknown or ambiguous location must not be accepted.
        if (actualMatches.isEmpty()) return false

        // Every possible matching hierarchy must be selected.
        // This prevents an ambiguous geocoding result from
        // passing just because one of its matches is permitted.
        return actualMatches.all { actual ->

            selectedAreas.any { selected ->

                val sameHierarchy =
                    normalize(actual.section) ==
                            normalize(selected.section) &&
                            normalize(actual.locality) ==
                            normalize(selected.locality) &&
                            normalize(actual.sublocality) ==
                            normalize(selected.sublocality)

                val actualPostcode = normalize(actual.postcode)
                val selectedPostcode = normalize(selected.postcode)

                val postcodeCompatible =
                    actualPostcode.isBlank() ||
                            selectedPostcode.isBlank() ||
                            actualPostcode == selectedPostcode

                sameHierarchy && postcodeCompatible
            }
        }
    }

    private fun normalize(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
}
