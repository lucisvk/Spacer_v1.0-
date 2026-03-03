package com.example.spacer.network

import android.content.Context

class SessionPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
        if (!value) {
            // Reset profile-related cached values on logout.
            prefs.edit()
                .putString(KEY_PROFILE_NAME, "")
                .putString(KEY_ABOUT_ME, "")
                .putString(KEY_PROFILE_IMAGE_URI, null)
                .putString(KEY_LOCATION_LABEL, "")
                .putInt(KEY_HOSTED_COUNT, 0)
                .putInt(KEY_ATTENDED_COUNT, 0)
                .putInt(KEY_FRIENDS_COUNT, 0)
                .putBoolean(KEY_CALENDAR_SHARING, true)
                .putBoolean(KEY_CALENDAR_CONFLICT_NOTIFY, false)
                .putBoolean(KEY_DEVICE_CALENDAR_READ, false)
                .putBoolean(KEY_EVENT_LOCATION_SHARING, true)
                .putBoolean(KEY_DARK_THEME, true)
                .putString(KEY_PRESENCE_STATUS, "offline")
                .apply()
            appContext.getSharedPreferences("calendar_conflict_notifications", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    fun saveProfileName(name: String) {
        prefs.edit().putString(KEY_PROFILE_NAME, name).apply()
    }

    fun getProfileName(): String = prefs.getString(KEY_PROFILE_NAME, "") ?: ""

    fun saveAboutMe(aboutMe: String) {
        prefs.edit().putString(KEY_ABOUT_ME, aboutMe).apply()
    }

    fun getAboutMe(): String = prefs.getString(KEY_ABOUT_ME, "") ?: ""

    fun saveProfileImageUri(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply()
    }

    fun getProfileImageUri(): String? = prefs.getString(KEY_PROFILE_IMAGE_URI, null)

    fun savePresenceStatus(status: String) {
        prefs.edit().putString(KEY_PRESENCE_STATUS, status).apply()
    }

    fun getPresenceStatus(): String = prefs.getString(KEY_PRESENCE_STATUS, "offline") ?: "offline"

    fun saveLocationLabel(location: String) {
        prefs.edit().putString(KEY_LOCATION_LABEL, location).apply()
    }

    fun getLocationLabel(): String = prefs.getString(KEY_LOCATION_LABEL, "") ?: ""

    fun getHostedCount(): Int = prefs.getInt(KEY_HOSTED_COUNT, 0)

    fun getAttendedCount(): Int = prefs.getInt(KEY_ATTENDED_COUNT, 0)

    fun getFriendsCount(): Int = prefs.getInt(KEY_FRIENDS_COUNT, 0)

    fun saveHostedCount(value: Int) {
        prefs.edit().putInt(KEY_HOSTED_COUNT, value.coerceAtLeast(0)).apply()
    }

    fun saveAttendedCount(value: Int) {
        prefs.edit().putInt(KEY_ATTENDED_COUNT, value.coerceAtLeast(0)).apply()
    }

    fun saveFriendsCount(value: Int) {
        prefs.edit().putInt(KEY_FRIENDS_COUNT, value.coerceAtLeast(0)).apply()
    }

    fun incrementHostedCount() {
        prefs.edit().putInt(KEY_HOSTED_COUNT, getHostedCount() + 1).apply()
    }

    fun incrementAttendedCount() {
        prefs.edit().putInt(KEY_ATTENDED_COUNT, getAttendedCount() + 1).apply()
    }

    fun incrementFriendsCount() {
        prefs.edit().putInt(KEY_FRIENDS_COUNT, getFriendsCount() + 1).apply()
    }

    /** Cache the most frequently read profile data for fast local restore. */
    fun saveProfileSnapshot(
        profileName: String,
        aboutMe: String,
        profileImageUri: String?,
        hostedCount: Int,
        attendedCount: Int,
        friendsCount: Int
    ) {
        prefs.edit()
            .putString(KEY_PROFILE_NAME, profileName)
            .putString(KEY_ABOUT_ME, aboutMe)
            .putString(KEY_PROFILE_IMAGE_URI, profileImageUri)
            .putInt(KEY_HOSTED_COUNT, hostedCount.coerceAtLeast(0))
            .putInt(KEY_ATTENDED_COUNT, attendedCount.coerceAtLeast(0))
            .putInt(KEY_FRIENDS_COUNT, friendsCount.coerceAtLeast(0))
            .apply()
    }

    /**
     * When true, the user allows Spacer to share invite availability with hosts (saved presets / notes).
     * Full read-sync with Google Calendar can be layered on later; this flag is the in-app consent gate.
     */
    fun isCalendarAvailabilitySharingEnabled(): Boolean =
        prefs.getBoolean(KEY_CALENDAR_SHARING, true)

    fun setCalendarAvailabilitySharingEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_SHARING, value).apply()
        if (!value) {
            prefs.edit().putBoolean(KEY_CALENDAR_CONFLICT_NOTIFY, false).apply()
        }
    }

    /** When true, notify the user if an invite time may conflict with calendar busy time (future work). */
    fun isCalendarConflictNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_CALENDAR_CONFLICT_NOTIFY, false)

    fun setCalendarConflictNotificationsEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_CALENDAR_CONFLICT_NOTIFY, value).apply()
    }

    /**
     * When true, the user asked to read on-device calendars (Google, etc.) for busy/free overlap checks.
     * Actual reads require [android.Manifest.permission.READ_CALENDAR] at runtime.
     */
    fun isDeviceCalendarReadEnabled(): Boolean =
        prefs.getBoolean(KEY_DEVICE_CALENDAR_READ, false)

    fun setDeviceCalendarReadEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE_CALENDAR_READ, value).apply()
        if (!value) {
            prefs.edit().putBoolean(KEY_CALENDAR_CONFLICT_NOTIFY, false).apply()
        }
    }

    fun isEventLocationSharingEnabled(): Boolean =
        prefs.getBoolean(KEY_EVENT_LOCATION_SHARING, true)

    fun setEventLocationSharingEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_EVENT_LOCATION_SHARING, value).apply()
    }

    fun isDarkThemeEnabled(): Boolean =
        prefs.getBoolean(KEY_DARK_THEME, true)

    fun setDarkThemeEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacer_session"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_ABOUT_ME = "about_me"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_HOSTED_COUNT = "hosted_count"
        private const val KEY_ATTENDED_COUNT = "attended_count"
        private const val KEY_FRIENDS_COUNT = "friends_count"
        private const val KEY_CALENDAR_SHARING = "calendar_availability_sharing_enabled"
        private const val KEY_CALENDAR_CONFLICT_NOTIFY = "calendar_conflict_notifications_enabled"
        private const val KEY_DEVICE_CALENDAR_READ = "device_calendar_read_enabled"
        private const val KEY_EVENT_LOCATION_SHARING = "event_location_sharing_enabled"
        private const val KEY_DARK_THEME = "dark_theme_enabled"
        private const val KEY_PRESENCE_STATUS = "presence_status"
    }
}
