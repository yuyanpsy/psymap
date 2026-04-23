package com.psymap.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// ==================== 数据模型 ====================
data class MindMapItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    val fileType: String = "", // "image", "pdf", "mm"
    val localFileName: String = "", // 存储在 app 内部目录的文件名
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== 持久化 ====================
object MindMapStore {
    private const val PREFS_KEY = "mindmap_items"
    private val gson = Gson()

    fun getItems(context: Context): List<MindMapItem> {
        val prefs = context.getSharedPreferences("psymap_mindmap", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, "[]") ?: "[]"
        return try { gson.fromJson(json, object : TypeToken<List<MindMapItem>>() {}.type) ?: emptyList() }
        catch (e: Exception) { emptyList() }
    }

    fun saveItems(context: Context, items: List<MindMapItem>) {
        context.getSharedPreferences("psymap_mindmap", Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, gson.toJson(items)).apply()
    }

    fun addItem(context: Context, item: MindMapItem) {
        val items = getItems(context).toMutableList()
        items.add(0, item)
        saveItems(context, items)
    }

    fun deleteItem(context: Context, id: String) {
        val items = getItems(context).toMutableList()
        val item = items.find { it.id == id }
        if (item != null) {
            // 删除本地文件
            File(context.filesDir, "mindmaps/${item.localFileName}").delete()
            items.removeAll { it.id == id }
            saveItems(context, items)
        }
    }

    fun getMindMapDir(context: Context): File {
        val dir = File(context.filesDir, "mindmaps")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

// ==================== 列表页（一级） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMapPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(MindMapStore.getItems(context)) }
    var viewingItem by remember { mutableStateOf<MindMapItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var pendingType by remember { mutableStateOf("") }

    // 文件选择器
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: ""
        val rawName = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "file"

        val type = when {
            rawName.endsWith(".mm") -> "mm"
            mimeType == "application/pdf" || rawName.endsWith(".pdf") -> "pdf"
            mimeType.startsWith("image/") || rawName.matches(Regex(".*\\.(png|jpg|jpeg|svg|webp)$", RegexOption.IGNORE_CASE)) -> "image"
            else -> { Toast.makeText(context, "不支持的格式", Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
        }
        pendingUri = uri
        pendingName = rawName.substringBeforeLast(".")
        pendingType = type
        showRenameDialog = true
    }

    // 保存导入的文件
    fun saveImportedFile(uri: Uri, name: String, type: String) {
        try {
            val ext = when (type) { "mm" -> "mm"; "pdf" -> "pdf"; else -> "png" }
            val localName = "${UUID.randomUUID()}.$ext"
            val destFile = File(MindMapStore.getMindMapDir(context), localName)

            if (type == "pdf") {
                // PDF: 渲染为图片保存
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bmp = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    destFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                renderer.close(); pfd.close()
            } else {
                // 直接复制文件
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val item = MindMapItem(name = name, fileType = if (type == "pdf") "image" else type, localFileName = localName)
            MindMapStore.addItem(context, item)
            items = MindMapStore.getItems(context)
            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 命名对话框
    if (showRenameDialog && pendingUri != null) {
        var editName by remember(pendingName) { mutableStateOf(pendingName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("保存思维导图") },
            text = {
                OutlinedTextField(value = editName, onValueChange = { editName = it },
                    label = { Text("文件名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    showRenameDialog = false
                    saveImportedFile(pendingUri!!, editName.ifBlank { pendingName }, pendingType)
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } }
        )
    }

    // 查看器
    if (viewingItem != null) {
        MindMapViewer(item = viewingItem!!, onBack = { viewingItem = null })
        return
    }

    // 列表 UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("思维导图") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    TextButton(onClick = {
                        filePicker.launch(arrayOf("image/*", "application/pdf", "application/x-freemind", "application/octet-stream", "*/*"))
                    }) { Text("导入", color = Color(0xFFEF6C00)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00), navigationIconContentColor = Color(0xFF333333), actionIconContentColor = Color(0xFF333333))
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧠", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("暂无思维导图", fontSize = 16.sp, color = Color.Gray)
                    Text("点击右上角「导入」添加文件", fontSize = 13.sp, color = Color.LightGray)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        filePicker.launch(arrayOf("image/*", "application/pdf", "application/x-freemind", "application/octet-stream", "*/*"))
                    }) { Text("导入文件") }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewingItem = item },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (item.fileType) { "mm" -> Icons.Default.AccountTree; "pdf" -> Icons.Default.PictureAsPdf; else -> Icons.Default.Image },
                                contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${when (item.fileType) { "mm" -> "FreeMind"; "pdf" -> "PDF"; else -> "图片" }} · ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.CHINA).format(item.createdAt)}",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                MindMapStore.deleteItem(context, item.id)
                                items = MindMapStore.getItems(context)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Default.Delete, "删除", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 查看器（二级） ====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MindMapViewer(item: MindMapItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isMm = item.fileType == "mm"
    val canExport = isMm

    // 模式：true=大纲笔记, false=思维导图
    var isOutlineMode by remember { mutableStateOf(true) }

    // .mm 编辑状态
    var mmNodes by remember { mutableStateOf<List<MmNode>>(emptyList()) }
    var mmRawXml by remember { mutableStateOf("") }
    var editingNodeIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    var hasChanges by remember { mutableStateOf(false) }

    val file = File(MindMapStore.getMindMapDir(context), item.localFileName)

    val bitmap = remember(item.id) {
        if (item.fileType == "image" && file.exists()) android.graphics.BitmapFactory.decodeFile(file.absolutePath) else null
    }
    LaunchedEffect(item.id) {
        if (isMm && file.exists()) { mmRawXml = file.readText(); mmNodes = parseMmXml(mmRawXml) }
    }

    fun saveEditedNode(index: Int, newText: String) {
        if (index < 0 || index >= mmNodes.size) return
        val oldText = mmNodes[index].text
        if (oldText == newText) return
        mmNodes = mmNodes.toMutableList().also { it[index] = it[index].copy(text = newText) }
        val escaped = newText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val oldEscaped = oldText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        mmRawXml = mmRawXml.replaceFirst("TEXT=\"$oldEscaped\"", "TEXT=\"$escaped\"")
        file.writeText(mmRawXml)
        hasChanges = true
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-freemind")) { uri: Uri? ->
        if (uri == null || !isMm) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(file.readBytes()) }
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) Toast.makeText(context, "已自动保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    if (isMm && mmNodes.isNotEmpty()) {
                        // 模式切换按钮（带文字标签）
                        TextButton(onClick = {
                            isOutlineMode = !isOutlineMode
                            scale = 1f; offsetX = 0f; offsetY = 0f; editingNodeIndex = -1
                        }) {
                            Icon(
                                if (isOutlineMode) Icons.Default.AccountTree else Icons.Default.FormatListBulleted,
                                contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isOutlineMode) "导图" else "大纲", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    TextButton(
                        onClick = { if (canExport) exportLauncher.launch("${item.name}.mm") },
                        enabled = canExport
                    ) { Text("导出", color = if (canExport) Color(0xFFEF6C00) else Color.Gray) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00), navigationIconContentColor = Color(0xFF333333), actionIconContentColor = Color(0xFF333333))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = item.name,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceAtLeast(0.3f); offsetX += pan.x; offsetY += pan.y } }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                        contentScale = ContentScale.Fit
                    )
                }
                isMm && mmNodes.isNotEmpty() && isOutlineMode -> {
                    // 大纲笔记模式
                    MmOutlineView(mmNodes, editingNodeIndex, editText,
                        onStartEdit = { idx -> editingNodeIndex = idx; editText = mmNodes[idx].text },
                        onEditChange = { editText = it },
                        onSave = { idx, text -> saveEditedNode(idx, text); editingNodeIndex = -1; Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() },
                        onCancel = { editingNodeIndex = -1 }
                    )
                }
                isMm && mmNodes.isNotEmpty() && !isOutlineMode -> {
                    // 思维导图模式（可缩放 + 可编辑）
                    MmMindMapView(mmNodes, scale, offsetX, offsetY, editingNodeIndex, editText,
                        onTransform = { s, ox, oy -> scale = s; offsetX = ox; offsetY = oy },
                        onStartEdit = { idx -> editingNodeIndex = idx; editText = mmNodes[idx].text },
                        onEditChange = { editText = it },
                        onSave = { idx, text -> saveEditedNode(idx, text); editingNodeIndex = -1; Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() },
                        onCancel = { editingNodeIndex = -1 }
                    )
                }
                else -> { Text("无法加载文件", color = Color.Gray) }
            }
        }
    }
}

// ==================== 大纲笔记模式 ====================
@Composable
fun MmOutlineView(
    nodes: List<MmNode>, editingIndex: Int, editText: String,
    onStartEdit: (Int) -> Unit, onEditChange: (String) -> Unit,
    onSave: (Int, String) -> Unit, onCancel: () -> Unit
) {
    val nodeColors = listOf(Color(0xFFEF6C00), Color(0xFF1976D2), Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFFD32F2F), Color(0xFF00796B))

    androidx.compose.foundation.text.selection.SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(nodes.size) { index ->
                val node = nodes[index]
                val isEditing = editingIndex == index
                val color = nodeColors[node.depth.coerceIn(0, nodeColors.size - 1)]
                val isSection = node.depth <= 1
                val prefix = when (node.depth) { 0 -> "🧠"; 1 -> "📌"; 2 -> "▸"; 3 -> "•"; else -> "◦" }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(start = (node.depth * 12).dp, top = if (isSection) 6.dp else 1.dp, bottom = 1.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = if (node.depth == 0) Color(0xFFFFF3E0) else Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSection) 1.dp else 0.dp)
                ) {
                    if (isEditing) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = editText, onValueChange = onEditChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = when (node.depth) { 0 -> 16.sp; 1 -> 14.sp; else -> 13.sp }, color = color)
                            )
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onCancel) { Text("取消", fontSize = 13.sp) }
                                Spacer(Modifier.width(4.dp))
                                Button(onClick = { onSave(index, editText) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("保存", fontSize = 13.sp) }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { onStartEdit(index) }.padding(10.dp)
                        ) {
                            Text(
                                text = "$prefix ${node.text}",
                                fontSize = when (node.depth) { 0 -> 16.sp; 1 -> 14.sp; else -> 13.sp },
                                fontWeight = if (node.depth <= 1) FontWeight.Bold else FontWeight.Normal,
                                color = color, lineHeight = 20.sp
                            )
                            if (node.richHtml.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                // 用 WebView 渲染 richcontent（含图片）
                                val richHtmlContent = "<html><body style='margin:0;padding:0;font-size:13px;line-height:1.5;color:${String.format("#%06X", 0xFFFFFF and color.hashCode())}'>${node.richHtml}</body></html>"
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        android.webkit.WebView(ctx).apply {
                                            settings.javaScriptEnabled = false
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            loadDataWithBaseURL(null, richHtmlContent, "text/html", "UTF-8", null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp, max = 300.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 思维导图模式（Canvas 树形布局） ====================
@Composable
fun MmMindMapView(
    nodes: List<MmNode>, scale: Float, offsetX: Float, offsetY: Float,
    editingIndex: Int, editText: String,
    onTransform: (Float, Float, Float) -> Unit,
    onStartEdit: (Int) -> Unit, onEditChange: (String) -> Unit,
    onSave: (Int, String) -> Unit, onCancel: () -> Unit
) {
    val context = LocalContext.current

    // 生成 HTML 思维导图
    val html = remember(nodes) { generateMindMapHtml(nodes) }

    Box(modifier = Modifier.fillMaxSize()) {
        android.webkit.WebView(context).let { webView ->
            webView.settings.javaScriptEnabled = true
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false
            webView.settings.setSupportZoom(true)
            webView.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

            // JS 接口：点击节点时回调
            webView.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onNodeClick(index: Int) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onStartEdit(index) }
                }
            }, "Android")

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { webView.apply { loadDataWithBaseURL(null, html, "text/html", "UTF-8", null) } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 编辑覆盖层
        if (editingIndex >= 0 && editingIndex < nodes.size) {
            val node = nodes[editingIndex]
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("编辑节点", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = editText, onValueChange = onEditChange,
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 10,
                            label = { Text("节点文字") })
                        if (node.richHtml.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("富文本内容（含图片）", fontSize = 12.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            val richHtmlContent = "<html><body style='margin:0;padding:8px;font-size:13px;line-height:1.5;'>${node.richHtml}</body></html>"
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = false
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
                                        loadDataWithBaseURL(null, richHtmlContent, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 250.dp)
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onCancel) { Text("取消") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onSave(editingIndex, editText) }) { Text("保存") }
                        }
                    }
                }
            }
        }
    }
}

/** 生成思维导图 HTML（SVG 树形布局，自适应节点高度，支持图文混排） */
private fun generateMindMapHtml(nodes: List<MmNode>): String {
    val colors = listOf("#4A90D9", "#5BA85B", "#D97B4A", "#9B59B6", "#E74C3C", "#1ABC9C")

    fun buildJsonTree(flatNodes: List<MmNode>): String {
        if (flatNodes.isEmpty()) return "{}"
        data class TreeBuilder(val text: String, val depth: Int, val idx: Int, val richHtml: String, val children: MutableList<TreeBuilder> = mutableListOf())
        val rootStack = mutableListOf<TreeBuilder>()
        var root: TreeBuilder? = null

        flatNodes.forEachIndexed { idx, n ->
            val tb = TreeBuilder(n.text, n.depth, idx, n.richHtml)
            while (rootStack.size > n.depth) rootStack.removeAt(rootStack.size - 1)
            if (rootStack.isNotEmpty()) rootStack.last().children.add(tb)
            else root = tb
            rootStack.add(tb)
        }

        fun toJson(node: TreeBuilder): String {
            val escaped = node.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val richEscaped = node.richHtml.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val childrenJson = node.children.joinToString(",") { toJson(it) }
            return """{"t":"$escaped","i":${node.idx},"r":"$richEscaped","c":[$childrenJson]}"""
        }
        return root?.let { toJson(it) } ?: "{}"
    }

    val treeJson = buildJsonTree(nodes)
    val colorsJs = colors.joinToString(",") { "\"$it\"" }

    return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=0.4,user-scalable=yes">
<style>
body{margin:0;background:#F5F5F5;overflow:auto;font-family:sans-serif}
.mm-container{position:relative;display:inline-block;min-width:100vw;min-height:100vh}
.node-box{position:absolute;border-radius:6px;border:1.5px solid #ccc;background:#fff;padding:8px 10px;
  cursor:pointer;box-sizing:border-box;word-wrap:break-word;overflow-wrap:break-word;
  font-size:13px;line-height:1.5}
.node-box.root{background:#FFF3E0;font-size:15px;font-weight:bold}
.node-box.section{font-weight:bold}
.node-box img{max-width:100%;height:auto;display:block;margin:4px 0;border-radius:4px}
.node-box .rich-content{margin-top:4px}
.node-box .rich-content p{margin:2px 0}
svg{position:absolute;top:0;left:0;pointer-events:none}
.link{fill:none;stroke-width:2}
</style></head><body>
<div class="mm-container" id="container">
<svg id="svg"></svg>
</div>
<script>
var root=$treeJson;
var colors=[$colorsJs];
var GY=8,GX=30,PX=10,PY=8,MAX_W=280,MIN_W=60;
var container=document.getElementById('container');
var svg=document.getElementById('svg');

function getColor(d,bi){return d==0?'#EF6C00':colors[bi%colors.length];}

// 创建隐藏的测量 div
var measureDiv=document.createElement('div');
measureDiv.style.cssText='position:absolute;visibility:hidden;width:'+MAX_W+'px;padding:'+PY+'px '+PX+'px;box-sizing:border-box;font-family:sans-serif;line-height:1.5;word-wrap:break-word;overflow-wrap:break-word;';
document.body.appendChild(measureDiv);

function measureNode(n,d){
    measureDiv.style.fontSize=(d==0?'15':'13')+'px';
    measureDiv.style.fontWeight=(d<=1?'bold':'normal');
    var html=n.t||'';
    if(n.r)html+='<div class="rich-content">'+n.r+'</div>';
    measureDiv.innerHTML=html;
    var w=Math.max(Math.min(measureDiv.scrollWidth+PX*2+4,MAX_W),MIN_W);
    measureDiv.style.width=w+'px';
    var h=measureDiv.scrollHeight+4;
    n.w=w;n.h=Math.max(h,28);n.d=d;
    (n.c||[]).forEach(function(c){measureNode(c,d+1)});
}

function layout(n,x,y){
    if(!n.c||n.c.length==0){n.x=x;n.y=y;return y+n.h+GY;}
    var cy=y;
    n.c.forEach(function(c){cy=layout(c,x+n.w+GX,cy);});
    n.x=x;
    var firstY=n.c[0].y+n.c[0].h/2;
    var lastY=n.c[n.c.length-1].y+n.c[n.c.length-1].h/2;
    n.y=(firstY+lastY)/2-n.h/2;
    if(n.y<y)n.y=y;
    return Math.max(cy,n.y+n.h+GY);
}

function render(n,bi){
    var div=document.createElement('div');
    div.className='node-box'+(n.d==0?' root':(n.d==1?' section':''));
    div.style.left=n.x+'px';div.style.top=n.y+'px';
    div.style.width=n.w+'px';
    div.style.borderColor=getColor(n.d,bi);
    div.style.color=getColor(n.d,bi);
    var html=n.t||'';
    if(n.r)html+='<div class="rich-content">'+n.r+'</div>';
    div.innerHTML=html;
    div.onclick=function(){if(window.Android)Android.onNodeClick(n.i);};
    container.appendChild(div);

    (n.c||[]).forEach(function(c,ci){
        var cbi=n.d==0?ci:bi;
        var sx=n.x+n.w,sy=n.y+n.h/2,ex=c.x,ey=c.y+c.h/2;
        var mx=(sx+ex)/2;
        var p=document.createElementNS('http://www.w3.org/2000/svg','path');
        p.setAttribute('d','M'+sx+','+sy+' C'+mx+','+sy+' '+mx+','+ey+' '+ex+','+ey);
        p.setAttribute('class','link');p.setAttribute('stroke',getColor(c.d,cbi));
        svg.appendChild(p);
        render(c,cbi);
    });
}

if(root.t!=null){
    measureNode(root,0);
    var totalH=layout(root,20,20);
    var maxX=0;
    function findMax(n){maxX=Math.max(maxX,n.x+n.w);(n.c||[]).forEach(findMax);}
    findMax(root);
    var W=maxX+40,H=totalH+20;
    container.style.width=W+'px';container.style.height=H+'px';
    svg.setAttribute('width',W);svg.setAttribute('height',H);
    render(root,0);
    measureDiv.remove();
}
</script></body></html>"""
}

// ==================== .mm XML 解析 ====================
data class MmNode(val text: String, val depth: Int, val children: List<MmNode> = emptyList(), val richHtml: String = "")

// 树形结构（用于导图布局）
data class MmTreeNode(
    val text: String,
    val children: MutableList<MmTreeNode> = mutableListOf(),
    var x: Float = 0f, var y: Float = 0f, // 布局后的坐标
    var width: Float = 0f, var height: Float = 0f,
    var flatIndex: Int = -1 // 对应 flatNodes 的索引
)

fun parseMmXml(xml: String): List<MmNode> {
    return try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xml.byteInputStream())
        val root = doc.documentElement
        val rootNode = findDirectChildNodes(root).firstOrNull() ?: return emptyList()
        val result = mutableListOf<MmNode>()
        flattenNode(rootNode, 0, result)
        result
    } catch (e: Exception) { parseMmRegex(xml) }
}

/** 解析为树形结构（导图模式用） */
fun parseMmTree(xml: String): MmTreeNode? {
    return try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val rootEl = findDirectChildNodes(doc.documentElement).firstOrNull() ?: return null
        buildTree(rootEl, mutableIntStateHolder())
    } catch (e: Exception) { null }
}

private class mutableIntStateHolder(var value: Int = 0)

private fun buildTree(element: Element, indexCounter: mutableIntStateHolder): MmTreeNode {
    val text = (element.getAttribute("TEXT") ?: "").let {
        it.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    }
    val node = MmTreeNode(text = text, flatIndex = indexCounter.value)
    indexCounter.value++
    findDirectChildNodes(element).forEach { child ->
        node.children.add(buildTree(child, indexCounter))
    }
    return node
}

private fun findDirectChildNodes(element: Element): List<Element> {
    val result = mutableListOf<Element>()
    val children = element.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child is Element && child.tagName == "node") result.add(child)
    }
    return result
}

private fun flattenNode(element: Element, depth: Int, result: MutableList<MmNode>) {
    val text = (element.getAttribute("TEXT") ?: "").let {
        it.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    }
    // 提取 richcontent（HTML 内容，可能包含图片）
    val richHtml = extractRichContent(element)
    if (text.isNotBlank() || richHtml.isNotBlank()) {
        result.add(MmNode(text = text, depth = depth, richHtml = richHtml))
    }
    findDirectChildNodes(element).forEach { child -> flattenNode(child, depth + 1, result) }
}

private fun extractRichContent(element: Element): String {
    val children = element.childNodes
    val sb = StringBuilder()
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child is Element && child.tagName == "richcontent") {
            // 提取 richcontent 内部的 HTML
            val htmlNodes = child.childNodes
            for (j in 0 until htmlNodes.length) {
                val htmlChild = htmlNodes.item(j)
                if (htmlChild is Element) {
                    sb.append(elementToString(htmlChild))
                }
            }
        }
    }
    return sb.toString().trim()
}

private fun elementToString(element: Element): String {
    val sb = StringBuilder()
    val tag = element.tagName.lowercase()
    // 对于 img 标签，保留 src 属性
    if (tag == "img") {
        val src = element.getAttribute("src") ?: ""
        return "<img src=\"$src\" style=\"max-width:100%;height:auto;\" />"
    }
    sb.append("<$tag>")
    val children = element.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child is Element) {
            sb.append(elementToString(child))
        } else if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
            sb.append(child.textContent ?: "")
        }
    }
    sb.append("</$tag>")
    return sb.toString()
}

private fun parseMmRegex(xml: String): List<MmNode> {
    val result = mutableListOf<MmNode>()
    val regex = Regex("""TEXT="([^"]*?)"""", RegexOption.IGNORE_CASE)
    var depth = 0
    for (line in xml.lines()) {
        val trimmed = line.trim()
        val opens = Regex("<node\\b").findAll(trimmed).count()
        val closes = Regex("</node>").findAll(trimmed).count()
        val selfClose = Regex("/>").findAll(trimmed).count()
        if (opens > 0) {
            regex.find(trimmed)?.let {
                result.add(MmNode(text = it.groupValues[1].replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#xa;", "\n"), depth = depth))
            }
            depth += opens - selfClose.coerceAtMost(opens)
        }
        depth = (depth - closes).coerceAtLeast(0)
    }
    return result
}

