package com.revyu.app.widget.interactive

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.revyu.app.R
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_CHECK
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FINISH
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FLIP
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_NEXT
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PREV
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_REVEAL
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_RESTART
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_SUBMIT

data class OptionUi(
    val label: String,
    val selected: Boolean,
    val reveal: Boolean,
    val correct: Boolean
)

data class RenderModel(
    val title: String,
    val pill: String,
    val prompt: String,
    val options: List<OptionUi> = emptyList(),
    val revealAnswer: String? = null,
    val hint: String? = null,
    val feedback: String? = null,
    val feedbackColor: Int = 0,
    val prevVisible: Boolean = false,
    val primaryLabel: String = "",
    val primaryAction: String? = null
)

data class ClickIntents(
    val body: PendingIntent? = null,
    val prev: PendingIntent? = null,
    val primary: PendingIntent? = null,
    val open: PendingIntent? = null,
    val options: List<PendingIntent?> = emptyList()
)

/**
 * Pure RemoteViews builder for the interactive practice widgets. No I/O, only safe
 * RemoteViews actions (the same allow-list the schedule widget uses: setTextViewText,
 * setViewVisibility, setImageViewResource, setOnClickPendingIntent, setTextColor — no
 * reflection setInt, no ProgressBar, no nested inflation), so ColorOS/Transsion hosts
 * apply it without "Problem loading widget".
 *
 * All section ids exist in the single shared layout; unused sections are GONE.
 */
object PracticeWidgetRenderer {

    val NAVY = 0xFF1B2A4A.toInt()
    val MUTED = 0xFF666D7F.toInt()
    val GREEN = 0xFF3A7D5C.toInt()
    val RED = 0xFFB3452F.toInt()

    private val MAX_OPTIONS = 4

    fun map(context: Context, session: PracticeWidgetSession?): RenderModel {
        if (session == null) {
            return RenderModel(
                title = context.getString(R.string.app_name),
                pill = "",
                prompt = context.getString(R.string.practice_loading),
                primaryLabel = context.getString(R.string.practice_open),
                primaryAction = null
            )
        }
        return when (session) {
            is PracticeWidgetSession.Flashcard -> mapFlashcard(context, session)
            is PracticeWidgetSession.Choice -> mapChoice(context, session)
            is PracticeWidgetSession.Qna -> mapQna(context, session)
            is PracticeWidgetSession.Exam -> mapExam(context, session)
        }
    }

    private fun mapFlashcard(context: Context, s: PracticeWidgetSession.Flashcard): RenderModel {
        val card = s.items.getOrNull(s.index) ?: return placeholderModel(context)
        val pill = context.getString(R.string.practice_pill_card, s.index + 1, s.items.size)
        return RenderModel(
            title = s.subjectName,
            pill = pill,
            prompt = if (s.showBack) card.back else card.front,
            hint = context.getString(
                if (s.showBack) R.string.practice_flip_back_hint else R.string.practice_flip_hint
            ),
            prevVisible = s.index > 0,
            primaryLabel = context.getString(
                if (s.index == s.items.lastIndex) R.string.practice_new_round else R.string.practice_next
            ),
            primaryAction = if (s.index < s.items.lastIndex) ACTION_NEXT else ACTION_RESTART
        )
    }

    private fun mapChoice(context: Context, s: PracticeWidgetSession.Choice): RenderModel {
        val q = s.questions.getOrNull(s.index) ?: return placeholderModel(context)
        if (s.finished) {
            val percent = if (s.totalQuestions == 0) 0 else s.correctCount * 100 / s.totalQuestions
            return RenderModel(
                title = s.subjectName,
                pill = context.getString(R.string.practice_pill_done),
                prompt = context.getString(R.string.practice_score, s.correctCount, s.totalQuestions, percent),
                hint = context.getString(R.string.practice_score_hint),
                primaryLabel = context.getString(R.string.practice_new_round),
                primaryAction = ACTION_RESTART
            )
        }
        val answered = s.answered[s.index]
        val selected = s.selections[s.index].toSet()
        val options = q.options.take(MAX_OPTIONS).mapIndexed { i, label ->
            OptionUi(
                label = label,
                selected = i in selected,
                reveal = answered,
                correct = answered && (q.correctAnswers.contains(label.trim().lowercase()))
            )
        }
        val correct = if (answered) PracticeWidgetSessionFlow.gradeChoice(q, s.selections[s.index]) else false
        return RenderModel(
            title = s.subjectName,
            pill = context.getString(R.string.practice_pill_question, s.index + 1, s.questions.size),
            prompt = q.prompt,
            options = options,
            feedback = if (answered) {
                context.getString(if (correct) R.string.practice_correct else R.string.practice_incorrect)
            } else null,
            feedbackColor = if (answered && correct) GREEN else if (answered) RED else 0,
            prevVisible = s.index > 0,
            primaryLabel = primaryLabel(context, s.index, s.questions.size, answered, q.multiple, selected),
            primaryAction = primaryAction(s.index, s.questions.size, answered, q.multiple, selected)
        )
    }

    private fun primaryLabel(
        context: Context,
        index: Int,
        size: Int,
        answered: Boolean,
        multiple: Boolean,
        selected: Set<Int>
    ): String = when {
        index == size - 1 && answered -> context.getString(R.string.practice_see_results)
        answered -> context.getString(R.string.practice_next)
        multiple && selected.isNotEmpty() -> context.getString(R.string.practice_check)
        else -> context.getString(R.string.practice_next)
    }

    private fun primaryAction(
        index: Int,
        size: Int,
        answered: Boolean,
        multiple: Boolean,
        selected: Set<Int>
    ): String? = when {
        index == size - 1 && answered -> ACTION_FINISH
        answered -> ACTION_NEXT
        multiple && selected.isNotEmpty() -> ACTION_CHECK
        else -> ACTION_NEXT
    }

    private fun mapQna(context: Context, s: PracticeWidgetSession.Qna): RenderModel {
        val item = s.items.getOrNull(s.index) ?: return placeholderModel(context)
        val revealed = s.revealed[s.index]
        return RenderModel(
            title = s.subjectName,
            pill = context.getString(R.string.practice_pill_question, s.index + 1, s.items.size),
            prompt = item.prompt,
            revealAnswer = if (revealed) context.getString(R.string.practice_answer, item.answer) else null,
            hint = if (!revealed) context.getString(R.string.practice_reveal_hint) else null,
            prevVisible = s.index > 0,
            primaryLabel = when {
                s.index == s.items.lastIndex && revealed -> context.getString(R.string.practice_new_round)
                revealed -> context.getString(R.string.practice_next)
                else -> context.getString(R.string.practice_reveal)
            },
            primaryAction = when {
                s.index == s.items.lastIndex && revealed -> ACTION_RESTART
                revealed -> ACTION_NEXT
                else -> ACTION_REVEAL
            }
        )
    }

    private fun mapExam(context: Context, s: PracticeWidgetSession.Exam): RenderModel {
        val q = s.questions.getOrNull(s.index) ?: return placeholderModel(context)
        if (s.finished) {
            val percent = if (s.totalQuestions == 0) 0 else s.correctCount * 100 / s.totalQuestions
            return RenderModel(
                title = s.subjectName,
                pill = context.getString(R.string.practice_pill_done),
                prompt = context.getString(R.string.practice_score, s.correctCount, s.totalQuestions, percent),
                hint = context.getString(R.string.practice_score_hint),
                primaryLabel = context.getString(R.string.practice_new_round),
                primaryAction = ACTION_RESTART
            )
        }
        val answered = s.answered[s.index]
        val selected = s.selections[s.index].toSet()
        val typed = q.typed
        val options = if (typed) emptyList() else q.options.take(MAX_OPTIONS).mapIndexed { i, label ->
            OptionUi(
                label = label,
                selected = i in selected,
                reveal = answered,
                correct = answered && (q.correctAnswers.contains(label.trim().lowercase()))
            )
        }
        val correct = if (answered && !typed) PracticeWidgetSessionFlow.gradeChoice(q, s.selections[s.index]) else false
        val lastAnswered = s.index == s.questions.lastIndex &&
            s.questions.indices.all { s.answered[it] || s.questions[it].typed }
        return RenderModel(
            title = s.subjectName,
            pill = context.getString(R.string.practice_pill_exam, s.index + 1, s.questions.size),
            prompt = q.prompt,
            options = options,
            revealAnswer = if (typed && s.revealed[s.index]) {
                context.getString(R.string.practice_answer, q.correctAnswers.joinToString(", "))
            } else null,
            hint = if (typed && !s.revealed[s.index]) {
                context.getString(R.string.practice_reveal_hint)
            } else if (answered && !typed) {
                context.getString(if (correct) R.string.practice_correct else R.string.practice_incorrect)
            } else null,
            feedbackColor = if (answered && correct) GREEN else if (answered) RED else 0,
            prevVisible = s.index > 0,
            primaryLabel = when {
                lastAnswered -> context.getString(R.string.practice_submit)
                typed && !s.revealed[s.index] -> context.getString(R.string.practice_reveal)
                q.multiple && selected.isNotEmpty() && !answered -> context.getString(R.string.practice_check)
                else -> context.getString(R.string.practice_next)
            },
            primaryAction = when {
                lastAnswered -> ACTION_SUBMIT
                typed && !s.revealed[s.index] -> ACTION_REVEAL
                q.multiple && selected.isNotEmpty() && !answered -> ACTION_CHECK
                else -> ACTION_NEXT
            }
        )
    }

    private fun placeholderModel(context: Context): RenderModel = RenderModel(
        title = context.getString(R.string.app_name),
        pill = "",
        prompt = "",
        primaryLabel = ""
    )

    fun render(context: Context, model: RenderModel, intents: ClickIntents): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_practice)

        v.setTextViewText(R.id.widget_practice_header_title, model.title)
        if (model.pill.isBlank()) {
            v.setViewVisibility(R.id.widget_practice_header_pill, View.GONE)
        } else {
            v.setTextViewText(R.id.widget_practice_header_pill, model.pill)
            v.setViewVisibility(R.id.widget_practice_header_pill, View.VISIBLE)
        }

        v.setTextViewText(R.id.widget_practice_prompt, model.prompt)

        // Options
        val optionCount = model.options.size
        for (i in 0 until MAX_OPTIONS) {
            val row = optionRowId(i)
            val text = optionTextId(i)
            val icon = optionIconId(i)
            if (i < optionCount) {
                val ui = model.options[i]
                v.setTextViewText(text, ui.label)
                v.setViewVisibility(row, View.VISIBLE)
                when {
                    ui.reveal && ui.correct -> {
                        v.setImageViewResource(icon, R.drawable.ic_widget_check)
                        v.setTextColor(text, GREEN)
                    }
                    ui.reveal && ui.selected -> {
                        v.setImageViewResource(icon, R.drawable.ic_widget_cross)
                        v.setTextColor(text, RED)
                    }
                    ui.selected -> v.setTextColor(text, NAVY)
                    else -> v.setTextColor(text, MUTED)
                }
            } else {
                v.setViewVisibility(row, View.GONE)
            }
        }

        // Answer / hint / feedback
        val answer = model.revealAnswer
        if (answer != null) {
            v.setTextViewText(R.id.widget_practice_answer, answer)
            v.setViewVisibility(R.id.widget_practice_answer, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.widget_practice_answer, View.GONE)
        }

        val hint = model.hint
        if (hint != null) {
            v.setTextViewText(R.id.widget_practice_feedback, hint)
            v.setTextColor(R.id.widget_practice_feedback, if (model.feedbackColor != 0) model.feedbackColor else MUTED)
            v.setViewVisibility(R.id.widget_practice_feedback, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.widget_practice_feedback, View.GONE)
        }

        // Footer
        intents.prev?.let {
            v.setOnClickPendingIntent(R.id.widget_practice_prev, it)
        }
        if (model.prevVisible) {
            v.setViewVisibility(R.id.widget_practice_prev, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.widget_practice_prev, View.INVISIBLE)
        }

        v.setTextViewText(R.id.widget_practice_next, model.primaryLabel.ifBlank { " " })
        if (intents.primary != null) {
            v.setOnClickPendingIntent(R.id.widget_practice_next, intents.primary)
        }

        if (intents.open != null) {
            v.setOnClickPendingIntent(R.id.widget_practice_open, intents.open)
        }

        // Body click (flashcard flip) + option clicks
        intents.body?.let {
            v.setOnClickPendingIntent(R.id.widget_practice_prompt, it)
        }
        intents.options.forEachIndexed { i, pi ->
            val row = optionRowId(i)
            if (i < MAX_OPTIONS) {
                v.setOnClickPendingIntent(row, pi)
            }
        }

        return v
    }

    fun renderEmpty(context: Context): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_practice)
        v.setTextViewText(R.id.widget_practice_header_title, context.getString(R.string.app_name))
        v.setViewVisibility(R.id.widget_practice_header_pill, View.GONE)
        v.setTextViewText(R.id.widget_practice_prompt, context.getString(R.string.practice_loading))
        for (i in 0 until MAX_OPTIONS) v.setViewVisibility(optionRowId(i), View.GONE)
        v.setViewVisibility(R.id.widget_practice_answer, View.GONE)
        v.setViewVisibility(R.id.widget_practice_feedback, View.GONE)
        v.setViewVisibility(R.id.widget_practice_prev, View.INVISIBLE)
        v.setTextViewText(R.id.widget_practice_next, " ")
        return v
    }

    private fun optionRowId(i: Int): Int = when (i) {
        0 -> R.id.widget_option_row_1
        1 -> R.id.widget_option_row_2
        2 -> R.id.widget_option_row_3
        3 -> R.id.widget_option_row_4
        else -> throw IllegalArgumentException("option index out of range: $i")
    }

    private fun optionTextId(i: Int): Int = when (i) {
        0 -> R.id.widget_option_text_1
        1 -> R.id.widget_option_text_2
        2 -> R.id.widget_option_text_3
        3 -> R.id.widget_option_text_4
        else -> throw IllegalArgumentException("option index out of range: $i")
    }

    private fun optionIconId(i: Int): Int = when (i) {
        0 -> R.id.widget_option_icon_1
        1 -> R.id.widget_option_icon_2
        2 -> R.id.widget_option_icon_3
        3 -> R.id.widget_option_icon_4
        else -> throw IllegalArgumentException("option index out of range: $i")
    }
}