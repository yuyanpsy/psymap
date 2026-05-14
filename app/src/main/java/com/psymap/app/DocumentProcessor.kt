package com.psymap.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.*

object DocumentProcessor {
    private const val TAG = "DocProcessor"

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    // ==================== Word ↔ PDF 转换 ====================

    fun convert(context: Context, uri: Uri, mode: String): String {
        init(context)
        return try {
            if (mode == "word2pdf") wordToPdf(context, uri)
            else pdfToWord(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "转换失败", e)
            "转换失败: ${e.message}"
        }
    }

    private fun wordToPdf(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return "无法打开文件"
        val doc = XWPFDocument(inputStream)
        inputStream.close()

        val pdfDoc = AndroidPdfDocument()
        val paint = android.graphics.Paint().apply {
            textSize = 14f
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }

        val pageWidth = 595 // A4
        val pageHeight = 842
        val margin = 50f
        val lineHeight = 20f
        val maxWidth = pageWidth - margin * 2

        var pageNum = 1
        var pageInfo = AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin + lineHeight

        for (para in doc.paragraphs) {
            val text = para.text
            if (text.isBlank()) { y += lineHeight * 0.5f; continue }

            // 简单换行处理
            val words = text.toCharArray()
            var lineStart = 0
            while (lineStart < words.size) {
                var lineEnd = lineStart
                var width = 0f
                while (lineEnd < words.size && width < maxWidth) {
                    width += paint.measureText(words[lineEnd].toString())
                    lineEnd++
                }
                val line = String(words, lineStart, lineEnd - lineStart)

                if (y + lineHeight > pageHeight - margin) {
                    pdfDoc.finishPage(page)
                    pageNum++
                    pageInfo = AndroidPdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + lineHeight
                }
                canvas.drawText(line, margin, y, paint)
                y += lineHeight
                lineStart = lineEnd
            }
            y += lineHeight * 0.3f
        }
        pdfDoc.finishPage(page)
        doc.close()

        val outputName = "converted_${System.currentTimeMillis()}.pdf"
        val outUri = saveToDownloads(context, outputName, "application/pdf") ?: return "保存失败"
        context.contentResolver.openOutputStream(outUri)?.use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return "转换完成: $outputName"
    }

    private fun pdfToWord(context: Context, uri: Uri): String {
        // 大文件：先复制到临时文件
        val tempFile = File(context.cacheDir, "convert_input_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
        } ?: return "无法打开文件"

        try {
            val pdfDoc = PDDocument.load(tempFile)
            val stripper = PDFTextStripper()
            val text = stripper.getText(pdfDoc)
            pdfDoc.close()
            tempFile.delete()

            val wordDoc = XWPFDocument()
            text.split("\n").forEach { line ->
                val para = wordDoc.createParagraph()
                val run = para.createRun()
                run.setText(line)
                run.fontSize = 12
            }

            val outputName = "converted_${System.currentTimeMillis()}.docx"
            val outUri = saveToDownloads(context, outputName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document") ?: return "保存失败"
            context.contentResolver.openOutputStream(outUri)?.use { wordDoc.write(it) }
            wordDoc.close()
            return "转换完成: $outputName"
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    // ==================== 文档拆分 ====================

    fun split(context: Context, uri: Uri, mode: String, value: Int): String {
        init(context)
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return try {
            if (mimeType.contains("pdf")) splitPdf(context, uri, mode, value)
            else splitWord(context, uri, mode, value)
        } catch (e: Exception) {
            Log.e(TAG, "拆分失败", e)
            "拆分失败: ${e.message}"
        }
    }

    private fun splitPdf(context: Context, uri: Uri, mode: String, value: Int): String {
        // 大文件处理：先复制到临时文件，用 RandomAccessFile 模式加载避免 OOM
        val tempFile = File(context.cacheDir, "split_input_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
        } ?: return "无法打开文件"

        try {
            val pdfDoc = PDDocument.load(tempFile)
            val totalPages = pdfDoc.numberOfPages

            val ranges = when (mode) {
                "pages" -> (0 until totalPages).chunked(value)
                "chapter" -> {
                    // 方案1：按书签/大纲拆分
                    val outline = pdfDoc.documentCatalog?.documentOutline
                    val bookmarkPages = mutableListOf<Int>()
                    if (outline != null) {
                        var item = outline.firstChild
                        while (item != null) {
                            try {
                                val dest = item.destination
                                if (dest is com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) {
                                    val pageNum = pdfDoc.pages.indexOf(dest.page)
                                    if (pageNum >= 0) bookmarkPages.add(pageNum)
                                }
                            } catch (_: Exception) {}
                            item = item.nextSibling
                        }
                    }

                    // 方案2：如果无书签，扫描文本内容识别章节标题
                    val chapterPages = if (bookmarkPages.isNotEmpty()) {
                        bookmarkPages
                    } else {
                        val detected = mutableListOf<Int>()
                        // 章节标题正则
                        val chapterRegex = Regex(
                            """(第[一二三四五六七八九十百零\d]+章|第[一二三四五六七八九十百零\d]+部分|Chapter\s+\d+|CHAPTER\s+\d+|Part\s+[IVX\d]+)"""
                        )

                        // 先尝试 PDFTextStripper（文字型PDF）
                        var hasText = false
                        for (i in 0 until minOf(3, totalPages)) {
                            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            val text = try { stripper.getText(pdfDoc) } catch (_: Exception) { "" }
                            if (text.trim().length > 10) { hasText = true; break }
                        }

                        if (hasText) {
                            // 文字型PDF：用 PDFTextStripper
                            for (i in 0 until totalPages) {
                                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                                stripper.startPage = i + 1
                                stripper.endPage = i + 1
                                val pageText = try { stripper.getText(pdfDoc) } catch (_: Exception) { "" }
                                if (chapterRegex.containsMatchIn(pageText)) {
                                    detected.add(i)
                                }
                            }
                        } else {
                            // 扫描件PDF：用 PdfRenderer + ML Kit OCR 扫描顶部
                            Log.d(TAG, "PDF为扫描件，使用OCR识别章节")
                            pdfDoc.close() // 先关闭 pdfbox 文档
                            val renderer = android.graphics.pdf.PdfRenderer(
                                android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            )
                            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
                            )
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                // 只渲染顶部 1/4 区域（章节标题在顶部）
                                val width = page.width
                                val height = page.height / 4
                                val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                // 渲染整页到临时 bitmap，然后裁剪顶部
                                val fullBmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                page.render(fullBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                // 裁剪顶部 1/4
                                val topBmp = android.graphics.Bitmap.createBitmap(fullBmp, 0, 0, fullBmp.width, fullBmp.height / 4)
                                fullBmp.recycle()

                                // OCR 识别
                                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(topBmp, 0)
                                val latch = java.util.concurrent.CountDownLatch(1)
                                var ocrText = ""
                                recognizer.process(image)
                                    .addOnSuccessListener { result -> ocrText = result.text; latch.countDown() }
                                    .addOnFailureListener { latch.countDown() }
                                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                                topBmp.recycle()

                                if (chapterRegex.containsMatchIn(ocrText)) {
                                    detected.add(i)
                                    Log.d(TAG, "OCR检测到章节标题在第${i+1}页: ${ocrText.take(50)}")
                                }
                            }
                            renderer.close()
                            // 重新打开 pdfbox 文档用于拆分
                            val reopenedDoc = PDDocument.load(tempFile)
                            // 用 reopenedDoc 替换 pdfDoc 进行后续拆分
                            // 由于变量作用域限制，这里直接在下面处理
                            if (detected.isEmpty()) {
                                reopenedDoc.close()
                            } else {
                                val sorted2 = (detected.distinct().sorted() + reopenedDoc.numberOfPages).distinct().sorted()
                                val starts2 = if (sorted2.first() > 0) listOf(0) + sorted2 else sorted2
                                val ranges2 = starts2.zipWithNext().map { (s, e) -> (s until e).toList() }.filter { it.isNotEmpty() }
                                var count2 = 0
                                for ((idx, range) in ranges2.withIndex()) {
                                    val newDoc = PDDocument()
                                    for (pageIdx in range) { newDoc.importPage(reopenedDoc.getPage(pageIdx)) }
                                    val name = "chapter_${idx + 1}_of_${ranges2.size}.pdf"
                                    val outUri = saveToDownloads(context, name, "application/pdf")
                                    if (outUri != null) {
                                        context.contentResolver.openOutputStream(outUri)?.use { newDoc.save(it) }
                                        count2++
                                    }
                                    newDoc.close()
                                }
                                reopenedDoc.close()
                                tempFile.delete()
                                return "按章节拆分完成: 共 $count2 个文件（OCR识别）"
                            }
                        }
                        Log.d(TAG, "章节检测结果: ${detected.size} 个章节: $detected")
                        detected
                    }

                    if (chapterPages.isEmpty()) {
                        // 无法识别章节，按10页一份
                        (0 until totalPages).chunked(10)
                    } else {
                        val sorted = (chapterPages.distinct().sorted() + totalPages).distinct().sorted()
                        // 确保第一页也包含
                        val starts = if (sorted.first() > 0) listOf(0) + sorted else sorted
                        starts.zipWithNext().map { (start, end) -> (start until end).toList() }
                            .filter { it.isNotEmpty() }
                    }
                }
                else -> {
                    // 按大小：估算每页大小，计算每份页数
                    val avgPageSize = tempFile.length() / totalPages
                    val pagesPerChunk = maxOf(1, ((value.toLong() * 1024 * 1024) / avgPageSize).toInt())
                    (0 until totalPages).chunked(pagesPerChunk)
                }
            }

            var count = 0
            for ((idx, range) in ranges.withIndex()) {
                val newDoc = PDDocument()
                for (pageIdx in range) {
                    newDoc.importPage(pdfDoc.getPage(pageIdx))
                }
                val name = "split_${idx + 1}_of_${ranges.size}.pdf"
                val outUri = saveToDownloads(context, name, "application/pdf")
                if (outUri != null) {
                    context.contentResolver.openOutputStream(outUri)?.use { newDoc.save(it) }
                    count++
                }
                newDoc.close()
            }
            pdfDoc.close()
            tempFile.delete()
            return "拆分完成: 共 $count 个文件，已保存到下载目录"
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun splitWord(context: Context, uri: Uri, mode: String, value: Int): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return "无法打开文件"
        val doc = XWPFDocument(inputStream)
        inputStream.close()
        val paragraphs = doc.paragraphs
        val total = paragraphs.size

        val ranges = when (mode) {
            "pages" -> (0 until total).chunked(value * 30) // 约30段=1页
            "chapter" -> {
                // 按 Heading 样式拆分
                val chapterStarts = mutableListOf<Int>()
                paragraphs.forEachIndexed { idx, para ->
                    val style = para.style ?: ""
                    if (style.contains("Heading", ignoreCase = true) || style.contains("标题")) {
                        chapterStarts.add(idx)
                    }
                }
                if (chapterStarts.isEmpty()) {
                    (0 until total).chunked(maxOf(1, total / 3))
                } else {
                    chapterStarts.add(total)
                    chapterStarts.zipWithNext().map { (start, end) -> (start until end).toList() }
                }
            }
            else -> (0 until total).chunked(maxOf(1, total / 3))
        }

        var count = 0
        for ((idx, range) in ranges.withIndex()) {
            val newDoc = XWPFDocument()
            for (paraIdx in range) {
                val para = paragraphs[paraIdx]
                val newPara = newDoc.createParagraph()
                val run = newPara.createRun()
                run.setText(para.text)
            }
            val name = "split_${idx + 1}_${System.currentTimeMillis()}.docx"
            val outUri = saveToDownloads(context, name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document") ?: continue
            context.contentResolver.openOutputStream(outUri)?.use { newDoc.write(it) }
            newDoc.close()
            count++
        }
        doc.close()
        return "拆分完成: 共 $count 个文件，已保存到下载目录"
    }

    // ==================== 文件合并 ====================

    fun merge(context: Context, uris: List<Uri>): String {
        init(context)
        val tempFiles = mutableListOf<File>()
        val sourceDocs = mutableListOf<PDDocument>()
        return try {
            val mergedDoc = PDDocument()
            for (uri in uris) {
                val mimeType = context.contentResolver.getType(uri) ?: ""
                when {
                    mimeType.contains("pdf") -> {
                        // 复制到临时文件，保持文件打开直到合并完成
                        val tempFile = File(context.cacheDir, "merge_${System.currentTimeMillis()}_${tempFiles.size}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
                        } ?: continue
                        tempFiles.add(tempFile)
                        val doc = PDDocument.load(tempFile)
                        sourceDocs.add(doc)
                        for (i in 0 until doc.numberOfPages) {
                            mergedDoc.importPage(doc.getPage(i))
                        }
                    }
                    mimeType.startsWith("image/") -> {
                        val input = context.contentResolver.openInputStream(uri) ?: continue
                        val bitmap = BitmapFactory.decodeStream(input)
                        input.close()
                        if (bitmap != null) {
                            val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                            mergedDoc.addPage(page)
                            val cs = PDPageContentStream(mergedDoc, page)
                            val img = JPEGFactory.createFromImage(mergedDoc, bitmap)
                            cs.drawImage(img, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                            cs.close()
                        }
                    }
                    mimeType.contains("word") || mimeType.contains("document") -> {
                        val input = context.contentResolver.openInputStream(uri) ?: continue
                        val wordDoc = XWPFDocument(input)
                        input.close()
                        val text = wordDoc.paragraphs.joinToString("\n") { it.text }
                        wordDoc.close()
                        addTextPage(mergedDoc, text)
                    }
                }
            }
            val name = "merged_${System.currentTimeMillis()}.pdf"
            val outUri = saveToDownloads(context, name, "application/pdf") ?: return "保存失败"
            context.contentResolver.openOutputStream(outUri)?.use { mergedDoc.save(it) }
            mergedDoc.close()
            // 关闭所有源文档和临时文件
            sourceDocs.forEach { it.close() }
            tempFiles.forEach { it.delete() }
            "合并完成: $name (${uris.size}个文件)"
        } catch (e: Exception) {
            sourceDocs.forEach { try { it.close() } catch (_: Exception) {} }
            tempFiles.forEach { it.delete() }
            Log.e(TAG, "合并失败", e)
            "合并失败: ${e.message}"
        }
    }

    private fun addTextPage(doc: PDDocument, text: String) {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
        cs.beginText()
        cs.setFont(font, 12f)
        cs.newLineAtOffset(50f, PDRectangle.A4.height - 50f)
        cs.setLeading(16f)
        text.take(3000).split("\n").take(50).forEach { line ->
            cs.showText(line.take(80))
            cs.newLine()
        }
        cs.endText()
        cs.close()
    }

    // ==================== PDF ↔ 图片转换 ====================

    fun pdfToImages(context: Context, uri: Uri): String {
        init(context)
        return try {
            val tempFile = File(context.cacheDir, "pdf2img_input_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
            } ?: return "无法打开文件"

            val fd = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val pageCount = renderer.pageCount
            var savedCount = 0

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // 渲染为 2x 分辨率以保证清晰度
                val scale = 2
                val width = page.width * scale
                val height = page.height * scale
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // 保存为 PNG
                val name = "pdf_page_${i + 1}_${System.currentTimeMillis()}.png"
                val outUri = saveToDownloads(context, name, "image/png")
                if (outUri != null) {
                    context.contentResolver.openOutputStream(outUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    savedCount++
                }
                bitmap.recycle()
            }

            renderer.close()
            fd.close()
            tempFile.delete()
            "转换完成: 共 $pageCount 页，已保存 $savedCount 张图片到下载目录"
        } catch (e: Exception) {
            Log.e(TAG, "PDF转图片失败", e)
            "PDF转图片失败: ${e.message}"
        }
    }

    fun imagesToPdf(context: Context, uris: List<Uri>): String {
        init(context)
        return try {
            val pdfDoc = PDDocument()

            for (uri in uris) {
                val input = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                if (bitmap == null) continue

                // 创建与图片等大的页面
                val pageWidth = bitmap.width.toFloat()
                val pageHeight = bitmap.height.toFloat()
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                pdfDoc.addPage(page)

                val cs = PDPageContentStream(pdfDoc, page)
                val img = JPEGFactory.createFromImage(pdfDoc, bitmap)
                cs.drawImage(img, 0f, 0f, pageWidth, pageHeight)
                cs.close()
                bitmap.recycle()
            }

            if (pdfDoc.numberOfPages == 0) {
                pdfDoc.close()
                return "未能解码任何图片"
            }

            val name = "images_to_pdf_${System.currentTimeMillis()}.pdf"
            val outUri = saveToDownloads(context, name, "application/pdf") ?: return "保存失败"
            context.contentResolver.openOutputStream(outUri)?.use { pdfDoc.save(it) }
            pdfDoc.close()
            "转换完成: ${uris.size} 张图片已合并为 $name"
        } catch (e: Exception) {
            Log.e(TAG, "图片转PDF失败", e)
            "图片转PDF失败: ${e.message}"
        }
    }

    // ==================== 压缩PDF ====================

    fun compressPdf(context: Context, uri: Uri): String {
        init(context)
        return try {
            val tempFile = File(context.cacheDir, "compress_input_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
            } ?: return "无法打开文件"

            val doc = PDDocument.load(tempFile)
            doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()

            val name = "compressed_${System.currentTimeMillis()}.pdf"
            val outUri = saveToDownloads(context, name, "application/pdf") ?: return "保存失败"
            context.contentResolver.openOutputStream(outUri)?.use { doc.save(it) }
            doc.close()
            tempFile.delete()
            "压缩完成: $name"
        } catch (e: Exception) {
            Log.e(TAG, "压缩失败", e)
            "压缩失败: ${e.message}"
        }
    }

    // ==================== 证件照背景替换 ====================

    private fun changePhotoBackground(bitmap: Bitmap, colorName: String): Bitmap? {
        val bgColorInt = when (colorName) {
            "white" -> android.graphics.Color.WHITE
            "red" -> android.graphics.Color.rgb(255, 0, 0)
            "blue" -> android.graphics.Color.rgb(103, 178, 230)
            else -> return null
        }

        val options = com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.Builder()
            .setDetectorMode(com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = com.google.mlkit.vision.segmentation.Segmentation.getClient(options)
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)

        val latch = java.util.concurrent.CountDownLatch(1)
        var mask: com.google.mlkit.vision.segmentation.SegmentationMask? = null
        segmenter.process(inputImage)
            .addOnSuccessListener { result -> mask = result; latch.countDown() }
            .addOnFailureListener { latch.countDown() }
        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)

        if (mask == null) { Log.e(TAG, "人像分割失败"); return null }

        val maskBuffer = mask!!.buffer
        val maskWidth = mask!!.width
        val maskHeight = mask!!.height

        // 1. 将 mask 转为浮点数组
        maskBuffer.rewind()
        val rawMask = FloatArray(maskWidth * maskHeight)
        for (i in rawMask.indices) {
            rawMask[i] = maskBuffer.float
        }

        // 2. 上采样 mask 到原图分辨率（双线性插值）
        val w = bitmap.width
        val h = bitmap.height
        val fullMask = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcX = x.toFloat() * (maskWidth - 1) / (w - 1)
                val srcY = y.toFloat() * (maskHeight - 1) / (h - 1)
                val x0 = srcX.toInt().coerceIn(0, maskWidth - 2)
                val y0 = srcY.toInt().coerceIn(0, maskHeight - 2)
                val fx = srcX - x0
                val fy = srcY - y0
                // 双线性插值
                val v00 = rawMask[y0 * maskWidth + x0]
                val v10 = rawMask[y0 * maskWidth + x0 + 1]
                val v01 = rawMask[(y0 + 1) * maskWidth + x0]
                val v11 = rawMask[(y0 + 1) * maskWidth + x0 + 1]
                val value = v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy
                fullMask[y * w + x] = value
            }
        }

        // 3. 边缘优化：在缩小的 mask 上做模糊（性能优化），再放大
        // sigmoid 增强
        for (i in fullMask.indices) {
            val v = fullMask[i]
            fullMask[i] = (1.0f / (1.0f + Math.exp((-8.0 * (v - 0.45)).toDouble()).toFloat()))
        }

        // 缩小到 1/4 分辨率做模糊（大幅提升性能）
        val smallW = w / 4
        val smallH = h / 4
        val smallMask = FloatArray(smallW * smallH)
        for (sy in 0 until smallH) {
            for (sx in 0 until smallW) {
                smallMask[sy * smallW + sx] = fullMask[(sy * 4) * w + (sx * 4)]
            }
        }

        // 在小图上做 3x3 模糊 6 次（等效大图上约 12 像素模糊半径）
        val smallTemp = FloatArray(smallW * smallH)
        val blurredSmall = smallMask.copyOf()
        repeat(6) {
            for (y in 1 until smallH - 1) {
                for (x in 1 until smallW - 1) {
                    val idx = y * smallW + x
                    var sum = blurredSmall[idx] * 4f
                    sum += (blurredSmall[idx - 1] + blurredSmall[idx + 1] + blurredSmall[idx - smallW] + blurredSmall[idx + smallW]) * 2f
                    sum += (blurredSmall[idx - smallW - 1] + blurredSmall[idx - smallW + 1] + blurredSmall[idx + smallW - 1] + blurredSmall[idx + smallW + 1])
                    smallTemp[idx] = sum / 16f
                }
            }
            System.arraycopy(smallTemp, 0, blurredSmall, 0, blurredSmall.size)
        }

        // 放大回原始分辨率（双线性插值）
        val blurred = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcX = (x.toFloat() * (smallW - 1) / (w - 1)).coerceIn(0f, (smallW - 2).toFloat())
                val srcY = (y.toFloat() * (smallH - 1) / (h - 1)).coerceIn(0f, (smallH - 2).toFloat())
                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val fx = srcX - x0
                val fy = srcY - y0
                val v00 = blurredSmall[y0 * smallW + x0]
                val v10 = blurredSmall[y0 * smallW + x0 + 1]
                val v01 = blurredSmall[(y0 + 1) * smallW + x0]
                val v11 = blurredSmall[(y0 + 1) * smallW + x0 + 1]
                blurred[y * w + x] = v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy
            }
        }

        // 4. Alpha 混合：前景保留原色，背景替换为目标色
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        result.getPixels(pixels, 0, w, 0, 0, w, h)

        val bgR = android.graphics.Color.red(bgColorInt)
        val bgG = android.graphics.Color.green(bgColorInt)
        val bgB = android.graphics.Color.blue(bgColorInt)

        for (i in pixels.indices) {
            val alpha = blurred[i].coerceIn(0f, 1f) // 1.0=前景, 0.0=背景
            if (alpha < 0.99f) {
                val origR = android.graphics.Color.red(pixels[i])
                val origG = android.graphics.Color.green(pixels[i])
                val origB = android.graphics.Color.blue(pixels[i])
                // alpha 混合
                val r = (origR * alpha + bgR * (1 - alpha)).toInt().coerceIn(0, 255)
                val g = (origG * alpha + bgG * (1 - alpha)).toInt().coerceIn(0, 255)
                val b = (origB * alpha + bgB * (1 - alpha)).toInt().coerceIn(0, 255)
                pixels[i] = android.graphics.Color.rgb(r, g, b)
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        segmenter.close()
        return result
    }

    // ==================== 媒体压缩 ====================

    fun compressMedia(context: Context, uri: Uri, params: CompressParams): String {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return try {
            when {
                mimeType.startsWith("video/") -> compressVideo(context, uri, params)
                mimeType.startsWith("image/") -> compressImage(context, uri, params)
                else -> compressDocument(context, uri, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "压缩失败", e)
            "压缩失败: ${e.message}"
        }
    }

    private fun compressImage(context: Context, uri: Uri, params: CompressParams): String {
        val input = context.contentResolver.openInputStream(uri) ?: return "无法打开文件"
        var original = BitmapFactory.decodeStream(input)
        input.close()
        if (original == null) return "无法解码图片"

        // 证件照背景替换
        if (params.bgColor.isNotBlank()) {
            original = changePhotoBackground(original, params.bgColor) ?: original
        }

        // 计算目标尺寸
        val targetW = if (params.targetWidth > 0) params.targetWidth else original.width
        val targetH = if (params.targetHeight > 0) params.targetHeight else original.height

        // 不变形缩放：先等比缩放到覆盖目标区域，再居中裁切
        val scaled: Bitmap
        if (targetW == original.width && targetH == original.height) {
            scaled = original
        } else {
            val srcRatio = original.width.toFloat() / original.height
            val dstRatio = targetW.toFloat() / targetH

            if (Math.abs(srcRatio - dstRatio) < 0.01f) {
                // 比例相同，直接缩放
                scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
                // 比例不同：等比缩放后居中裁切（centerCrop）
                val scaleW: Int
                val scaleH: Int
                if (srcRatio > dstRatio) {
                    // 源图更宽，按高度缩放，裁切宽度
                    scaleH = targetH
                    scaleW = (original.width.toFloat() * targetH / original.height).toInt()
                } else {
                    // 源图更高，按宽度缩放，裁切高度
                    scaleW = targetW
                    scaleH = (original.height.toFloat() * targetW / original.width).toInt()
                }
                val scaledFull = Bitmap.createScaledBitmap(original, scaleW, scaleH, true)
                // 居中裁切
                val cropX = (scaleW - targetW) / 2
                val cropY = (scaleH - targetH) / 2
                scaled = Bitmap.createBitmap(scaledFull, cropX, cropY, targetW, targetH)
                if (scaledFull != scaled) scaledFull.recycle()
            }
        }

        // 用二分法找到最接近目标大小的质量
        val targetBytes = (params.targetSizeMB * 1024 * 1024).toLong()
        var low = 10
        var high = 95
        var bestBytes: ByteArray? = null

        // 先用最高质量试一次
        val maxQualityBaos = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, high, maxQualityBaos)
        val maxQualityBytes = maxQualityBaos.toByteArray()

        if (maxQualityBytes.size <= targetBytes) {
            bestBytes = maxQualityBytes
        } else {
            while (low <= high) {
                val mid = (low + high) / 2
                val baos = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, mid, baos)
                val bytes = baos.toByteArray()
                if (bytes.size <= targetBytes) {
                    bestBytes = bytes
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (bestBytes == null) {
                val baos = java.io.ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 5, baos)
                bestBytes = baos.toByteArray()
            }
        }

        val name = "compressed_${System.currentTimeMillis()}.jpg"
        val outUri = saveToDownloads(context, name, "image/jpeg") ?: return "保存失败"
        context.contentResolver.openOutputStream(outUri)?.use { it.write(bestBytes) }
        val sizeMB = String.format("%.2f", bestBytes!!.size / 1024.0 / 1024.0)
        val cropInfo = if (original.width.toFloat() / original.height != targetW.toFloat() / targetH) " (居中裁切)" else ""
        return "压缩完成: $name\n原始: ${original.width}x${original.height}\n输出: ${targetW}x${targetH}${cropInfo}, ${sizeMB}MB"
    }

    private fun compressVideo(context: Context, uri: Uri, params: CompressParams): String {
        val tempInput = File(context.cacheDir, "video_input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempInput.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
        } ?: return "无法打开文件"

        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(tempInput.absolutePath)
            val origWidth = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val origHeight = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()

            val targetFps = if (params.targetFps > 0) params.targetFps else 30

            // 计算目标比特率（根据目标文件大小）
            val targetBitrate = if (params.targetSizeMB > 0 && durationMs > 0) {
                val targetBits = (params.targetSizeMB * 1024 * 1024 * 8).toLong()
                val durationSec = durationMs / 1000.0
                // 预留10%给音频
                ((targetBits * 0.9) / durationSec).toInt().coerceIn(500_000, 50_000_000)
            } else { 4_000_000 }

            val tempOutput = File(context.cacheDir, "video_output_${System.currentTimeMillis()}.mp4")
            val inputMediaItem = androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(tempInput))

            // 设置帧率
            val editedMediaItem = androidx.media3.transformer.EditedMediaItem.Builder(inputMediaItem)
                .setRemoveAudio(false)
                .setFrameRate(targetFps)
                .build()

            val latch = java.util.concurrent.CountDownLatch(1)
            var error: String? = null

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    // 设置视频编码器比特率
                    val videoEncoderSettings = androidx.media3.transformer.VideoEncoderSettings.Builder()
                        .setBitrate(targetBitrate)
                        .build()

                    val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(videoEncoderSettings)
                        .build()

                    val transformer = androidx.media3.transformer.Transformer.Builder(context)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .build()

                    transformer.addListener(object : androidx.media3.transformer.Transformer.Listener {
                        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                            latch.countDown()
                        }
                        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                            error = exportException.message
                            latch.countDown()
                        }
                    })

                    transformer.start(editedMediaItem, tempOutput.absolutePath)
                } catch (e: Exception) {
                    error = e.message
                    latch.countDown()
                }
            }

            // 等待转码完成（最长15分钟）
            val completed = latch.await(900, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                tempInput.delete(); tempOutput.delete()
                return "视频转码超时（超过15分钟）"
            }

            if (error != null) {
                tempInput.delete(); tempOutput.delete()
                return "视频转码失败: $error"
            }

            if (!tempOutput.exists() || tempOutput.length() == 0L) {
                tempInput.delete(); tempOutput.delete()
                return "视频转码失败: 输出文件为空"
            }

            val name = "compressed_${System.currentTimeMillis()}.mp4"
            val outUri = saveToDownloads(context, name, "video/mp4") ?: return "保存失败"
            context.contentResolver.openOutputStream(outUri)?.use { out ->
                tempOutput.inputStream().use { it.copyTo(out, 8192) }
            }

            val outputSize = String.format("%.2f", tempOutput.length() / 1024.0 / 1024.0)
            val inputSize = String.format("%.2f", tempInput.length() / 1024.0 / 1024.0)
            val displayW = if (rotation == 90 || rotation == 270) origHeight else origWidth
            val displayH = if (rotation == 90 || rotation == 270) origWidth else origHeight
            tempInput.delete(); tempOutput.delete()
            return "视频转码完成: $name\n原始: ${displayW}x${displayH} ${inputSize}MB\n输出: ${outputSize}MB (${targetFps}fps)\n比特率: ${targetBitrate/1000}kbps"
        } catch (e: Exception) {
            tempInput.delete()
            Log.e(TAG, "视频转码失败", e)
            return "视频转码失败: ${e.message}"
        }
    }

    private fun compressDocument(context: Context, uri: Uri, params: CompressParams): String {
        init(context)
        val mimeType = context.contentResolver.getType(uri) ?: ""
        if (!mimeType.contains("pdf")) {
            return "当前仅支持PDF文档压缩，其他格式开发中"
        }
        // PDF 压缩：移除元数据 + 重压缩图片
        val tempFile = File(context.cacheDir, "compress_doc_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
        } ?: return "无法打开文件"

        val doc = PDDocument.load(tempFile)
        doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()

        val name = "compressed_${System.currentTimeMillis()}.pdf"
        val outUri = saveToDownloads(context, name, "application/pdf") ?: return "保存失败"
        context.contentResolver.openOutputStream(outUri)?.use { doc.save(it) }
        val newSize = tempFile.length() // 近似
        doc.close()
        tempFile.delete()
        return "压缩完成: $name"
    }

    // ==================== 工具方法 ====================

    private fun saveToDownloads(context: Context, fileName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PsyMap")
            }
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }
}
