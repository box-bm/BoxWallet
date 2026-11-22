package com.boxdev.boxwallet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.boxdev.boxwallet.data.local.database.BoxWalletDatabase
import com.boxdev.boxwallet.data.local.entity.CategoryEntity
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepositoryTest {

    private lateinit var db: BoxWalletDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxWalletDatabase::class.java).allowMainThreadQueries().build()
        repository = CategoryRepository(db.categoryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a category keeps its color`() = runTest {
        repository.insertCategory(Category(name = "Super", icon = "ShoppingCart", color = AccountColor.ORANGE))

        assertEquals(AccountColor.ORANGE, repository.getAllCategories().first().single().color)
    }

    @Test
    fun `a category without a color reads as DEFAULT`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = "X"))

        assertEquals(AccountColor.DEFAULT, repository.getAllCategories().first().single().color)
    }

    @Test
    fun `an unknown color key reads as DEFAULT`() = runTest {
        db.categoryDao().insert(CategoryEntity(name = "X", color = "MAUVE"))

        assertEquals(AccountColor.DEFAULT, repository.getAllCategories().first().single().color)
    }
}
