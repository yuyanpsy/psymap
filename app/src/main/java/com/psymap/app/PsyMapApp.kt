package com.psymap.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

enum class AppTab(val label: String) {
    HOME("首页"),
    DISCOVER("题库"),
    CAMERA("拍照"),
    PRACTICE("学习"),
    PROFILE("我的")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsyMapApp(vm: PsyMapViewModel = viewModel()) {
    val isLoading by vm.isLoading.collectAsState()
    val loadingMsg by vm.loadingMessage.collectAsState()
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showImportDialog by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // 全分辨率拍照
    val photoFile = remember {
        File(context.cacheDir, "psymap_photo.jpg").apply { if (!exists()) createNewFile() }
    }
    val photoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            try {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bitmap != null) {
                    capturedBitmap = bitmap
                    showImportDialog = true
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

    // 从相册选择图片
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    capturedBitmap = bitmap
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

    Scaffold(
        bottomBar = {
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

    // 选择图片来源弹窗
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("选择图片来源") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            cameraPermission.launch(android.Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("拍照", fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("从相册选择", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) { Text("取消") }
            }
        )
    }

    // 全局监听导入结果，显示 Toast
    LaunchedEffect(vm.importResultMessage) {
        if (vm.importResultMessage.isNotBlank()) {
            Toast.makeText(context, vm.importResultMessage, Toast.LENGTH_LONG).show()
        }
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
}

@Composable
fun BottomNavBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        // 首页
        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
            label = { Text("首页", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
        // 发现
        NavigationBarItem(
            selected = currentTab == AppTab.DISCOVER,
            onClick = { onTabSelected(AppTab.DISCOVER) },
            icon = { Icon(Icons.Default.Explore, contentDescription = "题库") },
            label = { Text("题库", fontSize = 11.sp) },
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
                        .shadow(4.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
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
        // 练习
        NavigationBarItem(
            selected = currentTab == AppTab.PRACTICE,
            onClick = { onTabSelected(AppTab.PRACTICE) },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = "学习") },
            label = { Text("学习", fontSize = 11.sp) },
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
            label = { Text("我的", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = Color.Transparent
            )
        )
    }
}
