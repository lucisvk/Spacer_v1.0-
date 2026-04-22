package com.example.spacer.chatbot

import com.example.spacer.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.append
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

/** Request body for Groq API chat completion. */
@Serializable
data class GroqRequest(
    val model: String = "llama3-8b-8192",
    val messages: List<GroqMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 1024
)

@Serializable
data class GroqChoice(
    val message: GroqMessage,
    val finish_reason: String
)

@Serializable
data class GroqResponse(
    val id: String,
    val choices: List<GroqChoice>,
    val usage: Usage? = null
)

@Serializable
data class GroqError(
    val error: GroqErrorDetail
)

@Serializable
data class GroqErrorDetail(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

/** Token usage statistics for the API calls */
@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

/** Client for interacting with the Groq ai API for chat completions. */
class GroqApiClient {
    private val client = HttpClient()
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val apiKey = BuildConfig.GROQ_API_KEY
    private val baseUrl = "https://api.groq.com/openai/v1/chat/completions"
    
    /** Send a message to the Groq API and returns the AI response */
    suspend fun sendMessage(
        systemPrompt: String,
        userMessage: String,
        conversationHistory: List<GroqMessage> = emptyList()
    ): Result<String> {
        return try {
            if (apiKey.isBlank()) {
                return Result.failure(Exception("Groq API key is not configured"))
            }
            
            val messages = buildList {
                add(GroqMessage(role = "system", content = systemPrompt))
                addAll(conversationHistory)
                add(GroqMessage(role = "user", content = userMessage))
            }
            
            val request = GroqRequest(
                model = "llama3-8b-8192",
                messages = messages
            )
            
            val messagesJson = json.encodeToString(messages)
            val requestBody = """{"model":"llama-3.1-8b-instant","messages":$messagesJson,"temperature":0.9,"max_tokens":1024}"""
            android.util.Log.d("GroqAPI", "Request: $requestBody")
            
            val responseText = client.post(baseUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                headers {
                    append("Authorization", "Bearer $apiKey")
                }
            }.body<String>()
            
            android.util.Log.d("GroqAPI", "Response: $responseText")
            
            // Try to parse as error response first
            try {
                val errorResponse = json.decodeFromString<GroqError>(responseText)
                return Result.failure(Exception("Groq API Error: ${errorResponse.error.message}"))
            } catch (e: Exception) {
                // Not an error response, try as success response
            }
            
            val response = json.decodeFromString<GroqResponse>(responseText)
            
            val content = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("No response from Groq API"))
            
            Result.success(content)
        } catch (e: Exception) {
            android.util.Log.e("GroqAPI", "Request failed", e)
            Result.failure(Exception("API Error: ${e.message}"))
        }
    }
    
    companion object {
        /** Context for the event planning bot */
        val EVENT_PLANNER_SYSTEM_PROMPT = """
            You are Spacer's Event Planning Assistant. Your role is to help users plan memorable hangouts and events with friends.
            
            You should:
            1. Ask clarifying questions to understand the user's needs
            2. Ask multiple questions at once to speed up the conversation (e.g., "How many people, what day, and what time works for you?")
            3. Suggest creative and varied event ideas - avoid repeating the same suggestions
            4. Mix up your approach: sometimes be casual, sometimes more detailed, sometimes playful
            5. Provide practical advice for logistics (venues, timing, budget)
            6. Guide them through the event creation process efficiently
            7. Be conversational and friendly, not transactional
            
            When suggesting ideas, consider:
            - Group size
            - Time of day/year
            - Budget constraints
            - Location preferences
            - Vibe (chill, active, social, cultural, adventurous)
            
            For outdoor activities, remind users to:
            - Check the weather forecast before the event
            - Have a backup indoor plan in case of bad weather
            - Dress appropriately for expected conditions
            
            Be creative and varied! If the user has similar inputs, try suggesting different event types or approaches. Vary your tone and style naturally.
            
            Keep responses conversational and use emojis occasionally to be friendly.
        """.trimIndent()
    }
}
