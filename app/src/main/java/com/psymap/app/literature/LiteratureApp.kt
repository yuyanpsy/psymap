package com.psymap.app.literature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

enum class LitTab(val label: String) {
    LIBRARY("文献库"), READER("阅读"), AI("AI助手")
}

@Composable
fun LiteratureApp(
    vm: LiteratureViewModel = viewModel(),
    onBack: () -> Unit
) {
    var currentTab by remember { mutableStateOf(LitTab.LIBRARY) }
    var showReader by remember { mutableStateOf(false) }
    var showCitation by remember { mutableStateOf(false) }

    BackHandler(enabled = showReader || showCitation || currentTab != LitTab.LIBRARY) {
        when {
            showReader -> showReader = false
            showCitation -> showCitation = false
            currentTab != LitTab.LIBRARY -> currentTab = LitTab.LIBRARY
        }
    }

    val litOrange = Color(0xFFEF6C00)

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = litOrange,
            onPrimary = Color.White,
            surface = Color.White,
            background = Color(0xFFFAFAFA)
        )
    ) {
        if (showReader && vm.selectedLiterature != null) {
            PdfReaderPage(vm = vm, onBack = { showReader = false })
            return@MaterialTheme
        }

        if (showCitation) {
            CitationPage(vm = vm, onBack = { showCitation = false })
            return@MaterialTheme
        }

        Scaffold(
            topBar = {
                LitTopBar(title = "文献管理", onBack = onBack, actions = {
                    IconButton(onClick = { showCitation = true }) {
                        Icon(Icons.Default.FormatQuote, contentDescription = "引用管理")
                    }
                })
            },
            bottomBar = {
                LitBottomBar(currentTab) { currentTab = it }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (currentTab) {
                    LitTab.LIBRARY -> LiteratureLibraryPage(
                        vm = vm,
                        onOpenPdf = { lit ->
                            vm.selectLiterature(lit)
                            showReader = true
                        }
                    )
                    LitTab.READER -> {
                        if (vm.selectedLiterature != null) {
                            PdfReaderPage(vm = vm, onBack = { currentTab = LitTab.LIBRARY })
                        } else {
                            LiteratureLibraryPage(vm = vm, onOpenPdf = { lit ->
                                vm.selectLiterature(lit)
                                showReader = true
                            })
                        }
                    }
                    LitTab.AI -> AiAssistantPage(vm = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LitTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = Color(0xFFEF6C00)
        )
    )
}

@Composable
fun LitBottomBar(currentTab: LitTab, onTabSelected: (LitTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
        NavigationBarItem(
            selected = currentTab == LitTab.LIBRARY,
            onClick = { onTabSelected(LitTab.LIBRARY) },
            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
            label = { Text("文献库", fontSize = 11.sp) },
            colors = litNavColors()
        )
        NavigationBarItem(
            selected = currentTab == LitTab.READER,
            onClick = { onTabSelected(LitTab.READER) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
            label = { Text("阅读", fontSize = 11.sp) },
            colors = litNavColors()
        )
        NavigationBarItem(
            selected = currentTab == LitTab.AI,
            onClick = { onTabSelected(LitTab.AI) },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            label = { Text("AI助手", fontSize = 11.sp) },
            colors = litNavColors()
        )
    }
}

@Composable
private fun litNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color(0xFFEF6C00),
    selectedTextColor = Color(0xFFEF6C00),
    indicatorColor = Color.Transparent,
    unselectedIconColor = Color(0xFF999999),
    unselectedTextColor = Color(0xFF999999)
)
