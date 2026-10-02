# Project: REVYU (Android, Kotlin, Jetpack Compose, Material 3)
- Package: com.revyu.app. UI code lives under app/src/main/java/com/revyu/app/ui/.
- Always use the existing theme (MaterialTheme colors, typography). No hardcoded colors.
- Selectable option rows must use FlowRow or equal-weight layouts, never a plain Row that can overflow.
- Chip and button labels: maxLines = 1 unless the text is meant to wrap.
- Reuse components from ui/components/ before creating new ones.
- Support light and dark mode and all four accent colors (Orange, Green, Purple, Blue).
- Keep touch targets at least 48dp. Keep changes minimal and do not refactor unrelated code.
- After edits, run ./gradlew :app:compileDebugKotlin.
