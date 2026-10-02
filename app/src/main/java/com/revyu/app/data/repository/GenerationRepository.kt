package com.revyu.app.data.repository

import android.util.Log
import com.revyu.app.core.util.PdfReviewerRenderer
import com.revyu.app.core.util.RevyuResult
import com.revyu.app.core.util.GeneratedContentValidator
import com.revyu.app.core.util.SourceChunker
import com.revyu.app.data.local.dao.FlashcardDao
import com.revyu.app.data.local.dao.QuestionDao
import com.revyu.app.data.local.entities.DifficultyMix
import com.revyu.app.data.local.entities.FlashcardEntity
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionEntity
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.StudySetEntity
import com.revyu.app.data.prompt.PromptBuilder
import com.revyu.app.data.remote.ChatCompletionRequest
import com.revyu.app.data.remote.GeneratedFlashcardsPayload
import com.revyu.app.data.remote.GeneratedOutline
import com.revyu.app.data.remote.GeneratedQuestionsPayload
import com.revyu.app.data.remote.GeneratedTopic
import com.revyu.app.data.remote.MissingApiKeyException
import com.revyu.app.data.remote.OpenRouterApi
import com.revyu.app.data.remote.ReviewerBlock
import com.revyu.app.data.remote.StructuredReviewer
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
        vaultExamLabel: String? = null,
        onProgress: (String) -> Unit = {}
    ): RevyuResult<StudySetEntity> {
        var current = studySet.copy(generationStatus = GenerationStatus.GENERATING, generationError = null)
        studySetRepository.updateStudySet(current)
        var requestCount = 0

        suspend fun beforeRequest() {
            if (requestCount++ > 0) delay(INTER_REQUEST_DELAY_MILLIS)
        }

        var outline = decodeOutline(current.outlineJson)
        if (sourceText.length > SourceChunker.MAX_CHUNK_CHARS && outline == null) {
            onProgress(STEP_OUTLINE)
            val topics = mutableListOf<GeneratedTopic>()
            for (group in groupChunks(SourceChunker.chunk(sourceText))) {
                beforeRequest()
                val outcome = requestJson(
                    messages = PromptBuilder.buildOutlineMessages(subjectName, group, current.questionLanguage),
                    maxTokens = OUTLINE_MAX_TOKENS,
                    decode = { json.decodeFromString<GeneratedOutline>(extractJsonObject(it)) }
                )
                if (outcome is RequestOutcome.Failure) return failForStep(current, STEP_OUTLINE, outcome)
                topics += (outcome as RequestOutcome.Success).value.topics
            }
            outline = mergeTopics(topics)
            current = current.copy(outlineJson = json.encodeToString(outline))
            studySetRepository.updateStudySet(current)
        }

        val excerpts = selectRelevantExcerpts(sourceText, outline)
        val bodyText: String
        val persistedReviewer = current.reviewerBodyText
        if (!persistedReviewer.isNullOrBlank()) {
            bodyText = persistedReviewer
        } else {
            onProgress(STEP_REVIEWER)
            beforeRequest()
            val reviewerOutcome = requestJson(
                messages = PromptBuilder.buildReviewerMessages(
                    subjectName, excerpts, current.questionLanguage, current.reviewerDetail, outline, vaultExamLabel
                ),
                maxTokens = REVIEWER_MAX_TOKENS,
                decode = { json.decodeFromString<StructuredReviewer>(extractJsonObject(it)) }
            ).let { outcome ->
                if (outcome is RequestOutcome.Success && outcome.value.sections.isEmpty() && outcome.value.overview.isBlank()) {
                    RequestOutcome.Failure(REVIEWER_FORMAT_ERROR, parseFailure = true)
                } else {
                    outcome
                }
            }
            val reviewerJson: String?
            when (reviewerOutcome) {
                is RequestOutcome.Success -> {
                    reviewerJson = json.encodeToString(reviewerOutcome.value)
                    bodyText = flattenReviewer(reviewerOutcome.value)
                }
                is RequestOutcome.Failure -> {
                    if (!reviewerOutcome.parseFailure) return failForStep(current, STEP_REVIEWER, reviewerOutcome)
                    beforeRequest()
                    when (val plain = requestPlain(PromptBuilder.buildPlainReviewerMessages(subjectName, excerpts, current.questionLanguage))) {
                        is RequestOutcome.Success -> {
                            reviewerJson = null
                            bodyText = plain.value
                        }
                        is RequestOutcome.Failure -> return failForStep(current, STEP_REVIEWER, plain)
                    }
                }
            }
            current = current.copy(reviewerBlocksJson = reviewerJson, reviewerBodyText = bodyText)
            studySetRepository.updateStudySet(current)
        }
        if (current.reviewerPdfPath.isNullOrBlank()) {
            current = current.copy(reviewerPdfPath = renderReviewer(current, subjectName, bodyText))
            studySetRepository.updateStudySet(current)
        }

        if (flashcardDao.getForStudySetOnce(current.id).isEmpty()) {
            onProgress(STEP_FLASHCARDS)
            beforeRequest()
            val flashcardOutcome = requestJson(
                messages = PromptBuilder.buildFlashcardMessages(
                    subjectName, excerpts, current.questionLanguage, current.flashcardCount, outline
                ),
                maxTokens = FLASHCARD_MAX_TOKENS,
                decode = { json.decodeFromString<GeneratedFlashcardsPayload>(extractJsonObject(it)) }
            )
            if (flashcardOutcome is RequestOutcome.Failure) return failForStep(current, STEP_FLASHCARDS, flashcardOutcome)
            val generatedCards = (flashcardOutcome as RequestOutcome.Success).value.flashcards
            val cards = GeneratedContentValidator.validFlashcards(generatedCards)
            if (cards.size != generatedCards.size) {
                Log.w(TAG, "Dropped ${generatedCards.size - cards.size} invalid flashcards")
            }
            val topicTitles = outline?.topics.orEmpty().associate { it.id to it.title }
            val flashcardEntities = cards.mapIndexed { index, card ->
                FlashcardEntity(
                    studySetId = current.id,
                    front = card.front.trim(),
                    back = card.back.trim(),
                    orderIndex = index,
                    hint = card.hint.takeIf(String::isNotBlank),
                    topicId = card.topicId,
                    topicTitle = card.topicId?.let(topicTitles::get),
                    difficulty = card.difficulty.coerceIn(1, 3),
                    kind = card.kind
                )
            }
            if (flashcardEntities.isNotEmpty()) flashcardDao.insertAll(flashcardEntities)
        }

        if (questionDao.getForStudySetOnce(current.id).isEmpty()) {
            onProgress(STEP_QUESTIONS)
            beforeRequest()
            val questionOutcome = requestJson(
                messages = PromptBuilder.buildQuestionMessages(
                    subjectName,
                    excerpts,
                    current.questionLanguage,
                    current.maxQuestions,
                    current.questionTypes,
                    current.difficultyMix,
                    outline,
                    vaultExamLabel
                ),
                maxTokens = QUESTION_MAX_TOKENS,
                decode = { json.decodeFromString<GeneratedQuestionsPayload>(extractJsonObject(it)) }
            )
            if (questionOutcome is RequestOutcome.Failure) return failForStep(current, STEP_QUESTIONS, questionOutcome)
            val generatedQuestions = (questionOutcome as RequestOutcome.Success).value.questions
            val validQuestions = GeneratedContentValidator.validQuestions(generatedQuestions)
                .filter { q -> runCatching { QuestionType.valueOf(q.type.uppercase()) }.getOrNull() in current.questionTypes }
            if (validQuestions.size != generatedQuestions.size) {
                Log.w(TAG, "Dropped ${generatedQuestions.size - validQuestions.size} invalid or unselected questions")
            }
            val topicTitles = outline?.topics.orEmpty().associate { it.id to it.title }
            val questionEntities = validQuestions.mapIndexed { index, question ->
                val type = QuestionType.valueOf(question.type.uppercase())
                QuestionEntity(
                    studySetId = current.id,
                    type = type,
                    prompt = question.prompt.trim(),
                    options = question.options,
                    correctAnswers = question.correctAnswers,
                    orderIndex = index,
                    acceptedAnswers = question.acceptedAnswers,
                    explanation = question.explanation.takeIf(String::isNotBlank),
                    topicId = question.topicId,
                    topicTitle = question.topicId?.let(topicTitles::get),
                    difficulty = question.difficulty.coerceIn(1, 3),
                    level = question.level
                )
            }
            if (questionEntities.isNotEmpty()) questionDao.insertAll(questionEntities)
        }

        current = current.copy(generationStatus = GenerationStatus.READY, generationError = null)
        studySetRepository.updateStudySet(current)
        return RevyuResult.Success(current)
    }

    private suspend fun failForStep(
        studySet: StudySetEntity,
        step: String,
        failure: RequestOutcome.Failure
    ): RevyuResult.Error = fail(
        studySet,
        "$step failed: ${failure.message}",
        isRateLimit = failure.isRateLimit,
        isMissingApiKey = failure.isMissingApiKey
    )

    private suspend fun fail(
        studySet: StudySetEntity,
        message: String,
        isRateLimit: Boolean = false,
        isMissingApiKey: Boolean = false
    ): RevyuResult.Error {
        studySetRepository.updateStudySet(
            studySet.copy(generationStatus = GenerationStatus.FAILED, generationError = message)
        )
        return RevyuResult.Error(message = message, isRateLimit = isRateLimit, isMissingApiKey = isMissingApiKey)
    }

    private suspend fun <T> requestJson(
        messages: List<com.revyu.app.data.remote.ChatMessage>,
        maxTokens: Int,
        decode: (String) -> T
    ): RequestOutcome<T> {
        var lastFailure = RequestOutcome.Failure(RESPONSE_FORMAT_ERROR, parseFailure = true)
        for (attempt in 1..MAX_ATTEMPTS) {
            val choice = when (val request = request(messages, maxTokens)) {
                is RequestOutcome.Failure -> return request
                is RequestOutcome.Success -> request.value
            }
            val parsed = runCatching { decode(choice.message.content) }.getOrNull()
            if (parsed != null) return RequestOutcome.Success(parsed)
            Log.w(
                TAG,
                "Unparseable reply (attempt $attempt, finish=${choice.finishReason}): " +
                    "${choice.message.content.take(300)}"
            )
            lastFailure = RequestOutcome.Failure(
                if (choice.finishReason == "length") TRUNCATED_RESPONSE_ERROR else RESPONSE_FORMAT_ERROR,
                parseFailure = true
            )
        }
        return lastFailure
    }

    private suspend fun requestPlain(messages: List<com.revyu.app.data.remote.ChatMessage>): RequestOutcome<String> =
        when (val request = request(messages, PLAIN_REVIEWER_MAX_TOKENS, responseFormat = null)) {
            is RequestOutcome.Failure -> request
            is RequestOutcome.Success -> RequestOutcome.Success(request.value.message.content.trim())
        }

    private suspend fun request(
        messages: List<com.revyu.app.data.remote.ChatMessage>,
        maxTokens: Int,
        responseFormat: com.revyu.app.data.remote.ResponseFormat? = com.revyu.app.data.remote.ResponseFormat()
    ): RequestOutcome<com.revyu.app.data.remote.ChatChoice> = try {
        val response = api.createChatCompletion(
            ChatCompletionRequest(
                model = OpenRouterApi.MODEL_ID,
                messages = messages,
                maxTokens = maxTokens,
                responseFormat = responseFormat
            )
        )
        val choice = response.choices.firstOrNull()
        if (choice != null) RequestOutcome.Success(choice)
        else RequestOutcome.Failure(response.error?.message ?: EMPTY_RESPONSE_ERROR)
    } catch (e: MissingApiKeyException) {
        RequestOutcome.Failure(API_KEY_ERROR, isMissingApiKey = true)
    } catch (e: HttpException) {
        if (e.code() == 429) RequestOutcome.Failure(RATE_LIMIT_ERROR, isRateLimit = true)
        else RequestOutcome.Failure("OpenRouter returned an error (HTTP ${e.code()}). Please try again.")
    } catch (e: IOException) {
        RequestOutcome.Failure("Couldn't reach OpenRouter. Check your connection and try again.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RequestOutcome.Failure(e.message ?: "Something went wrong while generating this Study Set.")
    }

    private suspend fun renderReviewer(studySet: StudySetEntity, subjectName: String, bodyText: String): String? =
        try {
            pdfRenderer.render(
                studySetId = studySet.id,
                subjectName = subjectName,
                studySetTitle = studySet.title,
                bodyText = bodyText,
                columns = studySet.reviewerColumns,
                fontStyle = studySet.reviewerFontStyle,
                fontSizeSp = studySet.reviewerFontSizeSp,
                margins = studySet.reviewerMargins
            )
        } catch (e: Exception) {
            null
        }

    private fun flattenReviewer(reviewer: StructuredReviewer): String = buildString {
        if (reviewer.title.isNotBlank()) {
            appendLine(reviewer.title.trim())
            appendLine()
        }
        if (reviewer.overview.isNotBlank()) {
            appendLine("Overview")
            appendLine(reviewer.overview.trim())
            appendLine()
        }
        reviewer.sections.forEach { section ->
            if (section.heading.isNotBlank()) {
                appendLine(section.heading.trim())
                appendLine()
            }
            section.blocks.forEach { block -> appendReviewerBlock(block) }
            appendLine()
        }
        if (reviewer.cheatSheet.isNotEmpty()) {
            appendLine("Cheat Sheet")
            reviewer.cheatSheet.forEach { appendLine("• ${it.trim()}") }
            appendLine()
        }
        if (reviewer.glossary.isNotEmpty()) {
            appendLine("Glossary")
            reviewer.glossary.forEach { appendLine("${it.term}: ${it.meaning}") }
        }
    }.trim()

    private fun StringBuilder.appendReviewerBlock(block: ReviewerBlock) {
        when (block.type.uppercase()) {
            "PARAGRAPH", "FORMULA" -> block.text?.takeIf(String::isNotBlank)?.let { appendLine(it.trim()).appendLine() }
            "BULLETS" -> block.items.forEach { appendLine("• ${it.trim()}") }
            "STEPS" -> block.items.forEachIndexed { index, item -> appendLine("${index + 1}. ${item.trim()}") }
            "DEFINITION" -> block.text?.let { appendLine("${block.term.orEmpty()}: ${it.trim()}") }
            "CALLOUT" -> block.text?.let { appendLine("${block.kind.orEmpty()}: ${it.trim()}") }
            "TABLE" -> {
                if (block.headers.isNotEmpty()) appendLine(block.headers.joinToString(" | "))
                block.rows.forEach { row -> appendLine(row.joinToString(" | ")) }
            }
        }
        if (block.type.uppercase() in setOf("BULLETS", "STEPS", "TABLE")) appendLine()
    }

    private fun decodeOutline(encoded: String?): GeneratedOutline? =
        encoded?.let { runCatching { json.decodeFromString<GeneratedOutline>(it) }.getOrNull() }

    private fun mergeTopics(topics: List<GeneratedTopic>): GeneratedOutline {
        val merged = linkedMapOf<String, GeneratedTopic>()
        topics.filter { it.title.isNotBlank() }.forEachIndexed { index, topic ->
            val key = topic.title.lowercase().replace(NON_ALPHANUMERIC, " ").trim().replace(WHITESPACE, " ")
            val normalized = topic.copy(id = topic.id.ifBlank { "t${index + 1}" })
            val existing = merged[key]
            merged[key] = if (existing == null) normalized else existing.copy(
                importance = maxOf(existing.importance, normalized.importance),
                keyPoints = (existing.keyPoints + normalized.keyPoints).distinct(),
                terms = (existing.terms + normalized.terms).distinct()
            )
        }
        return GeneratedOutline(merged.values.mapIndexed { index, topic -> topic.copy(id = "t${index + 1}") })
    }

    private fun groupChunks(chunks: List<String>): List<String> {
        val groups = mutableListOf<String>()
        var current = StringBuilder()
        chunks.forEach { chunk ->
            if (current.isNotEmpty() && current.length + 2 + chunk.length > OUTLINE_GROUP_MAX_CHARS) {
                groups += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(chunk)
        }
        if (current.isNotEmpty()) groups += current.toString()
        return groups
    }

    private fun selectRelevantExcerpts(sourceText: String, outline: GeneratedOutline?): String {
        val chunks = SourceChunker.chunk(sourceText)
        if (sourceText.length <= STEP_SOURCE_MAX_CHARS) return sourceText
        if (outline?.topics.isNullOrEmpty()) return chunks.take(STEP_SOURCE_MAX_CHUNKS).joinToString("\n\n")

        val topicTerms = outline!!.topics.flatMap { topic ->
            (listOf(topic.title) + topic.keyPoints + topic.terms).flatMap(::tokens).toSet().map { it to topic.importance }
        }.toMap()
        val ranked = chunks.mapIndexed { index, chunk ->
            val score = tokens(chunk).sumOf { topicTerms[it] ?: 0 }
            Triple(index, chunk, score)
        }.sortedWith(compareByDescending<Triple<Int, String, Int>> { it.third }.thenBy { it.first })
        val selected = mutableListOf<Pair<Int, String>>()
        var charCount = 0
        for ((index, chunk, _) in ranked) {
            val nextSize = chunk.length + if (selected.isEmpty()) 0 else 2
            if (charCount + nextSize <= STEP_SOURCE_MAX_CHARS) {
                selected += index to chunk
                charCount += nextSize
            }
        }
        return selected.sortedBy { it.first }.joinToString("\n\n") { it.second }
    }

    private fun tokens(text: String): Set<String> =
        text.lowercase().replace(NON_ALPHANUMERIC, " ").split(WHITESPACE).filter(String::isNotBlank).toSet()

    private sealed interface RequestOutcome<out T> {
        data class Success<T>(val value: T) : RequestOutcome<T>
        data class Failure(
            val message: String,
            val isRateLimit: Boolean = false,
            val isMissingApiKey: Boolean = false,
            val parseFailure: Boolean = false
        ) : RequestOutcome<Nothing>
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
        const val INTER_REQUEST_DELAY_MILLIS = 700L
        const val OUTLINE_GROUP_MAX_CHARS = 24_002
        const val STEP_SOURCE_MAX_CHARS = 24_000
        const val STEP_SOURCE_MAX_CHUNKS = 2
        const val OUTLINE_MAX_TOKENS = 3_000
        const val REVIEWER_MAX_TOKENS = 8_000
        const val PLAIN_REVIEWER_MAX_TOKENS = 8_000
        const val FLASHCARD_MAX_TOKENS = 5_000
        const val QUESTION_MAX_TOKENS = 8_000
        const val STEP_OUTLINE = "Reading your file"
        const val STEP_REVIEWER = "Writing reviewer"
        const val STEP_FLASHCARDS = "Making flashcards"
        const val STEP_QUESTIONS = "Writing questions"
        const val API_KEY_ERROR = "Add your OpenRouter API key in Settings before generating."
        const val RATE_LIMIT_ERROR = "Nemotron's free tier is rate-limited right now. Wait a bit and try again."
        const val EMPTY_RESPONSE_ERROR = "Nemotron returned an empty response. Please try generating again."
        const val RESPONSE_FORMAT_ERROR = "Nemotron's response wasn't in the expected format. Please try generating again."
        const val REVIEWER_FORMAT_ERROR = "Nemotron's reviewer was empty or incomplete."
        const val TRUNCATED_RESPONSE_ERROR = "Nemotron's reply was cut off before it finished. Try again, or use a shorter file."
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")
    }
}