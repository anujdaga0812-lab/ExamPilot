package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PracticeQuestion
import com.example.model.PracticeTest
import com.example.model.QuestionType
import com.example.model.Subject
import com.example.model.TestDifficulty
import com.example.model.TestStatus
import com.example.model.UiState
import com.example.ui.components.AiActionPillButton
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SubjectChipRow
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun PracticeTestScreen(
    subjectsState: UiState<List<Subject>>,
    testsState: UiState<List<PracticeTest>>,
    selectedSubjectId: String?,
    activeTestId: String?,
    isAiBusy: Boolean,
    onSelectSubject: (String?) -> Unit,
    onSelectTest: (String?) -> Unit,
    onGenerateTest: (Subject, TestDifficulty, Int) -> Unit,
    onSubmitTest: (PracticeTest, Subject?, Map<String, String>) -> Unit,
    onDeleteTest: (String) -> Unit,
    onAddSubject: () -> Unit,
) {
    var difficulty by remember { mutableStateOf(TestDifficulty.STANDARD) }
    var questionCount by remember { mutableIntStateOf(6) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (subjectsState is UiState.Loading || testsState is UiState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
        val allTests = (testsState as? UiState.Success)?.data.orEmpty()
        val activeTest = activeTestId?.let { id -> allTests.firstOrNull { it.id == id } }

        if (activeTest != null) {
            BackHandler { onSelectTest(null) }
            val parentSubject = subjects.firstOrNull { it.id == activeTest.subjectId }
            ActivePracticeTestView(
                test = activeTest,
                isAiBusy = isAiBusy,
                onBack = { onSelectTest(null) },
                onSubmit = { answers -> onSubmitTest(activeTest, parentSubject, answers) },
            )
            return@Box
        }

        val activeSubject = subjects.firstOrNull { it.id == selectedSubjectId } ?: subjects.firstOrNull()
        val filteredTests = if (selectedSubjectId == null) {
            allTests
        } else {
            allTests.filter { it.subjectId == selectedSubjectId }
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
                        text = "Practice Tests",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "MCQ, short, and long exam questions graded with mistake explanations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (subjects.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "Ready to test yourself?",
                        subtitle = "Add a subject and syllabus notes first so Gemini can write custom exam-style questions for you.",
                        ctaLabel = "Add Subject & Notes",
                        onCtaClick = onAddSubject,
                        testTag = "practice_empty_add_subject",
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
                                    text = "New Practice Test: ${activeSubject.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TestDifficulty.entries.forEach { preset ->
                                        FilterChip(
                                            selected = difficulty == preset,
                                            onClick = { difficulty = preset },
                                            label = { Text(preset.label) },
                                            modifier = Modifier.testTag("test_difficulty_${preset.name}"),
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Questions:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    listOf(5, 7, 10).forEach { count ->
                                        FilterChip(
                                            selected = questionCount == count,
                                            onClick = { questionCount = count },
                                            label = { Text("$count") },
                                            modifier = Modifier.testTag("test_count_$count"),
                                        )
                                    }
                                }

                                AiActionPillButton(
                                    label = "Generate Practice Test",
                                    enabled = !isAiBusy,
                                    onClick = { onGenerateTest(activeSubject, difficulty, questionCount) },
                                    testTag = "generate_practice_test_button",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Your Tests (${filteredTests.size})",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                if (filteredTests.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "No tests yet. Generate one above to test your readiness!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                } else {
                    items(filteredTests, key = { it.id }) { test ->
                        PracticeTestSummaryCard(
                            test = test,
                            onOpen = { onSelectTest(test.id) },
                            onDelete = { onDeleteTest(test.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeTestSummaryCard(
    test: PracticeTest,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isGraded = test.statusEnum == TestStatus.GRADED
    val pct = if (test.totalMarks > 0) ((test.earnedMarks * 100) / test.totalMarks) else 0L

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("practice_test_card_${test.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = test.subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = test.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${test.questions.size} questions • ${test.difficultyEnum.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    color = if (isGraded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = if (isGraded) "${test.earnedMarks}/${test.totalMarks} ($pct%)" else "Resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGraded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_test_${test.id}"),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete test")
                }
            }
        }
    }
}

@Composable
private fun ActivePracticeTestView(
    test: PracticeTest,
    isAiBusy: Boolean,
    onBack: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit,
) {
    val isGraded = test.statusEnum == TestStatus.GRADED
    val answers = remember(test.id) {
        mutableStateMapOf<String, String>().apply {
            test.questions.forEach { q -> put(q.questionId, q.userAnswer) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("practice_test_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("All Tests")
                }
                Text(
                    text = "${test.totalMarks} Marks",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = test.subjectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = test.title,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }

        if (isGraded) {
            item {
                val pct = if (test.totalMarks > 0) ((test.earnedMarks * 100) / test.totalMarks) else 0L
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("graded_score_banner"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Score Summary",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "${test.earnedMarks} / ${test.totalMarks} ($pct%)",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = test.overallFeedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        itemsIndexed(test.questions, key = { _, q -> q.questionId }) { idx, q ->
            QuestionCard(
                index = idx + 1,
                question = q,
                isGraded = isGraded,
                currentAnswer = answers[q.questionId].orEmpty(),
                onAnswerChange = { newAns -> answers[q.questionId] = newAns },
            )
        }

        if (!isGraded) {
            item {
                Button(
                    onClick = { onSubmit(answers.toMap()) },
                    enabled = !isAiBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_practice_test_button"),
                ) {
                    Text(
                        text = if (isAiBusy) "Grading..." else "Submit & Grade with Gemini",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    question: PracticeQuestion,
    isGraded: Boolean,
    currentAnswer: String,
    onAnswerChange: (String) -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = "Q$index • ${question.typeEnum.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (isGraded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (question.correct) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (question.correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "${question.earnedMarks}/${question.maxMarks}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (question.correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        text = "${question.maxMarks} marks",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (question.typeEnum == QuestionType.MCQ && question.options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEach { option ->
                        val isSelected = currentAnswer == option
                        val isCorrectOpt = isGraded && option == question.correctAnswer
                        val borderColor = when {
                            isGraded && isCorrectOpt -> MaterialTheme.colorScheme.secondary
                            isGraded && isSelected && !question.correct -> MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.5.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isGraded) { onAnswerChange(option) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (!isGraded) { { onAnswerChange(option) } } else null,
                                    enabled = !isGraded,
                                )
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = currentAnswer,
                    onValueChange = { if (!isGraded && it.length <= 3900) onAnswerChange(it) },
                    readOnly = isGraded,
                    label = { Text(if (isGraded) "Your Answer" else "Write your answer") },
                    minLines = if (question.typeEnum == QuestionType.LONG) 4 else 2,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("answer_input_${question.questionId}"),
                )
            }

            if (isGraded) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Explanation & Mistake Review",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = question.feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!question.correct || question.typeEnum != QuestionType.MCQ) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Model Answer: ${question.correctAnswer}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
