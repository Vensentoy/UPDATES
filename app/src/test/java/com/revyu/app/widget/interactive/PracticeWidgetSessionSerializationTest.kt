package com.revyu.app.widget.interactive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeWidgetSessionSerializationTest {

    // Same config as PracticeWidgetSessionStore so persisted payloads stay compatible.
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun roundTripJSON(session: PracticeWidgetSession): String =
        json.encodeToString(PracticeWidgetSession.serializer(), session)

    private fun roundTrip(session: PracticeWidgetSession): PracticeWidgetSession =
        json.decodeFromString(PracticeWidgetSession.serializer(), roundTripJSON(session))

    @Test
    fun `flashcard survives a JSON round trip`() {
        val original = PracticeWidgetSession.Flashcard(
            widgetId = 7, pickedOn = "2026-09-18", title = "Wildlife", subjectName = "Biology",
            studySetId = "ss", items = listOf(CardFlash("f", "b"), CardFlash("f2", "b2"))
        ).copy(index = 1, showBack = true)
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `choice survives a JSON round trip`() {
        val original = PracticeWidgetSession.Choice(
            widgetId = 3, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
            questions = listOf(
                ChoiceQuestion("q1", "prompt", listOf("a", "b"), setOf("a"), multiple = false),
                ChoiceQuestion("q2", "multi", listOf("a", "b", "c"), setOf("a", "b"), multiple = true)
            )
        ).copy(
            index = 1,
            selections = listOf(listOf(0), listOf(0, 1)),
            answered = listOf(true, true),
            finished = true,
            correctCount = 1
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `qna survives a JSON round trip`() {
        val original = PracticeWidgetSession.Qna(
            widgetId = 4, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
            items = listOf(QnaItem("q1", "prompt", "answer 1"))
        ).copy(index = 0, revealed = listOf(true))
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `exam survives a JSON round trip`() {
        val original = PracticeWidgetSession.Exam(
            widgetId = 9, pickedOn = "2026-09-18", title = "t", subjectName = "s", studySetId = "ss",
            attemptId = "att-1",
            questions = listOf(
                ChoiceQuestion("q1", "prompt", listOf("True", "False"), setOf("true"), multiple = false),
                ChoiceQuestion("q2", "type it", emptyList(), setOf("x"), multiple = false, typed = true)
            )
        ).copy(
            index = 1,
            selections = listOf(listOf(0), emptyList()),
            answered = listOf(true, false),
            revealed = listOf(false, true),
            finished = true,
            correctCount = 1
        )
        assertEquals(original, roundTrip(original))
    }

    @Test
    fun `sealed discriminator is present in persisted json`() {
        val raw = roundTripJSON(
            PracticeWidgetSession.Flashcard(
                widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s",
                studySetId = "ss", items = listOf(CardFlash("f", "b"))
            )
        )
        val type = json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content
        assertEquals("flashcard", type)
    }

    @Test
    fun `decode tolerates unknown keys in a stored payload`() {
        val stored = """{"type":"flashcard","unknown":123,"widgetId":1,"pickedOn":"2026-09-18","title":"t","subjectName":"s","studySetId":"ss","items":[{"front":"f","back":"b"}]}"""
        val decoded = json.decodeFromString<PracticeWidgetSession>(stored)
        assertEquals(
            PracticeWidgetSession.Flashcard(
                widgetId = 1, pickedOn = "2026-09-18", title = "t", subjectName = "s",
                studySetId = "ss", items = listOf(CardFlash("f", "b"))
            ),
            decoded
        )
    }

    @Test
    fun `sessionKey prefixes widget ids per provider`() {
        assertEquals("flashcard_1", PracticeWidgetSessionStore.sessionKey("flashcard", 1))
        assertEquals("choice_2", PracticeWidgetSessionStore.sessionKey("choice", 2))
        assertTrue(
            PracticeWidgetSessionStore.sessionKey("flashcard", 1) !=
                PracticeWidgetSessionStore.sessionKey("choice", 1)
        )
    }
}