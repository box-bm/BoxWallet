package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.CategoryEntity

data class Category(
    val id: Long = 0,
    val name: String,
    val description: String = ""
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    description = description
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    description = description
)
