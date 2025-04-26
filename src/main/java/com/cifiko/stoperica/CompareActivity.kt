@file:Suppress("UnusedImport")

package com.cifiko.stoperica

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource

class CompareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle the intent extras for different API levels
        val sessions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("sessions", Session::class.java) ?: mutableListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Session>("sessions") ?: mutableListOf()
        }

        setContent {
            CompareScreen(
                sessions = sessions,
                onDeleteSession = { session ->
                    // Remove the session from the list
                    sessions.remove(session)
                    // Save the updated sessions list to SharedPreferences
                    saveSessions(this, sessions)
                    // Return the updated sessions list to MainActivity
                    val resultIntent = Intent().apply {
                        putParcelableArrayListExtra("sessions", ArrayList(sessions))
                    }
                    setResult(RESULT_OK, resultIntent)
                    // Show a toast to confirm deletion
                    Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show()
                },
                onUpdateSession = { updatedSession ->
                    // Update the session in the list
                    val index = sessions.indexOfFirst { it.id == updatedSession.id }
                    if (index != -1) {
                        sessions[index] = updatedSession
                        saveSessions(this, sessions)
                        // Return the updated sessions list to MainActivity
                        val resultIntent = Intent().apply {
                            putParcelableArrayListExtra("sessions", ArrayList(sessions))
                        }
                        setResult(RESULT_OK, resultIntent)
                    }
                }
            )
        }
    }

    private fun saveSessions(context: Context, sessions: List<Session>) {
        val sharedPreferences = context.getSharedPreferences("StopericaSessions", MODE_PRIVATE)
        sharedPreferences.edit().apply {
            val json = Gson().toJson(sessions)
            putString("sessions", json)
            apply()
        }
    }
}

@Composable
fun CompareScreen(
    sessions: List<Session>,
    onDeleteSession: (Session) -> Unit,
    onUpdateSession: (Session) -> Unit
) {
    var selectedSessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<Session?>(null) }
    var newSessionName by remember { mutableStateOf("") }

    if (showEditDialog && selectedSession != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
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
                        val updatedSession = selectedSession!!.copy(name = newSessionName)
                        onUpdateSession(updatedSession)
                        showEditDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showEditDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header: "Select Sessions to Compare"
        item {
            Text(
                text = "Select Sessions to Compare",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                color = Color.Black
            )
        }

        // List of sessions to select
        items(sessions) { session ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        selectedSessions = if (selectedSessions.contains(session)) {
                            selectedSessions - session // Deselect the session
                        } else {
                            if (selectedSessions.size < 2) {
                                selectedSessions + session // Select the session (max 2)
                            } else {
                                selectedSessions // Do not allow more than 2 selections
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSessions.contains(session)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedSessions.contains(session)) Color.White else Color.Black
                    )
                ) {
                    Text(text = session.name)
                }

                // Edit Button
                IconButton(
                    onClick = {
                        selectedSession = session
                        newSessionName = session.name
                        showEditDialog = true
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = "Edit Session Name"
                    )
                }
            }
        }

        // Display comparison results if 2 sessions are selected
        if (selectedSessions.size == 2) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Display the first selected session
                    Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                        Text(
                            text = "Session: ${selectedSessions[0].name}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black
                        )
                        Text(text = "Date: ${selectedSessions[0].date}", color = Color.Black)
                        Text(text = "Total Time: ${selectedSessions[0].totalTime}", color = Color.Black)
                        Text(text = "Location: ${selectedSessions[0].location}", color = Color.Black)
                        Text(text = "Fastest Lap: Lap ${selectedSessions[0].fastestLap}", color = Color.Black)
                        Text(text = "Slowest Lap: Lap ${selectedSessions[0].slowestLap}", color = Color.Black)
                        Text(text = "Average Lap: ${selectedSessions[0].averageLap}", color = Color.Black)
                        Text(text = "Consistency: ${selectedSessions[0].consistency}", color = Color.Black)

                        // Display Laps and Sectors for the first session
                        selectedSessions[0].laps.forEachIndexed { index, lap ->
                            Text(
                                text = lap,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                                color = Color.Black
                            )

                            if (index < selectedSessions[0].sectors.size && selectedSessions[0].sectors[index].isNotEmpty()) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    selectedSessions[0].sectors[index].forEach { sector ->
                                        Text(
                                            text = sector,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = Color.Black
                                        )
                                    }
                                }
                            }

                            // Add a separator after each lap
                            Text(
                                text = "---------------",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                                color = Color.Black
                            )
                        }
                    }

                    // Display the second selected session
                    Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                        Text(
                            text = "Session: ${selectedSessions[1].name}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.Black
                        )
                        Text(text = "Date: ${selectedSessions[1].date}", color = Color.Black)
                        Text(text = "Total Time: ${selectedSessions[1].totalTime}", color = Color.Black)
                        Text(text = "Location: ${selectedSessions[1].location}", color = Color.Black)
                        Text(text = "Fastest Lap: Lap ${selectedSessions[1].fastestLap}", color = Color.Black)
                        Text(text = "Slowest Lap: Lap ${selectedSessions[1].slowestLap}", color = Color.Black)
                        Text(text = "Average Lap: ${selectedSessions[1].averageLap}", color = Color.Black)
                        Text(text = "Consistency: ${selectedSessions[1].consistency}", color = Color.Black)

                        // Display Laps and Sectors for the second session
                        selectedSessions[1].laps.forEachIndexed { index, lap ->
                            Text(
                                text = lap,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                                color = Color.Black
                            )

                            if (index < selectedSessions[1].sectors.size && selectedSessions[1].sectors[index].isNotEmpty()) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    selectedSessions[1].sectors[index].forEach { sector ->
                                        Text(
                                            text = sector,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = Color.Black
                                        )
                                    }
                                }
                            }

                            // Add a separator after each lap
                            Text(
                                text = "---------------",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        } else if (selectedSessions.isNotEmpty()) {
            item {
                Text(
                    text = "Please select exactly 2 sessions to compare.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                    color = Color.Black
                )
            }
        }
    }
}