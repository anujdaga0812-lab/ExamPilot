package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.PracticeQuestion
import com.example.model.QuestionType
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TestDifficulty
import com.example.model.TopicDifficulty
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiRepo"
private const val AI_TIMEOUT_MS = 60_000L

data class GeneratedStudyPlanResult(
    val tasks: List<StudyTask>,
)

data class GeneratedRevisionResult(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val formulas: List<String>,
    val examTips: List<String>,
)

data class GeneratedPracticeTestResult(
    val title: String,
    val questions: List<PracticeQuestion>,
)

data class GradedTestResult(
    val earnedMarks: Long,
    val overallFeedback: String,
    val gradedQuestions: List<PracticeQuestion>,
)

class GeminiAiRepository {

    private val generalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val studyPlanSchema = Schema.obj(
        properties = mapOf(
            "tasks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "topicTitle" to Schema.string(description = "Clear study topic or milestone title"),
                        "focusTip" to Schema.string(description = "Friendly, actionable tip on how to master this topic"),
                        "scheduledDateIso" to Schema.string(description = "Date in YYYY-MM-DD format between today and exam date"),
                        "difficulty" to Schema.enumeration(
                            values = listOf("EASY", "MEDIUM", "HARD"),
                            description = "Estimated topic difficulty"
                        ),
                        "estimatedMinutes" to Schema.integer(description = "Recommended study duration in minutes (15 to 120)"),
                    )
                ),
                description = "Day-by-day study tasks ordered chronologically"
            )
        )
    )

    private val studyPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studyPlanSchema
            }
        )
    }

    private val revisionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise revision sheet title"),
            "summary" to Schema.string(description = "Clear, friendly overview of the material"),
            "keyPoints" to Schema.array(
                items = Schema.string(),
                description = "High-yield key points and core concepts"
            ),
            "formulas" to Schema.array(
                items = Schema.string(),
                description = "Important formulas, equations, laws, or core definitions"
            ),
            "examTips" to Schema.array(
                items = Schema.string(),
                description = "Memory tricks, common mistakes to avoid, and exam tips"
            )
        )
    )

    private val revisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = revisionSchema
            }
        )
    }

    private val practiceTestSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Practice test title"),
            "questions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionId" to Schema.string(description = "Short ID like q1, q2"),
                        "type" to Schema.enumeration(
                            values = listOf("MCQ", "SHORT", "LONG"),
                            description = "Question format"
                        ),
                        "prompt" to Schema.string(description = "Clear exam-style question text"),
                        "options" to Schema.array(
                            items = Schema.string(),
                            description = "4 answer choices for MCQ, or empty array for SHORT and LONG"
                        ),
                        "correctAnswer" to Schema.string(description = "Exact winning option text for MCQ, or model answer for SHORT/LONG"),
                        "maxMarks" to Schema.integer(description = "Marks for this question: 1-2 for MCQ, 3-4 for SHORT, 5-8 for LONG"),
                    )
                )
            )
        )
    )

    private val practiceTestModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = practiceTestSchema
            }
        )
    }

    private val gradingSchema = Schema.obj(
        properties = mapOf(
            "overallFeedback" to Schema.string(description = "Friendly, encouraging summary of strengths and what to review next"),
            "gradedQuestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionId" to Schema.string(description = "Matching questionId"),
                        "earnedMarks" to Schema.integer(description = "Marks awarded from 0 up to maxMarks"),
                        "correct" to Schema.boolean(description = "True if fully correct or mostly accurate"),
                        "feedback" to Schema.string(description = "Clear explanation of mistakes or why the answer earned its marks, plus key takeaway"),
                    )
                )
            )
        )
    )

    private val gradingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = gradingSchema
            }
        )
    }

    suspend fun extractSyllabusFromImage(bitmap: Bitmap, subjectHint: String): String {
        return runAiCall("Image syllabus extraction") {
            val response = generalModel.generateContent(
                content {
                    image(bitmap)
                    text(
                        """
                        You are ExamPilot, a helpful study assistant for students.
                        Extract all syllabus topics, chapters, dates, formulas, and study notes visible in this image.
                        ${if (subjectHint.isNotBlank()) "Subject context: $subjectHint." else ""}
                        Format it cleanly with bullet points and topic headings so it can be used to build a study plan and practice tests.
                        Return only the extracted structured notes/syllabus text.
                        """.trimIndent()
                    )
                }
            )
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) throw IllegalStateException("Could not read text from the selected photo.")
            text
        }
    }

    suspend fun extractSyllabusFromPdfOrText(
        bytes: ByteArray,
        mimeType: String,
        subjectHint: String,
    ): String {
        require(bytes.size <= 15 * 1024 * 1024) {
            "File is larger than 15 MB. Please upload a smaller PDF or paste text."
        }
        return runAiCall("Document syllabus extraction") {
            val response = generalModel.generateContent(
                content {
                    inlineData(bytes, mimeType)
                    text(
                        """
                        You are ExamPilot, a helpful study assistant for students.
                        Extract the syllabus topics, chapters, key concepts, and study notes from this document.
                        ${if (subjectHint.isNotBlank()) "Subject context: $subjectHint." else ""}
                        Organize it clearly with section headings and concise bullet points (keep under 12,000 characters).
                        """.trimIndent()
                    )
                }
            )
            val text = response.text?.trim().orEmpty()
            if (text.isEmpty()) throw IllegalStateException("Could not extract notes from the uploaded file.")
            text.take(28000)
        }
    }

    suspend fun generateStudyPlan(
        subject: Subject,
        todayIso: String,
        examIso: String,
        availableDatesIso: List<String>,
    ): GeneratedStudyPlanResult {
        val dateListText = availableDatesIso.joinToString(", ")
        val prompt = """
            You are ExamPilot, a friendly and motivating academic coach for students from Grade 9 to university.
            Build a realistic day-by-day study plan for:
            - Subject: ${subject.name}
            - Assessment Type: ${subject.examTypeEnum.label}
            - Academic Level: ${subject.academicLevelEnum.label}
            - Today's Date: $todayIso
            - Exam / Due Date: $examIso
            - Valid Dates to Schedule (YYYY-MM-DD): $dateListText

            Syllabus / Notes:
            ${subject.syllabusText.ifBlank { "No detailed syllabus provided; create a logical structured plan for ${subject.name} at ${subject.academicLevelEnum.label} level." }}

            Rules:
            1. Distribute topics logically across the valid dates ($dateListText), tackling harder foundational topics earlier and reserving the final 1-2 days before $examIso for revision and mock practice.
            2. Every scheduledDateIso MUST be one of the valid YYYY-MM-DD dates listed above.
            3. Create between 4 and 24 tasks depending on syllabus breadth and days available.
            4. Give each task a friendly, actionable focusTip and realistic estimatedMinutes (15 to 120).
        """.trimIndent()

        return runAiCall("Study plan generation") {
            val response = studyPlanModel.generateContent(prompt)
            val jsonStr = response.text ?: throw IllegalStateException("AI returned an empty study plan.")
            val json = JSONObject(jsonStr)
            val array = json.getJSONArray("tasks")
            val validDateSet = availableDatesIso.toSet()
            val fallbackDate = availableDatesIso.firstOrNull() ?: todayIso

            val tasks = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val rawDate = item.optString("scheduledDateIso", fallbackDate)
                val safeDate = if (rawDate in validDateSet) rawDate else availableDatesIso[i % availableDatesIso.size]
                StudyTask(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    topicTitle = item.optString("topicTitle", "Study Topic ${i + 1}").take(200),
                    focusTip = item.optString("focusTip", "Review key concepts and work through examples.").take(500),
                    scheduledDateIso = safeDate,
                    difficulty = TopicDifficulty.fromRaw(item.optString("difficulty")).name,
                    estimatedMinutes = item.optLong("estimatedMinutes", 40L).coerceIn(10L, 240L),
                    completed = false,
                    sortOrder = i.toLong(),
                )
            }
            if (tasks.isEmpty()) throw IllegalStateException("No study tasks were generated.")
            GeneratedStudyPlanResult(tasks)
        }
    }

    suspend fun generateQuickRevision(
        subject: Subject,
        focusTopic: String,
    ): GeneratedRevisionResult {
        val prompt = """
            You are ExamPilot, a friendly academic tutor helping a ${subject.academicLevelEnum.label} student revise quickly.
            Create a crisp, high-yield Quick Revision Sheet for:
            - Subject: ${subject.name}
            - Exam Type: ${subject.examTypeEnum.label}
            ${if (focusTopic.isNotBlank()) "- Focus Topic: $focusTopic" else ""}

            Notes / Syllabus:
            ${subject.syllabusText.ifBlank { "General ${subject.name} core syllabus at ${subject.academicLevelEnum.label} level." }}

            Include:
            - A clear, concise summary (2-4 paragraphs max, easy to read on a phone)
            - 5 to 12 bullet keyPoints
            - Key formulas, equations, laws, or crisp definitions in formulas (or key rules/syntax if non-STEM)
            - 3 to 6 friendly examTips and common pitfalls to avoid.
        """.trimIndent()

        return runAiCall("Quick revision generation") {
            val response = revisionModel.generateContent(prompt)
            val jsonStr = response.text ?: throw IllegalStateException("AI returned an empty revision sheet.")
            val json = JSONObject(jsonStr)
            val keyPointsArr = json.optJSONArray("keyPoints")
            val formulasArr = json.optJSONArray("formulas")
            val tipsArr = json.optJSONArray("examTips")

            GeneratedRevisionResult(
                title = json.optString("title", "${subject.name} Quick Revision"),
                summary = json.optString("summary", ""),
                keyPoints = keyPointsArr?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                formulas = formulasArr?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                examTips = tipsArr?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            )
        }
    }

    suspend fun generatePracticeTest(
        subject: Subject,
        difficulty: TestDifficulty,
        questionCount: Int = 6,
    ): GeneratedPracticeTestResult {
        val safeCount = questionCount.coerceIn(3, 10)
        val prompt = """
            You are ExamPilot, creating an exam-style practice test for a ${subject.academicLevelEnum.label} student.
            - Subject: ${subject.name}
            - Exam Type: ${subject.examTypeEnum.label}
            - Difficulty: ${difficulty.label}
            - Total Questions: $safeCount

            Source Notes / Syllabus:
            ${subject.syllabusText.ifBlank { "Core ${subject.name} concepts for ${subject.academicLevelEnum.label}." }}

            Requirements:
            1. Include a balanced mix of MCQ (at least 2-3), SHORT answer (at least 1-2), and LONG answer (at least 1) questions.
            2. For MCQ questions, provide exactly 4 distinct answer choices in `options`, and set `correctAnswer` to match one option verbatim.
            3. For SHORT and LONG questions, set `options` to an empty array [] and put a thorough model answer in `correctAnswer`.
            4. Assign realistic `maxMarks`: 1 or 2 for MCQ, 3 or 4 for SHORT, and 5 to 8 for LONG.
        """.trimIndent()

        return runAiCall("Practice test generation") {
            val response = practiceTestModel.generateContent(prompt)
            val jsonStr = response.text ?: throw IllegalStateException("AI returned an empty practice test.")
            val json = JSONObject(jsonStr)
            val qArray = json.getJSONArray("questions")
            val questions = (0 until qArray.length()).map { idx ->
                val q = qArray.getJSONObject(idx)
                val qType = QuestionType.fromRaw(q.optString("type"))
                val optArr = q.optJSONArray("options")
                val options = if (qType == QuestionType.MCQ && optArr != null) {
                    (0 until optArr.length()).map { optArr.getString(it) }.take(4)
                } else {
                    emptyList()
                }
                PracticeQuestion(
                    questionId = q.optString("questionId", "q${idx + 1}").ifBlank { "q${idx + 1}" },
                    type = qType.name,
                    prompt = q.optString("prompt"),
                    options = options,
                    correctAnswer = q.optString("correctAnswer"),
                    maxMarks = q.optLong("maxMarks", if (qType == QuestionType.MCQ) 2L else if (qType == QuestionType.SHORT) 3L else 5L)
                        .coerceIn(1L, 10L),
                )
            }
            if (questions.isEmpty()) throw IllegalStateException("No practice questions were generated.")
            GeneratedPracticeTestResult(
                title = json.optString("title", "${subject.name} ${difficulty.label} Practice"),
                questions = questions,
            )
        }
    }

    suspend fun gradePracticeTest(
        subject: Subject,
        questionsWithAnswers: List<PracticeQuestion>,
    ): GradedTestResult {
        val questionsJson = questionsWithAnswers.joinToString("\n") { q ->
            """
            - ID: ${q.questionId} | Type: ${q.type} | Max Marks: ${q.maxMarks}
              Question: ${q.prompt}
              Expected/Model Answer: ${q.correctAnswer}
              Student Answer: ${q.userAnswer.ifBlank { "(No answer submitted)" }}
            """.trimIndent()
        }

        val prompt = """
            You are ExamPilot, an encouraging and fair grader for a ${subject.academicLevelEnum.label} student taking a ${subject.name} practice test.
            Grade every question carefully:
            - For MCQ: full marks if the student's answer matches the correct answer; 0 otherwise, and clearly explain why the right option is correct and why the chosen mistake is wrong.
            - For SHORT and LONG answers: award fair partial marks (0 to maxMarks) based on key concepts covered, explain any missing points or misconceptions clearly, and show what earns full marks.
            - Keep your tone warm, friendly, and constructive.

            Questions & Student Answers:
            $questionsJson
        """.trimIndent()

        return runAiCall("Practice test grading") {
            val response = gradingModel.generateContent(prompt)
            val jsonStr = response.text ?: throw IllegalStateException("AI returned an empty grading report.")
            val json = JSONObject(jsonStr)
            val overallFeedback = json.optString("overallFeedback", "Good effort! Review the detailed notes below.")
            val gradedArr = json.getJSONArray("gradedQuestions")

            val gradeMap = mutableMapOf<String, Triple<Long, Boolean, String>>()
            for (i in 0 until gradedArr.length()) {
                val item = gradedArr.getJSONObject(i)
                val qId = item.optString("questionId")
                gradeMap[qId] = Triple(
                    item.optLong("earnedMarks", 0L),
                    item.optBoolean("correct", false),
                    item.optString("feedback", "")
                )
            }

            val updatedQuestions = questionsWithAnswers.map { q ->
                val grade = gradeMap[q.questionId]
                val earned = (grade?.first ?: 0L).coerceIn(0L, q.maxMarks)
                val isRight = grade?.second ?: (earned == q.maxMarks)
                val fb = grade?.third?.ifBlank { null }
                    ?: if (isRight) "Great job!" else "Expected answer: ${q.correctAnswer}"
                q.copy(
                    earnedMarks = earned,
                    correct = isRight,
                    feedback = fb,
                )
            }
            val totalEarned = updatedQuestions.sumOf { it.earnedMarks }
            GradedTestResult(
                earnedMarks = totalEarned,
                overallFeedback = overallFeedback,
                gradedQuestions = updatedQuestions,
            )
        }
    }

    private suspend fun <T> runAiCall(label: String, block: suspend () -> T): T {
        return try {
            withTimeout(AI_TIMEOUT_MS) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$label timed out after ${AI_TIMEOUT_MS}ms", e)
            throw IllegalStateException("$label timed out. Please check your connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$label failed", e)
            throw IllegalStateException(e.localizedMessage ?: "$label failed. Please try again.", e)
        }
    }
}
