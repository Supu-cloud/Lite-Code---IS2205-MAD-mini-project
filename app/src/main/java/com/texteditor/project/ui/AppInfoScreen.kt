package com.texteditor.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    
    // Premium theme-aware colors
    val backgroundColor = cs.surface
    val accentColor = cs.primary
    val contentColor = cs.onSurface
    val dividerColor = cs.outlineVariant.copy(alpha = 0.2f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "System Information",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = accentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Section with Cinematic Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(accentColor.copy(alpha = 0.15f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "< LiteCode />",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        ),
                        color = accentColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Professional Mobile IDE",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.6f),
                        letterSpacing = 2.sp
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                
                InfoAccordionItem(
                    title = "System Overview",
                    content = "LiteCode is a professional-grade mobile IDE and text editor designed for developers and power users. It provides a specialized environment for Kotlin development, web experimentation (HTML/CSS/JS), and rich-text document management, all wrapped in a sleek, high-tech interface.",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                InfoAccordionItem(
                    title = "Tri-Mode Workspace",
                    content = "• Kotlin Mode: Write and execute Kotlin code. Integrated with a remote Flask compiler server for real-time results.\n\n" +
                            "• Web Mode: An offline playground for HTML, CSS, and JavaScript. Combines all three into an instant preview with a built-in JS console.\n\n" +
                            "• Text Mode: A dedicated rich-text editor for notes and documents with support for advanced formatting.",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                InfoAccordionItem(
                    title = "Core Capabilities",
                    content = "• SQL Room Persistence: Automatically tracks recent files and manages a reusable Snippet Library.\n\n" +
                            "• Professional Tools: Real-time Find & Replace with match navigation, global Replace All, and independent Undo/Redo history.\n\n" +
                            "• Rich Formatting: Full support for Bold, Italic, Underline, custom sizes, and vibrant colors in Text mode.",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                InfoAccordionItem(
                    title = "Persistence Formats",
                    content = "• Code: .kt, .java, .py, .c, .cpp\n" +
                            "• Web: .html, .css, .js\n" +
                            "• Text: .txt (Plain), .lctxt (Rich Text JSON)",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                InfoAccordionItem(
                    title = "Development Team",
                    content = "• Supuni Weralupitya - 24021148\n" +
                            "• Amasha Lakshan - 24021131\n" +
                            "• Hemaka Wanshanath - 24021105",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                InfoAccordionItem(
                    title = "Project Specification",
                    content = "Course: IS2205: Mobile Application Design and Development\n" +
                            "Problem: Modern Mobile Text Editor with Incremental Version Control\n" +
                            "Year: 2026",
                    accentColor = accentColor,
                    contentColor = contentColor,
                    dividerColor = dividerColor
                )

                Spacer(Modifier.height(60.dp))

                Text(
                    text = "© 2026 LiteCode Team",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    color = contentColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
fun InfoAccordionItem(
    title: String,
    content: String,
    accentColor: Color,
    contentColor: Color,
    dividerColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "rotate"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    letterSpacing = 1.sp
                ),
                fontWeight = FontWeight.Bold,
                color = if (expanded) accentColor else contentColor.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = if (expanded) accentColor else contentColor.copy(alpha = 0.4f),
                modifier = Modifier.rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 26.sp,
                color = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp, start = 4.dp, end = 4.dp)
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
    }
}
