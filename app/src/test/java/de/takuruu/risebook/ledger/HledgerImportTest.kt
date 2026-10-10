package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HledgerImportTest {

    @Test
    fun `hledger journal with bang status and code`() {
        val text = """
            2025-01-15 ! (123) Bakery
                Expenses:Food          4.50 EUR
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals("Bakery", txns[0].payee)
        assertEquals(4.5, txns[0].postings[0].amount!!, 1e-9)
        assertEquals("EUR", txns[0].postings[0].commodity)
        assertNull(txns[0].postings[1].amount)
    }

    @Test
    fun `dollar commodity and negative amounts`() {
        val text = """
            2025/03/02 * Grocery store
                Expenses:Groceries     ${'$'}-42.10
                Assets:Checking       ${'$'}42.10
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals(LocalDate.of(2025, 3, 2), txns[0].date)
        assertEquals(-42.1, txns[0].postings[0].amount!!, 1e-9)
        assertEquals("$", txns[0].postings[0].commodity.substring(0, 1))
        assertTrue(txns[0].postings.all { it.amount != null })
    }

    @Test
    fun `tab separated posting lines`() {
        val text = "2025-01-15 * Bakery\n\tExpenses:Food\t4.50 €\n\tAssets:Cash"
        val txns = LedgerParser.parse(text)
        assertEquals(2, txns[0].postings.size)
        assertEquals(4.5, txns[0].postings[0].amount!!, 1e-9)
    }

    @Test
    fun `comments and directives are skipped`() {
        val text = """
            ; a comment
            # another comment

            2025-01-15 * Bakery
                Expenses:Food          4.50 €
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals("Bakery", txns[0].payee)
    }

    @Test
    fun `transaction without status marker still parses`() {
        val text = """
            2025-01-15 Bakery
                Expenses:Food          4.50 €
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals("Bakery", txns[0].payee)
    }

    @Test
    fun `postings with trailing comments`() {
        val text = """
            2025-01-15 * Bakery
                Expenses:Food          4.50 €  ; lunch
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(4.5, txns[0].postings[0].amount!!, 1e-9)
    }
}
