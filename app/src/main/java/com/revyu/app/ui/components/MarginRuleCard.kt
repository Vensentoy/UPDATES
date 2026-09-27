package com.revyu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.revyu.app.core.theme.MarginRuleWidth
import com.revyu.app.core.theme.MarginTabSize

import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.theme.FolderCoral
import com.revyu.app.core.theme.RevyuTheme

/**
 * Wraps [content] in a card with a highlighter-colored rule down the left edge and a
 * small "tab" dot at its top — Revyu's one recurring signature motif, echoing a margin
 * annotation in a printed reviewer. Used for subject cards, generated content sections,
 * and active exam questions; kept out of everything else so it stays meaningful.
 */
@Composable
fun MarginRuleCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(MarginRuleWidth)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun MarginTabDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(MarginTabSize)
            .height(MarginTabSize)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

@Preview(showBackground = true)
@Composable
private fun MarginRuleCardPreview() {
    RevyuTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MarginRuleCard(accentColor = FolderCoral) {
                Text("Margin Rule Card Example Content", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
