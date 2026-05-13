package com.psymap.app.db

import androidx.room.*

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions")
    suspend fun getAll(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE bank_id = :bankId")
    suspend fun getByBankId(bankId: String): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE bank_id = :bankId")
    suspend fun countByBankId(bankId: String): Int

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE is_in_wrong_book = 1")
    suspend fun getWrongBookQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE is_in_favorites = 1")
    suspend fun getFavoriteQuestions(): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity)

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM questions WHERE bank_id = :bankId")
    suspend fun deleteByBankId(bankId: String)

    @Query("DELETE FROM questions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE questions SET bank_id = :newBankId WHERE id = :questionId")
    suspend fun moveToBankId(questionId: String, newBankId: String)

    @Query("SELECT * FROM questions WHERE content LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(query: String): List<QuestionEntity>
}
