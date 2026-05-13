package com.psymap.app.db

import androidx.room.*

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bank_id") val bankId: String,
    val content: String,
    val answer: String,
    val explanation: String,
    val options: String,  // JSON array as string
    val type: String,
    val chapter: String,
    val tags: String,  // JSON array as string
    @ColumnInfo(name = "review_count") val reviewCount: Int = 0,
    @ColumnInfo(name = "is_in_wrong_book") val isInWrongBook: Boolean = false,
    @ColumnInfo(name = "is_in_favorites") val isInFavorites: Boolean = false,
    @ColumnInfo(name = "correct_count") val correctCount: Int = 0,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int = 0,
    val note: String = "",
    @ColumnInfo(name = "is_frequent") val isFrequent: Boolean = false,
    @ColumnInfo(name = "is_memorize") val isMemorize: Boolean = false,
    @ColumnInfo(name = "tts_generated") val ttsGenerated: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_studied_at") val lastStudiedAt: Long = 0
)
