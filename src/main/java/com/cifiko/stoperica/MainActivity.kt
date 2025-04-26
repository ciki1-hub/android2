package com.cifiko.stoperica

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.*
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.cifiko.stoperica.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var binding: ActivityMainBinding
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private lateinit var sharedPreferences: SharedPreferences
    private val isUploading = AtomicBoolean(false)
    private var lastStartLapClickTime = 0L
    private var lastSectorClickTime = 0L
    // Stopwatch State
    private var isRunning = false
    private var isPaused = false
    private var startTime = 0L
    private var pauseTime = 0L
    private var lapStartTime = 0L
    private var sectorStartTime = 0L
    private var bestLapTime = Long.MAX_VALUE
    private var worstLapTime = Long.MIN_VALUE
    private var lapCount = 0
    private var sectorCount = 0
    private val lapTimes = ArrayList<String>()
    private val sectorTimes = mutableListOf<MutableList<String>>()
    private val sessions = mutableListOf<Session>()
    private var autoStartLiveSession = false

    // Location Services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentCity: String = "Unknown Location"
    private var isGPSEnabled = false

    // System Services
    private lateinit var vibrator: Vibrator
    private lateinit var wakeLock: PowerManager.WakeLock

    // Firebase components
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var currentSessionId: String? = null
    private var isHost = false
    private var liveSessionListener: ValueEventListener? = null
    private var liveSessionBroadcaster: Runnable? = null

    // Broadcast Receivers
    private val startLapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            binding.startStopButton.performClick()
        }
    }

    private val lapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            binding.lapButton.performClick()
        }
    }

    private lateinit var networkChangeReceiver: BroadcastReceiver

    // UI State
    private var isBackgroundWhite = true

    // Permission Launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            createAndShowNotification()
        }
    }

    // Auth State Listener
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser == null) {
            initializeFirebaseAuth()
        }
    }

    companion object {
        // Request Codes
        private const val REQUEST_CODE_ANALYTICS = 100
        private const val REQUEST_CODE_HISTORY = 101
        private const val REQUEST_CODE_COMPARE = 102
        private const val REQUEST_CODE_GPS = 103
        private const val REQUEST_CODE_MAP = 104
        private const val REQUEST_CODE_LOCATION_PERMISSION = 105
        private const val REQUEST_CODE_LIVE_VIEWER = 106

        // Constants
        private const val FASTEST_INTERVAL: Long = 5000
        private const val INTERVAL: Long = 10000
        private const val WEB_UPLOAD_URL = "https://moto.webhop.me/upload"
        private const val NOTIFICATION_CHANNEL_ID = "stopwatch_channel"
        private const val NOTIFICATION_ID = 1
        private const val SESSION_TIMEOUT_MS = 30000L // 30 seconds

        // Debounce constants
        private const val START_LAP_DEBOUNCE = 500L // 0.5 seconds
        private const val SECTOR_DEBOUNCE = 300L // 0.3 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            initializeAppComponents()
            setupUI()
            restoreInstanceState(savedInstanceState)
            setupFirebase()
            setupLocationServices()
            setupBroadcastReceivers()
            setupNotificationChannel()
        } catch (e: Exception) {
            finish()
            startActivity(intent)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                binding.lapButton.performClick()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                binding.sectorButton.performClick()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveStopwatchState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    override fun onDestroy() {
        if (!isFinishing) {
            showExitNotification()
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_LIVE_VIEWER -> {
                if (resultCode == LiveViewerActivity.RESULT_SESSION_LEFT) {
                    currentSessionId = null
                    isHost = false
                    supportFragmentManager.fragments.forEach {
                        if (it is DialogFragment) {
                            it.dismiss()
                        }
                    }
                }
            }
            else -> {
                handleActivityResult(requestCode, resultCode, data)
            }
        }
    }

    private fun initializeAppComponents() {
        FirebaseApp.initializeApp(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        autoStartLiveSession = sharedPreferences.getBoolean("auto_start_live_session", false)
        handler = Handler(Looper.getMainLooper())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Stoperica::WakeLock"
        )
    }

    private fun setupUI() {
        binding.currentLapTextView.text = "Current Lap: 00:00:00"
        binding.bestLapTextView.text = "Best Lap: 00:00:00"

        binding.startStopButton.setOnClickListener {
            vibrate()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastStartLapClickTime > START_LAP_DEBOUNCE) {
                lastStartLapClickTime = currentTime
                if (isRunning) {
                    if (isPaused) {
                        resumeStopwatch()
                    } else {
                        pauseStopwatch()
                    }
                } else {
                    startStopwatch()
                }
            }
        }

        binding.resetButton.setOnClickListener {
            vibrate()
            if (isRunning || isPaused) {
                showResetOptions()
            } else {
                resetStopwatchCompletely()
            }
        }

        binding.lapButton.setOnClickListener {
            vibrate()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastStartLapClickTime > START_LAP_DEBOUNCE) {
                lastStartLapClickTime = currentTime
                addLap()
            }
        }

        binding.sectorButton.setOnClickListener {
            vibrate()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSectorClickTime > SECTOR_DEBOUNCE) {
                lastSectorClickTime = currentTime
                addSector()
            }
        }

        binding.fabNavigation.setOnClickListener { view ->
            showNavigationPopupMenu(view)
        }
    }

    private fun showResetOptions() {
        AlertDialog.Builder(this)
            .setTitle("Reset Stopwatch")
            .setMessage("Save session before resetting?")
            .setPositiveButton("Save & Reset") { _, _ ->
                saveSession()
                resetStopwatchCompletely()
                Toast.makeText(this, "Session saved and reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Just Reset") { _, _ ->
                resetStopwatchCompletely()
                Toast.makeText(this, "Reset without saving", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun resetStopwatchCompletely() {
        if (isRunning || isPaused) {
            stopStopwatch()
        }

        startTime = 0L
        pauseTime = 0L
        lapStartTime = 0L
        sectorStartTime = 0L

        bestLapTime = Long.MAX_VALUE
        worstLapTime = Long.MIN_VALUE
        lapCount = 0
        sectorCount = 0

        lapTimes.clear()
        sectorTimes.clear()
        currentLapSectors.clear()

        binding.totalTimeTextView.text = "00:00:00"
        binding.currentLapTextView.text = "Current Lap: 00:00:00"
        binding.bestLapTextView.text = "Best Lap: 00:00:00"
        updateDisplay()
    }

    private fun setupFirebase() {
        auth = Firebase.auth
        database = Firebase.database
        initializeFirebaseAuth()
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocation(location)
                }
            }
        }
    }

    private fun setupBroadcastReceivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                startLapReceiver,
                IntentFilter("com.cifiko.stoperica.START_LAP"),
                RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                lapReceiver,
                IntentFilter("com.cifiko.stoperica.LAP"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(startLapReceiver, IntentFilter("com.cifiko.stoperica.START_LAP"))
            registerReceiver(lapReceiver, IntentFilter("com.cifiko.stoperica.LAP"))
        }

        networkChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isInternetAvailable()) {
                    retryUploadSessions()
                }
            }
        }
        registerReceiver(networkChangeReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            isRunning = savedInstanceState.getBoolean("isRunning", false)
            isPaused = savedInstanceState.getBoolean("isPaused", false)
            startTime = savedInstanceState.getLong("startTime", 0L)
            pauseTime = savedInstanceState.getLong("pauseTime", 0L)
            lapStartTime = savedInstanceState.getLong("lapStartTime", 0L)
            sectorStartTime = savedInstanceState.getLong("sectorStartTime", 0L)
            bestLapTime = savedInstanceState.getLong("bestLapTime", Long.MAX_VALUE)
            worstLapTime = savedInstanceState.getLong("worstLapTime", Long.MIN_VALUE)
            lapCount = savedInstanceState.getInt("lapCount", 0)
            sectorCount = savedInstanceState.getInt("sectorCount", 0)
            lapTimes.addAll(savedInstanceState.getStringArrayList("lapTimes") ?: ArrayList())
            sectorTimes.addAll(savedInstanceState.getSerializable("sectorTimes") as List<MutableList<String>>)
        }

        runnable = Runnable {
            updateTime()
            handler.postDelayed(runnable, 10)
        }

        sessions.addAll(loadSessions())
    }

    private fun startStopwatch() {
        if (!::handler.isInitialized) {
            return
        }

        if (!isRunning) {
            isRunning = true
            isPaused = false
            startTime = System.currentTimeMillis()
            lapStartTime = startTime
            sectorStartTime = startTime
            handler.post(runnable)
            binding.startStopButton.text = getString(R.string.pause)

            if (!wakeLock.isHeld) {
                wakeLock.acquire()
            }

            if (autoStartLiveSession && currentSessionId == null) {
                createLiveSession()
            }

            fetchCurrentLocation { location ->
                location?.let { updateLocation(it) }
            }
        }
    }

    private fun pauseStopwatch() {
        if (isRunning && !isPaused) {
            isPaused = true
            pauseTime = System.currentTimeMillis()
            handler.removeCallbacks(runnable)
            binding.startStopButton.text = getString(R.string.resume)
        }
    }

    private fun resumeStopwatch() {
        if (isRunning && isPaused) {
            isPaused = false
            val pauseDuration = System.currentTimeMillis() - pauseTime
            startTime += pauseDuration
            lapStartTime += pauseDuration
            sectorStartTime += pauseDuration
            handler.post(runnable)
            binding.startStopButton.text = getString(R.string.pause)
        }
    }

    private fun stopStopwatch() {
        if (isRunning) {
            isRunning = false
            isPaused = false
            handler.removeCallbacks(runnable)
            binding.startStopButton.text = getString(R.string.start)

            if (currentSessionId != null && isHost) {
                leaveLiveSession()
            }

            startTime = 0L
            pauseTime = 0L
            lapStartTime = 0L
            sectorStartTime = 0L
            binding.totalTimeTextView.text = "00:00:00"
            binding.currentLapTextView.text = "Current Lap: 00:00:00"
            binding.bestLapTextView.text = "Best Lap: 00:00:00"
            lapTimes.clear()
            sectorTimes.clear()
            currentLapSectors.clear()
            bestLapTime = Long.MAX_VALUE
            worstLapTime = Long.MIN_VALUE
            lapCount = 0
            sectorCount = 0
            updateDisplay()

            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun resetStopwatch() {
        if (isRunning) {
            if (sectorStartTime != lapStartTime) {
                addSector()
            }
            saveSession()
        }
        stopStopwatch()
    }

    private fun updateTime() {
        val currentTime = System.currentTimeMillis() - startTime
        val lapTime = System.currentTimeMillis() - lapStartTime
        binding.totalTimeTextView.text = formatTime(currentTime)
        binding.currentLapTextView.text = "Current Lap: ${formatTime(lapTime)}"
    }

    private var currentLapSectors = mutableListOf<String>()

    private fun addLap() {
        if (isRunning && !isPaused) {
            val lapTime = System.currentTimeMillis() - lapStartTime

            if (lapTime < bestLapTime) {
                bestLapTime = lapTime
                binding.bestLapTextView.text = "Best Lap: ${formatTime(bestLapTime)}"
            }
            if (lapTime > worstLapTime) {
                worstLapTime = lapTime
            }

            lapCount++
            val lapString = getString(R.string.lap_time_format, lapCount, formatTime(lapTime))
            lapTimes.add(lapString)

            if (currentLapSectors.isNotEmpty()) {
                sectorTimes.add(currentLapSectors)
                currentLapSectors = mutableListOf()
            } else {
                sectorTimes.add(mutableListOf())
            }

            lapStartTime = System.currentTimeMillis()
            sectorStartTime = lapStartTime
            sectorCount = 0

            updateDisplay()
        }
    }

    private fun addSector() {
        if (isRunning && !isPaused) {
            val sectorTime = System.currentTimeMillis() - sectorStartTime
            sectorCount++
            val sectorString = getString(R.string.sector_time_format, sectorCount, formatTime(sectorTime))

            currentLapSectors.add(sectorString)
            sectorStartTime = System.currentTimeMillis()

            updateDisplay()
        }
    }

    private fun updateDisplay() {
        binding.timesList.removeAllViews()

        if (currentLapSectors.isNotEmpty()) {
            val header = TextView(this).apply {
                text = if (lapTimes.isEmpty()) "Current Sectors:" else "Lap ${lapCount + 1} Sectors:"
                textSize = 16f
            }
            binding.timesList.addView(header)

            for (sector in currentLapSectors.reversed()) {
                val sectorView = TextView(this).apply {
                    text = "   $sector"
                    textSize = 14f
                }
                binding.timesList.addView(sectorView)
            }

            if (lapTimes.isNotEmpty()) {
                binding.timesList.addView(TextView(this).apply {
                    text = "-----------------------"
                })
            }
        }

        for (i in lapTimes.indices.reversed()) {
            val lapView = TextView(this).apply {
                text = lapTimes[i]
                textSize = 16f
            }
            binding.timesList.addView(lapView)

            if (i < sectorTimes.size && sectorTimes[i].isNotEmpty()) {
                for (sector in sectorTimes[i].reversed()) {
                    val sectorView = TextView(this).apply {
                        text = "   $sector"
                        textSize = 14f
                    }
                    binding.timesList.addView(sectorView)
                }
            }

            if (i > 0) {
                binding.timesList.addView(TextView(this).apply {
                    text = "-----------------------"
                })
            }
        }
    }

    private fun updateLiveSession() {
        val allSectors = mutableListOf<MutableList<String>>().apply {
            addAll(sectorTimes)
            if (currentLapSectors.isNotEmpty() || isRunning) {
                add(currentLapSectors.toMutableList())
            }
        }

        val firebaseSectors = allSectors.map { it.toList() }

        database.reference.child("sessions").child(currentSessionId!!).updateChildren(
            mapOf(
                "laps" to lapTimes,
                "sectors" to firebaseSectors,
                "bestLap" to if (bestLapTime != Long.MAX_VALUE) "Best: ${formatTime(bestLapTime)}" else "N/A",
                "worstLap" to if (worstLapTime != Long.MIN_VALUE) "Worst: ${formatTime(worstLapTime)}" else "N/A",
                "timestamp" to ServerValue.TIMESTAMP
            )
        )
    }

    private fun saveStopwatchState(outState: Bundle) {
        outState.putBoolean("isRunning", isRunning)
        outState.putBoolean("isPaused", isPaused)
        outState.putLong("startTime", startTime)
        outState.putLong("pauseTime", pauseTime)
        outState.putLong("lapStartTime", lapStartTime)
        outState.putLong("sectorStartTime", sectorStartTime)
        outState.putLong("bestLapTime", bestLapTime)
        outState.putLong("worstLapTime", worstLapTime)
        outState.putInt("lapCount", lapCount)
        outState.putInt("sectorCount", sectorCount)
        outState.putStringArrayList("lapTimes", lapTimes)
        outState.putSerializable("sectorTimes", ArrayList(sectorTimes))
    }

    private fun saveSession() {
        if (lapTimes.isEmpty() && sectorTimes.isEmpty()) {
            return
        }

        val existingSession = sessions.find {
            it.laps == lapTimes && it.sectors == sectorTimes.map { it.toList() }
        }
        if (existingSession != null) {
            return
        }

        if (isRunning && !isPaused && sectorStartTime != lapStartTime) {
            addSector()
        }

        val validLapTimes = lapTimes.filter { parseTime(it) != Long.MAX_VALUE }

        val fastestLap = if (validLapTimes.isNotEmpty()) {
            val fastestLapTime = validLapTimes.minByOrNull { parseTime(it) } ?: "N/A"
            val lapNumber = lapTimes.indexOf(fastestLapTime) + 1
            "$lapNumber: ${fastestLapTime.substringAfterLast(": ")}"
        } else {
            "N/A"
        }

        val slowestLap = if (validLapTimes.isNotEmpty()) {
            val slowestLapTime = validLapTimes.maxByOrNull { parseTime(it) } ?: "N/A"
            val lapNumber = lapTimes.indexOf(slowestLapTime) + 1
            "$lapNumber: ${slowestLapTime.substringAfterLast(": ")}"
        } else {
            "N/A"
        }

        val averageLap = if (validLapTimes.isNotEmpty()) {
            val averageLapTime = validLapTimes.map { parseTime(it) }.average().toLong()
            formatTime(averageLapTime)
        } else {
            "N/A"
        }

        val consistency = calculateConsistency(validLapTimes)

        val username = sharedPreferences.getString("username", "Unknown User") ?: "Unknown User"
        val speeds = calculateSpeedMetrics()

        val session = Session(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.session_name_format, sessions.size + 1),
            username = username,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            startTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(startTime)),
            fastestLap = fastestLap,
            slowestLap = slowestLap,
            averageLap = averageLap,
            consistency = consistency,
            totalTime = binding.totalTimeTextView.text.toString(),
            location = currentCity,
            dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            laps = lapTimes.toList(),
            sectors = sectorTimes.map { it.toList() },
            topSpeed = speeds.first,
            averageSpeed = speeds.second
        )

        sessions.add(session)
        saveSessions(sessions)
        uploadSession(session)
    }

    private fun loadSessions(): List<Session> {
        val sharedPreferences = getSharedPreferences("StopericaSessions", MODE_PRIVATE)
        val json = sharedPreferences.getString("sessions", null)
        return if (json != null) {
            Gson().fromJson(json, object : TypeToken<List<Session>>() {}.type)
        } else {
            emptyList()
        }
    }

    private fun saveSessions(sessions: List<Session>) {
        val sharedPreferences = getSharedPreferences("StopericaSessions", MODE_PRIVATE)
        val existingJson = sharedPreferences.getString("sessions", null)
        val existingSessions = if (existingJson != null) {
            Gson().fromJson(existingJson, object : TypeToken<List<Session>>() {}.type)
        } else {
            emptyList<Session>()
        }

        if (existingSessions != sessions) {
            sharedPreferences.edit().apply {
                val json = Gson().toJson(sessions)
                putString("sessions", json)
                apply()
            }
        }
    }

    private fun initializeFirebaseAuth() {
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                showAuthErrorToast(task.exception)
            }
        }
    }

    private fun showLiveSessionDialog() {
        val options = if (currentSessionId == null) {
            arrayOf("Join Session", "Browse Public Sessions")
        } else {
            arrayOf("Leave Session")
        }

        AlertDialog.Builder(this)
            .setTitle("Live Sessions")
            .setItems(options) { _, which ->
                when {
                    currentSessionId == null && which == 0 -> joinLiveSessionWithID()
                    currentSessionId == null && which == 1 -> browsePublicSessions()
                    currentSessionId != null && which == 0 -> leaveLiveSession()
                }
            }
            .setOnDismissListener {
                if (currentSessionId != null) {
                    showLiveSessionDialog()
                }
            }
            .show()
    }

    private fun browsePublicSessions() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Loading available sessions...")
            setCancelable(false)
            show()
        }

        database.reference.child("sessions")
            .orderByChild("isActive").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressDialog.dismiss()
                    val sessions = mutableListOf<LiveSession>()

                    snapshot.children.forEach { child ->
                        try {
                            val session = LiveSession.fromSnapshot(child)
                            if (session.isActive &&
                                System.currentTimeMillis() - (session.timestamp) < SESSION_TIMEOUT_MS) {
                                sessions.add(session)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error parsing session", e)
                        }
                    }

                    if (sessions.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No active sessions found", Toast.LENGTH_SHORT).show()
                        return
                    }

                    sessions.sortByDescending { it.createdAt }
                    showSessionListDialog(sessions)
                }

                override fun onCancelled(error: DatabaseError) {
                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Failed to load sessions: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showSessionListDialog(sessions: List<LiveSession>) {
        val sessionNames = sessions.map {
            "${it.sessionName} (${it.hostName}) - ${formatSessionTime(it.createdAt ?: 0)}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Available Sessions")
            .setItems(sessionNames) { _, which ->
                val selectedSession = sessions[which]
                confirmJoinSession(selectedSession)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmJoinSession(session: LiveSession) {
        AlertDialog.Builder(this)
            .setTitle("Join Session?")
            .setMessage("Join ${session.sessionName} hosted by ${session.hostName}?")
            .setPositiveButton("Join") { _, _ ->
                currentSessionId = session.sessionId
                isHost = false
                joinLiveSession()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinLiveSessionWithID() {
        val input = EditText(this).apply {
            hint = "Enter session ID"
        }

        AlertDialog.Builder(this)
            .setTitle("Join Live Session")
            .setMessage("Enter session ID:")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val sessionId = input.text.toString().trim()
                if (sessionId.isNotEmpty()) {
                    currentSessionId = sessionId
                    isHost = false
                    joinLiveSession()
                } else {
                    Toast.makeText(this, "Please enter a session ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinLiveSession() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please wait while we connect to server...", Toast.LENGTH_SHORT).show()
            initializeFirebaseAuth()
            return
        }

        val username = sharedPreferences.getString("username", "Unknown User") ?: "Unknown User"
        val userId = auth.currentUser?.uid ?: return

        val participantUpdate = mapOf("participants/$userId" to username)

        database.reference.child("sessions").child(currentSessionId!!)
            .updateChildren(participantUpdate)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    setupSessionDisconnectHandler()
                    openLiveViewerActivity()
                    startWatchingSession()
                    Toast.makeText(this@MainActivity, "Joined session", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to join session: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun createLiveSession() {
        if (auth.currentUser == null) {
            Toast.makeText(
                this,
                "Please wait while we connect to the server...",
                Toast.LENGTH_SHORT
            ).show()
            initializeFirebaseAuth()
            return
        }

        currentSessionId = UUID.randomUUID().toString()
        isHost = true

        val username = sharedPreferences.getString("username", "Unknown User") ?: "Unknown User"
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        fetchCurrentLocation { location ->
            val session = LiveSession(
                sessionId = currentSessionId!!,
                hostId = userId,
                hostName = username,
                sessionName = "$username's Session",
                participants = mapOf(userId to username),
                location = currentCity,
                timestamp = System.currentTimeMillis(),
                isActive = true
            )

            database.reference.child("sessions").child(currentSessionId!!)
                .setValue(session.toMap())
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        setupSessionDisconnectHandler()
                        startBroadcastingSession()
                        Toast.makeText(this, "Live session created", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to create session: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        currentSessionId = null
                        isHost = false
                    }
                }
        }
    }

    private fun setupSessionDisconnectHandler() {
        currentSessionId?.let { id ->
            val updates = mapOf(
                "isActive" to false,
                "timestamp" to ServerValue.TIMESTAMP
            )
            database.reference.child("sessions").child(id).onDisconnect().updateChildren(updates)

            if (isHost) {
                database.reference.child("sessions").child(id).onDisconnect().removeValue()
            } else {
                val userId = auth.currentUser?.uid ?: return
                database.reference.child("sessions").child(id).child("participants").child(userId).onDisconnect().removeValue()
            }
        }
    }

    private fun leaveLiveSession() {
        if (currentSessionId != null) {
            val updates = mapOf(
                "isActive" to false,
                "timestamp" to ServerValue.TIMESTAMP
            )

            database.reference.child("sessions").child(currentSessionId!!).updateChildren(updates)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        if (isHost) {
                            database.reference.child("sessions").child(currentSessionId!!).removeValue()
                                .addOnCompleteListener {
                                    Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show()
                                    cleanupAfterSession()
                                }
                        } else {
                            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                            database.reference.child("sessions").child(currentSessionId!!)
                                .child("participants").child(userId).removeValue()
                                .addOnCompleteListener {
                                    Toast.makeText(this, "Left session", Toast.LENGTH_SHORT).show()
                                    cleanupAfterSession()
                                }
                        }
                    } else {
                        cleanupAfterSession()
                    }
                }
        } else {
            Toast.makeText(this, "Not in a live session", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupAfterSession() {
        stopWatchingSession()
        currentSessionId = null
        isHost = false
    }

    private fun openLiveViewerActivity() {
        if (currentSessionId != null) {
            val intent = Intent(this, LiveViewerActivity::class.java).apply {
                putExtra("SESSION_ID", currentSessionId)
                putExtra("USER_ID", auth.currentUser?.uid)
                putExtra("IS_HOST", isHost)
            }
            startActivityForResult(intent, REQUEST_CODE_LIVE_VIEWER)
        } else {
            Toast.makeText(this, "No active session", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBroadcastingSession() {
        liveSessionBroadcaster = object : Runnable {
            override fun run() {
                if (isRunning && isHost && currentSessionId != null) {
                    val updates = mapOf(
                        "laps" to lapTimes,
                        "sectors" to sectorTimes + listOf(currentLapSectors),
                        "bestLap" to if (bestLapTime != Long.MAX_VALUE) "Best: ${formatTime(bestLapTime)}" else "N/A",
                        "worstLap" to if (worstLapTime != Long.MIN_VALUE) "Worst: ${formatTime(worstLapTime)}" else "N/A",
                        "totalTime" to binding.totalTimeTextView.text.toString(),
                        "timestamp" to ServerValue.TIMESTAMP,
                        "isActive" to true
                    )

                    database.reference.child("sessions").child(currentSessionId!!)
                        .updateChildren(updates)
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(liveSessionBroadcaster!!)
    }

    private fun startWatchingSession() {
        stopWatchingSession()

        currentSessionId?.let { id ->
            liveSessionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@MainActivity, "Live session error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
            database.reference.child("sessions").child(id).addValueEventListener(liveSessionListener!!)
        }
    }

    private fun stopWatchingSession() {
        liveSessionListener?.let { listener ->
            currentSessionId?.let { id ->
                try {
                    database.reference.child("sessions").child(id).removeEventListener(listener)
                } catch (e: Exception) {
                }
            }
            liveSessionListener = null
        }
        liveSessionBroadcaster?.let { handler.removeCallbacks(it) }
        liveSessionBroadcaster = null
    }

    private fun showAuthErrorToast(exception: Exception?) {
        val errorMessage = when (exception) {
            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Anonymous sign-in disabled"
                    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error"
                    else -> "Authentication error"
                }
            }
            else -> "Could not connect to server"
        }

        runOnUiThread {
            Toast.makeText(
                this,
                "$errorMessage. Some features may be limited.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun uploadSession(session: Session) {
        if (isUploading.getAndSet(true)) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = Gson().toJson(session)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(WEB_UPLOAD_URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    sessions.find { it.id == session.id }?.isUploaded = true
                    saveSessions(sessions)
                }
            } catch (e: Exception) {
                saveFailedSession(session)
            } finally {
                isUploading.set(false)
            }
        }
    }

    private fun saveFailedSession(session: Session) {
        val sharedPreferences = getSharedPreferences("FailedSessions", MODE_PRIVATE)
        val gson = Gson()
        val sessionJson = gson.toJson(session)

        val failedSessions = sharedPreferences.getStringSet("failed_sessions", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        failedSessions.add(sessionJson)

        sharedPreferences.edit().apply {
            putStringSet("failed_sessions", failedSessions)
            apply()
        }
    }

    internal fun retryUploadSessions() {
        val sharedPreferences = getSharedPreferences("FailedSessions", MODE_PRIVATE)
        val gson = Gson()
        val failedSessions = sharedPreferences.getStringSet("failed_sessions", mutableSetOf()) ?: mutableSetOf()

        if (failedSessions.isNotEmpty()) {
            for (sessionJson in failedSessions) {
                val session = gson.fromJson(sessionJson, Session::class.java)
                uploadSession(session)
            }

            sharedPreferences.edit().apply {
                remove("failed_sessions")
                apply()
            }
        }
    }

    private fun deleteSessionFromWeb(sessionId: String, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://moto.webhop.me/delete-session/$sessionId")
                    .delete()
                    .addHeader("Username", username)
                    .build()

                client.newCall(request).execute()
            } catch (e: Exception) {
            }
        }
    }

    private fun updateLocation(location: Location) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            }
            currentCity = addresses?.get(0)?.locality ?: "Unknown Location"

            if (isHost && currentSessionId != null) {
                database.reference.child("sessions").child(currentSessionId!!).child("location").setValue(currentCity)
                    .addOnFailureListener { e ->
                    }
            }
        } catch (e: IOException) {
            currentCity = "Unknown Location"
        }
    }

    private fun fetchCurrentLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                callback(location)
            }.addOnFailureListener { e ->
                callback(null)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION_PERMISSION)
            callback(null)
        }
    }

    private fun toggleGPS() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (isGPSEnabled) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isGPSEnabled = false
            Toast.makeText(this, "GPS Disabled", Toast.LENGTH_SHORT).show()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                    .setMinUpdateIntervalMillis(3000)
                    .build()

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                isGPSEnabled = true
                Toast.makeText(this, "GPS Enabled", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION_PERMISSION)
            }
        }
    }

    private fun showExitNotification() {
        if (currentSessionId == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    createAndShowNotification()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                createAndShowNotification()
            }
        }
    }

    private fun createAndShowNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
            }
        }
    }

    private fun showNavigationPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.fab_navigation_menu, popupMenu.menu)

        val gpsMenuItem = popupMenu.menu.findItem(R.id.menu_gps_toggle)
        gpsMenuItem.title = if (isGPSEnabled) "GPS Off" else "GPS On"

        val autoStartMenuItem = popupMenu.menu.findItem(R.id.menu_auto_start)
        autoStartMenuItem.isChecked = autoStartLiveSession

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_analytics -> {
                    val intent = Intent(this, AnalyticsActivity::class.java).apply {
                        putParcelableArrayListExtra("sessions", ArrayList(sessions))
                    }
                    startActivityForResult(intent, REQUEST_CODE_ANALYTICS)
                    true
                }
                R.id.menu_history -> {
                    if (sessions.isNotEmpty()) {
                        val intent = Intent(this, HistoryActivity::class.java).apply {
                            putParcelableArrayListExtra("sessions", ArrayList(sessions))
                        }
                        startActivityForResult(intent, REQUEST_CODE_HISTORY)
                    } else {
                        Toast.makeText(this, "No sessions available", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.menu_compare -> {
                    val intent = Intent(this, CompareActivity::class.java).apply {
                        putParcelableArrayListExtra("sessions", ArrayList(sessions))
                    }
                    startActivityForResult(intent, REQUEST_CODE_COMPARE)
                    true
                }
                R.id.menu_map -> {
                    val intent = Intent(this, MapActivity::class.java)
                    startActivityForResult(intent, REQUEST_CODE_MAP)
                    true
                }
                R.id.menu_gps_toggle -> {
                    toggleGPS()
                    true
                }
                R.id.menu_set_username -> {
                    showUsernameDialog()
                    true
                }
                R.id.menu_open_web -> {
                    openWebLink("https://moto.webhop.me/")
                    true
                }
                R.id.menu_toggle_bg -> {
                    toggleBackgroundColor()
                    true
                }
                R.id.menu_live_sessions -> {
                    showLiveSessionDialog()
                    true
                }
                R.id.menu_auto_start -> {
                    autoStartLiveSession = !autoStartLiveSession
                    sharedPreferences.edit().putBoolean("auto_start_live_session", autoStartLiveSession).apply()
                    item.isChecked = autoStartLiveSession
                    Toast.makeText(this, if (autoStartLiveSession) "Auto-start enabled" else "Auto-start disabled",
                        Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showUsernameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_username, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set Username")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, which ->
                val usernameInput = dialogView.findViewById<EditText>(R.id.usernameInput)
                val username = usernameInput.text.toString()

                val editor = sharedPreferences.edit()
                editor.putString("username", username)
                editor.apply()

                Toast.makeText(this, "Username saved: $username", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun toggleBackgroundColor() {
        if (isBackgroundWhite) {
            binding.root.setBackgroundColor(Color.BLACK)
            setTextColorForAllViews(Color.WHITE)
        } else {
            binding.root.setBackgroundColor(Color.WHITE)
            setTextColorForAllViews(Color.BLACK)
        }
        isBackgroundWhite = !isBackgroundWhite
    }

    private fun setTextColorForAllViews(color: Int) {
        updateTextColor(binding.root, color)
    }

    private fun updateTextColor(view: View, color: Int) {
        if (view is TextView) {
            view.setTextColor(color)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateTextColor(view.getChildAt(i), color)
            }
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun openWebLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun formatTime(time: Long): String {
        val totalMilliseconds = time
        val minutes = (totalMilliseconds / 60000) % 60
        val seconds = (totalMilliseconds / 1000) % 60
        val milliseconds = totalMilliseconds % 1000 / 10
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", minutes, seconds, milliseconds)
    }

    private fun parseTime(timeString: String): Long {
        return try {
            val timePortion = timeString.substringAfterLast(": ").trim()
            if (!timePortion.matches(Regex("\\d{2}:\\d{2}:\\d{2}"))) {
                return Long.MAX_VALUE
            }

            timePortion.split(":").let { parts ->
                val minutes = parts[0].toLong()
                val seconds = parts[1].toLong()
                val milliseconds = parts[2].toLong()
                minutes * 60000 + seconds * 1000 + milliseconds * 10
            }
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun calculateConsistency(lapTimes: List<String>): String {
        if (lapTimes.size < 2) return "N/A"

        val lapTimesInMilliseconds = lapTimes.mapNotNull { parseTime(it) }
        val averageLapTime = lapTimesInMilliseconds.average()
        val variance = lapTimesInMilliseconds.map { lapTime ->
            (lapTime - averageLapTime).pow(2)
        }.average()
        val standardDeviation = sqrt(variance)
        val referenceStandardDeviation = 8944.3
        val consistencyPercentage = 100 * (1 - (standardDeviation / referenceStandardDeviation))
        val clampedConsistency = when {
            consistencyPercentage < 0 -> 0.0
            consistencyPercentage > 100 -> 100.0
            else -> consistencyPercentage
        }

        return "%.2f%%".format(clampedConsistency)
    }

    private fun calculateSpeedMetrics(): Pair<String, String> {
        if (lapTimes.isEmpty()) return Pair("N/A", "N/A")

        val trackLengthMeters = 1700.0

        val validLapTimes = lapTimes.mapNotNull { parseTime(it) }
        if (validLapTimes.isEmpty()) return Pair("N/A", "N/A")

        val averageSpeed = validLapTimes.map { lapTimeSeconds ->
            (trackLengthMeters / (lapTimeSeconds / 1000.0)) * 3.6
        }.average()

        val topSpeed = averageSpeed * 1.4

        return Pair("${topSpeed.toInt()} km/h", "${averageSpeed.toInt()} km/h")
    }

    private fun formatSessionTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (60 * 1000)
        return "$minutes min ago"
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(50)
        }
    }

    private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ANALYTICS && resultCode == RESULT_OK) {
            val updatedSessions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableArrayListExtra("sessions", Session::class.java) ?: mutableListOf()
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableArrayListExtra<Session>("sessions") ?: mutableListOf()
            }

            sessions.clear()
            sessions.addAll(updatedSessions)
            saveSessions(sessions)
        }
    }

    private fun cleanupResources() {
        showExitNotification()
        if (isRunning) {
            stopStopwatch()
        } else {
            leaveLiveSession()
        }
        handler.removeCallbacksAndMessages(null)

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        try {
            unregisterReceiver(startLapReceiver)
            unregisterReceiver(lapReceiver)
            unregisterReceiver(networkChangeReceiver)
        } catch (e: Exception) {
        }
    }
}