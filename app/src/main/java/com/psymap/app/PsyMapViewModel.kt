package com.psymap.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psymap.app.db.AppDatabase
import com.psymap.app.db.toEntity
import com.psymap.app.db.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PsyMapViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("psymap", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val db = AppDatabase.getInstance(app)
    private val questionDao = db.questionDao()

    // ========== 云端同步状态 ==========
    var cloudSyncing by mutableStateOf(false)
    var cloudSyncMessage by mutableStateOf("")
    var cloudUserId: String?
        get() = SupabaseClient.userId
        set(value) { SupabaseClient.userId = value }

    // 数据指纹：用于检测本地/云端数据是否有变化
    var localDataHash by mutableStateOf("")
    var lastPushedHash by mutableStateOf("")
    var lastPulledHash by mutableStateOf("")
    val hasLocalChanges: Boolean get() = localDataHash.isNotBlank() && localDataHash != lastPushedHash
    val hasCloudChanges: Boolean get() = false // 云端变化在 pullAll 后通过对比检测

    private fun computeDataHash(): String {
        val key = "${questionBanks.size}_${questions.size}_${questions.sumOf { it.reviewCount }}_${checkInRecords.size}_${dailyTargets.hashCode()}_${targetScores.hashCode()}"
        return key.hashCode().toString(16)
    }

    fun updateLocalHash() {
        localDataHash = computeDataHash()
    }

    // 自动同步定时器
    private var autoSyncJob: kotlinx.coroutines.Job? = null
    fun startAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(60_000) // 每60秒检查一次
                // 迁移完成前不自动推送
                if (!prefs.getBoolean("migration_delete_bulk_v4", false)) continue
                if (SupabaseClient.userId != null) {
                    val currentHash = computeDataHash()
                    if (currentHash != lastPushedHash) {
                        try {
                            SupabaseClient.pushAll(
                                questionBanks, questions, checkInRecords, dailyTargets,
                                targetScores["政治"] ?: 0, targetScores["英语"] ?: 0, targetScores["专业综合"] ?: 0,
                                studyPlans,
                                targetScoresMap = targetScores
                            )
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                lastPushedHash = currentHash
                                localDataHash = currentHash
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    // 后台同步：不阻塞 UI，静默推送
    private fun syncToCloud(block: suspend () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("PsyMap-Sync", "syncToCloud started [v2fix]")
                block()
                Log.d("PsyMap-Sync", "syncToCloud completed")
            } catch (e: Exception) {
                Log.e("PsyMap-Sync", "syncToCloud failed: ${e.message}")
            }
        }
        updateLocalHash()
    }

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

    // 目标分数（灵活科目，key=科目名，value=分数）
    var targetScores by mutableStateOf(mapOf<String, Int>())
    val targetTotalScore: Int get() = targetScores.values.sum()

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

    // 数据加载状态
    var dataLoading by mutableStateOf(true)

    init {
        loadDataFast()
        // 耗时的题目数据在后台从 Room 加载
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            migrateFromPrefsIfNeeded()
            // 一次性迁移：删除人格题库中社会心理学章节的题目
            if (!prefs.getBoolean("migration_delete_social_v1", false)) {
                val personalityBank = questionBanks.find { it.subject == Subject.PERSONALITY }
                if (personalityBank != null) {
                    val socialChapters = listOf("社会思维", "社会关系", "社会影响", "应用社会心理学")
                    val toDelete = questionDao.getByBankId(personalityBank.id)
                        .filter { e -> socialChapters.any { e.chapter.contains(it) } }
                    if (toDelete.isNotEmpty()) {
                        questionDao.deleteByIds(toDelete.map { it.id })
                        // 同步删除到云端
                        try { SupabaseClient.deleteQuestions(toDelete.map { it.id }) } catch (_: Exception) {}
                    }
                }
                prefs.edit().putBoolean("migration_delete_social_v1", true).apply()
            }
            // v2: 确保社会心理学章节从云端也彻底删除
            if (!prefs.getBoolean("migration_delete_social_v2", false)) {
                val personalityBank = questionBanks.find { it.subject == Subject.PERSONALITY }
                if (personalityBank != null) {
                    val socialChapters = listOf("社会思维", "社会关系", "社会影响", "应用社会心理学")
                    // 从Room删除（如果还有残留）
                    val toDelete = questionDao.getByBankId(personalityBank.id)
                        .filter { e -> socialChapters.any { e.chapter.contains(it) } }
                    if (toDelete.isNotEmpty()) {
                        questionDao.deleteByIds(toDelete.map { it.id })
                    }
                    // 强制从云端删除这些章节的题目
                    try {
                        val cloudData = SupabaseClient.pullAll()
                        val cloudToDelete = cloudData.questions
                            .filter { q -> q.bankId == personalityBank.id && socialChapters.any { q.chapter.contains(it) } }
                        if (cloudToDelete.isNotEmpty()) {
                            SupabaseClient.deleteQuestions(cloudToDelete.map { it.id })
                            Log.d("PsyMap-Migration", "v2: 从云端删除社会心理学题目 ${cloudToDelete.size} 道")
                        }
                    } catch (e: Exception) {
                        Log.e("PsyMap-Migration", "v2: 云端删除失败: ${e.message}")
                    }
                }
                prefs.edit().putBoolean("migration_delete_social_v2", true).apply()
            }
            val allQuestions = questionDao.getAll().map { it.toDomain() }
            val cleanQuestions = allQuestions.filter { !it.bankId.startsWith("__") }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                questions = cleanQuestions
                // 更新题库计数
                questionBanks = questionBanks.map { bank ->
                    bank.copy(questionCount = questions.count { it.bankId == bank.id })
                }
                saveBanks()
                dataLoading = false

                // 一次性修复：打卡记录补全
                val fixDates = listOf("2026-04-06", "2026-04-19", "2026-04-28", "2026-05-12")
                val totalTargetFix = dailyTargets.values.filter { it > 0 }.sum().takeIf { it > 0 } ?: 1
                var fixed = false
                for (date in fixDates) {
                    val existing = checkInRecords.find { it.date == date }
                    if (existing == null || existing.completedCount < (existing.targetCount.takeIf { it > 0 } ?: totalTargetFix)) {
                        val patched = existing?.copy(completedCount = totalTargetFix, targetCount = totalTargetFix)
                            ?: DailyCheckIn(date = date, completedCount = totalTargetFix, targetCount = totalTargetFix)
                        checkInRecords = checkInRecords.filter { it.date != date } + patched
                        syncToCloud { SupabaseClient.upsertCheckIn(patched) }
                        fixed = true
                    }
                }
                if (fixed) { saveCheckIns(); refreshCheckInStats() }
            }
        }
    }

    // ========== 数据持久化 ==========
    private fun loadDataFast() {
        // 恢复云端用户 ID
        SupabaseClient.userId = prefs.getString("cloud_user_id", null)
        SupabaseClient.supabaseUrl = prefs.getString("supabase_url", SupabaseClient.supabaseUrl) ?: SupabaseClient.supabaseUrl
        SupabaseClient.supabaseKey = prefs.getString("supabase_key", SupabaseClient.supabaseKey) ?: SupabaseClient.supabaseKey

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
        val rawBanks: List<QuestionBank> = gson.fromJson(banksJson, object : TypeToken<List<QuestionBank>>() {}.type) ?: emptyList()
        val cleanBanks = rawBanks.filter { !it.id.startsWith("__") }
        questionBanks = cleanBanks
        if (cleanBanks.size < rawBanks.size) saveBanks()

        val checkInJson = prefs.getString("checkIns", "[]") ?: "[]"
        checkInRecords = gson.fromJson(checkInJson, object : TypeToken<List<DailyCheckIn>>() {}.type) ?: emptyList()

        val plansJson = prefs.getString("studyPlans", "[]") ?: "[]"
        studyPlans = gson.fromJson(plansJson, object : TypeToken<List<StudyPlan>>() {}.type) ?: emptyList()

        val targetsJson = prefs.getString("dailyTargets", "{}") ?: "{}"
        dailyTargets = gson.fromJson(targetsJson, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap()
    }

    private suspend fun migrateFromPrefsIfNeeded() {
        if (prefs.contains("questions")) {
            val json = prefs.getString("questions", "[]") ?: "[]"
            if (json != "[]") {
                val list: List<Question> = try {
                    gson.fromJson(json, object : TypeToken<List<Question>>() {}.type) ?: emptyList()
                } catch (e: Exception) { emptyList() }
                if (list.isNotEmpty()) {
                    questionDao.insertAll(list.map { it.toEntity() })
                }
            }
            prefs.edit().remove("questions").apply()
        }
    }

    @Suppress("unused")
    private fun loadData() {
        loadDataFast()

        // 一次性修复：打卡记录补全（必须在 dailyTargets 加载之后）
        val fixDates = listOf("2026-04-06", "2026-04-19", "2026-04-28", "2026-05-12")
        val totalTargetFix = dailyTargets.values.filter { it > 0 }.sum()
        if (totalTargetFix > 0) {
            var fixed = false
            for (date in fixDates) {
                val existing = checkInRecords.find { it.date == date }
                if (existing == null || existing.completedCount < (existing.targetCount.takeIf { it > 0 } ?: totalTargetFix)) {
                    val patched = existing?.copy(completedCount = totalTargetFix, targetCount = totalTargetFix)
                        ?: DailyCheckIn(date = date, completedCount = totalTargetFix, targetCount = totalTargetFix)
                    checkInRecords = checkInRecords.filter { it.date != date } + patched
                    syncToCloud { SupabaseClient.upsertCheckIn(patched) }
                    fixed = true
                }
            }
            if (fixed) saveCheckIns()
        }

        // 目标分数（新格式：JSON map；兼容旧格式：3个固定字段）
        val scoresJson = prefs.getString("targetScoresMap", null)
        if (scoresJson != null) {
            targetScores = try { gson.fromJson(scoresJson, object : TypeToken<Map<String, Int>>() {}.type) ?: emptyMap() } catch (e: Exception) { emptyMap() }
        } else {
            val p = prefs.getInt("targetPoliticsScore", 0)
            val e = prefs.getInt("targetEnglishScore", 0)
            val s = prefs.getInt("targetPsyScore", 0)
            targetScores = mutableMapOf<String, Int>().apply {
                if (p > 0) put("政治", p)
                if (e > 0) put("英语", e)
                if (s > 0) put("专业综合", s)
            }
        }

        val userJson = prefs.getString("user", null)
        if (userJson != null) {
            currentUser = gson.fromJson(userJson, User::class.java)
            isLoggedIn = true
        }

        // 不在这里创建默认题库，等云端同步完成后再判断
        updateTodayCheckIn()
        refreshCheckInStats()

        // 获取设备ID
        val deviceId = try {
            android.provider.Settings.Secure.getString(
                getApplication<android.app.Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) { "" }

        // 自动登录并从云端恢复数据
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (SupabaseClient.userId == null) {
                    // 优先用设备ID查找（卸载重装后恢复）
                    if (deviceId.isNotBlank()) {
                        SupabaseClient.loginOrRegister(
                            nickname = currentUser.nickname.ifBlank { "device_$deviceId" },
                            deviceId = deviceId
                        )
                    } else if (currentUser.nickname.isNotBlank()) {
                        SupabaseClient.loginOrRegister(currentUser.nickname)
                    }
                }
                if (SupabaseClient.userId != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        // 迁移完成后才从云端同步
                        if (prefs.getBoolean("migration_delete_bulk_v4", false)) {
                            syncFromCloud()
                        }
                        // 云端同步完成后，如果仍然没有题库，才创建默认题库
                        if (questionBanks.isEmpty()) {
                            questionBanks = Subject.entries.map { subject ->
                                QuestionBank(name = subject.label, subject = subject)
                            }
                            saveBanks()
                        }
                        startAutoSync()
                    }
                } else {
                    // 无法连接云端，本地创建默认题库
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (questionBanks.isEmpty()) {
                            questionBanks = Subject.entries.map { subject ->
                                QuestionBank(name = subject.label, subject = subject)
                            }
                            saveBanks()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PsyMap-Sync", "自动登录失败: ${e.message}")
                // 失败时也要确保有默认题库
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (questionBanks.isEmpty()) {
                        questionBanks = Subject.entries.map { subject ->
                            QuestionBank(name = subject.label, subject = subject)
                        }
                        saveBanks()
                    }
                }
            }
        }
        updateLocalHash()
        lastPushedHash = localDataHash
    }

    /** App 进入后台时同步数据 */
    fun onAppBackground() {
        if (SupabaseClient.userId != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    SupabaseClient.pushAll(
                        questionBanks, questions, checkInRecords, dailyTargets,
                        targetScores["政治"] ?: 0, targetScores["英语"] ?: 0, targetScores["专业综合"] ?: 0,
                        studyPlans,
                        targetScoresMap = targetScores
                    )
                    SupabaseClient.upsertSettings(apiKey, apiBaseUrl, modelName, AiService.textModelName, aiEnabled)
                    Log.d("PsyMap-Sync", "后台同步完成")
                } catch (e: Exception) {
                    Log.e("PsyMap-Sync", "后台同步失败: ${e.message}")
                }
            }
        }
    }

    /** 从云端拉取并合并数据 */
    fun syncFromCloud() {
        cloudSyncing = true
        cloudSyncMessage = ""
        Log.d("PsyMap-Sync", "syncFromCloud started, userId=${SupabaseClient.userId}")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("PsyMap-Sync", "calling pullAll()...")
                val data = SupabaseClient.pullAll()
                Log.d("PsyMap-Sync", "pullAll done: banks=${data.banks.size}, questions=${data.questions.size}, checkIns=${data.checkIns.size}, dailyTargets=${data.dailyTargets.size}")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    // 清理旧版遗留的特殊 bank（__mindmap__ / __audio__）
                    questionBanks = questionBanks.filter { !it.id.startsWith("__") }
                    questions = questions.filter { !it.bankId.startsWith("__") }
                    saveBanks(); saveQuestions()

                    // 判断是否为全新安装（本地无用户数据）
                    val isFreshInstall = questions.isEmpty() && checkInRecords.isEmpty()
                    Log.d("PsyMap-Sync", "isFreshInstall=$isFreshInstall, localQuestions=${questions.size}, localCheckIns=${checkInRecords.size}")

                    if (isFreshInstall && data.banks.isNotEmpty()) {
                        Log.d("PsyMap-Sync", "Fresh install branch: replacing local with cloud data")
                        // 全新安装：直接用云端数据替换本地
                        questionBanks = data.banks
                        saveBanks()
                        questions = data.questions
                        saveQuestions()
                        Log.d("PsyMap-Sync", "Saved ${data.banks.size} banks, ${data.questions.size} questions")
                        if (data.checkIns.isNotEmpty()) {
                            checkInRecords = data.checkIns.sortedByDescending { it.date }
                            saveCheckIns()
                        }
                    } else {
                        // 强制用云端数据覆盖本地题目（保留本地的学习进度取较大值）
                        val localQMap = questions.associateBy { it.id }
                        val mergedQuestions = mutableListOf<Question>()
                        val cloudIds = mutableSetOf<String>()
                        
                        for (cq in data.questions) {
                            cloudIds.add(cq.id)
                            val lq = localQMap[cq.id]
                            if (lq == null) {
                                // 云端有但本地没有：如果本地几乎为空（全新安装漏判），添加云端数据
                                if (localQMap.size < 10) {
                                    mergedQuestions.add(cq)
                                }
                                // 否则视为本地已删除，不恢复
                            } else {
                                // 以云端 bankId 为准，学习进度取较大值
                                mergedQuestions.add(cq.copy(
                                    reviewCount = maxOf(lq.reviewCount, cq.reviewCount),
                                    correctCount = maxOf(lq.correctCount, cq.correctCount),
                                    wrongCount = maxOf(lq.wrongCount, cq.wrongCount),
                                    isInWrongBook = lq.isInWrongBook || cq.isInWrongBook,
                                    isInFavorites = lq.isInFavorites || cq.isInFavorites,
                                    isFrequent = lq.isFrequent || cq.isFrequent,
                                    isMemorize = lq.isMemorize || cq.isMemorize,
                                    ttsGenerated = lq.ttsGenerated || cq.ttsGenerated
                                ))
                            }
                        }
                        // 保留本地有但云端没有的题目
                        for (lq in questions) {
                            if (lq.id !in cloudIds) mergedQuestions.add(lq)
                        }
                        questions = mergedQuestions
                        saveQuestions()

                        // 合并题库
                        val localBankIds = questionBanks.map { it.id }.toSet()
                        val newBanks = data.banks.filter { it.id !in localBankIds }
                        if (newBanks.isNotEmpty()) {
                            questionBanks = questionBanks + newBanks
                            saveBanks()
                        }
                        // 更新题库计数
                        questionBanks = questionBanks.map { bank ->
                            bank.copy(questionCount = questions.count { it.bankId == bank.id })
                        }
                        saveBanks()

                        // 合并打卡
                        val localCiMap = checkInRecords.associateBy { it.date }.toMutableMap()
                        var ciChanged = false
                        for (cc in data.checkIns) {
                            val lc = localCiMap[cc.date]
                            if (lc == null) {
                                localCiMap[cc.date] = cc; ciChanged = true
                            } else if (cc.completedCount > lc.completedCount) {
                                localCiMap[cc.date] = cc; ciChanged = true
                            }
                        }
                        if (ciChanged) {
                            checkInRecords = localCiMap.values.sortedByDescending { it.date }
                            saveCheckIns()
                        }
                    }

                    // 每日目标：合并（取较大值）
                    if (data.dailyTargets.isNotEmpty()) {
                        val merged = dailyTargets.toMutableMap()
                        for ((k, v) in data.dailyTargets) { merged[k] = maxOf(merged[k] ?: 0, v) }
                        dailyTargets = merged
                        prefs.edit().putString("dailyTargets", gson.toJson(dailyTargets)).apply()
                    }

                    // 目标分数：合并云端数据（优先用完整 map，fallback 到 3 个固定字段）
                    val (p, e, s) = data.targetScores
                    val merged = targetScores.toMutableMap()
                    // 从云端拉取完整的 scores_map
                    val cloudScoresMap = try {
                        SupabaseClient.fetchTargetScoresMap()
                    } catch (ex: Exception) { emptyMap() }
                    for ((k, v) in cloudScoresMap) {
                        if (v > 0) merged[k] = maxOf(merged[k] ?: 0, v)
                    }
                    // 兼容：旧版 3 个固定字段
                    if (p > 0) merged["政治"] = maxOf(merged["政治"] ?: 0, p)
                    if (e > 0) merged["英语"] = maxOf(merged["英语"] ?: 0, e)
                    if (s > 0) merged["专业综合"] = maxOf(merged["专业综合"] ?: 0, s)
                    if (merged != targetScores) saveTargetScores(merged)

                    // AI 设置：云端有就用云端的
                    data.settings?.let { settings ->
                        val cloudApiKey = settings["api_key"] as? String
                        val cloudBaseUrl = settings["api_base_url"] as? String
                        val cloudModel = settings["model_name"] as? String
                        val cloudTextModel = settings["text_model_name"] as? String
                        val cloudAiEnabled = settings["ai_enabled"] as? Boolean
                        if (!cloudApiKey.isNullOrBlank()) apiKey = cloudApiKey
                        if (!cloudBaseUrl.isNullOrBlank()) apiBaseUrl = cloudBaseUrl
                        if (!cloudModel.isNullOrBlank()) modelName = cloudModel
                        if (cloudTextModel != null) AiService.textModelName = cloudTextModel
                        if (cloudAiEnabled != null) aiEnabled = cloudAiEnabled
                        saveApiConfig()
                    }

                    updateTodayCheckIn()
                    refreshCheckInStats()
                    cloudSyncing = false
                    updateLocalHash()
                    lastPulledHash = localDataHash
                    lastPushedHash = localDataHash

                    // 合并后推送回云端（确保云端也有本地独有的数据）
                    // 注意：全新安装时不删除云端数据（防止误删）
                    syncToCloud {
                        // 只有本地题目数量 > 云端的 50% 时才执行删除（防止全新安装误删）
                        val localIds = questions.map { it.id }.toSet()
                        val cloudOnlyIds = data.questions.map { it.id }.filter { it !in localIds }
                        if (cloudOnlyIds.isNotEmpty() && localIds.size > data.questions.size / 2) {
                            SupabaseClient.deleteQuestions(cloudOnlyIds)
                            Log.d("PsyMap-Sync", "从云端删除 ${cloudOnlyIds.size} 道本地已删除的题目")
                        } else if (cloudOnlyIds.isNotEmpty()) {
                            Log.d("PsyMap-Sync", "跳过云端删除：本地${localIds.size}题 vs 云端${data.questions.size}题，疑似全新安装")
                        }
                        SupabaseClient.pushAll(
                            questionBanks, questions, checkInRecords, dailyTargets,
                            targetScores["政治"] ?: 0, targetScores["英语"] ?: 0, targetScores["专业综合"] ?: 0,
                            studyPlans,
                            targetScoresMap = targetScores
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PsyMap-Sync", "syncFromCloud EXCEPTION: ${e.javaClass.simpleName}: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    cloudSyncing = false
                    cloudSyncMessage = "同步失败: ${e.message}"
                }
            }
        }
    }

    /** 全量推送到云端（首次迁移用） */
    fun pushToCloud(onResult: (String) -> Unit) {
        if (SupabaseClient.userId == null) { onResult("未登录云端"); return; }
        cloudSyncing = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                SupabaseClient.pushAll(
                    questionBanks, questions, checkInRecords, dailyTargets,
                    targetScores["政治"] ?: 0, targetScores["英语"] ?: 0, targetScores["专业综合"] ?: 0,
                    studyPlans,
                    targetScoresMap = targetScores
                )
                SupabaseClient.upsertSettings(apiKey, apiBaseUrl, modelName,
                    AiService.textModelName, aiEnabled)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    cloudSyncing = false
                    updateLocalHash()
                    lastPushedHash = localDataHash
                    onResult("推送成功！题库${questionBanks.size}个，题目${questions.size}道")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    cloudSyncing = false
                    onResult("推送失败: ${e.message}")
                }
            }
        }
    }

    /** 云端登录/注册 */
    fun cloudLogin(nickname: String, onResult: (Boolean, String) -> Unit) {
        val openId = currentUser.wechatOpenId
        val deviceId = try {
            android.provider.Settings.Secure.getString(
                getApplication<android.app.Application>().contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) { "" }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val uid = SupabaseClient.loginOrRegister(nickname, openId, deviceId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (uid != null) {
                        prefs.edit().putString("cloud_user_id", uid).apply()
                        onResult(true, "云端登录成功")
                    } else {
                        onResult(false, "登录失败")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "登录失败: ${e.message}")
                }
            }
        }
    }

    /** 获取同步码 */
    var syncCode by mutableStateOf("")
    fun fetchSyncCode() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val code = SupabaseClient.getSyncCode()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                syncCode = code ?: ""
            }
        }
    }

    /** 重新生成同步码（每次登录时调用） */
    fun regenerateSyncCode() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val code = SupabaseClient.regenerateSyncCode()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                syncCode = code ?: ""
            }
        }
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
        syncToCloud { SupabaseClient.upsertSettings(apiKey, apiBaseUrl, modelName, AiService.textModelName, aiEnabled) }
    }

    fun toggleAiEnabled(enabled: Boolean) {
        aiEnabled = enabled
        prefs.edit().putBoolean("aiEnabled", enabled).apply()
    }

    private fun saveBanks() {
        prefs.edit().putString("questionBanks", gson.toJson(questionBanks)).apply()
    }

    private fun saveQuestions() {
        invalidateQuestionsCache()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            questionDao.insertAll(questions.map { it.toEntity() })
        }
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
        syncToCloud { SupabaseClient.upsertBank(bank) }
        return bank
    }

    fun renameBankIfAdmin(bankId: String, newName: String) {
        if (currentUser.role != UserRole.ADMIN) return
        questionBanks = questionBanks.map {
            if (it.id == bankId) it.copy(name = newName) else it
        }
        saveBanks()
        syncToCloud { questionBanks.find { it.id == bankId }?.let { SupabaseClient.upsertBank(it) } }
    }

    fun deleteBank(bankId: String) {
        if (currentUser.role != UserRole.ADMIN) return
        val questionIds = questions.filter { it.bankId == bankId }.map { it.id }
        questions = questions.filter { it.bankId != bankId }
        questionBanks = questionBanks.filter { it.id != bankId }
        saveBanks()
        saveQuestions()
        syncToCloud {
            if (questionIds.isNotEmpty()) SupabaseClient.deleteQuestions(questionIds)
            SupabaseClient.deleteBank(bankId)
        }
    }

    fun getQuestionsForBank(bankId: String): List<Question> {
        return questionsCache.getOrPut(bankId) {
            questions.filter { it.bankId == bankId }.sortedByDescending { it.createdAt }
        }
    }

    // 题目缓存（按 bankId 分组），questions 变化时清空
    private var questionsCache = mutableMapOf<String, List<Question>>()

    fun invalidateQuestionsCache() {
        questionsCache.clear()
    }

    fun getQuestionCountForBank(bankId: String): Int {
        return questions.count { it.bankId == bankId }
    }

    fun addQuestion(bankId: String, content: String, answer: String, type: QuestionType,
                    options: List<String> = emptyList(), chapter: String = "",
                    tags: List<String> = emptyList(), isMemorize: Boolean = false,
                    explanation: String = "") {
        val q = Question(
            bankId = bankId,
            content = formatTextMarkdown(content),
            answer = formatTextMarkdown(answer),
            type = type,
            options = options, chapter = chapter, tags = tags, isMemorize = isMemorize,
            explanation = formatTextMarkdown(explanation)
        )
        questions = questions + q
        updateBankCount(bankId)
        saveQuestions()
        syncToCloud { SupabaseClient.upsertQuestion(q); questionBanks.find { it.id == bankId }?.let { SupabaseClient.upsertBank(it) } }
    }

    fun moveQuestionToBank(questionId: String, targetBankId: String) {
        val oldBankId = questions.find { it.id == questionId }?.bankId ?: return
        if (oldBankId == targetBankId) return

        questions = questions.map {
            if (it.id == questionId) it.copy(bankId = targetBankId) else it
        }
        updateBankCount(oldBankId)
        updateBankCount(targetBankId)
        saveQuestions()
        val movedQ = questions.find { it.id == questionId }
        if (movedQ != null) {
            syncToCloud { SupabaseClient.upsertQuestion(movedQ) }
        }
    }

    fun moveQuestionsToBank(questionIds: List<String>, targetBankId: String) {
        val affectedBankIds = mutableSetOf<String>()
        questions = questions.map { q ->
            if (q.id in questionIds && q.bankId != targetBankId) {
                affectedBankIds.add(q.bankId)
                q.copy(bankId = targetBankId)
            } else q
        }
        affectedBankIds.add(targetBankId)
        affectedBankIds.forEach { updateBankCount(it) }
        saveQuestions()
    }

    fun updateQuestion(questionId: String, content: String, answer: String, options: List<String> = emptyList(), explanation: String = "") {
        if (currentUser.role != UserRole.ADMIN) return
        val fmtContent = formatTextMarkdown(content)
        val fmtAnswer = formatTextMarkdown(answer)
        val fmtExplanation = formatTextMarkdown(explanation)
        questions = questions.map {
            if (it.id == questionId) it.copy(content = fmtContent, answer = fmtAnswer, options = options, explanation = fmtExplanation) else it
        }
        saveQuestions()
        syncToCloud { SupabaseClient.updateQuestionFields(questionId, mapOf("content" to fmtContent, "answer" to fmtAnswer, "options" to options, "explanation" to fmtExplanation)) }
    }

    fun updateQuestionType(questionId: String, type: QuestionType) {
        questions = questions.map {
            if (it.id == questionId) it.copy(type = type) else it
        }
        saveQuestions()
        syncToCloud { SupabaseClient.updateQuestionFields(questionId, mapOf("type" to type.name.lowercase())) }
    }

    fun updateQuestionChapter(questionId: String, chapter: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(chapter = chapter) else it
        }
        saveQuestions()
        syncToCloud { SupabaseClient.updateQuestionFields(questionId, mapOf("chapter" to chapter)) }
    }

    fun deleteQuestions(questionIds: Set<String>) {
        if (currentUser.role != UserRole.ADMIN) return
        val affectedBankIds = questions.filter { it.id in questionIds }.map { it.bankId }.toSet()
        questions = questions.filter { it.id !in questionIds }
        affectedBankIds.forEach { updateBankCount(it) }
        saveQuestions()
        // 从 Room DB 中也删除
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            questionDao.deleteByIds(questionIds.toList())
        }
        syncToCloud { SupabaseClient.deleteQuestions(questionIds.toList()) }
    }

    private fun updateBankCount(bankId: String) {
        val count = questions.count { it.bankId == bankId }
        questionBanks = questionBanks.map {
            if (it.id == bankId) it.copy(questionCount = count) else it
        }
        saveBanks()
        syncToCloud { questionBanks.find { it.id == bankId }?.let { SupabaseClient.upsertBank(it) } }
    }

    // ========== 记忆曲线筛选算法 ==========

    /** 计算单道题的复习紧迫度（0~105，越高越需要复习） */
    private fun calculateUrgency(q: Question): Double {
        val totalAttempts = q.correctCount + q.wrongCount
        val correctRate = q.correctRate

        // 理想复习间隔（天），基于艾宾浩斯遗忘曲线
        val idealInterval = when {
            totalAttempts == 0 -> 0.0
            correctRate < 0.4 -> 1.0
            correctRate < 0.6 -> 2.0
            correctRate < 0.75 -> 4.0
            correctRate < 0.85 -> 7.0
            correctRate < 0.95 -> 15.0
            else -> 30.0
        }

        val daysSinceLastStudy = if (q.lastStudiedAt > 0)
            (System.currentTimeMillis() - q.lastStudiedAt).toDouble() / 86400000.0
        else 999.0  // 从未学习过

        val overdue = daysSinceLastStudy - idealInterval

        // 基础分：正确率越低越高（0~40）
        val baseScore = (1.0 - correctRate) * 40.0
        // 逾期分：超过理想间隔越久越高（0~40）
        val overdueScore = (overdue * 5.0).coerceIn(0.0, 40.0)
        // 新题加分
        val newBonus = if (totalAttempts == 0) 15.0 else 0.0
        // 错题加分
        val wrongBonus = if (q.isInWrongBook) 10.0 else 0.0

        return baseScore + overdueScore + newBonus + wrongBonus
    }

    /** 按记忆曲线筛选题目，返回排序后的题目列表 */
    fun selectBySpacedRepetition(allQuestions: List<Question>, maxCount: Int = Int.MAX_VALUE): List<Question> {
        if (allQuestions.isEmpty()) return emptyList()

        val scored = allQuestions.map { it to calculateUrgency(it) }
            .sortedByDescending { it.second }

        // urgency > 0 的优先入选
        val urgent = scored.filter { it.second > 0 }
        // urgency <= 0 的有 5% 概率入选（避免完全不出现）
        val notUrgent = scored.filter { it.second <= 0 }.filter { Math.random() < 0.05 }

        val selected = (urgent + notUrgent).take(maxCount).map { it.first }
        return selected
    }

    // ========== 学习功能 ==========
    var studyQuestionIds by mutableStateOf(listOf<String>())

    fun startStudySession(bankId: String, shuffle: Boolean = false) {
        currentBankId = bankId
        currentQuestionIndex = 0
        sessionCorrectCount = 0
        sessionTotalCount = 0

        val allQuestions = getQuestionsForBank(bankId)
        // 用记忆曲线筛选并排序
        val selected = selectBySpacedRepetition(allQuestions)

        if (shuffle) {
            studyQuestionIds = selected.shuffled().map { it.id }
        } else {
            studyQuestionIds = selected.map { it.id }
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
                    isInWrongBook = !isCorrect,
                    correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                    wrongCount = if (!isCorrect) it.wrongCount + 1 else it.wrongCount,
                    lastStudiedAt = System.currentTimeMillis()
                )
            } else it
        }
        saveQuestions()

        // 同步题目状态到云端
        val updated = questions.find { it.id == questionId }
        if (updated != null) {
            syncToCloud {
                SupabaseClient.updateQuestionFields(questionId, mapOf(
                    "reviewCount" to updated.reviewCount, "isInWrongBook" to updated.isInWrongBook,
                    "correctCount" to updated.correctCount, "wrongCount" to updated.wrongCount
                ))
            }
        }

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
    fun gradeSubjectiveAnswer(question: Question, userAnswer: String, isHandwritten: Boolean = false) {
        aiGradeResult = ""
        aiGradeScore = -1

        if (question.answer.isBlank()) {
            aiGradeScore = -1
            aiGradeResult = "该题暂无标准答案，无法自动评分"
            submitAnswer(question.id, userAnswer, false)
            return
        }

        val correctAnswer = question.answer.trim()
        val userAns = userAnswer.trim()

        if (userAns.isBlank()) {
            aiGradeScore = 0
            aiGradeResult = "未作答"
            return
        }

        // 计算卷面分（手写答案时有效，最高20分）
        val neatnessScore = if (isHandwritten) {
            val charCount = userAns.replace(Regex("\\s+"), "").length
            val lineCount = userAns.lines().filter { it.isNotBlank() }.size
            when {
                charCount > 80 && lineCount > 3 -> 18  // 字迹清晰、内容充实
                charCount > 50 -> 15
                charCount > 30 -> 12
                charCount > 15 -> 8
                else -> 5  // 字迹潦草或内容过少
            }
        } else 0
        val contentMaxScore = if (isHandwritten) 80 else 100

        // 计算关键词覆盖率
        val punctuationRegex = Regex("[，。、；：\\u201c\\u201d\\u2018\\u2019（）\\[\\]【】\\s\\n\\r.,;:\"'()]+")
        val correctKeywords = correctAnswer.replace(punctuationRegex, " ").split(" ").filter { it.length >= 2 }.distinct()
        val userText = userAns.replace(punctuationRegex, " ")

        if (correctKeywords.isEmpty()) {
            val isCorrect = userAns.contains(correctAnswer, ignoreCase = true) || correctAnswer.contains(userAns, ignoreCase = true)
            val contentScore = if (isCorrect) 80 else 30
            aiGradeScore = if (isHandwritten) ((contentScore * contentMaxScore / 100) + neatnessScore).coerceIn(0, 100) else contentScore
            aiGradeResult = if (isCorrect) "答案基本匹配" else "答案与标准答案差异较大"
            if (isHandwritten) aiGradeResult += "\n📝 卷面分: $neatnessScore/20"
            submitAnswer(question.id, userAnswer, aiGradeScore >= 60)
            return
        }

        val matchedCount = correctKeywords.count { keyword -> userText.contains(keyword, ignoreCase = true) }
        val coverageRate = matchedCount.toFloat() / correctKeywords.size
        val contentScore = (coverageRate * contentMaxScore).toInt().coerceIn(0, contentMaxScore)
        aiGradeScore = (contentScore + neatnessScore).coerceIn(0, 100)

        val matchedKeywords = correctKeywords.filter { userText.contains(it, ignoreCase = true) }
        val missedKeywords = correctKeywords.filter { !userText.contains(it, ignoreCase = true) }

        val feedback = StringBuilder()
        feedback.appendLine("📊 踩分点: ${matchedCount}/${correctKeywords.size}")
        if (matchedKeywords.isNotEmpty()) feedback.appendLine("✅ 命中: ${matchedKeywords.take(10).joinToString("、")}")
        if (missedKeywords.isNotEmpty()) feedback.appendLine("❌ 缺失: ${missedKeywords.take(10).joinToString("、")}")
        if (isHandwritten) {
            val neatLabel = when { neatnessScore >= 15 -> "字迹清晰" ; neatnessScore >= 10 -> "基本可读" ; else -> "字迹较潦草" }
            feedback.appendLine("📝 卷面分: $neatnessScore/20（$neatLabel）")
            feedback.appendLine("🎯 总分 = 内容${contentScore} + 卷面${neatnessScore}")
        }
        aiGradeResult = feedback.toString().trim()
        submitAnswer(question.id, userAnswer, aiGradeScore >= 60)
    }

    // 导入结果提示
    var importResultMessage by mutableStateOf("")

    // ========== 拍照识别题目（ML Kit 本地OCR + AI 结构化） ==========
    fun recognizeAndImport(bitmap: Bitmap, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _isLoading.value = true
        _loadingMessage.value = "正在识别文字..."
        importResultMessage = ""

        // 始终使用 ML Kit 本地 OCR（快速、免费），然后用 AI 结构化
        recognizeAndImportLocal(bitmap, bankId, questionType, tagFrequent, tagMemorize)
    }

    // 本地 ML Kit OCR + AI/本地解析
    private fun recognizeAndImportLocal(bitmap: Bitmap, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        _loadingMessage.value = "正在识别文字..."
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
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
                Log.d("PsyMap-OCR", "ML Kit OCR完成, 文字长度: ${ocrText.length}")

                // 始终尝试 AI 结构化（文本处理消耗极小，且效果远优于本地解析）
                // 仅在 API Key 为空或文字超长时才降级
                if (apiKey.isBlank() || ocrText.length > 6000) {
                    if (ocrText.length > 6000) Log.d("PsyMap-OCR", "文字过长(${ocrText.length}字)，使用本地解析")
                    parseAndImportLocally(ocrText, bankId, questionType, tagFrequent, tagMemorize)
                    return@addOnSuccessListener
                }

                // AI 结构化
                _loadingMessage.value = "正在分析题目结构..."
                val prompt = """将以下OCR文字整理为题目JSON数组。

核心原则——正确区分"题目"和"答案"：
- 题目以主题号开头：如"1."、"4."、"12."或"一、"、"二、"
- 题目之后的所有内容都是该题的答案，直到遇到下一个主题号
- 答案中的子编号如(1)(2)(3)、①②③是答案要点，不是新题目！
- 一道题的答案可能有多段多个要点，全部归入同一道题的answer

举例："4. 成就目标理论  德维克区分了两种能力内隐观：(1) 能力实体观... (2) 能力增长观..."
→ 1道题，question="成就目标理论"，answer包含全部(1)(2)内容

字段：
- question: 题目（去掉题号，不含答案）
- answer: 完整答案，markdown格式（**加粗**关键词，\n换行）。选择题只填字母。无答案填""
- options: 选择题选项数组，非选择题填[]
- type: single_choice/multi_choice/short_answer/essay/case_analysis
- explanation: 解析（无则""）
- chapter: 章节名（无则""）
只返回JSON数组，不要代码块包裹。"""

                AiService.chatCompletion(prompt, ocrText, { aiResult ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            var s = aiResult.trim()
                            val codeBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
                            val match = codeBlockRegex.find(s)
                            if (match != null) s = match.groupValues[1].trim()

                            val list = com.google.gson.Gson().fromJson<List<Map<String, Any>>>(s,
                                object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type)
                            if (list.isNullOrEmpty()) {
                                parseAndImportLocally(ocrText, bankId, questionType, tagFrequent, tagMemorize)
                            } else {
                                importAiResults(list, bankId, questionType, tagFrequent, tagMemorize)
                            }
                        } catch (e: Exception) {
                            Log.w("PsyMap-OCR", "AI解析失败，降级本地: ${e.message}")
                            parseAndImportLocally(ocrText, bankId, questionType, tagFrequent, tagMemorize)
                        }
                    }
                }, { error ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Log.w("PsyMap-OCR", "AI请求失败，降级本地: $error")
                        parseAndImportLocally(ocrText, bankId, questionType, tagFrequent, tagMemorize)
                    }
                })
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                importResultMessage = "识别失败: ${e.message}"
            }
    }

    // 导入 AI 结构化结果
    private fun importAiResults(list: List<Map<String, Any>>, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        var count = 0
        for (item in list) {
            val question = item["question"] as? String ?: ""
            if (question.isBlank()) continue
            val answer = item["answer"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val options = (item["options"] as? List<String>) ?: emptyList()
            val explanation = item["explanation"] as? String ?: ""
            val typeStr = item["type"] as? String ?: ""
            val type = if (questionType != null) questionType else when (typeStr.lowercase()) {
                "single_choice" -> QuestionType.SINGLE_CHOICE
                "multi_choice" -> QuestionType.MULTI_CHOICE
                "short_answer" -> QuestionType.SHORT_ANSWER
                "essay" -> QuestionType.ESSAY
                "case_analysis" -> QuestionType.CASE_ANALYSIS
                "comprehensive" -> QuestionType.COMPREHENSIVE
                else -> if (options.isNotEmpty()) QuestionType.SINGLE_CHOICE else QuestionType.SHORT_ANSWER
            }
            addQuestion(bankId, question.trim(), answer.trim(), type,
                options = options, isMemorize = tagMemorize, explanation = explanation.trim())
            val lastQ = questions.lastOrNull()
            if (lastQ != null && tagFrequent) toggleFrequent(lastQ.id)
            count++
        }
        _isLoading.value = false
        importResultMessage = if (count > 0) "成功导入 $count 道题目" else "识别到文字但未解析出题目"
    }

    // 纯本地正则解析（最终降级方案）
    private fun parseAndImportLocally(ocrText: String, bankId: String, questionType: QuestionType? = null, tagFrequent: Boolean = false, tagMemorize: Boolean = true) {
        val defaultType = questionType ?: QuestionType.SHORT_ANSWER
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var count = 0
        var currentQuestion = ""
        var currentAnswer = ""
        var currentExplanation = ""
        var currentOptions = mutableListOf<String>()
        var currentChapter = ""
        var currentSectionType: QuestionType? = null
        var section = "none" // none, question, options, answer, explanation

        // 题号模式：1. 1、 1) （1） 第1题
        val questionNumRegex = Regex("^(\\d{1,3})[.、）\\)\\.]\\s*(.+)")
        val questionNumRegex2 = Regex("^（(\\d{1,3})）\\s*(.+)")
        val questionNumRegex3 = Regex("^第(\\d{1,3})题[.、：:]?\\s*(.*)")
        // 选项模式：A. A、 A) A:（支持大小写A-I）
        val optionRegex = Regex("^([A-Ia-i])[.、）\\):\\s]\\s*(.+)")
        // 答案模式
        val answerRegex = Regex("^(答案|答|参考答案|标准答案)[：:．.]?\\s*(.*)", RegexOption.IGNORE_CASE)
        // 解析模式
        val explanationRegex = Regex("^(解析|详解|分析|解答|解题思路)[：:．.]?\\s*(.*)", RegexOption.IGNORE_CASE)
        // 章节标题
        val chapterRegex = Regex("^第[一二三四五六七八九十百\\d]+[章编节部分].*")
        // 题型分类标题
        val sectionTypeRegex = Regex("^[一二三四五六七八九十]+[、.．]\\s*[（(]?\\s*(选择|单选|多选|简答|论述|案例分析|综合|填空|名词解释|判断|问答).*")
        // 纯标题行（不含题目内容的行，如"选择题"、"简答题"等）
        val pureTitleRegex = Regex("^(选择题|单选题|多选题|简答题|论述题|案例分析题|综合题|名词解释|判断题|问答题)$")

        fun saveQ() {
            if (currentQuestion.isNotBlank() && currentQuestion.length >= 4) {
                val type = if (questionType != null) questionType
                    else if (currentOptions.isNotEmpty()) {
                        if (currentAnswer.length > 1 && currentAnswer.replace(Regex("[,，\\s]"), "").length > 1)
                            QuestionType.MULTI_CHOICE else QuestionType.SINGLE_CHOICE
                    }
                    else currentSectionType ?: defaultType
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
            // 1. 检测章节标题（跳过，不作为题目）
            if (chapterRegex.matches(line)) {
                saveQ(); currentChapter = line; continue
            }
            // 2. 检测题型分类标题
            if (sectionTypeRegex.matches(line) || pureTitleRegex.matches(line)) {
                saveQ()
                currentSectionType = when {
                    line.contains("单选") || (line.contains("选择") && !line.contains("多选")) -> QuestionType.SINGLE_CHOICE
                    line.contains("多选") -> QuestionType.MULTI_CHOICE
                    line.contains("简答") || line.contains("名词解释") || line.contains("问答") -> QuestionType.SHORT_ANSWER
                    line.contains("论述") -> QuestionType.ESSAY
                    line.contains("案例") -> QuestionType.CASE_ANALYSIS
                    line.contains("综合") -> QuestionType.COMPREHENSIVE
                    line.contains("判断") -> QuestionType.SINGLE_CHOICE
                    else -> null
                }
                continue
            }
            // 3. 检测纯大纲/目录行（如"一、人格的定义"这种不是题目的行）
            // 如果当前没有在答案/解析模式，且行以中文数字+顿号开头但不是题型标题，可能是大纲
            val isOutlineHeading = line.matches(Regex("^[一二三四五六七八九十]+[、.]\\s*.+")) && !sectionTypeRegex.matches(line)

            // 4. 检测答案行
            val ansMatch = answerRegex.find(line)
            if (ansMatch != null) {
                section = "answer"
                currentAnswer = ansMatch.groupValues[2]
                continue
            }
            // 5. 检测解析行
            val expMatch = explanationRegex.find(line)
            if (expMatch != null) {
                section = "explanation"
                currentExplanation = expMatch.groupValues[2]
                continue
            }
            // 6. 检测选项行（仅在题目或选项模式下）
            val optMatch = optionRegex.find(line)
            if (optMatch != null && section != "answer" && section != "explanation") {
                currentOptions.add(line)
                section = "options"
                continue
            }
            // 7. 检测新题目（数字题号）
            val qMatch = questionNumRegex.find(line) ?: questionNumRegex2.find(line) ?: questionNumRegex3.find(line)
            if (qMatch != null) {
                saveQ()
                currentQuestion = qMatch.groupValues.last()
                section = "question"
                continue
            }
            // 8. 大纲标题行：如果当前没有正在编辑的题目，把大纲标题当作简答题的题目
            if (isOutlineHeading && currentQuestion.isBlank()) {
                saveQ()
                currentQuestion = line.replace(Regex("^[一二三四五六七八九十]+[、.]\\s*"), "")
                section = "question"
                continue
            }
            // 9. 续行：根据当前 section 追加内容
            when (section) {
                "answer" -> currentAnswer += "\n$line"
                "explanation" -> currentExplanation += "\n$line"
                "question", "options" -> {
                    // 如果当前有题目，追加到题目内容（可能是多行题目）
                    if (currentQuestion.isNotBlank()) currentQuestion += "\n$line"
                }
                else -> {
                    // 没有明确的 section，如果行看起来像是内容（不是太短），当作新题目
                    if (line.length >= 6) {
                        saveQ()
                        currentQuestion = line
                        section = "question"
                    }
                }
            }
        }
        saveQ()

        _isLoading.value = false
        importResultMessage = if (count > 0) "成功导入 $count 道题目（本地解析）" else "识别到文字但未解析出题目，建议开启AI功能"
    }

    // 为文本自动添加 markdown 格式（编号加粗、换行、关键词高亮）
    private fun formatTextMarkdown(text: String): String {
        if (text.isBlank()) return text
        // 如果文本已经包含 markdown 格式标记，不重复处理
        if (text.contains("**") || text.contains("### ")) return text

        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size <= 1 && !lines.firstOrNull().orEmpty().matches(Regex(".*\\d+[.、）].*"))) return text

        val result = StringBuilder()
        val numberedPointRegex = Regex("^(\\d+)[.、）\\)．]\\s*(.*)")
        val parenPointRegex = Regex("^[（(](\\d+)[）)]\\s*(.*)")
        val chineseNumRegex = Regex("^([一二三四五六七八九十]+)[、.．]\\s*(.*)")
        var hasStructure = false

        for (line in lines) {
            // 数字编号要点：1. xxx  2. xxx
            val numMatch = numberedPointRegex.find(line) ?: parenPointRegex.find(line)
            if (numMatch != null) {
                hasStructure = true
                val num = numMatch.groupValues[1]
                val content = numMatch.groupValues[2]
                val colonIdx = content.indexOfFirst { it == '：' || it == ':' }
                if (colonIdx in 1..30) {
                    val title = content.substring(0, colonIdx)
                    val rest = content.substring(colonIdx)
                    result.append("\n**$num. $title**$rest\n")
                } else if (content.length <= 25) {
                    result.append("\n**$num. $content**\n")
                } else {
                    result.append("\n$num. $content\n")
                }
                continue
            }
            // 中文编号要点：一、xxx  二、xxx
            val cnMatch = chineseNumRegex.find(line)
            if (cnMatch != null) {
                hasStructure = true
                val num = cnMatch.groupValues[1]
                val content = cnMatch.groupValues[2]
                result.append("\n### ${num}、$content\n")
                continue
            }
            // 普通行
            result.append("\n$line\n")
        }

        return if (hasStructure) result.toString().trim() else text
    }

    // ========== 错题本 & 收藏本 ==========
    fun getWrongQuestions(): List<Question> = questions.filter { it.isInWrongBook }.sortedByDescending { it.createdAt }

    fun getFavoriteQuestions(): List<Question> = questions.filter { it.isInFavorites }.sortedByDescending { it.createdAt }

    fun toggleFavorite(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isInFavorites = !it.isInFavorites) else it
        }
        saveQuestions()
        syncToCloud { val q = questions.find { it.id == questionId }; if (q != null) SupabaseClient.updateQuestionFields(questionId, mapOf("isInFavorites" to q.isInFavorites)) }
    }

    fun toggleFrequent(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isFrequent = !it.isFrequent) else it
        }
        saveQuestions()
        syncToCloud { val q = questions.find { it.id == questionId }; if (q != null) SupabaseClient.updateQuestionFields(questionId, mapOf("isFrequent" to q.isFrequent)) }
    }

    fun toggleMemorize(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isMemorize = !it.isMemorize) else it
        }
        saveQuestions()
        syncToCloud { val q = questions.find { it.id == questionId }; if (q != null) SupabaseClient.updateQuestionFields(questionId, mapOf("isMemorize" to q.isMemorize)) }
    }

    fun markTtsGenerated(questionIds: List<String>) {
        questions = questions.map {
            if (it.id in questionIds) it.copy(ttsGenerated = true) else it
        }
        saveQuestions()
        syncToCloud { for (id in questionIds) SupabaseClient.updateQuestionFields(id, mapOf("ttsGenerated" to true)) }
    }

    fun removeFromWrongBook(questionId: String) {
        questions = questions.map {
            if (it.id == questionId) it.copy(isInWrongBook = false) else it
        }
        saveQuestions()
        syncToCloud { SupabaseClient.updateQuestionFields(questionId, mapOf("isInWrongBook" to false)) }
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

        // 同步打卡到云端
        syncToCloud { SupabaseClient.upsertCheckIn(updated) }

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
        // 先检查今天是否已完成
        val todayStr = dateFormat.format(cal.time)
        val todayRecord = checkInRecords.find { it.date == todayStr }
        if (todayRecord != null && isDayCheckedIn(todayRecord)) {
            count++
        }
        // 从昨天开始往回数连续天数
        cal.add(Calendar.DAY_OF_YEAR, -1)
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
        syncToCloud { SupabaseClient.upsertDailyTargets(targets) }
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
            "targetScoresMap" to targetScores,
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

            // 兼容旧版备份格式
            
            
            run { val sm = mutableMapOf<String, Int>(); val p = (map["targetPoliticsScore"] as? Double)?.toInt() ?: 0; val e = (map["targetEnglishScore"] as? Double)?.toInt() ?: 0; val s = (map["targetPsyScore"] as? Double)?.toInt() ?: 0; if (p > 0) sm["政治"] = p; if (e > 0) sm["英语"] = e; if (s > 0) sm["专业综合"] = s; @Suppress("UNCHECKED_CAST") val newMap = (map["targetScoresMap"] as? Map<String, Double>)?.mapValues { it.value.toInt() }; if (newMap != null) sm.putAll(newMap); saveTargetScores(sm) }

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

            // 兼容旧版备份格式
            
            
            run { val sm = mutableMapOf<String, Int>(); val p = (map["targetPoliticsScore"] as? Double)?.toInt() ?: 0; val e = (map["targetEnglishScore"] as? Double)?.toInt() ?: 0; val s = (map["targetPsyScore"] as? Double)?.toInt() ?: 0; if (p > 0) sm["政治"] = p; if (e > 0) sm["英语"] = e; if (s > 0) sm["专业综合"] = s; @Suppress("UNCHECKED_CAST") val newMap = (map["targetScoresMap"] as? Map<String, Double>)?.mapValues { it.value.toInt() }; if (newMap != null) sm.putAll(newMap); saveTargetScores(sm) }

            "恢复成功！\n题库: ${questionBanks.size}个, 题目: ${questions.size}道\n错题: ${questions.count { it.isInWrongBook }}道, 收藏: ${questions.count { it.isInFavorites }}道\n打卡: ${checkInRecords.size}天"
        } catch (e: Exception) {
            "恢复失败: ${e.message}"
        }
    }

    // ========== 目标分数 ==========
    fun saveTargetScores(scores: Map<String, Int>) {
        targetScores = scores
        prefs.edit().putString("targetScoresMap", gson.toJson(scores)).apply()
        // 云端同步：既传 3 个固定字段（兼容旧版），也传完整的 map（支持灵活科目）
        val p = scores["政治"] ?: 0
        val e = scores["英语"] ?: 0
        val s = scores["专业综合"] ?: 0
        syncToCloud { SupabaseClient.upsertTargetScores(p, e, s, scores) }
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
    private var lastSearchKeyword: String = ""

    fun searchQuestions(keyword: String) {
        lastSearchKeyword = keyword
        searchResults = if (keyword.isBlank()) emptyList()
        else questions.filter {
            it.content.contains(keyword, ignoreCase = true) ||
            it.answer.contains(keyword, ignoreCase = true) ||
            it.chapter.contains(keyword, ignoreCase = true) ||
            it.options.any { opt -> opt.contains(keyword, ignoreCase = true) } ||
            it.explanation.contains(keyword, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(keyword, ignoreCase = true) }
        }
    }

    fun refreshSearchResults() {
        if (lastSearchKeyword.isNotBlank()) {
            searchQuestions(lastSearchKeyword)
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
        // 自动连接云端并刷新同步码
        cloudLogin(nickname) { success, _ ->
            if (success) {
                regenerateSyncCode()
                syncFromCloud()
            }
        }
    }

    val isAdmin: Boolean get() = currentUser.role == UserRole.ADMIN

    // 实时计算打卡天数（不依赖 currentUser 缓存）
    val totalCheckedDays: Int get() = checkInRecords.count { isDayCheckedIn(it) }
    val consecutiveCheckedDays: Int get() = calculateConsecutiveDays()
}
