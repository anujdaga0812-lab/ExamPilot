package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.UiState
import com.example.ui.components.DifficultyBadge
import com.example.ui.components.ExamCountdownCard
import com.example.ui.components.ExamDateUtils
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    userName: String?,
    subjectsState: UiState<List<Subject>>,
    tasksState: UiState<List<StudyTask>>,
    isAiBusy: Boolean,
    onAddSubject: () -> Unit,
    onEditSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit,
    onGeneratePlan: (Subject) -> Unit,
    onToggleTask: (StudyTask) -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToRevise: (String?) -> Unit,
    onNavigateToPractice: (String?) -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    var subjectPendingDelete by remember { mutableStateOf<Subject?>(null) }

    if (subjectPendingDelete != null) {
        val target = subjectPendingDelete!!
        AlertDialog(
            onDismissRequest = { subjectPendingDelete = null },
            title = { Text("Remove ${target.name}?") },
            text = { Text("This deletes the subject along with its study tasks, revision sheets, and practice tests.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSubject(target)
                        subjectPendingDelete = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectPendingDelete = null }) {
                    Text("Keep")
                }
            },
        )
    }

    val firstName = userName?.trim()?.substringBefore(" ")?.takeIf { it.isNotEmpty() } ?: "Pilot"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (subjectsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = subjectsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is UiState.Success -> {
                val subjects = subjectsState.data
                val allTasks = (tasksState as? UiState.Success)?.data.orEmpty()
                val nextSubject = subjects.firstOrNull { ExamDateUtils.daysUntilEpoch(it.examDateEpochMillis) >= 0L }
                    ?: subjects.firstOrNull()
                val nextSubjectTasks = nextSubject?.let { s -> allTasks.filter { it.subjectId == s.id } }.orEmpty()
                val todayIso = ExamDateUtils.todayIso()
                val todayTasks = allTasks.filter { it.scheduledDateIso <= todayIso && !it.completed }
                    .ifEmpty { allTasks.filter { it.scheduledDateIso == todayIso } }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Hey $firstName,",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Your Study Cockpit",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    item {
                        ExamCountdownCard(
                            nextSubject = nextSubject,
                            nowMillis = nowMillis,
                            completedTasks = nextSubjectTasks.count { it.completed },
                            totalTasks = nextSubjectTasks.size,
                            onAddSubject = onAddSubject,
                            onOpenPlan = onNavigateToPlan,
                        )
                    }

                    if (subjects.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = { onNavigateToRevise(nextSubject?.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_quick_revise_button"),
                                ) {
                                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Quick Revise", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FilledTonalButton(
                                    onClick = { onNavigateToPractice(nextSubject?.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("home_quick_practice_button"),
                                ) {
                                    Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Practice Test", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        if (todayTasks.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Today's Focus",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    TextButton(onClick = onNavigateToPlan) {
                                        Text("Full Plan")
                                    }
                                }
                            }
                            items(todayTasks.take(4), key = { it.id }) { task ->
                                HomeTaskCompactRow(
                                    task = task,
                                    onToggle = { onToggleTask(task) },
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Your Subjects (${subjects.size})",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        items(subjects, key = { it.id }) { subject ->
                            val subTasks = allTasks.filter { it.subjectId == subject.id }
                            SubjectDashboardCard(
                                subject = subject,
                                completedCount = subTasks.count { it.completed },
                                totalCount = subTasks.size,
                                isAiBusy = isAiBusy,
                                onEdit = { onEditSubject(subject) },
                                onDelete = { subjectPendingDelete = subject },
                                onGeneratePlan = { onGeneratePlan(subject) },
                                onRevise = { onNavigateToRevise(subject.id) },
                                onPractice = { onNavigateToPractice(subject.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTaskCompactRow(
    task: StudyTask,
    onToggle: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("home_task_card_${task.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.topicTitle,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.subjectName} • ${task.estimatedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DifficultyBadge(task.difficultyEnum)
        }
    }
}

@Composable
private fun SubjectDashboardCard(
    subject: Subject,
    completedCount: Int,
    totalCount: Int,
    isAiBusy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onGeneratePlan: () -> Unit,
    onRevise: () -> Unit,
    onPractice: () -> Unit,
) {
    val accent = ExamDateUtils.subjectColor(subject.colorIndex)
    val daysLeft = ExamDateUtils.daysUntilEpoch(subject.examDateEpochMillis)
    val daysLabel = when {
        daysLeft < 0L -> "Passed"
        daysLeft == 0L -> "Today!"
        daysLeft == 1L -> "1 day left"
        else -> "$daysLeft days left"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subject_card_${subject.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Column {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${subject.examTypeEnum.label} • ${ExamDateUtils.formatEpochDate(subject.examDateEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = daysLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (subject.syllabusText.isNotBlank()) {
                Text(
                    text = subject.syllabusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_subject_${subject.id}"),
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit subject")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_subject_${subject.id}"),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete subject")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onRevise) {
                        Text("Revise")
                    }
                    TextButton(onClick = onPractice) {
                        Text("Quiz")
                    }
                    FilledTonalButton(
                        onClick = onGeneratePlan,
                        enabled = !isAiBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("plan_subject_${subject.id}"),
                    ) {
                        Icon(
                            imageVector = if (totalCount == 0) Icons.Rounded.AutoAwesome else Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (totalCount == 0) "Build Plan" else "$completedCount/$totalCount")
                    }
                }
            }
        }
    }
}
