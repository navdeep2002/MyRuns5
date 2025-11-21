package com.example.navdeep_bilin_myruns5

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : ComponentActivity() {

    // UI - user interface
    private lateinit var imgProfile: ImageView
    private lateinit var btnChangePhoto: Button
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etClass: EditText
    private lateinit var etMajor: EditText
    private lateinit var rgGender: RadioGroup
    private lateinit var rbFemale: RadioButton
    private lateinit var rbMale: RadioButton
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // Preferences
    private val prefsName = "profile_prefs"
    private val KEY_NAME = "name"
    private val KEY_EMAIL = "email"
    private val KEY_PHONE = "phone"
    private val KEY_GENDER = "gender"
    private val KEY_CLASS = "class_year"
    private val KEY_MAJOR = "major"
    private val KEY_PHOTO_URI = "photo_uri"

    // Rotation state
    private val STATE_PENDING_URI = "state_pending_uri"

    // Photo state
    private var pendingPhotoUri: Uri? = null
    private var lastSavedPhotoUri: Uri? = null

    // lecture reference for fileProvider pattern from camera lecture
    private val fileProviderAuthority by lazy { "$packageName.fileprovider" }

    // Launchers
    private val takePicture =

        // lecture reference: ActivityResultContracts.TakePicture standard usage
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingPhotoUri?.let { imgProfile.setImageURI(it) }
            } else {
                Toast.makeText(this, "Camera canceled", Toast.LENGTH_SHORT).show()
            }
        }

    // Use ACTION_PICK so the system “Select a photo / Device folders / Pictures” UI shows
    private val pickFromGallery =
        // adapted from lecture in my own implementation for a launcher with action_pick to get
        // content uri
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uri = res.data?.data
            if (uri != null) {
                pendingPhotoUri = uri
                imgProfile.setImageURI(uri)
            }
        }


    private val requestPerms =
        // follows from multiple perms lecture, similar pattern not direct copy paste
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<Button?>(R.id.btnMenu)?.setOnClickListener {
            Toast.makeText(this, "Menu clicked", Toast.LENGTH_SHORT).show()
        }

        bindViews() // lecture reference - view binding by using findViewById
        addLightValidation() // adpation for input cleanup on digits
        requestFirstRunPermissions() // adaptions for perms request

        // adapted code to restore the pending image across rotation and to load the saved profile
        if (savedInstanceState != null) {
            pendingPhotoUri = savedInstanceState.getString(STATE_PENDING_URI)?.let(Uri::parse)
            if (pendingPhotoUri != null) {
                imgProfile.setImageURI(pendingPhotoUri)
                loadProfile(loadImage = false)
            } else {
                loadProfile(loadImage = true)
            }
        } else {
            loadProfile(loadImage = true)
        }

        // lecture reference - alertDialog chooser
        btnChangePhoto.setOnClickListener { showPickPhotoDialog() }

        btnSave.setOnClickListener {
            if (validateAndSave()) {
                // adaption from lecture in own implementation to combine the validation and
                // sharedPrefernces save
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                finish() // go back to Settings tab
            }
        }
        btnCancel.setOnClickListener {
            // revert everything including image, then go back, adapted from lecture material
            pendingPhotoUri = null
            loadProfile(loadImage = true)
            Toast.makeText(this, "Changes discarded", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ---------- helpers ----------

    private fun bindViews() {
        imgProfile = findViewById(R.id.imgProfile)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etClass = findViewById(R.id.etClass)
        etMajor = findViewById(R.id.etMajor)
        rgGender = findViewById(R.id.rgGender)
        rbFemale = findViewById(R.id.rbFemale)
        rbMale = findViewById(R.id.rbMale)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
    }

    private fun requestFirstRunPermissions() {

        // adpated to fit my implementation, split media permission and legacy storage read
        val wants = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) {
            wants += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            wants += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val need = wants.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) requestPerms.launch(need.toTypedArray())
    }

    // 1) Dialog with two options
    private fun showPickPhotoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Pick Profile Picture")
            .setItems(arrayOf("Open Camera", "Select from Gallery")) { _, which ->
                when (which) {
                    0 -> ensureCameraReadyThenLaunch()  // lecture reference, lecture 3
                    1 -> launchGalleryPicker()          // adapted system gallery picker
                }
            }.show()
    }

    // 3) Launch system gallery picker that shows “Select a photo”, Device folders, Pictures, etc.
    private fun launchGalleryPicker() {
        // READ permission requested earlier when needed
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickFromGallery.launch(intent)
    }

    // Camera path (Lecture 3 pattern)
    private fun fileForTempPhoto(): File {
        val dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "profile")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "profile_temp.jpg")
    }

    private fun ensureCameraReadyThenLaunch() {

        // lecture reference - check the permissions before launching
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) need += Manifest.permission.CAMERA

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (need.isNotEmpty()) requestPerms.launch(need.toTypedArray())

        // lecture refernce - create a temp file and use the FIleProvider Uri to take picture
        val photoFile = fileForTempPhoto()
        val photoUri = FileProvider.getUriForFile(this, fileProviderAuthority, photoFile)
        pendingPhotoUri = photoUri
        takePicture.launch(photoUri)
    }

    private fun loadProfile(loadImage: Boolean) {

        // lecture reference - shared preferences load pattern
        val sp = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        etName.setText(sp.getString(KEY_NAME, "") ?: "")
        etEmail.setText(sp.getString(KEY_EMAIL, "") ?: "")
        etPhone.setText(sp.getString(KEY_PHONE, "") ?: "")
        etClass.setText(sp.getInt(KEY_CLASS, 0).takeIf { it > 0 }?.toString() ?: "")
        etMajor.setText(sp.getString(KEY_MAJOR, "") ?: "")

        when (sp.getInt(KEY_GENDER, -1)) {
            0 -> rgGender.check(R.id.rbFemale)
            1 -> rgGender.check(R.id.rbMale)
            else -> rgGender.clearCheck()
        }

        if (loadImage) {
            lastSavedPhotoUri = sp.getString(KEY_PHOTO_URI, null)?.let(Uri::parse)
            if (lastSavedPhotoUri != null) imgProfile.setImageURI(lastSavedPhotoUri)
            else imgProfile.setImageResource(R.mipmap.ic_launcher)
        }
    }

    private fun validateAndSave(): Boolean {

        // adapted from lecture to my implementation: provides simple validation and numeric checks
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val major = etMajor.text.toString().trim()
        val classStr = etClass.text.toString().trim()

        if (phone.isNotEmpty() && !phone.all { it.isDigit() }) {
            etPhone.error = "Numbers only"
            return false
        }

        val classYear = if (classStr.isEmpty()) 0 else try {
            classStr.toInt()
        } catch (_: NumberFormatException) {
            etClass.error = "Enter a number"
            return false
        }

        val gender = when (rgGender.checkedRadioButtonId) {
            R.id.rbFemale -> 0
            R.id.rbMale -> 1
            else -> -1
        }

        // heavy AI use here:
        // Copy pending photo into public Pictures/MyRuns2 then persist its Uri
        val finalPhotoUri: Uri? = pendingPhotoUri?.let { src ->
            persistToGallery(src) ?: lastSavedPhotoUri
        } ?: lastSavedPhotoUri

        // lecture reference to save to the SharedPreferences
        val editor = getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_NAME, name)
        editor.putString(KEY_EMAIL, email)
        editor.putString(KEY_PHONE, phone)
        editor.putInt(KEY_CLASS, classYear)
        editor.putString(KEY_MAJOR, major)
        editor.putInt(KEY_GENDER, gender)
        if (finalPhotoUri != null) editor.putString(KEY_PHOTO_URI, finalPhotoUri.toString())
        editor.apply()

        lastSavedPhotoUri = finalPhotoUri
        pendingPhotoUri = null
        return true
    }

    // Save into MediaStore so it appears in Gallery: Pictures/MyRuns2
    private fun persistToGallery(sourceUri: Uri): Uri? {

        // AI - heavy here
        // used chatgpt to help with mediaStore insert for display name and relative_path
        return try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "profile_$ts.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyRuns2")
                }
            }

            val external = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val destUri = contentResolver.insert(external, values) ?: return null

            contentResolver.openOutputStream(destUri)?.use { out ->
                contentResolver.openInputStream(sourceUri)?.use { `in`: InputStream ->
                    `in`.copyTo(out)
                }
            }
            destUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addLightValidation() {

        // adapted from lectures, implemented TextWatcher that keeps digits only in phone and class fields
        val digitsOnlyWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val clean = s.filter { it.isDigit() }.toString()
                if (clean != s.toString()) {
                    val pos = clean.length
                    s.replace(0, s.length, clean)
                    (currentFocus as? EditText)?.setSelection(pos.coerceAtMost(clean.length))
                }
            }
        }
        etPhone.addTextChangedListener(digitsOnlyWatcher)
        etClass.addTextChangedListener(digitsOnlyWatcher)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // adapted from lectures, keeps the pending image Uri across rotations
        outState.putString(STATE_PENDING_URI, pendingPhotoUri?.toString())
    }
}