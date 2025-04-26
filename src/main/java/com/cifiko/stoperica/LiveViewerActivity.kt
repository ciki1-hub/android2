package com.cifiko.stoperica

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cifiko.stoperica.databinding.ActivityLiveViewerBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth


class LiveViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveViewerBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var sessionId: String? = null
    private var userId: String? = null
    private var isHost: Boolean = false
    private var sessionListener: ValueEventListener? = null
    companion object {
        const val RESULT_SESSION_LEFT = 1001  // Add this line

        // ... any other existing companion object members
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        // Get intent extras
        sessionId = intent.getStringExtra("SESSION_ID")
        userId = intent.getStringExtra("USER_ID") ?: auth.currentUser?.uid
        isHost = intent.getBooleanExtra("IS_HOST", false)

        setupUI()
        setupLiveUpdates()
    }

    private fun setupUI() {
        binding.leaveSessionButton.setOnClickListener {
            leaveSession()
        }

        // Change button text if user is host
        if (isHost) {
            binding.leaveSessionButton.text = "End Session"
        }
    }

    private fun leaveSession() {
        if (sessionId == null || userId == null) {
            setResult(RESULT_SESSION_LEFT)
            finish()
            return
        }

        val sessionRef = database.getReference("sessions/$sessionId")

        if (isHost) {
            sessionRef.removeValue()
                .addOnSuccessListener {
                    setResult(RESULT_SESSION_LEFT)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to end session", Toast.LENGTH_SHORT).show()
                    finish()
                }
        } else {
            sessionRef.child("participants").child(userId!!).removeValue()
                .addOnSuccessListener {
                    setResult(RESULT_SESSION_LEFT)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to leave session", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLiveUpdates() {
        sessionId?.let { id ->
            val sessionRef = database.getReference("sessions/$id")
            sessionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (!snapshot.exists()) {
                            // Session no longer exists
                            showMessage("Session has ended")
                            returnToMainActivity()
                            return
                        }

                        val session = LiveSession.fromSnapshot(snapshot)
                        updateUI(session)
                    } catch (e: Exception) {
                        Log.e("LiveViewer", "Error parsing session data", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("LiveViewer", "Listener cancelled", error.toException())
                }
            }
            sessionRef.addValueEventListener(sessionListener!!)
        }
    }

    private fun updateUI(session: LiveSession) {
        runOnUiThread {
            try {
                binding.liveSessionTitle.text = session.sessionName

                val displayText = buildString {
                    // Current lap sectors (if any)
                    val currentLapIndex = session.laps.size
                    if (currentLapIndex < session.sectors.size &&
                        session.sectors[currentLapIndex].isNotEmpty()) {
                        append("Current Sectors (Lap ${currentLapIndex + 1}):\n")
                        session.sectors[currentLapIndex].forEachIndexed { index, sector ->
                            append("  Sector ${index + 1}: $sector\n")
                        }
                        if (session.laps.isNotEmpty()) append("----------------\n")
                    }

                    // Completed laps with their sectors
                    session.laps.forEachIndexed { lapIndex, lapTime ->
                        append("Lap ${lapIndex + 1}: $lapTime\n")
                        if (lapIndex < session.sectors.size) {
                            session.sectors[lapIndex].forEachIndexed { sectorIndex, sector ->
                                append("  Sector ${sectorIndex + 1}: $sector\n")
                            }
                        }
                        if (lapIndex < session.laps.size - 1) append("----------------\n")
                    }
                }

                binding.liveDataText.text = displayText

                binding.sessionStatsText.text = """
                Host: ${session.hostName}
                Location: ${session.location}
                Best Lap: ${session.bestLap}
                Worst Lap: ${session.worstLap}
                Total Time: ${session.totalTime}
                Updated: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(session.timestamp))}
            """.trimIndent()

            } catch (e: Exception) {
                Log.e("LiveViewer", "Error updating UI", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.let {
            sessionId?.let { id ->
                database.getReference("sessions/$id").removeEventListener(it)
            }
        }
    }
}