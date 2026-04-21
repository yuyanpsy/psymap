package com.psymap.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ProfilePage(vm: PsyMapViewModel) {
    var showApiConfig by remember { mutableStateOf(false) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var showShareBank by remember { mutableStateOf(false) }
    var showScoreSetting by remember { mutableStateOf(false) }
    var showAccountInfo by remember { mutableStateOf(false) }
    var showVersionInfo by remember { mutableStateOf(false) }
    var showCloudSync by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 恢复：用系统文件选择器选备份文件（不受权限限制）
    val restoreFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val result = vm.importBackupFromUri(context.contentResolver, uri)
            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // 用户头像区域（参考截图风格）
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFEF6C00), Color(0xFFFF9800))
                        )
                    )
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showAccountInfo = true }) {
                    // 头像（支持微信头像）
                    if (vm.currentUser.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = vm.currentUser.avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            vm.currentUser.nickname.ifBlank { "考研人" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (vm.isAdmin) "管理员" else "普通用户",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "失败不是终点，而是提升能力、靠近目标的必经过程。天赋只是起点，持续坚定的努力，才是获得成功与回报的根本原因。",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Text(
                    "—— 卡罗尔·德韦克（Carol S. Dweck）",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 目标分数展示
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("目标分数", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = { showScoreSetting = true }) {
                            Text("设定", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val scoreColors = listOf(Color(0xFFD32F2F), Color(0xFF1976D2), Color(0xFFEF6C00), Color(0xFF9C27B0), Color(0xFF00796B), Color(0xFF5D4037))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        vm.targetScores.entries.forEachIndexed { index, (name, score) ->
                            ScoreItem(name, score, scoreColors[index % scoreColors.size])
                        }
                        ScoreItem("总分", vm.targetTotalScore, Color(0xFF4CAF50))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 学习统计卡片
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("累计正确率", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    val bankStats = vm.getBankStats()
                    bankStats.forEach { (bank, correct, total) ->
                        val rate = if (total > 0) correct.toFloat() / total else 0f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${bank.subject.emoji} ${bank.name}",
                                fontSize = 13.sp, modifier = Modifier.widthIn(min = 80.dp),
                                maxLines = 1)
                            LinearProgressIndicator(
                                progress = { rate },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFFE0E0E0)
                            )
                            Text("${(rate * 100).toInt()}%",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp).widthIn(min = 42.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                maxLines = 1)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // 设置列表
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    ProfileMenuItem(Icons.Default.Sync, "云端同步",
                        subtitle = if (vm.cloudSyncing) "同步中..." else if (vm.cloudUserId != null) "已连接" else "未连接") {
                        showCloudSync = true
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ProfileMenuItem(Icons.Default.Share, "分享题库") {
                        showShareBank = true
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ProfileMenuItem(Icons.Default.GetApp, "分享App") {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "羽言心理 - 学习神器")
                            putExtra(android.content.Intent.EXTRA_TEXT, "推荐你使用「羽言心理」App！\n新知 · 成长 · 快乐\n下载地址: https://github.com/yuyanpsy/psymap/releases")
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "分享App"))
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ProfileMenuItem(Icons.Default.CloudUpload, "数据备份") {
                        showBackupConfirm = true
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ProfileMenuItem(Icons.Default.CloudDownload, "数据恢复") {
                        restoreFilePicker.launch(arrayOf("application/json", "*/*"))
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ProfileMenuItem(Icons.Default.Info, "版本信息") {
                        showVersionInfo = true
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    // API配置弹窗
    if (showApiConfig) {
        ApiConfigDialog(vm) { showApiConfig = false }
    }

    if (showBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showBackupConfirm = false },
            title = { Text("数据备份") },
            text = {
                Column {
                    Text("将所有数据导出到 Downloads/psymap_backup.json")
                    Text("卸载App后备份文件不会丢失", fontSize = 13.sp, color = Color(0xFF4CAF50))
                    Spacer(Modifier.height(8.dp))
                    Text("题库: ${vm.questionBanks.size} 个", fontSize = 13.sp, color = Color.Gray)
                    Text("题目: ${vm.questions.size} 道", fontSize = 13.sp, color = Color.Gray)
                    Text("错题: ${vm.questions.count { it.isInWrongBook }} 道", fontSize = 13.sp, color = Color.Gray)
                    Text("收藏: ${vm.questions.count { it.isInFavorites }} 道", fontSize = 13.sp, color = Color.Gray)
                    Text("打卡: ${vm.checkInRecords.size} 天", fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val result = vm.exportBackup()
                    showBackupConfirm = false
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                }) { Text("备份") }
            },
            dismissButton = { TextButton(onClick = { showBackupConfirm = false }) { Text("取消") } }
        )
    }

    if (showAccountInfo) {
        AlertDialog(
            onDismissRequest = { showAccountInfo = false },
            title = { Text("登录账号") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // 头像
                    if (vm.currentUser.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = vm.currentUser.avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.size(72.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(vm.currentUser.nickname.ifBlank { "未登录" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(if (vm.currentUser.wechatOpenId.isNotBlank()) "微信已绑定" else "微信未绑定",
                        fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        vm.logout()
                        showAccountInfo = false
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("退出登录", color = Color(0xFFD32F2F))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccountInfo = false }) { Text("关闭") } }
        )
    }

    if (showVersionInfo) {
        var checking by remember { mutableStateOf(false) }
        var updateMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVersionInfo = false },
            title = { Text("版本信息") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("羽言心理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("当前版本: v0.1.1", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(4.dp))
                        Text("检查中...", fontSize = 13.sp, color = Color.Gray)
                    }
                    if (updateMsg.isNotBlank()) {
                        Text(updateMsg, fontSize = 13.sp, color = if (updateMsg.contains("新版本")) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        checking = true; updateMsg = ""
                        // 从 GitHub Pages 检查版本信息（不受 API 限流影响）
                        Thread {
                            try {
                                val url = "https://raw.githubusercontent.com/yuyanpsy/psymap/main/version.json"
                                val resp = okhttp3.OkHttpClient.Builder()
                                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                    .newCall(okhttp3.Request.Builder().url(url).build()).execute()
                                val json = resp.body?.string() ?: ""
                                val map = com.google.gson.Gson().fromJson<Map<String, Any>>(json, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                                val latestVersion = (map["versionName"] as? String) ?: (map["version"] as? String) ?: ""
                                val downloadUrl = (map["downloadUrl"] as? String) ?: (map["url"] as? String) ?: ""
                                val currentVersion = "0.1.1"
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    checking = false
                                    if (latestVersion.isNotBlank() && latestVersion != currentVersion) {
                                        updateMsg = "发现新版本: v$latestVersion"
                                        if (downloadUrl.isNotBlank()) {
                                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl)))
                                        }
                                    } else {
                                        updateMsg = "已是最新版本"
                                    }
                                }
                            } catch (e: Exception) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    checking = false
                                    updateMsg = "检查失败: ${e.message}"
                                }
                            }
                        }.start()
                    }, enabled = !checking) { Text("检查更新") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showVersionInfo = false; showApiConfig = true }) {
                        Text("API 配置", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVersionInfo = false }) { Text("关闭") } }
        )
    }

    if (showScoreSetting) {
        ScoreSettingDialog(vm = vm, onDismiss = { showScoreSetting = false })
    }

    if (showShareBank) {
        ShareBankDialog(vm = vm, onDismiss = { showShareBank = false })
    }

    // 云端同步弹窗
    if (showCloudSync) {
        CloudSyncDialog(vm = vm, onDismiss = { showCloudSync = false })
    }
}

@Composable
fun ScoreItem(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (score > 0) "$score" else "--",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = color
        )
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun ScoreSettingDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    // 可编辑的科目列表：每项是 (科目名, 分数字符串)
    val items = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            if (vm.targetScores.isEmpty()) {
                add("政治" to "0"); add("英语" to "0"); add("专业综合" to "0")
            } else {
                vm.targetScores.forEach { (k, v) -> add(k to v.toString()) }
            }
        }
    }
    val total = items.sumOf { it.second.toIntOrNull() ?: 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标分数设定") },
        text = {
            Column {
                Text("设定各科目标分数，可新增/删除科目", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                items.forEachIndexed { index, (name, score) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = name,
                            onValueChange = { items[index] = it to score },
                            modifier = Modifier.weight(1f).height(40.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF333333)),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = score,
                            onValueChange = { newVal -> items[index] = name to newVal.filter { c -> c.isDigit() }.take(3) },
                            modifier = Modifier.width(60.dp).height(40.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color(0xFF333333), textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("分", fontSize = 13.sp, color = Color.Gray)
                        if (items.size > 1) {
                            IconButton(onClick = { items.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { items.add("科目${items.size + 1}" to "0") }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加科目", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("总分: $total 分", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF4CAF50))
            }
        },
        confirmButton = {
            Button(onClick = {
                val map = mutableMapOf<String, Int>()
                items.forEach { (name, score) ->
                    if (name.isNotBlank()) map[name] = score.toIntOrNull() ?: 0
                }
                vm.saveTargetScores(map)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF666666),
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
    }
}

// ==================== 分享题库弹窗 ====================
@Composable
fun ShareBankDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var selectedBankIds by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享题库") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("选择要分享的题库", fontSize = 13.sp, color = Color.Gray)
                    TextButton(onClick = {
                        selectedBankIds = if (selectedBankIds.size == vm.questionBanks.size)
                            emptySet() else vm.questionBanks.map { it.id }.toSet()
                    }) {
                        Text(if (selectedBankIds.size == vm.questionBanks.size) "取消全选" else "全选", fontSize = 13.sp)
                    }
                }
                vm.questionBanks.forEach { bank ->
                    val count = vm.getQuestionsForBank(bank.id).size
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedBankIds = if (bank.id in selectedBankIds) selectedBankIds - bank.id else selectedBankIds + bank.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = bank.id in selectedBankIds,
                            onCheckedChange = { selectedBankIds = if (it) selectedBankIds + bank.id else selectedBankIds - bank.id })
                        Spacer(Modifier.width(4.dp))
                        Text("${bank.subject.emoji} ${bank.name} (${count}题)", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val banks = vm.questionBanks.filter { it.id in selectedBankIds }
                    val sb = StringBuilder()
                    sb.appendLine("📚 PsyMap题库分享 (${banks.size}个题库)")
                    sb.appendLine("---")
                    banks.forEach { bank ->
                        val questions = vm.getQuestionsForBank(bank.id)
                        sb.appendLine("\n${bank.subject.emoji} ${bank.name} (${questions.size}题)")
                        questions.take(10).forEachIndexed { idx, q ->
                            sb.appendLine("${idx + 1}. ${q.content}")
                            if (q.answer.isNotBlank()) sb.appendLine("   答案: ${q.answer}")
                        }
                        if (questions.size > 10) sb.appendLine("...还有 ${questions.size - 10} 题")
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "PsyMap题库分享")
                        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "分享题库到"))
                    onDismiss()
                },
                enabled = selectedBankIds.isNotEmpty()
            ) { Text("分享") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== 云端同步弹窗 ====================
@Composable
fun CloudSyncDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isConnected = vm.cloudUserId != null

    LaunchedEffect(isConnected) { if (isConnected) vm.fetchSyncCode() }
    LaunchedEffect(Unit) {
        if (isConnected) { vm.fetchSyncCode(); vm.updateLocalHash() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "云端同步",
                color = if (isConnected) Color(0xFF4CAF50) else Color.Unspecified
            )
        },
        text = {
            Column {
                if (isConnected) {
                    // 同步码
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("你的同步码", fontSize = 13.sp, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                vm.syncCode.ifBlank { "..." },
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF6C00),
                                letterSpacing = 8.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("在 Web 端输入此码即可同步数据", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    // 拉取按钮（暂时始终可用，因为无法预知云端是否有变化）
                    Button(
                        onClick = {
                            vm.syncFromCloud()
                            android.widget.Toast.makeText(context, "正在从云端拉取...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !vm.cloudSyncing
                    ) { Text(if (vm.cloudSyncing) "同步中..." else "从云端拉取最新数据") }

                    Spacer(Modifier.height(8.dp))

                    // 推送按钮（本地有变化时才可点击）
                    Button(
                        onClick = {
                            vm.pushToCloud { result ->
                                android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vm.hasLocalChanges) Color(0xFFEF6C00) else Color(0xFFBDBDBD)
                        ),
                        enabled = !vm.cloudSyncing && vm.hasLocalChanges
                    ) { Text("推送本地数据到云端") }

                    if (!vm.hasLocalChanges) {
                        Text("本地数据已是最新", fontSize = 11.sp, color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    Text("未连接云端", color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text("连接后可获取同步码，在 Web 端输入即可同步数据。", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val nickname = vm.currentUser.nickname.ifBlank { "考研人" }
                            vm.cloudLogin(nickname) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                if (success) {
                                    vm.fetchSyncCode()
                                    vm.pushToCloud { result ->
                                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !vm.cloudSyncing
                    ) { Text("连接云端") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 权限管理弹窗 ====================
@Composable
fun PermissionsDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var newAdminName by remember { mutableStateOf("") }
    var adminList by remember { mutableStateOf(vm.getAdminList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限管理") },
        text = {
            Column {
                Text("管理员列表", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                if (adminList.isEmpty()) {
                    Text("暂无其他管理员", fontSize = 13.sp, color = Color.Gray)
                } else {
                    adminList.forEach { name ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFEF6C00))
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                vm.removeAdmin(name)
                                adminList = vm.getAdminList()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("添加管理员（输入微信昵称）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newAdminName,
                        onValueChange = { newAdminName = it },
                        label = { Text("微信昵称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newAdminName.isNotBlank()) {
                                vm.addAdmin(newAdminName)
                                adminList = vm.getAdminList()
                                newAdminName = ""
                            }
                        },
                        enabled = newAdminName.isNotBlank()
                    ) { Text("添加") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
