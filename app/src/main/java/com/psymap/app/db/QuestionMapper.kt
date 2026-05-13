package com.psymap.app.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psymap.app.Question
import com.psymap.app.QuestionType

private val gson = Gson()

fun Question.toEntity(): QuestionEntity = QuestionEntity(
    id = id, bankId = bankId, content = content, answer = answer,
    explanation = explanation, options = gson.toJson(options),
    type = type.name, chapter = chapter, tags = gson.toJson(tags),
    reviewCount = reviewCount, isInWrongBook = isInWrongBook,
    isInFavorites = isInFavorites, correctCount = correctCount,
    wrongCount = wrongCount, note = note, isFrequent = isFrequent,
    isMemorize = isMemorize, ttsGenerated = ttsGenerated,
    createdAt = createdAt, lastStudiedAt = lastStudiedAt
)

fun QuestionEntity.toDomain(): Question = Question(
    id = id, bankId = bankId, content = content, answer = answer,
    explanation = explanation,
    options = try { gson.fromJson(options, object : TypeToken<List<String>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() },
    type = try { QuestionType.valueOf(type) } catch (e: Exception) { QuestionType.SHORT_ANSWER },
    chapter = chapter,
    tags = try { gson.fromJson(tags, object : TypeToken<List<String>>() {}.type) ?: emptyList() } catch (e: Exception) { emptyList() },
    reviewCount = reviewCount, isInWrongBook = isInWrongBook,
    isInFavorites = isInFavorites, correctCount = correctCount,
    wrongCount = wrongCount, note = note, isFrequent = isFrequent,
    isMemorize = isMemorize, ttsGenerated = ttsGenerated,
    createdAt = createdAt, lastStudiedAt = lastStudiedAt
)
