package com.psymap.app.fundpicker

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun FundDiscoverPage(
    vm: FundPickerViewModel,
    onFundClick: (Fund) -> Unit,
    onSectorClick: (String) -> Unit = {}
) {
    val funds by vm.funds.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val predictions by vm.aiPredictions.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val positions by vm.positions.collectAsState()
    val favCodes = remember(favorites) { favorites.map { it.code }.toSet() }
    val posCodes = remember(positions) { positions.map { it.fundCode }.toSet() }
    val hasSearch = query.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("发现", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { vm.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("输入基金代码或名称", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { vm.search("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = FundBlue, unfocusedBorderColor = Color(0xFFE0E0E0))
            )
            Spacer(Modifier.height(12.dp))
        }

        if (isLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = FundBlue) }
        }

        if (hasSearch) {
            if (funds.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("未找到基金", fontSize = 14.sp, color = FundTextSecondary)
                    }
                }
            }
            items(funds, key = { it.code }) { fund ->
                val pred = predictions[fund.code]
                val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
                val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
                val maxDd = (pred?.get("max_drawdown") as? Double) ?: 100.0
                val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0
                FundListItem(
                    fund = fund,
                    onClick = { onFundClick(fund) },
                    isFavorite = fund.code in favCodes,
                    isPositioned = fund.code in posCodes,
                    confidence = conf,
                    sharpe = sharpe,
                    maxDrawdown = maxDd,
                    positivePct = posPct,
                    onFavoriteToggle = {
                        vm.repo.ensureFundCached(fund)
                        vm.toggleFavorite(fund.code)
                    }
                )
            }
        } else {
            // 行业板块网格
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(FundBlue, RoundedCornerShape(1.5.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("行业板块", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = FundTextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("${FundApi.SECTOR_LIST.size} 个板块", fontSize = 11.sp,
                        color = FundTextSecondary)
                }
            }

            val sectorNames = FundApi.SECTOR_LIST.map { it.first } + listOf("__golden__")
            val goldenCount = predictions.count { (_, v) ->
                val prob = (v["probability"] as? Double) ?: 0.0
                val conf = (v["confidence"] as? Double)?.toInt() ?: 0
                val sharpe = (v["sharpe"] as? Double) ?: 0.0
                val maxDd = (v["max_drawdown"] as? Double) ?: 100.0
                val posPct = (v["positive_pct"] as? Double) ?: 0.0
                prob >= 70 && conf >= 4 && sharpe > 2.0 && maxDd < 15.0 && posPct > 80
            }
            items(sectorNames.chunked(4)) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sector ->
                        if (sector == "__golden__") {
                            // 金色基金入口
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF0F4FF))
                                    .clickable { onSectorClick("__golden__") }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("金色基金", fontSize = 13.sp, color = Color(0xFFD4AF37),
                                        fontWeight = FontWeight.Medium)
                                    Text("${goldenCount}只", fontSize = 9.sp, color = Color(0xFFDAA520))
                                }
                            }
                        } else {
                            // 普通板块
                            val keywords = FundApi.SECTOR_LIST.find { it.first == sector }?.second ?: emptyList()
                            val maxAi = predictions.entries
                                .filter { (_, v) -> 
                                    val name = v["name"] as? String ?: ""
                                    keywords.any { kw -> name.contains(kw) }
                                }
                                .maxOfOrNull { (it.value["probability"] as? Double)?.toInt() ?: 0 } ?: 0

                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF0F4FF))
                                    .clickable { onSectorClick(sector) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(sector, fontSize = 13.sp, color = FundBlue,
                                        fontWeight = FontWeight.Medium)
                                    if (maxAi > 0) {
                                        Text("AI最高 ${maxAi}%", fontSize = 9.sp,
                                            color = if (maxAi >= 70) Color(0xFFD4AF37) else FundTextSecondary)
                                    } else {
                                        Text("AI最高 --", fontSize = 9.sp, color = FundTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * 板块基金列表页 — 显示某板块下的所有基金
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectorFundListPage(
    sectorName: String,
    vm: FundPickerViewModel,
    onBack: () -> Unit,
    onFundClick: (Fund) -> Unit
) {
    var baseFunds by remember { mutableStateOf<List<Fund>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 直接观察 AI 预测数据 StateFlow — 数据加载完后自动触发重组
    val predictions by vm.aiPredictions.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val positions by vm.positions.collectAsState()
    val favCodes = remember(favorites) { favorites.map { it.code }.toSet() }
    val posCodes = remember(positions) { positions.map { it.fundCode }.toSet() }
    // 当 baseFunds 或 predictions 变化时，重新计算 AI 分数
    val sectorFunds = remember(baseFunds, predictions) {
        baseFunds.map { fund ->
            val pred = predictions[fund.code]
            val score = if (pred != null) {
                if (pred.containsKey("probability")) (pred["probability"] as? Double)?.toInt() ?: 0
                else 0
            } else 0
            fund.copy(aiScore = score)
        }
    }

    // 从 aiPredictions（Supabase）按板块字段或关键词筛选
    // 数据源唯一：predictions 里包含 AI预测率 + 涨跌幅 + 板块归类
    LaunchedEffect(sectorName, predictions) {
        loading = true
        val keywords = FundApi.SECTOR_LIST.find { it.first == sectorName }?.second ?: emptyList()
        if (keywords.isEmpty()) { loading = false; return@LaunchedEffect }

        val filtered = predictions.entries
            .filter { (_, v) ->
                val sector = v["sector"] as? String ?: ""
                if (sector == sectorName) return@filter true
                val name = v["name"] as? String ?: ""
                keywords.any { kw -> name.contains(kw) }
            }
            .map { (code, v) ->
                Fund(
                    code = code,
                    name = v["name"] as? String ?: code,
                    aiScore = (v["probability"] as? Double)?.toInt() ?: 0,
                    yearChange = (v["year_change"] as? Double) ?: 0.0,
                    sixMonthChange = (v["six_month_change"] as? Double) ?: 0.0,
                    threeMonthChange = (v["three_month_change"] as? Double) ?: 0.0,
                    monthChange = (v["month_change"] as? Double) ?: 0.0,
                    weekChange = (v["week_change"] as? Double) ?: 0.0,
                )
            }
            .sortedByDescending { it.aiScore }

        baseFunds = filtered
        loading = false
    }

    // 排序状态：最后点击的为主排序，之前的为次排序
    var primarySort by remember { mutableStateOf("ai") }  // 默认按 AI 降序（和板块网格"AI最高"一致）
    var primaryAsc by remember { mutableStateOf(false) }
    var secondarySort by remember { mutableStateOf<String?>(null) }
    var secondaryAsc by remember { mutableStateOf(false) }

    // 排序后的列表
    val sortedFunds = remember(sectorFunds, primarySort, primaryAsc, secondarySort, secondaryAsc) {
        fun getSortValue(fund: Fund, key: String): Double = when (key) {
            "ai" -> fund.aiScore.toDouble()
            "3m" -> fund.threeMonthChange
            "6m" -> fund.sixMonthChange
            "week" -> fund.weekChange
            "month" -> fund.monthChange
            else -> fund.yearChange
        }
        val primary = compareBy<Fund> { getSortValue(it, primarySort) }
            .let { if (primaryAsc) it else it.reversed() }
        val secondary = secondarySort?.let { key ->
            compareBy<Fund> { getSortValue(it, key) }
                .let { if (secondaryAsc) it else it.reversed() }
        }
        if (secondary != null) {
            sectorFunds.sortedWith(primary.then(secondary))
        } else {
            sectorFunds.sortedWith(primary)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$sectorName · 相关基金", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Search, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(FundBg)) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        // 固定排序栏
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${sectorFunds.size} 只", fontSize = 12.sp, color = FundTextSecondary)
            Spacer(Modifier.weight(1f))
            var timeExpanded by remember { mutableStateOf(false) }
            val timeLabel = when (if (primarySort != "ai") primarySort else "year") {
                "week" -> "近1周"; "month" -> "近1月"; "3m" -> "近3月"; "6m" -> "近6月"; else -> "近1年"
            }
            Box {
                Text("$timeLabel ${if (primarySort != "ai") (if (!primaryAsc) "↓" else "↑") else ""}",
                    fontSize = 13.sp, color = if (primarySort != "ai") FundBlue else FundTextSecondary,
                    fontWeight = FontWeight.Medium, modifier = Modifier.clickable { timeExpanded = true })
                DropdownMenu(expanded = timeExpanded, onDismissRequest = { timeExpanded = false }) {
                    listOf("year" to "近1年", "6m" to "近6月", "3m" to "近3月", "month" to "近1月", "week" to "近1周").forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            if (primarySort == key) primaryAsc = !primaryAsc
                            else { if (primarySort == "ai") { secondarySort = "ai"; secondaryAsc = primaryAsc }; primarySort = key; primaryAsc = false }
                            timeExpanded = false; coroutineScope.launch { listState.animateScrollToItem(0) }
                        })
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Text("AI ${if (primarySort == "ai" || secondarySort == "ai") (if (if (primarySort == "ai") !primaryAsc else !secondaryAsc) "↓" else "↑") else ""}",
                fontSize = 13.sp, color = if (primarySort == "ai") FundBlue else if (secondarySort == "ai") FundBlue else FundTextSecondary,
                fontWeight = FontWeight.Medium, modifier = Modifier.clickable {
                    if (primarySort == "ai") primaryAsc = !primaryAsc
                    else { secondarySort = primarySort; secondaryAsc = primaryAsc; primarySort = "ai"; primaryAsc = false }
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                })
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FundBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }

            itemsIndexed(sortedFunds) { index, fund ->
                val pred = predictions[fund.code]
                val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
                val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
                val maxDd = (pred?.get("max_drawdown") as? Double) ?: 100.0
                val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.5.dp),
                    onClick = { onFundClick(fund) }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        // 统一头部
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", fontSize = 12.sp, color = FundTextSecondary,
                                modifier = Modifier.width(22.dp))
                            FundHeaderRow(
                                fundName = fund.name, fundCode = fund.code,
                                isFavorite = fund.code in favCodes,
                                isPositioned = fund.code in posCodes,
                                onFavoriteToggle = {
                                    vm.repo.ensureFundCached(fund)
                                    vm.toggleFavorite(fund.code)
                                },
                                goldenWhenHighAi = true, aiScore = fund.aiScore,
                                confidence = conf, sharpe = sharpe,
                                maxDrawdown = maxDd, positivePct = posPct,
                                nameFontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(22.dp))
                            if (fund.fundSize.isNotBlank()) {
                                Text("规模 ${fund.fundSize}亿", fontSize = 11.sp, color = FundTextSecondary)
                                Spacer(Modifier.width(10.dp))
                            }
                            val displayValue = when (primarySort) {
                                "week" -> fund.weekChange
                                "month" -> fund.monthChange
                                "3m" -> fund.threeMonthChange
                                "6m" -> fund.sixMonthChange
                                "ai" -> fund.yearChange
                                else -> fund.yearChange
                            }
                            val displayLabel = when (primarySort) {
                                "week" -> "近1周"
                                "month" -> "近1月"
                                "3m" -> "近3月"
                                "6m" -> "近6月"
                                else -> "近1年"
                            }
                            Text("$displayLabel ", fontSize = 11.sp, color = FundTextSecondary)
                            if (displayValue != 0.0) {
                                Text(formatChange(displayValue), fontSize = 12.sp,
                                    color = changeColor(displayValue), fontWeight = FontWeight.Medium)
                            } else {
                                Text("--", fontSize = 12.sp, color = FundTextSecondary)
                            }
                            Spacer(Modifier.weight(1f))
                            if (fund.aiScore > 0) {
                                Text("AI ${fund.aiScore}%", fontSize = 12.sp, color = FundBlue,
                                    fontWeight = FontWeight.Medium)
                            } else {
                                Text("AI --", fontSize = 12.sp, color = FundTextSecondary)
                            }
                        }
                    }
                }
            }

            if (!loading && sectorFunds.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("暂无相关基金数据", fontSize = 14.sp, color = FundTextSecondary)
                    }
                }
            }
        }
        } // Column
    }
}

/**
 * 金色基金列表页 — 展示所有符合金色标准的基金
 * 条件：AI≥70% + 置信度≥4 + 夏普>2 + 回撤<15% + 正收益>80%
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldenFundListPage(
    vm: FundPickerViewModel,
    onBack: () -> Unit,
    onFundClick: (Fund) -> Unit
) {
    val predictions by vm.aiPredictions.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val positions by vm.positions.collectAsState()
    val favCodes = remember(favorites) { favorites.map { it.code }.toSet() }
    val posCodes = remember(positions) { positions.map { it.fundCode }.toSet() }

    // 筛选金色基金
    val goldenFunds = remember(predictions) {
        predictions.entries
            .filter { (_, v) ->
                val prob = (v["probability"] as? Double) ?: 0.0
                val conf = (v["confidence"] as? Double)?.toInt() ?: 0
                val sharpe = (v["sharpe"] as? Double) ?: 0.0
                val maxDd = (v["max_drawdown"] as? Double) ?: 100.0
                val posPct = (v["positive_pct"] as? Double) ?: 0.0
                prob >= 70 && conf >= 4 && sharpe > 2.0 && maxDd < 15.0 && posPct > 80
            }
            .map { (code, v) ->
                Fund(
                    code = code,
                    name = v["name"] as? String ?: code,
                    aiScore = (v["probability"] as? Double)?.toInt() ?: 0,
                    yearChange = (v["year_change"] as? Double) ?: 0.0,
                    sixMonthChange = (v["six_month_change"] as? Double) ?: 0.0,
                    threeMonthChange = (v["three_month_change"] as? Double) ?: 0.0,
                    monthChange = (v["month_change"] as? Double) ?: 0.0,
                    weekChange = (v["week_change"] as? Double) ?: 0.0,
                )
            }
    }

    // 排序
    var primarySort by remember { mutableStateOf("ai") }
    var primaryAsc by remember { mutableStateOf(false) }

    val sortedFunds = remember(goldenFunds, primarySort, primaryAsc) {
        val comparator = compareBy<Fund> {
            when (primarySort) {
                "ai" -> it.aiScore.toDouble()
                "year" -> it.yearChange
                "6m" -> it.sixMonthChange
                "3m" -> it.threeMonthChange
                "month" -> it.monthChange
                "week" -> it.weekChange
                else -> it.aiScore.toDouble()
            }
        }
        if (primaryAsc) goldenFunds.sortedWith(comparator)
        else goldenFunds.sortedWith(comparator.reversed())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("金色基金", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Search, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(FundBg)) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // 排序栏
            Row(
                Modifier.fillMaxWidth().background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("共 ${sortedFunds.size} 只", fontSize = 12.sp, color = FundTextSecondary)
                Spacer(Modifier.weight(1f))

                val sortOptions = listOf(
                    "year" to "近1年", "6m" to "近6月", "3m" to "近3月",
                    "month" to "近1月", "week" to "近1周"
                )
                var timeExpanded by remember { mutableStateOf(false) }
                val timeLabel = sortOptions.find { it.first == primarySort }?.second ?: "近1年"
                Box {
                    Text(
                        "$timeLabel ${if (primarySort != "ai") (if (!primaryAsc) "↓" else "↑") else ""}",
                        fontSize = 13.sp,
                        color = if (primarySort != "ai") FundBlue else FundTextSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { timeExpanded = true }
                    )
                    DropdownMenu(expanded = timeExpanded, onDismissRequest = { timeExpanded = false }) {
                        sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                if (primarySort == key) primaryAsc = !primaryAsc
                                else { primarySort = key; primaryAsc = false }
                                timeExpanded = false
                                coroutineScope.launch { listState.animateScrollToItem(0) }
                            })
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "AI ${if (primarySort == "ai") (if (!primaryAsc) "↓" else "↑") else ""}",
                    fontSize = 13.sp,
                    color = if (primarySort == "ai") FundBlue else FundTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        if (primarySort == "ai") primaryAsc = !primaryAsc
                        else { primarySort = "ai"; primaryAsc = false }
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(sortedFunds) { index, fund ->
                    val pred = predictions[fund.code]
                    val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
                    val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
                    val maxDd = (pred?.get("max_drawdown") as? Double) ?: 0.0
                    val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0

                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(0.5.dp),
                        onClick = { onFundClick(fund) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", fontSize = 12.sp, color = FundTextSecondary,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                                FundHeaderRow(
                                    fundName = fund.name, fundCode = fund.code,
                                    isFavorite = fund.code in favCodes,
                                    isPositioned = fund.code in posCodes,
                                    onFavoriteToggle = {
                                        vm.repo.ensureFundCached(fund)
                                        vm.toggleFavorite(fund.code)
                                    },
                                    goldenWhenHighAi = true, aiScore = fund.aiScore,
                                    confidence = conf, sharpe = sharpe,
                                    maxDrawdown = maxDd, positivePct = posPct,
                                    nameFontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(22.dp))
                                Text("夏普 %.1f".format(sharpe), fontSize = 10.sp, color = FundTextSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text("回撤 %.1f%%".format(maxDd), fontSize = 10.sp, color = FundTextSecondary)
                                Spacer(Modifier.width(8.dp))
                                Text("正收益 %.0f%%".format(posPct), fontSize = 10.sp, color = FundTextSecondary)
                                Spacer(Modifier.weight(1f))
                                val displayValue = when (primarySort) {
                                    "week" -> fund.weekChange
                                    "month" -> fund.monthChange
                                    "3m" -> fund.threeMonthChange
                                    "6m" -> fund.sixMonthChange
                                    else -> fund.yearChange
                                }
                                val displayLabel = when (primarySort) {
                                    "week" -> "近1周"
                                    "month" -> "近1月"
                                    "3m" -> "近3月"
                                    "6m" -> "近6月"
                                    "ai" -> "近1年"
                                    else -> "近1年"
                                }
                                if (primarySort != "ai") {
                                    Text("$displayLabel ", fontSize = 11.sp, color = FundTextSecondary)
                                    if (displayValue != 0.0) {
                                        Text(formatChange(displayValue), fontSize = 12.sp,
                                            color = changeColor(displayValue), fontWeight = FontWeight.Medium)
                                    } else {
                                        Text("--", fontSize = 12.sp, color = FundTextSecondary)
                                    }
                                } else {
                                    Text("AI ${fund.aiScore}%", fontSize = 12.sp,
                                        color = Color(0xFFD4AF37), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                if (sortedFunds.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("暂无金色基金", fontSize = 14.sp, color = FundTextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text("夏普数据仍在计算中，请稍后刷新", fontSize = 12.sp, color = FundTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
