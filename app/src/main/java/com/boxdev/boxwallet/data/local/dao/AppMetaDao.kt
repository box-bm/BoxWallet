package com.boxdev.boxwallet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boxdev.boxwallet.data.local.entity.AppMetaEntity

@Dao
interface AppMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AppMetaEntity)

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("DELETE FROM app_meta WHERE `key` IN (:keys)")
    suspend fun delete(keys: List<String>)
}
