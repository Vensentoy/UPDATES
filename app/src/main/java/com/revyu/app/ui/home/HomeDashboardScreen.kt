package com.revyu.app.ui.home
import androidx.compose.ui.tooling.preview.Preview
// Import your app's main theme wrapper, e.g.:
import com.revyu.app.core.theme.RevyuTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revyu.app.R
import com.revyu.app.core.theme.InkNavy
import com.revyu.app.core.theme.Paper
import com.revyu.app.ui.components.EmptyState
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.widgets.HomeGridSection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.CatExpression
import com.revyu.app.core.preferences.getCatDrawableRes
import com.revyu.app.di.LocalAppContainer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * HomeDashboardScreen — the prototype's warm Home tab. Cream Paper page, the round
 * Revyu mascot leading the greeting ("Hi, I'm Revyu! Ready to review?"), then the flat
 * rounded subject sticker grid. Matches revyu-prototype.html's Home: mascot-led,
 * cream, sticker-grid, no dense chrome.
 */
@Composable
fun HomeDashboardScreen(
    onOpenStudySet: (String) -> Unit,
    onOpenExam: (String) -> Unit,
    onImportStudyLoad: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val settings by container.appSettingsStore.settings.collectAsState(initial = AppSettings())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MascotGreetingCard(accentColor = settings.accentColor)
        }
        item {
            HomeGridSection(
                openStudySet = onOpenStudySet,
                openExam = onOpenExam
            )
        }
    }
}

@Composable
private fun MascotGreetingCard(
    accentColor: com.revyu.app.core.preferences.AccentColor = com.revyu.app.core.preferences.AccentColor.ORANGE
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(accentColor.getCatDrawableRes(CatExpression.CHEER)),
                contentDescription = "Revyu the reviewer mascot",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Paper, RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    "Hi, I'm Revyu!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Ready to review? Tap a subject sticker to jump in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
/* =========================================================================
 * Previews
 * ========================================================================= */

@Preview(
    name = "Home Screen - Light Mode",
    showBackground = true,
    backgroundColor = 0xFFFFFBEA // Matching Cream/Paper background
)
@Composable
private fun HomeDashboardScreenPreview() {
    val context = LocalContext.current
    val container = remember(context) { com.revyu.app.di.AppContainer(context) }
    RevyuTheme {
        CompositionLocalProvider(LocalAppContainer provides container) {
            HomeDashboardScreen(
                onOpenStudySet = {},
                onOpenExam = {}
            )
        }
    }
}

@Preview(
    name = "Mascot Greeting Card",
    showBackground = true
)
@Composable
private fun MascotGreetingCardPreview() {
    RevyuTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MascotGreetingCard()
        }
    }
}