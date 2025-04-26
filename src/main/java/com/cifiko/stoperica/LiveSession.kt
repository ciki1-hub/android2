package com.cifiko.stoperica

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Exclude
import java.util.*

data class LiveSession(
    val sessionId: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val sessionName: String = "Live Session",
    val participants: Map<String, String> = mapOf(),
    val laps: List<String> = listOf(),
    val sectors: List<List<String>> = listOf(listOf()),
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val location: String = "Unknown Location",
    val bestLap: String = "N/A",
    val worstLap: String = "N/A",
    val totalTime: String = "00:00:00",
    val createdAt: Long = System.currentTimeMillis()
) {
    @Exclude
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "sessionId" to sessionId,
            "hostId" to hostId,
            "hostName" to hostName,
            "sessionName" to sessionName,
            "participants" to participants,
            "laps" to laps,
            "sectors" to sectors.toFirebaseFormat(),
            "timestamp" to timestamp,
            "isActive" to isActive,
            "location" to location,
            "bestLap" to bestLap,
            "worstLap" to worstLap,
            "totalTime" to totalTime,
            "createdAt" to createdAt
        )
    }

    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): LiveSession {
            val laps = parseStringList(snapshot.child("laps"))
            val sectors = parseSectors(snapshot.child("sectors"), laps.size)

            return LiveSession(
                sessionId = snapshot.child("sessionId").getValue(String::class.java) ?: "",
                hostId = snapshot.child("hostId").getValue(String::class.java) ?: "",
                hostName = snapshot.child("hostName").getValue(String::class.java) ?: "",
                sessionName = snapshot.child("sessionName").getValue(String::class.java) ?: "Live Session",
                participants = parseParticipants(snapshot.child("participants")),
                laps = laps,
                sectors = sectors,
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis(),
                isActive = snapshot.child("isActive").getValue(Boolean::class.java) ?: true,
                location = snapshot.child("location").getValue(String::class.java) ?: "Unknown Location",
                bestLap = snapshot.child("bestLap").getValue(String::class.java) ?: "N/A",
                worstLap = snapshot.child("worstLap").getValue(String::class.java) ?: "N/A",
                totalTime = snapshot.child("totalTime").getValue(String::class.java) ?: "00:00:00",
                createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
            )
        }

        private fun parseParticipants(snapshot: DataSnapshot): Map<String, String> {
            return snapshot.children.associate { child ->
                child.key!! to (child.getValue(String::class.java) ?: "")
            }
        }

        private fun parseStringList(snapshot: DataSnapshot): List<String> {
            return snapshot.children.mapNotNull { it.getValue(String::class.java) }
        }

        private fun parseSectors(snapshot: DataSnapshot, lapCount: Int): List<List<String>> {
            val result = mutableListOf<MutableList<String>>()

            // Initialize with empty lists for each lap
            repeat(lapCount) { result.add(mutableListOf()) }

            // Parse existing sectors
            snapshot.children.forEach { lapEntry ->
                val lapIndex = lapEntry.key?.toIntOrNull() ?: return@forEach
                if (lapIndex < lapCount) {
                    lapEntry.children.forEach { sectorEntry ->
                        sectorEntry.getValue(String::class.java)?.let {
                            result[lapIndex].add(it)
                        }
                    }
                }
            }

            return result
        }
    }
}

private fun List<List<String>>.toFirebaseFormat(): Any {
    return if (this.isEmpty() || this.all { it.isEmpty() }) {
        mapOf("empty" to true)
    } else {
        this.mapIndexedNotNull { lapIndex, sectors ->
            if (sectors.isNotEmpty()) {
                lapIndex.toString() to sectors.mapIndexed { sectorIndex, sector ->
                    sectorIndex.toString() to sector
                }.toMap()
            } else {
                null
            }
        }.toMap()
    }
}