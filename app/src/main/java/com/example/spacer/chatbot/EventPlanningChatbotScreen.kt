package com.example.spacer.chatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.location.PlaceUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** A chat message with ID, sender type, text, and timestamp. */
data class ChatMessage(
    val id: String,
    val isBot: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** Steps in the conversation flow. */
enum class ConversationStep {
    GREETING,
    COLLECT_GROUP_SIZE,
    COLLECT_DATE_TIME,
    COLLECT_VIBE,
    SUGGEST_IDEAS,
    COLLECT_SELECTION,
    COLLECT_BUDGET,
    COLLECT_PREFERENCES,
    SEARCH_VENUES,
    SELECT_VENUE,
    CONFIRM_EVENT,
    COMPLETE
}

/** Main chatbot screen for event planning. */
@Composable
fun EventPlanningChatbotScreen(
    conversationId: String?,
    onBack: () -> Unit,
    onOpenVenueSelection: (onVenueSelected: (PlaceUi) -> Unit) -> Unit,
    onCreateEvent: (EventData) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groqApiClient = remember { GroqApiClient() }
    val chatPrefs = remember { ChatPrefs(context) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var currentStep by remember { mutableStateOf(ConversationStep.GREETING) }
    var userInput by remember { mutableStateOf("") }
    var isBotTyping by remember { mutableStateOf(false) }
    var conversationHistory = remember { mutableListOf<GroqMessage>() }
    
    var eventData by remember { mutableStateOf(EventData()) }
    var showVenueSelection by remember { mutableStateOf(false) }
    var currentConversationId by remember { mutableStateOf(conversationId) }

    LaunchedEffect(conversationId) {
        // Treat "null" string as actual null for new conversations
        val effectiveConversationId = if (conversationId == "null") null else conversationId
        
        // Reset state when conversation changes
        messages.clear()
        conversationHistory.clear()
        currentStep = ConversationStep.GREETING
        eventData = EventData()
        
        if (effectiveConversationId != null) {
            // Load existing conversation
            val savedConversation = chatPrefs.loadConversation(effectiveConversationId)
            if (savedConversation != null) {
                // Restore messages
                savedConversation.messages.forEach { savedMsg ->
                    messages.add(
                        ChatMessage(
                            id = java.util.UUID.randomUUID().toString(),
                            isBot = savedMsg.isBot,
                            text = savedMsg.text,
                            timestamp = savedMsg.timestamp
                        )
                    )
                }
                // Restore step
                currentStep = try {
                    ConversationStep.valueOf(savedConversation.conversationStep)
                } catch (e: Exception) {
                    ConversationStep.GREETING
                }
                // Restore event data
                eventData = savedConversation.eventData
                // Restore conversation history for AI context
                messages.forEach { msg ->
                    conversationHistory.add(
                        GroqMessage(
                            role = if (msg.isBot) "assistant" else "user",
                            content = msg.text
                        )
                    )
                }
            }
        } else {
            // Create new conversation
            val newConversation = chatPrefs.createNewConversation()
            currentConversationId = newConversation.id
            chatPrefs.setCurrentConversationId(newConversation.id)
            addBotMessage(
                "Hi! I'm your Event Planning Assistant 🎉\n\n" +
                "I can help you plan a great hangout with your friends. Let's get started!\n\n" +
                "How many people, what day, and what time works for you?",
                messages
            )
            currentStep = ConversationStep.COLLECT_GROUP_SIZE
        }
    }

    // Auto-save conversation when it changes
    LaunchedEffect(messages.size, currentStep, eventData) {
        if (currentConversationId != null) {
            val savedMessages = messages.map { msg ->
                SavedChatMessage(
                    text = msg.text,
                    isBot = msg.isBot,
                    timestamp = msg.timestamp
                )
            }
            // Generate title from first user message
            val title = messages.firstOrNull { !it.isBot }?.text?.take(30) ?: "New Conversation"
            
            val savedConversation = SavedConversation(
                id = currentConversationId!!,
                title = title,
                messages = savedMessages,
                conversationStep = currentStep.name,
                eventData = eventData,
                timestamp = System.currentTimeMillis()
            )
            chatPrefs.saveConversation(savedConversation)
        }
    }

    LaunchedEffect(showVenueSelection) {
        if (showVenueSelection) {
            onOpenVenueSelection { place ->
                eventData = eventData.copy(venue = place)
                addBotMessage(
                    "Great choice! I've selected: ${place.name}\n\n" +
                    "Does everything look good? Type 'yes' to create your event or 'no' to make changes.",
                    messages
                )
                currentStep = ConversationStep.CONFIRM_EVENT
                showVenueSelection = false
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Event Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onBack) {
                            Text("Back")
                        }
                    }
                }
            }

            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    ChatBubble(message = message)
                }
                if (isBotTyping) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Thinking...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text(getPlaceholderForStep(currentStep)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank() && !isBotTyping) {
                                scope.launch {
                                    isBotTyping = true
                                    handleUserInput(
                                        userInput,
                                        messages,
                                        eventData,
                                        currentStep,
                                        onCreateEvent,
                                        onBack,
                                        groqApiClient,
                                        conversationHistory,
                                        chatPrefs,
                                        currentConversationId
                                    ) { newStep, newData, botResponse ->
                                        currentStep = newStep
                                        eventData = newData
                                        userInput = ""
                                        if (botResponse != null) {
                                            addBotMessage(botResponse, messages)
                                        }
                                        // Trigger venue selection when reaching SEARCH_VENUES step
                                        if (newStep == ConversationStep.SEARCH_VENUES) {
                                            showVenueSelection = true
                                        }
                                    }
                                    isBotTyping = false
                                }
                            }
                        },
                        enabled = userInput.isNotBlank() && !isBotTyping
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val backgroundColor = if (message.isBot) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = if (message.isBot) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val alignment = if (message.isBot) Alignment.CenterStart else Alignment.CenterEnd

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

private fun addBotMessage(text: String, messages: MutableList<ChatMessage>) {
    messages.add(
        ChatMessage(
            id = "bot_${System.currentTimeMillis()}",
            isBot = true,
            text = text
        )
    )
}

private fun addUserMessage(text: String, messages: MutableList<ChatMessage>) {
    messages.add(
        ChatMessage(
            id = "user_${System.currentTimeMillis()}",
            isBot = false,
            text = text
        )
    )
}

private fun getPlaceholderForStep(step: ConversationStep): String {
    return when (step) {
        ConversationStep.COLLECT_GROUP_SIZE -> "Number of people..."
        ConversationStep.COLLECT_DATE_TIME -> "Date and time (e.g., Saturday afternoon)..."
        ConversationStep.COLLECT_VIBE -> "Chill, active, social, or surprise me..."
        ConversationStep.SUGGEST_IDEAS -> "Type the number of your choice..."
        ConversationStep.COLLECT_SELECTION -> "Your choice..."
        ConversationStep.COLLECT_BUDGET -> "Budget per person (e.g., $20)..."
        ConversationStep.COLLECT_PREFERENCES -> "Food preferences, dietary restrictions..."
        ConversationStep.CONFIRM_EVENT -> "Type 'yes' to confirm or 'no' to edit..."
        else -> "Type your response..."
    }
}

private suspend fun handleUserInput(
    input: String,
    messages: MutableList<ChatMessage>,
    eventData: EventData,
    currentStep: ConversationStep,
    onCreateEvent: (EventData) -> Unit,
    onBack: () -> Unit,
    groqApiClient: GroqApiClient,
    conversationHistory: MutableList<GroqMessage>,
    chatPrefs: ChatPrefs,
    currentConversationId: String?,
    onStepComplete: (ConversationStep, EventData, String?) -> Unit
) {
    addUserMessage(input, messages)
    conversationHistory.add(GroqMessage(role = "user", content = input))
    
    when (currentStep) {
        ConversationStep.COLLECT_GROUP_SIZE -> {
            val newEventData = eventData.copy(groupSize = input.trim())
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                "User said they want to plan an event for ${input.trim()} people. Ask what day and time works for them."
            )
            onStepComplete(ConversationStep.COLLECT_DATE_TIME, newEventData, response)
        }
        ConversationStep.COLLECT_DATE_TIME -> {
            // Parse the response to extract date and time
            val parts = input.trim().split(Regex(",|and|\\s+"))
            val date = parts.getOrNull(0) ?: input.trim()
            val time = parts.getOrNull(1) ?: ""
            val newEventData = eventData.copy(date = date, time = time)
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                "User said '$date' and '$time'. Ask what kind of vibe they want: chill at home, go out, try an activity, or surprise them with ideas."
            )
            onStepComplete(ConversationStep.COLLECT_VIBE, newEventData, response)
        }
        ConversationStep.COLLECT_VIBE -> {
            val newEventData = eventData.copy(vibe = input.trim())
            val nextStep = if (input.trim().lowercase().contains("surprise")) {
                ConversationStep.SUGGEST_IDEAS
            } else {
                ConversationStep.COLLECT_BUDGET
            }
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                if (nextStep == ConversationStep.SUGGEST_IDEAS) {
                    "User wants surprise ideas. Suggest 5-7 fun event ideas like lunch+walk, movie night, board games, café meetup, park adventure, thrift run, dessert place. Ask them to pick one."
                } else {
                    "User chose '${input.trim()}'. Ask what their budget per person is."
                }
            )
            onStepComplete(nextStep, newEventData, response)
        }
        ConversationStep.SUGGEST_IDEAS -> {
            val selectedIdea = input.trim()
            val newEventData = eventData.copy(selectedIdea = selectedIdea)
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                "User chose '$selectedIdea'. Ask what their budget per person is."
            )
            onStepComplete(ConversationStep.COLLECT_BUDGET, newEventData, response)
        }
        ConversationStep.COLLECT_BUDGET -> {
            val newEventData = eventData.copy(budget = input.trim())
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                "User said ${input.trim()} per person. Ask about food preferences or dietary restrictions."
            )
            onStepComplete(ConversationStep.COLLECT_PREFERENCES, newEventData, response)
        }
        ConversationStep.COLLECT_PREFERENCES -> {
            val newEventData = eventData.copy(preferences = input.trim())
            val response = "Perfect! Let me search for venues that match your criteria.\n\nI'll open the venue selector for you now."
            onStepComplete(ConversationStep.SEARCH_VENUES, newEventData, response)
        }
        ConversationStep.SEARCH_VENUES -> {
            // This step triggers the venue selection UI
            onStepComplete(ConversationStep.SEARCH_VENUES, eventData, null)
        }
        ConversationStep.CONFIRM_EVENT -> {
            if (input.trim().lowercase() == "yes") {
                val response = "Creating your event... 🎉"
                onStepComplete(ConversationStep.COMPLETE, eventData, response)
                onCreateEvent(eventData)
                // Delete saved conversation after event creation
                if (currentConversationId != null) {
                    chatPrefs.deleteConversation(currentConversationId)
                }
            } else {
                val response = "No problem! Let's start over. How many people will be joining?"
                onStepComplete(ConversationStep.COLLECT_GROUP_SIZE, EventData(), response)
            }
        }
        else -> {
            val response = getAIResponse(
                groqApiClient,
                conversationHistory,
                "User said: $input. Help them continue with event planning."
            )
            onStepComplete(currentStep, eventData, response)
        }
    }
}

private suspend fun getAIResponse(
    groqApiClient: GroqApiClient,
    conversationHistory: MutableList<GroqMessage>,
    contextPrompt: String
): String {
    return withContext(Dispatchers.IO) {
        val result = groqApiClient.sendMessage(
            systemPrompt = GroqApiClient.EVENT_PLANNER_SYSTEM_PROMPT + "\n\nContext: $contextPrompt",
            userMessage = "",
            conversationHistory = conversationHistory
        )
        
        result.fold(
            onSuccess = { it },
            onFailure = { error ->
                android.util.Log.e("GroqAPI", "API Error: ${error.message}", error)
                "Connection error: ${error.message}"
            }
        )
    }
}

@Serializable
data class EventData(
    val groupSize: String = "",
    val date: String = "",
    val time: String = "",
    val vibe: String = "",
    val selectedIdea: String = "",
    val budget: String = "",
    val preferences: String = "",
    val venue: PlaceUi? = null
)
