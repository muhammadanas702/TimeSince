package com.timesince.app

/** One city row of the world clock: a display name and a time zone id such as Asia/Karachi. */
data class Zone(val name: String, val id: String)

object Cities {
    /** Follows whatever time zone the phone is set to. An empty id means "use the phone's zone". */
    val LOCAL = Zone("Local time", "")

    val ALL: List<Zone> = listOf(
        Zone("Lahore", "Asia/Karachi"),
        Zone("Dubai", "Asia/Dubai"),
        Zone("Riyadh", "Asia/Riyadh"),
        Zone("Doha", "Asia/Qatar"),
        Zone("Muscat", "Asia/Muscat"),
        Zone("Baghdad", "Asia/Baghdad"),
        Zone("Tehran", "Asia/Tehran"),
        Zone("Kabul", "Asia/Kabul"),
        Zone("Tashkent", "Asia/Tashkent"),
        Zone("Delhi", "Asia/Kolkata"),
        Zone("Colombo", "Asia/Colombo"),
        Zone("Kathmandu", "Asia/Kathmandu"),
        Zone("Dhaka", "Asia/Dhaka"),
        Zone("Bangkok", "Asia/Bangkok"),
        Zone("Jakarta", "Asia/Jakarta"),
        Zone("Singapore", "Asia/Singapore"),
        Zone("Kuala Lumpur", "Asia/Kuala_Lumpur"),
        Zone("Hong Kong", "Asia/Hong_Kong"),
        Zone("Shanghai", "Asia/Shanghai"),
        Zone("Seoul", "Asia/Seoul"),
        Zone("Tokyo", "Asia/Tokyo"),
        Zone("Sydney", "Australia/Sydney"),
        Zone("Melbourne", "Australia/Melbourne"),
        Zone("Auckland", "Pacific/Auckland"),
        Zone("Honolulu", "Pacific/Honolulu"),
        Zone("Istanbul", "Europe/Istanbul"),
        Zone("Cairo", "Africa/Cairo"),
        Zone("Lagos", "Africa/Lagos"),
        Zone("Nairobi", "Africa/Nairobi"),
        Zone("Johannesburg", "Africa/Johannesburg"),
        Zone("Moscow", "Europe/Moscow"),
        Zone("Berlin", "Europe/Berlin"),
        Zone("Paris", "Europe/Paris"),
        Zone("Madrid", "Europe/Madrid"),
        Zone("Rome", "Europe/Rome"),
        Zone("London", "Europe/London"),
        Zone("New York", "America/New_York"),
        Zone("Toronto", "America/Toronto"),
        Zone("Chicago", "America/Chicago"),
        Zone("Denver", "America/Denver"),
        Zone("Los Angeles", "America/Los_Angeles"),
        Zone("Mexico City", "America/Mexico_City"),
        Zone("Sao Paulo", "America/Sao_Paulo"),
        Zone("Buenos Aires", "America/Argentina/Buenos_Aires"),
        Zone("UTC", "UTC")
    )

    /** What the city picker offers, with the phone's own time zone first. */
    val CHOICES: List<Zone> = listOf(LOCAL) + ALL

    fun byName(name: String): Zone = ALL.first { it.name == name }
}
