package com.boxdev.boxwallet.data.repository

import com.boxdev.boxwallet.data.local.dao.CategoryDao
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category.toEntity())

    suspend fun updateCategory(category: Category) = categoryDao.update(category.toEntity())

    suspend fun deleteCategory(id: Long) = categoryDao.deleteById(id)


    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories().map { it.map { e -> e.toDomain() } }
}
