package com.psymap.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 文档工具主页 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentToolsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTool by remember { mutableStateOf<DocTool?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文档工具", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFFEF6C00),
                    navigationIconContentColor = Color(0xFF333333)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择工具", fontSize = 14.sp, color = Color.Gray)

            ToolCard(
                icon = Icons.Default.SwapHoriz,
                title = "Word ↔ PDF 转换",
                desc = "Word转PDF / PDF转Word",
                onClick = { currentTool = DocTool.CONVERT }
            )
            ToolCard(
                icon = Icons.Default.Image,
                title = "PDF ↔ 图片",
                desc = "PDF转图片 / 图片转PDF",
                onClick = { currentTool = DocTool.PDF_IMAGE }
            )
            ToolCard(
                icon = Icons.Default.ContentCut,
                title = "文档拆分",
                desc = "按页数、大小或章节拆分PDF/Word",
                onClick = { currentTool = DocTool.SPLIT }
            )
            ToolCard(
                icon = Icons.Default.MergeType,
                title = "文件合并",
                desc = "合并多个PDF、Word或图片",
                onClick = { currentTool = DocTool.MERGE }
            )
            ToolCard(
                icon = Icons.Default.Compress,
                title = "媒体压缩",
                desc = "压缩视频、图片或文档大小",
                onClick = { currentTool = DocTool.COMPRESS }
            )
        }
    }

    // 处理中遮罩
    if (isProcessing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFFEF6C00))
                    Spacer(Modifier.height(16.dp))
                    Text(progressMessage, fontSize = 14.sp)
                }
            }
        }
    }

    // 各工具子页面
    currentTool?.let { tool ->
        when (tool) {
            DocTool.CONVERT -> ConvertToolDialog(
                onDismiss = { currentTool = null },
                onProcess = { uri, mode ->
                    scope.launch {
                        isProcessing = true
                        progressMessage = if (mode == "word2pdf") "正在转换为PDF..." else "正在转换为Word..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.convert(context, uri, mode)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        currentTool = null
                    }
                }
            )
            DocTool.SPLIT -> SplitToolDialog(
                onDismiss = { currentTool = null },
                onProcess = { uri, splitMode, value ->
                    currentTool = null  // 立即关闭对话框
                    scope.launch {
                        isProcessing = true
                        progressMessage = "正在拆分文档（扫描件可能需要1-2分钟）..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.split(context, uri, splitMode, value)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            )
            DocTool.MERGE -> MergeToolDialog(
                onDismiss = { currentTool = null },
                onProcess = { uris ->
                    scope.launch {
                        isProcessing = true
                        progressMessage = "正在合并文件..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.merge(context, uris)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        currentTool = null
                    }
                }
            )
            DocTool.PDF_IMAGE -> PdfImageToolDialog(
                onDismiss = { currentTool = null },
                onProcess = { uri, mode ->
                    scope.launch {
                        isProcessing = true
                        progressMessage = if (mode == "pdf2img") "正在将PDF转为图片..." else "正在将图片转为PDF..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.pdfToImages(context, uri)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        currentTool = null
                    }
                },
                onProcessMultiple = { uris ->
                    scope.launch {
                        isProcessing = true
                        progressMessage = "正在将${uris.size}张图片合并为PDF..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.imagesToPdf(context, uris)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        currentTool = null
                    }
                }
            )
            DocTool.COMPRESS -> MediaCompressDialog(
                onDismiss = { currentTool = null },
                onProcess = { uri, params ->
                    currentTool = null  // 立即关闭对话框
                    scope.launch {
                        isProcessing = true
                        progressMessage = "正在压缩媒体文件..."
                        val result = withContext(Dispatchers.IO) {
                            DocumentProcessor.compressMedia(context, uri, params)
                        }
                        isProcessing = false
                        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

private enum class DocTool { CONVERT, SPLIT, MERGE, PDF_IMAGE, COMPRESS }

@Composable
private fun ToolCard(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(desc, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

// ==================== 转换工具对话框 ====================
@Composable
private fun ConvertToolDialog(onDismiss: () -> Unit, onProcess: (Uri, String) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("word2pdf") }
    val mimeTypes = if (mode == "word2pdf")
        arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")
    else arrayOf("application/pdf")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Word ↔ PDF 转换", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 模式切换 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == "word2pdf") Color.White else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { mode = "word2pdf"; selectedUri = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Word → PDF",
                            fontSize = 14.sp,
                            fontWeight = if (mode == "word2pdf") FontWeight.Medium else FontWeight.Normal,
                            color = if (mode == "word2pdf") Color(0xFFEF6C00) else Color(0xFF666666)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == "pdf2word") Color.White else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { mode = "pdf2word"; selectedUri = null }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PDF → Word",
                            fontSize = 14.sp,
                            fontWeight = if (mode == "pdf2word") FontWeight.Medium else FontWeight.Normal,
                            color = if (mode == "pdf2word") Color(0xFFEF6C00) else Color(0xFF666666)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { picker.launch(mimeTypes) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri != null) "已选择文件 ✓" else if (mode == "word2pdf") "选择Word文件" else "选择PDF文件")
                }

                if (selectedUri != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (mode == "word2pdf") "将转换为PDF格式" else "将提取文字转为Word",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUri?.let { onProcess(it, mode) } },
                enabled = selectedUri != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF6C00),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("开始转换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF999999)) } }
    )
}

// ==================== 拆分工具对话框 ====================
@Composable
private fun SplitToolDialog(onDismiss: () -> Unit, onProcess: (Uri, String, Int) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var splitMode by remember { mutableStateOf("pages") } // pages, chapter, size
    var value by remember { mutableStateOf("5") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文档拆分", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        picker.launch(arrayOf("application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri != null) "已选择文件 ✓" else "选择PDF/Word文件")
                }

                Spacer(Modifier.height(16.dp))
                Text("拆分方式", fontSize = 13.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                // 拆分方式 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("pages" to "按页数", "chapter" to "按章节", "size" to "按大小").forEach { (key, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (splitMode == key) Color.White else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { splitMode = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (splitMode == key) FontWeight.Medium else FontWeight.Normal,
                                color = if (splitMode == key) Color(0xFFEF6C00) else Color(0xFF666666)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() } },
                    label = { Text(when (splitMode) { "pages" -> "每份页数"; "chapter" -> "自动按章节（无需输入）"; else -> "每份大小(MB)" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = splitMode != "chapter",
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUri?.let { onProcess(it, splitMode, value.toIntOrNull() ?: 5) } },
                enabled = selectedUri != null && value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF6C00),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("开始拆分") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF999999)) } }
    )
}

// ==================== 合并工具对话框 ====================
@Composable
private fun MergeToolDialog(onDismiss: () -> Unit, onProcess: (List<Uri>) -> Unit) {
    var selectedUris by remember { mutableStateOf(listOf<Uri>()) }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) selectedUris = selectedUris + uris
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件合并", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("合并结果按列表从上到下排列", fontSize = 12.sp, color = Color(0xFF888888))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        picker.launch(arrayOf("application/pdf", "image/*",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加文件")
                }

                if (selectedUris.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("已选 ${selectedUris.size} 个文件:", fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    selectedUris.forEachIndexed { index, uri ->
                        val fileName = getFileName(context, uri)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .background(Color(0xFFFAFAFA), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF6C00), modifier = Modifier.width(20.dp))
                            Text(fileName, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            // 上移
                            IconButton(onClick = {
                                if (index > 0) {
                                    val list = selectedUris.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    selectedUris = list
                                }
                            }, modifier = Modifier.size(28.dp), enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (index > 0) Color(0xFF666666) else Color(0xFFCCCCCC))
                            }
                            // 下移
                            IconButton(onClick = {
                                if (index < selectedUris.size - 1) {
                                    val list = selectedUris.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    selectedUris = list
                                }
                            }, modifier = Modifier.size(28.dp), enabled = index < selectedUris.size - 1) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (index < selectedUris.size - 1) Color(0xFF666666) else Color(0xFFCCCCCC))
                            }
                            // 删除
                            IconButton(onClick = {
                                selectedUris = selectedUris.toMutableList().also { it.removeAt(index) }
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "删除",
                                    modifier = Modifier.size(14.dp), tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onProcess(selectedUris) },
                enabled = selectedUris.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF6C00),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("开始合并 (${selectedUris.size}个)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF999999)) } }
    )
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "未知文件"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
    } catch (_: Exception) {}
    return name
}

// ==================== 单文件工具对话框 ====================
@Composable
private fun SingleFileToolDialog(title: String, fileTypes: Array<String>, onDismiss: () -> Unit, onProcess: (Uri) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Button(onClick = { picker.launch(fileTypes) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedUri != null) "已选择文件 ✓" else "选择文件")
            }
        },
        confirmButton = {
            Button(onClick = { selectedUri?.let { onProcess(it) } }, enabled = selectedUri != null) {
                Text("开始处理")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== 媒体压缩参数 ====================
data class CompressParams(
    val targetSizeMB: Int = 10,
    val targetWidth: Int = 0,  // 0=保持原始
    val targetHeight: Int = 0,
    val targetFps: Int = 0,    // 0=保持原始，仅视频
    val bgColor: String = ""   // "", "white", "red", "blue" — 证件照背景
)

// ==================== 媒体压缩对话框 ====================
@Composable
private fun MediaCompressDialog(onDismiss: () -> Unit, onProcess: (Uri, CompressParams) -> Unit) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf("") } // video, image, file
    var targetSizeMB by remember { mutableStateOf("10") }
    var targetWidth by remember { mutableStateOf("") }
    var targetHeight by remember { mutableStateOf("") }
    var targetFps by remember { mutableStateOf("") }
    var bgColor by remember { mutableStateOf("") } // "", "white", "red", "blue"
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            val mime = context.contentResolver.getType(uri) ?: ""
            mediaType = when {
                mime.startsWith("video/") -> "video"
                mime.startsWith("image/") -> "image"
                else -> "file"
            }
            // 读取媒体文件信息
            try {
                if (mediaType == "video") {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    if (w != null) targetWidth = w
                    if (h != null) targetHeight = h
                    retriever.release()
                    // 读取帧率：从视频轨道的帧时间戳间隔推算
                    try {
                        val extractor = android.media.MediaExtractor()
                        val fd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (fd != null) {
                            extractor.setDataSource(fd.fileDescriptor)
                            for (i in 0 until extractor.trackCount) {
                                val format = extractor.getTrackFormat(i)
                                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                                if (mime.startsWith("video/")) {
                                    // 方法1：直接读取（CFR 视频有效）
                                    val fps = try { format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0 }
                                    if (fps > 0) {
                                        targetFps = fps.toString()
                                    } else {
                                        // 方法2：采样前30帧的时间戳间隔推算帧率
                                        extractor.selectTrack(i)
                                        val timestamps = mutableListOf<Long>()
                                        var count = 0
                                        while (count < 30) {
                                            val size = extractor.readSampleData(java.nio.ByteBuffer.allocate(1), 0)
                                            if (size < 0) break
                                            timestamps.add(extractor.sampleTime)
                                            extractor.advance()
                                            count++
                                        }
                                        if (timestamps.size >= 2) {
                                            val totalDurationUs = timestamps.last() - timestamps.first()
                                            if (totalDurationUs > 0) {
                                                val calculatedFps = ((timestamps.size - 1) * 1_000_000.0 / totalDurationUs).toInt()
                                                targetFps = calculatedFps.coerceIn(1, 120).toString()
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                            extractor.release()
                            fd.close()
                        }
                    } catch (_: Exception) {}
                    if (targetFps.isBlank()) targetFps = "30"
                } else if (mediaType == "image") {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                    }
                    if (opts.outWidth > 0) targetWidth = opts.outWidth.toString()
                    if (opts.outHeight > 0) targetHeight = opts.outHeight.toString()
                }
                // 读取文件大小
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    val sizeMB = fd.statSize / 1024.0 / 1024.0
                    targetSizeMB = maxOf(1, (sizeMB * 0.5).toInt()).toString() // 默认压缩到50%
                }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("媒体压缩") },
        text = {
            Column {
                Button(onClick = {
                    picker.launch(arrayOf("video/*", "image/*", "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/msword", "application/vnd.ms-excel"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedUri != null) "已选择: ${mediaType}文件 ✓" else "选择视频/图片/文档")
                }

                if (selectedUri != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("压缩参数", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    // 目标大小（所有类型都有）
                    OutlinedTextField(
                        value = targetSizeMB,
                        onValueChange = { targetSizeMB = it.filter { c -> c.isDigit() } },
                        label = { Text("目标大小 (MB)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )

                    // 分辨率（视频和图片）
                    if (mediaType == "video" || mediaType == "image") {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = targetWidth,
                                onValueChange = { targetWidth = it.filter { c -> c.isDigit() } },
                                label = { Text("宽度") },
                                placeholder = { Text("原始") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetHeight,
                                onValueChange = { targetHeight = it.filter { c -> c.isDigit() } },
                                label = { Text("高度") },
                                placeholder = { Text("原始") },
                                singleLine = true, modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 帧率（仅视频，只读显示）
                    if (mediaType == "video") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = if (targetFps.isNotBlank()) "${targetFps} fps（不可调整）" else "未知",
                            onValueChange = {},
                            label = { Text("原始帧率") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            enabled = false
                        )
                    }

                    // 证件照背景（仅图片）
                    if (mediaType == "image") {
                        Spacer(Modifier.height(12.dp))
                        Text("变更证件照背景", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 不变更
                            FilterChip(
                                selected = bgColor == "",
                                onClick = { bgColor = "" },
                                label = { Text("不变更", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            // 白色
                            FilterChip(
                                selected = bgColor == "white",
                                onClick = { bgColor = "white" },
                                leadingIcon = {
                                    Box(Modifier.size(14.dp).background(Color.White, CircleShape)
                                        .border(1.dp, Color(0xFFCCCCCC), CircleShape))
                                },
                                label = { Text("白色", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            // 红色
                            FilterChip(
                                selected = bgColor == "red",
                                onClick = { bgColor = "red" },
                                leadingIcon = {
                                    Box(Modifier.size(14.dp).background(Color.Red, CircleShape))
                                },
                                label = { Text("红色", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            // 浅蓝
                            FilterChip(
                                selected = bgColor == "blue",
                                onClick = { bgColor = "blue" },
                                leadingIcon = {
                                    Box(Modifier.size(14.dp).background(Color(0xFF67B2E6), CircleShape))
                                },
                                label = { Text("浅蓝", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("留空表示保持原始值", fontSize = 11.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedUri?.let {
                        onProcess(it, CompressParams(
                            targetSizeMB = targetSizeMB.toIntOrNull() ?: 10,
                            targetWidth = targetWidth.toIntOrNull() ?: 0,
                            targetHeight = targetHeight.toIntOrNull() ?: 0,
                            targetFps = targetFps.toIntOrNull() ?: 0,
                            bgColor = bgColor
                        ))
                    }
                },
                enabled = selectedUri != null && targetSizeMB.isNotBlank()
            ) { Text("开始压缩") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== PDF ↔ 图片转换 ====================
@Composable
private fun PdfImageToolDialog(
    onDismiss: () -> Unit,
    onProcess: (Uri, String) -> Unit,
    onProcessMultiple: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf("pdf2img") } // pdf2img or img2pdf
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedUris = uris
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF ↔ 图片", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 模式切换 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == "pdf2img") Color.White else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { mode = "pdf2img"; selectedUri = null; selectedUris = emptyList() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PDF → 图片",
                            fontSize = 14.sp,
                            fontWeight = if (mode == "pdf2img") FontWeight.Medium else FontWeight.Normal,
                            color = if (mode == "pdf2img") Color(0xFFEF6C00) else Color(0xFF666666)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (mode == "img2pdf") Color.White else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { mode = "img2pdf"; selectedUri = null; selectedUris = emptyList() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "图片 → PDF",
                            fontSize = 14.sp,
                            fontWeight = if (mode == "img2pdf") FontWeight.Medium else FontWeight.Normal,
                            color = if (mode == "img2pdf") Color(0xFFEF6C00) else Color(0xFF666666)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 文件选择区域
                if (mode == "pdf2img") {
                    Button(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedUri != null) "已选择PDF文件 ✓" else "选择PDF文件")
                    }
                    if (selectedUri != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "每页将导出为一张PNG图片",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                } else {
                    Button(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedUris.isNotEmpty()) "已选择 ${selectedUris.size} 张图片 ✓" else "选择图片（可多选）")
                    }
                    if (selectedUris.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "将按选择顺序合并为一个PDF",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mode == "pdf2img" && selectedUri != null) {
                        onProcess(selectedUri!!, "pdf2img")
                    } else if (mode == "img2pdf" && selectedUris.isNotEmpty()) {
                        onProcessMultiple(selectedUris)
                    }
                },
                enabled = (mode == "pdf2img" && selectedUri != null) || (mode == "img2pdf" && selectedUris.isNotEmpty()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF6C00),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(8.dp)
            ) { Text("开始转换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF999999)) } }
    )
}
