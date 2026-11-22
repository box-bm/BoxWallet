package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class LoansGroupingTest {
    private val usd = java.util.Currency.getInstance("USD")

    private fun loan(id: Long, type: LoanType, paid: Long = 0, day: Int = 1) = Loan(
        id = id, name = "L$id", loanType = type, total = Money(1000, usd), accountId = 1,
        date = LocalDateTime.of(2026, 1, day, 0, 0), paid = Money(paid, usd),
    )

    @Test
    fun `open loans split by direction and settled ones go apart newest first`() {
        val groups = listOf(
            loan(1, LoanType.INCOME, day = 1),
            loan(2, LoanType.INCOME, day = 5),
            loan(3, LoanType.EXPENSE),
            loan(4, LoanType.EXPENSE, paid = 1000, day = 2),
            loan(5, LoanType.INCOME, paid = 1000, day = 3),
        ).groupForList()

        assertEquals(listOf(2L, 1L), groups.owedToMe.map { it.id })
        assertEquals(listOf(3L), groups.iOwe.map { it.id })
        assertEquals(listOf(5L, 4L), groups.settled.map { it.id })
    }
}
