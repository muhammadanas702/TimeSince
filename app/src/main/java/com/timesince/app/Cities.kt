package com.timesince.app

import java.util.Locale

/**
 * One city of the world clock: a display name, a time zone id such as Asia/Karachi, and the country
 * (used only by the search box, so "korea" finds Seoul).
 */
data class Zone(val name: String, val id: String, val country: String = "") {

    /** What the city list shows, for example "Seoul · South Korea". */
    fun label(): String = if (country.isEmpty()) name else "$name · $country"

    /** True when the search text is part of the city, the country or the time zone id. */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase(Locale.ENGLISH)
        if (q.isEmpty()) return true
        return name.lowercase(Locale.ENGLISH).contains(q) ||
            country.lowercase(Locale.ENGLISH).contains(q) ||
            id.replace('_', ' ').lowercase(Locale.ENGLISH).contains(q)
    }
}

object Cities {
    /** Follows whatever time zone the phone is set to. An empty id means "use the phone's zone". */
    val LOCAL = Zone("Local time", "")

    val ALL: List<Zone> = listOf(
        // Pakistan and the Gulf
        Zone("Lahore", "Asia/Karachi", "Pakistan"),
        Zone("Karachi", "Asia/Karachi", "Pakistan"),
        Zone("Islamabad", "Asia/Karachi", "Pakistan"),
        Zone("Dubai", "Asia/Dubai", "United Arab Emirates"),
        Zone("Abu Dhabi", "Asia/Dubai", "United Arab Emirates"),
        Zone("Riyadh", "Asia/Riyadh", "Saudi Arabia"),
        Zone("Jeddah", "Asia/Riyadh", "Saudi Arabia"),
        Zone("Mecca", "Asia/Riyadh", "Saudi Arabia"),
        Zone("Doha", "Asia/Qatar", "Qatar"),
        Zone("Kuwait City", "Asia/Kuwait", "Kuwait"),
        Zone("Manama", "Asia/Bahrain", "Bahrain"),
        Zone("Muscat", "Asia/Muscat", "Oman"),
        Zone("Baghdad", "Asia/Baghdad", "Iraq"),
        Zone("Tehran", "Asia/Tehran", "Iran"),
        Zone("Kabul", "Asia/Kabul", "Afghanistan"),
        Zone("Tashkent", "Asia/Tashkent", "Uzbekistan"),
        Zone("Amman", "Asia/Amman", "Jordan"),
        Zone("Beirut", "Asia/Beirut", "Lebanon"),
        // South, South-East and East Asia
        Zone("Delhi", "Asia/Kolkata", "India"),
        Zone("Mumbai", "Asia/Kolkata", "India"),
        Zone("Colombo", "Asia/Colombo", "Sri Lanka"),
        Zone("Kathmandu", "Asia/Kathmandu", "Nepal"),
        Zone("Dhaka", "Asia/Dhaka", "Bangladesh"),
        Zone("Bangkok", "Asia/Bangkok", "Thailand"),
        Zone("Jakarta", "Asia/Jakarta", "Indonesia"),
        Zone("Manila", "Asia/Manila", "Philippines"),
        Zone("Ho Chi Minh City", "Asia/Ho_Chi_Minh", "Vietnam"),
        Zone("Singapore", "Asia/Singapore", "Singapore"),
        Zone("Kuala Lumpur", "Asia/Kuala_Lumpur", "Malaysia"),
        Zone("Hong Kong", "Asia/Hong_Kong", "Hong Kong"),
        Zone("Taipei", "Asia/Taipei", "Taiwan"),
        Zone("Shanghai", "Asia/Shanghai", "China"),
        Zone("Beijing", "Asia/Shanghai", "China"),
        Zone("Seoul", "Asia/Seoul", "South Korea"),
        Zone("Busan", "Asia/Seoul", "South Korea"),
        Zone("Tokyo", "Asia/Tokyo", "Japan"),
        // Oceania
        Zone("Sydney", "Australia/Sydney", "Australia"),
        Zone("Melbourne", "Australia/Melbourne", "Australia"),
        Zone("Perth", "Australia/Perth", "Australia"),
        Zone("Auckland", "Pacific/Auckland", "New Zealand"),
        Zone("Honolulu", "Pacific/Honolulu", "United States"),
        // Africa
        Zone("Cairo", "Africa/Cairo", "Egypt"),
        Zone("Lagos", "Africa/Lagos", "Nigeria"),
        Zone("Nairobi", "Africa/Nairobi", "Kenya"),
        Zone("Johannesburg", "Africa/Johannesburg", "South Africa"),
        Zone("Casablanca", "Africa/Casablanca", "Morocco"),
        Zone("Addis Ababa", "Africa/Addis_Ababa", "Ethiopia"),
        Zone("Accra", "Africa/Accra", "Ghana"),
        // Europe
        Zone("Istanbul", "Europe/Istanbul", "Turkey"),
        Zone("Moscow", "Europe/Moscow", "Russia"),
        Zone("London", "Europe/London", "United Kingdom"),
        Zone("Dublin", "Europe/Dublin", "Ireland"),
        Zone("Paris", "Europe/Paris", "France"),
        Zone("Berlin", "Europe/Berlin", "Germany"),
        Zone("Madrid", "Europe/Madrid", "Spain"),
        Zone("Lisbon", "Europe/Lisbon", "Portugal"),
        Zone("Rome", "Europe/Rome", "Italy"),
        Zone("Amsterdam", "Europe/Amsterdam", "Netherlands"),
        Zone("Brussels", "Europe/Brussels", "Belgium"),
        Zone("Zurich", "Europe/Zurich", "Switzerland"),
        Zone("Vienna", "Europe/Vienna", "Austria"),
        Zone("Stockholm", "Europe/Stockholm", "Sweden"),
        Zone("Oslo", "Europe/Oslo", "Norway"),
        Zone("Copenhagen", "Europe/Copenhagen", "Denmark"),
        Zone("Helsinki", "Europe/Helsinki", "Finland"),
        Zone("Warsaw", "Europe/Warsaw", "Poland"),
        Zone("Athens", "Europe/Athens", "Greece"),
        Zone("Kyiv", "Europe/Kiev", "Ukraine"),
        // Americas
        Zone("New York", "America/New_York", "United States"),
        Zone("Chicago", "America/Chicago", "United States"),
        Zone("Denver", "America/Denver", "United States"),
        Zone("Los Angeles", "America/Los_Angeles", "United States"),
        Zone("San Francisco", "America/Los_Angeles", "United States"),
        Zone("Toronto", "America/Toronto", "Canada"),
        Zone("Vancouver", "America/Vancouver", "Canada"),
        Zone("Mexico City", "America/Mexico_City", "Mexico"),
        Zone("Bogota", "America/Bogota", "Colombia"),
        Zone("Lima", "America/Lima", "Peru"),
        Zone("Santiago", "America/Santiago", "Chile"),
        Zone("Sao Paulo", "America/Sao_Paulo", "Brazil"),
        Zone("Buenos Aires", "America/Argentina/Buenos_Aires", "Argentina"),
        Zone("UTC", "UTC", "Universal time")
    )

    /** What the city picker offers: the phone's own time zone first, then every city A to Z. */
    val CHOICES: List<Zone> = listOf(LOCAL) + ALL.sortedBy { it.name }

    fun byName(name: String): Zone = ALL.first { it.name == name }

    /** The three-letter label of the sample design: LON, NEW, SEO. */
    fun shortName(zone: Zone): String {
        if (zone.id.isEmpty()) return "LOCAL"
        return zone.name.filter { it.isLetter() }.take(3).uppercase(Locale.ENGLISH)
    }
}
