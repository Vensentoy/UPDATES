package com.revyu.app.data.prompt

import com.revyu.app.data.local.entities.DifficultyMix
import com.revyu.app.data.local.entities.QuestionType
import com.revyu.app.data.local.entities.ReviewerDetail
import com.revyu.app.data.remote.ChatMessage
import com.revyu.app.data.remote.GeneratedOutline
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptBuilder {
  private val json = Json { encodeDefaults = true }

  private val defaultAngles = listOf(
    "definition recall",
    "real-world scenario",
    "compare/contrast",
    "cause and effect",
    "order of steps / process",
    "which statement is FALSE",
    "worked example / calculation",
    "common misconception"
  )

  fun buildOutlineMessages(
        subjectName: String,
        sourceText: String,
    language: String
    ): List<ChatMessage> {
    return messages(
      system = """
        Create a concise topic outline for a college Study Set. Reply with JSON only:
        {"topics":[{"id":"t1","title":"string","importance":1,"keyPoints":["string"],"terms":["string"]}]}
        Use importance 1 for supporting, 2 for important, and 3 for core topics. Use stable short ids.
        Merge repeated ideas within this source group. Write titles and content in $language.
        Only include topics and facts supported by the source.
      """.trimIndent(),
      subjectName = subjectName,
      sourceLabel = "Source group (Markdown)",
      sourceText = sourceText
        )
    }

  fun buildReviewerMessages(
    subjectName: String,
    sourceText: String,
    language: String,
    detail: ReviewerDetail,
    outline: GeneratedOutline?,
    vaultExamLabel: String? = null
  ): List<ChatMessage> {
    val budget = when (detail) {
      ReviewerDetail.CONCISE -> "500-900 words"
      ReviewerDetail.STANDARD -> "900-1800 words"
      ReviewerDetail.DETAILED -> "1800-3500 words"
    }
    val topics = outline?.let { json.encodeToString(it) } ?: "No outline supplied; derive sections from the excerpts."
    val vaultInstruction = vaultExamLabel?.let {
      "This is a comprehensive $it reviewer. Consolidate overlapping concepts and cover each distinct topic."
    }.orEmpty()
    return messages(
      system = """
        You are writing a source-grounded college reviewer for Revyu. Return one valid JSON object only:
        {"title":"string","overview":"3-5 sentences","sections":[{"topicId":"t1","heading":"string","blocks":[{"type":"PARAGRAPH","text":"string"}]}],"glossary":[{"term":"string","meaning":"string"}],"cheatSheet":["one-line fact"]}
        Write in $language and target $budget for the whole reviewer; hard cap 3500 words.
        Every section starts with one PARAGRAPH sentence, then useful blocks, and ends with exactly one
        CALLOUT with kind REMEMBER. Use only these block types: PARAGRAPH, BULLETS, STEPS, DEFINITION,
        CALLOUT, TABLE, FORMULA. Use DEFINITION for bold-worthy terms, TABLE only for real comparisons,
        STEPS only for real sequences. CheatSheet has 8-12 concise facts. Do not use Markdown markers.
        Every claim must be supported by the provided source. $vaultInstruction
      """.trimIndent(),
      subjectName = subjectName,
      sourceLabel = "Relevant source excerpts",
      sourceText = sourceText,
      extra = "Outline topics:\n$topics"
    )
  }

  fun buildFlashcardMessages(
    subjectName: String,
    sourceText: String,
    language: String,
    count: Int,
    outline: GeneratedOutline?,
    avoidFlashcardFronts: List<String> = emptyList(),
    priorityTopics: List<String> = emptyList(),
    angles: List<String> = defaultAngles
  ): List<ChatMessage> {
    val topics = outline?.let { json.encodeToString(it) } ?: "No outline supplied. Cover the source proportionally."
    val avoidText = avoidFlashcardFronts.take(40).joinToString("\n") { "- ${it.take(90)}" }
    val priorityText = if (priorityTopics.isEmpty()) "" else "Prioritize the least-covered topics first: ${priorityTopics.joinToString(", ")}."
    val angleText = if (angles.isEmpty()) "" else "Use these angles in rotation: ${angles.joinToString(", ")}."
    return messages(
      system = """
        Generate exactly $count useful flashcards in $language. Return JSON only:
        {"flashcards":[{"front":"string","back":"string","hint":"string or empty","topicId":"t1","difficulty":1,"kind":"TERM|CONCEPT|COMPARE|PROCESS|FORMULA|CLOZE"}]}
        Test one idea per card. Fronts are real questions or clear terms, never "Explain X in detail".
        Keep each back to about 25 words. Mix definitions, why/how, compare/contrast, processes, formulas,
        and cloze cards. Cover every topic, scaling card coverage with topic importance. Avoid duplicates
        and cards that only restate another card's answer. Use only source-supported facts.
        Do NOT repeat or lightly reword any of these: ${avoidText.ifBlank { "none" }}. Test different facts or the same facts from a different angle.
        $priorityText $angleText
      """.trimIndent(),
      subjectName = subjectName,
      sourceLabel = "Relevant source excerpts",
      sourceText = sourceText,
      extra = "Outline topics:\n$topics"
    )
  }

  fun buildQuestionMessages(
    subjectName: String,
    sourceText: String,
    language: String,
    maxQuestions: Int,
    questionTypes: List<QuestionType>,
    difficultyMix: DifficultyMix,
    outline: GeneratedOutline?,
    vaultExamLabel: String? = null,
    avoidQuestionPrompts: List<String> = emptyList(),
    priorityTopics: List<String> = emptyList(),
    angles: List<String> = defaultAngles
  ): List<ChatMessage> {
    val types = questionTypes.joinToString(", ") { it.name }
    val mix = when (difficultyMix) {
      DifficultyMix.EASIER -> "50% easy, 35% medium, 15% hard"
      DifficultyMix.BALANCED -> "30% easy, 50% medium, 20% hard; at least 30% APPLY or ANALYZE"
      DifficultyMix.HARDER -> "15% easy, 45% medium, 40% hard"
    }
    val topics = outline?.let { json.encodeToString(it) } ?: "No outline supplied. Cover the source proportionally."
    val vaultInstruction = vaultExamLabel?.let { "This is for a comprehensive $it assessment." }.orEmpty()
    val avoidText = avoidQuestionPrompts.take(40).joinToString("\n") { "- ${it.take(90)}" }
    val priorityText = if (priorityTopics.isEmpty()) "" else "Prioritize the least-covered topics first: ${priorityTopics.joinToString(", ")}."
    val angleText = if (angles.isEmpty()) "" else "Rotate through these angles: ${angles.joinToString(", ")}."
    return messages(
      system = """
        Generate up to $maxQuestions questions in $language using only these types: $types. Return JSON only:
        {"questions":[{"type":"SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|IDENTIFICATION|SHORT_ANSWER","prompt":"string","options":["string"],"correctAnswers":["string"],"acceptedAnswers":["alternate wording"],"explanation":"1-2 sentences","topicId":"t1","difficulty":1,"level":"RECALL|UNDERSTAND|APPLY|ANALYZE"}]}
        Difficulty distribution: $mix. At least 30% APPLY/ANALYZE in Balanced. Choice distractors should be
        plausible, similar in grammar and length, and based on real misconceptions or neighboring source
        concepts. Never use "all of the above" or "none of the above". Avoid absolute giveaway words.
        Set difficulty to 1 for easy, 2 for medium, and 3 for hard.
        Explain why the answer is right and, for choice questions, address the most tempting wrong option.
        For IDENTIFICATION and SHORT_ANSWER, include a canonical answer and 2-4 accepted variants.
        Cover every topic proportionally to its importance, vary question angles, and test distinct facts.
        Do NOT repeat or lightly reword any of these: ${avoidText.ifBlank { "none" }}. Test different facts or the same facts from a different angle.
        $priorityText $angleText
        SINGLE_CHOICE has 4 options and 1 correct option. MULTIPLE_CHOICE has 4-6 options and at least 2
        correct options. TRUE_FALSE has empty options and exactly True or False as its answer.
        IDENTIFICATION/SHORT_ANSWER have empty options. $vaultInstruction
      """.trimIndent(),
      subjectName = subjectName,
      sourceLabel = "Relevant source excerpts",
      sourceText = sourceText,
      extra = "Allowed types: $types\nOutline topics:\n$topics"
    )
  }

  fun buildPlainReviewerMessages(subjectName: String, sourceText: String, language: String): List<ChatMessage> =
    messages(
      system = """
        Write a well-organized plain-text study reviewer in $language. Use short headings on their own lines,
        then concise paragraphs and bullet points. Do not return JSON or Markdown heading markers. Use only
        facts supported by the source, and keep the reviewer useful for studying.
      """.trimIndent(),
      subjectName = subjectName,
      sourceLabel = "Relevant source excerpts",
      sourceText = sourceText
    )

  private fun messages(
    system: String,
    subjectName: String,
    sourceLabel: String,
    sourceText: String,
    extra: String = ""
  ): List<ChatMessage> = listOf(
    ChatMessage(role = "system", content = system),
    ChatMessage(
      role = "user",
      content = buildString {
        appendLine("Subject: $subjectName")
        if (extra.isNotBlank()) {
          appendLine()
          appendLine(extra)
        }
        appendLine()
        appendLine("$sourceLabel:")
        appendLine("---")
        appendLine(sourceText)
        appendLine("---")
      }.trim()
    )
  )
}