package com.example.spacer.chatbot

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** a chat message with text, sender type, and timestamp. */
@Serializable
data class SavedChatMessage(
    val text: String,
    val isBot: Boolean,
    val timestamp: Long
)

/** A saved conversation with ID, title, messages, current step, event data, and timestamp. */
@Serializable
data class SavedConversation(
    val id: String,
    val title: String,
    val messages: List<SavedChatMessage>,
    val conversationStep: String,
    val eventData: EventData,
    val timestamp: Long
)

/** Manages persistence of chatbot conversations using SharedPreferences. */
class ChatPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFS_NAME = "chat_prefs"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_CURRENT_CONVERSATION_ID = "current_conversation_id"
    }

    /** Save or update a conversation in local storage. */
    fun saveConversation(conversation: SavedConversation) {
        val conversations = getAllConversations().toMutableList()
        val existingIndex = conversations.indexOfFirst { it.id == conversation.id }
        
        if (existingIndex >= 0) {
            conversations[existingIndex] = conversation
        } else {
            conversations.add(conversation)
        }
        
        val jsonString = json.encodeToString(conversations)
        prefs.edit().putString(KEY_CONVERSATIONS, jsonString).apply()
    }

    /** Retrieve all saved conversations from local storage. */
    fun getAllConversations(): List<SavedConversation> {
        val jsonString = prefs.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SavedConversation>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Load a specific conversation by ID */
    fun loadConversation(id: String): SavedConversation? {
        return getAllConversations().find { it.id == id }
    }

    /** Delete a conversation by ID and clear current ID if it was the active one. */
    fun deleteConversation(id: String) {
        val conversations = getAllConversations().toMutableList()
        conversations.removeAll { it.id == id }
        val jsonString = json.encodeToString(conversations)
        prefs.edit().putString(KEY_CONVERSATIONS, jsonString).apply()
        
        // If we deleted the current conversation, clear the current ID
        if (getCurrentConversationId() == id) {
            setCurrentConversationId(null)
        }
    }

    /** Set the currently active conversation ID. */
    fun setCurrentConversationId(id: String?) {
        prefs.edit().putString(KEY_CURRENT_CONVERSATION_ID, id).apply()
    }

    /** Gets the currently active conversation ID */
    fun getCurrentConversationId(): String? {
        return prefs.getString(KEY_CURRENT_CONVERSATION_ID, null)
    }

    /** Gets the currently active conversation */
    fun getCurrentConversation(): SavedConversation? {
        val currentId = getCurrentConversationId() ?: return null
        return loadConversation(currentId)
    }

    /** Create a new empty conversation with a unique ID. */
    fun createNewConversation(): SavedConversation {
        val id = java.util.UUID.randomUUID().toString()
        return SavedConversation(
            id = id,
            title = "New Conversation",
            messages = emptyList(),
            conversationStep = "GREETING",
            eventData = EventData(),
            timestamp = System.currentTimeMillis()
        )
    }
}
