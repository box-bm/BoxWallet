package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.domain.model.AccountColor
import com.boxdev.boxwallet.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFormStateTest {

    private val groceries = Category(
        id = 7, name = "Groceries", description = "Weekly shop", icon = "ShoppingCart", color = AccountColor.TEAL,
    )

    @Test
    fun `from null is a blank new form with the automatic color and the generic icon`() {
        val form = CategoryFormState.from(null)

        assertNull(form.id)
        assertEquals("", form.name)
        assertEquals("", form.description)
        assertEquals("Category", form.icon)
        assertEquals(AccountColor.DEFAULT, form.color)
    }

    @Test
    fun `from a category copies every field including its color`() {
        val form = CategoryFormState.from(groceries)

        assertEquals(CategoryFormState(7, "Groceries", "Weekly shop", "ShoppingCart", AccountColor.TEAL), form)
    }

    @Test
    fun `canSave needs a name that is not blank`() {
        assertFalse(CategoryFormState(name = "").canSave)
        assertFalse(CategoryFormState(name = "  ").canSave)
        assertTrue(CategoryFormState(name = "Super").canSave)
    }

    @Test
    fun `preview of a new form has id 0`() {
        val preview = CategoryFormState(name = "Super", icon = "Restaurant", color = AccountColor.PINK).preview()

        assertEquals(Category(id = 0, name = "Super", description = "", icon = "Restaurant", color = AccountColor.PINK), preview)
    }

    @Test
    fun `preview of an edit keeps the real id so the category keeps its silhouette`() {
        val preview = CategoryFormState.from(groceries).copy(name = "Food").preview()

        assertEquals(groceries.copy(name = "Food"), preview)
    }
    @Test
    fun `a new form previews as a steady circle whatever its name`() {
        assertTrue(CategoryFormState(name = "S").previewIsCircle)
        assertTrue(CategoryFormState(name = "Super", color = AccountColor.PINK).previewIsCircle)
        assertFalse(CategoryFormState.from(groceries).previewIsCircle)
    }

    @Test
    fun `only a new form with the automatic color previews in the neutral tone`() {
        assertTrue(CategoryFormState(name = "Super").previewIsNeutral)
        assertFalse(CategoryFormState(name = "Super", color = AccountColor.PINK).previewIsNeutral)
        assertFalse(CategoryFormState.from(groceries.copy(color = AccountColor.DEFAULT)).previewIsNeutral)
    }
}
