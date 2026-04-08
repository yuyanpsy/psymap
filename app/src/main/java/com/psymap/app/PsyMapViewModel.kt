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
    val examDate = "2026-12-26"
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
        AiService.apiKey = apiKey
        AiService.apiBaseUrl = apiBaseUrl
        AiService.modelName = modelName
        AiService.textModelName = prefs.getString("textModelName", "Qwen/Qwen2.5-72B-Instruct") ?: "Qwen/Qwen2.5-72B-Instruct"

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
            .apply()
        AiService.apiKey = apiKey
        AiService.apiBaseUrl = apiBaseUrl
        AiService.modelName = modelName
    }

    private fun saveBanks() {
        prefs.edit().putString("questionBanks", gson.toJson(questionBanks)).apply()
    }

    private fun saveQuestions() {
        prefs.edit().putString("questions", gson.toJson(questions)).apply()
    }

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
                    tags: List<String> = emptyList(), isMemorize: Boolean = false) {
        val q = Question(
            bankId = bankId, content = content, answer = answer, type = type,
            options = options, chapter = chapter, tags = tags, isMemorize = isMemorize
        )
        questions = questions + q
        updateBankCount(bankId)
        saveQuestions()

        // 答案为空时，AI自动生成答案
        if (answer.isBlank() && content.isNotBlank() && type != QuestionType.SINGLE_CHOICE && type != QuestionType.MULTI_CHOICE) {
            val qId = q.id
            val prompt = "你是北师大MAP考研辅导专家。请为以下${type.label}提供标准答案，按踩分点逐条列出，专业术语准确。\n题目：$content"
            AiService.chatCompletion(prompt, content, { result ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (result.isNotBlank()) {
                        questions = questions.map { if (it.id == qId) it.copy(answer = result) else it }
                        saveQuestions()
                    }
                }
            }, { _ -> })
        }
    }

    fun updateQuestion(questionId: String, content: String, answer: String, options: List<String> = emptyList()) {
        if (currentUser.role != UserRole.ADMIN) return
        questions = questions.map {
            if (it.id == questionId) it.copy(content = content, answer = answer, options = options) else it
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
            recordBankProgress(bankId, 1)
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

    // ========== 主观题AI打分 ==========
    fun gradeSubjectiveAnswer(question: Question, userAnswer: String) {
        _isLoading.value = true
        _loadingMessage.value = "AI 正在评分..."
        aiGradeResult = ""
        aiGradeScore = -1

        if (question.answer.isNotBlank()) {
            // 本地有答案，直接对比评分
            AiService.gradeSubjectiveAnswer(
                question = question.content,
                correctAnswer = question.answer,
                userAnswer = userAnswer,
                onResult = { score, feedback ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        aiGradeScore = score
                        aiGradeResult = feedback
                        _isLoading.value = false
                        submitAnswer(question.id, userAnswer, score >= 60)
                    }
                },
                onError = { error ->
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        aiGradeResult = "评分失败: $error"; aiGradeScore = 0; _isLoading.value = false
                    }
                }
            )
        } else {
            // 本地无答案，AI先生成标准答案再评分
            _loadingMessage.value = "AI 正在生成标准答案并评分..."
            val genPrompt = "你是北师大MAP考研辅导专家。请为以下${question.type.label}提供标准答案，按踩分点逐条列出。\n题目：${question.content}"
            AiService.chatCompletion(genPrompt, question.content, { aiAnswer ->
                // 保存AI生成的答案
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    questions = questions.map { if (it.id == question.id) it.copy(answer = aiAnswer) else it }
                    saveQuestions()
                }
                // 用AI答案评分
                AiService.gradeSubjectiveAnswer(
                    question = question.content,
                    correctAnswer = aiAnswer,
                    userAnswer = userAnswer,
                    onResult = { score, feedback ->
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            aiGradeScore = score
                            aiGradeResult = feedback
                            _isLoading.value = false
                            submitAnswer(question.id, userAnswer, score >= 60)
                        }
                    },
                    onError = { error ->
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            aiGradeResult = "评分失败: $error"; aiGradeScore = 0; _isLoading.value = false
                        }
                    }
                )
            }, { error ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    aiGradeResult = "生成答案失败: $error"; aiGradeScore = 0; _isLoading.value = false
                }
            })
        }
    }

    // 导入结果提示
    var importResultMessage by mutableStateOf("")

    // ========== 拍照识别题目 ==========
    fun recognizeAndImport(bitmap: Bitmap, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _isLoading.value = true
        _loadingMessage.value = "AI 正在识别题目..."
        importResultMessage = ""

        AiService.recognizeQuestions(bitmap,
            onResult = { triples ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    var count = 0
                    triples.forEach { (q, a, opts) ->
                        if (q.isNotBlank()) {
                            val type = questionType ?: if (opts.isNotEmpty()) QuestionType.SINGLE_CHOICE else QuestionType.SHORT_ANSWER
                            addQuestion(bankId, q, a, type, options = opts, isMemorize = tagMemorize)
                            val lastQ = questions.lastOrNull()
                            if (lastQ != null && tagFrequent) toggleFrequent(lastQ.id)
                            count++
                        }
                    }
                    _isLoading.value = false
                    importResultMessage = "成功导入 $count 道题目"
                }
            },
            onError = { error ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    _isLoading.value = false
                    importResultMessage = "识别失败: $error"
                }
            }
        )
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
    fun recordBankProgress(bankId: String, count: Int) {
        val today = dateFormat.format(Date())
        val existing = checkInRecords.find { it.date == today }
        val currentProgress = existing?.bankProgress ?: emptyMap()
        val newProgress = currentProgress.toMutableMap()
        newProgress[bankId] = (newProgress[bankId] ?: 0) + count

        val totalDone = newProgress.values.sum()
        // 保存当天的总目标数（快照），后续修改计划不影响历史判断
        val totalTarget = dailyTargets.values.filter { it > 0 }.sum().coerceAtLeast(
            existing?.targetCount ?: 0  // 不降低已记录的目标
        )
        val updated = existing?.copy(completedCount = totalDone, bankProgress = newProgress, targetCount = totalTarget)
            ?: DailyCheckIn(date = today, completedCount = totalDone, targetCount = totalTarget, bankProgress = newProgress)

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

    // ========== 文件导入题目（AI解析文档内容） ==========
    fun importFromFileContent(text: String, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _isLoading.value = true
        _loadingMessage.value = "AI 正在解析文档..."
        importResultMessage = ""

        val typeHint = if (questionType != null) "所有题目的题型为「${questionType.label}」。" else ""
        val prompt = """你是题目解析专家。请从以下文档内容中提取所有题目和答案。${typeHint}
只返回纯JSON数组，不要用markdown代码块包裹。
规则：选择题把选项放在options数组中，非选择题options为空数组。
格式：[{"question":"题目","answer":"答案","options":["A.xx","B.xx"]}]
如果没有明确答案，answer填空字符串。"""

        AiService.chatCompletion(prompt, text.take(12000), { result ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    val cleaned = result.replace(Regex("```(?:json)?\\s*"), "").replace(Regex("```\\s*"), "").trim()
                    val list = com.google.gson.Gson().fromJson<List<Map<String, Any>>>(cleaned,
                        object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type)
                    var count = 0
                    val defaultType = questionType ?: QuestionType.SHORT_ANSWER
                    list?.forEach { item ->
                        val q = item["question"] as? String ?: ""
                        val a = item["answer"] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val opts = (item["options"] as? List<String>) ?: emptyList()
                        if (q.isNotBlank()) {
                            val type = if (opts.isNotEmpty()) QuestionType.SINGLE_CHOICE else defaultType
                            addQuestion(bankId, q, a, type, options = opts, isMemorize = tagMemorize)
                            val lastQ = questions.lastOrNull()
                            if (lastQ != null && tagFrequent) toggleFrequent(lastQ.id)
                            count++
                        }
                    }
                    _isLoading.value = false
                    importResultMessage = "成功导入 $count 道题目"
                } catch (e: Exception) {
                    _isLoading.value = false
                    importResultMessage = "解析失败: ${e.message}"
                }
            }
        }, { error ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                _isLoading.value = false
                importResultMessage = "导入失败: $error"
            }
        })
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
        // subject -> (correct, total)
        val result = mutableMapOf<Subject, Pair<Int, Int>>()
        for (bank in questionBanks) {
            val bankQuestions = getQuestionsForBank(bank.id)
            val correct = bankQuestions.sumOf { it.correctCount }
            val total = bankQuestions.sumOf { it.correctCount + it.wrongCount }
            result[bank.subject] = Pair(correct, total)
        }
        return result
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
