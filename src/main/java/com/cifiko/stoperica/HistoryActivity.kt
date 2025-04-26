package com.cifiko.stoperica

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import androidx.compose.ui.Alignment

class HistoryActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("sessions", Session::class.java) ?: mutableListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Session>("sessions") ?: mutableListOf()
        }

        setContent {
            HistoryScreen(
                sessions = sessions,
                onUpdateSession = { updatedSession ->
                    val index = sessions.indexOfFirst { it.id == updatedSession.id }
                    if (index != -1) {
                        sessions[index] = updatedSession
                        saveSessions(sessions)
                        val resultIntent = Intent().apply {
                            putParcelableArrayListExtra("sessions", ArrayList(sessions))
                        }
                        setResult(RESULT_OK, resultIntent)
                    }
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
}

@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onUpdateSession: (Session) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Session History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (sessions.isEmpty()) {
            Text(
                text = "No sessions available",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(sessions) { session ->
                    SessionItem(
                        session = session,
                        onUpdateSession = onUpdateSession
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SessionItem(
    session: Session,
    onUpdateSession: (Session) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf(session.name) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isEditing = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Edit Session Name"
                )
            }
        }

        Text(text = "Date: ${session.date}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Location: ${session.location}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Total Time: ${session.totalTime}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Fastest Lap: ${session.fastestLap}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Average Lap: ${session.averageLap}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Consistency: ${session.consistency}", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Lap Details:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (session.laps.isNotEmpty()) {
            val firstLap = session.laps.first()
            Text(
                text = firstLap,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (session.sectors.isNotEmpty() && session.sectors[0].isNotEmpty()) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    session.sectors[0].forEach { sector ->
                        Text(
                            text = sector,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            session.laps.drop(1).forEachIndexed { index, lap ->
                Text(
                    text = lap,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )

                if (session.sectors.size > index + 1 && session.sectors[index + 1].isNotEmpty()) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        session.sectors[index + 1].forEach { sector ->
                            Text(
                                text = sector,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
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
                        label = { Text("New Session Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updatedSession = session.copy(name = newSessionName)
                            onUpdateSession(updatedSession)
                            isEditing = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { isEditing = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}