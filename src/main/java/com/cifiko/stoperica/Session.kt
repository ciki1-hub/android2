package com.cifiko.stoperica

import android.os.Parcel
import android.os.Parcelable
import java.text.SimpleDateFormat
import java.util.*

data class Session(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val username: String,
    val date: String,
    val startTime: String,
    val fastestLap: String,
    val slowestLap: String,
    val averageLap: String,
    val consistency: String,
    val totalTime: String,
    val location: String,
    val dateTime: String,
    val laps: List<String>,
    val sectors: List<List<String>>,
    val isLive: Boolean = false,
    val liveSessionId: String? = null,
    val topSpeed: String = "N/A",
    val averageSpeed: String = "N/A",
    var isUploaded: Boolean = false,  // Changed from val to var
    val uploadError: String? = null // New field to track upload errors
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        mutableListOf<List<String>>().apply {
            val size = parcel.readInt()
            for (i in 0 until size) {
                add(parcel.createStringArrayList() ?: emptyList())
            }
        },
        parcel.readByte() != 0.toByte(),
        parcel.readString(),
        parcel.readString() ?: "N/A",
        parcel.readString() ?: "N/A",
        parcel.readByte() != 0.toByte(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(username)
        parcel.writeString(date)
        parcel.writeString(startTime)
        parcel.writeString(fastestLap)
        parcel.writeString(slowestLap)
        parcel.writeString(averageLap)
        parcel.writeString(consistency)
        parcel.writeString(totalTime)
        parcel.writeString(location)
        parcel.writeString(dateTime)
        parcel.writeStringList(laps)
        parcel.writeInt(sectors.size)
        sectors.forEach { parcel.writeStringList(it) }
        parcel.writeByte(if (isLive) 1 else 0)
        parcel.writeString(liveSessionId)
        parcel.writeString(topSpeed)
        parcel.writeString(averageSpeed)
        parcel.writeByte(if (isUploaded) 1 else 0)
        parcel.writeString(uploadError)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Session> {
        override fun createFromParcel(parcel: Parcel): Session {
            return Session(parcel)
        }

        override fun newArray(size: Int): Array<Session?> {
            return arrayOfNulls(size)
        }
    }

    // Helper function to get formatted date
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(dateTime) ?: return dateTime
            val outputFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            outputFormat.format(date)
        } catch (e: Exception) {
            dateTime
        }
    }

    // Helper function to get duration in milliseconds
    fun getTotalDurationMs(): Long {
        return try {
            val parts = totalTime.split(":")
            if (parts.size == 3) {
                parts[0].toLong() * 60000 + parts[1].toLong() * 1000 + parts[2].toLong() * 10
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}