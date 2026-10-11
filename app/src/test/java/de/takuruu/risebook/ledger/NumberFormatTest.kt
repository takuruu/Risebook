package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatTest {

    @Test
    fun `dot decimal parses`() {
        assertEquals(4.5, LedgerParser.normalizeNumber("4.50"), 1e-9)
        assertEquals(-3.0, LedgerParser.normalizeNumber("-3.00"), 1e-9)
    }

    @Test
    fun `comma decimal parses (German style)`() {
        assertEquals(4.5, LedgerParser.normalizeNumber("4,50"), 1e-9)
        assertEquals(-3.25, LedgerParser.normalizeNumber("-3,25"), 1e-9)
    }

    @Test
    fun `German thousands with comma decimals parse`() {
        assertEquals(1234.56, LedgerParser.normalizeNumber("1.234,56"), 1e-9)
    }

    @Test
    fun `parseAmount accepts comma amounts`() {
        assertEquals(4.5 to "€", LedgerParser.parseAmount("4,50"))
        assertEquals(12.5 to "EUR", LedgerParser.parseAmount("12,50 EUR"))
    }

    @Test
    fun `posting line with comma amount parses`() {
        val text = """
            2025-01-15 * Bäckerei
                Expenses:Food          4,50 €
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        assertEquals(4.5, txns[0].postings[0].amount!!, 1e-9)
        assertEquals(4.5, txns[0].postings[0].amount!!, 1e-9)
    }

    @Test
    fun `journal with German decimals parses fully`() {
        val text = """
            2025-01-15 * Bäckerei Müller
                Expenses:Food          12,99 €
                Assets:Cash           -12,99 €

            2025-02-01 * Gehalt
                Assets:Checking       3.250,00 €
                Income:Salary        -3.250,00 €
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(2, txns.size)
        assertEquals(12.99, txns[0].postings[0].amount!!, 1e-9)
        assertEquals(3250.0, txns[1].postings[0].amount!!, 1e-9)
    }
}
