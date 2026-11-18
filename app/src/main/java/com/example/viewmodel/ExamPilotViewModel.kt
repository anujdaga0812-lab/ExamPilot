package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExamPilotRepository
import com.example.data.GeminiAiRepository
import com.example.model.AcademicLevel
import com.example.model.AiTaskStatus
import com.example.model.ExamType
import com.example.model.PracticeQuestion
import com.example.model.PracticeTest
import com.example.model.RevisionSheet
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TestDifficulty
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExamPilotViewModel(
    private val repository: ExamPilotRepository,
    private val aiRepository: GeminiAiRepository,
    private val currentUserId: String,
) : ViewModel() {

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        repository.observeSubjects(currentUserId)
            .map<List<Subject>, UiState<List<Subject>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "subjects")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val studyTasksState: StateFlow<UiState<List<StudyTask>>> =
        repository.observeStudyTasks(currentUserId)
            .map<List<StudyTask>, UiState<List<StudyTask>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "study plan")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val revisionsState: StateFlow<UiState<List<RevisionSheet>>> =
        repository.observeRevisions(currentUserId)
            .map<List<RevisionSheet>, UiState<List<RevisionSheet>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "revision sheets")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val practiceTestsState: StateFlow<UiState<List<PracticeTest>>> =
        repository.observePracticeTests(currentUserId)
            .map<List<PracticeTest>, UiState<List<PracticeTest>>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, "practice tests")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val _aiStatus = MutableStateFlow<AiTaskStatus>(AiTaskStatus.Idle)
    val aiStatus: StateFlow<AiTaskStatus> = _aiStatus.asStateFlow()

    private val _extractedSyllabusPreview = MutableStateFlow<String?>(null)
    val extractedSyllabusPreview: StateFlow<String?> = _extractedSyllabusPreview.asStateFlow()

    private val _activeTestId = MutableStateFlow<String?>(null)
    val activeTestId: StateFlow<String?> = _activeTestId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearAiStatus() {
        _aiStatus.value = AiTaskStatus.Idle
    }

    fun clearExtractedSyllabus() {
        _extractedSyllabusPreview.value = null
    }

    fun selectPracticeTest(testId: String?) {
        _activeTestId.value = testId
    }

    fun extractSyllabusFromPhoto(bitmap: Bitmap, subjectHint: String) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Reading notes from your photo...")
            try {
                val extracted = aiRepository.extractSyllabusFromImage(bitmap, subjectHint)
                _extractedSyllabusPreview.value = extracted
                _aiStatus.value = AiTaskStatus.Success("Notes extracted from photo!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiStatus.value = AiTaskStatus.Error(e.message ?: "Could not extract photo notes.")
            }
        }
    }

    fun extractSyllabusFromDocument(bytes: ByteArray, mimeType: String, subjectHint: String) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Reading your uploaded file...")
            try {
                val extracted = aiRepository.extractSyllabusFromPdfOrText(bytes, mimeType, subjectHint)
                _extractedSyllabusPreview.value = extracted
                _aiStatus.value = AiTaskStatus.Success("Syllabus extracted from document!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiStatus.value = AiTaskStatus.Error(e.message ?: "Could not extract document notes.")
            }
        }
    }

    fun saveSubjectAndOptionallyPlan(
        existingId: String?,
        name: String,
        examType: ExamType,
        academicLevel: AcademicLevel,
        examDateEpochMillis: Long,
        syllabusText: String,
        colorIndex: Long,
        generatePlanImmediately: Boolean,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working(
                if (generatePlanImmediately) "Saving subject & building your study plan..." else "Saving subject..."
            )
            val saveResult = repository.saveSubject(
                userId = currentUserId,
                existingId = existingId,
                name = name,
                examType = examType,
                academicLevel = academicLevel,
                examDateEpochMillis = examDateEpochMillis,
                syllabusText = syllabusText,
                colorIndex = colorIndex,
            )
            saveResult.onFailure { err ->
                _aiStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Failed to save subject.")
                return@launch
            }
            val savedId = saveResult.getOrThrow()

            if (generatePlanImmediately) {
                val subject = Subject(
                    id = savedId,
                    userId = currentUserId,
                    name = name.trim(),
                    examType = examType.name,
                    academicLevel = academicLevel.name,
                    examDateEpochMillis = examDateEpochMillis,
                    syllabusText = syllabusText.trim(),
                    colorIndex = colorIndex,
                )
                generateStudyPlanInternal(subject)
            } else {
                _aiStatus.value = AiTaskStatus.Success("Subject saved!")
                _snackbarMessage.value = "Saved \"$name\"!"
            }
            onSaved()
        }
    }

    fun generateStudyPlan(subject: Subject) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Crafting day-by-day plan for ${subject.name}...")
            generateStudyPlanInternal(subject)
        }
    }

    private suspend fun generateStudyPlanInternal(subject: Subject) {
        try {
            val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayCal = startOfDayCalendar(System.currentTimeMillis())
            val examCal = startOfDayCalendar(subject.examDateEpochMillis)
            val endCal = if (examCal.before(todayCal)) todayCal else examCal
            val availableDates = buildAvailableDates(todayCal, endCal, isoFmt)

            val generated = aiRepository.generateStudyPlan(
                subject = subject,
                todayIso = isoFmt.format(todayCal.time),
                examIso = isoFmt.format(endCal.time),
                availableDatesIso = availableDates,
            )
            val saveRes = repository.replaceStudyPlanForSubject(currentUserId, subject, generated.tasks)
            saveRes.onFailure { err ->
                _aiStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Could not save generated plan.")
                return
            }
            _aiStatus.value = AiTaskStatus.Success("Study plan ready (${generated.tasks.size} tasks)!")
            _snackbarMessage.value = "Study plan ready for ${subject.name}!"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _aiStatus.value = AiTaskStatus.Error(e.message ?: "Failed to generate study plan.")
        }
    }

    private fun startOfDayCalendar(millis: Long): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun buildAvailableDates(
        start: Calendar,
        end: Calendar,
        isoFmt: SimpleDateFormat,
    ): List<String> {
        val totalDays = ((end.timeInMillis - start.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
        return if (totalDays <= 20) {
            (0..totalDays).map { offset ->
                val c = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                isoFmt.format(Date(c.timeInMillis))
            }
        } else {
            val step = totalDays / 20.0
            (0..20).map { idx ->
                val c = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, (idx * step).toInt()) }
                isoFmt.format(Date(c.timeInMillis))
            }.distinct()
        }
    }

    fun toggleTaskCompleted(task: StudyTask) {
        viewModelScope.launch {
            val res = repository.toggleStudyTaskCompleted(
                userId = currentUserId,
                taskId = task.id,
                completed = !task.completed,
            )
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not update task."
            }
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            val res = repository.deleteSubjectAndRelated(currentUserId, subjectId)
            res.onSuccess {
                _snackbarMessage.value = "Subject removed."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete subject."
            }
        }
    }

    fun generateQuickRevision(subject: Subject, focusTopic: String) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Distilling ${subject.name} notes into quick revision...")
            try {
                val result = aiRepository.generateQuickRevision(subject, focusTopic)
                val saveRes = repository.saveRevisionSheet(
                    userId = currentUserId,
                    subject = subject,
                    title = result.title,
                    summary = result.summary,
                    keyPoints = result.keyPoints,
                    formulas = result.formulas,
                    examTips = result.examTips,
                )
                saveRes.onFailure { err ->
                    _aiStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Failed to save revision sheet.")
                    return@launch
                }
                _aiStatus.value = AiTaskStatus.Success("Quick revision sheet saved!")
                _snackbarMessage.value = "Quick revision ready!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiStatus.value = AiTaskStatus.Error(e.message ?: "Failed to generate quick revision.")
            }
        }
    }

    fun deleteRevision(revisionId: String) {
        viewModelScope.launch {
            repository.deleteRevisionSheet(currentUserId, revisionId)
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not delete sheet." }
        }
    }

    fun generatePracticeTest(subject: Subject, difficulty: TestDifficulty, questionCount: Int) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Writing exam-style questions for ${subject.name}...")
            try {
                val generated = aiRepository.generatePracticeTest(subject, difficulty, questionCount)
                val createRes = repository.createPracticeTest(
                    userId = currentUserId,
                    subject = subject,
                    title = generated.title,
                    difficulty = difficulty,
                    questions = generated.questions,
                )
                createRes.onFailure { err ->
                    _aiStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Failed to save practice test.")
                    return@launch
                }
                val newTestId = createRes.getOrThrow()
                _activeTestId.value = newTestId
                _aiStatus.value = AiTaskStatus.Success("Practice test ready! Good luck!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiStatus.value = AiTaskStatus.Error(e.message ?: "Failed to generate practice test.")
            }
        }
    }

    fun submitAndGradeTest(
        test: PracticeTest,
        subject: Subject?,
        answersByQuestionId: Map<String, String>,
    ) {
        viewModelScope.launch {
            _aiStatus.value = AiTaskStatus.Working("Grading your answers & preparing explanations...")
            try {
                val questionsWithAnswers = test.questions.map { q ->
                    q.copy(userAnswer = answersByQuestionId[q.questionId]?.trim().orEmpty())
                }
                val subjectForGrading = subject ?: Subject(
                    id = test.subjectId,
                    userId = currentUserId,
                    name = test.subjectName,
                )
                val graded = aiRepository.gradePracticeTest(subjectForGrading, questionsWithAnswers)
                val saveRes = repository.submitGradedTest(
                    userId = currentUserId,
                    testId = test.id,
                    totalMarks = test.totalMarks,
                    earnedMarks = graded.earnedMarks,
                    overallFeedback = graded.overallFeedback,
                    gradedQuestions = graded.gradedQuestions,
                )
                saveRes.onFailure { err ->
                    _aiStatus.value = AiTaskStatus.Error(err.localizedMessage ?: "Could not save graded results.")
                    return@launch
                }
                _aiStatus.value = AiTaskStatus.Success("Test graded: ${graded.earnedMarks}/${test.totalMarks}!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiStatus.value = AiTaskStatus.Error(e.message ?: "Failed to grade practice test.")
            }
        }
    }

    fun deletePracticeTest(testId: String) {
        viewModelScope.launch {
            if (_activeTestId.value == testId) _activeTestId.value = null
            repository.deletePracticeTest(currentUserId, testId)
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not delete test." }
        }
    }

    private fun unwrapFirestoreError(error: Throwable, resourceName: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $resourceName", rootError)
        return UiState.Error(rootError.localizedMessage ?: "Unable to load $resourceName.")
    }

    private companion object {
        const val TAG = "ExamPilotVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
