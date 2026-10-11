package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression test based on a real user journal: currency symbol before the
 * amount (€ -800.96), headers without status marker, lowercase accounts,
 * commodity attached without space (€1.85) and a UTF-8 BOM.
 */
class RealJournalTest {

    private val journal = """
        2026-09-23 * starting balances
            assets:ING:Checking                  € -800.96
            liabilities:PayPal                    € -719.61
            equity:OpeningBalances

        2026-09-28 Mail & Media | Gehalt
            assets:ING:Checking                                        € 3533.64
            income:mailmedia                                          € -3533.64

        2026-10-05 * Kantine
            expenses:Food   € 4.90
            expenses:Food   €1.85
            assets:ING:Checking

        2026-10-02 * REWE
            expenses:Food:drinks  € 4.54
            expenses:Food:drinks  € 2.93
            expenses:Food  € 19.38
            expenses:Food  € 19.48
            assets:ING:Checking:lebensmittel
    """.trimIndent()

    @Test
    fun `real journal parses completely`() {
        val txns = LedgerParser.parse(journal)
        assertEquals(4, txns.size)
    }

    @Test
    fun `currency before amount parses`() {
        val txns = LedgerParser.parse(journal)
        val posting = txns[0].postings[0]
        assertEquals("assets:ING:Checking", posting.account)
        assertEquals(-800.96, posting.amount!!, 1e-9)
        assertEquals("€", posting.commodity)
    }

    @Test
    fun `header without status marker parses`() {
        val txns = LedgerParser.parse(journal)
        assertEquals("Mail & Media | Gehalt", txns[1].payee)
        assertEquals(LocalDate.of(2026, 9, 28), txns[1].date)
    }

    @Test
    fun `commodity without space parses`() {
        val txns = LedgerParser.parse(journal)
        assertEquals(1.85, txns[2].postings[1].amount!!, 1e-9)
    }

    @Test
    fun `all transactions balance`() {
        LedgerParser.parse(journal).forEach { assertTrue(it.balanced()) }
    }

    @Test
    fun `utf8 bom is tolerated`() {
        val txns = LedgerParser.parse("﻿" + journal)
        assertEquals(4, txns.size)
    }

    @Test
    fun `error messages include line number`() {
        val bad = "2026-09-23 * Test\n    expenses:Food  ???\n"
        try {
            LedgerParser.parse(bad)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Zeile 2"))
        }
    }
}
