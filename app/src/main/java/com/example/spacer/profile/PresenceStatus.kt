package com.example.spacer.profile

import androidx.compose.ui.graphics.Color

enum class PresenceStatus(
    val dbValue: String,
    val label: String,
    val dotColor: Color
) {
    ONLINE("online", "Online", Color(0xFF22C55E)),
    BUSY("busy", "Busy", Color(0xFFEF4444)),
    INACTIVE("inactive", "Inactive", Color(0xFFF59E0B)),
    OFFLINE("offline", "Offline", Color(0xFF6B7280));

    companion object {
        fun fromDb(value: String?): PresenceStatus {
            return entries.firstOrNull { it.dbValue.equals(value?.trim(), ignoreCase = true) } ?: OFFLINE
        }
    }
}
