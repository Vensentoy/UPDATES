package com.revyu.app.widget.interactive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CardFlash(
    val front: String,
    val back: String
)

@Serializable
data class ChoiceQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val correctAnswers: Set<String>,
    val multiple: Boolean,
    val typed: Boolean = false
)

@Serializable
data class QnaItem(
    val id: String?,
    val prompt: String,
    val answer: String
)

@Serializable
sealed class PracticeWidgetSession {
    abstract val widgetId: Int
    abstract val pickedOn: String
    abstract val title: String
    abstract val subjectName: String
    abstract val studySetId: String

    @Serializable
    @SerialName("flashcard")
    data class Flashcard(
        override val widgetId: Int,
        override val pickedOn: String,
        override val title: String,
        override val subjectName: String,
        override val studySetId: String,
        val items: List<CardFlash>,
        val index: Int = 0,
        val showBack: Boolean = false
    ) : PracticeWidgetSession()

    @Serializable
    @SerialName("choice")
    data class Choice(
        override val widgetId: Int,
        override val pickedOn: String,
        override val title: String,
        override val subjectName: String,
        override val studySetId: String,
        val questions: List<ChoiceQuestion>,
        val index: Int = 0,
        val selections: List<List<Int>> = questions.map { emptyList() },
        val answered: List<Boolean> = questions.map { false },
        val finished: Boolean = false,
        val correctCount: Int = 0
    ) : PracticeWidgetSession() {
        val totalQuestions: Int get() = questions.size
    }

    @Serializable
    @SerialName("qna")
    data class Qna(
        override val widgetId: Int,
        override val pickedOn: String,
        override val title: String,
        override val subjectName: String,
        override val studySetId: String,
        val items: List<QnaItem>,
        val index: Int = 0,
        val revealed: List<Boolean> = items.map { false }
    ) : PracticeWidgetSession()

    @Serializable
    @SerialName("exam")
    data class Exam(
        override val widgetId: Int,
        override val pickedOn: String,
        override val title: String,
        override val subjectName: String,
        override val studySetId: String,
        val attemptId: String,
        val questions: List<ChoiceQuestion>,
        val index: Int = 0,
        val selections: List<List<Int>> = questions.map { emptyList() },
        val answered: List<Boolean> = questions.map { false },
        val revealed: List<Boolean> = questions.map { false },
        val finished: Boolean = false,
        val correctCount: Int = 0
    ) : PracticeWidgetSession() {
        val totalQuestions: Int get() = questions.size
    }
}
