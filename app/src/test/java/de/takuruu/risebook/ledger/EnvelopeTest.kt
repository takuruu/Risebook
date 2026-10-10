package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnvelopeTest {

    @Test
    fun `assignment transaction parses as regular transaction`() {
        val text = """
            2025-01-01 * Budget-Zuweisung 2025-01
                Budget:Food          200.00 €
                Equity:Budgettopf   -200.00 €
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertTrue(txns[0].balanced())
    }

    @Test
    fun `available is assigned minus spent`() {
        val env = Envelope(
            account = "Budget:Food",
            assignedTotal = 200.0,
            spentTotal = 84.5,
            assignedThisMonth = 200.0,
            spentThisMonth = 84.5
        )
        assertEquals(115.5, env.available, 1e-9)
    }

    @Test
    fun `leftover rolls over into next month`() {
        val assignedTotal = 200.0 + 200.0
        val spentTotal = 150.0 + 0.0
        val env = Envelope("Budget:Food", assignedTotal, spentTotal, 200.0, 0.0)
        assertEquals(250.0, env.available, 1e-9)
    }

    @Test
    fun `overspending makes available negative`() {
        val env = Envelope("Budget:Food", 100.0, 120.0, 100.0, 120.0)
        assertEquals(-20.0, env.available, 1e-9)
    }

    @Test
    fun `expense posting maps to envelope account`() {
        val expenseAccount = "Expenses:Food"
        val envelopeAccount = "Budget:" + expenseAccount.removePrefix("Expenses:")
        assertEquals("Budget:Food", envelopeAccount)
    }

    @Test
    fun `~ monthly blocks are skipped on parse but assignments are transactions`() {
        val text = """
            ~ monthly
                Expenses:Food          200.00 €

            2025-01-01 * Budget-Zuweisung 2025-01
                Budget:Food          200.00 €
                Equity:Budgettopf   -200.00 €
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals("Budget-Zuweisung 2025-01", txns[0].payee)
    }

    @Test
    fun `envelope totals from a realistic journal`() {
        val text = """
            2025-01-01 * Budget-Zuweisung 2025-01
                Budget:Food          200.00 €
                Equity:Budgettopf   -200.00 €

            2025-01-15 * Bakery
                Expenses:Food          4.50 €
                Assets:Cash           -4.50 €
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        val assigned = mutableMapOf<String, Double>()
        val spent = mutableMapOf<String, Double>()
        txns.forEach { txn ->
            val isAssignment = txn.postings.any { it.account == "Equity:Budgettopf" }
            txn.postings.forEach { p ->
                if (isAssignment && p.account.startsWith("Budget:")) {
                    assigned.merge(p.account, p.amount!!, Double::plus)
                } else if (!isAssignment && p.account.startsWith("Expenses:")) {
                    val env = "Budget:" + p.account.removePrefix("Expenses:")
                    if (env in assigned) spent.merge(env, p.amount!!, Double::plus)
                }
            }
        }
        assertEquals(200.0, assigned["Budget:Food"]!!, 1e-9)
        assertEquals(4.5, spent["Budget:Food"]!!, 1e-9)
    }
}
