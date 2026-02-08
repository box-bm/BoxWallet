package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.CategoryEntity

data class Category(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "Category"
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    description = description,
    icon = icon
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    description = description,
    icon = icon
)
