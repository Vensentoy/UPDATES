package com.revyu.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton

@Composable
fun CustomizeReviewerScreen(
    viewModel: CreateStudySetViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    CustomizeReviewerContent(
        reviewerColumns = viewModel.reviewerColumns,
        reviewerFontStyle = viewModel.reviewerFontStyle,
        reviewerFontSizeSp = viewModel.reviewerFontSizeSp,
        reviewerMargins = viewModel.reviewerMargins,
        onApplyColumns = { viewModel.applyReviewerColumns(it) },
        onApplyFontStyle = { viewModel.applyReviewerFontStyle(it) },
        onSetFontSize = { viewModel.setReviewerFontSize(it) },
        onApplyMargins = { viewModel.applyReviewerMargins(it) },
        onNext = onNext,
        onBack = onBack
    )
}

@Composable
fun CustomizeReviewerContent(
    reviewerColumns: Int,
    reviewerFontStyle: ReviewerFontStyle,
    reviewerFontSizeSp: Int,
    reviewerMargins: ReviewerMargins,
    onApplyColumns: (Int) -> Unit,
    onApplyFontStyle: (ReviewerFontStyle) -> Unit,
    onSetFontSize: (Int) -> Unit,
    onApplyMargins: (ReviewerMargins) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        WizardStepHeader(step = 2, total = 4, title = "Customize your Reviewer")
        Spacer(Modifier.height(20.dp))

        SettingLabel("Layout")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4).forEach { columns ->
                FilterChip(
                    selected = reviewerColumns == columns,
                    onClick = { onApplyColumns(columns) },
                    label = { Text(if (columns == 1) "1 column" else "$columns columns") }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingLabel("Font style")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewerFontStyle.entries.forEach { style ->
                FilterChip(
                    selected = reviewerFontStyle == style,
                    onClick = { onApplyFontStyle(style) },
                    label = { Text(style.displayName) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingLabel("Font size — ${reviewerFontSizeSp}sp")
        Slider(
            value = reviewerFontSizeSp.toFloat(),
            onValueChange = { onSetFontSize(it.toInt()) },
            valueRange = 9f..18f,
            steps = 8
        )

        Spacer(Modifier.height(20.dp))
        SettingLabel("Margins")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewerMargins.entries.forEach { margin ->
                FilterChip(
                    selected = reviewerMargins == margin,
                    onClick = { onApplyMargins(margin) },
                    label = { Text(margin.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            PrimaryButton(text = "Next", onClick = onNext, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
}

@Preview(showBackground = true)
@Composable
private fun CustomizeReviewerPreview() {
    RevyuTheme {
        CustomizeReviewerContent(
            reviewerColumns = 2,
            reviewerFontStyle = ReviewerFontStyle.SERIF,
            reviewerFontSizeSp = 12,
            reviewerMargins = ReviewerMargins.NORMAL,
            onApplyColumns = {},
            onApplyFontStyle = {},
            onSetFontSize = {},
            onApplyMargins = {},
            onNext = {},
            onBack = {}
        )
    }
}
