package com.revyu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.core.theme.SubjectAccents

fun subjectAccentColor(accentIndex: Int): Color =
    SubjectAccents[accentIndex.mod(SubjectAccents.size)]

/** A small "folder tab" style label — used next to a subject name wherever it appears in a list. */
@Composable
fun SubjectTag(name: String, accentIndex: Int, modifier: Modifier = Modifier) {
    val color = subjectAccentColor(accentIndex)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SubjectTagPreview() {
    RevyuTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubjectTag(name = "Math 101", accentIndex = 0)
            SubjectTag(name = "CS 202", accentIndex = 1)
            SubjectTag(name = "Physics 301", accentIndex = 2)
        }
    }
}
