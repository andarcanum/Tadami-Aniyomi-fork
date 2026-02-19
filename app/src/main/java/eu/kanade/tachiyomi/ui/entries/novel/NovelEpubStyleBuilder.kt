package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import tachiyomi.domain.entries.novel.model.Novel

internal object NovelEpubStyleBuilder {

    internal data class ThemeColors(
        val background: String,
        val text: String,
    )

    fun buildStylesheet(
        settings: NovelReaderSettings,
        sourceId: Long,
        applyReaderTheme: Boolean,
        includeCustomCss: Boolean,
        linkColor: String,
    ): String? {
        val themeStyles = if (applyReaderTheme) {
            val colors = resolveThemeColors(settings)
            buildString {
                appendLine("html {")
                appendLine("  scroll-behavior: smooth;")
                appendLine("  overflow-x: hidden;")
                appendLine("  word-wrap: break-word;")
                appendLine("}")
                appendLine("body {")
                appendLine("  padding-left: ${settings.margin}px;")
                appendLine("  padding-right: ${settings.margin}px;")
                appendLine("  padding-bottom: 40px;")
                appendLine("  font-size: ${settings.fontSize}px;")
                appendLine("  color: ${colors.text};")
                appendLine("  text-align: ${settings.textAlign.name.lowercase()};")
                appendLine("  line-height: ${settings.lineHeight};")
                if (settings.fontFamily.isNotBlank()) {
                    appendLine("  font-family: \"${settings.fontFamily}\";")
                }
                appendLine("  background-color: ${colors.background};")
                appendLine("}")
                appendLine("hr {")
                appendLine("  margin-top: 20px;")
                appendLine("  margin-bottom: 20px;")
                appendLine("}")
                appendLine("a {")
                appendLine("  color: $linkColor;")
                appendLine("}")
                appendLine("img {")
                appendLine("  display: block;")
                appendLine("  width: auto;")
                appendLine("  height: auto;")
                appendLine("  max-width: 100%;")
                appendLine("}")
            }
        } else {
            ""
        }

        val customStyles = if (includeCustomCss) {
            sanitizeSourceScopedCss(
                css = settings.customCSS,
                sourceId = sourceId,
            )
        } else {
            ""
        }

        val combined = (themeStyles + customStyles).trim()
        return combined.ifBlank { null }
    }

    fun buildJavaScript(
        settings: NovelReaderSettings,
        novel: Novel,
        includeCustomJs: Boolean,
    ): String? {
        if (!includeCustomJs) return null
        if (settings.customJS.isBlank()) return null

        return buildString {
            appendLine("let novelName = \"${escapeJsString(novel.title)}\";")
            appendLine("let sourceId = ${novel.source};")
            appendLine("let novelId = ${novel.id};")
            appendLine()
            append(settings.customJS)
        }.trim().ifBlank { null }
    }

    fun resolveThemeColors(settings: NovelReaderSettings): ThemeColors {
        val resolvedBackground = settings.backgroundColor
            ?.takeIf { it.isNotBlank() }
            ?: when (settings.theme) {
                NovelReaderTheme.LIGHT -> "#FFFFFF"
                NovelReaderTheme.DARK -> "#121212"
                NovelReaderTheme.SYSTEM -> "#121212"
            }

        val resolvedText = settings.textColor
            ?.takeIf { it.isNotBlank() }
            ?: when (settings.theme) {
                NovelReaderTheme.LIGHT -> "#212121"
                NovelReaderTheme.DARK -> "#EAEAEA"
                NovelReaderTheme.SYSTEM -> "#EAEAEA"
            }

        return ThemeColors(
            background = resolvedBackground,
            text = resolvedText,
        )
    }

    private fun sanitizeSourceScopedCss(
        css: String,
        sourceId: Long,
    ): String {
        if (css.isBlank()) return ""

        val sourceScopeRegex = Regex("#sourceId-$sourceId\\s*\\{", RegexOption.IGNORE_CASE)
        val selectorCleanupRegex = Regex("#sourceId-$sourceId[^.#A-Z]*", RegexOption.IGNORE_CASE)

        return css
            .replace(sourceScopeRegex, "body {")
            .replace(selectorCleanupRegex, "")
    }

    private fun escapeJsString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
