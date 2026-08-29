package tachiyomi.source.local.io.novel

import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

fun markdownToHtml(markdownText: String): String {
    if (markdownText.isBlank()) return "<html><body></body></html>"
    val flavour = CommonMarkFlavourDescriptor()
    val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownText)
    val body = HtmlGenerator(markdownText, tree, flavour).generateHtml()
    return "<html><body>$body</body></html>"
}
