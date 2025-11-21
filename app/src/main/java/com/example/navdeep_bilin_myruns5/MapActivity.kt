package com.example.navdeep_bilin_myruns5

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.navdeep_bilin_myruns5.data.ExerciseEntryEntity
import com.example.navdeep_bilin_myruns5.data.ExerciseRepository
import com.example.navdeep_bilin_myruns5.data.MyRunsDatabase
import com.example.navdeep_bilin_myruns5.service.TrackingService
import com.example.navdeep_bilin_myruns5.util.LocationCodec
import com.example.navdeep_bilin_myruns5.util.Units
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// this shows the live map and tracks the single workout / exercise
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    private lateinit var tvStats: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // holds the current path points and stats for the excercise
    private var mapCentered = false
    private lateinit var polylineOptions: PolylineOptions
    private var polyline: Polyline? = null
    private var startMarker: Marker? = null
    private var currentMarker: Marker? = null
    private val path = mutableListOf<LatLng>()

    private var startTimeMs: Long = 0L

    private var trackingStarted = false
    private var lastPoint: LatLng? = null
    private var totalDistanceM = 0f

    // calories + entry type
    private var caloriesBurned = 0.0
    private var inputTypeFromIntent = 1
    private var activityTypeFromIntent = 0

    // inferred activity type from service (Running/Walking/Standing index)
    private var inferredActivityType: Int? = null

    private val repo by lazy {
        val dao = MyRunsDatabase.getInstance(applicationContext).exerciseEntryDao()
        ExerciseRepository(dao)
    }

    //  broadcast from TrackingService
    private var receiverRegistered = false

    // receive the location and update from trackingservice.kt
    private val locReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (!::mMap.isInitialized) return

            val lat = intent.getDoubleExtra(TrackingService.EXTRA_LAT, Double.NaN)
            val lng = intent.getDoubleExtra(TrackingService.EXTRA_LNG, Double.NaN)
            val time = intent.getLongExtra(
                TrackingService.EXTRA_TIME,
                System.currentTimeMillis()
            )
            if (lat.isNaN() || lng.isNaN()) return

            val speed = intent.getFloatExtra(TrackingService.EXTRA_SPEED, 0f)

            // pick up inferred activity type from service if present
            val svcType = intent.getIntExtra(TrackingService.EXTRA_ACTIVITY_TYPE, -1)
            if (svcType != -1) {
                inferredActivityType = svcType
            }

            val loc = Location("mr4").apply {
                latitude = lat
                longitude = lng
                this.time = time
                this.speed = speed
            }

            handleLocationUpdate(loc)
        }
    }


    // Activity lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        inputTypeFromIntent = intent.getIntExtra("input_type", 1)
        activityTypeFromIntent = intent.getIntExtra("activity_type", 0)

        tvStats = findViewById(R.id.type_stats)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // set up the map view and asynchronous map callback
        val frag = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        frag.getMapAsync(this)

        btnSave.setOnClickListener { saveEntryAndFinish() }
        btnCancel.setOnClickListener {
            stopTrackingService()
            finish()
        }

        val filter = IntentFilter(TrackingService.ACTION_LOC_BROADCAST)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                locReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(locReceiver, filter)
        }
        receiverRegistered = true

        // requets the location permission and start tracking if it is allowed by user
        checkPermsAndMaybeStart()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(locReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        super.onDestroy()
    }


    // called when the google map's is ready to be used
    override fun onMapReady(map: GoogleMap) {
        mMap = map
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true

        // draws the polyline for the current recorded path
        polylineOptions = PolylineOptions()
            .color(Color.BLUE)
            .width(8f)
    }


    // Permissions + service start
    private fun checkPermsAndMaybeStart() {
        val need = mutableListOf<String>()

        // check if the locaiton permission is already granted or not
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }

        if (need.isNotEmpty()) {

            // request the location permission from the user
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 42)
        } else {
            startTrackingService()
        }
    }

    // hanles the result of the permission request dialog box
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startTrackingService()
        } else {

            // show a toast message if perm is denied
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // start the foreground tracking service for the gps updates
    private fun startTrackingService() {
        val intent = Intent(this, TrackingService::class.java)
            .setAction(TrackingService.ACTION_START)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }


    // stop the tracking service and finish the tracking as well
    private fun stopTrackingService() {
        val intent = Intent(this, TrackingService::class.java)
            .setAction(TrackingService.ACTION_STOP)
        stopService(intent)
    }

    // Location handling (from broadcasts), adds a new point to the path and update poly line
    private fun handleLocationUpdate(loc: Location) {

        if (!trackingStarted) {
            // start the timer exactly when first gps point arrives
            startTimeMs = loc.time
            trackingStarted = true
        }
        val latLng = LatLng(loc.latitude, loc.longitude)
        path += latLng

        // distance using previous LatLng
        lastPoint?.let { prev ->
            val results = FloatArray(1)
            Location.distanceBetween(
                prev.latitude, prev.longitude,
                latLng.latitude, latLng.longitude,
                results
            )
            totalDistanceM += results[0]   // meters
        }
        lastPoint = latLng

        if (!mapCentered) {

            // move the camera to the current gps location
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            mapCentered = true

            startMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Start")
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
            )
            polyline = mMap.addPolyline(polylineOptions)
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Current")
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_RED
                        )
                    )
            )
        } else {

            // draw or update the polyline for the current recorded path
            polyline?.points = path
            currentMarker?.position = latLng
        }

        // re-calculate the distance, speed, and calories from the path
        updateStats(loc)
    }

    // Calorie helper, assumed a 70kg weight
    /*
     * kcal = MET * 70kg * hours
     */
    private fun estimateCalories(durationSec: Double, activityType: Int): Double {
        val met = when (activityType) {
            0 -> 8.0   // Running
            1 -> 3.5   // Walking
            2 -> 6.0   // Cycling
            else -> 4.0
        }

        val weightKg = 70.0
        val hours = durationSec / 3600.0

        return met * weightKg * hours
    }

    // updatees the stats in textviews with the latest values on the map
    private fun updateStats(loc: Location) {

        if (!trackingStarted) {
            tvStats.text = "Waiting for GPS..."
            return
        }

        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
        val avgMps = if (elapsedSec > 0) totalDistanceM / elapsedSec else 0.0

        // Current speed (zero until we actually move)
        val curMps = if (totalDistanceM < 1f) 0.0 else loc.speed.toDouble()

        val system = Units.system(this)

        val avgSpeedStr = if (system == Units.System.IMPERIAL)
            String.format("%.2f mph", avgMps * 2.23694)
        else
            String.format("%.2f km/h", avgMps * 3.6)

        val curSpeedStr = if (system == Units.System.IMPERIAL)
            String.format("%.2f mph", curMps * 2.23694)
        else
            String.format("%.2f km/h", curMps * 3.6)

        // Distance using Units util
        val (distVal, distUnit) = Units.metersToPreferred(this, totalDistanceM.toDouble())

        // For calories, use inferred activity if available (automatic mode), else original
        val activityForCalories = inferredActivityType ?: activityTypeFromIntent
        caloriesBurned = if (totalDistanceM < 1f) 0.0
        else estimateCalories(elapsedSec, activityForCalories)

        // Choose label to display:
        // - if we have an inferred type (automatic), show that
        // - otherwise show the original spinner type
        val typeLabel = activityName(inferredActivityType ?: activityTypeFromIntent)

        tvStats.text =
            "Type: $typeLabel\n" +
                    "Avg speed: $avgSpeedStr\n" +
                    "Cur speed: $curSpeedStr\n" +
                    "Climb: 0 $distUnit\n" +
                    "Calorie: ${"%.1f".format(caloriesBurned)}\n" +
                    "Distance: ${"%.2f".format(distVal)} $distUnit"
    }

    private fun activityName(type: Int): String {
        val names = resources.getStringArray(R.array.activity_type_entries)
        return names.getOrNull(type) ?: "Unknown"
    }

    // Save to my runs data base, and then finish
    private fun saveEntryAndFinish() {
        val db = MyRunsDatabase.getInstance(applicationContext)
        val repo = ExerciseRepository(db.exerciseEntryDao())

        val inputType = intent.getIntExtra("input_type", 1)    // 1 = GPS / Automatic
        val origActivityType = intent.getIntExtra("activity_type", 0)

        val now = System.currentTimeMillis()
        val durationSec = (now - startTimeMs) / 1000.0

        val encodedRoute = try {
            if (path.isNotEmpty()) LocationCodec.encode(path) else null
        } catch (e: Exception) {
            null
        }

        // If we have an inferred type (automatic mode), use it; otherwise keep original
        val finalActivityType = inferredActivityType ?: origActivityType

        val entry = ExerciseEntryEntity(
            id = 0L,
            inputType = inputType,
            activityType = finalActivityType,
            dateTimeMillis = startTimeMs,
            durationSec = durationSec,
            distanceMeters = totalDistanceM.toDouble(),
            calorie = caloriesBurned,
            climbMeters = null,
            heartRate = null,
            comment = "",
            locationBlob = encodedRoute
        )

        lifecycleScope.launch(Dispatchers.IO) {
            repo.insert(entry)
        }

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()

        stopTrackingService()
        finish()
    }
}




