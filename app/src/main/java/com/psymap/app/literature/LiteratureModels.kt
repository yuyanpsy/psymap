package com.psymap.app.literature

import java.util.UUID

/** 文献条目 */
data class Literature(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val authors: List<String> = emptyList(),
    val journal: String = "",
    val year: Int = 0,
    val doi: String = "",
    val abstract: String = "",
    val keywords: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val groupId: String = "",       // 所属分组
    val source: String = "",        // 来源（手动/DOI/PDF导入）
    val pdfPath: String = "",       // 本地PDF路径
    val notes: String = "",         // 用户笔记
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 文献分组 */
data class LiteratureGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "#EF6C00",
    val createdAt: Long = System.currentTimeMillis()
)

/** PDF 标注 */
data class PdfAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val literatureId: String = "",
    val page: Int = 0,
    val type: AnnotationType = AnnotationType.HIGHLIGHT,
    val text: String = "",          // 选中的文本
    val comment: String = "",       // 用户批注
    val color: String = "#FFEB3B", // 标注颜色
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AnnotationType {
    HIGHLIGHT, UNDERLINE, NOTE, RECT
}

/** 引用格式 */
enum class CitationStyle(val label: String) {
    APA("APA 7th"),
    MLA("MLA 9th"),
    GB_T7714("GB/T 7714-2015"),
    CHICAGO("Chicago"),
    HARVARD("Harvard"),
    IEEE("IEEE"),
    VANCOUVER("Vancouver")
}
