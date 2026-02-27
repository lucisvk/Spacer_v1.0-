package com.example.spacer.Navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.spacer.R
import com.example.spacer.home.HomeScreen
import com.example.spacer.profile.EditProfileScreen
import com.example.spacer.profile.ProfileScreen
import com.example.spacer.profile.AttendedEventsScreen
import com.example.spacer.profile.AvailabilityScreen
import com.example.spacer.profile.BlockedUsersScreen
import com.example.spacer.network.AuthRepository
import com.example.spacer.network.SessionPrefs
import com.example.spacer.profile.FriendsScreen
import com.example.spacer.profile.HostedEventsScreen
import com.example.spacer.profile.PublicProfileScreen
import com.example.spacer.profile.ProfileRepository
import com.example.spacer.profile.SettingsScreen
import com.example.spacer.profile.displayLabelFromProfile
import com.example.spacer.events.EventsHubScreen
import com.example.spacer.events.DmChatScreen
import com.example.spacer.events.DmThreadsScreen
import com.example.spacer.events.EventChatScreen
import com.example.spacer.events.HostEventDetailScreen
import com.example.spacer.events.InviteEventScreen
import com.example.spacer.location.CreateEventDetailsScreen
import com.example.spacer.location.CreateEventPlaceScreen
import com.example.spacer.location.PlaceDetailScreen
import com.example.spacer.location.PlaceUi
import com.example.spacer.location.toPlaceUi
import com.example.spacer.chatbot.EventPlanningChatbotScreen
import com.example.spacer.chatbot.EventData
import com.example.spacer.chatbot.CreateChoiceScreen
import com.example.spacer.chatbot.ConversationListScreen
import com.example.spacer.chatbot.ChatPrefs
import com.example.spacer.ui.theme.SpacerPurplePrimary
import com.example.spacer.ui.theme.SpacerPurpleSurface
import com.example.spacer.network.SupabaseManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRoutes {
    const val Home = "home"
    const val Events = "events"
    const val Create = "create"
    const val Profile = "profile"
    const val EditProfile = "edit_profile"
    const val Settings = "settings"
    const val HostedEvents = "hosted_events"
    const val AttendedEvents = "attended_events"
    const val Friends = "friends"
    const val BlockedUsers = "blocked_users"
    const val PublicProfile = "public_profile"
    const val DmThreads = "dm_threads"
    const val DmChat = "dm_chat"
    const val Availability = "availability"
}

private data class BottomNavItem(
    val label: String,
    val route: String,
    val iconRes: Int
)

private val bottomNavItems = listOf(
    BottomNavItem(label = "Home", route = AppRoutes.Home, iconRes = R.drawable.home_button),
    BottomNavItem(label = "Events", route = AppRoutes.Events, iconRes = R.drawable.calendar_button),
    BottomNavItem(label = "Create", route = AppRoutes.Create, iconRes = R.drawable.create_event_button),
    BottomNavItem(label = "Profile", route = AppRoutes.Profile, iconRes = R.drawable.profile_icon)
)

@Composable
fun SpacerAppScaffold(
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    pendingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = androidx.navigation.compose.rememberNavController()
    var eventsDeepLinkRoute by remember { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current
    val sessionPrefs = remember { SessionPrefs(appContext) }
    val authRepository = remember { AuthRepository() }
    val profileRepository = remember { ProfileRepository() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            authRepository.ensureProfileAfterOAuthSignIn()
            profileRepository.load().onSuccess { snap ->
                val label = displayLabelFromProfile(snap.profile)
                if (label.isNotBlank()) {
                    sessionPrefs.saveProfileName(label)
                }
            }
        }
    }

    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        val target = parseDeepLinkTarget(link)
        when (target) {
            is DeepLinkTarget.EventInvite -> {
                eventsDeepLinkRoute = "invite/${target.eventId}"
                navController.navigate(AppRoutes.Events) { launchSingleTop = true }
            }
            is DeepLinkTarget.EventChat -> {
                eventsDeepLinkRoute = "event_chat/${target.eventId}"
                navController.navigate(AppRoutes.Events) { launchSingleTop = true }
            }
            is DeepLinkTarget.DmThreads -> {
                navController.navigate(AppRoutes.DmThreads) { launchSingleTop = true }
            }
            is DeepLinkTarget.DmChat -> {
                navController.navigate("${AppRoutes.DmChat}/${Uri.encode(target.peerId)}") { launchSingleTop = true }
            }
            is DeepLinkTarget.PublicProfile -> {
                navController.navigate("${AppRoutes.PublicProfile}/${Uri.encode(target.userId)}") { launchSingleTop = true }
            }
            is DeepLinkTarget.EventsHub -> {
                navController.navigate(AppRoutes.Events) { launchSingleTop = true }
            }
            is DeepLinkTarget.SocialRequests -> {
                navController.navigate(AppRoutes.Friends) { launchSingleTop = true }
            }
            DeepLinkTarget.None -> Unit
        }
        onDeepLinkConsumed()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                SpacerBottomBar(
                    navController = navController,
                    onNavigate = {}
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppRoutes.Home,
                modifier = Modifier.padding(innerPadding)
            ) {
            composable(AppRoutes.Home) {
                HomeScreen(
                    onViewAllEvents = {
                        navController.navigate(AppRoutes.Events) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onHostOwnEvent = {
                        navController.navigate(AppRoutes.Create) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                    onOpenEventChat = { eventId ->
                        eventsDeepLinkRoute = "event_chat/$eventId"
                        navController.navigate(AppRoutes.Events) { launchSingleTop = true }
                    },
                    onOpenEventDetails = { event ->
                        val eventId = event.id.trim()
                        if (eventId.isBlank()) {
                            navController.navigate(AppRoutes.Events) { launchSingleTop = true }
                        } else {
                            val currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id
                            val isHost = currentUserId != null && currentUserId == event.hostId
                            val safeId = Uri.encode(eventId)
                            val targetRoute = if (isHost) "host/$safeId" else "invite/$safeId"
                            eventsDeepLinkRoute = targetRoute
                            navController.navigate(AppRoutes.Events) { launchSingleTop = true }
                        }
                    }
                )
            }
            composable(AppRoutes.Events) {
                val innerNav = rememberNavController()
                val pendingInnerRoute = eventsDeepLinkRoute
                LaunchedEffect(pendingInnerRoute) {
                    if (!pendingInnerRoute.isNullOrBlank()) {
                        innerNav.navigate(pendingInnerRoute) { launchSingleTop = true }
                        eventsDeepLinkRoute = null
                    }
                }
                NavHost(
                    navController = innerNav,
                    startDestination = "events_hub",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("events_hub") {
                        EventsHubScreen(
                            innerEventsNav = innerNav,
                            outerNav = navController,
                            onOpenInvite = { eventId ->
                                val safeId = Uri.encode(eventId.trim())
                                if (safeId.isNotBlank()) {
                                    runCatching { innerNav.navigate("invite/$safeId") }
                                }
                            },
                            onOpenHostEvent = { eventId ->
                                val safeId = Uri.encode(eventId.trim())
                                if (safeId.isNotBlank()) {
                                    runCatching { innerNav.navigate("host/$safeId") }
                                }
                            },
                            onOpenPublicProfile = { userId ->
                                innerNav.navigate("public_profile/${Uri.encode(userId)}")
                            }
                        )
                    }
                    composable(
                        route = "invite/{eventId}",
                        arguments = listOf(
                            navArgument("eventId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val id = Uri.decode(entry.arguments?.getString("eventId").orEmpty())
                        InviteEventScreen(
                            eventId = id,
                            onBack = { innerNav.popBackStack() },
                            onOpenEventChat = {
                                val safeId = Uri.encode(id.trim())
                                if (safeId.isNotBlank()) {
                                    runCatching { innerNav.navigate("event_chat/$safeId") }
                                }
                            }
                        )
                    }
                    composable(
                        route = "host/{eventId}",
                        arguments = listOf(
                            navArgument("eventId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val id = Uri.decode(entry.arguments?.getString("eventId").orEmpty())
                        HostEventDetailScreen(
                            eventId = id,
                            onOpenEventChat = {
                                val safeId = Uri.encode(id.trim())
                                if (safeId.isNotBlank()) {
                                    runCatching { innerNav.navigate("event_chat/$safeId") }
                                }
                            },
                            onOpenAvailabilityDashboard = {
                                val safeId = Uri.encode(id.trim())
                                if (safeId.isNotBlank()) {
                                    runCatching { innerNav.navigate("availability_dashboard/$safeId") }
                                }
                            },
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    composable(
                        route = "availability_dashboard/{eventId}",
                        arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                    ) { entry ->
                        val id = Uri.decode(entry.arguments?.getString("eventId").orEmpty())
                        com.example.spacer.events.GuestAvailabilityDashboardScreen(
                            eventId = id,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    composable(
                        route = "event_chat/{eventId}",
                        arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                    ) { entry ->
                        val id = Uri.decode(entry.arguments?.getString("eventId").orEmpty())
                        EventChatScreen(
                            eventId = id,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    composable(
                        route = "public_profile/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { entry ->
                        val userId = Uri.decode(entry.arguments?.getString("userId").orEmpty())
                        PublicProfileScreen(
                            userId = userId,
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                }
            }
            composable(AppRoutes.Create) {
                val innerNav = rememberNavController()
                var selectedVenue by remember { mutableStateOf<PlaceUi?>(null) }
                var draftEventTitle by remember { mutableStateOf("") }
                NavHost(
                    navController = innerNav,
                    startDestination = "create_choice",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("create_choice") {
                        CreateChoiceScreen(
                            onUseChatbot = {
                                innerNav.navigate("chatbot_list")
                            },
                            onUseManual = {
                                innerNav.navigate("create_place")
                            }
                        )
                    }
                    composable("chatbot_list") {
                        val context = LocalContext.current
                        val chatPrefs = remember { ChatPrefs(context) }
                        ConversationListScreen(
                            chatPrefs = chatPrefs,
                            onSelectConversation = { conversationId ->
                                innerNav.navigate("chatbot_create/$conversationId")
                            },
                            onNewConversation = {
                                innerNav.navigate("chatbot_create/null")
                            },
                            onBack = { innerNav.popBackStack() }
                        )
                    }
                    composable("chatbot_create/{conversationId}") { backStackEntry ->
                        val conversationId = backStackEntry.arguments?.getString("conversationId")
                        var chatbotVenue by remember { mutableStateOf<PlaceUi?>(null) }
                        EventPlanningChatbotScreen(
                            conversationId = conversationId,
                            onBack = { innerNav.popBackStack("chatbot_list", inclusive = false) },
                            onOpenVenueSelection = { onVenueSelected ->
                                chatbotVenue = null
                                innerNav.navigate("chatbot_place_selection")
                                innerNav.currentBackStackEntry?.savedStateHandle?.set(
                                    "venue_callback",
                                    onVenueSelected
                                )
                            },
                            onCreateEvent = { data ->
                                // Navigate to create_details with the collected data
                                val place = data.venue
                                if (place != null) {
                                    selectedVenue = place
                                    draftEventTitle = data.selectedIdea.ifBlank { "Event with ${data.groupSize} friends" }
                                    innerNav.navigate("create_details") { launchSingleTop = true }
                                }
                            }
                        )
                    }
                    composable("chatbot_place_selection") {
                        val venueCallback = innerNav.previousBackStackEntry
                            ?.savedStateHandle
                            ?.get<(PlaceUi) -> Unit>("venue_callback")

                        CreateEventPlaceScreen(
                            selectedPlace = selectedVenue,
                            onSelectedPlaceChange = { selectedVenue = it },
                            eventTitle = draftEventTitle,
                            onEventTitleChange = { draftEventTitle = it },
                            onOpenPlaceDetail = { place ->
                                innerNav.navigate("place_detail/${Uri.encode(place.id, "UTF-8")}")
                            },
                            onContinue = {
                                if (selectedVenue != null) {
                                    venueCallback?.invoke(selectedVenue!!)
                                    innerNav.popBackStack()
                                }
                            },
                            onUsePlaceForEvent = {
                                if (selectedVenue != null) {
                                    venueCallback?.invoke(selectedVenue!!)
                                    innerNav.popBackStack()
                                }
                            }
                        )
                    }
                    composable("create_place") {
                        CreateEventPlaceScreen(
                            selectedPlace = selectedVenue,
                            onSelectedPlaceChange = { selectedVenue = it },
                            eventTitle = draftEventTitle,
                            onEventTitleChange = { draftEventTitle = it },
                            onOpenPlaceDetail = { place ->
                                innerNav.navigate("place_detail/${Uri.encode(place.id, "UTF-8")}")
                            },
                            onContinue = {
                                if (selectedVenue != null && draftEventTitle.isNotBlank()) {
                                    innerNav.navigate("create_details") { launchSingleTop = true }
                                }
                            },
                            onUsePlaceForEvent = {
                                innerNav.navigate("create_details") { launchSingleTop = true }
                            }
                        )
                    }
                    composable("create_details") {
                        val place = selectedVenue
                        if (place == null) {
                            LaunchedEffect(Unit) {
                                innerNav.popBackStack()
                            }
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            CreateEventDetailsScreen(
                                place = place,
                                eventTitle = draftEventTitle,
                                onEventTitleChange = { draftEventTitle = it },
                                onBack = { innerNav.popBackStack() },
                                onPublished = {
                                    draftEventTitle = ""
                                    selectedVenue = null
                                    innerNav.popBackStack("create_choice", inclusive = false)
                                    navController.navigate(AppRoutes.Events) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                    composable(
                        route = "place_detail/{placeId}",
                        arguments = listOf(
                            navArgument("placeId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val enc = entry.arguments?.getString("placeId").orEmpty()
                        val placeId = Uri.decode(enc)
                        PlaceDetailScreen(
                            placeId = placeId,
                            onBack = { innerNav.popBackStack() },
                            onUseForEvent = { detail ->
                                selectedVenue = detail.toPlaceUi()
                                innerNav.popBackStack()
                                innerNav.navigate("create_details") { launchSingleTop = true }
                            }
                        )
                    }
                }
            }
            composable(AppRoutes.Profile) {
                ProfileScreen(
                    navController = navController,
                    onOpenEditProfile = { navController.navigate(AppRoutes.EditProfile) },
                    onOpenSettings = { navController.navigate(AppRoutes.Settings) },
                    onOpenHostedEvents = { navController.navigate(AppRoutes.HostedEvents) },
                    onOpenAttendedEvents = { navController.navigate(AppRoutes.AttendedEvents) },
                    onOpenFriends = { navController.navigate(AppRoutes.Friends) },
                    onOpenMessages = { navController.navigate(AppRoutes.DmThreads) },
                    onOpenAvailability = { navController.navigate(AppRoutes.Availability) }
                )
            }
            composable(AppRoutes.EditProfile) {
                EditProfileScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.Settings) {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onLogout = onLogout,
                    onOpenBlockedUsers = { navController.navigate(AppRoutes.BlockedUsers) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.Availability) {
                AvailabilityScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.HostedEvents) {
                HostedEventsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.AttendedEvents) {
                AttendedEventsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.Friends) {
                FriendsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { userId ->
                        navController.navigate("${AppRoutes.PublicProfile}/${Uri.encode(userId)}")
                    },
                    onOpenDm = { userId ->
                        navController.navigate("${AppRoutes.DmChat}/${Uri.encode(userId)}")
                    }
                )
            }
            composable(AppRoutes.DmThreads) {
                DmThreadsScreen(
                    onOpenThread = { _, peerId ->
                        navController.navigate("${AppRoutes.DmChat}/${Uri.encode(peerId)}")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "${AppRoutes.DmChat}/{peerId}",
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) { entry ->
                val peerId = Uri.decode(entry.arguments?.getString("peerId").orEmpty())
                DmChatScreen(
                    peerUserId = peerId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.BlockedUsers) {
                BlockedUsersScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { userId ->
                        navController.navigate("${AppRoutes.PublicProfile}/${Uri.encode(userId)}")
                    }
                )
            }
            composable(
                route = "${AppRoutes.PublicProfile}/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { entry ->
                val userId = Uri.decode(entry.arguments?.getString("userId").orEmpty())
                PublicProfileScreen(
                    userId = userId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
    }
}

@Composable
private fun SpacerBottomBar(
    navController: NavHostController,
    onNavigate: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == item.route } == true
                    SpacerBottomTab(
                        item = item,
                        selected = selected,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onNavigate()
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = item.route != AppRoutes.Create
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpacerBottomTab(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

    Surface(
        modifier = modifier.height(44.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = containerColor
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            if (selected) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                androidx.compose.material3.Text(
                    text = item.label,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

private sealed interface DeepLinkTarget {
    data class EventInvite(val eventId: String) : DeepLinkTarget
    data class EventChat(val eventId: String) : DeepLinkTarget
    data class DmChat(val peerId: String) : DeepLinkTarget
    data object DmThreads : DeepLinkTarget
    data object EventsHub : DeepLinkTarget
    data object SocialRequests : DeepLinkTarget
    data class PublicProfile(val userId: String) : DeepLinkTarget
    data object None : DeepLinkTarget
}

private fun parseDeepLinkTarget(raw: String): DeepLinkTarget {
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return DeepLinkTarget.None
    val host = uri.host?.lowercase() ?: return DeepLinkTarget.None
    val firstPath = uri.pathSegments.firstOrNull().orEmpty()
    val secondPath = uri.pathSegments.getOrNull(1).orEmpty()
    return when (host) {
        "event", "invite" -> {
            val id = firstPath.ifBlank { uri.getQueryParameter("id").orEmpty() }
            if (id.isBlank()) DeepLinkTarget.None else DeepLinkTarget.EventInvite(id)
        }
        "event-chat" -> {
            val id = firstPath.ifBlank { uri.getQueryParameter("id").orEmpty() }
            if (id.isBlank()) DeepLinkTarget.None else DeepLinkTarget.EventChat(id)
        }
        "dm" -> {
            when {
                firstPath == "threads" -> DeepLinkTarget.DmThreads
                firstPath.isNotBlank() -> DeepLinkTarget.DmChat(firstPath)
                else -> DeepLinkTarget.None
            }
        }
        "events" -> DeepLinkTarget.EventsHub
        "social" -> if (firstPath == "requests") DeepLinkTarget.SocialRequests else DeepLinkTarget.None
        "profile", "user" -> {
            val userId = firstPath.ifBlank { secondPath }.ifBlank { uri.getQueryParameter("id").orEmpty() }
            if (userId.isBlank()) DeepLinkTarget.None else DeepLinkTarget.PublicProfile(userId)
        }
        else -> DeepLinkTarget.None
    }
}
