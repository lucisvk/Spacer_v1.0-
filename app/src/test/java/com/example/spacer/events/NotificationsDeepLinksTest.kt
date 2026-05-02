package com.example.spacer.events

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationsDeepLinksTest {

    @Test
    fun deepLinks_eventsHub_matchesExpectedRoute() {
        assertEquals("spacer://events", NotificationsRepository.DeepLinks.eventsHub())
    }

    @Test
    fun deepLinks_eventRoutes_embedEventId() {
        val eventId = "evt-123"
        assertEquals("spacer://event/evt-123", NotificationsRepository.DeepLinks.eventInvite(eventId))
        assertEquals("spacer://event-chat/evt-123", NotificationsRepository.DeepLinks.eventChat(eventId))
    }

    @Test
    fun deepLinks_dmRoutes_embedPeerId() {
        val peerId = "user-42"
        assertEquals("spacer://dm/threads", NotificationsRepository.DeepLinks.dmThreads())
        assertEquals("spacer://dm/user-42", NotificationsRepository.DeepLinks.dmChat(peerId))
    }

    @Test
    fun deepLinks_socialAndProfile_routesAreStable() {
        assertEquals("spacer://social/requests", NotificationsRepository.DeepLinks.socialRequests())
        assertEquals("spacer://profile/u1", NotificationsRepository.DeepLinks.publicProfile("u1"))
    }
}
