package com.texteditor.project.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.texteditor.project.network.CompilerClient
import com.texteditor.project.data.AppTheme
import kotlinx.coroutines.launch

enum class WorkspaceMode { KOTLIN_COMPILER, KOTLIN_WRITER, JAVA_WRITER, PYTHON_WRITER, C_WRITER, CPP_WRITER, RICH_TEXT, MARKDOWN, WEB }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(themeViewModel: ThemeViewModel, onShowInfo: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    val currentTheme by themeViewModel.themeState.collectAsState()
    val serverUrl by themeViewModel.serverUrlState.collectAsState()
    val isDark = when(currentTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    // ---------- Global State ----------
    var workspaceMode by rememberSaveable { mutableStateOf(WorkspaceMode.KOTLIN_COMPILER) }
    
    // ---------- Text States ----------
    val ktTemplate = "fun main() {\n    println(\"Hello World\")\n}"
    var kotlinText by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(ktTemplate)) }
    
    // Runner UI State
    var showRunner by rememberSaveable { mutableStateOf(false) }
    var runnerTab by rememberSaveable { mutableStateOf(0) } 
    var stdin by rememberSaveable { mutableStateOf("") }
    var stdout by rememberSaveable { mutableStateOf("") }
    var errorMsg by rememberSaveable { mutableStateOf("") }
    var compiling by rememberSaveable { mutableStateOf(false) }
    val compiler = remember { CompilerClient() }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val cs = MaterialTheme.colorScheme

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
                        modifier = Modifier.fillMaxWidth().height(140.dp).background(
                            Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface))
                        ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column(Modifier.padding(start = 28.dp)) {
                            Text("< Lite Code />", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = cs.primary))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("WORKSPACE", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp), color = cs.primary)
                    
                    ModeItem("Kotlin Compiler", Icons.Outlined.Terminal, workspaceMode == WorkspaceMode.KOTLIN_COMPILER) { 
                        workspaceMode = WorkspaceMode.KOTLIN_COMPILER; scope.launch { drawerState.close() } 
                    }
                    ModeItem("Code Writer", Icons.Outlined.Code, workspaceMode == WorkspaceMode.KOTLIN_WRITER) { 
                        workspaceMode = WorkspaceMode.KOTLIN_WRITER; scope.launch { drawerState.close() } 
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LiteCode", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                    navigationIcon = { 
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { 
                            Icon(Icons.Outlined.Menu, "Menu", tint = cs.primary) 
                        } 
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 2.dp, color = cs.surface) {
                    BottomActionBar(
                        mode = workspaceMode,
                        compiling = compiling,
                        text = kotlinText,
                        clipboard = clipboard,
                        ctx = ctx,
                        isDark = isDark,
                        onRun = {
                            if (workspaceMode == WorkspaceMode.KOTLIN_COMPILER) {
                                showRunner = true
                                compiling = true
                                runnerTab = 1
                                stdout = ""
                                errorMsg = ""
                                scope.launch {
                                    val resp = compiler.compile(serverUrl, kotlinText.text, stdin, "kt")
                                    compiling = false
                                    if (resp.success) {
                                        stdout = resp.output
                                        runnerTab = 1
                                    } else {
                                        errorMsg = resp.error
                                        runnerTab = 2
                                    }
                                }
                            }
                        },
                        onTextChange = { kotlinText = it }
                    )
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding).fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(cs.surface)) {
                    BasicTextField(
                        value = kotlinText,
                        onValueChange = { kotlinText = it },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = cs.onSurface),
                        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        cursorBrush = SolidColor(cs.primary)
                    )
                }

                AnimatedVisibility(showRunner) {
                    RunnerPanel(
                        selectedTab = runnerTab,
                        compiling = compiling,
                        stdout = stdout,
                        errorMsg = errorMsg,
                        stdin = stdin,
                        onTabChange = { runnerTab = it },
                        onStdinChange = { stdin = it },
                        onClose = { showRunner = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ModeItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun BottomActionBar(
    mode: WorkspaceMode, 
    compiling: Boolean, 
    text: TextFieldValue, 
    clipboard: androidx.compose.ui.platform.ClipboardManager, 
    ctx: android.content.Context, 
    isDark: Boolean,
    onRun: () -> Unit, 
    onTextChange: (TextFieldValue) -> Unit
) {
    val runButtonBrush = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF64FFDA), Color(0xFF0EA5E9)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }
            ActionIconButton(Icons.Outlined.ContentPaste, "Paste", true, isDark) {
                val clip = clipboard.getText()?.text.orEmpty()
                if (clip.isNotEmpty()) {
                    val sel = text.selection
                    onTextChange(TextFieldValue(text.text.replaceRange(sel.start, sel.end, clip), TextRange(sel.start + clip.length)))
                }
            }
        }

        if (mode == WorkspaceMode.KOTLIN_COMPILER) {
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .widthIn(min = 130.dp)
                    .background(runButtonBrush, RoundedCornerShape(24.dp))
                    .clickable(enabled = !compiling, onClick = onRun),
                contentAlignment = Alignment.Center
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (compiling) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("EXECUTING", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("RUN CODE", fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ActionIconButton(icon: ImageVector, desc: String, enabled: Boolean, isDark: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp).background(
            if (isDark) Color(0xFF112240).copy(alpha = if(enabled) 1f else 0.5f) 
            else Color(0xFFDBEAFE).copy(alpha = if(enabled) 1f else 0.5f), 
            CircleShape
        )
    ) {
        Icon(icon, desc, tint = if(enabled) (if(isDark) MaterialTheme.colorScheme.primary else Color(0xFF2563EB)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun RunnerPanel(
    selectedTab: Int, 
    compiling: Boolean, 
    stdout: String, 
    errorMsg: String, 
    stdin: String, 
    onTabChange: (Int) -> Unit, 
    onStdinChange: (String) -> Unit, 
    onClose: () -> Unit
) {
    Surface(Modifier.fillMaxWidth().height(350.dp), tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, 
                    modifier = Modifier.weight(1f), 
                    edgePadding = 0.dp, 
                    containerColor = Color.Transparent,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    divider = {}
                ) {
                    listOf("Input", "Output", "Problems").forEachIndexed { i, label ->
                        Tab(selected = selectedTab == i, onClick = { onTabChange(i) }) {
                            Text(label, modifier = Modifier.padding(16.dp), fontSize = 12.sp, fontWeight = if(selectedTab==i) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, null) }
            }
            Box(Modifier.fillMaxSize().padding(12.dp)) {
                when (selectedTab) {
                    0 -> OutlinedTextField(value = stdin, onValueChange = onStdinChange, modifier = Modifier.fillMaxSize(), placeholder = { Text("Stdin...") })
                    1 -> SelectionContainer { Text(if (compiling) "Running..." else stdout.ifBlank { "No output." }, fontFamily = FontFamily.Monospace) }
                    2 -> SelectionContainer { Text(errorMsg, color = Color.Red, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}
