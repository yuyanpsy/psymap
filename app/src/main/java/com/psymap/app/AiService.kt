package com.psymap.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object AiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    var apiKey: String = "sk-ozgipwvoghexlmpzriaesynaypyqjszqdllemcqzxvaokzqr"
    var apiBaseUrl: String = "https://api.siliconflow.cn/v1"
    var modelName: String = "deepseek-ai/DeepSeek-OCR"
    // 文本模型用于评分等纯文本任务，更快更稳定
    var textModelName: String = "Qwen/Qwen2.5-72B-Instruct"

    /** 从 AI 返回中提取纯 JSON（去掉 markdown 代码块包裹） */
    private fun cleanJson(raw: String): String {
        var s = raw.trim()
        // 去掉 ```json ... ``` 或 ``` ... ```
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockRegex.find(s)
        if (match != null) {
            s = match.groupValues[1].trim()
        }
        return s
    }

    /** 通用聊天请求（指定模型） */
    private fun doChat(
        model: String,
        messages: List<Map<String, Any>>,
        temperature: Double,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (apiKey.isBlank()) { onError("请先配置 API Key"); return }
        Log.d("PsyMap-AI", "doChat model=$model, url=$apiBaseUrl/chat/completions")

        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to temperature
        ))

        val request = Request.Builder()
            .url("$apiBaseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(JSON_TYPE))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.body?.string() ?: ""
                    Log.d("PsyMap-AI", "Response code=${response.code}, body(200)=${json.take(200)}")
                    if (!response.isSuccessful) {
                        onError("API错误(${response.code}): ${json.take(200)}")
                        return
                    }
                    val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                    @Suppress("UNCHECKED_CAST")
                    val choices = map["choices"] as? List<Map<String, Any>>
                    if (choices.isNullOrEmpty()) {
                        onError("API返回无内容: ${json.take(200)}")
                        return
                    }
                    @Suppress("UNCHECKED_CAST")
                    val msg = choices[0]["message"] as? Map<String, Any>
                    val content = msg?.get("content") as? String ?: ""
                    if (content.isBlank()) {
                        onError("API返回内容为空")
                    } else {
                        onResult(content)
                    }
                } catch (e: Exception) {
                    onError("解析错误: ${e.message}")
                }
            }
        })
    }

    /** 纯文本聊天（用文本模型） */
    fun chatCompletion(
        systemPrompt: String,
        userMessage: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        doChat(
            model = textModelName,
            messages = listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userMessage)
            ),
            temperature = 0.3,
            onResult = onResult,
            onError = onError
        )
    }

    /** 带图片的视觉请求（用视觉模型） */
    fun visionRequest(
        systemPrompt: String,
        userText: String,
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val base64 = bitmapToBase64(bitmap)
        doChat(
            model = modelName,
            messages = listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to listOf(
                    mapOf("type" to "image_url", "image_url" to mapOf(
                        "url" to "data:image/jpeg;base64,$base64",
                        "detail" to "high"
                    )),
                    mapOf("type" to "text", "text" to userText)
                ))
            ),
            temperature = 0.2,
            onResult = onResult,
            onError = onError
        )
    }

    /** 主观题AI打分 */
    fun gradeSubjectiveAnswer(
        question: String,
        correctAnswer: String,
        userAnswer: String,
        onResult: (score: Int, feedback: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = """你是北师大MAP考研阅卷专家。请根据标准答案对学生的作答进行评分。
评分标准：满分100分，根据要点覆盖度、逻辑性、专业术语使用来打分。
你必须只返回纯JSON，不要用markdown代码块包裹，不要加任何其他文字。
格式：{"score": 数字, "feedback": "详细反馈"}"""

        val userMsg = "题目：$question\n标准答案：$correctAnswer\n学生作答：$userAnswer"

        chatCompletion(prompt, userMsg, { result ->
            try {
                val cleaned = cleanJson(result)
                val map = gson.fromJson<Map<String, Any>>(cleaned, object : TypeToken<Map<String, Any>>() {}.type)
                val score = (map["score"] as? Double)?.toInt() ?: 0
                val feedback = map["feedback"] as? String ?: "无反馈"
                onResult(score, feedback)
            } catch (e: Exception) {
                // 解析失败时尝试用正则提取 score
                val scoreMatch = Regex("\"score\"\\s*:\\s*(\\d+)").find(result)
                val feedbackMatch = Regex("\"feedback\"\\s*:\\s*\"([^\"]+)\"").find(result)
                if (scoreMatch != null) {
                    onResult(
                        scoreMatch.groupValues[1].toIntOrNull() ?: 0,
                        feedbackMatch?.groupValues?.get(1) ?: "AI返回格式异常，但已提取分数"
                    )
                } else {
                    onResult(0, "评分解析失败，AI原始回复：${result.take(300)}")
                }
            }
        }, onError)
    }

    // OCR 专用模型
    var ocrModelName: String = "deepseek-ai/DeepSeek-OCR"

    /** 拍照识别题目：用 DeepSeek-OCR 提取文字，再用文本模型结构化 */
    fun recognizeQuestions(
        bitmap: Bitmap,
        onResult: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val base64 = bitmapToBase64(bitmap)
        Log.d("PsyMap-OCR", "开始OCR识别, 图片base64长度: ${base64.length}")

        doChat(
            model = ocrModelName,
            messages = listOf(
                mapOf("role" to "user", "content" to listOf(
                    mapOf("type" to "image_url", "image_url" to mapOf(
                        "url" to "data:image/jpeg;base64,$base64"
                    )),
                    mapOf("type" to "text", "text" to "OCR识别图片中的所有文字内容，完整输出，不要遗漏。请使用中文简体输出，不要使用繁体字。保留原文的层级结构、题号、选项字母等格式。")
                ))
            ),
            temperature = 0.1,
            onResult = { ocrText ->
                Log.d("PsyMap-OCR", "OCR结果(前500字): ${ocrText.take(500)}")
                if (ocrText.isBlank()) {
                    onError("OCR未识别到文字")
                    return@doChat
                }
                // Step 2: 用文本模型结构化
                val prompt = """你是考研试卷题目解析专家。以下是从试卷/教材图片中OCR识别出的原始文字。
请将这些文字整理为结构化的题目列表。

重要规则：
1. 仔细识别文档结构：章节标题（如"第一章 xxx"）、题型分类（如"一、选择题"、"二、简答题"、"三、论述题"）
2. 每道题必须包含以下字段：
   - "question": 完整题目内容（不含选项、不含题号）
   - "answer": 参考答案，必须保留markdown格式：
     · 用编号（1. 2. 3.）分点，每个要点用\n换行
     · 重要概念和关键词用**加粗**
     · 每个要点的小标题加粗，如"**1. 遗传是人格发展的先天基础**\n遗传基因决定..."
     · 选择题答案只保留字母（如"A"）
     · 无答案填空字符串
   - "options": 选择题选项数组，非选择题填空数组[]
   - "type": single_choice/multi_choice/short_answer/essay/case_analysis/comprehensive
   - "explanation": 解析内容（保留格式，没有则填空字符串）
   - "chapter": 所属章节名称（没有则填空字符串）

3. 题型判断：有A/B/C/D选项→选择题，标注简答→short_answer，标注论述→essay
4. 严格基于OCR文字，不要编造内容。使用中文简体。

只返回纯JSON数组，不要用markdown代码块包裹：
[{"question":"题目","answer":"**1. 要点一**\n内容...\n\n**2. 要点二**\n内容...","options":[],"type":"essay","explanation":"","chapter":""}]"""

                chatCompletion(prompt, "OCR原始文字：\n$ocrText", { aiResult ->
                    Log.d("PsyMap-OCR", "AI结构化结果(前500字): ${aiResult.take(500)}")
                    try {
                        val cleaned = cleanJson(aiResult)
                        val list = gson.fromJson<List<Map<String, Any>>>(cleaned,
                            object : TypeToken<List<Map<String, Any>>>() {}.type)
                        if (list.isNullOrEmpty()) {
                            onError("AI未能从OCR文字中提取题目")
                        } else {
                            onResult(list)
                        }
                    } catch (e: Exception) {
                        onError("解析失败: ${e.message}")
                    }
                }, { error ->
                    Log.e("PsyMap-OCR", "AI结构化失败: $error")
                    onError(error)
                })
            },
            onError = { error ->
                Log.e("PsyMap-OCR", "OCR请求失败: $error")
                onError(error)
            }
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // 提高图片质量以确保文字清晰可识别
        val maxDim = 1600
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
