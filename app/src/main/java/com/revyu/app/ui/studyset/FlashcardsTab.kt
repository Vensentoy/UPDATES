package com.revyu.app.ui.studyset

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.ui.components.EmptyState

@Composable
fun FlashcardsTab(flashcards: List<FlashcardEntity>) {
    if (flashcards.isEmpty()) {
        EmptyState(title = "No flashcards yet", body = "This Study Set is still generating.")
        return
    }

    var index by remember(flashcards) { mutableIntStateOf(0) }
    var showingBack by remember(flashcards) { mutableStateOf(false) }
    val card = flashcards[index]

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${index + 1} of ${flashcards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        FlipCard(
            front = card.front,
            back = card.back,
            showingBack = showingBack,
            onClick = { showingBack = !showingBack },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1.4f)
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (index > 0) { index--; showingBack = false } },
                enabled = index > 0
            ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous") }

            Text("Tap card to flip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            IconButton(
                onClick = { if (index < flashcards.lastIndex) { index++; showingBack = false } },
                enabled = index < flashcards.lastIndex
            ) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next") }
        }
    }
}

@Composable
private fun FlipCard(
    front: String,
    back: String,
    showingBack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (showingBack) 180f else 0f,
        animationSpec = tween(350),
        label = "flip"
    )

    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            val text = if (rotation <= 90f) front else back
            val displayModifier = if (rotation > 90f) Modifier.graphicsLayer { rotationY = 180f } else Modifier
            Text(
                text = text,
                modifier = displayModifier,
                style = if (rotation <= 90f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
