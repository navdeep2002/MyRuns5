package com.example.navdeep_bilin_myruns5

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.navdeep_bilin_myruns5.data.ExerciseRepository
import com.example.navdeep_bilin_myruns5.data.MyRunsDatabase
import com.example.navdeep_bilin_myruns5.data.ExerciseEntryEntity
import com.example.navdeep_bilin_myruns5.util.LocationCodec
import com.example.navdeep_bilin_myruns5.util.Units
import com.example.navdeep_bilin_myruns5.viewmodel.DisplayEntryVMFactory
import com.example.navdeep_bilin_myruns5.viewmodel.DisplayEntryViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

class DisplayGpsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var vm: DisplayEntryViewModel
    private var entryId: Long = -1L

    // Stats overlay views
    private lateinit var tvActivityType: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvCalories: TextView
    private lateinit var btnDelete: Button

    // Map
    private var gMap: GoogleMap? = null
    private var routePoints: List<LatLng> = emptyList()

    // Prefs for metric / imperial
    private lateinit var prefs: SharedPreferences
    private var currentEntry: ExerciseEntryEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_gps_entry)

        // Read id (HistoryFragment sends "entry_id")
        entryId = intent.getLongExtra("entry_id", -1L)
        if (entryId == -1L) {
            // Fallback in case something launches with old key
            entryId = intent.getLongExtra("ENTRY_ID", -1L)
        }

        // Bind overlay views
        tvActivityType = findViewById(R.id.tvActivityType)
        tvDistance     = findViewById(R.id.tvDistance)
        tvDuration     = findViewById(R.id.tvDuration)
        tvAvgSpeed     = findViewById(R.id.tvAvgSpeed)
        tvCalories     = findViewById(R.id.tvCalories)
        btnDelete      = findViewById(R.id.btnDeleteEntry)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Set up map
        val mapFrag = supportFragmentManager
            .findFragmentById(R.id.displayGpsMap) as SupportMapFragment
        mapFrag.getMapAsync(this)

        // Set up repo + ViewModel (same pattern as DisplayEntryActivity)
        val dao = MyRunsDatabase.getInstance(this).exerciseEntryDao()
        val repo = ExerciseRepository(dao)
        val factory = DisplayEntryVMFactory(repo, entryId)
        vm = ViewModelProvider(this, factory)[DisplayEntryViewModel::class.java]

        vm.entry.observe(this) { e ->
            currentEntry = e
            if (e != null) {
                bindStats(e)

                if (e.inputType == 1 && e.locationBlob != null) {
                    routePoints = try {
                        LocationCodec.decode(e.locationBlob)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    drawRouteIfReady()
                } else {
                    routePoints = emptyList()
                    gMap?.clear()
                }
            }
        }
        vm.load()

        btnDelete.setOnClickListener {
            vm.delete { runOnUiThread { finish() } }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        gMap?.uiSettings?.isZoomControlsEnabled = true
        drawRouteIfReady()
    }

    /** Fill the overlay TextViews with stats for this GPS entry */
    private fun bindStats(e: ExerciseEntryEntity) {
        tvActivityType.text = "Activity: ${activityName(e.activityType)}"

        // Distance + duration using your existing helpers
        tvDistance.text = "Distance: ${Units.formatDistance(this, e.distanceMeters)}"
        tvDuration.text = "Duration: ${Units.formatDurationFromSeconds(e.durationSec)}"

        // Avg speed based on pref (metric / imperial)
        val distanceM   = e.distanceMeters ?: 0.0
        val durationSec = e.durationSec ?: 0.0
        val useMetric   = prefs.getBoolean("pref_key_use_metric", false)

        val avgMps = if (durationSec > 0.0) distanceM / durationSec else 0.0
        val avg = if (useMetric) avgMps * 3.6 else avgMps * 2.23694
        val speedUnit = if (useMetric) "km/h" else "mph"

        tvAvgSpeed.text = "Avg speed: %.2f %s".format(avg, speedUnit)

        tvCalories.text = "Calories: ${e.calorie?.toInt() ?: 0}"
    }

    private fun activityName(type: Int): String {
        val names = resources.getStringArray(R.array.activity_type_entries)
        return names.getOrNull(type) ?: "Unknown"
    }

    /* Draw saved route as a polyline with start/end markers */
    private fun drawRouteIfReady() {
        val map = gMap ?: return
        if (routePoints.isEmpty()) return

        map.clear()

        map.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .color(Color.BLUE)
                .width(8f)
        )

        val start = routePoints.first()
        val end   = routePoints.last()

        map.addMarker(
            MarkerOptions()
                .position(start)
                .title("Start")
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN
                    )
                )
        )

        map.addMarker(
            MarkerOptions()
                .position(end)
                .title("End")
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )
        )

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 16f))
    }
}
