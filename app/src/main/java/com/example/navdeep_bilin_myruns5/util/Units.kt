package com.example.navdeep_bilin_myruns5.util

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale
import kotlin.math.roundToInt

object Units {
    enum class System { METRIC, IMPERIAL }

    // reads 'pref_units' key when present and a boolean fallback
    fun system(ctx: Context): System {
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)

        // 1) Try string-based ListPreference (common): "km" or "mi"
        val str = sp.getString("pref_units", null) // <-- update key if different
        if (str != null) {
            return if (str.equals("km", true) || str.equals("metric", true)) System.METRIC else System.IMPERIAL
        }

        // 2) Fallback to boolean switch (your earlier version)
        val metric = sp.getBoolean("pref_key_use_metric", true)
        return if (metric) System.METRIC else System.IMPERIAL
    }

    fun metersToPreferred(context: Context, meters: Double): Pair<Double, String> {
        return if (system(context) == System.METRIC) metersToKm(meters) to "Kilometers"
        else metersToMiles(meters) to "Miles" // convert and label based on user Preference
    }

    // converts users input to meters
    fun preferredToMeters(ctx: Context, value: Double): Double =
        if (system(ctx) == System.METRIC) value * 1000.0 else value * 1609.344

    private fun metersToKm(m: Double) = m / 1000.0
    private fun kmToMeters(km: Double) = km * 1000.0
    private fun metersToMiles(m: Double) = m / 1609.344
    private fun milesToMeters(mi: Double) = mi * 1609.344

    fun formatDistance(ctx: Context, meters: Double): String =
        if (system(ctx) == System.METRIC)
            String.format(Locale.getDefault(), "%.2f Kilometers", meters / 1000.0)
        else
            // user facing distance string
            String.format(Locale.getDefault(), "%.2f Miles", meters / 1609.344)

    fun minutesSecondsFromDecimalMinutes(decimalMinutes: Double): Pair<Int, Int> {
        val mins = decimalMinutes.toInt()
        val secs = ((decimalMinutes - mins) * 60.0).roundToInt()
        // handles the 60 second edge case, here it will round up
        return if (secs == 60) mins + 1 to 0 else mins to secs
    }

    fun formatDurationFromDecimalMinutes(decimalMinutes: Double): String {
        val (m, s) = minutesSecondsFromDecimalMinutes(decimalMinutes)
        return "${m}mins ${s}secs" // converts decimal inputs to respective min,sec
    }

    // converts total seocnds to "M mins S Secs" per entry
    fun formatDurationFromSeconds(seconds: Double): String {
        val total = seconds.toInt()
        val m = total / 60
        val s = total % 60
        return "${m}mins ${s}secs" // converts raw seconds to readable format
    }
}


// * Responsibilities:
// *  - Detect user's unit system from SharedPreferences
// *  - Convert preferred units to meters for storage
// *  - Format display strings in km or miles
// *  - Format duration "Xmins Ysecs" from seconds
// * Preference compatibility:
// *  - Supports ListPreference ("pref_units" values "km"/"mi") and a boolean fallback
// *
// * AI notice:
// *  - The system(ctx) function was adapted with AI assistance to support both
// *    string and boolean preference styles safely
