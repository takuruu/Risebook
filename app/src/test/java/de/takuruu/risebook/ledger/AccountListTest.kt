package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AccountListTest {

    @Test
    fun `distinct accounts across transactions`() {
        val txns = listOf(
            Transaction(
                LocalDate.of(2025, 1, 15), "Bakery",
                listOf(Posting("Expenses:Food", 4.5), Posting("Assets:Cash"))
            ),
            Transaction(
                LocalDate.of(2025, 1, 16), "Bus",
                listOf(Posting("Expenses:Transport", 3.0), Posting("Assets:Cash", -3.0))
            )
        )
        val accounts = txns.flatMap { it.postings.map { p -> p.account } }.distinct()
        assertEquals(
            listOf("Expenses:Food", "Assets:Cash", "Expenses:Transport"),
            accounts
        )
    }

    @Test
    fun `balances include auto-balanced remainder`() {
        val txn = Transaction(
            LocalDate.of(2025, 1, 15), "Bakery",
            listOf(Posting("Expenses:Food", 4.5), Posting("Assets:Cash"))
        ).validated()
        val totals = mutableMapOf<String, Double>()
        txn.postings.forEach { p ->
            val remainder = -txn.postings.filter { it.amount != null }.sumOf { it.amount!! }
            val value = p.amount ?: remainder
            totals.merge(p.account, value, Double::plus)
        }
        assertEquals(-4.5, totals["Assets:Cash"]!!, 1e-9)
        assertEquals(4.5, totals["Expenses:Food"]!!, 1e-9)
    }
}
