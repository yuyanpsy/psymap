package com.psymap.app.literature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psymap.app.FullScreenDialog
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderPage(vm: LiteratureViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lit = vm.selectedLiterature ?: return
    var pageText by remember { mutableStateOf("") }
    var showTextView by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 加载PDF
    LaunchedEffect(lit) { vm.openPdf(context, lit) }

    // 提取当前页文本
    LaunchedEffect(vm.currentPdfPage) {
        pageText = withContext(Dispatchers.IO) {
            try {
                val file = File(lit.pdfPath)
                if (!file.exists()) return@withContext ""
                val doc = PDDocument.load(file)
                val stripper = PDFTextStripper().apply { startPage = vm.currentPdfPage + 1; endPage = vm.currentPdfPage + 1 }
                val text = stripper.getText(doc)
                doc.close()
                text
            } catch (_: Exception) { "" }
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 4f)
        offset = Offset(offset.x + panChange.x, offset.y + panChange.y)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lit.title, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                        Text("第 ${vm.currentPdfPage + 1} / ${vm.currentPdfTotalPages} 页", fontSize = 11.sp, color = Color.Gray)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                actions = {
                    // 文本/图片切换
                    IconButton(onClick = { showTextView = !showTextView }) {
                        Icon(if (showTextView) Icons.Default.Image else Icons.Default.TextFields, contentDescription = "切换视图", tint = Color(0xFFEF6C00))
                    }
                    // 标注笔记
                    IconButton(onClick = { showAnnotations = true }) {
                        Icon(Icons.Default.EditNote, contentDescription = "标注笔记", tint = Color(0xFFEF6C00))
                    }
                    // 复制当前页
                    IconButton(onClick = {
                        if (pageText.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PDF文本", pageText))
                            Toast.makeText(context, "已复制当前页文本", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "复制") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFF333333))
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.goToPage(context, vm.currentPdfPage - 1) }, enabled = vm.currentPdfPage > 0) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一页")
                    }
                    if (vm.currentPdfTotalPages > 1) {
                        Slider(
                            value = vm.currentPdfPage.toFloat(),
                            onValueChange = { vm.goToPage(context, it.toInt()) },
                            valueRange = 0f..(vm.currentPdfTotalPages - 1).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFEF6C00), activeTrackColor = Color(0xFFEF6C00))
                        )
                    }
                    IconButton(onClick = { vm.goToPage(context, vm.currentPdfPage + 1) }, enabled = vm.currentPdfPage < vm.currentPdfTotalPages - 1) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一页")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
            if (vm.isLoading) {
                CircularProgressIndicator(color = Color(0xFFEF6C00))
            } else if (showTextView) {
                // 文本视图（可选中复制）
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(pageText, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            } else {
                // PDF 渲染视图
                if (vm.currentPdfPages.isNotEmpty()) {
                    Image(
                        bitmap = vm.currentPdfPages.first().asImageBitmap(),
                        contentDescription = "PDF页面",
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                            .transformable(state = transformState)
                            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1.5f) 1f else 2.5f; offset = Offset.Zero }) },
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("无法渲染PDF", color = Color.Gray)
                }
            }
        }
    }

    // 标注笔记（全屏）
    if (showAnnotations) {
        FullScreenDialog(onDismissRequest = { showAnnotations = false }) {
            AnnotationFullPage(vm = vm, lit = lit, pageText = pageText, onBack = { showAnnotations = false })
        }
    }
}

// ==================== 全屏标注笔记页 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationFullPage(vm: LiteratureViewModel, lit: Literature, pageText: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedSentence by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    val annotations = vm.getAnnotationsForLiterature(lit.id).sortedByDescending { it.createdAt }

    // 将页面文本拆分为句子
    val sentences = remember(pageText) {
        pageText.split(Regex("""(?<=[.。!！?？;；\n])\s*"""))
            .filter { it.isNotBlank() && it.length > 5 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("标注 & 笔记", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 添加笔记区域
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (selectedSentence.isNotBlank()) {
                        Text("选中文本:", fontSize = 11.sp, color = Color(0xFF888888))
                        Text("\"${selectedSentence}\"", fontSize = 13.sp, color = Color(0xFF333333), maxLines = 3,
                            modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Medium)
                        TextButton(onClick = { selectedSentence = "" }, contentPadding = PaddingValues(0.dp)) {
                            Text("清除选中", fontSize = 11.sp, color = Color(0xFFEF6C00))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = noteText, onValueChange = { noteText = it },
                            placeholder = { Text("输入笔记内容...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 3
                        )
                        Button(
                            onClick = {
                                if (noteText.isNotBlank()) {
                                    vm.addAnnotation(PdfAnnotation(
                                        literatureId = lit.id,
                                        page = vm.currentPdfPage,
                                        type = if (selectedSentence.isNotBlank()) AnnotationType.HIGHLIGHT else AnnotationType.NOTE,
                                        text = selectedSentence,
                                        comment = noteText
                                    ))
                                    noteText = ""; selectedSentence = ""
                                    Toast.makeText(context, "已添加笔记", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = noteText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                            shape = RoundedCornerShape(8.dp)
                        ) { Icon(Icons.Default.Send, contentDescription = "添加", modifier = Modifier.size(18.dp)) }
                    }
                }
            }

            // 句子选择区域
            if (sentences.isNotEmpty()) {
                Text("点击句子添加精准标注 (第${vm.currentPdfPage + 1}页)", fontSize = 12.sp, color = Color(0xFF888888),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                LazyColumn(modifier = Modifier.weight(0.4f).padding(horizontal = 16.dp)) {
                    items(sentences) { sentence ->
                        val isSelected = sentence == selectedSentence
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable { selectedSentence = if (isSelected) "" else sentence },
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFFFF3E0) else Color(0xFFFAFAFA)
                            ),
                            elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
                        ) {
                            Text(
                                sentence.trim(), fontSize = 12.sp, modifier = Modifier.padding(8.dp),
                                color = if (isSelected) Color(0xFFEF6C00) else Color(0xFF444444),
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 已有笔记列表
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFFF0F0F0))
            Text("已有笔记 (${annotations.size})", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            LazyColumn(modifier = Modifier.weight(0.6f)) {
                items(annotations, key = { it.id }) { anno ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                            .clickable {
                                // 点击笔记定位到对应页面
                                if (anno.page != vm.currentPdfPage) {
                                    vm.goToPage(context, anno.page)
                                    Toast.makeText(context, "已跳转到第${anno.page + 1}页", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // 引用的原文
                            if (anno.text.isNotBlank()) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(modifier = Modifier.width(3.dp).height(40.dp).background(Color(0xFFEF6C00), RoundedCornerShape(2.dp)))
                                    Spacer(Modifier.width(8.dp))
                                    Text("\"${anno.text}\"", fontSize = 12.sp, color = Color(0xFF666666), maxLines = 3,
                                        overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            // 笔记内容
                            Text(anno.comment, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                            Spacer(Modifier.height(4.dp))
                            // 元信息
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("第${anno.page + 1}页 · ${anno.type.name}", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                                IconButton(onClick = { vm.deleteAnnotation(anno.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(14.dp), tint = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }
                if (annotations.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("暂无笔记，选择句子或直接输入添加", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
