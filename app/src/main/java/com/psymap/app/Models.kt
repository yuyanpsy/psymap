package com.psymap.app

import java.util.*

// ==================== 用户模型 ====================

enum class UserRole { ADMIN, NORMAL }

data class User(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String = "",
    val avatarUrl: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.NORMAL,
    val wechatOpenId: String = "",
    val totalCheckInDays: Int = 0,
    val consecutiveCheckInDays: Int = 0
)

// ==================== 题库模型 ====================

enum class Subject(val label: String, val emoji: String) {
    POLITICS("政治", "📕"),
    ENGLISH("英语", "📗"),
    GENERAL_PSY("普心", "📘"),
    PERSONALITY("人格", "📙"),
    STATS_MEASURE("实统测", "📓")
}

enum class QuestionType(val label: String) {
    // 通用
    SINGLE_CHOICE("单选题"),
    MULTI_CHOICE("多选题"),
    CASE_ANALYSIS("案例分析题"),
    SHORT_ANSWER("简答题"),
    ESSAY("论述题"),
    COMPREHENSIVE("综合写作题"),
    // 英语专用
    VOCAB_PHRASE("单词短语"),
    LONG_SENTENCE("长难句"),
    COMPOSITION("作文")
}

/** 根据科目返回可用题型 */
fun Subject.availableQuestionTypes(): List<QuestionType> = when (this) {
    Subject.ENGLISH -> listOf(
        QuestionType.VOCAB_PHRASE, QuestionType.LONG_SENTENCE, QuestionType.COMPOSITION
    )
    Subject.POLITICS -> listOf(
        QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.ESSAY
    )
    else -> listOf(
        QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE, QuestionType.CASE_ANALYSIS,
        QuestionType.SHORT_ANSWER, QuestionType.ESSAY, QuestionType.COMPREHENSIVE
    )
}

/** 自定义标签 */
enum class QuestionTag(val label: String) {
    FREQUENT("常考"),
    MEMORIZE("多背")
}

data class QuestionBank(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    val subject: Subject = Subject.GENERAL_PSY,
    val questionCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val creatorId: String = ""
)

data class Question(
    val id: String = UUID.randomUUID().toString(),
    val bankId: String = "",
    val content: String = "",
    val answer: String = "",
    val explanation: String = "",              // 解析（选择题/单词短语/长难句用）
    val options: List<String> = emptyList(),  // 选择题选项
    val type: QuestionType = QuestionType.SINGLE_CHOICE,
    val chapter: String = "",                  // 章节标签
    val tags: List<String> = emptyList(),      // 自定义标签
    var reviewCount: Int = 0,                  // 复习遍数
    var isInWrongBook: Boolean = false,
    var isInFavorites: Boolean = false,
    var correctCount: Int = 0,
    var wrongCount: Int = 0,
    val note: String = "",                     // 个人笔记
    var isFrequent: Boolean = false,           // 常考标签
    var isMemorize: Boolean = false,           // 多背标签
    var ttsGenerated: Boolean = false          // 已生成TTS音频
) {
    val errorRate: Double
        get() = if (correctCount + wrongCount == 0) 0.0
                else wrongCount.toDouble() / (correctCount + wrongCount)
}

// ==================== 学习记录 ====================

data class AnswerRecord(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String = "",
    val bankId: String = "",
    val isCorrect: Boolean = false,
    val userAnswer: String = "",
    val aiScore: Int = -1,           // AI打分 (主观题, -1表示未打分)
    val aiFeedback: String = "",     // AI反馈
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyCheckIn(
    val date: String = "",           // yyyy-MM-dd
    val completedCount: Int = 0,
    val targetCount: Int = 0,
    val studyMinutes: Int = 0,
    val bankProgress: Map<String, Int> = emptyMap(),  // bankId -> completed (含重复，用于打卡判定)
    val bankCorrect: Map<String, Int> = emptyMap(),   // bankId -> today correct count (含重复)
    val bankStudiedIds: Map<String, List<String>> = emptyMap(),   // bankId -> 今日学过的题目ID（去重）
    val bankCorrectIds: Map<String, List<String>> = emptyMap()   // bankId -> 今日最新答对的题目ID（去重）
)

// ==================== 学习计划 ====================

enum class StudyPhase(val label: String) {
    FOUNDATION("基础阶段"),
    INTENSIVE("强化阶段"),
    SPRINT("冲刺阶段")
}

data class StudyPlan(
    val id: String = UUID.randomUUID().toString(),
    val phase: StudyPhase = StudyPhase.FOUNDATION,
    val startDate: String = "",
    val endDate: String = "",
    val dailyTargets: Map<String, Int> = emptyMap(),  // bankId -> daily target
    val description: String = ""
)

// ==================== 艾宾浩斯复习 ====================

data class ReviewReminder(
    val questionId: String = "",
    val bankName: String = "",
    val nextReviewDate: String = "",
    val reviewStage: Int = 0   // 0=1天后, 1=2天后, 2=4天后, 3=7天后, 4=15天后...
)
