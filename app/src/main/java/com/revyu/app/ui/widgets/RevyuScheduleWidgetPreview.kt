package com.revyu.app.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revyu.app.core.theme.InkNavy
import com.revyu.app.core.theme.Paper
import com.revyu.app.core.theme.RevyuTheme
import com.revyu.app.core.widget.ExamReminder
import com.revyu.app.core.widget.NextRow
import com.revyu.app.core.widget.NowCard
import com.revyu.app.core.widget.SchoolEventRow
import com.revyu.app.core.widget.SmartScheduleData

/**
 * RevyuScheduleWidgetCard — Compose implementation matching the launcher native 4x4
 * widget_schedule_large.xml layout for preview and in-app display.
 */
@Composable
fun RevyuScheduleWidgetCard(
    data: SmartScheduleData,
    modifier: Modifier = Modifier,
    openStudySet: (String) -> Unit = {},
    openExam: (String) -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFAF7F2), // Light cream widget background
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE7E2D6), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = InkNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Revyu",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = InkNavy
                            )
                        )
                    }
                    Text(
                        text = "Your classes, smarter.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF666D7F)
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = data.weekdayLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = InkNavy
                        )
                    )
                    Text(
                        text = data.dateLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF666D7F)
                        )
                    )
                }
            }

            // NOW Card
            val now = data.now
            if (now != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(InkNavy, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InkNavy
                                ) {
                                    Text(
                                        text = "NOW",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = Color.White
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${now.minutesLeft} min left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFF666D7F)
                                )
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = now.subject,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = InkNavy
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF666D7F),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = now.timeRange,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFF666D7F)
                                )
                            )

                            Spacer(Modifier.weight(1f))

                            Icon(
                                imageVector = Icons.Filled.Book,
                                contentDescription = null,
                                tint = InkNavy,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = now.subject,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = InkNavy
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE7E2D6))
                        ) {
                            val progressFraction = (now.progressPercent / 100f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(InkNavy)
                            )
                        }
                    }
                }
            }

            // Exam Reminder Banner
            val exam = data.exam
            if (exam != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF1C1), // Light yellow highlight background
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditCalendar,
                                contentDescription = null,
                                tint = InkNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = exam.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = InkNavy
                                    )
                                )
                                Text(
                                    text = exam.prompt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFF3C4A6B)
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (exam.studySetId != null) {
                                    openExam(exam.studySetId)
                                } else if (exam.subjectId != null) {
                                    openStudySet(exam.subjectId)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkNavy,
                                contentColor = Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            )
                        ) {
                            Text(
                                text = "Start Review →",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            // NEXT section
            val next = data.next
            if (next != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NEXT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            color = Color(0xFF8C8475)
                        )
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(InkNavy, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = next.timeLabel,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = InkNavy
                                    )
                                )
                                Text(
                                    text = next.timeRange,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = Color(0xFF666D7F)
                                    )
                                )
                            }
                        }

                        Text(
                            text = next.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = InkNavy
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFE7E2D6), thickness = 1.dp)
                }
            }

            // Footer (Class count + View all)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = Color(0xFF666D7F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (data.classCountToday > 0) {
                            "${data.classCountToday} class${if (data.classCountToday == 1) "" else "es"} today"
                        } else {
                            data.noClassMessage ?: "0 classes today"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF666D7F)
                        )
                    )
                }

                Text(
                    text = "View all →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = InkNavy
                    )
                )
            }

            // Next School Calendar Event (Signing of Student Clearance / Intramurals, etc.)
            val schoolEvent = data.nextSchoolEvent
            if (schoolEvent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF666D7F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Next school event in ${schoolEvent.daysUntil} days",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF666D7F)
                            )
                        )
                        Text(
                            text = schoolEvent.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = InkNavy
                            )
                        )
                    }
                }
            }
        }
    }
}

// Sample Data matching the exact screenshot provided by the user
private val sampleScheduleData = SmartScheduleData(
    weekdayLabel = "FRIDAY",
    dateLabel = "September 25",
    now = NowCard(
        subject = "Study: Industrial Organization and Management",
        timeRange = "4:45 PM - 5:30 PM",
        minutesLeft = 43,
        progressPercent = 25
    ),
    exam = ExamReminder(
        title = "Final Exam is Coming!",
        prompt = "Oct 5, 2026 (10 days away) — want to review first?",
        subjectName = "Industrial Organization and Management",
        subjectId = "sample_sub"
    ),
    next = NextRow(
        timeLabel = "4:00 PM",
        title = "Review: Environmental Science...",
        timeRange = "4:00 PM - 4:45 PM"
    ),
    classCountToday = 0,
    hasSchedule = true,
    nextSchoolEvent = SchoolEventRow(
        daysUntil = 17,
        title = "Signing of Student Clearance"
    )
)

@Preview(name = "Revyu LLCC Schedule Widget - Large Preview", showBackground = true, widthDp = 360)
@Composable
private fun RevyuScheduleWidgetPreview() {
    RevyuTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RevyuScheduleWidgetCard(data = sampleScheduleData)
        }
    }
}

@Preview(name = "Revyu LLCC Schedule Widget - Empty State", showBackground = true, widthDp = 360)
@Composable
private fun RevyuScheduleWidgetEmptyPreview() {
    RevyuTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RevyuScheduleWidgetCard(
                data = SmartScheduleData(
                    weekdayLabel = "MONDAY",
                    dateLabel = "June 8",
                    now = null,
                    exam = null,
                    next = null,
                    classCountToday = 0,
                    hasSchedule = false,
                    nextSchoolEvent = SchoolEventRow(daysUntil = 0, title = "Start of Classes")
                )
            )
        }
    }
}
