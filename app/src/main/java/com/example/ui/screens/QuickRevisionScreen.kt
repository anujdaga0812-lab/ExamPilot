package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.RevisionSheet
import com.example.model.Subject
import com.example.model.UiState
import com.example.ui.components.AiActionPillButton
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExamDateUtils
import com.example.ui.components.SubjectChipRow
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun QuickRevisionScreen(
    subjectsState: UiState<List<Subject>>,
    revisionsState: UiState<List<RevisionSheet>>,
    selectedSubjectId: String?,
    isAiBusy: Boolean,
    onSelectSubject: (String?) -> Unit,
    onGenerateRevision: (Subject, String) -> Unit,
    onDeleteRevision: (String) -> Unit,
    onAddSubject: () -> Unit,
) {
    var focusTopic by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (subjectsState is UiState.Loading || revisionsState is UiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
        val allRevisions = (revisionsState as? UiState.Success)?.data.orEmpty()
        val activeSubject = subjects.firstOrNull { it.id == selectedSubjectId } ?: subjects.firstOrNull()
        val filteredRevisions = if (selectedSubjectId == null) {
            allRevisions
        } else {
            allRevisions.filter { it.subjectId == selectedSubjectId }
        }

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
                        text = "Quick Revision",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "Distill notes into short summaries, key points, and essential formulas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Add notes to revise fast",
                        subtitle = "Once you add a subject and paste or upload notes, Gemini turns them into crisp cheat-sheets here.",
                        ctaLabel = "Add Subject & Notes",
                        onCtaClick = onAddSubject,
                        testTag = "revise_empty_add_subject",
                    )
                }
            } else {
                item {
                    SubjectChipRow(
                        subjects = subjects,
                        selectedSubjectId = selectedSubjectId,
                        includeAllOption = true,
                        onSelect = onSelectSubject,
                    )
                }

                if (activeSubject != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "Create Revision Sheet for ${activeSubject.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedTextField(
                                    value = focusTopic,
                                    onValueChange = { if (it.length <= 120) focusTopic = it },
                                    label = { Text("Focus topic (optional)") },
                                    placeholder = { Text("e.g., Chapter 4 Thermodynamics or full syllabus") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("revision_focus_input"),
                                )
                                AiActionPillButton(
                                    label = "Generate Quick Revision",
                                    enabled = !isAiBusy,
                                    onClick = {
                                        onGenerateRevision(activeSubject, focusTopic.trim())
                                    },
                                    testTag = "generate_revision_button",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                if (filteredRevisions.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "No revision sheets generated yet. Tap \"Generate Quick Revision\" above!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    items(filteredRevisions, key = { it.id }) { sheet ->
                        val accent = subjects.firstOrNull { it.id == sheet.subjectId }
                            ?.let { ExamDateUtils.subjectColor(it.colorIndex) }
                            ?: MaterialTheme.colorScheme.primary
                        RevisionSheetCard(
                            sheet = sheet,
                            accentColor = accent,
                            onDelete = { onDeleteRevision(sheet.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionSheetCard(
    sheet: RevisionSheet,
    accentColor: androidx.compose.ui.graphics.Color,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("revision_card_${sheet.id}"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sheet.subjectName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                    )
                    Text(
                        text = sheet.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_revision_${sheet.id}"),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete revision sheet")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = sheet.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (sheet.keyPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Key Points",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    sheet.keyPoints.forEach { point ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (sheet.formulas.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Functions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Formulas & Key Definitions",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        sheet.formulas.forEach { formula ->
                            Surface(
                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = formula,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (sheet.examTips.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Exam Tips",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        sheet.examTips.forEach { tip ->
                            Text(
                                text = "-> $tip",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
