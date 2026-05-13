package com.psymap.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("首页"),
    DISCOVER("题库"),
    CAMERA("拍照"),
    PRACTICE("学习"),
    PROFILE("我的")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsyMapApp(
    vm: PsyMapViewModel = viewModel(),
    sharedImageUris: List<android.net.Uri> = emptyList(),
    sharedFileUri: android.net.Uri? = null
) {
    val isLoading by vm.isLoading.collectAsState()
    val loadingMsg by vm.loadingMessage.collectAsState()
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showImportDialog by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showGoStudySession by remember { mutableStateOf(false) }

    // 双击返回退出
    var backPressedOnce by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            android.widget.Toast.makeText(context, "再次返回将退出应用", android.widget.Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.MainScope().launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    // 处理外部分享的图片（拼接后进入拍照识题）
    LaunchedEffect(sharedImageUris) {
        if (sharedImageUris.isNotEmpty()) {
            try {
                val bitmaps = sharedImageUris.mapNotNull { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmaps.isNotEmpty()) {
                    // 垂直拼接多张图片
                    val totalWidth = bitmaps.maxOf { it.width }
                    val totalHeight = bitmaps.sumOf { it.height }
                    val merged = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(merged)
                    var y = 0f
                    bitmaps.forEach { bmp ->
                        canvas.drawBitmap(bmp, (totalWidth - bmp.width) / 2f, y, null)
                        y += bmp.height
                    }
                    capturedBitmap = merged
                    showImportDialog = true
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "图片加载失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    // 处理外部分享的文档
    var showSharedFileImport by remember { mutableStateOf(false) }
    LaunchedEffect(sharedFileUri) {
        if (sharedFileUri != null) {
            showSharedFileImport = true
        }
    }

    // 全分辨率拍照
    val photoFile = remember {
        File(context.cacheDir, "psymap_photo.jpg").apply { if (!exists()) createNewFile() }
    }
    val photoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    // 多张拍照支持
    var capturedPhotos by remember { mutableStateOf(listOf<Bitmap>()) }
    var showMultiPhotoPreview by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            try {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bitmap != null) {
                    capturedPhotos = capturedPhotos + bitmap
                    showMultiPhotoPreview = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "照片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(photoUri)
        else Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    // 从相册选择多张图片
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            try {
                val bitmaps = uris.mapNotNull { uri ->
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmaps.size == 1) {
                    capturedBitmap = bitmaps[0]
                    showImportDialog = true
                } else if (bitmaps.size > 1) {
                    // 垂直拼接
                    val totalWidth = bitmaps.maxOf { it.width }
                    val totalHeight = bitmaps.sumOf { it.height }
                    val merged = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(merged)
                    var y = 0f
                    bitmaps.forEach { bmp ->
                        canvas.drawBitmap(bmp, (totalWidth - bmp.width) / 2f, y, null)
                        y += bmp.height
                    }
                    capturedBitmap = merged
                    showImportDialog = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 登录检查
    if (!vm.isLoggedIn) {
        LoginPage(vm = vm)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            Column {
                // 全局迷你播放条（磨耳朵播放时显示）
                val svc = AudioPlaybackService.instance
                var miniPlaying by remember { mutableStateOf(false) }
                var miniFileName by remember { mutableStateOf("") }
                var miniProgress by remember { mutableStateOf(0f) }

                LaunchedEffect(Unit) {
                    while (true) {
                        val s = AudioPlaybackService.instance
                        miniPlaying = s?.isPlaying == true
                        miniFileName = s?.currentFileName ?: ""
                        val dur = s?.duration ?: 0
                        val pos = s?.currentPosition ?: 0
                        miniProgress = if (dur > 0) pos.toFloat() / dur else 0f
                        kotlinx.coroutines.delay(500)
                    }
                }

                if (miniPlaying || miniFileName.isNotBlank()) {
                    Surface(color = Color(0xFF333333), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            LinearProgressIndicator(
                                progress = { miniProgress },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Color(0xFFFF8A00),
                                trackColor = Color(0xFF555555),
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null,
                                    tint = Color(0xFFFF8A00), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(miniFileName.take(20), color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.weight(1f), maxLines = 1)
                                IconButton(onClick = {
                                    val s = AudioPlaybackService.instance
                                    if (s != null) {
                                        if (s.isPlaying) { s.mediaPlayer?.pause(); s.isPlaying = false }
                                        else { s.mediaPlayer?.start(); s.isPlaying = true }
                                    }
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(if (miniPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = {
                                    val stopIntent = android.content.Intent(context, AudioPlaybackService::class.java).apply { action = "STOP" }
                                    context.startService(stopIntent)
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = null,
                                        tint = Color(0xFF999999), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    if (tab == AppTab.CAMERA) {
                        if (vm.isAdmin) {
                            showImageSourceDialog = true
                        } else {
                            Toast.makeText(context, "仅管理员可拍照录题", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        currentTab = tab
                    }
                }
            )
            }  // end Column
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                AppTab.HOME -> HomePage(vm = vm)
                AppTab.DISCOVER -> DiscoverPage(vm = vm)
                AppTab.CAMERA -> {} // 拍照不是页面
                AppTab.PRACTICE -> PracticePage(vm = vm)
                AppTab.PROFILE -> ProfilePage(vm = vm)
            }

            // 全局 Go 浮动球
            val totalTarget = vm.questionBanks.sumOf { vm.dailyTargets[it.id] ?: 0 }
            val todayDone = vm.questionBanks.sumOf { vm.todayCheckIn.bankStudiedIds[it.id]?.size ?: 0 }
            val remaining = (totalTarget - todayDone).coerceAtLeast(0)
            val planCompleted = remaining == 0 && totalTarget > 0
            // 完成后进入超额模式：显示总计划数，按超额学习递减
            val extraDone = if (planCompleted) (todayDone - totalTarget).coerceAtLeast(0) else 0
            val extraRemaining = if (planCompleted) (totalTarget - extraDone % totalTarget).let { if (it == totalTarget && extraDone > 0) totalTarget else it } else remaining
            val displayCount = if (planCompleted) (totalTarget - (todayDone - totalTarget) % totalTarget).let { if (it == totalTarget) totalTarget else it } else remaining
            if (totalTarget > 0) {
                FloatingActionButton(
                    onClick = {
                        val allCandidates = mutableListOf<Question>()
                        vm.questionBanks.forEach { bank ->
                            val target = vm.dailyTargets[bank.id] ?: 0
                            if (target > 0) {
                                val bankQuestions = vm.getQuestionsForBank(bank.id)
                                val selected = vm.selectBySpacedRepetition(bankQuestions, target)
                                allCandidates.addAll(selected)
                            }
                        }
                        if (allCandidates.isNotEmpty()) {
                            vm.startStudySessionWithQuestions(allCandidates.map { it.id })
                            showGoStudySession = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 16.dp)
                        .size(56.dp)
                        .shadow(8.dp, shape = CircleShape),
                    containerColor = if (planCompleted) Color(0xFF4CAF50) else Color(0xFFFF8A00),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Go", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${displayCount}题", fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // 全局 Loading
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(loadingMsg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    // 全屏页面栈（覆盖在 Scaffold 之上，不创建新 Window）
    RenderFullScreenPages()
    } // end outer Box

    // 选择图片来源弹窗（底部弹出，iOS风格）
    if (showImageSourceDialog) {
        Dialog(onDismissRequest = { showImageSourceDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImageSourceDialog = false
                                    cameraPermission.launch(android.Manifest.permission.CAMERA)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFEF6C00))
                            Spacer(Modifier.width(12.dp))
                            Text("拍照", fontSize = 16.sp)
                        }
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImageSourceDialog = false
                                    galleryLauncher.launch("image/*")
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFFEF6C00))
                            Spacer(Modifier.width(12.dp))
                            Text("从相册选择", fontSize = 16.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().clickable { showImageSourceDialog = false }
                ) {
                    Text("取消", fontSize = 16.sp, color = Color(0xFFEF6C00),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 多张拍照预览（继续拍照 or 开始识别）
    if (showMultiPhotoPreview && capturedPhotos.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMultiPhotoPreview = false; capturedPhotos = emptyList() },
            title = { Text("已拍 ${capturedPhotos.size} 张", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("题目跨页时可继续拍照，所有照片将拼接后一起识别", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    // 缩略图预览
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        capturedPhotos.takeLast(3).forEach { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showMultiPhotoPreview = false
                    // 拼接所有照片
                    val totalWidth = capturedPhotos.maxOf { it.width }
                    val totalHeight = capturedPhotos.sumOf { it.height }
                    val merged = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(merged)
                    var y = 0f
                    capturedPhotos.forEach { bmp ->
                        canvas.drawBitmap(bmp, (totalWidth - bmp.width) / 2f, y, null)
                        y += bmp.height
                    }
                    capturedBitmap = merged
                    capturedPhotos = emptyList()
                    showImportDialog = true
                }) { Text("开始识别") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showMultiPhotoPreview = false; capturedPhotos = emptyList() }) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        showMultiPhotoPreview = false
                        cameraLauncher.launch(photoUri)
                    }) { Text("继续拍照 +") }
                }
            }
        )
    }

    // 拍照导入弹窗
    if (showImportDialog && capturedBitmap != null) {
        PhotoImportDialog(
            vm = vm,
            bitmap = capturedBitmap!!,
            onDismiss = {
                showImportDialog = false
                capturedBitmap = null
            }
        )
    }

    // 外部分享的文档导入
    if (showSharedFileImport && sharedFileUri != null) {
        FileImportDialog(
            vm = vm,
            uri = sharedFileUri,
            onDismiss = { showSharedFileImport = false }
        )
    }

    // Go 学习会话
    if (showGoStudySession) {
        FullScreenDialog(onDismissRequest = { showGoStudySession = false }) {
            StudySessionPage(vm = vm, onFinish = { showGoStudySession = false })
        }
    }
}

@Composable
fun BottomNavBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.shadow(12.dp)
    ) {
        // 首页
        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
            label = { Text("首页", fontSize = 11.sp, fontWeight = if (currentTab == AppTab.HOME) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
        // 题库
        NavigationBarItem(
            selected = currentTab == AppTab.DISCOVER,
            onClick = { onTabSelected(AppTab.DISCOVER) },
            icon = { Icon(Icons.Default.Explore, contentDescription = "题库") },
            label = { Text("题库", fontSize = 11.sp, fontWeight = if (currentTab == AppTab.DISCOVER) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
        // 中间拍照按钮（突出样式）
        NavigationBarItem(
            selected = false,
            onClick = { onTabSelected(AppTab.CAMERA) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Color(0xFFFF8A00), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            label = { },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        // 学习
        NavigationBarItem(
            selected = currentTab == AppTab.PRACTICE,
            onClick = { onTabSelected(AppTab.PRACTICE) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "学习") },
            label = { Text("学习", fontSize = 11.sp, fontWeight = if (currentTab == AppTab.PRACTICE) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
        // 我的
        NavigationBarItem(
            selected = currentTab == AppTab.PROFILE,
            onClick = { onTabSelected(AppTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
            label = { Text("我的", fontSize = 11.sp, fontWeight = if (currentTab == AppTab.PROFILE) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
    }
}
