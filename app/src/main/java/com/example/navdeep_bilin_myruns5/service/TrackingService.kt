package com.example.navdeep_bilin_myruns5.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.navdeep_bilin_myruns5.MapActivity
import com.example.navdeep_bilin_myruns5.R
import com.example.navdeep_bilin_myruns5.WekaClassifier
import com.example.navdeep_bilin_myruns5.util.FFT
import kotlin.math.sqrt

// Foreground service for both GPS tracking + accelerometer classification
class TrackingService : Service(), LocationListener, SensorEventListener {

    companion object {
        const val ACTION_START = "mr4.START"
        const val ACTION_STOP  = "mr4.STOP"
        const val ACTION_LOC_BROADCAST = "mr4.LOCATION"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_TIME = "time"
        const val EXTRA_SPEED = "speed"

        // NEW: activity type sent to MapActivity
        const val EXTRA_ACTIVITY_TYPE = "activity_type_inferred"

        private const val CHANNEL_ID = "mr4_tracking"
        private const val NOTIFY_ID = 1001

        // Activity recognition constants (Weka label indices)
        private const val BLOCK_SIZE = 64
        private const val LABEL_STILL   = 0
        private const val LABEL_WALKING = 1
        private const val LABEL_RUNNING = 2
        private const val LABEL_OTHER   = 3

        //  Emulator Test Switch

        // true  -> fake walking/running magnitudes (emulator)
        // false -> real accelerometer (real phone / submission)
        private const val TEST_MODE = false
    }

    // GPS
    private lateinit var lm: LocationManager

    // ---- ACCELEROMETER / WEKA PIPELINE ----
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val accBlock = DoubleArray(BLOCK_SIZE)
    private val re = DoubleArray(BLOCK_SIZE)
    private val im = DoubleArray(BLOCK_SIZE)
    private val fft = FFT(BLOCK_SIZE)

    private var accIndex = 0

    // counts of Weka labels seen so far (0..3)
    private val labelCounts = IntArray(4)

    // activityType index for ExerciseEntry / UI (Running=0, Walking=1, Standing=2, etc.)
    private var currentActivityTypeForEntry: Int = 0


    override fun onCreate() {
        super.onCreate()

        // GPS
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Accelerometer setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            // Use GAME rate to avoid HIGH_SAMPLING_RATE_SENSORS permission requirement
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        createChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    NOTIFY_ID,
                    buildNotification("Service has started (tap to return)")
                )

                try {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000L,
                        0f,
                        this
                    )
                } catch (_: SecurityException) {}
            }

            ACTION_STOP -> {
                try { lm.removeUpdates(this) } catch (_: Exception) {}
                sensorManager.unregisterListener(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }


    override fun onDestroy() {
        try { lm.removeUpdates(this) } catch (_: Exception) {}
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


    // -------------------- GPS --------------------
    override fun onLocationChanged(loc: Location) {

        // broadcast location + current inferred activity type
        sendBroadcast(
            Intent(ACTION_LOC_BROADCAST)
                .setPackage(packageName)
                .putExtra(EXTRA_LAT, loc.latitude)
                .putExtra(EXTRA_LNG, loc.longitude)
                .putExtra(EXTRA_TIME, loc.time)
                .putExtra(EXTRA_SPEED, loc.speed)
                .putExtra(EXTRA_ACTIVITY_TYPE, currentActivityTypeForEntry)
        )
    }


    // Accelrometer
    override fun onSensorChanged(event: SensorEvent?) {

        // Decide where x,y,z come from: real sensor vs fake emulator values
        val x: Double
        val y: Double
        val z: Double

        if (TEST_MODE) {
            // ----- EMULATOR TEST MODE -----
            // Alternate every ~4 seconds between "walking" and "running" patterns
            val phase = (System.currentTimeMillis() / 4000L) % 2L
            val fake = if (phase == 0L) {
                // Walking-ish magnitudes
                doubleArrayOf(2.0, 3.0, 1.5)
            } else {
                // Running magnitudes (bigger / more bouncy)
                doubleArrayOf(5.0, 6.0, 4.0)
            }
            x = fake[0]
            y = fake[1]
            z = fake[2]
        } else {
            // ----- REAL SENSOR MODE -----
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            x = event.values[0].toDouble()
            y = event.values[1].toDouble()
            z = event.values[2].toDouble()
        }

        val magnitude = sqrt(x * x + y * y + z * z)

        accBlock[accIndex] = magnitude
        accIndex++

        if (accIndex >= BLOCK_SIZE) {

            for (i in 0 until BLOCK_SIZE) {
                re[i] = accBlock[i]
                im[i] = 0.0
            }

            fft.fft(re, im)

            val features = arrayOfNulls<Any>(BLOCK_SIZE + 1)
            var maxMag = Double.NEGATIVE_INFINITY

            for (i in 0 until BLOCK_SIZE) {
                val mag = sqrt(re[i] * re[i] + im[i] * im[i])
                features[i] = mag
                if (mag > maxMag) maxMag = mag
            }

            features[BLOCK_SIZE] = maxMag

            try {
                val wekaLabel = WekaClassifier.classify(features).toInt()
                handleActivityResult(wekaLabel)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            accIndex = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    // Update counts + map Weka label -> app activityType index
    private fun handleActivityResult(wekaLabel: Int) {
        if (wekaLabel in 0..3) {
            labelCounts[wekaLabel]++
        }

        // majority label so far
        var bestLabel = 0
        var bestCount = labelCounts[0]
        for (i in 1 until labelCounts.size) {
            if (labelCounts[i] > bestCount) {
                bestCount = labelCounts[i]
                bestLabel = i
            }
        }

        // Map Weka label -> app index (Running=0, Walking=1, Standing=2)
        val mappedType = when (bestLabel) {
            LABEL_STILL   -> 2  // Standing
            LABEL_WALKING -> 1  // Walking
            LABEL_RUNNING -> 0  // Running
            else          -> 0  // default to Running
        }

        currentActivityTypeForEntry = mappedType

        // For debugging in Logcat
        val msg = when (mappedType) {
            0 -> "Running"
            1 -> "Walking"
            2 -> "Standing"
            else -> "Other"
        }
        println("Activity = $msg")
    }


    //  Notification
    private fun buildNotification(text: String): Notification {

        val tapIntent = Intent(this, MapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val tap = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "MR5 Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
    }
}



