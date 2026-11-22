package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.analytics.AnalyticsHelper
import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val food = Category(id = 1, name = "Food")
    private val fun_ = Category(id = 2, name = "Fun")
    private val repository = mock<CategoryRepository>()
    private val analytics = mock<AnalyticsHelper>()
    private val prefs = mock<UserPreferences>()
    private val defaultId = MutableStateFlow<Long?>(null)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(repository.getAllCategories()).thenReturn(flowOf(listOf(food, fun_)))
        whenever(prefs.defaultCategoryId).thenReturn(defaultId)
        whenever(prefs.setDefaultCategory(anyOrNull())).doSuspendableAnswer {
            defaultId.value = it.arguments[0] as Long?
            Unit
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CategoriesViewModel(repository, prefs, analytics)

    @Test
    fun `the default category resolves from the list`() = runTest {
        defaultId.value = 2
        val vm = vm()
        advanceUntilIdle()
        assertEquals(2L, vm.uiState.value.defaultCategory?.id)
    }

    @Test
    fun `a default id missing from the list shows none`() = runTest {
        defaultId.value = 9
        val vm = vm()
        advanceUntilIdle()
        assertNull(vm.uiState.value.defaultCategory)
    }

    @Test
    fun `save without id creates a trimmed category with its color`() = runTest {
        val vm = vm()
        vm.save(null, "  Super ", "", "ShoppingCart", AccountColor.ORANGE)
        advanceUntilIdle()
        verify(repository).insertCategory(Category(name = "Super", icon = "ShoppingCart", color = AccountColor.ORANGE))
    }

    @Test
    fun `save with id updates it`() = runTest {
        val vm = vm()
        vm.save(2, "Fun", "Games", "Category", AccountColor.ORANGE)
        advanceUntilIdle()
        verify(repository).updateCategory(Category(id = 2, name = "Fun", description = "Games", icon = "Category", color = AccountColor.ORANGE))
    }

    @Test
    fun `a blank name is not saved`() = runTest {
        val vm = vm()
        vm.save(null, "   ", "", "Category", AccountColor.DEFAULT)
        advanceUntilIdle()
        verify(repository, never()).insertCategory(any())
    }

    @Test
    fun `deleting the default clears it`() = runTest {
        defaultId.value = 2
        val vm = vm()
        vm.delete(2)
        advanceUntilIdle()
        verify(repository).deleteCategory(2)
        assertNull(defaultId.value)
    }

    @Test
    fun `deleting another category keeps the default`() = runTest {
        defaultId.value = 2
        val vm = vm()
        vm.delete(1)
        advanceUntilIdle()
        verify(repository).deleteCategory(1)
        assertEquals(2L, defaultId.value)
    }

    @Test
    fun `a failing save shows a localized error`() = runTest {
        whenever(repository.insertCategory(any())).thenThrow(RuntimeException("boom"))
        val vm = vm()
        vm.save(null, "X", "", "Category", AccountColor.DEFAULT)
        advanceUntilIdle()
        assertEquals(R.string.categories_save_failed, vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }
}
