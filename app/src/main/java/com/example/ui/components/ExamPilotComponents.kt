package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AiTaskStatus
import com.example.model.Subject
import com.example.model.TopicDifficulty
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SubjectAccentColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExamDateUtils {
    fun formatEpochDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "No date set"
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(epochMillis))
    }

    fun formatIsoDateLabel(isoDate: String): String {
        return try {
            val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = isoFmt.parse(isoDate) ?: return isoDate
            val todayCal = startOfDayCalendar(System.currentTimeMillis())
            val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val targetCal = startOfDayCalendar(date.time)
            val shortFmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val fullFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            when {
                isSameDay(targetCal, todayCal) -> "Today (${shortFmt.format(date)})"
                isSameDay(targetCal, tomorrowCal) -> "Tomorrow (${shortFmt.format(date)})"
                else -> fullFmt.format(date)
            }
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatShortDayAndNumber(isoDate: String): Pair<String, String> {
        return try {
            val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = isoFmt.parse(isoDate) ?: return "" to isoDate.takeLast(2)
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val numFmt = SimpleDateFormat("d", Locale.getDefault())
            dayFmt.format(date) to numFmt.format(date)
        } catch (_: Exception) {
            "" to isoDate.takeLast(2)
        }
    }

    fun todayIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date())
    }

    fun epochToIso(epochMillis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(epochMillis))
    }

    fun defaultExamDateMillis(daysAhead: Int = 10): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        return cal.timeInMillis
    }

    fun daysUntilEpoch(epochMillis: Long): Long {
        val today = startOfDayCalendar(System.currentTimeMillis())
        val target = startOfDayCalendar(epochMillis)
        val diffMillis = target.timeInMillis - today.timeInMillis
        return diffMillis / 86_400_000L
    }

    fun subjectColor(colorIndex: Long): Color {
        val idx = (colorIndex.toInt()).coerceIn(0, SubjectAccentColors.lastIndex)
        return SubjectAccentColors[idx]
    }

    private fun startOfDayCalendar(timeMillis: Long): Calendar {
        return Calendar.getInstance().apply {
            this.timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}

data class CountdownBreakdown(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isPastDue: Boolean,
)

fun computeCountdown(targetEpochMillis: Long, nowMillis: Long): CountdownBreakdown {
    val targetCal = Calendar.getInstance().apply {
        timeInMillis = targetEpochMillis
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diff = targetCal.timeInMillis - nowMillis
    if (diff <= 0L) {
        return CountdownBreakdown(0, 0, 0, 0, isPastDue = true)
    }
    val totalSeconds = diff / 1000L
    val days = totalSeconds / 86400L
    val hours = (totalSeconds % 86400L) / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return CountdownBreakdown(days, hours, minutes, seconds, isPastDue = false)
}

@Composable
fun ExamCountdownCard(
    nextSubject: Subject?,
    nowMillis: Long,
    completedTasks: Int,
    totalTasks: Int,
    onAddSubject: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    if (nextSubject == null) {
        EmptyStateCard(
            title = "Ready for takeoff, Pilot?",
            subtitle = "Add your first subject, exam date, and syllabus (paste text or upload a photo/PDF) to unlock your live countdown & study plan.",
            ctaLabel = "Add First Subject",
            onCtaClick = onAddSubject,
            testTag = "empty_home_add_subject_button",
        )
        return
    }

    val accent = ExamDateUtils.subjectColor(nextSubject.colorIndex)
    val countdown = computeCountdown(nextSubject.examDateEpochMillis, nowMillis)
    val progress = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 0f

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlan)
            .testTag("next_exam_countdown_card"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "NEXT EXAM COUNTDOWN",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                    }
                    Surface(
                        color = accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = nextSubject.examTypeEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = nextSubject.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${ExamDateUtils.formatEpochDate(nextSubject.examDateEpochMillis)} • ${nextSubject.academicLevelEnum.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (countdown.isPastDue) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Exam day is here! You've got this — do a quick revision.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CountdownTile(value = countdown.days, label = "DAYS", accent = accent, modifier = Modifier.weight(1f))
                        CountdownTile(value = countdown.hours, label = "HRS", accent = accent, modifier = Modifier.weight(1f))
                        CountdownTile(value = countdown.minutes, label = "MIN", accent = accent, modifier = Modifier.weight(1f))
                        CountdownTile(value = countdown.seconds, label = "SEC", accent = accent, modifier = Modifier.weight(1f))
                    }
                }

                if (totalTasks > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Study Readiness",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$completedTasks / $totalTasks topics done",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownTile(
    value: Long,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString().padStart(2, '0'),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
    }
}

@Composable
fun AiProgressBanner(
    status: AiTaskStatus,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = status !is AiTaskStatus.Idle) {
        val (container, onContainer) = when (status) {
            is AiTaskStatus.Working -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            is AiTaskStatus.Success -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            is AiTaskStatus.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            AiTaskStatus.Idle -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
        }
        Surface(
            color = container,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (status) {
                    is AiTaskStatus.Working -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = onContainer,
                        )
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is AiTaskStatus.Success -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = onContainer,
                        )
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = onContainer)
                        }
                    }
                    is AiTaskStatus.Error -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = onContainer,
                        )
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = onContainer)
                        }
                    }
                    AiTaskStatus.Idle -> {}
                }
            }
        }
    }
}

@Composable
fun SubjectChipRow(
    subjects: List<Subject>,
    selectedSubjectId: String?,
    includeAllOption: Boolean,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (includeAllOption) {
            FilterChip(
                selected = selectedSubjectId == null,
                onClick = { onSelect(null) },
                label = { Text("All Subjects") },
                modifier = Modifier.testTag("subject_filter_all"),
            )
        }
        subjects.forEach { subject ->
            val accent = ExamDateUtils.subjectColor(subject.colorIndex)
            FilterChip(
                selected = selectedSubjectId == subject.id,
                onClick = { onSelect(subject.id) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                },
                label = {
                    Text(
                        text = subject.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.testTag("subject_chip_${subject.id}"),
            )
        }
    }
}

@Composable
fun DifficultyBadge(difficulty: TopicDifficulty) {
    val (bg, fg) = when (difficulty) {
        TopicDifficulty.EASY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TopicDifficulty.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        TopicDifficulty.HARD -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = difficulty.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCtaClick: () -> Unit,
    testTag: String,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_study_empty_1794988086239),
                contentDescription = "Friendly study illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCtaClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(testTag),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(ctaLabel)
            }
        }
    }
}

@Composable
fun AiActionPillButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
