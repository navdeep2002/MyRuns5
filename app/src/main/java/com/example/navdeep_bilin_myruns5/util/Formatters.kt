package com.example.navdeep_bilin_myruns5.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object Formatters {
    private fun dateTimeFormatter(): SimpleDateFormat =
        SimpleDateFormat("HH:mm:ss MMM dd yyyy", Locale.getDefault()) // time first format

    fun titleLine(context: Context, inputType: Int, activityType: String, timeMillis: Long): String {
        val inputLabel = when (inputType) { 0 -> "Manual Entry"; 1 -> "GPS"; else -> "Automatic" }
        val time = dateTimeFormatter().format(Date(timeMillis))
        return "$inputLabel: $activityType, $time" // creates a list item for primary text
    }

    fun subtitleLine(context: Context, distanceMeters: Double, durationSec: Double): String {
        val dist = Units.formatDistance(context, distanceMeters)
        val dur = Units.formatDurationFromSeconds(durationSec)
        return "$dist, $dur" // create the list item secondary text with converted units
    }

    fun dateThenTime(millis: Long): String { // format date before time
        val sdf = SimpleDateFormat("MMM dd yyyy, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis)) // date first format
    }
}


// * Contains:
// *  - dateThenTime(millis): "MMM dd yyyy, HH:mm:ss" used in DisplayEntry

// * AI notice:
// *  - SimpleDateFormat usage

