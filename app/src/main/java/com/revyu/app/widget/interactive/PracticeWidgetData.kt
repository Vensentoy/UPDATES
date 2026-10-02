package com.revyu.app.widget.interactive

import com.revyu.app.RevyuApplication
import com.revyu.app.core.widget.FLASHCARD_SEED
import com.revyu.app.core.widget.QUESTION_SEED
import com.revyu.app.core.widget.PracticeWidgetPicker
import com.revyu.app.data.local.entities.GenerationStatus
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.StudySetEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

object PracticeWidgetData {

    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private const val MAX_FLASHCARDS = 10
    private const val MAX_QUESTIONS = 6

    private data class PoolMeta(val setTitle: String, val subjectName: String, val studySetId: String)

    private suspend fun metaFor(context: android.content.Context, studySet: StudySetEntity): PoolMeta {
        val app = context.applicationContext as RevyuApplication
        val container = app.container
        val names = container.database.subjectDao().getAll().associate { it.id to it.name }
        return names[studySet.subjectId].let {
            PoolMeta(studySet.title, it ?: studySet.title, studySet.id)
        }
    }

    suspend fun buildFlashcardSession(context: android.content.Context, widgetId: Int): PracticeWidgetSession.Flashcard? {
        val app = context.applicationContext as RevyuApplication
        val container = app.container
        val date = LocalDate.now()
        val dateStr = date.format(DATE_FMT)

        val pins = container.appSettingsStore.practiceWidgetPins.first()
        val allSets = container.studySetRepository.getAllOnce()
        val candidates = pins.flashcardStudySetId?.let { pin -> allSets.filter { it.id == pin } } ?: allSets
        if (candidates.isEmpty()) return null
        val candidateIds = candidates.map { it.id }.toSet()

        val cards = container.database.flashcardDao().getAllOnce().filter { it.studySetId in candidateIds }
        if (cards.isEmpty()) return null

        val startIndex = PracticeWidgetPicker.pickIndex(date, cards.size, FLASHCARD_SEED)
        val rotated = cards.drop(startIndex) + cards.take(startIndex)
        val items = rotated.take(MAX_FLASHCARDS).map { CardFlash(it.front, it.back) }

        val set = candidates.first { it.id == rotated.first().studySetId }
        val meta = metaFor(context, set)

        return PracticeWidgetSession.Flashcard(
            widgetId = widgetId,
            pickedOn = dateStr,
            title = meta.setTitle,
            subjectName = meta.subjectName,
            studySetId = meta.studySetId,
            items = items
        )
    }

    private val CHOICE_TYPES = setOf(
        QuestionType.SINGLE_CHOICE.name,
        QuestionType.MULTIPLE_CHOICE.name,
        QuestionType.TRUE_FALSE.name
    )

    private val TYPED_TYPES = setOf(
        QuestionType.IDENTIFICATION.name,
        QuestionType.SHORT_ANSWER.name
    )

    private suspend fun rotateQuestions(
        context: android.content.Context,
        date: LocalDate,
        pin: String?
    ): List<com.revyu.app.data.local.entities.QuestionEntity> {
        val app = context.applicationContext as RevyuApplication
        val container = app.container
        val allSets = container.studySetRepository.getAllOnce()
        val candidates = pin?.let { p -> allSets.filter { it.id == p } } ?: allSets
        if (candidates.isEmpty()) return emptyList()
        val candidateIds = candidates.map { it.id }.toSet()
        val all = container.database.questionDao().getAllOnce().filter { it.studySetId in candidateIds }
        if (all.isEmpty()) return emptyList()
        val startIndex = PracticeWidgetPicker.pickIndex(date, all.size, QUESTION_SEED)
        return all.drop(startIndex) + all.take(startIndex)
    }

    private suspend fun metaForQuestion(context: android.content.Context, questionId: String): PoolMeta? {
        val app = context.applicationContext as RevyuApplication
        val container = app.container
        val allSets = container.studySetRepository.getAllOnce()
        val question = container.database.questionDao().getAllOnce().firstOrNull { it.id == questionId }
            ?: return null
        val set = allSets.firstOrNull { it.id == question.studySetId } ?: return null
        return metaFor(context, set)
    }

    suspend fun buildChoiceSession(context: android.content.Context, widgetId: Int): PracticeWidgetSession.Choice? {
        val date = LocalDate.now()
        val dateStr = date.format(DATE_FMT)
        val pins = (context.applicationContext as RevyuApplication).container.appSettingsStore.practiceWidgetPins.first()

        val pool = rotateQuestions(context, date, pins.questionStudySetId)
            .filter { it.type.name in CHOICE_TYPES }
        if (pool.isEmpty()) return null

        val meta = metaForQuestion(context, pool.first().id) ?: return null
        val questions = pool.take(MAX_QUESTIONS).map { q ->
            ChoiceQuestion(
                id = q.id,
                prompt = q.prompt,
                options = when (q.type) {
                    QuestionType.TRUE_FALSE -> listOf("True", "False")
                    else -> q.options.take(4)
                },
                correctAnswers = q.correctAnswers.map { it.trim().lowercase() }.toSet(),
                multiple = q.type == QuestionType.MULTIPLE_CHOICE,
                typed = false
            )
        }

        return PracticeWidgetSession.Choice(
            widgetId = widgetId,
            pickedOn = dateStr,
            title = meta.setTitle,
            subjectName = meta.subjectName,
            studySetId = meta.studySetId,
            questions = questions
        )
    }

    suspend fun buildQnaSession(context: android.content.Context, widgetId: Int): PracticeWidgetSession.Qna? {
        val date = LocalDate.now()
        val dateStr = date.format(DATE_FMT)
        val pins = (context.applicationContext as RevyuApplication).container.appSettingsStore.practiceWidgetPins.first()

        val rotated = rotateQuestions(context, date, pins.questionStudySetId)
        val typed = rotated.filter { it.type.name in TYPED_TYPES }
        val pool = if (typed.isNotEmpty()) typed else rotated
        if (pool.isEmpty()) return null

        val meta = metaForQuestion(context, pool.first().id) ?: return null
        val items = pool.take(MAX_QUESTIONS).map { q ->
            QnaItem(
                id = q.id,
                prompt = q.prompt,
                answer = q.correctAnswers.joinToString(", ")
            )
        }

        return PracticeWidgetSession.Qna(
            widgetId = widgetId,
            pickedOn = dateStr,
            title = meta.setTitle,
            subjectName = meta.subjectName,
            studySetId = meta.studySetId,
            items = items
        )
    }

    suspend fun buildExamSession(context: android.content.Context, widgetId: Int): PracticeWidgetSession.Exam? {
        val app = context.applicationContext as RevyuApplication
        val container = app.container
        val date = LocalDate.now()
        val dateStr = date.format(DATE_FMT)

        val allSets = container.studySetRepository.getAllOnce()
        val pins = container.appSettingsStore.practiceWidgetPins.first()
        val set = pins.questionStudySetId?.let { pin -> allSets.firstOrNull { it.id == pin } }
            ?: allSets.firstOrNull { it.generationStatus == GenerationStatus.READY }
            ?: return null

        val questions = container.database.questionDao().getForStudySetOnce(set.id)
        if (questions.isEmpty()) return null

        val meta = metaFor(context, set)
        val attempt = container.studySetRepository.startExamAttempt(set.id)
        val qItems = questions.map { q ->
            ChoiceQuestion(
                id = q.id,
                prompt = q.prompt,
                options = when (q.type) {
                    QuestionType.TRUE_FALSE -> listOf("True", "False")
                    QuestionType.IDENTIFICATION, QuestionType.SHORT_ANSWER -> emptyList()
                    else -> q.options.take(4)
                },
                correctAnswers = q.correctAnswers.map { it.trim().lowercase() }.toSet(),
                multiple = q.type == QuestionType.MULTIPLE_CHOICE,
                typed = q.type == QuestionType.IDENTIFICATION || q.type == QuestionType.SHORT_ANSWER
            )
        }

        return PracticeWidgetSession.Exam(
            widgetId = widgetId,
            pickedOn = dateStr,
            title = meta.setTitle,
            subjectName = meta.subjectName,
            studySetId = meta.studySetId,
            attemptId = attempt.id,
            questions = qItems
        )
    }
}