package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AcademicLevel
import com.example.model.AiTaskStatus
import com.example.model.ExamType
import com.example.model.Subject
import com.example.ui.components.AiProgressBanner
import com.example.ui.components.ExamDateUtils
import com.example.ui.theme.SubjectAccentColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusInputScreen(
    editingSubject: Subject?,
    aiStatus: AiTaskStatus,
    extractedSyllabusPreview: String?,
    onClearExtractedPreview: () -> Unit,
    onDismissAiStatus: () -> Unit,
    onExtractFromPhoto: (Bitmap, String) -> Unit,
    onExtractFromDocument: (ByteArray, String, String) -> Unit,
    onSaveSubject: (
        existingId: String?,
        name: String,
        examType: ExamType,
        academicLevel: AcademicLevel,
        examDateEpochMillis: Long,
        syllabusText: String,
        colorIndex: Long,
        generatePlanImmediately: Boolean,
    ) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultExamDate = remember { ExamDateUtils.defaultExamDateMillis(10) }

    var name by remember(editingSubject) { mutableStateOf(editingSubject?.name.orEmpty()) }
    var examType by remember(editingSubject) {
        mutableStateOf(editingSubject?.examTypeEnum ?: ExamType.SEMESTER)
    }
    var academicLevel by remember(editingSubject) {
        mutableStateOf(editingSubject?.academicLevelEnum ?: AcademicLevel.GRADE_11_12)
    }
    var examDateMillis by remember(editingSubject) {
        mutableLongStateOf(editingSubject?.examDateEpochMillis?.takeIf { it > 0L } ?: defaultExamDate)
    }
    var syllabusText by remember(editingSubject) {
        mutableStateOf(editingSubject?.syllabusText.orEmpty())
    }
    var colorIndex by remember(editingSubject) {
        mutableLongStateOf(editingSubject?.colorIndex ?: 0L)
    }
    var generatePlanNow by remember(editingSubject) {
        mutableStateOf(editingSubject == null)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(extractedSyllabusPreview) {
        if (!extractedSyllabusPreview.isNullOrBlank()) {
            syllabusText = if (syllabusText.isBlank()) {
                extractedSyllabusPreview
            } else {
                "$syllabusText\n\n$extractedSyllabusPreview"
            }
            onClearExtractedPreview()
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            fileError = null
            scope.launch {
                val bitmap = decodeScaledBitmap(context, uri)
                if (bitmap != null) {
                    onExtractFromPhoto(bitmap, name)
                } else {
                    fileError = "Could not open the selected image."
                }
            }
        }
    }

    // Zero-permission SAF Document Picker for PDF / Image / Text files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            fileError = null
            scope.launch {
                val loaded = readUriBytes(context, uri)
                if (loaded == null) {
                    fileError = "Unable to read the selected file."
                } else if (loaded.first.size > 15 * 1024 * 1024) {
                    fileError = "File exceeds 15 MB limit. Try a smaller PDF or photo."
                } else {
                    onExtractFromDocument(loaded.first, loaded.second, name)
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = examDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { examDateMillis = it }
                        showDatePicker = false
                    }
                ) {
                    Text("Set Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    val isBusy = aiStatus is AiTaskStatus.Working

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editingSubject == null) "Add Subject & Syllabus" else "Edit Subject")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("syllabus_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AiProgressBanner(status = aiStatus, onDismiss = onDismissAiStatus)

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text("Subject or Course Name") },
                    placeholder = { Text("e.g., Organic Chemistry, Calculus II, World History") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subject_name_input"),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assessment Type",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ExamType.entries.forEach { type ->
                            FilterChip(
                                selected = examType == type,
                                onClick = { examType = type },
                                label = { Text(type.label) },
                                modifier = Modifier.testTag("exam_type_${type.name}"),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Academic Level",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AcademicLevel.entries.forEach { level ->
                            FilterChip(
                                selected = academicLevel == level,
                                onClick = { academicLevel = level },
                                label = { Text(level.label) },
                                modifier = Modifier.testTag("academic_level_${level.name}"),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("exam_date_picker_button"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Exam / Submission Date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = ExamDateUtils.formatEpochDate(examDateMillis),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Select exam date",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Subject Color",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SubjectAccentColors.forEachIndexed { idx, color ->
                            val selected = colorIndex == idx.toLong()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { colorIndex = idx.toLong() }
                                    .testTag("color_swatch_$idx")
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Syllabus or Notes",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Paste your syllabus below, or snap/upload a photo or PDF and let Gemini extract the topics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("upload_photo_button"),
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Photo")
                        }
                        OutlinedButton(
                            onClick = {
                                documentPickerLauncher.launch(
                                    arrayOf("application/pdf", "image/*", "text/plain")
                                )
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("upload_pdf_button"),
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF / File")
                        }
                    }
                    if (fileError != null) {
                        Text(
                            text = fileError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = syllabusText,
                        onValueChange = { if (it.length <= 29500) syllabusText = it },
                        label = { Text("Syllabus / Study Notes") },
                        placeholder = { Text("Paste chapters, unit objectives, formulas, or lecture notes here...") },
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("syllabus_text_input"),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = "Auto-generate study plan",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "Build a day-by-day checklist right after saving",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = generatePlanNow,
                            onCheckedChange = { generatePlanNow = it },
                            modifier = Modifier.testTag("auto_plan_switch"),
                        )
                    }
                }

                Button(
                    onClick = {
                        onSaveSubject(
                            editingSubject?.id,
                            name,
                            examType,
                            academicLevel,
                            examDateMillis,
                            syllabusText,
                            colorIndex,
                            generatePlanNow,
                        )
                    },
                    enabled = name.isNotBlank() && !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_subject_button"),
                ) {
                    Text(
                        text = if (generatePlanNow) "Save & Generate Study Plan" else "Save Subject",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private suspend fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = 1600
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}

private suspend fun readUriBytes(context: Context, uri: Uri): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
    try {
        val mime = context.contentResolver.getType(uri) ?: "application/pdf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        bytes to mime
    } catch (_: Exception) {
        null
    }
}
