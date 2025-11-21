package com.example.navdeep_bilin_myruns5

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.navdeep_bilin_myruns5.data.ExerciseEntryEntity
import com.example.navdeep_bilin_myruns5.data.ExerciseRepository
import com.example.navdeep_bilin_myruns5.data.MyRunsDatabase
import com.example.navdeep_bilin_myruns5.util.Formatters
import com.example.navdeep_bilin_myruns5.util.Units
import com.example.navdeep_bilin_myruns5.viewmodel.HistoryViewModel
import com.example.navdeep_bilin_myruns5.viewmodel.HistoryViewModelFactory
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class HistoryFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<ExerciseEntryEntity>
    private lateinit var viewModel: HistoryViewModel

    private lateinit var prefs: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pref_key_use_metric") {
            // Rebind rows so Units.formatDistance() re runs with new preference
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        listView = view.findViewById(R.id.history_list)

        val deleteAllButton = view.findViewById<Button>(R.id.btnDeleteAll)
        deleteAllButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete all entries")
                .setMessage("This will permanently delete all saved entries. Continue?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteAllEntries()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Initialize database, repository, and ViewModel
        val db = MyRunsDatabase.getInstance(requireContext())
        val repo = ExerciseRepository(db.exerciseEntryDao())
        val factory = HistoryViewModelFactory(repo)
        viewModel = ViewModelProvider(this, factory)[HistoryViewModel::class.java]

        // Adapter with two line layout
        adapter = object : ArrayAdapter<ExerciseEntryEntity>(
            requireContext(),
            android.R.layout.simple_list_item_2,
            android.R.id.text1,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val title = row.findViewById<TextView>(android.R.id.text1)
                val subtitle = row.findViewById<TextView>(android.R.id.text2)
                val entry = getItem(position)

                if (entry != null) {
                    title.text = Formatters.titleLine(
                        requireContext(),
                        entry.inputType,
                        activityName(entry.activityType),
                        entry.dateTimeMillis
                    )
                    subtitle.text =
                        "${Units.formatDistance(requireContext(), entry.distanceMeters)}, " +
                                Units.formatDurationFromSeconds(entry.durationSec)
                }
                return row
            }
        }

        listView.adapter = adapter

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Observe DB changes and update UI automatically
        viewModel.entriesLiveData.observe(viewLifecycleOwner) { entries ->
            adapter.clear()
            adapter.addAll(entries)
            adapter.notifyDataSetChanged()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@setOnItemClickListener

            if (entry.inputType == 1 || entry.inputType == 2) {
                // 1 = GPS, 2 = Automatic → open NEW GPS detail screen
                val i = Intent(requireContext(), DisplayGpsActivity::class.java)
                i.putExtra("entry_id", entry.id)
                startActivity(i)
            } else {
                // Manual entry → open old text-only screen
                val i = Intent(requireContext(), DisplayEntryActivity::class.java)
                i.putExtra("ENTRY_ID", entry.id)
                startActivity(i)
            }
        }

        return view
    }

    private fun activityName(type: Int): String {
        val activities = resources.getStringArray(R.array.activity_type_entries)
        return activities.getOrNull(type) ?: "Unknown"
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // Defensively rebind in case we switched tabs right at insert commit time
        adapter.notifyDataSetChanged()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }
}

/*
 * Responsibilities:
 *  - Observe entries LiveData and render a two line list (title plus subtitle)
 *  - Title: "<Input type>: <Activity>, <Time> <Date>"
 *  - Subtitle: "<Distance in user units>, <Duration mins secs>"
 *  - Open DisplayEntryActivity for all entries
 *      Manual entries show text only
 *      GPS and Automatic entries can show a map if locationBlob is present
 *
 * Unit switching:
 *  - Registers a SharedPreferences listener; calls adapter.notifyDataSetChanged()
 *    so Units.formatDistance() re evaluates with new preference
 *
 * Lifecycle:
 *  - Observe with viewLifecycleOwner to avoid stale observers
 *
 * AI notice:
 *  - The lightweight preference listener to refresh rows on the fly was suggested by AI
 */
