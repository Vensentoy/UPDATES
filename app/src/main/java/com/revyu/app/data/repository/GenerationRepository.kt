package com.revyu.app.data.repository

import com.revyu.app.core.util.PdfReviewerRenderer
import com.revyu.app.core.util.RevyuResult
import com.revyu.app.data.local.dao.FlashcardDao
import com.revyu.app.data.local.dao.QuestionDao
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.prompt.PromptBuilder
import com.revyu.app.data.remote.ChatCompletionRequest
import com.revyu.app.data.remote.GeneratedStudySetPayload
import com.revyu.app.data.remote.MissingApiKeyException
import com.revyu.app.data.remote.OpenRouterApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class GenerationRepository(
    private val api: OpenRouterApi,
    private val studySetRepository: StudySetRepository,
    private val flashcardDao: FlashcardDao,
    private val questionDao: QuestionDao,
    private val pdfRenderer: PdfReviewerRenderer
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateStudySet(
        studySet: StudySetEntity,
        subjectName: String,
        sourceText: String,
        /** Non-null ("Midterm Exam" / "Final Exam") for a Semester Vault synthesis; null for a regular Study Set. */
        vaultExamLabel: String? = null
    ): RevyuResult<StudySetEntity> {
        studySetRepository.updateStudySet(studySet.copy(generationStatus = GenerationStatus.GENERATING, generationError = null))

        val messages = PromptBuilder.buildGenerationMessages(
            subjectName = subjectName,
            sourceText = sourceText,
            language = studySet.questionLanguage,
            maxQuestions = studySet.maxQuestions,
            questionTypes = studySet.questionTypes,
            vaultExamLabel = vaultExamLabel
        )

        val rawContent: String = try {
            val response = api.createChatCompletion(
                ChatCompletionRequest(model = OpenRouterApi.MODEL_ID, messages = messages)
            )
            response.choices.firstOrNull()?.message?.content
                ?: return fail(studySet, "Nemotron returned an empty response. Please try generating again.")
        } catch (e: MissingApiKeyException) {
            return fail(studySet, "Add your OpenRouter API key in Settings before generating.", isMissingApiKey = true)
        } catch (e: HttpException) {
            return if (e.code() == 429) {
                fail(studySet, "Nemotron's free tier is rate-limited right now. Wait a bit and try again.", isRateLimit = true)
            } else {
                fail(studySet, "OpenRouter returned an error (HTTP ${e.code()}). Please try again.")
            }
        } catch (e: IOException) {
            return fail(studySet, "Couldn't reach OpenRouter. Check your connection and try again.")
        } catch (e: Exception) {
            return fail(studySet, e.message ?: "Something went wrong while generating this Study Set.")
        }

        val payload: GeneratedStudySetPayload = try {
            json.decodeFromString(stripCodeFences(rawContent))
        } catch (e: SerializationException) {
            return fail(studySet, "Nemotron's response wasn't in the expected format. Please try generating again.")
        }

        val flashcards = payload.flashcards.mapIndexed { index, fc ->
            FlashcardEntity(studySetId = studySet.id, front = fc.front, back = fc.back, orderIndex = index)
        }
        if (flashcards.isNotEmpty()) flashcardDao.insertAll(flashcards)

        val questions = payload.questions.mapIndexedNotNull { index, q ->
            val type = runCatching { QuestionType.valueOf(q.type.uppercase()) }.getOrNull() ?: return@mapIndexedNotNull null
            QuestionEntity(
                studySetId = studySet.id,
                type = type,
                prompt = q.prompt,
                options = q.options,
                correctAnswers = q.correctAnswers,
                orderIndex = index
            )
        }
        if (questions.isNotEmpty()) questionDao.insertAll(questions)

        val pdfPath = try {
            pdfRenderer.render(
                studySetId = studySet.id,
                subjectName = subjectName,
                studySetTitle = studySet.title,
                bodyText = payload.reviewer,
                columns = studySet.reviewerColumns,
                fontStyle = studySet.reviewerFontStyle,
                fontSizeSp = studySet.reviewerFontSizeSp,
                margins = studySet.reviewerMargins
            )
        } catch (e: Exception) {
            null // Reviewer PDF is a nice-to-have render; flashcards/questions still succeeded, so don't fail the whole generation.
        }

        val updated = studySet.copy(
            reviewerBodyText = payload.reviewer,
            reviewerPdfPath = pdfPath,
            generationStatus = GenerationStatus.READY,
            generationError = null
        )
        studySetRepository.updateStudySet(updated)
        return RevyuResult.Success(updated)
    }

    private suspend fun fail(studySet: StudySetEntity, message: String, isRateLimit: Boolean = false, isMissingApiKey: Boolean = false): RevyuResult.Error {
        studySetRepository.updateStudySet(
            studySet.copy(generationStatus = GenerationStatus.FAILED, generationError = message)
        )
        return RevyuResult.Error(message = message, isRateLimit = isRateLimit, isMissingApiKey = isMissingApiKey)
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        return if (trimmed.startsWith("```")) {
            trimmed.lines().drop(1).dropLastWhile { it.isBlank() }.let { lines ->
                if (lines.isNotEmpty() && lines.last().trim() == "```") lines.dropLast(1) else lines
            }.joinToString("\n")
        } else trimmed
    }
}
