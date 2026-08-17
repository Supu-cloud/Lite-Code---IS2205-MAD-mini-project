package com.texteditor.project.ui

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import com.texteditor.project.data.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.google.gson.Gson
import com.texteditor.project.data.CompileResponse
import com.texteditor.project.data.HighlightConfig
import com.texteditor.project.data.RichTextData
import com.texteditor.project.data.StyleSpan
import com.texteditor.project.database.AppDatabase
import com.texteditor.project.database.entity.FileVersion
import com.texteditor.project.database.entity.RecentFile
import com.texteditor.project.database.entity.Snippet
import com.texteditor.project.network.CompilerClient
import com.texteditor.project.util.LocalCodeExecutor
import com.texteditor.project.util.highlightWithConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ---------------------- Models ----------------------

enum class WorkspaceMode { KOTLIN_COMPILER, KOTLIN_WRITER, JAVA_WRITER, PYTHON_WRITER, C_WRITER, CPP_WRITER, RICH_TEXT, MARKDOWN, WEB }

data class ConsoleLog(val message: String, val level: LogLevel, val source: String = "", val line: Int = 0)
enum class LogLevel { INFO, ERROR, WARN }

// ---------------------- Theme ----------------------

@SuppressLint("UnusedBoxWithConstraintsScope", "SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(themeViewModel: ThemeViewModel, onShowInfo: () -> Unit) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(ctx) }
    val recentFiles by db.recentFileDao().getAllRecentFiles().collectAsState(initial = emptyList())
    val snippets by db.snippetDao().getAllSnippets().collectAsState(initial = emptyList())

    val focusRequester = remember { FocusRequester() }
    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    // ---------- Global State ----------
    val currentTheme by themeViewModel.themeState.collectAsState()
    val serverUrl by themeViewModel.serverUrlState.collectAsState()
    val isDark = when(currentTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    var workspaceMode by rememberSaveable { mutableStateOf(WorkspaceMode.KOTLIN_COMPILER) }
    var isSoftWrapEnabled by rememberSaveable { mutableStateOf(true) }

    // Find and Replace state
    var showSearchPanel by rememberSaveable { mutableStateOf(false) }
    var showRecentFiles by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSnippetLibrary by rememberSaveable { mutableStateOf(false) }
    
    var snippetTitleInput by remember { mutableStateOf("") }
    var showAddSnippetDialog by remember { mutableStateOf(false) }

    var findQuery by rememberSaveable { mutableStateOf("") }
    var replaceQuery by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var currentMatchIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showReplaceAllWarning by remember { mutableStateOf(false) }

    // Formatting state
    var showFormattingBar by rememberSaveable { mutableStateOf(false) }
    var selectedFontWeight by rememberSaveable { mutableStateOf("Normal") }
    var selectedFontStyle by rememberSaveable { mutableStateOf("Normal") }
    var selectedFontFamilyName by rememberSaveable { mutableStateOf("Default") }
    var selectedFontSize by rememberSaveable { mutableStateOf(16f) }
    var selectedBaseColorHex by rememberSaveable { mutableStateOf("") }
    var selectedUnderline by rememberSaveable { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme

    // ---- Syntax Highlighting Factory ----
        fun getHighlightConfigFor(lang: String, dark: Boolean): HighlightConfig {
            val base = if (dark) "#E5E7EB" else "#111827"
            val kw = if (dark) "#8B93FF" else "#1F4B99"
            val comm = if (dark) "#6A9955" else "#2F7D32"
            val str = if (dark) "#D69D85" else "#B55339"
            val num = if (dark) "#B5CEA8" else "#2F6F3E"

            return when (lang.lowercase()) {
                "html" -> HighlightConfig("html", emptyList(), "<!--", listOf("\""), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "css" -> HighlightConfig("css", emptyList(), "/*", listOf("'"), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "javascript", "js" -> HighlightConfig("javascript", listOf("var", "let", "const", "function", "if", "else", "for", "while", "return", "class", "new", "this", "try", "catch", "async", "await"), "//", listOf("\"", "'", "`"), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "kotlin" -> HighlightConfig("kotlin", listOf("fun","class","val","var","when","if","else","for","while","return","import","package","null","true","false","this"), "//", listOf("\"", "'"), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "java" -> HighlightConfig("java", listOf("public","private","protected","static","final","class","interface","enum","extends","implements","import","package","return","if","else","for","while","new","null","true","false","this","void","int","long","float","double","boolean","char","byte","short"), "//", listOf("\""), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "python" -> HighlightConfig("python", listOf("def","class","if","else","elif","for","while","return","import","from","as","try","except","finally","with","lambda","yield","in","is","not","and","or","None","True","False","pass","break","continue"), "#", listOf("\"", "'"), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "c", "cpp" -> HighlightConfig("cpp", listOf("int","long","float","double","char","void","bool","struct","class","public","private","protected","template","typename","operator","new","delete","return","if","else","for","while","do","switch","case","break","continue","goto","using","namespace","include","define"), "//", listOf("\""), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                "markdown", "md" -> HighlightConfig("markdown", emptyList(), "", emptyList(), mapOf("base" to base, "keyword" to kw, "comment" to comm, "string" to str, "number" to num))
                else -> HighlightConfig("text", emptyList(), "", emptyList(), mapOf("base" to base, "keyword" to base, "comment" to base, "string" to base, "number" to base))
            }
        }

        // ---- Templates ----
        val ktTemplate = "fun main() {\n    println(\"Hello World\")\n}"
        val javaTemplate = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World\");\n    }\n}"
        val pyTemplate = "print(\"Hello World\")"
        val cTemplate = "#include <stdio.h>\n\nint main() {\n    printf(\"Hello World\\n\");\n    return 0;\n}"
        val cppTemplate = "#include <iostream>\n\nint main() {\n    std::cout << \"Hello World\" << std::endl;\n    return 0;\n}"
        val mdTemplate = "# Hello Markdown\n\nThis is a markdown file."
        val htmlTemplate = "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"UTF-8\">\n    <title>LiteCode Preview</title>\n</head>\n<body>\n    <h1>Hello from LiteCode!</h1>\n    <p>Edit the HTML, CSS and JavaScript, then press Run.</p>\n</body>\n</html>"
        val cssTemplate = "body {\n    margin: 0;\n    padding: 24px;\n    font-family: Arial, sans-serif;\n    background: #f5f7fb;\n    color: #1f2937;\n}\n\nh1 {\n    color: #4f46e5;\n}"
        val jsTemplate = "console.log(\"LiteCode JavaScript is running!\");"

        // ---------- States ----------
        var kotlinText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(ktTemplate)) }
        var javaText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(javaTemplate)) }
        var pyText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(pyTemplate)) }
        var cText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(cTemplate)) }
        var cppText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(cppTemplate)) }
        var htmlText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(htmlTemplate)) }
        var cssText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(cssTemplate)) }
        var jsText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(jsTemplate)) }
        var markdownText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(mdTemplate)) }
        var plainText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
        
        var fileName by rememberSaveable { mutableStateOf("Main.kt") }
        var currentFileUri by rememberSaveable { mutableStateOf<String?>(null) }
        
        var activeWebTab by rememberSaveable { mutableStateOf(0) } 

        val currentTextValue = when (workspaceMode) {
            WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText
            WorkspaceMode.JAVA_WRITER -> javaText
            WorkspaceMode.PYTHON_WRITER -> pyText
            WorkspaceMode.C_WRITER -> cText
            WorkspaceMode.CPP_WRITER -> cppText
            WorkspaceMode.MARKDOWN -> markdownText
            WorkspaceMode.RICH_TEXT -> plainText
            WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText; 1 -> cssText; else -> jsText }
        }

        val activeLang = when (workspaceMode) {
            WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> "kotlin"
            WorkspaceMode.JAVA_WRITER -> "java"
            WorkspaceMode.PYTHON_WRITER -> "python"
            WorkspaceMode.C_WRITER -> "c"
            WorkspaceMode.CPP_WRITER -> "cpp"
            WorkspaceMode.MARKDOWN -> "markdown"
            WorkspaceMode.RICH_TEXT -> "text"
            WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "javascript" }
        }
        var config by remember(activeLang, isDark) { mutableStateOf(getHighlightConfigFor(activeLang, isDark)) }

        // ----- Text layout result for scrolling to search matches -----
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        // Search match calculation
        val matchIndices = remember(currentTextValue.text, findQuery, matchCase, wholeWord) {
            if (findQuery.isEmpty()) emptyList<IntRange>()
            else {
                val pattern = Regex.escape(findQuery)
                val regexOptions = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                val finalPattern = if (wholeWord) "\\b$pattern\\b" else pattern
                try {
                    Regex(finalPattern, regexOptions).findAll(currentTextValue.text).map { it.range }.toList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        LaunchedEffect(matchIndices) {
            if (matchIndices.isEmpty()) {
                currentMatchIndex = -1
            } else if (currentMatchIndex !in matchIndices.indices) {
                currentMatchIndex = 0
            }
        }

        // Auto-scroll to current match
        LaunchedEffect(currentMatchIndex, layoutResult) {
            if (currentMatchIndex in matchIndices.indices) {
                val layout = layoutResult ?: return@LaunchedEffect
                val range = matchIndices[currentMatchIndex]
                val rect = layout.getBoundingBox(range.first)
                
                vScrollState.animateScrollTo(rect.top.toInt())
                hScrollState.animateScrollTo(rect.left.toInt() - 20)
                
                val newValue = currentTextValue.copy(selection = TextRange(range.first, range.last + 1))
                when(workspaceMode) {
                    WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = newValue
                    WorkspaceMode.JAVA_WRITER -> javaText = newValue
                    WorkspaceMode.PYTHON_WRITER -> pyText = newValue
                    WorkspaceMode.C_WRITER -> cText = newValue
                    WorkspaceMode.CPP_WRITER -> cppText = newValue
                    WorkspaceMode.MARKDOWN -> markdownText = newValue
                    WorkspaceMode.RICH_TEXT -> plainText = newValue
                    WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = newValue; 1 -> cssText = newValue; else -> jsText = newValue }
                }
            }
        }

        LaunchedEffect(selectedBaseColorHex) {
            if (workspaceMode == WorkspaceMode.RICH_TEXT && selectedBaseColorHex.isNotEmpty()) {
                config = config.copy(colors = config.colors.toMutableMap().apply { put("base", selectedBaseColorHex) })
            }
        }

        // Runner UI State
        var showRunner by rememberSaveable { mutableStateOf(false) }
        var runnerTab by rememberSaveable { mutableStateOf(0) } 
        var stdin by rememberSaveable { mutableStateOf("") }
        var stdout by rememberSaveable { mutableStateOf("") }
        var errorMsg by rememberSaveable { mutableStateOf("") }
        val consoleLogs = remember { mutableStateListOf<ConsoleLog>() }
        var compiling by rememberSaveable { mutableStateOf(false) }
        val compiler = remember { CompilerClient() }
        val localExecutor = remember { LocalCodeExecutor() }

        // ---- Fault Tolerance: Background Auto-Cache (10s) ----
        LaunchedEffect(currentTextValue.text) {
            delay(10000)
            runCatching {
                val cacheFile = File(ctx.cacheDir, "crash_recovery.bak")
                cacheFile.writeText(currentTextValue.text)
            }
        }

        // ---- Dialog States ----
        var showNewFileChoice by remember { mutableStateOf(false) }
        var showFormattingLossWarning by remember { mutableStateOf(false) }
        var pendingSaveUri by remember { mutableStateOf<android.net.Uri?>(null) }

        // Undo/Redo
        val undoStack = remember { mutableStateMapOf<String, MutableList<TextFieldValue>>() }
        val redoStack = remember { mutableStateMapOf<String, MutableList<TextFieldValue>>() }

        fun pushUndo(snapshot: TextFieldValue, key: String) {
            val stack = undoStack.getOrPut(key) { mutableListOf() }
            if (stack.isEmpty() || stack.last().text != snapshot.text) {
                stack.add(snapshot)
                if (stack.size > 50) stack.removeAt(0)
            }
        }

        fun doUndo() {
            val key = when (workspaceMode) {
                WorkspaceMode.KOTLIN_COMPILER -> "kotlin_compiler"
                WorkspaceMode.KOTLIN_WRITER -> "kotlin_writer"
                WorkspaceMode.JAVA_WRITER -> "java"
                WorkspaceMode.PYTHON_WRITER -> "python"
                WorkspaceMode.C_WRITER -> "c"
                WorkspaceMode.CPP_WRITER -> "cpp"
                WorkspaceMode.MARKDOWN -> "markdown"
                WorkspaceMode.RICH_TEXT -> "text"
                WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "js" }
            }
            val uStack = undoStack[key] ?: return
            if (uStack.isNotEmpty()) {
                val current = currentTextValue
                redoStack.getOrPut(key) { mutableListOf() }.add(current)
                val prev = uStack.removeAt(uStack.lastIndex)
                when (workspaceMode) {
                    WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = prev
                    WorkspaceMode.JAVA_WRITER -> javaText = prev
                    WorkspaceMode.PYTHON_WRITER -> pyText = prev
                    WorkspaceMode.C_WRITER -> cText = prev
                    WorkspaceMode.CPP_WRITER -> cppText = prev
                    WorkspaceMode.MARKDOWN -> markdownText = prev
                    WorkspaceMode.RICH_TEXT -> plainText = prev
                    WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = prev; 1 -> cssText = prev; else -> jsText = prev }
                }
            }
        }

        fun doRedo() {
            val key = when (workspaceMode) {
                WorkspaceMode.KOTLIN_COMPILER -> "kotlin_compiler"
                WorkspaceMode.KOTLIN_WRITER -> "kotlin_writer"
                WorkspaceMode.JAVA_WRITER -> "java"
                WorkspaceMode.PYTHON_WRITER -> "python"
                WorkspaceMode.C_WRITER -> "c"
                WorkspaceMode.CPP_WRITER -> "cpp"
                WorkspaceMode.MARKDOWN -> "markdown"
                WorkspaceMode.RICH_TEXT -> "text"
                WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "js" }
            }
            val rStack = redoStack[key] ?: return
            if (rStack.isNotEmpty()) {
                val current = currentTextValue
                pushUndo(current, key)
                val next = rStack.removeAt(rStack.lastIndex)
                when (workspaceMode) {
                    WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = next
                    WorkspaceMode.JAVA_WRITER -> javaText = next
                    WorkspaceMode.PYTHON_WRITER -> pyText = next
                    WorkspaceMode.C_WRITER -> cText = next
                    WorkspaceMode.CPP_WRITER -> cppText = next
                    WorkspaceMode.MARKDOWN -> markdownText = next
                    WorkspaceMode.RICH_TEXT -> plainText = next
                    WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = next; 1 -> cssText = next; else -> jsText = next }
                }
            }
        }

        fun executeReplaceAll() {
            if (matchIndices.isNotEmpty()) {
                val key = when (workspaceMode) {
                    WorkspaceMode.KOTLIN_COMPILER -> "kotlin_compiler"
                    WorkspaceMode.KOTLIN_WRITER -> "kotlin_writer"
                    WorkspaceMode.JAVA_WRITER -> "java"
                    WorkspaceMode.PYTHON_WRITER -> "python"
                    WorkspaceMode.C_WRITER -> "c"
                    WorkspaceMode.CPP_WRITER -> "cpp"
                    WorkspaceMode.MARKDOWN -> "markdown"
                    WorkspaceMode.RICH_TEXT -> "text"
                    WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "js" }
                }
                pushUndo(currentTextValue, key)
                
                val regexOptions = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
                val pattern = if (wholeWord) "\\b${Regex.escape(findQuery)}\\b" else Regex.escape(findQuery)
                val regex = Regex(pattern, regexOptions)
                
                val newText = currentTextValue.text.replace(regex, replaceQuery)
                
                if (workspaceMode == WorkspaceMode.RICH_TEXT) {
                    config = config.copy(styleSpans = emptyList())
                    plainText = TextFieldValue(newText)
                } else {
                    val newValue = TextFieldValue(newText)
                    when(workspaceMode) {
                        WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = newValue
                        WorkspaceMode.JAVA_WRITER -> javaText = newValue
                        WorkspaceMode.PYTHON_WRITER -> pyText = newValue
                        WorkspaceMode.C_WRITER -> cText = newValue
                        WorkspaceMode.CPP_WRITER -> cppText = newValue
                        WorkspaceMode.MARKDOWN -> markdownText = newValue
                        WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = newValue; 1 -> cssText = newValue; else -> jsText = newValue }
                        WorkspaceMode.RICH_TEXT -> plainText = newValue
                    }
                }
                redoStack[key]?.clear()
                Toast.makeText(ctx, "Replaced ${matchIndices.size} occurrences.", Toast.LENGTH_SHORT).show()
            }
        }

        val editorTextStyle = remember(selectedFontWeight, selectedFontStyle, selectedFontFamilyName, selectedFontSize, workspaceMode) {
            val family = if (workspaceMode == WorkspaceMode.RICH_TEXT) {
                when (selectedFontFamilyName) {
                    "Serif" -> FontFamily.Serif
                    "Sans Serif" -> FontFamily.SansSerif
                    "Monospace" -> FontFamily.Monospace
                    "Cursive" -> FontFamily.Cursive
                    "Fantasy" -> FontFamily.Default 
                    else -> FontFamily.Default
                }
            } else FontFamily.Monospace

            TextStyle(
                fontFamily = family,
                fontSize = if (workspaceMode == WorkspaceMode.RICH_TEXT) selectedFontSize.sp else 16.sp,
                lineHeight = (if (workspaceMode == WorkspaceMode.RICH_TEXT) selectedFontSize * 1.25 else 20.0).sp,
                fontWeight = if (workspaceMode == WorkspaceMode.RICH_TEXT && selectedFontWeight == "Bold") FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (workspaceMode == WorkspaceMode.RICH_TEXT && selectedFontStyle == "Italic") FontStyle.Italic else FontStyle.Normal
            )
        }

        // ---- File Operations ----
        fun getActualFileName(uri: android.net.Uri): String {
            var result: String? = null
            if (uri.scheme == "content") {
                runCatching {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) result = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            if (result == null) {
                result = uri.path?.substringAfterLast("/")
            }
            return result ?: "Untitled.txt"
        }

        fun recordRecentFile(uri: String, name: String, mode: WorkspaceMode) {
            scope.launch {
                db.recentFileDao().upsertRecentFile(RecentFile(uri, name, System.currentTimeMillis(), mode.name))
            }
        }

        val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                ctx.contentResolver.openInputStream(uri)?.bufferedReader().use {
                    val loaded = it?.readText().orEmpty()
                    val actualName = getActualFileName(uri)
                    val ext = actualName.substringAfterLast(".", "").lowercase()
                    when(ext) {
                        "html" -> { workspaceMode = WorkspaceMode.WEB; activeWebTab = 0; htmlText = TextFieldValue(loaded) }
                        "css" -> { workspaceMode = WorkspaceMode.WEB; activeWebTab = 1; cssText = TextFieldValue(loaded) }
                        "js" -> { workspaceMode = WorkspaceMode.WEB; activeWebTab = 2; jsText = TextFieldValue(loaded) }
                        "kt" -> { 
                            if (workspaceMode != WorkspaceMode.KOTLIN_WRITER) workspaceMode = WorkspaceMode.KOTLIN_COMPILER
                            kotlinText = TextFieldValue(loaded) 
                        }
                        "java" -> { workspaceMode = WorkspaceMode.JAVA_WRITER; javaText = TextFieldValue(loaded) }
                        "py" -> { workspaceMode = WorkspaceMode.PYTHON_WRITER; pyText = TextFieldValue(loaded) }
                        "c" -> { workspaceMode = WorkspaceMode.C_WRITER; cText = TextFieldValue(loaded) }
                        "cpp", "cc" -> { workspaceMode = WorkspaceMode.CPP_WRITER; cppText = TextFieldValue(loaded) }
                        "md" -> { workspaceMode = WorkspaceMode.MARKDOWN; markdownText = TextFieldValue(loaded) }
                        "lctxt" -> {
                            workspaceMode = WorkspaceMode.RICH_TEXT
                            runCatching {
                                val data = Gson().fromJson(loaded, RichTextData::class.java)
                                plainText = TextFieldValue(data.content)
                                config = config.copy(styleSpans = data.styleSpans)
                                selectedBaseColorHex = data.baseColorHex
                                selectedFontSize = data.defaultFontSize
                                selectedFontFamilyName = data.defaultFontFamily
                            }.onFailure { plainText = TextFieldValue(loaded) }
                        }
                        else -> { workspaceMode = WorkspaceMode.RICH_TEXT; plainText = TextFieldValue(loaded) }
                    }
                    currentFileUri = uri.toString()
                    fileName = actualName
                    recordRecentFile(currentFileUri!!, fileName, workspaceMode)
                    Toast.makeText(ctx, "Opened", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val saveFileAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                val actualName = getActualFileName(uri)
                val ext = actualName.substringAfterLast(".", "").lowercase()
                
                if (ext == "txt" && workspaceMode == WorkspaceMode.RICH_TEXT && config.styleSpans.isNotEmpty()) {
                    pendingSaveUri = uri
                    showFormattingLossWarning = true
                } else {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        val content = if (ext == "lctxt") {
                            val data = RichTextData(
                                content = currentTextValue.text,
                                styleSpans = config.styleSpans,
                                baseColorHex = selectedBaseColorHex,
                                defaultFontSize = selectedFontSize,
                                defaultFontFamily = selectedFontFamilyName
                            )
                            Gson().toJson(data)
                        } else currentTextValue.text
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    currentFileUri = uri.toString()
                    fileName = actualName
                    recordRecentFile(currentFileUri!!, fileName, workspaceMode)
                    Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun doSave() {
            if (currentFileUri != null) {
                val uri = android.net.Uri.parse(currentFileUri!!)
                val ext = fileName.substringAfterLast(".", "").lowercase()
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        val content = if (ext == "lctxt") {
                            val data = RichTextData(
                                content = currentTextValue.text,
                                styleSpans = config.styleSpans,
                                baseColorHex = selectedBaseColorHex,
                                defaultFontSize = selectedFontSize,
                                defaultFontFamily = selectedFontFamilyName
                            )
                            Gson().toJson(data)
                        } else currentTextValue.text
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess { Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show() }
                .onFailure { saveFileAs.launch(fileName) }
            } else saveFileAs.launch(fileName)
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(310.dp),
                    drawerContainerColor = cs.surface,
                    drawerContentColor = cs.onSurface,
                    drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(cs.primaryContainer, cs.surface)
                                    )
                                ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(Modifier.padding(start = 28.dp)) {
                                Text(
                                    "< Lite Code />",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cs.primary,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    "PRO EDITION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                                    letterSpacing = 3.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("WORKSPACE", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)
                        
                        ModeItem("Rich Text Editor", Icons.Outlined.Description, workspaceMode == WorkspaceMode.RICH_TEXT) { 
                            workspaceMode = WorkspaceMode.RICH_TEXT; fileName = "Untitled.txt"; scope.launch { drawerState.close() } 
                        }
                        ModeItem("Markdown Writer", Icons.Outlined.Description, workspaceMode == WorkspaceMode.MARKDOWN) { 
                            workspaceMode = WorkspaceMode.MARKDOWN; fileName = "readme.md"; showFormattingBar = false; scope.launch { drawerState.close() }
                        }
                        val isCodeWriterSelected = workspaceMode in listOf(WorkspaceMode.KOTLIN_WRITER, WorkspaceMode.JAVA_WRITER, WorkspaceMode.PYTHON_WRITER, WorkspaceMode.C_WRITER, WorkspaceMode.CPP_WRITER)
                        ModeItem("Code Writer", Icons.Outlined.Code, isCodeWriterSelected) { 
                            if (!isCodeWriterSelected) {
                                workspaceMode = WorkspaceMode.KOTLIN_WRITER
                                fileName = "Main.kt"
                            }
                            showFormattingBar = false; scope.launch { drawerState.close() } 
                        }
                        ModeItem("Kotlin Compiler", Icons.Outlined.Terminal, workspaceMode == WorkspaceMode.KOTLIN_COMPILER) { 
                            workspaceMode = WorkspaceMode.KOTLIN_COMPILER; fileName = "Main.kt"; showFormattingBar = false; scope.launch { drawerState.close() } 
                        }
                        ModeItem("Web Sandbox", Icons.Outlined.Language, workspaceMode == WorkspaceMode.WEB) { 
                            workspaceMode = WorkspaceMode.WEB; fileName = "index.html"; showFormattingBar = false; scope.launch { drawerState.close() } 
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 28.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                        Text("FILE OPERATIONS", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)

                        ActionItem("New File", Icons.Outlined.NoteAdd) { showNewFileChoice = true; scope.launch { drawerState.close() } }
                        ActionItem("Open File", Icons.Outlined.FolderOpen) { openFile.launch(arrayOf("*/*")); scope.launch { drawerState.close() } }
                        ActionItem("Save", Icons.Outlined.Save) { doSave(); scope.launch { drawerState.close() } }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 28.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                        Text("DATABASE", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)
                        ActionItem("Recent Files", Icons.Outlined.History) { showRecentFiles = true; scope.launch { drawerState.close() } }
                        ActionItem("Snippet Library", Icons.Outlined.CollectionsBookmark) { showSnippetLibrary = true; scope.launch { drawerState.close() } }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 28.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                        Text("APPEARANCE", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)

                        ModeItem("System Default", Icons.Outlined.BrightnessAuto, currentTheme == AppTheme.SYSTEM) {
                            themeViewModel.setTheme(AppTheme.SYSTEM)
                            scope.launch { drawerState.close() }
                        }
                        ModeItem("Light Theme", Icons.Outlined.LightMode, currentTheme == AppTheme.LIGHT) {
                            themeViewModel.setTheme(AppTheme.LIGHT)
                            scope.launch { drawerState.close() }
                        }
                        ModeItem("Dark Theme", Icons.Outlined.DarkMode, currentTheme == AppTheme.DARK) {
                            themeViewModel.setTheme(AppTheme.DARK)
                            scope.launch { drawerState.close() }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 28.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                        Text("ABOUT", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)
                        ActionItem("App Info", Icons.Outlined.Info) { onShowInfo(); scope.launch { drawerState.close() } }
                        
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F) {
                            showSearchPanel = true
                            val sel = currentTextValue.selection
                            if (sel.start != sel.end) {
                                findQuery = currentTextValue.text.substring(sel.start, sel.end)
                            }
                            true
                        } else false
                    },
                topBar = {
                    Column {
                        TopAppBar(
                            title = { 
                                Text(
                                    fileName, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                ) 
                            },
                            navigationIcon = { 
                                IconButton(onClick = { scope.launch { drawerState.open() } }) { 
                                    Icon(Icons.Outlined.Menu, "Menu", tint = cs.primary) 
                                } 
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = cs.surface,
                                scrolledContainerColor = cs.surfaceVariant
                            ),
                            actions = {
                                IconButton(onClick = {
                                    showSearchPanel = !showSearchPanel
                                    if (showSearchPanel) {
                                        val sel = currentTextValue.selection
                                        if (sel.start != sel.end) {
                                            findQuery = currentTextValue.text.substring(sel.start, sel.end)
                                        }
                                    }
                                }) { 
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(if(showSearchPanel) cs.primaryContainer else Color.Transparent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Search, "Find and replace", tint = if(showSearchPanel) cs.primary else cs.onSurfaceVariant)
                                    }
                                }
                                if (workspaceMode == WorkspaceMode.RICH_TEXT) {
                                    SelectableIconButton(showFormattingBar, Icons.Outlined.FormatSize, "Format") { showFormattingBar = !showFormattingBar }
                                }
                                
                                IconButton(onClick = { doUndo() }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo", tint = cs.onSurfaceVariant) }
                                IconButton(onClick = { doRedo() }) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo", tint = cs.onSurfaceVariant) }
                            }
                        )
                        // Cinematic Accent Line
                        Box(Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(listOf(cs.primary.copy(alpha = 0f), cs.primary, cs.primary.copy(alpha = 0f)))
                        ))
                        if (workspaceMode == WorkspaceMode.WEB) {
                            WebTabs(activeWebTab) { activeWebTab = it; fileName = when(it){0->"index.html"; 1->"style.css"; else->"script.js"} }
                        }
                        AnimatedVisibility(showSearchPanel) {
                            SearchPanel(
                                findQuery = findQuery,
                                onFindQueryChange = { findQuery = it },
                                replaceQuery = replaceQuery,
                                onReplaceQueryChange = { replaceQuery = it },
                                matchCase = matchCase,
                                onMatchCaseToggle = { matchCase = !matchCase },
                                wholeWord = wholeWord,
                                onWholeWordToggle = { wholeWord = !wholeWord },
                                matchCount = matchIndices.size,
                                currentIndex = currentMatchIndex,
                                onPrev = { if (matchIndices.isNotEmpty()) currentMatchIndex = (currentMatchIndex - 1 + matchIndices.size) % matchIndices.size },
                                onNext = { if (matchIndices.isNotEmpty()) currentMatchIndex = (currentMatchIndex + 1) % matchIndices.size },
                                onReplace = {
                                    if (currentMatchIndex in matchIndices.indices) {
                                        val range = matchIndices[currentMatchIndex]
                                        val key = when (workspaceMode) {
                                            WorkspaceMode.KOTLIN_COMPILER -> "kotlin_compiler"
                                            WorkspaceMode.KOTLIN_WRITER -> "kotlin_writer"
                                            WorkspaceMode.JAVA_WRITER -> "java"
                                            WorkspaceMode.PYTHON_WRITER -> "python"
                                            WorkspaceMode.C_WRITER -> "c"
                                            WorkspaceMode.CPP_WRITER -> "cpp"
                                            WorkspaceMode.MARKDOWN -> "markdown"
                                            WorkspaceMode.RICH_TEXT -> "text"
                                            WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "js" }
                                        }
                                        pushUndo(currentTextValue, key)
                                        
                                        val newText = currentTextValue.text.replaceRange(range.first, range.last + 1, replaceQuery)
                                        
                                        if (workspaceMode == WorkspaceMode.RICH_TEXT) {
                                            val diff = replaceQuery.length - (range.last + 1 - range.first)
                                            val newSpans = config.styleSpans.filterNot { it.start < range.last + 1 && it.end > range.first }
                                                .map { span ->
                                                    if (span.start >= range.last + 1) span.copy(start = span.start + diff, end = span.end + diff)
                                                    else span
                                                }
                                            config = config.copy(styleSpans = newSpans)
                                            plainText = TextFieldValue(newText, TextRange(range.first + replaceQuery.length))
                                        } else {
                                            val newValue = TextFieldValue(newText, TextRange(range.first + replaceQuery.length))
                                            when(workspaceMode) {
                                                WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = newValue
                                                WorkspaceMode.JAVA_WRITER -> javaText = newValue
                                                WorkspaceMode.PYTHON_WRITER -> pyText = newValue
                                                WorkspaceMode.C_WRITER -> cText = newValue
                                                WorkspaceMode.CPP_WRITER -> cppText = newValue
                                                WorkspaceMode.MARKDOWN -> markdownText = newValue
                                                WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = newValue; 1 -> cssText = newValue; else -> jsText = newValue }
                                                WorkspaceMode.RICH_TEXT -> plainText = newValue
                                            }
                                        }
                                        redoStack[key]?.clear()
                                    }
                                },
                                onReplaceAll = {
                                    if (matchIndices.size > 100) showReplaceAllWarning = true
                                    else executeReplaceAll()
                                },
                                onClose = { showSearchPanel = false }
                            )
                        }
                    }
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp, color = cs.surface, modifier = Modifier.navigationBarsPadding()) {
                        Column(Modifier.fillMaxWidth()) {
                            if (workspaceMode == WorkspaceMode.RICH_TEXT && showFormattingBar) {
                                FormattingToolbar(
                                    selectedFontWeight, selectedFontStyle, selectedUnderline, selectedFontFamilyName, selectedFontSize, isDark,
                                    currentTextValue.selection, config,
                                    onUpdateWeight = { selectedFontWeight = it },
                                    onUpdateStyle = { selectedFontStyle = it },
                                    onUpdateUnderline = { selectedUnderline = it },
                                    onUpdateFamily = { selectedFontFamilyName = it },
                                    onUpdateSize = { selectedFontSize = it },
                                    onUpdateColor = { selectedBaseColorHex = it },
                                    onConfigChange = { config = it }
                                )
                                Divider()
                            }
                            BottomActionBar(workspaceMode, compiling, currentTextValue, clipboard, ctx, isDark, onRun = {
                                if (workspaceMode == WorkspaceMode.KOTLIN_COMPILER) {
                                    showRunner = true
                                    compiling = true
                                    runnerTab = 1
                                    stdout = ""
                                    errorMsg = ""
                                    scope.launch {
                                        val resp = compiler.compile(serverUrl, currentTextValue.text, stdin, "kt")
                                        compiling = false
                                        if (resp.success) {
                                            stdout = resp.output
                                            runnerTab = 1
                                        } else {
                                            errorMsg = resp.error
                                            runnerTab = 2
                                        }
                                    }
                                } else if (workspaceMode == WorkspaceMode.WEB) {
                                    showRunner = true
                                    runnerTab = 3
                                }
                            }, onTextChange = {
                                when(workspaceMode){
                                    WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = it
                                    WorkspaceMode.JAVA_WRITER -> javaText = it
                                    WorkspaceMode.PYTHON_WRITER -> pyText = it
                                    WorkspaceMode.C_WRITER -> cText = it
                                    WorkspaceMode.CPP_WRITER -> cppText = it
                                    WorkspaceMode.MARKDOWN -> markdownText = it
                                    WorkspaceMode.RICH_TEXT -> plainText = it
                                    WorkspaceMode.WEB -> when(activeWebTab){0->htmlText=it; 1->cssText=it; else->jsText=it}
                                }
                            })
                        }
                    }
                }
            ) { innerPadding ->
                Column(Modifier.padding(innerPadding).fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(cs.surface)) {
                        val density = LocalDensity.current
                        val lineCount = currentTextValue.text.count { it == '\n' } + 1
                        val gutterWidth = with(density) { rememberTextMeasurer().measure(lineCount.toString(), editorTextStyle).size.width.toDp() } + 16.dp

                        Row(Modifier.fillMaxSize().verticalScroll(vScrollState)
                            .then(if (isSoftWrapEnabled) Modifier else Modifier.horizontalScroll(hScrollState))
                            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
                            Text(
                                (1..lineCount).joinToString("\n"),
                                style = editorTextStyle.copy(color = cs.onSurface.copy(alpha = 0.4f)),
                                modifier = Modifier.width(gutterWidth).background(cs.surfaceVariant).padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                            Box(Modifier.fillMaxHeight().width(1.dp).background(cs.outlineVariant))
                            BasicTextField(
                                value = currentTextValue,
                                onValueChange = {
                                    val key = when (workspaceMode) { 
                                        WorkspaceMode.KOTLIN_COMPILER -> "kotlin_compiler"
                                        WorkspaceMode.KOTLIN_WRITER -> "kotlin_writer"
                                        WorkspaceMode.JAVA_WRITER -> "java"
                                        WorkspaceMode.PYTHON_WRITER -> "python"
                                        WorkspaceMode.C_WRITER -> "c"
                                        WorkspaceMode.CPP_WRITER -> "cpp"
                                        WorkspaceMode.MARKDOWN -> "markdown"
                                        WorkspaceMode.RICH_TEXT -> "text"
                                        WorkspaceMode.WEB -> when(activeWebTab) { 0 -> "html"; 1 -> "css"; else -> "js" } 
                                    }
                                    pushUndo(currentTextValue, key)
                                    redoStack[key]?.clear()
                                    when (workspaceMode) { 
                                        WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = it
                                        WorkspaceMode.JAVA_WRITER -> javaText = it
                                        WorkspaceMode.PYTHON_WRITER -> pyText = it
                                        WorkspaceMode.C_WRITER -> cText = it
                                        WorkspaceMode.CPP_WRITER -> cppText = it
                                        WorkspaceMode.MARKDOWN -> markdownText = it
                                        WorkspaceMode.RICH_TEXT -> plainText = it
                                        WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = it; 1 -> cssText = it; else -> jsText = it } 
                                    }
                                },
                                textStyle = editorTextStyle.copy(color = Color.Transparent),
                                modifier = Modifier.padding(8.dp).then(if (isSoftWrapEnabled) Modifier.weight(1f) else Modifier.widthIn(min = this@BoxWithConstraints.maxWidth - gutterWidth)).focusRequester(focusRequester).onFocusChanged {  },
                                cursorBrush = SolidColor(cs.primary),
                                onTextLayout = { layoutResult = it }
                            ) { inner ->
                                Box {
                                    val syntaxAnnotated = highlightWithConfig(currentTextValue.text, config)
                                    val searchAnnotated = remember(currentTextValue.text, findQuery, matchIndices, currentMatchIndex) {
                                        buildAnnotatedString {
                                            append(currentTextValue.text)
                                            matchIndices.forEachIndexed { i, range ->
                                                val isCurrent = i == currentMatchIndex
                                                addStyle(
                                                    SpanStyle(
                                                        background = if (isCurrent) cs.primaryContainer else cs.primaryContainer.copy(alpha = 0.4f),
                                                        color = cs.onPrimaryContainer
                                                    ),
                                                    range.first, range.last + 1
                                                )
                                            }
                                        }
                                    }
                                    Text(syntaxAnnotated, style = editorTextStyle.copy(color = cs.onSurface), softWrap = isSoftWrapEnabled)
                                    if (findQuery.isNotEmpty()) {
                                        Text(searchAnnotated, style = editorTextStyle.copy(color = Color.Transparent), softWrap = isSoftWrapEnabled)
                                    }
                                    inner()
                                }
                            }
                        }
                    }

                    AnimatedVisibility(showRunner && workspaceMode != WorkspaceMode.RICH_TEXT) {
                        RunnerPanel(workspaceMode, runnerTab, compiling, stdout, errorMsg, stdin, htmlText.text, cssText.text, jsText.text, consoleLogs, 
                            onTabChange = { runnerTab = it }, 
                            onStdinChange = { stdin = it },
                            onClose = { showRunner = false; if(!compiling) stdout = "" },
                            onLog = { consoleLogs.add(it) },
                            onClearLogs = { consoleLogs.clear() }
                        )
                    }
                }
            }
        }

        // Dialogs
        if (showNewFileChoice) {
            AlertDialog(
                onDismissRequest = { showNewFileChoice = false },
                title = { Text("New Project") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        NewItemChoice("New Rich Text File", Icons.Outlined.Description) { workspaceMode = WorkspaceMode.RICH_TEXT; plainText = TextFieldValue(""); fileName = "Untitled.txt"; showNewFileChoice = false }
                        NewItemChoice("New Markdown File", Icons.Outlined.Description) { workspaceMode = WorkspaceMode.MARKDOWN; markdownText = TextFieldValue(mdTemplate); fileName = "readme.md"; showNewFileChoice = false }
                        
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("CODE WRITER", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        
                        NewItemChoice("New Kotlin File (.kt)", Icons.Outlined.Code) { workspaceMode = WorkspaceMode.KOTLIN_WRITER; kotlinText = TextFieldValue(ktTemplate); fileName = "Main.kt"; showNewFileChoice = false }
                        NewItemChoice("New Java File (.java)", Icons.Outlined.Code) { workspaceMode = WorkspaceMode.JAVA_WRITER; javaText = TextFieldValue(javaTemplate); fileName = "Main.java"; showNewFileChoice = false }
                        NewItemChoice("New Python File (.py)", Icons.Outlined.Code) { workspaceMode = WorkspaceMode.PYTHON_WRITER; pyText = TextFieldValue(pyTemplate); fileName = "script.py"; showNewFileChoice = false }
                        NewItemChoice("New C File (.c)", Icons.Outlined.Code) { workspaceMode = WorkspaceMode.C_WRITER; cText = TextFieldValue(cTemplate); fileName = "main.c"; showNewFileChoice = false }
                        NewItemChoice("New C++ File (.cpp)", Icons.Outlined.Code) { workspaceMode = WorkspaceMode.CPP_WRITER; cppText = TextFieldValue(cppTemplate); fileName = "main.cpp"; showNewFileChoice = false }
                        
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("KOTLIN COMPILER", style = MaterialTheme.typography.labelSmall, color = cs.primary)
                        NewItemChoice("New Kotlin Project (Run)", Icons.Outlined.Terminal) { workspaceMode = WorkspaceMode.KOTLIN_COMPILER; kotlinText = TextFieldValue(ktTemplate); fileName = "Main.kt"; showNewFileChoice = false }
                        
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        NewItemChoice("New Web Project", Icons.Outlined.Language) { workspaceMode = WorkspaceMode.WEB; htmlText = TextFieldValue(htmlTemplate); cssText = TextFieldValue(cssTemplate); jsText = TextFieldValue(jsTemplate); fileName = "index.html"; showNewFileChoice = false }
                    }
                },
                confirmButton = {}
            )
        }

        if (showFormattingLossWarning) {
            AlertDialog(
                onDismissRequest = { showFormattingLossWarning = false },
                title = { Text("Warning") },
                text = { Text("Plain text files cannot preserve formatting. The text will be saved, but font styles and colours will be removed.") },
                confirmButton = { Button(onClick = { pendingSaveUri?.let { uri -> ctx.contentResolver.openOutputStream(uri)?.use { out -> out.write(currentTextValue.text.toByteArray()) }; currentFileUri = uri.toString() }; showFormattingLossWarning = false }) { Text("Save Anyway") } },
                dismissButton = { TextButton(onClick = { showFormattingLossWarning = false }) { Text("Cancel") } }
            )
        }

        if (showReplaceAllWarning) {
            AlertDialog(
                onDismissRequest = { showReplaceAllWarning = false },
                title = { Text("Replace All") },
                text = { Text("Are you sure you want to replace all ${matchIndices.size} occurrences of \"$findQuery\"?") },
                confirmButton = { Button(onClick = { executeReplaceAll(); showReplaceAllWarning = false }) { Text("Replace All") } },
                dismissButton = { TextButton(onClick = { showReplaceAllWarning = false }) { Text("Cancel") } }
            )
        }

        if (showRecentFiles) {
            AlertDialog(
                onDismissRequest = { showRecentFiles = false },
                title = { Text("Recent Files", fontFamily = FontFamily.Monospace) },
                text = {
                    if (recentFiles.isEmpty()) {
                        Text("No recent files found.")
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            recentFiles.forEach { file ->
                                ListItem(
                                    headlineContent = { Text(file.fileName) },
                                    supportingContent = { Text(file.mode, style = MaterialTheme.typography.labelSmall) },
                                    leadingContent = { Icon(Icons.Outlined.InsertDriveFile, null) },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            runCatching {
                                                val uri = android.net.Uri.parse(file.uri)
                                                runCatching {
                                                    ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                                }

                                                ctx.contentResolver.openInputStream(uri)?.bufferedReader().use {
                                                    val loaded = it?.readText().orEmpty()
                                                    workspaceMode = WorkspaceMode.valueOf(file.mode)
                                                    when(workspaceMode) {
                                                        WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = TextFieldValue(loaded)
                                                        WorkspaceMode.JAVA_WRITER -> javaText = TextFieldValue(loaded)
                                                        WorkspaceMode.PYTHON_WRITER -> pyText = TextFieldValue(loaded)
                                                        WorkspaceMode.C_WRITER -> cText = TextFieldValue(loaded)
                                                        WorkspaceMode.CPP_WRITER -> cppText = TextFieldValue(loaded)
                                                        WorkspaceMode.MARKDOWN -> markdownText = TextFieldValue(loaded)
                                                        WorkspaceMode.RICH_TEXT -> plainText = TextFieldValue(loaded)
                                                        WorkspaceMode.WEB -> { htmlText = TextFieldValue(loaded); activeWebTab = 0 }
                                                    }
                                                    currentFileUri = file.uri
                                                    fileName = file.fileName
                                                    recordRecentFile(currentFileUri!!, fileName, workspaceMode)
                                                }
                                            }.onFailure {
                                                Toast.makeText(ctx, "Could not open file. Permission may have been revoked.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        showRecentFiles = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showRecentFiles = false }) { Text("Close") } }
            )
        }

        if (showSnippetLibrary) {
            AlertDialog(
                onDismissRequest = { showSnippetLibrary = false },
                title = { 
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Snippet Library")
                        IconButton(onClick = { showAddSnippetDialog = true }) { Icon(Icons.Default.Add, "Add Snippet") }
                    }
                },
                text = {
                    if (snippets.isEmpty()) {
                        Text("Save reusable code blocks here.")
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            snippets.forEach { snippet ->
                                ListItem(
                                    headlineContent = { Text(snippet.title) },
                                    supportingContent = { Text(snippet.language, style = MaterialTheme.typography.labelSmall) },
                                    trailingContent = {
                                        IconButton(onClick = { scope.launch { db.snippetDao().deleteSnippet(snippet) } }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        val currentVal = currentTextValue
                                        val newText = currentVal.text.replaceRange(currentVal.selection.start, currentVal.selection.end, snippet.code)
                                        val newValue = TextFieldValue(newText, TextRange(currentVal.selection.start + snippet.code.length))
                                        when(workspaceMode) {
                                            WorkspaceMode.KOTLIN_COMPILER, WorkspaceMode.KOTLIN_WRITER -> kotlinText = newValue
                                            WorkspaceMode.JAVA_WRITER -> javaText = newValue
                                            WorkspaceMode.PYTHON_WRITER -> pyText = newValue
                                            WorkspaceMode.C_WRITER -> cText = newValue
                                            WorkspaceMode.CPP_WRITER -> cppText = newValue
                                            WorkspaceMode.MARKDOWN -> markdownText = newValue
                                            WorkspaceMode.RICH_TEXT -> plainText = newValue
                                            WorkspaceMode.WEB -> when(activeWebTab) { 0 -> htmlText = newValue; 1 -> cssText = newValue; else -> jsText = newValue }
                                        }
                                        showSnippetLibrary = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSnippetLibrary = false }) { Text("Close") } }
            )
        }

        if (showAddSnippetDialog) {
            AlertDialog(
                onDismissRequest = { showAddSnippetDialog = false },
                title = { Text("Save Snippet") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Title this snippet to reuse it later.")
                        OutlinedTextField(
                            value = snippetTitleInput,
                            onValueChange = { snippetTitleInput = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = snippetTitleInput.isNotBlank(),
                        onClick = {
                            scope.launch {
                                db.snippetDao().upsertSnippet(Snippet(
                                    title = snippetTitleInput,
                                    code = currentTextValue.text,
                                    language = activeLang
                                ))
                                snippetTitleInput = ""
                                showAddSnippetDialog = false
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showAddSnippetDialog = false }) { Text("Cancel") } }
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Theme Settings") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select your preferred application theme:")
                        ThemeOption(
                            label = "Follow System Default",
                            selected = currentTheme == AppTheme.SYSTEM,
                            onClick = { themeViewModel.setTheme(AppTheme.SYSTEM) }
                        )
                        ThemeOption(
                            label = "Light Mode",
                            selected = currentTheme == AppTheme.LIGHT,
                            onClick = { themeViewModel.setTheme(AppTheme.LIGHT) }
                        )
                        ThemeOption(
                            label = "Dark Mode",
                            selected = currentTheme == AppTheme.DARK,
                            onClick = { themeViewModel.setTheme(AppTheme.DARK) }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
            )
        }
    }

@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------- Sub-Composables ----------------------

@Composable
fun SearchPanel(
    findQuery: String, onFindQueryChange: (String) -> Unit,
    replaceQuery: String, onReplaceQueryChange: (String) -> Unit,
    matchCase: Boolean, onMatchCaseToggle: () -> Unit,
    wholeWord: Boolean, onWholeWordToggle: () -> Unit,
    matchCount: Int, currentIndex: Int,
    onPrev: () -> Unit, onNext: () -> Unit,
    onReplace: () -> Unit, onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindQueryChange,
                    label = { Text("Find", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (matchCount > 0) "${currentIndex + 1} of $matchCount" else "No matches", style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = onPrev, enabled = matchCount > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Prev") }
                            IconButton(onClick = onNext, enabled = matchCount > 0) { Icon(Icons.Outlined.KeyboardArrowDown, "Next") }
                        }
                    }
                )
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    label = { Text("Replace", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = onReplace, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.FindReplace, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Replace", fontSize = 12.sp)
                }
                Button(onClick = onReplaceAll, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.Autorenew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("All", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = matchCase, onClick = onMatchCaseToggle, label = { Text("Match case", fontSize = 11.sp) })
                FilterChip(selected = wholeWord, onClick = onWholeWordToggle, label = { Text("Whole word", fontSize = 11.sp) })
            }
        }
    }
}

@Composable
fun ModeItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        icon = { 
            Icon(
                imageVector = if (selected) getFilled(icon) else icon, 
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        label = { 
            Text(
                label, 
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            ) 
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun ActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        selected = false,
        onClick = onClick,
        icon = { 
            Icon(
                imageVector = icon, 
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        label = { 
            Text(
                label,
                fontWeight = FontWeight.Medium
            ) 
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun WebTabs(selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface, divider = {}) {
        val tabs = listOf("HTML" to Icons.Outlined.Code, "CSS" to Icons.Outlined.Palette, "JS" to Icons.Outlined.Javascript)
        tabs.forEachIndexed { i, (label, icon) ->
            Tab(selected = selected == i, onClick = { onSelect(i) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selected == i) getFilled(icon) else icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun FormattingToolbar(
    weight: String, style: String, underline: Boolean, family: String, size: Float, isDark: Boolean,
    sel: TextRange, cfg: HighlightConfig,
    onUpdateWeight: (String) -> Unit, onUpdateStyle: (String) -> Unit, onUpdateUnderline: (Boolean) -> Unit,
    onUpdateFamily: (String) -> Unit, onUpdateSize: (Float) -> Unit, onUpdateColor: (String) -> Unit,
    onConfigChange: (HighlightConfig) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = {
            if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans.filterNot { it.start >= sel.start && it.end <= sel.end }))
            else {
                onConfigChange(cfg.copy(styleSpans = emptyList()))
                onUpdateWeight("Normal"); onUpdateStyle("Normal"); onUpdateUnderline(false); onUpdateFamily("Default"); onUpdateSize(16f); onUpdateColor("")
            }
        }) { Icon(Icons.Outlined.Restore, "Reset") }

        FormatToggle(weight == "Bold", Icons.Filled.FormatBold) {
            val nw = if(weight=="Bold") "Normal" else "Bold"
            if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans + StyleSpan(sel.start, sel.end, fontWeight = nw)))
            else onUpdateWeight(nw)
        }
        FormatToggle(style == "Italic", Icons.Filled.FormatItalic) {
            val ns = if(style=="Italic") "Normal" else "Italic"
            if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans + StyleSpan(sel.start, sel.end, fontStyle = ns)))
            else onUpdateStyle(ns)
        }
        FormatToggle(underline, Icons.Filled.FormatUnderlined) {
            val nu = !underline
            if (sel.start != sel.end) {
                val existing = cfg.styleSpans.find { it.start == sel.start && it.end == sel.end }
                onConfigChange(cfg.copy(styleSpans = cfg.styleSpans.filterNot { it.start == sel.start && it.end == sel.end } + (existing?.copy(underline = nu) ?: StyleSpan(sel.start, sel.end, underline = nu))))
            } else onUpdateUnderline(nu)
        }

        VerticalDivider(Modifier.height(24.dp))
        listOf("Default", "Sans Serif", "Serif", "Monospace", "Cursive", "Fantasy").forEach { name ->
            FilterChip(
                selected = family == name, 
                onClick = { 
                    if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans + StyleSpan(sel.start, sel.end, fontFamily = name)))
                    else onUpdateFamily(name)
                }, 
                label = { Text(name) }
            )
        }

        VerticalDivider(Modifier.height(24.dp))
        listOf(12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f).forEach { s ->
            FilterChip(selected = size == s, onClick = {
                if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans + StyleSpan(sel.start, sel.end, fontSize = s)))
                else onUpdateSize(s)
            }, label = { Text("${s.toInt()}") })
        }

        VerticalDivider(Modifier.height(24.dp))
        val colors = if (isDark) listOf("#E5E7EB", "#FF6B6B", "#4ECDC4", "#FFE66D", "#FF9F43", "#A29BFE")
                     else listOf("#111827", "#D32F2F", "#388E3C", "#FBC02D", "#E64A19", "#512DA8")
        colors.forEach { hex ->
            Box(Modifier.size(32.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                .pointerInput(hex) { detectTapGestures {
                    if (sel.start != sel.end) onConfigChange(cfg.copy(styleSpans = cfg.styleSpans + StyleSpan(sel.start, sel.end, color = hex)))
                    else onUpdateColor(hex)
                } })
        }
    }
}

@Composable
fun FormatToggle(active: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val container by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(200))
    val content by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, tween(200))
    IconButton(onClick = onClick, modifier = Modifier.background(container, RoundedCornerShape(8.dp))) {
        Icon(icon, null, tint = content)
    }
}

@Composable
fun BottomActionBar(mode: WorkspaceMode, compiling: Boolean, text: TextFieldValue, clipboard: androidx.compose.ui.platform.ClipboardManager, ctx: android.content.Context, isDark: Boolean, onRun: () -> Unit, onTextChange: (TextFieldValue) -> Unit) {
    // Premium Gradient for the Run button
    val runButtonBrush = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF64FFDA), Color(0xFF0EA5E9)))
    } else {
        // Royal Blue gradient for Light Theme matching your reference
        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionIconButton(Icons.Outlined.ContentCut, "Cut", text.selection.start != text.selection.end, isDark) {
                val sel = text.selection
                clipboard.setText(AnnotatedString(text.text.substring(sel.start, sel.end)))
                onTextChange(TextFieldValue(text.text.removeRange(sel.start, sel.end), TextRange(sel.start)))
            }
            
            ActionIconButton(Icons.Outlined.ContentCopy, "Copy", text.selection.start != text.selection.end, isDark) {
                val sel = text.selection
                clipboard.setText(AnnotatedString(text.text.substring(sel.start, sel.end)))
                Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            
            ActionIconButton(Icons.Outlined.ContentPaste, "Paste", true, isDark) {
                val clip = clipboard.getText()?.text.orEmpty()
                if (clip.isNotEmpty()) {
                    val sel = text.selection
                    onTextChange(TextFieldValue(text.text.replaceRange(sel.start, sel.end, clip), TextRange(sel.start + clip.length)))
                }
            }
        }

        if (mode == WorkspaceMode.KOTLIN_COMPILER || mode == WorkspaceMode.WEB) {
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .widthIn(min = 130.dp)
                    .background(runButtonBrush, RoundedCornerShape(24.dp))
                    .clickable(enabled = !compiling, onClick = onRun),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (compiling) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("EXECUTING", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                    } else {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("RUN CODE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionIconButton(icon: ImageVector, contentDescription: String, enabled: Boolean, isDark: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(42.dp)
            .background(
                if (isDark) Color(0xFF112240).copy(alpha = if(enabled) 1f else 0.5f) 
                else Color(0xFFDBEAFE).copy(alpha = if(enabled) 1f else 0.5f), 
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = contentDescription, 
            tint = if(enabled) {
                if(isDark) MaterialTheme.colorScheme.primary else Color(0xFF2563EB)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun RunnerPanel(mode: WorkspaceMode, selectedTab: Int, compiling: Boolean, stdout: String, errorMsg: String, stdin: String, html: String, css: String, js: String, logs: List<ConsoleLog>, onTabChange: (Int) -> Unit, onStdinChange: (String) -> Unit, onClose: () -> Unit, onLog: (ConsoleLog) -> Unit, onClearLogs: () -> Unit) {
    Surface(Modifier.fillMaxWidth().height(350.dp), tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 16.dp) {
        Column {
            val tabs = if(mode == WorkspaceMode.WEB) listOf("Input" to Icons.Outlined.Keyboard, "Output" to Icons.Outlined.Output, "Problems" to Icons.Outlined.ErrorOutline, "Preview" to Icons.Outlined.Visibility, "Console" to Icons.Outlined.Terminal)
                       else listOf("Input" to Icons.Outlined.Keyboard, "Output" to Icons.Outlined.Output, "Problems" to Icons.Outlined.ErrorOutline)
            
            val currentTabs = remember(mode) { tabs }

            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, 
                    modifier = Modifier.weight(1f), 
                    edgePadding = 0.dp, 
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    currentTabs.forEachIndexed { i, (label, icon) ->
                        Tab(
                            selected = selectedTab == i, 
                            onClick = { onTabChange(i) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .background(if(selectedTab == i) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (selectedTab == i) getFilled(icon) else icon, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, fontSize = 12.sp, fontWeight = if(selectedTab==i) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null) }
            }
            Box(Modifier.fillMaxSize().padding(12.dp)) {
                when (selectedTab) {
                    0 -> OutlinedTextField(value = stdin, onValueChange = onStdinChange, modifier = Modifier.fillMaxSize(), placeholder = { Text("Standard input...") })
                    1 -> SelectionContainer { Text(if (compiling) "Running..." else stdout.ifBlank { "No output." }, fontFamily = FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState())) }
                    2 -> SelectionContainer { Text(errorMsg.ifBlank { "No problems." }, color = if(errorMsg.isNotBlank()) Color.Red else MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState())) }
                    3 -> if (mode == WorkspaceMode.WEB) WebPreview(html, css, js, onLog)
                    4 -> Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Console", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = onClearLogs) { Icon(Icons.Outlined.DeleteSweep, null) }
                        }
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            logs.forEach { log ->
                                Text("${if(log.line > 0) "[Line ${log.line}] " else ""}${log.message}", color = when(log.level){LogLevel.ERROR->Color.Red; LogLevel.WARN->Color(0xFFFFA000); else->MaterialTheme.colorScheme.onSurface}, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableIconButton(selected: Boolean, icon: ImageVector, desc: String, onClick: () -> Unit) {
    val container by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(200))
    val content by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, tween(200))
    IconButton(onClick = onClick, modifier = Modifier.background(container, CircleShape)) {
        Icon(if (selected) getFilled(icon) else icon, desc, tint = content)
    }
}

@Composable
fun NewItemChoice(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
        Icon(icon, null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

fun getFilled(icon: ImageVector): ImageVector {
    return when (icon.name) {
        "Outlined.Terminal" -> Icons.Filled.Terminal
        "Outlined.Language" -> Icons.Filled.Language
        "Outlined.Description" -> Icons.Filled.Description
        "Outlined.Code" -> Icons.Filled.Code
        "Outlined.Palette" -> Icons.Filled.Palette
        "Outlined.Javascript" -> Icons.Filled.Javascript
        "Outlined.Visibility" -> Icons.Filled.Visibility
        "Outlined.Keyboard" -> Icons.Filled.Keyboard
        "Outlined.Output" -> Icons.Filled.Output
        "Outlined.ErrorOutline" -> Icons.Filled.ErrorOutline
        "Outlined.FormatSize" -> Icons.Filled.FormatSize
        "Outlined.EditNote" -> Icons.Filled.EditNote
        "Outlined.History" -> Icons.Filled.History
        "Outlined.CollectionsBookmark" -> Icons.Filled.CollectionsBookmark
        "Outlined.NoteAdd" -> Icons.Filled.NoteAdd
        "Outlined.FolderOpen" -> Icons.Filled.FolderOpen
        "Outlined.Save" -> Icons.Filled.Save
        "Outlined.SaveAs" -> Icons.Filled.SaveAs
        "Outlined.AddModerator" -> Icons.Filled.AddModerator
        "Outlined.HistoryEdu" -> Icons.Filled.HistoryEdu
        "Outlined.LightMode" -> Icons.Filled.LightMode
        "Outlined.DarkMode" -> Icons.Filled.DarkMode
        else -> icon
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreview(html: String, css: String, js: String, onLog: (ConsoleLog) -> Unit) {
    val combinedHtml = remember(html, css, js) {
        val style = "<style>$css</style>"
        val script = """
            <script>
            (function() {
                const oldLog = console.log;
                const oldWarn = console.warn;
                const oldError = console.error;
                console.log = function(...args) { window.android.log(args.join(' '), 'INFO'); oldLog.apply(console, args); };
                console.warn = function(...args) { window.android.log(args.join(' '), 'WARN'); oldWarn.apply(console, args); };
                console.error = function(...args) { window.android.log(args.join(' '), 'ERROR'); oldError.apply(console, args); };
                window.onerror = function(msg, url, line) { window.android.log(msg, 'ERROR', line); };
            })();
            $js
            </script>
        """.trimIndent()

        var doc = html
        doc = if (doc.contains("</head>")) doc.replace("</head>", "$style</head>") else "$style$doc"
        doc = if (doc.contains("</body>")) doc.replace("</body>", "$script</body>") else "$doc$script"
        doc
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                        msg?.let {
                            val level = when(it.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                                ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARN
                                else -> LogLevel.INFO
                            }
                            onLog(ConsoleLog(it.message(), level, it.sourceId() ?: "", it.lineNumber()))
                        }
                        return true
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun log(msg: String, level: String, line: Int = 0) {
                        onLog(ConsoleLog(msg, LogLevel.valueOf(level), "", line))
                    }
                }, "android")
                loadDataWithBaseURL("https://litecode.local", combinedHtml, "text/html", "UTF-8", null)
            }
        },
        update = { it.loadDataWithBaseURL("https://litecode.local", combinedHtml, "text/html", "UTF-8", null) },
        modifier = Modifier.fillMaxSize()
    )
}
