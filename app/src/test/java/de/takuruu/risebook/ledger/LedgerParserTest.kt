package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerParserTest {

    @Test
    fun `valid account names`() {
        assertTrue(LedgerParser.isValidAccount("Expenses:Groceries"))
        assertTrue(LedgerParser.isValidAccount("Assets:Cash"))
        assertTrue(LedgerParser.isValidAccount("Income"))
    }

    @Test
    fun `invalid account names`() {
        assertFalse(LedgerParser.isValidAccount("expenses:groceries"))
        assertFalse(LedgerParser.isValidAccount("Expenses::Groceries"))
        assertFalse(LedgerParser.isValidAccount(""))
    }

    @Test
    fun `parse amount with default commodity`() {
        assertEquals(12.5 to "€", LedgerParser.parseAmount("12.50"))
        assertEquals(-3.0 to "€", LedgerParser.parseAmount("-3.00"))
    }

    @Test
    fun `parse amount with custom commodity`() {
        assertEquals(10.0 to "USD", LedgerParser.parseAmount("10 USD"))
    }

    @Test
    fun `invalid amount returns null`() {
        assertNull(LedgerParser.parseAmount("abc"))
        assertNull(LedgerParser.parseAmount(""))
    }

    @Test
    fun `parse transaction with auto-balanced posting`() {
        val text = """
            2025-01-15 * Bakery
                Expenses:Food                             4.50 €
                Assets:Cash
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(1, txns.size)
        val txn = txns.first()
        assertEquals(LocalDate.of(2025, 1, 15), txn.date)
        assertEquals("Bakery", txn.payee)
        assertEquals(2, txn.postings.size)
        assertEquals(4.5, txn.postings[0].amount!!, 1e-9)
        assertNull(txn.postings[1].amount)
        assertTrue(txn.balanced())
    }

    @Test
    fun `parse multiple transactions separated by blank lines`() {
        val text = """
            2025-01-15 * Bakery
                Expenses:Food                             4.50 €
                Assets:Cash                             -4.50 €

            2025-01-16 * Bus
                Expenses:Transport                        3.00 €
                Assets:Cash                             -3.00 €
        """.trimIndent()
        val txns = LedgerParser.parse(text)
        assertEquals(2, txns.size)
        assertEquals("Bus", txns[1].payee)
    }

    @Test
    fun `format transaction roundtrip`() {
        val txn = Transaction(
            date = LocalDate.of(2025, 1, 15),
            payee = "Bakery",
            postings = listOf(
                Posting("Expenses:Food", 4.5, "€"),
                Posting("Assets:Cash")
            )
        )
        val reparsed = LedgerParser.parse(txn.format())
        assertEquals(1, reparsed.size)
        assertEquals(txn.payee, reparsed.first().payee)
        assertEquals(txn.postings.size, reparsed.first().postings.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unbalanced transaction without auto posting fails validation`() {
        Transaction(
            date = LocalDate.of(2025, 1, 15),
            payee = "Bakery",
            postings = listOf(
                Posting("Expenses:Food", 4.5, "€"),
                Posting("Assets:Cash", -3.0, "€")
            )
        ).validated()
    }

    @Test
    fun `auto-balanced transaction passes validation`() {
        Transaction(
            date = LocalDate.of(2025, 1, 15),
            payee = "Bakery",
            postings = listOf(
                Posting("Expenses:Food", 4.5, "€"),
                Posting("Assets:Cash")
            )
        ).validated()
    }
}
