package com.revyu.app.data.repository

import android.util.Log
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
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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

        var parsedPayload: GeneratedStudySetPayload? = null
        var lastError = "Nemotron's response wasn't in the expected format. Please try generating again."

        // One automatic retry: free-tier output is sometimes cut off or wrapped in extra text.
        for (attempt in 1..MAX_ATTEMPTS) {
            val choice = try {
                val response = api.createChatCompletion(
                    ChatCompletionRequest(model = OpenRouterApi.MODEL_ID, messages = messages)
                )
                response.choices.firstOrNull()
                    ?: return fail(studySet, response.error?.message ?: "Nemotron returned an empty response. Please try generating again.")
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

            val rawContent = choice.message.content
            val parsed = try {
                json.decodeFromString<GeneratedStudySetPayload>(extractJsonObject(rawContent))
            } catch (e: IllegalArgumentException) { // SerializationException is a subclass
                Log.w(TAG, "Unparseable reply (attempt $attempt, finish=${choice.finishReason}): ${rawContent.take(500)} ... ${rawContent.takeLast(200)}", e)
                lastError = if (choice.finishReason == "length") {
                    "Nemotron's reply was cut off before it finished. Try again, or use a shorter file."
                } else {
                    "Nemotron's response wasn't in the expected format. Please try generating again."
                }
                null
            }
            if (parsed != null) {
                parsedPayload = parsed
                break
            }
        }
        val payload: GeneratedStudySetPayload = parsedPayload ?: return fail(studySet, lastError)

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

    /**
     * Pulls the JSON object out of a model reply: drops any <think> block, then takes
     * everything from the first '{' to the last '}'. This also skips code fences and
     * any intro text. If there is no closing brace (a truncated reply), the text is
     * returned as-is so decoding fails cleanly.
     */
    private fun extractJsonObject(content: String): String {
        val noThink = content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        val start = noThink.indexOf('{')
        val end = noThink.lastIndexOf('}')
        return if (start >= 0 && end > start) noThink.substring(start, end + 1) else noThink
    }

    private companion object {
        const val TAG = "GenerationRepository"
        const val MAX_ATTEMPTS = 2
    }
}