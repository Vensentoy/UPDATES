package com.revyu.app.core.theme

import androidx.compose.ui.graphics.Color
import com.revyu.app.core.preferences.AccentColor

/*
 * REVYU design tokens — grounded in the physical artifacts of Filipino student study culture:
 * a printed "reviewer," a highlighter, a manila-folder tab, highlighter margin rules on bond
 * paper. Lifted verbatim from the 2026 prototype (revyu-prototype.html) so the Compose app
 * is the same warm-cream, orange-accent, mascot-led product the prototype ships.
 *
 * The prototype's neutral tokens map 1:1 here:
 *   --bg        -> PaperDim
 *   --surface   -> Paper
 *   --card      -> PaperElevated
 *   --text      -> InkNavy
 *   --text-soft -> InkNavyMuted
 *   --text-faint-> InkNavyFaint
 *   --line      -> MarginGray
 *   --accent (+ 4 swatches) -> AccentPalettes (orange/green/purple/blue)
 */

/* ==================================================================== */
/* Neutral core (light) — prototype tokens                               */
/* ==================================================================== */

/** Page background — warm cream (prototype --bg #FBF4E2). */
val PaperDim = Color(0xFFFBF4E2)
/** Dimmed surface used behind cards on the cream page. */
val PaperDimMuted = Color(0xFFF7EDD9)
/** Solid card / elevated surface (--surface / --card). */
val Paper = Color(0xFFFFFDF8)
/** Brightest elevated card, "lifted" element. */
val PaperElevated = Color(0xFFFFFFFF)
/** Chosen/active chip surface. */
val PaperTint = Color(0xFFFCF2E2)

/* ------------------------------------------------------------------ */
/* Text / ink — warm brown family (--text #4A2E1E)                     */
/* ------------------------------------------------------------------ */
val InkNavy = Color(0xFF4A2E1E)          // primary ink (deep warm brown)
val InkNavyMuted = Color(0xFF8B7458)     // --text-soft secondary
val InkNavyFaint = Color(0xFFB8A891)     // --text-faint placeholders

/* ------------------------------------------------------------------ */
/* Lines / outlines (--line #F0E4CE)                                   */
/* ------------------------------------------------------------------ */
val MarginGray = Color(0xFFF0E4CE)                       // hairline dividers
val MarginGrayLight = Color(0xFFF7F0DE)                  // softer hairline

/* ==================================================================== */
/* Semantic accents (light) — prototype school-calendar colors           */
/* ==================================================================== */

/** FolderCoral — the "folder" accent (prototype orange swatch default). */
val FolderCoral = Color(0xFFED8347)
val FolderCoralMuted = Color(0xFFFBE4D2)   // accent container / tint
val FolderCoralDark = Color(0xFFD96B2C)    // pressed / darker

/** PassGreen — "ok / passed / done" green. */
val PassGreen = Color(0xFF7FA87A)
val PassGreenMuted = Color(0xFFE3EFDF)

/** HighlighterYellow — highlight / study-plan accents. */
val HighlighterYellow = Color(0xFFF4C430)
val HighlighterYellowMuted = Color(0xFFFBE9AE)

/** Error rust — warm error red. */
val ErrorRust = Color(0xFFB3452F)
val ErrorRustMuted = Color(0xFFF3DAD3)

/* ==================================================================== */
/* Subject accent rotation — distinct stable folder accents per subject */
/* ==================================================================== */

/**
 * Six distinct accents rotated among subjects (and the 4 theme accents share the first
 * swatches so a subject folder can visually match the app accent). Prototype swatch set:
 * green / purple / orange / blue, plus pink + highlighter for subjects beyond four.
 */
val SubjectAccents = listOf(
    FolderCoral,        // orange
    PassGreen,
    Color(0xFF83A9CD),  // blue
    Color(0xFFB79BD1),  // purple
    Color(0xFFE7A6A0),  // pink
    HighlighterYellow
)

/* ==================================================================== */
/* Dark scheme — warm late-night ink-navy, paper tones inverted          */
/* ==================================================================== */
val DarkInkNavy = Color(0xFF0F1830)
val DarkSurface = Color(0xFF16213B)
val DarkSurfaceVariant = Color(0xFF1F2C4C)
val DarkInkNavyMuted = Color(0xFF243352)
val DarkText = Color(0xFFF1ECE1)
val DarkTextMuted = Color(0xFFB9B2A2)
val DarkOutline = Color(0xFF54607E)
val DarkFolderCoral = Color(0xFFF08168)
val DarkFolderCoralContainer = Color(0xFF5E2F22)
val DarkPassGreen = Color(0xFF82C8A2)
val DarkPassGreenContainer = Color(0xFF244B36)
val DarkHighlighter = Color(0xFFF2D27B)
val DarkError = Color(0xFFFFB4AB)
val DarkErrorContainer = Color(0xFF93000A)

/* ==================================================================== */
/* Accent-color system — the four prototype theme swatches               */
/* ==================================================================== */

/**
 * Per-accent token set used to build a light or dark [ColorScheme] from [AccentColor].
 * Mirrors the prototype's active/swatch/container structure so a theme change feels like
 * switching the highlighter, not reskinning the app.
 */
data class AccentPalette(
    val id: AccentColor,
    val accent: Color,          // light scheme primary
    val onAccent: Color,        // text on primary (light)
    val accentContainer: Color, // light scheme primaryContainer
    val onAccentContainer: Color,
    val accentDark: Color,      // dark scheme primary (lighter, pops on ink-navy)
    val onAccentDark: Color,
    val accentContainerDark: Color,
    val onAccentContainerDark: Color
)

val AccentPalettes = mapOf(
    AccentColor.ORANGE to AccentPalette(
        id = AccentColor.ORANGE,
        accent = Color(0xFFED8347),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFFBE4D2),
        onAccentContainer = Color(0xFF4A2E1E),
        accentDark = Color(0xFFF0925E),
        onAccentDark = Color(0xFF4A2E1E),
        accentContainerDark = Color(0xFF5A3420),
        onAccentContainerDark = Color(0xFFFBE4D2)
    ),
    AccentColor.GREEN to AccentPalette(
        id = AccentColor.GREEN,
        accent = Color(0xFF7FA87A),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFE3EFDF),
        onAccentContainer = Color(0xFF4A2E1E),
        accentDark = Color(0xFF95B890),
        onAccentDark = Color(0xFF223522),
        accentContainerDark = Color(0xFF2C4432),
        onAccentContainerDark = Color(0xFFE3EFDF)
    ),
    AccentColor.PURPLE to AccentPalette(
        id = AccentColor.PURPLE,
        accent = Color(0xFFB79BD1),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFEFE6F5),
        onAccentContainer = Color(0xFF4A2E1E),
        accentDark = Color(0xFFC9B3DD),
        onAccentDark = Color(0xFF3A2948),
        accentContainerDark = Color(0xFF46345F),
        onAccentContainerDark = Color(0xFFEFE6F5)
    ),
    AccentColor.BLUE to AccentPalette(
        id = AccentColor.BLUE,
        accent = Color(0xFF83A9CD),
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFE2EBF3),
        onAccentContainer = Color(0xFF4A2E1E),
        accentDark = Color(0xFFA3C1DE),
        onAccentDark = Color(0xFF223A4E),
        accentContainerDark = Color(0xFF2A4560),
        onAccentContainerDark = Color(0xFFE2EBF3)
    )
)

/** Convenience accessor used by the Compose theme. */
val AccentColor.palette: AccentPalette get() = AccentPalettes.getValue(this)
