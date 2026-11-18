package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ExamType(val label: String) {
    ASSIGNMENT("Assignment"),
    INTERNAL("Internal Exam"),
    SEMESTER("Semester Exam"),
    ;

    companion object {
        fun fromRaw(raw: String?): ExamType =
            entries.firstOrNull { it.name == raw } ?: SEMESTER
    }
}

enum class AcademicLevel(val label: String) {
    GRADE_9_10("Grade 9–10"),
    GRADE_11_12("Grade 11–12"),
    UNIVERSITY("University"),
    ;

    companion object {
        fun fromRaw(raw: String?): AcademicLevel =
            entries.firstOrNull { it.name == raw } ?: GRADE_11_12
    }
}

enum class TopicDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    ;

    companion object {
        fun fromRaw(raw: String?): TopicDifficulty =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TestDifficulty(val label: String) {
    STANDARD("Standard"),
    CHALLENGE("Challenge"),
    ;

    companion object {
        fun fromRaw(raw: String?): TestDifficulty =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

enum class TestStatus {
    IN_PROGRESS,
    GRADED,
    ;

    companion object {
        fun fromRaw(raw: String?): TestStatus =
            entries.firstOrNull { it.name == raw } ?: IN_PROGRESS
    }
}

enum class QuestionType(val label: String) {
    MCQ("Multiple Choice"),
    SHORT("Short Answer"),
    LONG("Long Answer"),
    ;

    companion object {
        fun fromRaw(raw: String?): QuestionType =
            entries.firstOrNull { it.name == raw } ?: SHORT
    }
}

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val examType: String = ExamType.SEMESTER.name,
    val academicLevel: String = AcademicLevel.GRADE_11_12.name,
    val examDateEpochMillis: Long = 0L,
    val syllabusText: String = "",
    val colorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val examTypeEnum: ExamType get() = ExamType.fromRaw(examType)
    val academicLevelEnum: AcademicLevel get() = AcademicLevel.fromRaw(academicLevel)
}

@Keep
@IgnoreExtraProperties
data class StudyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicTitle: String = "",
    val focusTip: String = "",
    val scheduledDateIso: String = "",
    val difficulty: String = TopicDifficulty.MEDIUM.name,
    val estimatedMinutes: Long = 30L,
    val completed: Boolean = false,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val difficultyEnum: TopicDifficulty get() = TopicDifficulty.fromRaw(difficulty)
}

@Keep
@IgnoreExtraProperties
data class RevisionSheet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String = "",
    val summary: String = "",
    val keyPoints: List<String> = emptyList(),
    val formulas: List<String> = emptyList(),
    val examTips: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PracticeQuestion(
    val questionId: String = "",
    val type: String = QuestionType.MCQ.name,
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val maxMarks: Long = 1L,
    val userAnswer: String = "",
    val earnedMarks: Long = 0L,
    val feedback: String = "",
    val correct: Boolean = false,
) {
    val typeEnum: QuestionType get() = QuestionType.fromRaw(type)

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "questionId" to questionId.take(64),
        "type" to typeEnum.name,
        "prompt" to prompt.take(2000),
        "options" to options.take(6).map { it.take(300) },
        "correctAnswer" to correctAnswer.take(2000),
        "maxMarks" to maxMarks.coerceIn(1L, 50L),
        "userAnswer" to userAnswer.take(4000),
        "earnedMarks" to earnedMarks.coerceIn(0L, maxMarks.coerceIn(1L, 50L)),
        "feedback" to feedback.take(2000),
        "correct" to correct,
    )
}

@Keep
@IgnoreExtraProperties
data class PracticeTest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String = "",
    val difficulty: String = TestDifficulty.STANDARD.name,
    val status: String = TestStatus.IN_PROGRESS.name,
    val totalMarks: Long = 0L,
    val earnedMarks: Long = 0L,
    val overallFeedback: String = "",
    val questions: List<PracticeQuestion> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val difficultyEnum: TestDifficulty get() = TestDifficulty.fromRaw(difficulty)
    val statusEnum: TestStatus get() = TestStatus.fromRaw(status)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskStatus {
    data object Idle : AiTaskStatus
    data class Working(val label: String) : AiTaskStatus
    data class Success(val message: String) : AiTaskStatus
    data class Error(val message: String) : AiTaskStatus
}
