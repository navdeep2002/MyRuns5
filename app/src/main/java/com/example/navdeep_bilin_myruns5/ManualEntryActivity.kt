package com.example.navdeep_bilin_myruns5

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.navdeep_bilin_myruns5.data.MyRunsDatabase
import com.example.navdeep_bilin_myruns5.data.ExerciseEntryEntity
import com.example.navdeep_bilin_myruns5.data.ExerciseRepository
import com.example.navdeep_bilin_myruns5.util.Units
import kotlinx.coroutines.launch
import java.util.Calendar


class ManualEntryActivity : AppCompatActivity() {

    private lateinit var rowDate: View
    private lateinit var rowTime: View
    private lateinit var rowDuration: View
    private lateinit var rowDistance: View
    private lateinit var rowCalories: View
    private lateinit var rowHeartRate: View
    private lateinit var rowComment: View

    private lateinit var tvDateValue: TextView
    private lateinit var tvTimeValue: TextView
    private lateinit var tvDurationValue: TextView
    private lateinit var tvDistanceValue: TextView
    private lateinit var tvCaloriesValue: TextView
    private lateinit var tvHeartRateValue: TextView
    private lateinit var tvCommentValue: TextView

    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Date dialog state
    private var dateDialog: DatePickerDialog? = null
    private var isDateDialogShowing = false
    private var dateYear = 0
    private var dateMonth = 0
    private var dateDay = 0

    // Time dialog state
    private var timeDialog: TimePickerDialog? = null
    private var isTimeDialogShowing = false
    private var timeHour = 0
    private var timeMinute = 0

    companion object {
        private const val STATE_DATE_SHOWING = "state_date_showing"
        private const val STATE_DATE_YEAR = "state_date_year"
        private const val STATE_DATE_MONTH = "state_date_month"
        private const val STATE_DATE_DAY = "state_date_day"

        private const val STATE_TIME_SHOWING = "state_time_showing"
        private const val STATE_TIME_HOUR = "state_time_hour"
        private const val STATE_TIME_MINUTE = "state_time_minute"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_entry)
        supportActionBar?.title = getString(R.string.app_name)

        // Rows
        rowDate = findViewById(R.id.rowDate)
        rowTime = findViewById(R.id.rowTime)
        rowDuration = findViewById(R.id.rowDuration)
        rowDistance = findViewById(R.id.rowDistance)
        rowCalories = findViewById(R.id.rowCalories)
        rowHeartRate = findViewById(R.id.rowHeartRate)
        rowComment = findViewById(R.id.rowComment)

        // Values
        tvDateValue = findViewById(R.id.tvDateValue)
        tvTimeValue = findViewById(R.id.tvTimeValue)
        tvDurationValue = findViewById(R.id.tvDurationValue)
        tvDistanceValue = findViewById(R.id.tvDistanceValue)
        tvCaloriesValue = findViewById(R.id.tvCaloriesValue)
        tvHeartRateValue = findViewById(R.id.tvHeartRateValue)
        tvCommentValue = findViewById(R.id.tvCommentValue)

        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Restore dialogs after rotation
        savedInstanceState?.let { b ->
            isDateDialogShowing = b.getBoolean(STATE_DATE_SHOWING, false)
            dateYear = b.getInt(STATE_DATE_YEAR, 0)
            dateMonth = b.getInt(STATE_DATE_MONTH, 0)
            dateDay = b.getInt(STATE_DATE_DAY, 0)
            if (isDateDialogShowing) showDateDialog(dateYear, dateMonth, dateDay)

            isTimeDialogShowing = b.getBoolean(STATE_TIME_SHOWING, false)
            timeHour = b.getInt(STATE_TIME_HOUR, 0)
            timeMinute = b.getInt(STATE_TIME_MINUTE, 0)
            if (isTimeDialogShowing) showTimeDialog(timeHour, timeMinute)
        }

        // Pickers
        rowDate.setOnClickListener {
            val cal = Calendar.getInstance()
            showDateDialog(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        rowTime.setOnClickListener {
            val cal = Calendar.getInstance()
            showTimeDialog(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        // Numeric dialogs → write into the target TextView
        rowDuration.setOnClickListener {
            showDecimalDialog(target = tvDurationValue, title = "Enter the duration (minutes)", hint = "Minutes")
        }

        rowDistance.setOnClickListener {
            val isMetric = Units.system(this) == Units.System.METRIC
            val unitHint = if (isMetric) "Kilometers" else "Miles"
            showDecimalDialog(target = tvDistanceValue, title = "Enter the distance ($unitHint)", hint = unitHint)
        }

        rowCalories.setOnClickListener {
            showNumberDialog(target = tvCaloriesValue, title = "Enter the calories", hint = "kcal")
        }

        rowHeartRate.setOnClickListener {
            showNumberDialog(target = tvHeartRateValue, title = "Enter the heart rate", hint = "BPM")
        }

        rowComment.setOnClickListener { showCommentDialog(tvCommentValue) }

        // Buttons
        btnSave.setOnClickListener { persistEntry() }
        btnCancel.setOnClickListener { finish() }
    }

    // --- Dialog helpers ------------------------------------------------------

    private fun showDateDialog(year: Int, month: Int, day: Int) {
        dateDialog?.dismiss()
        dateDialog = DatePickerDialog(
            this,
            { _, y, m, d ->
                dateYear = y; dateMonth = m; dateDay = d
                tvDateValue.text = String.format("%04d-%02d-%02d", y, m + 1, d)
            },
            year, month, day
        ).apply {
            setOnShowListener { isDateDialogShowing = true }
            setOnDismissListener { isDateDialogShowing = false }
            show()
        }
    }

    private fun showTimeDialog(hour24: Int, minute: Int) {
        timeDialog?.dismiss()
        timeDialog = TimePickerDialog(
            this,
            { _, hOfDay, m ->
                timeHour = hOfDay; timeMinute = m
                // show in HH:mm
                tvTimeValue.text = String.format("%02d:%02d", hOfDay, m)
            },
            hour24, minute, false
        ).apply {
            setOnShowListener { isTimeDialogShowing = true }
            setOnDismissListener { isTimeDialogShowing = false }
            show()
        }
    }

    private fun showNumberDialog(target: TextView, title: String, hint: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setHint(hint)
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { dlg, _ ->
                target.text = input.text.toString()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    // writes back to tvDurationValue, here decimals are allowed
    private fun showDecimalDialog(target: TextView, title: String, hint: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setHint(hint)
            filters = arrayOf(InputFilter.LengthFilter(8))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { dlg, _ ->
                target.text = input.text.toString()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    private fun showCommentDialog(target: TextView) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHint("Enter your comment")
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Comment")
            .setView(input)
            .setPositiveButton("OK") { dlg, _ ->
                target.text = input.text.toString()
                dlg.dismiss()
            }
            .setNegativeButton("Cancel") { dlg, _ -> dlg.dismiss() }
            .show()
    }

    // --- State ---------------------------------------------------------------

    override fun onSaveInstanceState(outState: Bundle) {
        dateDialog?.datePicker?.let {
            dateYear = it.year; dateMonth = it.month; dateDay = it.dayOfMonth
        }
        outState.putBoolean(STATE_DATE_SHOWING, dateDialog?.isShowing == true || isDateDialogShowing)
        outState.putInt(STATE_DATE_YEAR, dateYear)
        outState.putInt(STATE_DATE_MONTH, dateMonth)
        outState.putInt(STATE_DATE_DAY, dateDay)

        timeDialog?.let { dlg ->
            if (dlg.isShowing) {
                // No need to dig the internal picker; we already track hour/minute
            }
        }
        outState.putBoolean(STATE_TIME_SHOWING, timeDialog?.isShowing == true || isTimeDialogShowing)
        outState.putInt(STATE_TIME_HOUR, timeHour)
        outState.putInt(STATE_TIME_MINUTE, timeMinute)

        super.onSaveInstanceState(outState)
    }

    // --- Persist -------------------------------------------------------------

    private fun persistEntry() {
        // Build datetime from pickers; default to "now" if none selected
        val cal = Calendar.getInstance().apply {
            if (dateYear != 0) {
                set(Calendar.YEAR, dateYear)
                set(Calendar.MONTH, dateMonth)
                set(Calendar.DAY_OF_MONTH, dateDay)
            }
            if (!(timeHour == 0 && timeMinute == 0 && tvTimeValue.text.isNullOrBlank())) {
                set(Calendar.HOUR_OF_DAY, timeHour)
                set(Calendar.MINUTE, timeMinute)
            }
        }
        val dateTimeMillis = cal.timeInMillis

        // Activity type comes from Start tab usually; read extra, default 0
        val activityTypeIndex = intent.getIntExtra("activity_type", 0)

        // Distance: entered in user's preferred unit; convert to meters for DB
        val distanceUser = tvDistanceValue.text.toString().toDoubleOrNull() ?: 0.0
        val distanceMeters = Units.preferredToMeters(this, distanceUser)

        // Duration: minutes integer → seconds
        val durationMinutes = tvDurationValue.text.toString().toDoubleOrNull() ?: 0.0
        val durationSec = durationMinutes * 60.0

        val calories = tvCaloriesValue.text.toString().toDoubleOrNull()
        val heart = tvHeartRateValue.text.toString().toDoubleOrNull()
        val comment = tvCommentValue.text?.toString()

        val db = MyRunsDatabase.getInstance(this)
        val repo = ExerciseRepository(db.exerciseEntryDao())

        val entry = ExerciseEntryEntity(
            id = 0L,
            inputType = 0,                  // Manual
            activityType = activityTypeIndex,
            dateTimeMillis = dateTimeMillis,
            durationSec = durationSec,
            distanceMeters = distanceMeters,
            calorie = calories,
            climbMeters = null,
            heartRate = heart,
            comment = comment,
            locationBlob = null
        )

        lifecycleScope.launch {
            val id = repo.insert(entry)
            Toast.makeText(this@ManualEntryActivity, "#entry $id saved", Toast.LENGTH_SHORT).show()
            finish() // back to MainActivity (tabs). History LiveData will refresh automatically.
        }
    }
}