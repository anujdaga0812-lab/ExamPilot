package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.UiState
import com.example.ui.components.AiActionPillButton
import com.example.ui.components.DifficultyBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExamDateUtils
import com.example.ui.components.SubjectChipRow

@Composable
fun StudyPlanScreen(
    subjectsState: UiState<List<Subject>>,
    tasksState: UiState<List<StudyTask>>,
    selectedSubjectId: String?,
    isAiBusy: Boolean,
    onSelectSubject: (String?) -> Unit,
    onGeneratePlan: (Subject) -> Unit,
    onToggleTask: (StudyTask) -> Unit,
    onAddSubject: () -> Unit,
) {
    var selectedDateIso by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (subjectsState is UiState.Loading || tasksState is UiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
        val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()

        val filteredBySubject = if (selectedSubjectId == null) {
            allTasks
        } else {
            allTasks.filter { it.subjectId == selectedSubjectId }
        }

        val uniqueDates = filteredBySubject.map { it.scheduledDateIso }.distinct().sorted()
        val displayedTasks = if (selectedDateIso != null && selectedDateIso in uniqueDates) {
            filteredBySubject.filter { it.scheduledDateIso == selectedDateIso }
        } else {
            filteredBySubject
        }

        val groupedByDate = displayedTasks.groupBy { it.scheduledDateIso }
        val completedCount = filteredBySubject.count { it.completed }
        val totalCount = filteredBySubject.size
        val targetSubjectForGenerate = subjects.firstOrNull { it.id == selectedSubjectId } ?: subjects.firstOrNull()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Study Plan",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Day-by-day checklist paced by topic difficulty and exam dates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No subjects yet",
                        subtitle = "Add a subject and exam date first so Gemini can build your personalized day-by-day schedule.",
                        ctaLabel = "Add Subject & Syllabus",
                        onCtaClick = onAddSubject,
                        testTag = "plan_empty_add_subject",
                    )
                }
            } else {
                item {
                    SubjectChipRow(
                        subjects = subjects,
                        selectedSubjectId = selectedSubjectId,
                        includeAllOption = true,
                        onSelect = {
                            onSelectSubject(it)
                            selectedDateIso = null
                        },
                    )
                }

                if (targetSubjectForGenerate != null) {
                    item {
                        AiActionPillButton(
                            label = if (filteredBySubject.isEmpty()) {
                                "Generate Plan for ${targetSubjectForGenerate.name}"
                            } else {
                                "Regenerate Plan for ${targetSubjectForGenerate.name}"
                            },
                            enabled = !isAiBusy,
                            onClick = { onGeneratePlan(targetSubjectForGenerate) },
                            testTag = "generate_study_plan_button",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (totalCount > 0) {
                    item {
                        val progress = completedCount.toFloat() / totalCount.toFloat()
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Overall Progress",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "$completedCount / $totalCount completed",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp)),
                                )
                            }
                        }
                    }

                    // Calendar Day Strip
                    item {
                        CalendarDayStrip(
                            datesIso = uniqueDates,
                            tasks = filteredBySubject,
                            selectedDateIso = selectedDateIso,
                            onSelectDate = { date ->
                                selectedDateIso = if (selectedDateIso == date) null else date
                            },
                        )
                    }

                    groupedByDate.forEach { (dateIso, dayTasks) ->
                        item(key = "header_$dateIso") {
                            Text(
                                text = ExamDateUtils.formatIsoDateLabel(dateIso),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(dayTasks, key = { it.id }) { task ->
                            val subjectColor = subjects.firstOrNull { it.id == task.subjectId }
                                ?.let { ExamDateUtils.subjectColor(it.colorIndex) }
                                ?: MaterialTheme.colorScheme.primary
                            StudyTaskCard(
                                task = task,
                                accentColor = subjectColor,
                                onToggle = { onToggleTask(task) },
                            )
                        }
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "No study tasks scheduled yet",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Tap the button above to let Gemini break your syllabus into bite-sized daily goals!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayStrip(
    datesIso: List<String>,
    tasks: List<StudyTask>,
    selectedDateIso: String?,
    onSelectDate: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        datesIso.forEach { dateIso ->
            val (dayLabel, dayNumber) = ExamDateUtils.formatShortDayAndNumber(dateIso)
            val dayTasks = tasks.filter { it.scheduledDateIso == dateIso }
            val allDone = dayTasks.isNotEmpty() && dayTasks.all { it.completed }
            val isSelected = selectedDateIso == dateIso

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                modifier = Modifier
                    .width(62.dp)
                    .clickable { onSelectDate(dateIso) }
                    .testTag("calendar_day_$dateIso"),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = dayNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    allDone -> MaterialTheme.colorScheme.secondary
                                    dayTasks.isNotEmpty() -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyTaskCard(
    task: StudyTask,
    accentColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("study_task_card_${task.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("study_task_checkbox_${task.id}"),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.subjectName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DifficultyBadge(task.difficultyEnum)
                }

                Text(
                    text = task.topicTitle,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )

                if (task.focusTip.isNotBlank()) {
                    Text(
                        text = task.focusTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${task.estimatedMinutes} mins",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
