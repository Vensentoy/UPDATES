package com.revyu.app.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.data.local.entities.ReviewerDetail
import com.revyu.app.data.local.entities.ReviewerFontStyle
import com.revyu.app.data.local.entities.ReviewerMargins
import com.revyu.app.ui.components.PrimaryButton
import com.revyu.app.ui.components.SecondaryButton
import com.revyu.app.ui.components.ChipGroup

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
        reviewerDetail = viewModel.reviewerDetail,
        isVariation = viewModel.variationIndex > 0,
        rewriteReviewer = viewModel.rewriteReviewer,
        onApplyColumns = { viewModel.applyReviewerColumns(it) },
        onApplyFontStyle = { viewModel.applyReviewerFontStyle(it) },
        onSetFontSize = { viewModel.setReviewerFontSize(it) },
        onApplyMargins = { viewModel.applyReviewerMargins(it) },
        onApplyDetail = { viewModel.applyReviewerDetail(it) },
        onApplyRewriteReviewer = { viewModel.applyRewriteReviewer(it) },
        onNext = onNext,
        onBack = onBack
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizeReviewerContent(
    reviewerColumns: Int,
    reviewerFontStyle: ReviewerFontStyle,
    reviewerFontSizeSp: Int,
    reviewerMargins: ReviewerMargins,
    reviewerDetail: ReviewerDetail = ReviewerDetail.STANDARD,
    isVariation: Boolean = false,
    rewriteReviewer: Boolean = false,
    onApplyColumns: (Int) -> Unit,
    onApplyFontStyle: (ReviewerFontStyle) -> Unit,
    onSetFontSize: (Int) -> Unit,
    onApplyMargins: (ReviewerMargins) -> Unit,
    onApplyDetail: (ReviewerDetail) -> Unit = {},
    onApplyRewriteReviewer: (Boolean) -> Unit = {},
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            WizardStepHeader(step = 2, total = 4, title = "Customize your Reviewer")
            Spacer(Modifier.height(20.dp))

            SettingLabel("Detail level")
            ChipGroup {
                ReviewerDetail.entries.forEach { detail ->
                    FilterChip(
                        selected = reviewerDetail == detail,
                        onClick = { onApplyDetail(detail) },
                        label = {
                            Text(
                                detail.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            if (isVariation) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Also rewrite the reviewer", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "By default, variations reuse the existing reviewer to save generation time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rewriteReviewer,
                        onCheckedChange = onApplyRewriteReviewer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingLabel("Layout")
            ChipGroup {
                listOf(1, 2, 3, 4).forEach { columns ->
                    FilterChip(
                        selected = reviewerColumns == columns,
                        onClick = { onApplyColumns(columns) },
                        label = {
                            Text(
                                if (columns == 1) "1 column" else "$columns columns",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingLabel("Font style")
            ChipGroup {
                ReviewerFontStyle.entries.forEach { style ->
                    FilterChip(
                        selected = reviewerFontStyle == style,
                        onClick = { onApplyFontStyle(style) },
                        label = { Text(style.displayName, maxLines = 1, softWrap = false) }
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
            ChipGroup {
                ReviewerMargins.entries.forEach { margin ->
                    FilterChip(
                        selected = reviewerMargins == margin,
                        onClick = { onApplyMargins(margin) },
                        label = {
                            Text(
                                margin.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

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
