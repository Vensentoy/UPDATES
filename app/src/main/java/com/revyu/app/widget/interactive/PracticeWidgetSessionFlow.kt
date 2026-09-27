package com.revyu.app.widget.interactive

import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_CHECK
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FINISH
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FLIP
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_NEXT
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PICK_OPTION
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PREV
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_REVEAL
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_SUBMIT

object PracticeWidgetSessionFlow {

    fun apply(session: PracticeWidgetSession, action: String, optionIndex: Int): PracticeWidgetSession =
        when (session) {
            is PracticeWidgetSession.Flashcard -> applyFlashcard(session, action)
            is PracticeWidgetSession.Choice -> applyChoice(session, action, optionIndex)
            is PracticeWidgetSession.Qna -> applyQna(session, action)
            is PracticeWidgetSession.Exam -> applyExam(session, action, optionIndex)
        }

    private fun applyFlashcard(s: PracticeWidgetSession.Flashcard, action: String) = when (action) {
        ACTION_FLIP -> s.copy(showBack = !s.showBack)
        ACTION_NEXT -> if (s.index < s.items.lastIndex) s.copy(index = s.index + 1, showBack = false) else s
        ACTION_PREV -> if (s.index > 0) s.copy(index = s.index - 1, showBack = false) else s
        else -> s
    }

    private fun applyChoice(s: PracticeWidgetSession.Choice, action: String, optionIndex: Int): PracticeWidgetSession {
        if (s.finished || s.index !in s.questions.indices) return s
        val q = s.questions[s.index]
        return when (action) {
            ACTION_PICK_OPTION -> {
                if (s.answered[s.index]) return s
                val newSelections = s.selections.toMutableList()
                val newAnswered = s.answered.toMutableList()
                if (q.multiple) {
                    val current = newSelections[s.index].toMutableSet()
                    if (optionIndex in current) current.remove(optionIndex) else current.add(optionIndex)
                    newSelections[s.index] = current.toList()
                } else {
                    newSelections[s.index] = listOf(optionIndex)
                    newAnswered[s.index] = true
                }
                s.copy(selections = newSelections, answered = newAnswered)
            }
            ACTION_CHECK -> {
                if (!q.multiple || s.answered[s.index] || s.selections[s.index].isEmpty()) return s
                val newAnswered = s.answered.toMutableList()
                newAnswered[s.index] = true
                s.copy(answered = newAnswered)
            }
            ACTION_NEXT -> {
                if (s.index < s.questions.lastIndex) s.copy(index = s.index + 1) else s
            }
            ACTION_PREV -> {
                if (s.index > 0) s.copy(index = s.index - 1) else s
            }
            ACTION_FINISH -> {
                val correct = s.questions.indices.count { i ->
                    s.answered[i] && gradeChoice(s.questions[i], s.selections[i])
                }
                s.copy(finished = true, correctCount = correct)
            }
            else -> s
        }
    }

    private fun applyQna(s: PracticeWidgetSession.Qna, action: String) = when (action) {
        ACTION_REVEAL -> {
            val newRevealed = s.revealed.toMutableList()
            newRevealed[s.index] = true
            s.copy(revealed = newRevealed)
        }
        ACTION_NEXT -> if (s.index < s.items.lastIndex) s.copy(index = s.index + 1) else s
        ACTION_PREV -> if (s.index > 0) s.copy(index = s.index - 1) else s
        else -> s
    }

    private fun applyExam(s: PracticeWidgetSession.Exam, action: String, optionIndex: Int): PracticeWidgetSession {
        if (s.finished || s.index !in s.questions.indices) return s
        val q = s.questions[s.index]
        return when (action) {
            ACTION_PICK_OPTION -> {
                if (s.answered[s.index]) return s
                val newSelections = s.selections.toMutableList()
                val newAnswered = s.answered.toMutableList()
                if (q.multiple) {
                    val current = newSelections[s.index].toMutableSet()
                    if (optionIndex in current) current.remove(optionIndex) else current.add(optionIndex)
                    newSelections[s.index] = current.toList()
                } else if (q.typed) {
                    return s
                } else {
                    newSelections[s.index] = listOf(optionIndex)
                    newAnswered[s.index] = true
                }
                s.copy(selections = newSelections, answered = newAnswered)
            }
            ACTION_CHECK -> {
                if (!q.multiple || s.answered[s.index] || s.selections[s.index].isEmpty()) return s
                val newAnswered = s.answered.toMutableList()
                newAnswered[s.index] = true
                s.copy(answered = newAnswered)
            }
            ACTION_REVEAL -> {
                if (!q.typed) return s
                val newRevealed = s.revealed.toMutableList()
                newRevealed[s.index] = true
                s.copy(revealed = newRevealed)
            }
            ACTION_NEXT -> if (s.index < s.questions.lastIndex) s.copy(index = s.index + 1) else s
            ACTION_PREV -> if (s.index > 0) s.copy(index = s.index - 1) else s
            ACTION_SUBMIT, ACTION_FINISH -> {
                val correct = s.questions.indices.count { i ->
                    s.answered[i] && !s.questions[i].typed && gradeChoice(s.questions[i], s.selections[i])
                }
                s.copy(finished = true, correctCount = correct)
            }
            else -> s
        }
    }

    fun gradeChoice(q: ChoiceQuestion, selection: List<Int>): Boolean {
        if (q.typed) return false
        if (selection.isEmpty()) return false
        val picked = selection
            .mapNotNull { q.options.getOrNull(it) }
            .map { it.trim().lowercase() }
            .toSet()
        return picked == q.correctAnswers.map { it.trim().lowercase() }.toSet()
    }

    fun buildExamAnswers(s: PracticeWidgetSession.Exam): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        s.questions.indices.forEach { i ->
            if (s.answered[i] && !s.questions[i].typed) {
                val texts = s.selections[i]
                    .mapNotNull { s.questions[i].options.getOrNull(it) }
                map[s.questions[i].id] = texts
            }
        }
        return map
    }
}
