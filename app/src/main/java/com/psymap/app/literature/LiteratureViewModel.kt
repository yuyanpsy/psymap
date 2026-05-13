package com.psymap.app.literature

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psymap.app.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class LiteratureViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG = "LiteratureVM"
    private val gson = Gson()
    private val prefs = app.getSharedPreferences("literature_data", Context.MODE_PRIVATE)

    // 状态
    var literatures by mutableStateOf(listOf<Literature>())
        private set
    var groups by mutableStateOf(listOf<LiteratureGroup>())
        private set
    var annotations by mutableStateOf(listOf<PdfAnnotation>())
        private set
    var selectedLiterature by mutableStateOf<Literature?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf(listOf<Literature>())
        private set
    var onlineSearchResults by mutableStateOf(listOf<Literature>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var aiResult by mutableStateOf("")
        private set

    // 当前PDF阅读状态
    var currentPdfPages by mutableStateOf(listOf<Bitmap>())
        private set
    var currentPdfPage by mutableStateOf(0)
        private set
    var currentPdfTotalPages by mutableStateOf(0)
        private set

    init {
        PDFBoxResourceLoader.init(app)
        loadData()
    }

    // ==================== 数据持久化 ====================

    private fun loadData() {
        try {
            val litJson = prefs.getString("literatures", "[]") ?: "[]"
            val groupJson = prefs.getString("groups", "[]") ?: "[]"
            val annoJson = prefs.getString("annotations", "[]") ?: "[]"
            literatures = gson.fromJson(litJson, object : TypeToken<List<Literature>>() {}.type) ?: emptyList()
            groups = gson.fromJson(groupJson, object : TypeToken<List<LiteratureGroup>>() {}.type) ?: emptyList()
            annotations = gson.fromJson(annoJson, object : TypeToken<List<PdfAnnotation>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "加载数据失败", e)
        }
    }

    private fun saveData() {
        prefs.edit()
            .putString("literatures", gson.toJson(literatures))
            .putString("groups", gson.toJson(groups))
            .putString("annotations", gson.toJson(annotations))
            .apply()
    }

    // ==================== 文献收集 ====================

    fun addLiterature(lit: Literature) {
        literatures = literatures + lit
        saveData()
    }

    fun updateLiterature(lit: Literature) {
        literatures = literatures.map { if (it.id == lit.id) lit.copy(updatedAt = System.currentTimeMillis()) else it }
        saveData()
    }

    fun deleteLiterature(id: String) {
        literatures = literatures.filter { it.id != id }
        annotations = annotations.filter { it.literatureId != id }
        saveData()
    }

    /** 从PDF导入文献（提取元数据） */
    fun importFromPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                extractPdfMetadata(context, uri)
            }
            if (result != null) {
                addLiterature(result)
            }
            isLoading = false
        }
    }

    private fun extractPdfMetadata(context: Context, uri: Uri): Literature? {
        return try {
            // 复制到本地
            val litDir = File(context.getExternalFilesDir(null), "literature").apply { mkdirs() }
            val pdfFile = File(litDir, "lit_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                pdfFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val doc = PDDocument.load(pdfFile)
            val info = doc.documentInformation

            // 提取前3页文本用于摘要
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(3, doc.numberOfPages)
            }
            val text = stripper.getText(doc)
            doc.close()

            // 尝试从文本中提取标题（通常是第一行非空文本）
            val lines = text.lines().filter { it.isNotBlank() }
            val title = info?.title?.takeIf { it.isNotBlank() }
                ?: lines.firstOrNull()?.take(200) ?: "未知标题"
            val authors = info?.author?.split(",", ";", "and", "、")?.map { it.trim() } ?: emptyList()

            // 尝试提取DOI
            val doiRegex = Regex("""10\.\d{4,}/[^\s]+""")
            val doi = doiRegex.find(text)?.value ?: ""

            // 提取摘要（查找 Abstract 关键词后的内容）
            val abstractRegex = Regex("""(?i)(abstract|摘\s*要)[:\s：]*(.{50,800})""")
            val abstractText = abstractRegex.find(text)?.groupValues?.getOrNull(2)?.trim() ?: ""

            Literature(
                title = title,
                authors = authors,
                doi = doi,
                abstract = abstractText,
                pdfPath = pdfFile.absolutePath,
                source = "PDF导入"
            )
        } catch (e: Exception) {
            Log.e(TAG, "PDF元数据提取失败", e)
            null
        }
    }

    /** 手动添加文献 */
    fun addManualLiterature(
        title: String, authors: String, journal: String,
        year: Int, doi: String, abstract: String, tags: List<String>
    ) {
        val authorList = authors.split(",", ";", "、").map { it.trim() }.filter { it.isNotBlank() }
        addLiterature(Literature(
            title = title, authors = authorList, journal = journal,
            year = year, doi = doi, abstract = abstract, tags = tags, source = "手动录入"
        ))
    }

    /** 通过DOI从CrossRef导入 */
    var doiImportError by mutableStateOf("")
        private set

    fun importFromDoi(doi: String) {
        viewModelScope.launch {
            isLoading = true
            doiImportError = ""
            val result = withContext(Dispatchers.IO) { fetchFromCrossRef(doi) }
            if (result != null) {
                addLiterature(result)
            } else {
                doiImportError = "导入失败：请检查DOI格式是否正确，或网络是否可用"
            }
            isLoading = false
        }
    }

    private fun fetchFromCrossRef(doi: String): Literature? {
        return try {
            // 清理DOI：去除各种前缀
            val cleanDoi = doi
                .replace(Regex("""(?i)^doi\s*[：:]\s*"""), "")
                .removePrefix("https://doi.org/")
                .removePrefix("http://doi.org/")
                .removePrefix("doi.org/")
                .trim()
                .trimEnd('.', ',', ';', ')', ']') // 去除末尾标点

            if (!cleanDoi.startsWith("10.")) {
                Log.e(TAG, "DOI格式无效: $cleanDoi")
                return null
            }

            // 不对DOI做URL编码，直接拼接（CrossRef API支持原始DOI路径）
            val url = "https://api.crossref.org/works/$cleanDoi"
            val request = okhttp3.Request.Builder().url(url)
                .addHeader("User-Agent", "PsyMap/1.0 (mailto:psymap@example.com)")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "CrossRef HTTP ${response.code}: $cleanDoi")
                return null
            }

            val json = response.body?.string() ?: return null
            val map = gson.fromJson(json, Map::class.java)
            val message = (map["message"] as? Map<*, *>) ?: return null

            val title = ((message["title"] as? List<*>)?.firstOrNull() as? String) ?: "未知标题"
            val authors = (message["author"] as? List<*>)?.mapNotNull { a ->
                val am = a as? Map<*, *> ?: return@mapNotNull null
                val given = am["given"]?.toString() ?: ""
                val family = am["family"]?.toString() ?: ""
                "$given $family".trim()
            } ?: emptyList()
            val journal = ((message["container-title"] as? List<*>)?.firstOrNull() as? String) ?: ""
            val published = message["published-print"] as? Map<*, *>
                ?: message["published-online"] as? Map<*, *>
                ?: message["created"] as? Map<*, *>
            val dateParts = (published?.get("date-parts") as? List<*>)?.firstOrNull() as? List<*>
            val year = (dateParts?.firstOrNull() as? Double)?.toInt() ?: 0
            val abstractText = (message["abstract"] as? String)?.replace(Regex("<[^>]+>"), "") ?: ""

            Literature(
                title = title, authors = authors, journal = journal,
                year = year, doi = cleanDoi, abstract = abstractText, source = "DOI导入"
            )
        } catch (e: Exception) {
            Log.e(TAG, "CrossRef获取失败: ${e.message}", e)
            null
        }
    }

    /** 在线搜索（OpenAlex API） */
    fun searchOnline(query: String, sortBy: String = "relevance") {
        viewModelScope.launch {
            isLoading = true
            onlineSearchResults = emptyList()
            val results = withContext(Dispatchers.IO) { searchOpenAlex(query, sortBy) }
            onlineSearchResults = results
            isLoading = false
        }
    }

    private fun searchOpenAlex(query: String, sortBy: String): List<Literature> {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val sort = when (sortBy) {
                "date_desc" -> "publication_date:desc"
                "date_asc" -> "publication_date:asc"
                "cited" -> "cited_by_count:desc"
                else -> "relevance_score:desc"
            }
            val url = "https://api.openalex.org/works?search=$encoded&per_page=25&sort=$sort&mailto=psymap@example.com"
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return emptyList()
            val map = gson.fromJson(json, Map::class.java)
            val results = (map["results"] as? List<*>) ?: return emptyList()

            results.mapNotNull { item ->
                val work = item as? Map<*, *> ?: return@mapNotNull null
                val title = (work["title"] as? String) ?: return@mapNotNull null
                val doi = (work["doi"] as? String)?.removePrefix("https://doi.org/") ?: ""
                val year = (work["publication_year"] as? Double)?.toInt()?.let {
                    if (it in 1800..2030) it else 0  // 过滤明显错误的年份
                } ?: 0

                val authorships = (work["authorships"] as? List<*>) ?: emptyList<Any>()
                val authors = authorships.mapNotNull { a ->
                    val auth = a as? Map<*, *> ?: return@mapNotNull null
                    val author = auth["author"] as? Map<*, *> ?: return@mapNotNull null
                    author["display_name"]?.toString()
                }

                val primaryLocation = work["primary_location"] as? Map<*, *>
                val source = primaryLocation?.get("source") as? Map<*, *>
                val journal = source?.get("display_name")?.toString() ?: ""

                // 摘要（OpenAlex 返回 inverted index 格式）
                val abstractInverted = work["abstract_inverted_index"] as? Map<*, *>
                val abstractText = if (abstractInverted != null) {
                    try {
                        val wordPositions = mutableListOf<Pair<Int, String>>()
                        abstractInverted.forEach { (word, positions) ->
                            (positions as? List<*>)?.forEach { pos ->
                                val p = (pos as? Double)?.toInt() ?: return@forEach
                                wordPositions.add(p to word.toString())
                            }
                        }
                        wordPositions.sortedBy { it.first }.joinToString(" ") { it.second }
                    } catch (_: Exception) { "" }
                } else ""

                Literature(
                    title = title, authors = authors, journal = journal,
                    year = year, doi = doi, abstract = abstractText.take(500), source = "OpenAlex"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAlex搜索失败", e)
            emptyList()
        }
    }

    // ==================== 文献整理 ====================

    fun createGroup(name: String, color: String = "#EF6C00") {
        groups = groups + LiteratureGroup(name = name, color = color)
        saveData()
    }

    fun deleteGroup(id: String) {
        groups = groups.filter { it.id != id }
        // 清除文献的分组引用
        literatures = literatures.map { if (it.groupId == id) it.copy(groupId = "") else it }
        saveData()
    }

    fun moveToGroup(literatureId: String, groupId: String) {
        literatures = literatures.map {
            if (it.id == literatureId) it.copy(groupId = groupId, updatedAt = System.currentTimeMillis()) else it
        }
        saveData()
    }

    fun addTag(literatureId: String, tag: String) {
        literatures = literatures.map {
            if (it.id == literatureId && tag !in it.tags) it.copy(tags = it.tags + tag) else it
        }
        saveData()
    }

    fun removeTag(literatureId: String, tag: String) {
        literatures = literatures.map {
            if (it.id == literatureId) it.copy(tags = it.tags - tag) else it
        }
        saveData()
    }

    /** 去重检测 */
    fun findDuplicates(): List<Pair<Literature, Literature>> {
        val duplicates = mutableListOf<Pair<Literature, Literature>>()
        for (i in literatures.indices) {
            for (j in i + 1 until literatures.size) {
                val a = literatures[i]
                val b = literatures[j]
                // DOI 完全匹配
                if (a.doi.isNotBlank() && a.doi == b.doi) {
                    duplicates.add(a to b)
                    continue
                }
                // 标题相似度 > 80%
                if (a.title.isNotBlank() && b.title.isNotBlank()) {
                    val similarity = jaroWinkler(a.title.lowercase(), b.title.lowercase())
                    if (similarity > 0.85) duplicates.add(a to b)
                }
            }
        }
        return duplicates
    }

    private fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxDist = (maxOf(s1.length, s2.length) / 2) - 1
        if (maxDist < 0) return 0.0
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        var transpositions = 0
        for (i in s1.indices) {
            val start = maxOf(0, i - maxDist)
            val end = minOf(i + maxDist + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true; s2Matches[j] = true; matches++; break
            }
        }
        if (matches == 0) return 0.0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }
        val jaro = (matches.toDouble() / s1.length + matches.toDouble() / s2.length + (matches - transpositions / 2.0) / matches) / 3.0
        var prefix = 0
        for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    // ==================== 全文检索 ====================

    fun search(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }
        val q = query.lowercase()
        searchResults = literatures.filter { lit ->
            lit.title.lowercase().contains(q) ||
            lit.authors.any { it.lowercase().contains(q) } ||
            lit.journal.lowercase().contains(q) ||
            lit.doi.lowercase().contains(q) ||
            lit.abstract.lowercase().contains(q) ||
            lit.tags.any { it.lowercase().contains(q) } ||
            lit.notes.lowercase().contains(q) ||
            lit.keywords.any { it.lowercase().contains(q) }
        }
    }

    // ==================== PDF 阅读 ====================

    fun selectLiterature(lit: Literature) {
        selectedLiterature = lit
    }

    fun openPdf(context: Context, lit: Literature) {
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val file = File(lit.pdfPath)
                    if (!file.exists()) return@withContext
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    currentPdfTotalPages = renderer.pageCount
                    // 渲染当前页
                    renderPage(renderer, 0)
                    renderer.close()
                    fd.close()
                } catch (e: Exception) {
                    Log.e(TAG, "打开PDF失败", e)
                }
            }
            isLoading = false
        }
    }

    fun goToPage(context: Context, page: Int) {
        val lit = selectedLiterature ?: return
        if (page < 0 || page >= currentPdfTotalPages) return
        currentPdfPage = page
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(lit.pdfPath)
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                renderPage(renderer, page)
                renderer.close()
                fd.close()
            } catch (e: Exception) {
                Log.e(TAG, "渲染页面失败", e)
            }
        }
    }

    private fun renderPage(renderer: PdfRenderer, pageIndex: Int) {
        val page = renderer.openPage(pageIndex)
        val scale = 2
        val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        currentPdfPages = listOf(bitmap)
        currentPdfPage = pageIndex
    }

    // ==================== 标注管理 ====================

    fun addAnnotation(annotation: PdfAnnotation) {
        annotations = annotations + annotation
        saveData()
    }

    fun deleteAnnotation(id: String) {
        annotations = annotations.filter { it.id != id }
        saveData()
    }

    fun getAnnotationsForLiterature(litId: String): List<PdfAnnotation> {
        return annotations.filter { it.literatureId == litId }
    }

    // ==================== 引用管理 ====================

    fun generateCitation(lit: Literature, style: CitationStyle): String {
        return when (style) {
            CitationStyle.APA -> generateAPA(lit)
            CitationStyle.MLA -> generateMLA(lit)
            CitationStyle.GB_T7714 -> generateGBT7714(lit)
            CitationStyle.CHICAGO -> generateChicago(lit)
            CitationStyle.HARVARD -> generateHarvard(lit)
            CitationStyle.IEEE -> generateIEEE(lit)
            CitationStyle.VANCOUVER -> generateVancouver(lit)
        }
    }

    private fun generateAPA(lit: Literature): String {
        val authors = when {
            lit.authors.isEmpty() -> "Unknown"
            lit.authors.size == 1 -> formatAuthorAPA(lit.authors[0])
            lit.authors.size <= 20 -> lit.authors.dropLast(1).joinToString(", ") { formatAuthorAPA(it) } + ", & " + formatAuthorAPA(lit.authors.last())
            else -> lit.authors.take(19).joinToString(", ") { formatAuthorAPA(it) } + ", ... " + formatAuthorAPA(lit.authors.last())
        }
        val year = if (lit.year > 0) "(${lit.year})" else "(n.d.)"
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}." else ""
        val doi = if (lit.doi.isNotBlank()) " https://doi.org/${lit.doi}" else ""
        return "$authors $year. ${lit.title}.$journal$doi"
    }

    private fun formatAuthorAPA(name: String): String {
        val parts = name.trim().split(" ")
        return if (parts.size >= 2) "${parts.last()}, ${parts.dropLast(1).joinToString(" ") { "${it.first()}." }}"
        else name
    }

    private fun generateMLA(lit: Literature): String {
        val author = lit.authors.firstOrNull() ?: "Unknown"
        val parts = author.split(" ")
        val formatted = if (parts.size >= 2) "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}" else author
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}," else ""
        val year = if (lit.year > 0) " ${lit.year}" else ""
        return "$formatted. \"${lit.title}.\"$journal$year."
    }

    private fun generateGBT7714(lit: Literature): String {
        val authors = if (lit.authors.isEmpty()) "佚名"
        else if (lit.authors.size <= 3) lit.authors.joinToString(", ")
        else lit.authors.take(3).joinToString(", ") + ", 等"
        val year = if (lit.year > 0) "${lit.year}" else ""
        val journal = if (lit.journal.isNotBlank()) "${lit.journal}" else ""
        val doi = if (lit.doi.isNotBlank()) "DOI:${lit.doi}" else ""
        return "$authors. ${lit.title}[J]. $journal, $year. $doi"
    }

    private fun generateChicago(lit: Literature): String {
        val author = lit.authors.firstOrNull() ?: "Unknown"
        val parts = author.split(" ")
        val formatted = if (parts.size >= 2) "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}" else author
        val year = if (lit.year > 0) "${lit.year}" else ""
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}" else ""
        return "$formatted. \"${lit.title}.\"$journal ($year)."
    }

    private fun generateHarvard(lit: Literature): String {
        val authors = if (lit.authors.isEmpty()) "Unknown"
        else lit.authors.joinToString(", ") { it.split(" ").last() + ", " + it.split(" ").dropLast(1).joinToString(" ") { n -> "${n.first()}." } }
        val year = if (lit.year > 0) "(${lit.year})" else ""
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}," else ""
        return "$authors $year '${lit.title}',$journal."
    }

    private fun generateIEEE(lit: Literature): String {
        val authors = if (lit.authors.isEmpty()) "Unknown"
        else lit.authors.joinToString(", ") { name ->
            val parts = name.split(" ")
            if (parts.size >= 2) parts.dropLast(1).joinToString(" ") { "${it.first()}." } + " " + parts.last()
            else name
        }
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}," else ""
        val year = if (lit.year > 0) " ${lit.year}" else ""
        return "$authors, \"${lit.title},\"$journal$year."
    }

    private fun generateVancouver(lit: Literature): String {
        val authors = if (lit.authors.isEmpty()) "Unknown"
        else lit.authors.take(6).joinToString(", ") { name ->
            val parts = name.split(" ")
            if (parts.size >= 2) parts.last() + " " + parts.dropLast(1).joinToString("") { "${it.first()}" }
            else name
        } + if (lit.authors.size > 6) ", et al" else ""
        val journal = if (lit.journal.isNotBlank()) " ${lit.journal}." else ""
        val year = if (lit.year > 0) " ${lit.year}" else ""
        return "$authors. ${lit.title}.$journal$year."
    }

    // ==================== AI 功能 ====================

    fun aiSummarize(lit: Literature) {
        viewModelScope.launch {
            isLoading = true
            aiResult = ""
            val text = if (lit.abstract.isNotBlank()) lit.abstract
            else withContext(Dispatchers.IO) { extractFullText(lit) }

            if (text.isBlank()) {
                aiResult = "无法提取文本内容"
                isLoading = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                callAI("请用中文对以下学术论文内容进行摘要总结，提取核心观点、方法和结论（300字以内）：\n\n${text.take(4000)}")
            }
            aiResult = result
            isLoading = false
        }
    }

    fun aiTranslate(text: String, targetLang: String = "中文") {
        viewModelScope.launch {
            isLoading = true
            aiResult = ""
            val result = withContext(Dispatchers.IO) {
                callAI("请将以下学术文本翻译为${targetLang}，保持学术用语的准确性：\n\n${text.take(3000)}")
            }
            aiResult = result
            isLoading = false
        }
    }

    fun aiFindRelated(lit: Literature) {
        viewModelScope.launch {
            isLoading = true
            aiResult = ""
            val keywords = (lit.keywords + lit.tags).joinToString(", ")
            val result = withContext(Dispatchers.IO) {
                callAI("基于以下论文信息，推荐5篇相关文献（给出标题、作者、年份、期刊）：\n标题: ${lit.title}\n摘要: ${lit.abstract.take(500)}\n关键词: $keywords")
            }
            aiResult = result
            isLoading = false
        }
    }

    private fun extractFullText(lit: Literature): String {
        return try {
            val file = File(lit.pdfPath)
            if (!file.exists()) return ""
            val doc = PDDocument.load(file)
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = minOf(10, doc.numberOfPages)
            }
            val text = stripper.getText(doc)
            doc.close()
            text
        } catch (e: Exception) {
            Log.e(TAG, "提取全文失败", e)
            ""
        }
    }

    private fun callAI(prompt: String): String {
        return try {
            val prefs = getApplication<Application>().getSharedPreferences("psymap_prefs", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val apiUrl = prefs.getString("api_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
            val model = prefs.getString("api_model", "gpt-4o-mini") ?: "gpt-4o-mini"

            if (apiKey.isBlank()) return "请先在设置中配置AI API密钥"

            val body = """{"model":"$model","messages":[{"role":"user","content":${gson.toJson(prompt)}}],"max_tokens":1500}"""
            val request = okhttp3.Request.Builder()
                .url("$apiUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return "请求失败"
            val map = gson.fromJson(json, Map::class.java)
            val choices = map["choices"] as? List<*> ?: return "解析失败"
            val first = choices.firstOrNull() as? Map<*, *> ?: return "无结果"
            val message = first["message"] as? Map<*, *> ?: return "无结果"
            message["content"]?.toString() ?: "无内容"
        } catch (e: Exception) {
            Log.e(TAG, "AI调用失败", e)
            "AI调用失败: ${e.message}"
        }
    }

    // ==================== 云同步 ====================

    fun syncToCloud(onResult: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                try {
                    if (SupabaseClient.userId == null) return@withContext "请先登录"
                    val data = mapOf(
                        "literatures" to literatures,
                        "groups" to groups,
                        "annotations" to annotations
                    )
                    SupabaseClient.upsertLiteratureData(data)
                    "同步成功！文献${literatures.size}篇，标注${annotations.size}条"
                } catch (e: Exception) {
                    "同步失败: ${e.message}"
                }
            }
            isLoading = false
            onResult(result)
        }
    }

    fun syncFromCloud(onResult: (String) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                try {
                    if (SupabaseClient.userId == null) return@withContext "请先登录"
                    val data = SupabaseClient.fetchLiteratureData() ?: return@withContext "云端无数据"
                    val lits: List<Literature> = gson.fromJson(gson.toJson(data["literatures"]), object : TypeToken<List<Literature>>() {}.type) ?: emptyList()
                    val grps: List<LiteratureGroup> = gson.fromJson(gson.toJson(data["groups"]), object : TypeToken<List<LiteratureGroup>>() {}.type) ?: emptyList()
                    val annos: List<PdfAnnotation> = gson.fromJson(gson.toJson(data["annotations"]), object : TypeToken<List<PdfAnnotation>>() {}.type) ?: emptyList()
                    literatures = lits
                    groups = grps
                    annotations = annos
                    saveData()
                    "恢复成功！文献${lits.size}篇，标注${annos.size}条"
                } catch (e: Exception) {
                    "恢复失败: ${e.message}"
                }
            }
            isLoading = false
            onResult(result)
        }
    }

    fun clearAiResult() { aiResult = "" }
}
