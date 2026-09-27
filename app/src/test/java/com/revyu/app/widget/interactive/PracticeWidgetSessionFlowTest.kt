package com.revyu.app.widget.interactive

import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_CHECK
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FINISH
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_FLIP
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_NEXT
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PICK_OPTION
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_PREV
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_REVEAL
import com.revyu.app.widget.interactive.InteractiveWidgetActions.ACTION_SUBMIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeWidgetSessionFlowTest {

    private fun card(front: String, back: String) = CardFlash(front, back)

    private fun question(id: String, multiple: Boolean = false) = ChoiceQuestion(
        id = id,
        prompt = "prompt $id",
        options = listOf("a", "b", "c"),
        correctAnswers = setOf("a"),
        multiple = multiple
    )

    private fun flashcard() = PracticeWidgetSession.Flashcard(
        widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
        items = listOf(card("front0", "back0"), card("front1", "back1"))
    )

    private fun choice() = PracticeWidgetSession.Choice(
        widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
        questions = listOf(question("q1"), question("q2"))
    )

    private fun qna() = PracticeWidgetSession.Qna(
        widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
        items = listOf(QnaItem("q1", "prompt", "answer1"))
    )

    private fun exam() = PracticeWidgetSession.Exam(
        widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
        attemptId = "att", questions = listOf(question("q1"), question("q2"))
    )

    // ---------- Flashcard ----------

    @Test
    fun `flashcard flip toggles showBack`() {
        var s = flashcard()
        assertFalse(s.showBack)
        s = PracticeWidgetSessionFlow.apply(s, ACTION_FLIP, -1) as PracticeWidgetSession.Flashcard
        assertTrue(s.showBack)
        s = PracticeWidgetSessionFlow.apply(s, ACTION_FLIP, -1) as PracticeWidgetSession.Flashcard
        assertFalse(s.showBack)
    }

    @Test
    fun `flashcard next advances and resets showBack`() {
        var s = flashcard()
        s = PracticeWidgetSessionFlow.apply(s, ACTION_FLIP, -1) as PracticeWidgetSession.Flashcard
        s = PracticeWidgetSessionFlow.apply(s, ACTION_NEXT, -1) as PracticeWidgetSession.Flashcard
        assertEquals(1, s.index)
        assertFalse(s.showBack)
    }

    @Test
    fun `flashcard next at last card stays put`() {
        var s = flashcard()
        s = PracticeWidgetSessionFlow.apply(s, ACTION_NEXT, -1) as PracticeWidgetSession.Flashcard
        s = PracticeWidgetSessionFlow.apply(s, ACTION_NEXT, -1) as PracticeWidgetSession.Flashcard
        assertEquals(1, s.index)
    }

    @Test
    fun `flashcard prev at first card stays put`() {
        val s = PracticeWidgetSessionFlow.apply(flashcard(), ACTION_PREV, -1) as PracticeWidgetSession.Flashcard
        assertEquals(0, s.index)
    }

    // ---------- Choice ----------

    @Test
    fun `single choice pick answers immediately`() {
        val s = PracticeWidgetSessionFlow.apply(choice(), ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Choice
        assertTrue(s.answered[0])
        assertEquals(listOf(0), s.selections[0])
    }

    @Test
    fun `picking on an answered question is ignored`() {
        var s = choice()
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Choice
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 1) as PracticeWidgetSession.Choice
        assertEquals(listOf(0), s.selections[0])
        assertFalse(s.answered[1])
    }

    @Test
    fun `multiple choice toggles then check locks the answer`() {
        val multiple = choice().copy(questions = listOf(question("q1", multiple = true)))
        var s = multiple
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Choice
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 1) as PracticeWidgetSession.Choice
        assertEquals(setOf(0, 1), s.selections[0].toSet())
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Choice
        assertEquals(setOf(1), s.selections[0].toSet())
        s = PracticeWidgetSessionFlow.apply(s, ACTION_CHECK, -1) as PracticeWidgetSession.Choice
        assertTrue(s.answered[0])
    }

    @Test
    fun `check with no selection does nothing`() {
        val s = PracticeWidgetSessionFlow.apply(choice(), ACTION_CHECK, -1) as PracticeWidgetSession.Choice
        assertFalse(s.answered[0])
    }

    @Test
    fun `finish counts only answered correct`() {
        var s = choice()
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Choice // q1 -> {"a"}
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 1) as PracticeWidgetSession.Choice // q2 -> {"b"} wrong
        s = PracticeWidgetSessionFlow.apply(s, ACTION_FINISH, -1) as PracticeWidgetSession.Choice
        assertTrue(s.finished)
        assertEquals(1, s.correctCount)
        assertEquals(2, s.totalQuestions)
    }

    @Test
    fun `choice next beyond last is a no-op`() {
        val one = choice().copy(questions = listOf(question("q1")))
        val s = PracticeWidgetSessionFlow.apply(one, ACTION_NEXT, -1) as PracticeWidgetSession.Choice
        assertEquals(0, s.index)
    }

    // ---------- Q&A ----------

    @Test
    fun `qna reveal then next`() {
        var s = qna()
        val r = PracticeWidgetSessionFlow.apply(s, ACTION_REVEAL, -1) as PracticeWidgetSession.Qna
        assertTrue(r.revealed[0])
        assertFalse(r.revealed[0].not())
    }

    @Test
    fun `qna prev at first stays put`() {
        val s = PracticeWidgetSessionFlow.apply(qna(), ACTION_PREV, -1) as PracticeWidgetSession.Qna
        assertEquals(0, s.index)
    }

    // ---------- Exam ----------

    @Test
    fun `exam submit finishes and counts only answered correct`() {
        var s = exam()
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Exam // q1 correct
        s = PracticeWidgetSessionFlow.apply(s, ACTION_NEXT, -1) as PracticeWidgetSession.Exam
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 1) as PracticeWidgetSession.Exam // q2 wrong
        s = PracticeWidgetSessionFlow.apply(s, ACTION_SUBMIT, -1) as PracticeWidgetSession.Exam
        assertTrue(s.finished)
        assertEquals(1, s.correctCount)
        assertEquals(2, s.totalQuestions)
    }

    @Test
    fun `exam typed question ignores picks and only reveals`() {
        val typed = exam().copy(questions = listOf(question("q1").copy(typed = true)))
        var s = typed
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Exam
        assertFalse(s.answered[0])
        assertEquals(emptyList<Int>(), s.selections[0])
        s = PracticeWidgetSessionFlow.apply(s, ACTION_REVEAL, -1) as PracticeWidgetSession.Exam
        assertTrue(s.revealed[0])
    }

    @Test
    fun `exam reveal is ignored for non typed`() {
        val s = PracticeWidgetSessionFlow.apply(exam(), ACTION_REVEAL, -1) as PracticeWidgetSession.Exam
        assertFalse(s.revealed[0])
    }

    // ---------- Grading helpers ----------

    @Test
    fun `gradeChoice single correct set`() {
        val s = choice()
        val q = s.questions[0]
        assertTrue(PracticeWidgetSessionFlow.gradeChoice(q, listOf(0)))
        assertFalse(PracticeWidgetSessionFlow.gradeChoice(q, listOf(1)))
        assertFalse(PracticeWidgetSessionFlow.gradeChoice(q, listOf(0, 1)))
        assertFalse(PracticeWidgetSessionFlow.gradeChoice(q, emptyList()))
    }

    @Test
    fun `gradeChoice multiple requires exact set`() {
        val q = question("q1", multiple = true).copy(correctAnswers = setOf("a", "b"))
        assertFalse(PracticeWidgetSessionFlow.gradeChoice(q, listOf(0)))
        assertTrue(PracticeWidgetSessionFlow.gradeChoice(q, listOf(0, 1)))
        assertFalse(PracticeWidgetSessionFlow.gradeChoice(q, listOf(0, 1, 2)))
    }

    @Test
    fun `buildExamAnswers only includes answered non typed`() {
        val typed = question("q2").copy(typed = true)
        var s = exam().copy(questions = listOf(question("q1"), typed))
        s = PracticeWidgetSessionFlow.apply(s, ACTION_PICK_OPTION, 0) as PracticeWidgetSession.Exam
        val answers = PracticeWidgetSessionFlow.buildExamAnswers(s)
        assertEquals(listOf("a"), answers["q1"])
        assertFalse("typed must be excluded", answers.containsKey("q2"))
        assertEquals(1, answers.size)
    }
}