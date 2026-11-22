package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "Category",
    /** An [com.boxdev.boxwallet.domain.model.AccountColor] key; DEFAULT keeps the seeded tone. */
    val color: String = "DEFAULT"
)
