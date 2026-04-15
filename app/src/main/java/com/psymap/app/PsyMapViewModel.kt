package com.psymap.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PsyMapViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("psymap", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    // ========== 状态 ==========
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("加载中...")
    val loadingMessage = _loadingMessage.asStateFlow()

    // 用户
    var currentUser by mutableStateOf(User())
    var isLoggedIn by mutableStateOf(false)

    // API配置
    var apiKey by mutableStateOf("sk-ozgipwvoghexlmpzriaesynaypyqjszqdllemcqzxvaokzqr")
    var apiBaseUrl by mutableStateOf("https://api.siliconflow.cn/v1")
    var modelName by mutableStateOf("deepseek-ai/DeepSeek-OCR")

    // AI功能开关（控制所有付费API调用）
    var aiEnabled by mutableStateOf(false)

    // 题库
    var questionBanks by mutableStateOf(listOf<QuestionBank>())
    var questions by mutableStateOf(listOf<Question>())

    // 学习
    var currentBankId by mutableStateOf("")
    var currentQuestionIndex by mutableStateOf(0)
    var sessionCorrectCount by mutableStateOf(0)
    var sessionTotalCount by mutableStateOf(0)
    var aiGradeResult by mutableStateOf("")
    var aiGradeScore by mutableStateOf(-1)

    // 打卡
    var checkInRecords by mutableStateOf(listOf<DailyCheckIn>())
    var todayCheckIn by mutableStateOf(DailyCheckIn())

    // 学习计划
    var studyPlans by mutableStateOf(listOf<StudyPlan>())
    var dailyTargets by mutableStateOf(mapOf<String, Int>())

    // 搜索
    var searchResults by mutableStateOf(listOf<Question>())

    // 目标分数
    var targetPoliticsScore by mutableStateOf(0)
    var targetEnglishScore by mutableStateOf(0)
    var targetPsyScore by mutableStateOf(0)
    val targetTotalScore: Int get() = targetPoliticsScore + targetEnglishScore + targetPsyScore

    // 考研倒计时
    val examDate = "2026-12-19"
    val daysUntilExam: Int
        get() {
            return try {
                val exam = dateFormat.parse(examDate)!!
                val today = Calendar.getInstance().time
                ((exam.time - today.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
            } catch (e: Exception) { 0 }
        }

    init {
        loadData()
    }

    // ========== 数据持久化 ==========
    private fun loadData() {
        // 强制迁移旧模型配置
        val savedModel = prefs.getString("modelName", null)
        if (savedModel == null || savedModel.contains("Qwen2.5-VL") || savedModel.contains("Qwen3-VL")) {
            prefs.edit().putString("modelName", "deepseek-ai/DeepSeek-OCR").apply()
        }

        apiKey = prefs.getString("apiKey", "sk-ozgipwvoghexlmpzriaesynaypyqjszqdllemcqzxvaokzqr") ?: "sk-ozgipwvoghexlmpzriaesynaypyqjszqdllemcqzxvaokzqr"
        apiBaseUrl = prefs.getString("apiBaseUrl", "https://api.siliconflow.cn/v1") ?: "https://api.siliconflow.cn/v1"
        modelName = prefs.getString("modelName", "deepseek-ai/DeepSeek-OCR") ?: "deepseek-ai/DeepSeek-OCR"
        aiEnabled = prefs.getBoolean("aiEnabled", false)
        AiService.apiKey = apiKey
        AiService.apiBaseUrl = apiBaseUrl
        AiService.modelName = modelName
        AiService.textModelName = prefs.getString("textModelName", "Qwen/Qwen2.5-72B-Instruct") ?: "Qwen/Qwen2.5-72B-Instruct"
        TencentConfig.init(prefs)

        val banksJson = prefs.getString("questionBanks", "[]") ?: "[]"
        questionBanks = gson.fromJson(banksJson, object : TypeToken<List<QuestionBank>>() {}.type) ?: emptyList()

        val questionsJson = prefs.getString("questions", "[]") ?: "[]"
        questions = gson.fromJson(questionsJson, object : TypeToken<List<Question>>() {}.type) ?: emptyList()

        val checkInJson = prefs.getString("checkIns", "[]") ?: "[]"
        checkInRecords = gson.fromJson(checkInJson, object : TypeToken<List<DailyCheckIn>>() {}.type) ?: emptyList()

        val plansJson = prefs.getString("studyPlans", "[]") ?: "[]"
        studyPlans = gson.fromJson(plansJson, object : TypeToken<List<StudyPlan>>() {}.type) ?: emptyList()

        val targetsJson = prefs.getString("dailyTargets", "{}") ?: "{}"
        dailyTargets = gson.fromJson(targetsJson, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()

        // 目标分数
        targetPoliticsScore = prefs.getInt("targetPoliticsScore", 0)
        targetEnglishScore = prefs.getInt("targetEnglishScore", 0)
        targetPsyScore = prefs.getInt("targetPsyScore", 0)

        val userJson = prefs.getString("user", null)
        if (userJson != null) {
            currentUser = gson.fromJson(userJson, User::class.java)
            isLoggedIn = true
        }

        // 初始化默认题库
        if (questionBanks.isEmpty()) {
            questionBanks = Subject.entries.map { subject ->
                QuestionBank(name = subject.label, subject = subject)
            }
            saveBanks()
        }

        updateTodayCheckIn()
        refreshCheckInStats()
    }

    fun saveApiConfig() {
        prefs.edit()
            .putString("apiKey", apiKey)
            .putString("apiBaseUrl", apiBaseUrl)
            .putString("modelName", modelName)
            .putBoolean("aiEnabled", aiEnabled)
            .apply()
        AiService.apiKey = apiKey
        AiService.apiBaseUrl = apiBaseUrl
        AiService.modelName = modelName
    }

    fun toggleAiEnabled(enabled: Boolean) {
        aiEnabled = enabled
        prefs.edit().putBoolean("aiEnabled", enabled).apply()
    }

    private fun saveBanks() {
        prefs.edit().putString("questionBanks", gson.toJson(questionBanks)).apply()
    }

    private fun saveQuestions() {
        prefs.edit().putString("questions", gson.toJson(questions)).apply()
    }

    fun saveQuestionsPublic() = saveQuestions()

    private fun saveCheckIns() {
        prefs.edit().putString("checkIns", gson.toJson(checkInRecords)).apply()
    }

    fun saveUser() {
        prefs.edit().putString("user", gson.toJson(currentUser)).apply()
    }

    // ========== 题库管理 ==========
    fun createBank(name: String, subject: Subject): QuestionBank {
        val bank = QuestionBank(name = name, subject = subject, creatorId = currentUser.id)
        questionBanks = questionBanks + bank
        saveBanks()
        return bank
    }

    fun renameBankIfAdmin(bankId: String, newName: String) {
        if (currentUser.role != UserRole.ADMIN) return
        questionBanks = questionBanks.map {
            if (it.id == bankId) it.copy(name = newName) else it
        }
        saveBanks()
    }

    fun deleteBank(bankId: String) {
        if (currentUser.role != UserRole.ADMIN) return
        questions = questions.filter { it.bankId != bankId }
        questionBanks = questionBanks.filter { it.id != bankId }
        saveBanks()
        saveQuestions()
    }

    fun getQuestionsForBank(bankId: String): List<Question> {
        return questions.filter { it.bankId == bankId }
    }

    fun addQuestion(bankId: String, content: String, answer: String, type: QuestionType,
                    options: List<String> = emptyList(), chapter: String = "",
                    tags: List<String> = emptyList(), isMemorize: Boolean = false,
                    explanation: String = "") {
        val q = Question(
            bankId = bankId, content = content, answer = answer, type = type,
            options = options, chapter = chapter, tags = tags, isMemorize = isMemorize,
            explanation = explanation
        )
        questions = questions + q
        updateBankCount(bankId)
        saveQuestions()
    }

    fun updateQuestion(questionId: String, content: String, answer: String, options: List<String> = emptyList(), explanation: String = "") {
        if (currentUser.role != UserRole.ADMIN) return
        questions = questions.map {
            if (it.id == questionId) it.copy(content = content, answer = answer, options = options, explanation = explanation) else it
        }
        saveQuestions()
    }

    fun updateQuestionType(questionId: String, type: QuestionType) {
        questions = questions.map {
            if (it.id == questionId) it.copy(type = type) else it
        }
        saveQuestions()
    }

    fun deleteQuestions(questionIds: Set<String>) {
        if (currentUser.role != UserRole.ADMIN) return
        val affectedBankIds = questions.filter { it.id in questionIds }.map { it.bankId }.toSet()
        questions = questions.filter { it.id !in questionIds }
        affectedBankIds.forEach { updateBankCount(it) }
        saveQuestions()
    }

    private fun updateBankCount(bankId: String) {
        val count = questions.count { it.bankId == bankId }
        questionBanks = questionBanks.map {
            if (it.id == bankId) it.copy(questionCount = count) else it
        }
        saveBanks()
    }

    // ========== 学习功能 ==========
    var studyQuestionIds by mutableStateOf(listOf<String>())  // 自定义题目列表（错题/收藏练习用）

    fun startStudySession(bankId: String, shuffle: Boolean = false) {
        currentBankId = bankId
        currentQuestionIndex = 0
        sessionCorrectCount = 0
        sessionTotalCount = 0
        if (shuffle) {
            // 乱序：把题目ID列表打乱，用 studyQuestionIds 控制顺序
            studyQuestionIds = getQuestionsForBank(bankId).shuffled().map { it.id }
        } else {
            studyQuestionIds = emptyList()  // 顺序模式用原始列表
        }
    }

    /** 只练习指定题目（错题本/收藏本用） */
    fun startStudySessionWithQuestions(questionIds: List<String>) {
        studyQuestionIds = questionIds
        currentBankId = ""
        currentQuestionIndex = 0
        sessionCorrectCount = 0
        sessionTotalCount = 0
    }

    fun getCurrentQuestion(): Question? {
        val list = if (studyQuestionIds.isNotEmpty()) {
            studyQuestionIds.mapNotNull { id -> questions.find { it.id == id } }
        } else {
            getQuestionsForBank(currentBankId)
        }
        return list.getOrNull(currentQuestionIndex)
    }

    fun getStudySessionSize(): Int {
        return if (studyQuestionIds.isNotEmpty()) studyQuestionIds.size
        else getQuestionsForBank(currentBankId).size
    }

    /** 检查题目今天是否已学习过 */
    fun isStudiedToday(questionId: String): Boolean {
        return todayCheckIn.bankStudiedIds.values.any { questionId in it }
    }

    fun submitAnswer(questionId: String, userAnswer: String, isCorrect: Boolean) {
        sessionTotalCount++
        if (isCorrect) sessionCorrectCount++

        val question = questions.find { it.id == questionId }
        questions = questions.map {
            if (it.id == questionId) {
                it.copy(
                    reviewCount = it.reviewCount + 1,
                    isInWrongBook = !isCorrect,  // 答对移出错题本，答错加入错题本
                    correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                    wrongCount = if (!isCorrect) it.wrongCount + 1 else it.wrongCount
                )
            } else it
        }
        saveQuestions()

        // 更新今日题库维度的练习进度
        val bankId = question?.bankId ?: currentBankId
        if (bankId.isNotBlank()) {
            recordBankProgress(bankId, 1, if (isCorrect) 1 else 0, questionId, isCorrect)
        }
    }

    fun moveToNext(): Boolean {
        val total = getStudySessionSize()
        return if (currentQuestionIndex < total - 1) {
            currentQuestionIndex++
            true
        } else false
    }

    fun moveToPrev(): Boolean {
        return if (currentQuestionIndex > 0) {
            currentQuestionIndex--
            true
        } else false
    }

    // ========== 主观题本地评分 ==========
    fun gradeSubjectiveAnswer(question: Question, userAnswer: String) {
        aiGradeResult = ""
        aiGradeScore = -1

        if (question.answer.isBlank()) {
            // 本地无答案，无法评分，仅记录作答
            aiGradeScore = -1
            aiGradeResult = "该题暂无标准答案，无法自动评分"
            submitAnswer(question.id, userAnswer, false)
            return
        }

        // 本地文本比对评分
        val correctAnswer = question.answer.trim()
        val userAns = userAnswer.trim()

        if (userAns.isBlank()) {
            aiGradeScore = 0
            aiGradeResult = "未作答"
            return
        }

        // 计算关键词覆盖率
        val punctuationRegex = Regex("[，。、；：\\u201c\\u201d\\u2018\\u2019（）\\[\\]【】\\s\\n\\r.,;:\"'()]+")
        val correctKeywords = correctAnswer
            .replace(punctuationRegex, " ")
            .split(" ")
            .filter { it.length >= 2 }
            .distinct()
        val userText = userAns.replace(punctuationRegex, " ")

        if (correctKeywords.isEmpty()) {
            // 答案太短，直接做包含判断
            val isCorrect = userAns.contains(correctAnswer, ignoreCase = true)
                || correctAnswer.contains(userAns, ignoreCase = true)
            aiGradeScore = if (isCorrect) 80 else 30
            aiGradeResult = if (isCorrect) "答案基本匹配" else "答案与标准答案差异较大"
            submitAnswer(question.id, userAnswer, aiGradeScore >= 60)
            return
        }

        val matchedCount = correctKeywords.count { keyword ->
            userText.contains(keyword, ignoreCase = true)
        }
        val coverageRate = matchedCount.toFloat() / correctKeywords.size

        aiGradeScore = (coverageRate * 100).toInt().coerceIn(0, 100)
        val matchedKeywords = correctKeywords.filter { userText.contains(it, ignoreCase = true) }
        val missedKeywords = correctKeywords.filter { !userText.contains(it, ignoreCase = true) }

        val feedback = StringBuilder()
        feedback.appendLine("关键词覆盖率: ${matchedCount}/${correctKeywords.size}")
        if (matchedKeywords.isNotEmpty()) {
            feedback.appendLine("✅ 命中: ${matchedKeywords.take(10).joinToString("、")}")
        }
        if (missedKeywords.isNotEmpty()) {
            feedback.appendLine("❌ 缺失: ${missedKeywords.take(10).joinToString("、")}")
        }
        aiGradeResult = feedback.toString().trim()
        submitAnswer(question.id, userAnswer, aiGradeScore >= 60)
    }

    // 导入结果提示
    var importResultMessage by mutableStateOf("")

    // ========== 拍照识别题目（ML Kit 本地OCR，免费） ==========
    fun recognizeAndImport(bitmap: Bitmap, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _isLoading.value = true
        _loadingMessage.value = "正在识别文字..."
        importResultMessage = ""

        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
        // 使用中文+拉丁文识别器
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
        )
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val ocrText = visionText.text
                if (ocrText.isBlank()) {
                    _isLoading.value = false
                    importResultMessage = "未识别到文字"
                    return@addOnSuccessListener
                }
                // 本地解析OCR文字为题目
                val defaultType = questionType ?: QuestionType.SHORT_ANSWER
                val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
                var count = 0
                var currentQuestion = ""
                var currentAnswer = ""
                var currentExplanation = ""
                var currentOptions = mutableListOf<String>()
                var section = "none"

                fun saveQ() {
                    if (currentQuestion.isNotBlank()) {
                        val type = if (currentOptions.isNotEmpty()) QuestionType.SINGLE_CHOICE else defaultType
                        addQuestion(bankId, currentQuestion.trim(), currentAnswer.trim(), type,
                            options = currentOptions.toList(), isMemorize = tagMemorize,
                            explanation = currentExplanation.trim())
                        val lastQ = questions.lastOrNull()
                        if (lastQ != null && tagFrequent) toggleFrequent(lastQ.id)
                        count++
                    }
                    currentQuestion = ""; currentAnswer = ""; currentExplanation = ""
                    currentOptions = mutableListOf(); section = "none"
                }

                for (line in lines) {
                    val isNewQ = line.matches(Regex("^(\\d+[.、）\\)]|（\\d+）|第\\d+题).*"))
                    val isOpt = line.matches(Regex("^[A-Ia-i][.、）\\):].*"))
                    val isAns = line.matches(Regex("^(答案|答)[：:].*")) || line == "答案"
                    val isExp = line.matches(Regex("^(解析|详解|分析|解答)[：:].*")) || line == "解析"
                    when {
                        isNewQ -> { saveQ(); currentQuestion = line.replace(Regex("^(\\d+[.、）\\)]|（\\d+）|第\\d+题[.、：:]?)\\s*"), ""); section = "question" }
                        isOpt && section != "answer" && section != "explanation" -> { currentOptions.add(line); section = "options" }
                        isExp -> { section = "explanation"; currentExplanation = line.replace(Regex("^(解析|详解|分析|解答)[：:]?\\s*"), "") }
                        isAns -> { section = "answer"; currentAnswer = line.replace(Regex("^(答案|答)[：:]?\\s*"), "") }
                        section == "explanation" -> currentExplanation += "\n$line"
                        section == "answer" -> currentAnswer += "\n$line"
                        currentQuestion.isNotBlank() && (section == "question" || section == "none") -> currentQuestion += "\n$line"
                        else -> { saveQ(); currentQuestion = line; section = "question" }
                    }
                }
                saveQ()

                _isLoading.value = false
                importResultMessage = if (count > 0) "成功导入 $count 道题目" else "识别到文字但未解析出题目，请检查格式"
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                importResultMessage = "识别失败: ${e.message}"
            }
    }

    // ========== 错题本 & 收藏本 ==========
    fun getWrongQuestions(): List<Question> = questions.filter { it.isInWrongBook }

    fun getFavoriteQuestions(): List<Question> = questions.filter { it.isInFavorites }

    fun toggleFavorite(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isInFavorites = !it.isInFavorites) else it
        }
        saveQuestions()
    }

    fun toggleFrequent(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isFrequent = !it.isFrequent) else it
        }
        saveQuestions()
    }

    fun toggleMemorize(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isMemorize = !it.isMemorize) else it
        }
        saveQuestions()
    }

    fun markTtsGenerated(questionIds: List<String>) {
        questions = questions.map {
            if (it.id in questionIds) it.copy(ttsGenerated = true) else it
        }
        saveQuestions()
    }

    fun removeFromWrongBook(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isInWrongBook = false) else it
        }
        saveQuestions()
    }

    // ========== 打卡 ==========
    private fun updateTodayCheckIn() {
        val today = dateFormat.format(Date())
        todayCheckIn = checkInRecords.find { it.date == today }
            ?: DailyCheckIn(date = today)
    }

    /** 记录某题库今日练习进度 +count 题 */
    fun recordBankProgress(bankId: String, count: Int, correctDelta: Int = 0, questionId: String = "", isCorrect: Boolean = false) {
        val today = dateFormat.format(Date())
        val existing = checkInRecords.find { it.date == today }
        val currentProgress = existing?.bankProgress ?: emptyMap()
        val newProgress = currentProgress.toMutableMap()
        newProgress[bankId] = (newProgress[bankId] ?: 0) + count

        val currentCorrect = existing?.bankCorrect ?: emptyMap()
        val newCorrect = currentCorrect.toMutableMap()
        newCorrect[bankId] = (newCorrect[bankId] ?: 0) + correctDelta

        // 去重的题目ID集合
        val currentStudiedIds = existing?.bankStudiedIds ?: emptyMap()
        val newStudiedIds = currentStudiedIds.toMutableMap()
        if (questionId.isNotBlank()) {
            val ids = (newStudiedIds[bankId] ?: emptyList()).toMutableSet()
            ids.add(questionId)
            newStudiedIds[bankId] = ids.toList()
        }

        val currentCorrectIds = existing?.bankCorrectIds ?: emptyMap()
        val newCorrectIds = currentCorrectIds.toMutableMap()
        if (questionId.isNotBlank()) {
            val ids = (newCorrectIds[bankId] ?: emptyList()).toMutableSet()
            if (isCorrect) ids.add(questionId) else ids.remove(questionId)
            newCorrectIds[bankId] = ids.toList()
        }

        val totalDone = newProgress.values.sum()
        // 保存当天的总目标数（快照），后续修改计划不影响历史判断
        val totalTarget = dailyTargets.values.filter { it > 0 }.sum().coerceAtLeast(
            existing?.targetCount ?: 0  // 不降低已记录的目标
        )
        val updated = existing?.copy(
            completedCount = totalDone, bankProgress = newProgress, bankCorrect = newCorrect,
            bankStudiedIds = newStudiedIds, bankCorrectIds = newCorrectIds, targetCount = totalTarget
        ) ?: DailyCheckIn(
            date = today, completedCount = totalDone, targetCount = totalTarget,
            bankProgress = newProgress, bankCorrect = newCorrect,
            bankStudiedIds = newStudiedIds, bankCorrectIds = newCorrectIds
        )

        checkInRecords = checkInRecords.filter { it.date != today } + updated
        todayCheckIn = updated
        saveCheckIns()

        // 检查是否所有有目标的题库都完成了 → 才算当天打卡
        refreshCheckInStats()
    }

    fun recordCheckIn(count: Int) {
        recordBankProgress(currentBankId, 0) // 触发刷新
    }

    /** 判断某天是否完成打卡（用当天记录的目标判断，不受后续修改影响） */
    fun isDayCheckedIn(checkIn: DailyCheckIn): Boolean {
        // 用记录中保存的目标（targetCount > 0 说明当天有设定目标）
        if (checkIn.targetCount > 0) {
            return checkIn.completedCount >= checkIn.targetCount
        }
        // 没有目标记录的旧数据，有练习就算打卡
        return checkIn.completedCount > 0
    }

    /** 刷新打卡统计（总天数、连续天数） */
    private fun refreshCheckInStats() {
        val checkedDays = checkInRecords.count { isDayCheckedIn(it) }
        val consecutive = calculateConsecutiveDays()
        currentUser = currentUser.copy(
            totalCheckInDays = checkedDays,
            consecutiveCheckInDays = consecutive
        )
        saveUser()
    }

    private fun calculateConsecutiveDays(): Int {
        val cal = Calendar.getInstance()
        var count = 0
        while (true) {
            val dateStr = dateFormat.format(cal.time)
            val dayRecord = checkInRecords.find { it.date == dateStr }
            if (dayRecord != null && isDayCheckedIn(dayRecord)) {
                count++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return count
    }

    // ========== 每日复习计划 ==========
    fun saveDailyTargets(targets: Map<String, Int>) {
        dailyTargets = targets
        prefs.edit().putString("dailyTargets", gson.toJson(targets)).apply()
    }

    // ========== 数据备份/恢复 ==========
    fun exportBackup(): String {
        val app = getApplication<android.app.Application>()
        // 收集英文泛读和心理学知识数据
        val readingPrefs = app.getSharedPreferences("psymap_reading", android.content.Context.MODE_PRIVATE)
        val marksPrefs = app.getSharedPreferences("psymap_marks", android.content.Context.MODE_PRIVATE)
        val psyPrefs = app.getSharedPreferences("psymap_psy_knowledge", android.content.Context.MODE_PRIVATE)

        val backup = mapOf(
            "version" to 4,
            "questionBanks" to questionBanks,
            "questions" to questions,
            "checkIns" to checkInRecords,
            "studyPlans" to studyPlans,
            "dailyTargets" to dailyTargets,
            "user" to currentUser,
            "apiKey" to apiKey,
            "apiBaseUrl" to apiBaseUrl,
            "modelName" to modelName,
            "targetPoliticsScore" to targetPoliticsScore,
            "targetEnglishScore" to targetEnglishScore,
            "targetPsyScore" to targetPsyScore,
            "readingArticles" to (readingPrefs.getString("saved_articles", "[]") ?: "[]"),
            "readingMarks" to marksPrefs.all.mapValues { it.value?.toString() ?: "" },
            "psyArticles" to (psyPrefs.getString("psy_articles", "[]") ?: "[]")
        )
        val json = gson.toJson(backup)

        return try {
            // 用 MediaStore 写入 Downloads（跨安装可访问）
            val resolver = app.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "psymap_backup.json")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            // 先删除旧文件
            resolver.delete(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf("psymap_backup.json")
            )
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            val qCount = questions.size
            val wCount = questions.count { it.isInWrongBook }
            val fCount = questions.count { it.isInFavorites }
            val freqCount = questions.count { it.isFrequent }
            val memCount = questions.count { it.isMemorize }
            "备份成功！\n题库: ${questionBanks.size}个, 题目: ${qCount}道\n错题: ${wCount}道, 收藏: ${fCount}道\n常考: ${freqCount}道, 多背: ${memCount}道\n打卡记录: ${checkInRecords.size}天\n连续打卡: ${currentUser.consecutiveCheckInDays}天, 累计: ${currentUser.totalCheckInDays}天\n文件: Downloads/psymap_backup.json"
        } catch (e: Exception) {
            "备份失败: ${e.message}"
        }
    }

    fun importBackup(): String {
        val app = getApplication<android.app.Application>()
        return try {
            val resolver = app.contentResolver
            // 通过 MediaStore 查找 Downloads 中的备份文件
            val projection = arrayOf(android.provider.MediaStore.Downloads._ID)
            val selection = "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=?"
            val selectionArgs = arrayOf("psymap_backup.json")
            val cursor = resolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                return "未找到备份文件\n请确认 Downloads 目录下有 psymap_backup.json"
            }
            val id = cursor.getLong(0)
            cursor.close()
            val uri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
            )
            val json = resolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return "无法读取备份文件"

            val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)

            questionBanks = gson.fromJson(gson.toJson(map["questionBanks"]),
                object : TypeToken<List<QuestionBank>>() {}.type) ?: emptyList()
            questions = gson.fromJson(gson.toJson(map["questions"]),
                object : TypeToken<List<Question>>() {}.type) ?: emptyList()
            checkInRecords = gson.fromJson(gson.toJson(map["checkIns"]),
                object : TypeToken<List<DailyCheckIn>>() {}.type) ?: emptyList()
            studyPlans = gson.fromJson(gson.toJson(map["studyPlans"]),
                object : TypeToken<List<StudyPlan>>() {}.type) ?: emptyList()
            dailyTargets = gson.fromJson(gson.toJson(map["dailyTargets"]),
                object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()

            saveBanks(); saveQuestions(); saveCheckIns()
            prefs.edit()
                .putString("studyPlans", gson.toJson(studyPlans))
                .putString("dailyTargets", gson.toJson(dailyTargets))
                .apply()

            val restoredUser = try {
                gson.fromJson(gson.toJson(map["user"]), User::class.java)
            } catch (e: Exception) { null }
            if (restoredUser != null) {
                currentUser = restoredUser
                isLoggedIn = true
                saveUser()
            }

            updateTodayCheckIn()
            refreshCheckInStats()

            val pScore = (map["targetPoliticsScore"] as? Double)?.toInt() ?: 0
            val eScore = (map["targetEnglishScore"] as? Double)?.toInt() ?: 0
            val psyScore = (map["targetPsyScore"] as? Double)?.toInt() ?: 0
            saveTargetScores(pScore, eScore, psyScore)

            val fCount = questions.count { it.isFrequent }
            val mCount = questions.count { it.isMemorize }

            // 恢复英文泛读、标记、心理学知识
            try {
                val readingArticles = map["readingArticles"] as? String
                if (!readingArticles.isNullOrBlank()) {
                    app.getSharedPreferences("psymap_reading", android.content.Context.MODE_PRIVATE)
                        .edit().putString("saved_articles", readingArticles).apply()
                }
                @Suppress("UNCHECKED_CAST")
                val readingMarks = map["readingMarks"] as? Map<String, String>
                if (readingMarks != null) {
                    val marksEditor = app.getSharedPreferences("psymap_marks", android.content.Context.MODE_PRIVATE).edit()
                    readingMarks.forEach { (k, v) -> marksEditor.putString(k, v) }
                    marksEditor.apply()
                }
                val psyArticles = map["psyArticles"] as? String
                if (!psyArticles.isNullOrBlank()) {
                    app.getSharedPreferences("psymap_psy_knowledge", android.content.Context.MODE_PRIVATE)
                        .edit().putString("psy_articles", psyArticles).apply()
                }
            } catch (_: Exception) {}

            "恢复成功！\n题库: ${questionBanks.size}个, 题目: ${questions.size}道\n错题: ${questions.count { it.isInWrongBook }}道, 收藏: ${questions.count { it.isInFavorites }}道\n常考: ${fCount}道, 多背: ${mCount}道\n打卡: ${checkInRecords.size}天"
        } catch (e: Exception) {
            "恢复失败: ${e.message}"
        }
    }

    fun importBackupFromUri(resolver: android.content.ContentResolver, uri: android.net.Uri): String {
        return try {
            val json = resolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: return "无法读取文件"

            val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)

            questionBanks = gson.fromJson(gson.toJson(map["questionBanks"]),
                object : TypeToken<List<QuestionBank>>() {}.type) ?: emptyList()
            questions = gson.fromJson(gson.toJson(map["questions"]),
                object : TypeToken<List<Question>>() {}.type) ?: emptyList()
            checkInRecords = gson.fromJson(gson.toJson(map["checkIns"]),
                object : TypeToken<List<DailyCheckIn>>() {}.type) ?: emptyList()
            studyPlans = gson.fromJson(gson.toJson(map["studyPlans"]),
                object : TypeToken<List<StudyPlan>>() {}.type) ?: emptyList()
            dailyTargets = gson.fromJson(gson.toJson(map["dailyTargets"]),
                object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()

            saveBanks(); saveQuestions(); saveCheckIns()
            prefs.edit()
                .putString("studyPlans", gson.toJson(studyPlans))
                .putString("dailyTargets", gson.toJson(dailyTargets))
                .apply()

            val restoredUser = try {
                gson.fromJson(gson.toJson(map["user"]), User::class.java)
            } catch (e: Exception) { null }
            if (restoredUser != null) {
                currentUser = restoredUser
                isLoggedIn = true
                saveUser()
            }

            updateTodayCheckIn()
            refreshCheckInStats()

            val pScore = (map["targetPoliticsScore"] as? Double)?.toInt() ?: 0
            val eScore = (map["targetEnglishScore"] as? Double)?.toInt() ?: 0
            val psyScore = (map["targetPsyScore"] as? Double)?.toInt() ?: 0
            saveTargetScores(pScore, eScore, psyScore)

            "恢复成功！\n题库: ${questionBanks.size}个, 题目: ${questions.size}道\n错题: ${questions.count { it.isInWrongBook }}道, 收藏: ${questions.count { it.isInFavorites }}道\n打卡: ${checkInRecords.size}天"
        } catch (e: Exception) {
            "恢复失败: ${e.message}"
        }
    }

    // ========== 目标分数 ==========
    fun saveTargetScores(politics: Int, english: Int, psy: Int) {
        targetPoliticsScore = politics
        targetEnglishScore = english
        targetPsyScore = psy
        prefs.edit()
            .putInt("targetPoliticsScore", politics)
            .putInt("targetEnglishScore", english)
            .putInt("targetPsyScore", psy)
            .apply()
    }

    // ========== 退出登录 ==========
    fun logout() {
        currentUser = User()
        isLoggedIn = false
        prefs.edit().remove("user").apply()
    }

    // ========== 管理员权限管理 ==========
    fun getAdminList(): List<String> {
        val json = prefs.getString("adminList", "[]") ?: "[]"
        return gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
    }

    fun addAdmin(name: String) {
        val list = getAdminList().toMutableList()
        if (name !in list) list.add(name)
        prefs.edit().putString("adminList", gson.toJson(list)).apply()
    }

    fun removeAdmin(name: String) {
        val list = getAdminList().toMutableList()
        list.remove(name)
        prefs.edit().putString("adminList", gson.toJson(list)).apply()
    }

    // ========== 文件导入题目（本地解析，不调用AI） ==========
    fun importFromFileContent(text: String, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _isLoading.value = true
        _loadingMessage.value = "正在解析文档..."
        importResultMessage = ""

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                val defaultType = questionType ?: QuestionType.SHORT_ANSWER
                var count = 0
                var currentQuestion = ""
                var currentAnswer = ""
                var currentExplanation = ""
                var currentOptions = mutableListOf<String>()
                // 解析状态：none=题目区, options=选项区, answer=答案区, explanation=解析区
                var section = "none"

                fun saveCurrentQuestion() {
                    if (currentQuestion.isNotBlank()) {
                        val type = if (currentOptions.isNotEmpty()) QuestionType.SINGLE_CHOICE else defaultType
                        addQuestion(bankId, currentQuestion.trim(), currentAnswer.trim(), type,
                            options = currentOptions.toList(), isMemorize = tagMemorize,
                            explanation = currentExplanation.trim())
                        val lastQ = questions.lastOrNull()
                        if (lastQ != null && tagFrequent) toggleFrequent(lastQ.id)
                        count++
                    }
                    currentQuestion = ""; currentAnswer = ""; currentExplanation = ""
                    currentOptions = mutableListOf(); section = "none"
                }

                for (line in lines) {
                    // 匹配题号开头：1. / 1、/ 1） / （1） / 第1题 等
                    val isNewQuestion = line.matches(Regex("^(\\d+[.、）\\)]|（\\d+）|第\\d+题).*"))
                    // 匹配选项：A. / A、/ A） 等
                    val isOption = line.matches(Regex("^[A-Ia-i][.、）\\):].*"))
                    // 匹配答案标记
                    val isAnswerLine = line.matches(Regex("^(答案|答)[：:].*")) || line == "答案" || line == "答"
                    // 匹配解析标记
                    val isExplanationLine = line.matches(Regex("^(解析|详解|分析|解答|解题思路)[：:].*"))
                        || line == "解析" || line == "详解"

                    when {
                        isNewQuestion -> {
                            saveCurrentQuestion()
                            currentQuestion = line.replace(Regex("^(\\d+[.、）\\)]|（\\d+）|第\\d+题[.、：:]?)\\s*"), "")
                            section = "question"
                        }
                        isOption && section != "answer" && section != "explanation" -> {
                            currentOptions.add(line)
                            section = "options"
                        }
                        isExplanationLine -> {
                            section = "explanation"
                            currentExplanation = line.replace(Regex("^(解析|详解|分析|解答|解题思路)[：:]?\\s*"), "")
                        }
                        isAnswerLine -> {
                            section = "answer"
                            currentAnswer = line.replace(Regex("^(答案|答)[：:]?\\s*"), "")
                        }
                        section == "explanation" -> {
                            currentExplanation += "\n$line"
                        }
                        section == "answer" -> {
                            currentAnswer += "\n$line"
                        }
                        currentQuestion.isNotBlank() && (section == "question" || section == "none") -> {
                            currentQuestion += "\n$line"
                        }
                        else -> {
                            saveCurrentQuestion()
                            currentQuestion = line
                            section = "question"
                        }
                    }
                }
                saveCurrentQuestion()

                _isLoading.value = false
                importResultMessage = "成功导入 $count 道题目"
            } catch (e: Exception) {
                _isLoading.value = false
                importResultMessage = "解析失败: ${e.message}"
            }
        }
    }

    // ========== 搜索 ==========
    fun searchQuestions(keyword: String) {
        searchResults = if (keyword.isBlank()) emptyList()
        else questions.filter {
            it.content.contains(keyword, ignoreCase = true) ||
            it.answer.contains(keyword, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(keyword, ignoreCase = true) }
        }
    }

    // ========== 统计 ==========
    val sessionAccuracy: Double
        get() = if (sessionTotalCount == 0) 0.0
                else sessionCorrectCount.toDouble() / sessionTotalCount

    fun getSubjectStats(): Map<Subject, Pair<Int, Int>> {
        // subject -> (correct, studied) 每道题只算一次，取最新结果
        val result = mutableMapOf<Subject, Pair<Int, Int>>()
        for (bank in questionBanks) {
            val bankQuestions = getQuestionsForBank(bank.id)
            val studied = bankQuestions.filter { it.correctCount + it.wrongCount > 0 }
            val correct = studied.count { !it.isInWrongBook }
            val prev = result[bank.subject]
            if (prev != null) {
                result[bank.subject] = Pair(prev.first + correct, prev.second + studied.size)
            } else {
                result[bank.subject] = Pair(correct, studied.size)
            }
        }
        return result
    }

    /** 按题库维度统计正确率 — 每道题只算一次，取最新结果 */
    fun getBankStats(): List<Triple<QuestionBank, Int, Int>> {
        return questionBanks.map { bank ->
            val bankQuestions = getQuestionsForBank(bank.id)
            // 只统计学习过的题目（有答题记录的）
            val studied = bankQuestions.filter { it.correctCount + it.wrongCount > 0 }
            // 最新一次答对的题目数（不在错题本 = 最新答对）
            val correct = studied.count { !it.isInWrongBook }
            Triple(bank, correct, studied.size)
        }
    }

    // ========== 管理员登录 ==========
    fun verifyAdmin(username: String, password: String): Boolean {
        return username == "YuYanPsy" && password == "8monthpanda."
    }

    fun loginAsAdmin(username: String) {
        currentUser = User(
            nickname = username,
            role = UserRole.ADMIN
        )
        isLoggedIn = true
        saveUser()
    }

    fun loginAsNormalUser(nickname: String, openId: String = "", avatarUrl: String = "") {
        currentUser = User(
            nickname = nickname,
            role = UserRole.ADMIN,
            wechatOpenId = openId,
            avatarUrl = avatarUrl
        )
        isLoggedIn = true
        saveUser()
    }

    val isAdmin: Boolean get() = currentUser.role == UserRole.ADMIN

    // 实时计算打卡天数（不依赖 currentUser 缓存）
    val totalCheckedDays: Int get() = checkInRecords.count { isDayCheckedIn(it) }
    val consecutiveCheckedDays: Int get() = calculateConsecutiveDays()
}
