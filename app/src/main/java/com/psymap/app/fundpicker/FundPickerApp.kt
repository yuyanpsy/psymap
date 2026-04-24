package com.psymap.app.fundpicker

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

enum class FundTab(val label: String) {
    HOME("首页"), DISCOVER("发现"), PORTFOLIO("持仓"),
    FAVORITES("自选"), PROFILE("我的")
}

@Composable
fun FundPickerApp(
    vm: FundPickerViewModel = viewModel(),
    onBack: () -> Unit
) {
    var currentTab by remember { mutableStateOf(FundTab.HOME) }
    // 详情页导航
    var showDetail by remember { mutableStateOf(false) }
    var showAiLab by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FundBlue,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD2E3FC),
            surface = Color.White,
            background = FundBg,
            error = FundRed
        )
    ) {
        if (showAiLab) {
            AiLabPage(vm = vm, onBack = { showAiLab = false })
            return@MaterialTheme
        }

        if (showDetail) {
            FundDetailPage(
                vm = vm,
                onBack = { showDetail = false },
                onOpenAiLab = { showAiLab = true },
                onBuy = { /* 买入后切到持仓tab */ currentTab = FundTab.PORTFOLIO }
            )
            return@MaterialTheme
        }

        Scaffold(
            topBar = {
                if (currentTab == FundTab.HOME) {
                    FundTopBar(title = "智选基金", onBack = onBack)
                }
            },
            bottomBar = {
                FundBottomBar(currentTab) { currentTab = it }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (currentTab) {
                    FundTab.HOME -> FundHomePage(vm = vm, onFundClick = {
                        vm.selectFund(it); showDetail = true
                    })
                    FundTab.DISCOVER -> FundDiscoverPage(vm = vm, onFundClick = {
                        vm.selectFund(it); showDetail = true
                    })
                    FundTab.PORTFOLIO -> PortfolioPage(vm = vm, onFundClick = {
                        vm.selectFundByCode(it); showDetail = true
                    })
                    FundTab.FAVORITES -> FundFavoritesPage(vm = vm, onFundClick = {
                        vm.selectFund(it); showDetail = true
                    })
                    FundTab.PROFILE -> FundProfilePage(vm = vm, onBack = onBack)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = FundTextPrimary
        )
    )
}

@Composable
fun FundBottomBar(currentTab: FundTab, onTabSelected: (FundTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
        NavigationBarItem(
            selected = currentTab == FundTab.HOME,
            onClick = { onTabSelected(FundTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("首页", fontSize = 11.sp) },
            colors = fundNavColors()
        )
        NavigationBarItem(
            selected = currentTab == FundTab.DISCOVER,
            onClick = { onTabSelected(FundTab.DISCOVER) },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("发现", fontSize = 11.sp) },
            colors = fundNavColors()
        )
        NavigationBarItem(
            selected = currentTab == FundTab.PORTFOLIO,
            onClick = { onTabSelected(FundTab.PORTFOLIO) },
            icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
            label = { Text("持仓", fontSize = 11.sp) },
            colors = fundNavColors()
        )
        NavigationBarItem(
            selected = currentTab == FundTab.FAVORITES,
            onClick = { onTabSelected(FundTab.FAVORITES) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("自选", fontSize = 11.sp) },
            colors = fundNavColors()
        )
        NavigationBarItem(
            selected = currentTab == FundTab.PROFILE,
            onClick = { onTabSelected(FundTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("我的", fontSize = 11.sp) },
            colors = fundNavColors()
        )
    }
}

@Composable
private fun fundNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = FundBlue,
    selectedTextColor = FundBlue,
    indicatorColor = Color.Transparent,
    unselectedIconColor = FundTextSecondary,
    unselectedTextColor = FundTextSecondary
)
