
package com.example.rideconnect.models

import android.content.Context
import org.json.JSONObject

data class OperatingSublocality(
    val name: String,
    val postcode: String
)

data class OperatingLocality(
    val name: String,
    val sublocalities: List<OperatingSublocality>
)

data class OperatingSection(
    val name: String,
    val localities: List<OperatingLocality>
)

object OperatingAreaRepository {

    fun load(context: Context): List<OperatingSection> {

        val json = context.assets
            .open("rideconnect_localities.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val root = JSONObject(json)
        val sectionsArray = root.getJSONArray("sections")

        val sections = mutableListOf<OperatingSection>()

        for (i in 0 until sectionsArray.length()) {

            val sectionObject = sectionsArray.getJSONObject(i)
            val sectionName = sectionObject.getString("name")
            val localitiesArray = sectionObject.getJSONArray("localities")

            val localities = mutableListOf<OperatingLocality>()

            for (j in 0 until localitiesArray.length()) {

                val localityObject = localitiesArray.getJSONObject(j)
                val localityName = localityObject.getString("name")
                val sublocalitiesArray =
                    localityObject.getJSONArray("sublocalities")

                val sublocalities = mutableListOf<OperatingSublocality>()

                for (k in 0 until sublocalitiesArray.length()) {

                    val sublocalityObject =
                        sublocalitiesArray.getJSONObject(k)

                    sublocalities.add(
                        OperatingSublocality(
                            name = sublocalityObject.getString("name"),
                            postcode = sublocalityObject.getString("postcode")
                        )
                    )
                }

                localities.add(
                    OperatingLocality(
                        name = localityName,
                        sublocalities = sublocalities
                    )
                )
            }

            sections.add(
                OperatingSection(
                    name = sectionName,
                    localities = localities
                )
            )
        }

        return sections
    }
}
