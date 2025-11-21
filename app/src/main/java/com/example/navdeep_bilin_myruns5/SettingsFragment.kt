package com.example.navdeep_bilin_myruns5

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Open Profile screen from a preference (lecture preference)
        findPreference<Preference>("pref_user_profile")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
            true
        }

        // adaptation implementation: keep the listPreference summary synced with selected entry
        findPreference<ListPreference>("pref_units")?.apply {
            summary = entry
            setOnPreferenceChangeListener { pref, newValue ->
                val lp = pref as ListPreference
                val index = lp.entryValues.indexOf(newValue.toString())
                if (index >= 0) lp.summary = lp.entries[index]
                true // saves automatically
            }
        }

        // reflect current comment as summary for comment field
        findPreference<EditTextPreference>("pref_comment")?.apply {
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        }

        // 4) Example link
        findPreference<Preference>("pref_homepage")?.setOnPreferenceClickListener {
            val url = "https://www.sfu.ca/fas/computing.html" // example
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }
    }
}