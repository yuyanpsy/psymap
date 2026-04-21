package com.psymap.app

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Supabase REST API 客户端
 * 直接使用 OkHttp + Gson 调用 PostgREST API，无需额外 SDK
 */
object SupabaseClient {
    // ========== 配置（请替换为你的 Supabase 项目信息） ==========
    var supabaseUrl = "https://edzsmjegnkrbedqpotgu.supabase.co"
    var supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU"

    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // 当前登录用户的 Supabase user ID
    var userId: String? = null

    // ========== 通用请求方法 ==========
    private fun buildUrl(table: String, query: String = ""): String {
        return "$supabaseUrl/rest/v1/$table$query"
    }

    private fun baseHeaders(): Headers {
        return Headers.Builder()
            .add("apikey", supabaseKey)
            .add("Authorization", "Bearer $supabaseKey")
            .add("Content-Type", "application/json")
            .add("Prefer", "return=representation")
            .build()
    }

    private suspend fun get(table: String, query: String = ""): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl(table, query))
            .headers(baseHeaders())
            .get()
            .build()
        try {
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }

    private suspend fun post(table: String, json: String, query: String = ""): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl(table, query))
            .headers(baseHeaders())
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }

    private suspend fun upsert(table: String, json: String, onConflict: String = ""): String? = withContext(Dispatchers.IO) {
        val headers = Headers.Builder()
            .add("apikey", supabaseKey)
            .add("Authorization", "Bearer $supabaseKey")
            .add("Content-Type", "application/json")
            .add("Prefer", "return=representation,resolution=merge-duplicates")
            .build()
        val url = if (onConflict.isNotEmpty()) buildUrl(table, "?on_conflict=$onConflict") else buildUrl(table)
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }

    private suspend fun patch(table: String, query: String, json: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl(table, query))
            .headers(baseHeaders())
            .patch(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }

    private suspend fun delete(table: String, query: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildUrl(table, query))
            .headers(baseHeaders())
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    // ========== 用户登录/注册 ==========
    suspend fun loginOrRegister(nickname: String, wechatOpenId: String = ""): String? {
        // 优先用 wechat_open_id 查找
        if (wechatOpenId.isNotBlank()) {
            val existing = get("users", "?wechat_open_id=eq.$wechatOpenId&select=*&limit=1")
            val list = try { gson.fromJson<List<Map<String, Any>>>(existing, object : TypeToken<List<Map<String, Any>>>() {}.type) } catch (e: Exception) { null }
            if (!list.isNullOrEmpty()) {
                userId = list[0]["id"] as? String
                return userId
            }
        }
        // 再用 nickname 查找
        val existing = get("users", "?nickname=eq.$nickname&select=*&limit=1")
        val list = try { gson.fromJson<List<Map<String, Any>>>(existing, object : TypeToken<List<Map<String, Any>>>() {}.type) } catch (e: Exception) { null }
        if (!list.isNullOrEmpty()) {
            userId = list[0]["id"] as? String
            // 更新 wechat_open_id（如果之前没有）
            if (wechatOpenId.isNotBlank() && (list[0]["wechat_open_id"] as? String).isNullOrBlank()) {
                patch("users", "?id=eq.$userId", gson.toJson(mapOf("wechat_open_id" to wechatOpenId)))
            }
            return userId
        }
        // 创建新用户
        val body = gson.toJson(mapOf("nickname" to nickname, "role" to "admin", "wechat_open_id" to wechatOpenId))
        val result = post("users", body, "?select=*")
        val created = try { gson.fromJson<List<Map<String, Any>>>(result, object : TypeToken<List<Map<String, Any>>>() {}.type) } catch (e: Exception) { null }
        userId = created?.firstOrNull()?.get("id") as? String
        return userId
    }

    // ========== 获取同步码 ==========
    suspend fun getSyncCode(): String? {
        val uid = userId ?: return null
        val json = get("users", "?id=eq.$uid&select=sync_code&limit=1") ?: return null
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            list.firstOrNull()?.get("sync_code") as? String
        } catch (e: Exception) { null }
    }

    // ========== 重新生成同步码 ==========
    suspend fun regenerateSyncCode(): String? {
        val uid = userId ?: return null
        val newCode = String.format("%06d", (Math.random() * 1000000).toInt())
        patch("users", "?id=eq.$uid", gson.toJson(mapOf("sync_code" to newCode)))
        return newCode
    }

    // ========== 题库 CRUD ==========
    suspend fun upsertBank(bank: QuestionBank) {
        val uid = userId ?: return
        val map = mapOf(
            "id" to bank.id, "user_id" to uid, "name" to bank.name,
            "subject" to bank.subject.name.lowercase(), "question_count" to bank.questionCount,
            "created_at" to bank.createdAt
        )
        upsert("banks", gson.toJson(listOf(map)), "id")
    }

    suspend fun deleteBank(bankId: String) {
        val uid = userId ?: return
        delete("banks", "?id=eq.$bankId&user_id=eq.$uid")
    }

    suspend fun fetchBanks(): List<QuestionBank> {
        val uid = userId ?: return emptyList()
        val json = get("banks", "?user_id=eq.$uid&order=created_at") ?: return emptyList()
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            list.map { row ->
                QuestionBank(
                    id = row["id"] as? String ?: "",
                    name = row["name"] as? String ?: "",
                    subject = try { Subject.valueOf((row["subject"] as? String ?: "general_psy").uppercase()) } catch (e: Exception) { Subject.GENERAL_PSY },
                    questionCount = (row["question_count"] as? Double)?.toInt() ?: 0,
                    createdAt = (row["created_at"] as? Double)?.toLong() ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    // ========== 题目 CRUD ==========
    suspend fun upsertQuestions(questions: List<Question>) {
        val uid = userId ?: return
        val batch = questions.map { q -> questionToMap(q, uid) }
        for (i in batch.indices step 500) {
            upsert("questions", gson.toJson(batch.subList(i, minOf(i + 500, batch.size))), "id")
        }
    }

    suspend fun upsertQuestion(q: Question) {
        val uid = userId ?: return
        upsert("questions", gson.toJson(listOf(questionToMap(q, uid))), "id")
    }

    suspend fun updateQuestionFields(questionId: String, fields: Map<String, Any?>) {
        val uid = userId ?: return
        val dbFields = mutableMapOf<String, Any?>()
        val keyMap = mapOf(
            "reviewCount" to "review_count", "isInWrongBook" to "is_in_wrong_book",
            "isInFavorites" to "is_in_favorites", "correctCount" to "correct_count",
            "wrongCount" to "wrong_count", "isFrequent" to "is_frequent",
            "isMemorize" to "is_memorize", "ttsGenerated" to "tts_generated",
            "content" to "content", "answer" to "answer", "explanation" to "explanation",
            "options" to "options", "type" to "type"
        )
        for ((k, v) in fields) {
            keyMap[k]?.let { dbFields[it] = v }
        }
        if (dbFields.isNotEmpty()) {
            patch("questions", "?id=eq.$questionId&user_id=eq.$uid", gson.toJson(dbFields))
        }
    }

    suspend fun deleteQuestions(ids: List<String>) {
        val uid = userId ?: return
        // PostgREST in 查询
        val idList = ids.joinToString(",") { "\"$it\"" }
        delete("questions", "?id=in.($idList)&user_id=eq.$uid")
    }

    suspend fun fetchQuestions(): List<Question> {
        val uid = userId ?: return emptyList()
        val json = get("questions", "?user_id=eq.$uid") ?: return emptyList()
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            list.map { questionFromMap(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun questionToMap(q: Question, uid: String): Map<String, Any?> = mapOf(
        "id" to q.id, "user_id" to uid, "bank_id" to q.bankId, "content" to q.content,
        "answer" to q.answer, "explanation" to q.explanation, "options" to q.options,
        "type" to q.type.name.lowercase(), "chapter" to q.chapter,
        "review_count" to q.reviewCount, "is_in_wrong_book" to q.isInWrongBook,
        "is_in_favorites" to q.isInFavorites, "correct_count" to q.correctCount,
        "wrong_count" to q.wrongCount, "is_frequent" to q.isFrequent,
        "is_memorize" to q.isMemorize, "tts_generated" to q.ttsGenerated
    )

    @Suppress("UNCHECKED_CAST")
    private fun questionFromMap(row: Map<String, Any>): Question {
        val typeStr = row["type"] as? String ?: "short_answer"
        val qType = try { QuestionType.valueOf(typeStr.uppercase()) } catch (e: Exception) { QuestionType.SHORT_ANSWER }
        val optionsRaw = row["options"]
        val options: List<String> = when (optionsRaw) {
            is List<*> -> optionsRaw.filterIsInstance<String>()
            else -> emptyList()
        }
        return Question(
            id = row["id"] as? String ?: "",
            bankId = row["bank_id"] as? String ?: "",
            content = row["content"] as? String ?: "",
            answer = row["answer"] as? String ?: "",
            explanation = row["explanation"] as? String ?: "",
            options = options,
            type = qType,
            chapter = row["chapter"] as? String ?: "",
            reviewCount = (row["review_count"] as? Double)?.toInt() ?: 0,
            isInWrongBook = row["is_in_wrong_book"] as? Boolean ?: false,
            isInFavorites = row["is_in_favorites"] as? Boolean ?: false,
            correctCount = (row["correct_count"] as? Double)?.toInt() ?: 0,
            wrongCount = (row["wrong_count"] as? Double)?.toInt() ?: 0,
            isFrequent = row["is_frequent"] as? Boolean ?: false,
            isMemorize = row["is_memorize"] as? Boolean ?: false,
            ttsGenerated = row["tts_generated"] as? Boolean ?: false
        )
    }

    // ========== 打卡 CRUD ==========
    suspend fun upsertCheckIn(checkIn: DailyCheckIn) {
        val uid = userId ?: return
        val map = mapOf(
            "user_id" to uid, "date" to checkIn.date,
            "completed_count" to checkIn.completedCount, "target_count" to checkIn.targetCount,
            "study_minutes" to checkIn.studyMinutes,
            "bank_progress" to checkIn.bankProgress, "bank_correct" to checkIn.bankCorrect,
            "bank_studied_ids" to checkIn.bankStudiedIds, "bank_correct_ids" to checkIn.bankCorrectIds
        )
        upsert("check_ins", gson.toJson(listOf(map)), "user_id,date")
    }

    suspend fun fetchCheckIns(): List<DailyCheckIn> {
        val uid = userId ?: return emptyList()
        val json = get("check_ins", "?user_id=eq.$uid&order=date.desc") ?: return emptyList()
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            list.map { row -> checkInFromMap(row) }
        } catch (e: Exception) { emptyList() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkInFromMap(row: Map<String, Any>): DailyCheckIn {
        return DailyCheckIn(
            date = row["date"] as? String ?: "",
            completedCount = (row["completed_count"] as? Double)?.toInt() ?: 0,
            targetCount = (row["target_count"] as? Double)?.toInt() ?: 0,
            studyMinutes = (row["study_minutes"] as? Double)?.toInt() ?: 0,
            bankProgress = (row["bank_progress"] as? Map<String, Double>)?.mapValues { it.value.toInt() } ?: emptyMap(),
            bankCorrect = (row["bank_correct"] as? Map<String, Double>)?.mapValues { it.value.toInt() } ?: emptyMap(),
            bankStudiedIds = (row["bank_studied_ids"] as? Map<String, List<String>>) ?: emptyMap(),
            bankCorrectIds = (row["bank_correct_ids"] as? Map<String, List<String>>) ?: emptyMap()
        )
    }

    // ========== 每日目标 & 分数 & 设置 ==========
    suspend fun upsertDailyTargets(targets: Map<String, Int>) {
        val uid = userId ?: return
        upsert("daily_targets", gson.toJson(listOf(mapOf("user_id" to uid, "targets" to targets))), "user_id")
    }

    suspend fun fetchDailyTargets(): Map<String, Int> {
        val uid = userId ?: return emptyMap()
        val json = get("daily_targets", "?user_id=eq.$uid&select=targets&limit=1") ?: return emptyMap()
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            @Suppress("UNCHECKED_CAST")
            (list.firstOrNull()?.get("targets") as? Map<String, Double>)?.mapValues { it.value.toInt() } ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    suspend fun upsertTargetScores(politics: Int, english: Int, psy: Int) {
        val uid = userId ?: return
        upsert("target_scores", gson.toJson(listOf(mapOf("user_id" to uid, "politics" to politics, "english" to english, "psy" to psy))), "user_id")
    }

    suspend fun fetchTargetScores(): Triple<Int, Int, Int> {
        val uid = userId ?: return Triple(0, 0, 0)
        val json = get("target_scores", "?user_id=eq.$uid&limit=1") ?: return Triple(0, 0, 0)
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            val row = list.firstOrNull() ?: return Triple(0, 0, 0)
            Triple(
                (row["politics"] as? Double)?.toInt() ?: 0,
                (row["english"] as? Double)?.toInt() ?: 0,
                (row["psy"] as? Double)?.toInt() ?: 0
            )
        } catch (e: Exception) { Triple(0, 0, 0) }
    }

    suspend fun upsertSettings(apiKey: String, apiBaseUrl: String, modelName: String, textModelName: String, aiEnabled: Boolean) {
        val uid = userId ?: return
        upsert("user_settings", gson.toJson(listOf(mapOf(
            "user_id" to uid, "api_key" to apiKey, "api_base_url" to apiBaseUrl,
            "model_name" to modelName, "text_model_name" to textModelName, "ai_enabled" to aiEnabled
        ))), "user_id")
    }

    suspend fun fetchSettings(): Map<String, Any>? {
        val uid = userId ?: return null
        val json = get("user_settings", "?user_id=eq.$uid&limit=1") ?: return null
        return try {
            val list = gson.fromJson<List<Map<String, Any>>>(json, object : TypeToken<List<Map<String, Any>>>() {}.type)
            list.firstOrNull()
        } catch (e: Exception) { null }
    }

    // ========== 合并推送（upsert，不删除云端数据） ==========
    suspend fun pushAll(
        banks: List<QuestionBank>, questions: List<Question>,
        checkIns: List<DailyCheckIn>, dailyTargets: Map<String, Int>,
        politics: Int, english: Int, psy: Int
    ) {
        val uid = userId ?: return
        // 题库 upsert
        if (banks.isNotEmpty()) {
            val bankMaps = banks.map { b ->
                mapOf("id" to b.id, "user_id" to uid, "name" to b.name,
                    "subject" to b.subject.name.lowercase(), "question_count" to b.questionCount,
                    "created_at" to b.createdAt)
            }
            for (i in bankMaps.indices step 500) {
                upsert("banks", gson.toJson(bankMaps.subList(i, minOf(i + 500, bankMaps.size))), "id")
            }
        }
        // 题目 upsert
        if (questions.isNotEmpty()) {
            val qMaps = questions.map { questionToMap(it, uid) }
            for (i in qMaps.indices step 500) {
                upsert("questions", gson.toJson(qMaps.subList(i, minOf(i + 500, qMaps.size))), "id")
            }
        }
        // 打卡 upsert
        if (checkIns.isNotEmpty()) {
            val ciMaps = checkIns.map { ci ->
                mapOf("user_id" to uid, "date" to ci.date,
                    "completed_count" to ci.completedCount, "target_count" to ci.targetCount,
                    "study_minutes" to ci.studyMinutes, "bank_progress" to ci.bankProgress,
                    "bank_correct" to ci.bankCorrect, "bank_studied_ids" to ci.bankStudiedIds,
                    "bank_correct_ids" to ci.bankCorrectIds)
            }
            for (i in ciMaps.indices step 500) {
                upsert("check_ins", gson.toJson(ciMaps.subList(i, minOf(i + 500, ciMaps.size))), "user_id,date")
            }
        }
        upsertDailyTargets(dailyTargets)
        upsertTargetScores(politics, english, psy)
    }

    // ========== 全量拉取 ==========
    data class AllData(
        val banks: List<QuestionBank>,
        val questions: List<Question>,
        val checkIns: List<DailyCheckIn>,
        val dailyTargets: Map<String, Int>,
        val targetScores: Triple<Int, Int, Int>,
        val settings: Map<String, Any>?
    )

    suspend fun pullAll(): AllData {
        return AllData(
            banks = fetchBanks(),
            questions = fetchQuestions(),
            checkIns = fetchCheckIns(),
            dailyTargets = fetchDailyTargets(),
            targetScores = fetchTargetScores(),
            settings = fetchSettings()
        )
    }
}

