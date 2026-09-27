package com.revyu.app.data.repository

import com.revyu.app.data.local.dao.ExamAttemptDao
import com.revyu.app.data.local.dao.FlashcardDao
import com.revyu.app.data.local.dao.QuestionDao
import com.revyu.app.data.local.dao.StudySetDao
import com.revyu.app.data.local.entities.ExamAttemptEntity
import com.revyu.app.data.local.entities.ExamStatus
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.local.entities.StudySetKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StudySetRepository(
    private val studySetDao: StudySetDao,
    private val flashcardDao: FlashcardDao,
    private val questionDao: QuestionDao,
    private val examAttemptDao: ExamAttemptDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeForSubject(subjectId: String): Flow<List<StudySetEntity>> =
        studySetDao.observeForSubject(subjectId)

    fun observeById(id: String): Flow<StudySetEntity?> = studySetDao.observeById(id)

    suspend fun getById(id: String): StudySetEntity? = studySetDao.getById(id)

    suspend fun getReadyByMaterialId(materialId: String): StudySetEntity? =
        studySetDao.getReadyByMaterialId(materialId)

    suspend fun getAllOnce(): List<StudySetEntity> = studySetDao.getAllOnce()

    fun observeAllStudySets(): Flow<List<StudySetEntity>> = studySetDao.observeAll()

    /** Cascades to flashcards/questions/exam attempts via Room's FK CASCADE rules; also removes the rendered PDF, which lives outside the DB. */
    suspend fun deleteStudySet(studySet: StudySetEntity) {
        studySet.reviewerPdfPath?.let { path -> runCatching { java.io.File(path).delete() } }
        studySetDao.delete(studySet)
    }

    fun observeFlashcards(studySetId: String): Flow<List<FlashcardEntity>> =
        flashcardDao.observeForStudySet(studySetId)

    fun observeQuestions(studySetId: String): Flow<List<QuestionEntity>> =
        questionDao.observeForStudySet(studySetId)

    suspend fun getQuestionsOnce(studySetId: String): List<QuestionEntity> =
        questionDao.getForStudySetOnce(studySetId)

    fun observeExamAttempts(studySetId: String): Flow<List<ExamAttemptEntity>> =
        examAttemptDao.observeForStudySet(studySetId)

    fun observeAllSubmittedAttempts(): Flow<List<ExamAttemptEntity>> =
        examAttemptDao.observeAllSubmitted()

    suspend fun getExamAttempt(attemptId: String): ExamAttemptEntity? = examAttemptDao.getById(attemptId)

    suspend fun insertStudySet(studySet: StudySetEntity) = studySetDao.insert(studySet)

    suspend fun updateStudySet(studySet: StudySetEntity) = studySetDao.update(studySet)

    suspend fun startExamAttempt(studySetId: String): ExamAttemptEntity {
        val attempt = ExamAttemptEntity(studySetId = studySetId)
        examAttemptDao.insert(attempt)
        studySetDao.updateExamStatus(studySetId, ExamStatus.IN_PROGRESS)
        return attempt
    }

    /** Grades and persists a submitted attempt, returns it with results filled in. */
    suspend fun submitExamAttempt(
        attempt: ExamAttemptEntity,
        answers: Map<String, List<String>>
    ): ExamAttemptEntity {
        val questions = questionDao.getForStudySetOnce(attempt.studySetId)
        var correct = 0
        for (question in questions) {
            val given = answers[question.id].orEmpty().map { it.trim().lowercase() }.toSet()
            val expected = question.correctAnswers.map { it.trim().lowercase() }.toSet()
            if (given == expected) correct++
        }
        val total = questions.size
        val incorrect = total - correct
        val percentage = if (total == 0) 0f else (correct.toFloat() / total.toFloat()) * 100f

        val updated = attempt.copy(
            submittedAt = java.time.Instant.now(),
            answersJson = json.encodeToString(answers),
            totalQuestions = total,
            correctCount = correct,
            incorrectCount = incorrect,
            scorePercentage = percentage
        )
        examAttemptDao.update(updated)
        studySetDao.updateExamStatus(attempt.studySetId, ExamStatus.COMPLETED)
        return updated
    }

    fun decodeAnswers(answersJson: String): Map<String, List<String>> =
        if (answersJson.isBlank()) emptyMap() else json.decodeFromString(answersJson)

    fun performanceLabel(percentage: Float): String = when {
        percentage >= 90f -> "Excellent"
        percentage >= 75f -> "Good"
        percentage >= 60f -> "Fair"
        else -> "Needs Review"
    }

    /** Regular, successfully-generated Study Sets for a subject — the only valid source material for a Semester Vault synthesis. */
    suspend fun getVaultEligibleStudySets(subjectId: String): List<StudySetEntity> =
        studySetDao.observeForSubject(subjectId).first().filter {
            it.kind == StudySetKind.REGULAR && it.generationStatus == GenerationStatus.READY
        }

    /**
     * Concatenates each selected Study Set's reviewer text, flashcards, and questions
     * into one combined source blob for the Semester Vault prompt. This is deliberately
     * simple string aggregation rather than fetching original uploaded materials again —
     * feeding the model what was already distilled out of each material keeps the
     * combined text far shorter than re-sending every raw upload, and stays true to the
     * spec's "uses accumulated Study Sets" framing rather than the original files.
     */
    suspend fun aggregateContentForVault(studySets: List<StudySetEntity>): String {
        // NOTE: joinToString's `transform` lambda is NOT inline (unlike forEach/map), so it
        // can't contain suspend calls directly. Build each block via the inline `.map` first,
        // then join the already-built strings.
        val blocks = studySets.map { studySet ->
            val flashcards = flashcardDao.getForStudySetOnce(studySet.id)
            val questions = questionDao.getForStudySetOnce(studySet.id)
            buildString {
                appendLine("Study Set: ${studySet.title}")
                studySet.reviewerBodyText?.let {
                    appendLine()
                    appendLine("Reviewer:")
                    appendLine(it)
                }
                if (flashcards.isNotEmpty()) {
                    appendLine()
                    appendLine("Flashcards:")
                    flashcards.forEach { appendLine("- ${it.front} => ${it.back}") }
                }
                if (questions.isNotEmpty()) {
                    appendLine()
                    appendLine("Previously covered questions:")
                    questions.forEach { appendLine("- ${it.prompt} (answer: ${it.correctAnswers.joinToString(", ")})") }
                }
            }
        }
        return blocks.joinToString("\n\n=====\n\n")
    }
}
