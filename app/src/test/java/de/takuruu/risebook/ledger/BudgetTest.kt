package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetTest {

    @Test
    fun `parse monthly budget block`() {
        val text = """
            2025-01-15 * Bakery
                Expenses:Food          4.50 €
                Assets:Cash

            ~ monthly
                Expenses:Food          200.00 €
                Expenses:Transport      50.00 €
                budget:Assets
        """.trimIndent()
        val budgets = LedgerParser.parseBudgets(text)
        assertEquals(2, budgets.size)
        assertEquals(Budget("Expenses:Food", 200.0), budgets[0])
        assertEquals(Budget("Expenses:Transport", 50.0), budgets[1])
    }

    @Test
    fun `no budgets in plain journal`() {
        val text = """
            2025-01-15 * Bakery
                Expenses:Food          4.50 €
                Assets:Cash
        """.trimIndent()
        assertTrue(LedgerParser.parseBudgets(text).isEmpty())
    }

    @Test
    fun `transactions still parse with budget block present`() {
        val text = """
            ~ monthly
                Expenses:Food          200.00 €

            2025-01-15 * Bakery
                Expenses:Food          4.50 €
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals("Bakery", txns[0].payee)
    }

    @Test
    fun `budget for account matches sub-accounts`() {
        val budgets = listOf(Budget("Expenses:Food", 200.0))
        assertEquals(budgets[0], budgets.forAccount("Expenses:Food"))
        assertEquals(budgets[0], budgets.forAccount("Expenses:Food:Restaurant"))
        assertEquals(null, budgets.forAccount("Expenses:Transport"))
    }

    @Test
    fun `monthly spending includes sub-accounts and current month only`() {
        val now = LocalDate.now()
        val txns = listOf(
            Transaction(now, "Bakery", listOf(Posting("Expenses:Food", 4.5), Posting("Assets:Cash"))),
            Transaction(now, "Restaurant", listOf(Posting("Expenses:Food:Restaurant", 20.0), Posting("Assets:Cash"))),
            Transaction(now.minusMonths(1), "Old", listOf(Posting("Expenses:Food", 99.0), Posting("Assets:Cash")))
        )
        val totals = mutableMapOf<String, Double>()
        txns.filter { it.date.year == now.year && it.date.month == now.month }
            .forEach { txn ->
                txn.postings.filter { it.account.startsWith("Expenses") }
                    .forEach { totals.merge(it.account, it.effectiveAmount, Double::plus) }
            }
        val foodTotal = totals.entries
            .filter { it.key == "Expenses:Food" || it.key.startsWith("Expenses:Food:") }
            .sumOf { it.value }
        assertEquals(24.5, foodTotal, 1e-9)
    }
}
