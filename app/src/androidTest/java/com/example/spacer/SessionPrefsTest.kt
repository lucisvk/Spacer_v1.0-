package com.example.spacer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spacer.network.SessionPrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SessionPrefsTest {

    private fun prefs(context: Context): SessionPrefs = SessionPrefs(context)

    @Test
    fun setLoggedIn_false_resetsCountersAndProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sp = prefs(context)

        // Prime state with non-zero values.
        sp.incrementHostedCount()
        sp.incrementAttendedCount()
        sp.incrementFriendsCount()
        sp.saveProfileName("Test User")
        sp.saveAboutMe("Hello")
        sp.saveLocationLabel("Test City, 12345")
        sp.saveProfileImageUri("https://example.com/avatar.png")
        sp.setDeviceCalendarReadEnabled(true)
        sp.setCalendarConflictNotificationsEnabled(true)

        sp.setLoggedIn(false)

        assertEquals(0, sp.getHostedCount())
        assertEquals(0, sp.getAttendedCount())
        assertEquals(0, sp.getFriendsCount())
        assertEquals("", sp.getProfileName())
        assertEquals("", sp.getAboutMe())
        assertEquals("", sp.getLocationLabel())
        assertEquals(null, sp.getProfileImageUri())
        assertEquals(false, sp.isDeviceCalendarReadEnabled())
        assertEquals(false, sp.isCalendarConflictNotificationsEnabled())
    }

    @Test
    fun counters_increment_fromZero() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sp = prefs(context)

        // Force reset.
        sp.setLoggedIn(false)

        sp.incrementHostedCount()
        sp.incrementHostedCount()
        sp.incrementAttendedCount()

        assertEquals(2, sp.getHostedCount())
        assertEquals(1, sp.getAttendedCount())
        assertEquals(0, sp.getFriendsCount())
    }
}

