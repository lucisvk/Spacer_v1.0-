package com.example.spacer.events

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun AnimatedMessageRow(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(durationMillis = 220)
            ) +
            expandVertically(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 500f
                )
            )
    ) {
        content()
    }
}

@Composable
internal fun ChatAvatar(
    name: String,
    avatarUrl: String?,
    contentDescription: String
) {
    if (avatarUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        AsyncImage(
            model = Uri.parse(avatarUrl),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
internal fun ChatMessageBubble(
    mine: Boolean,
    senderName: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Text(
                senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                SelectionContainer {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}
