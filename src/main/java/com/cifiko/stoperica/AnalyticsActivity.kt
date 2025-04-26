package com.cifiko.stoperica

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve sessions from the intent
        val sessions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("sessions", Session::class.java) ?: mutableListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Session>("sessions") ?: mutableListOf()
        }

        // Set the content using Jetpack Compose
        setContent {
            AnalyticsScreen(
                sessions = sessions,
                onSessionUpdated = { updatedSessions ->
                    saveSessions(updatedSessions)
                    val resultIntent = Intent().apply {
                        putParcelableArrayListExtra("sessions", ArrayList(updatedSessions))
                    }
                    setResult(RESULT_OK, resultIntent)
                },
                onDeleteSession = { sessionId, username ->
                    deleteSessionFromWeb(sessionId, username)
                },
                onUpdateSession = { session ->
                    updateSessionOnWeb(session)
                }
            )
        }
    }

    private fun saveSessions(sessions: List<Session>) {
        val sharedPreferences = getSharedPreferences("StopericaSessions", MODE_PRIVATE)
        sharedPreferences.edit().apply {
            val json = Gson().toJson(sessions)
            putString("sessions", json)
            apply()
        }
    }

    private fun deleteSessionFromWeb(sessionId: String, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OkHttpClient().newCall(
                    Request.Builder()
                        .url("https://moto.webhop.me/delete-session/$sessionId")
                        .delete()
                        .addHeader("Username", username)
                        .build()
                ).execute()
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }

    private fun updateSessionOnWeb(session: Session) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = Gson().toJson(session)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://moto.webhop.me/upload")
                    .post(body)
                    .build()

                client.newCall(request).execute()
            } catch (e: Exception) {
                // Silent failure
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    sessions: List<Session>,
    onSessionUpdated: (List<Session>) -> Unit,
    onDeleteSession: (String, String) -> Unit,
    onUpdateSession: (Session) -> Unit
) {
    var fastestSession by remember { mutableStateOf<Session?>(null) }
    var slowestSession by remember { mutableStateOf<Session?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf<Session?>(null) }
    var localSessions by remember { mutableStateOf(sessions) }

    val reversedSessions = localSessions.reversed()

    LaunchedEffect(localSessions) {
        if (localSessions.isNotEmpty()) {
            fastestSession = localSessions.minByOrNull { parseTime(it.fastestLap) }
            slowestSession = localSessions.maxByOrNull { parseTime(it.slowestLap) }
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(reversedSessions) { session ->
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Session Name: ${session.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            isEditing = true
                            newSessionName = session.name
                            selectedSession = session
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit Session Name"
                        )
                    }
                    IconButton(
                        onClick = {
                            localSessions = localSessions - session
                            onSessionUpdated(localSessions)
                            onDeleteSession(session.id, session.username)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete Session"
                        )
                    }
                }

                Text(text = "Date: ${session.date}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Ride Time: ${session.startTime}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Fastest Lap: ${session.fastestLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Slowest Lap: ${session.slowestLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Average Lap: ${session.averageLap}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Consistency: ${session.consistency}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Total Time: ${session.totalTime}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Location: ${session.location}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = { Text("Edit Session Name") },
            text = {
                OutlinedTextField(
                    value = newSessionName,
                    onValueChange = { newSessionName = it },
                    label = { Text("Session Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedSession?.let { session ->
                            val updatedSession = session.copy(name = newSessionName)
                            localSessions = localSessions.map {
                                if (it.id == session.id) updatedSession else it
                            }
                            onSessionUpdated(localSessions)
                            onUpdateSession(updatedSession)
                            isEditing = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = { isEditing = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
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