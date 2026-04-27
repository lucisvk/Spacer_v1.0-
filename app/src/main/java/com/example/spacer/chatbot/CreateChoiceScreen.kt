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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacer.ui.theme.SpacerButton
import com.example.spacer.ui.theme.SpacerButtonKind
import com.example.spacer.ui.theme.SpacerButtonSize
import com.example.spacer.ui.theme.SpacerCard
import com.example.spacer.ui.theme.SpacerChip
import com.example.spacer.ui.theme.SpacerChipTone
import com.example.spacer.ui.theme.SpacerScreenHeader
import com.example.spacer.ui.theme.SpacerTipStrip

@Composable
fun CreateChoiceScreen(
    onUseChatbot: () -> Unit,
    onUseManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SpacerScreenHeader(
                kicker = "Plan something",
                title = "Create an",
                italic = "event",
                sub = "How would you like to get started?"
            )

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChooserCard(
                    tag = "Recommended",
                    tagTone = SpacerChipTone.Gold,
                    title = "Chat with assistant",
                    description = "Step by step — I'll ask the right questions, suggest ideas, and handle the details.",
                    ctaLabel = "Start chat",
                    ctaKind = SpacerButtonKind.Gold,
                    icon = "💬",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    onClick = onUseChatbot
                )

                ChooserCard(
                    tag = "Manual",
                    tagTone = SpacerChipTone.Purple,
                    title = "Manual entry",
                    description = "Fill out the details yourself — choose venues, set dates, and invite guests at your own pace.",
                    ctaLabel = "Build it",
                    ctaKind = SpacerButtonKind.Primary,
                    icon = "✏️",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onUseManual
                )

                SpacerTipStrip(
                    label = "Tip",
                    body = "You can switch from assistant to manual mid-flow — your draft carries over."
                )
            }
        }
    }
}

@Composable
private fun ChooserCard(
    tag: String,
    tagTone: SpacerChipTone,
    title: String,
    description: String,
    ctaLabel: String,
    ctaKind: SpacerButtonKind,
    icon: String,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    SpacerCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 20.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpacerChip(text = tag, tone = tagTone)
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 22.sp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(16.dp))

            SpacerButton(
                label = ctaLabel,
                onClick = onClick,
                kind = ctaKind,
                size = SpacerButtonSize.Lg,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
