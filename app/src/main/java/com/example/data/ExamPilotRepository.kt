package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AcademicLevel
import com.example.model.ExamType
import com.example.model.PracticeQuestion
import com.example.model.PracticeTest
import com.example.model.RevisionSheet
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.TestDifficulty
import com.example.model.TestStatus
import com.example.model.TopicDifficulty
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ExamPilotRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google before saving your study data.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in account changed; refusing cross-user access." }
        }
        return uid
    }

    fun observeSubjects(userId: String): Flow<List<Subject>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/subjects"
        emitAll(
            db.collection("users").document(uid).collection("subjects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Subject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.examDateEpochMillis }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeStudyTasks(userId: String): Flow<List<StudyTask>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/studyTasks"
        emitAll(
            db.collection("users").document(uid).collection("studyTasks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<StudyTask> { it.scheduledDateIso }.thenBy { it.sortOrder })
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeRevisions(userId: String): Flow<List<RevisionSheet>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/revisions"
        emitAll(
            db.collection("users").document(uid).collection("revisions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(RevisionSheet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observePracticeTests(userId: String): Flow<List<PracticeTest>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/practiceTests"
        emitAll(
            db.collection("users").document(uid).collection("practiceTests")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PracticeTest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    suspend fun saveSubject(
        userId: String,
        existingId: String?,
        name: String,
        examType: ExamType,
        academicLevel: AcademicLevel,
        examDateEpochMillis: Long,
        syllabusText: String,
        colorIndex: Long,
    ): Result<String> {
        var docPath = "users/$userId/subjects"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim().take(100)
            require(cleanName.isNotEmpty()) { "Please enter a subject name." }
            require(examDateEpochMillis > 0L) { "Please pick an exam or due date." }
            val cleanSyllabus = syllabusText.trim().take(30000)
            val safeColor = colorIndex.coerceIn(0L, 5L)

            val subjectsCol = db.collection("users").document(uid).collection("subjects")
            if (existingId.isNullOrBlank()) {
                val docRef = subjectsCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "examType" to examType.name,
                    "academicLevel" to academicLevel.name,
                    "examDateEpochMillis" to examDateEpochMillis,
                    "syllabusText" to cleanSyllabus,
                    "colorIndex" to safeColor,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = subjectsCol.document(existingId)
                docPath = docRef.path
                val updatePayload = mapOf(
                    "name" to cleanName,
                    "examType" to examType.name,
                    "academicLevel" to academicLevel.name,
                    "examDateEpochMillis" to examDateEpochMillis,
                    "syllabusText" to cleanSyllabus,
                    "colorIndex" to safeColor,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSubjectAndRelated(userId: String, subjectId: String): Result<Unit> {
        val docPath = "users/$userId/subjects/$subjectId"
        return try {
            val uid = requireUserId(userId)
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            val userDoc = db.collection("users").document(uid)

            val taskSnap = userDoc.collection("studyTasks").whereEqualTo("subjectId", subjectId).get().await()
            val revSnap = userDoc.collection("revisions").whereEqualTo("subjectId", subjectId).get().await()
            val testSnap = userDoc.collection("practiceTests").whereEqualTo("subjectId", subjectId).get().await()

            val allRefs = taskSnap.documents.map { it.reference } +
                revSnap.documents.map { it.reference } +
                testSnap.documents.map { it.reference } +
                userDoc.collection("subjects").document(subjectId)

            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun replaceStudyPlanForSubject(
        userId: String,
        subject: Subject,
        tasks: List<StudyTask>,
    ): Result<Unit> {
        val path = "users/$userId/studyTasks"
        return try {
            val uid = requireUserId(userId)
            require(subject.id.isNotBlank()) { "Subject must be saved before generating a plan." }
            val tasksCol = db.collection("users").document(uid).collection("studyTasks")

            val existingSnap = tasksCol.whereEqualTo("subjectId", subject.id).get().await()
            val existingRefs = existingSnap.documents.map { it.reference }

            existingRefs.chunked(400).forEach { chunk ->
                val deleteBatch = db.batch()
                chunk.forEach { deleteBatch.delete(it) }
                deleteBatch.commit().await()
            }

            tasks.take(120).chunked(400).forEach { chunk ->
                val writeBatch = db.batch()
                chunk.forEachIndexed { idx, task ->
                    val ref = tasksCol.document()
                    val payload = mapOf(
                        "userId" to uid,
                        "subjectId" to subject.id,
                        "subjectName" to subject.name.take(100),
                        "topicTitle" to task.topicTitle.trim().take(200).ifEmpty { "Study Session" },
                        "focusTip" to task.focusTip.trim().take(500),
                        "scheduledDateIso" to task.scheduledDateIso,
                        "difficulty" to TopicDifficulty.fromRaw(task.difficulty).name,
                        "estimatedMinutes" to task.estimatedMinutes.coerceIn(5L, 480L),
                        "completed" to false,
                        "sortOrder" to task.sortOrder.coerceIn(0L, 1000L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    writeBatch.set(ref, payload)
                }
                writeBatch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStudyTaskCompleted(
        userId: String,
        taskId: String,
        completed: Boolean,
    ): Result<Unit> {
        val docPath = "users/$userId/studyTasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "Task ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("studyTasks").document(taskId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveRevisionSheet(
        userId: String,
        subject: Subject,
        title: String,
        summary: String,
        keyPoints: List<String>,
        formulas: List<String>,
        examTips: List<String>,
    ): Result<String> {
        var docPath = "users/$userId/revisions"
        return try {
            val uid = requireUserId(userId)
            require(subject.id.isNotBlank()) { "Subject ID is required." }
            val docRef = db.collection("users").document(uid).collection("revisions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subject.id,
                "subjectName" to subject.name.take(100),
                "title" to title.trim().take(150).ifEmpty { "${subject.name} Quick Revision" },
                "summary" to summary.trim().take(10000).ifEmpty { "Summary unavailable." },
                "keyPoints" to keyPoints.take(30).map { it.trim().take(500) }.filter { it.isNotEmpty() },
                "formulas" to formulas.take(30).map { it.trim().take(500) }.filter { it.isNotEmpty() },
                "examTips" to examTips.take(20).map { it.trim().take(500) }.filter { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRevisionSheet(userId: String, revisionId: String): Result<Unit> {
        val docPath = "users/$userId/revisions/$revisionId"
        return try {
            val uid = requireUserId(userId)
            require(revisionId.isNotBlank()) { "Revision ID cannot be blank." }
            db.collection("users").document(uid).collection("revisions").document(revisionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createPracticeTest(
        userId: String,
        subject: Subject,
        title: String,
        difficulty: TestDifficulty,
        questions: List<PracticeQuestion>,
    ): Result<String> {
        var docPath = "users/$userId/practiceTests"
        return try {
            val uid = requireUserId(userId)
            require(subject.id.isNotBlank()) { "Subject ID is required." }
            val boundedQuestions = questions.take(20)
            require(boundedQuestions.isNotEmpty()) { "Test must contain at least one question." }
            val totalMarks = boundedQuestions.sumOf { it.maxMarks.coerceIn(1L, 50L) }.coerceIn(1L, 500L)

            val docRef = db.collection("users").document(uid).collection("practiceTests").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subject.id,
                "subjectName" to subject.name.take(100),
                "title" to title.trim().take(150).ifEmpty { "${subject.name} Practice Test" },
                "difficulty" to difficulty.name,
                "status" to TestStatus.IN_PROGRESS.name,
                "totalMarks" to totalMarks,
                "earnedMarks" to 0L,
                "overallFeedback" to "",
                "questions" to boundedQuestions.map { it.toFirestoreMap() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitGradedTest(
        userId: String,
        testId: String,
        totalMarks: Long,
        earnedMarks: Long,
        overallFeedback: String,
        gradedQuestions: List<PracticeQuestion>,
    ): Result<Unit> {
        val docPath = "users/$userId/practiceTests/$testId"
        return try {
            val uid = requireUserId(userId)
            require(testId.isNotBlank()) { "Test ID cannot be blank." }
            val boundedEarned = earnedMarks.coerceIn(0L, totalMarks.coerceAtLeast(1L))
            db.collection("users").document(uid)
                .collection("practiceTests").document(testId)
                .update(
                    mapOf(
                        "status" to TestStatus.GRADED.name,
                        "earnedMarks" to boundedEarned,
                        "overallFeedback" to overallFeedback.trim().take(4000),
                        "questions" to gradedQuestions.take(20).map { it.toFirestoreMap() },
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePracticeTest(userId: String, testId: String): Result<Unit> {
        val docPath = "users/$userId/practiceTests/$testId"
        return try {
            val uid = requireUserId(userId)
            require(testId.isNotBlank()) { "Test ID cannot be blank." }
            db.collection("users").document(uid).collection("practiceTests").document(testId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
