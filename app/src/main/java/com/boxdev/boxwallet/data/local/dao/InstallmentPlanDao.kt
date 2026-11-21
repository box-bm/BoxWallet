package com.boxdev.boxwallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.boxdev.boxwallet.data.local.entity.InstallmentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPlanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(plan: InstallmentPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(plans: List<InstallmentPlanEntity>)

    @Update
    suspend fun update(plan: InstallmentPlanEntity)

    @Query("SELECT * FROM installment_plans WHERE id = :id")
    suspend fun getById(id: Long): InstallmentPlanEntity?

    @Query("SELECT * FROM installment_plans WHERE id = :id")
    fun observeById(id: Long): Flow<InstallmentPlanEntity?>

    @Query("SELECT * FROM installment_plans WHERE accountId IN (:accountIds)")
    fun observeByAccounts(accountIds: List<Long>): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans")
    fun observeAll(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans")
    suspend fun getAllSync(): List<InstallmentPlanEntity>

    @Query("DELETE FROM installment_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM installment_plans")
    suspend fun deleteAll()
}
