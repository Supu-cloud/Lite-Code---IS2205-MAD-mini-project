package com.texteditor.project.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.texteditor.project.data.HighlightConfig

fun highlightWithConfig(code: String, cfg: HighlightConfig): AnnotatedString {
    val baseColor = Color(android.graphics.Color.parseColor(cfg.colors["base"] ?: "#FFFFFF"))
    val keywordColor = Color(android.graphics.Color.parseColor(cfg.colors["keyword"] ?: "#569CD6"))
    val commentColor = Color(android.graphics.Color.parseColor(cfg.colors["comment"] ?: "#6A9955"))
    val stringColor = Color(android.graphics.Color.parseColor(cfg.colors["string"] ?: "#D69D85"))
    val numberColor = Color(android.graphics.Color.parseColor(cfg.colors["number"] ?: "#B5CEA8"))

    // Additional colors for Web
    val tagColor = Color(0xFF569CD6) // blue
    val attrColor = Color(0xFF9CDCFE) // light blue
    val selectorColor = Color(0xFFD7BA7D) // yellow-orange

    val builder = AnnotatedString.Builder(code)
    builder.addStyle(SpanStyle(color = baseColor), 0, code.length)

    when (cfg.language.lowercase()) {
        "html" -> {
            // HTML Tags: <tag, </tag, >
            Regex("</?[a-zA-Z0-9]+|/?>").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = tagColor), it.range.first, it.range.last + 1)
            }
            // HTML Attributes (within tags)
            Regex("\\b[a-zA-Z0-9-]+(?=\\=)").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = attrColor), it.range.first, it.range.last + 1)
            }
            // Strings (attr values)
            Regex("\"[^\"]*\"").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
            }
            // Comments
            Regex("<!--[\\s\\S]*?-->").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        "css" -> {
            // Selectors (everything before {)
            Regex("[^{]+(?=\\{)").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = selectorColor), it.range.first, it.range.last + 1)
            }
            // Properties (within {})
            Regex("[a-zA-Z0-9-]+(?=\\s*:)").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
            }
            // Comments
            Regex("/\\*[\\s\\S]*?\\*/").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
            }
        }
        else -> {
            // Default (Kotlin/JS/Java/Python style)
            // Keywords
            if (cfg.keywords.isNotEmpty()) {
                val kwRegex = Regex("\\b(${cfg.keywords.joinToString("|")})\\b")
                kwRegex.findAll(code).forEach {
                    builder.addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
                }
            }

            // Single-line comments
            if (cfg.comment.isNotEmpty()) {
                Regex("${Regex.escape(cfg.comment)}.*", RegexOption.MULTILINE).findAll(code).forEach {
                    builder.addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
                }
            }

            // Strings for each delimiter
            cfg.stringDelimiters.forEach { delim ->
                Regex("$delim([^$delim\\\\]|\\\\.)*$delim").findAll(code).forEach {
                    builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
                }
            }

            // Numbers
            Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
            }
        }
    }

    // Custom Word Colors (apply globally)
    cfg.customColors.forEach { (word, hex) ->
        runCatching {
            val color = Color(android.graphics.Color.parseColor(hex))
            Regex("\\b${Regex.escape(word)}\\b").findAll(code).forEach {
                builder.addStyle(SpanStyle(color = color), it.range.first, it.range.last + 1)
            }
        }
    }

    // Selective Style Spans
    cfg.styleSpans.forEach { span ->
        val start = span.start.coerceIn(0, code.length)
        val end = span.end.coerceIn(0, code.length)
        if (start < end) {
            builder.addStyle(
                SpanStyle(
                    fontWeight = if (span.fontWeight == "Bold") FontWeight.Bold else null,
                    fontStyle = if (span.fontStyle == "Italic") FontStyle.Italic else null,
                    fontSize = span.fontSize?.sp ?: TextUnit.Unspecified,
                    fontFamily = when (span.fontFamily) {
                        "Monospace" -> FontFamily.Monospace
                        "Serif" -> FontFamily.Serif
                        "Sans Serif" -> FontFamily.SansSerif
                        "Cursive" -> FontFamily.Cursive
                        else -> null
                    },
                    color = span.color?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.Unspecified,
                    textDecoration = if (span.underline) TextDecoration.Underline else null
                ),
                start, end
            )
        }
    }

    return builder.toAnnotatedString()
}
