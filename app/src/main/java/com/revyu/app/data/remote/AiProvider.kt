package com.revyu.app.data.remote

/**
 * AI providers the app can plug into for generation/planning. Each provider is defined by
 * stable metadata and nothing else - actual HTTP work is done by the matching client.
 *
 * The Smart Calendar planner is fully deterministic and offline today; this enum is the
 * seam where an AI-assisted planner ("move my sessions, I have an exam tomorrow") can be
 * added later without touching the scheduler or the Settings UI.
 */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val description: String
) {
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultModel = "openai/gpt-4o-mini",
        description = "One key for many models - used for reviewer & study set generation."
    )
}