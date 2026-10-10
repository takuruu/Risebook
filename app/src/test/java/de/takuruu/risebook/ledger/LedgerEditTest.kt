package de.takuruu.risebook.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerEditTest {

    private fun sampleJournal(): List<Transaction> = listOf(
        Transaction(
            LocalDate.of(2025, 1, 15), "Bakery",
            listOf(Posting("Expenses:Food", 4.5), Posting("Assets:Cash"))
        ).validated(),
        Transaction(
            LocalDate.of(2025, 1, 16), "Bus",
            listOf(Posting("Expenses:Transport", 3.0), Posting("Assets:Cash", -3.0))
        ).validated()
    )

    @Test
    fun `edit replaces the target transaction and keeps order`() {
        val journal = sampleJournal().toMutableList()
        val original = journal[0]
        val edited = Transaction(
            original.date, "Bäckerei Müller",
            listOf(Posting("Expenses:Food", 5.0), Posting("Assets:Cash"))
        ).validated()
        journal[0] = edited
        assertEquals(2, journal.size)
        assertEquals("Bäckerei Müller", journal[0].payee)
        assertEquals("Bus", journal[1].payee)
    }

    @Test
    fun `delete removes only the target transaction`() {
        val journal = sampleJournal().toMutableList()
        val removed = journal.removeAt(1)
        assertEquals(1, journal.size)
        assertFalse(journal.contains(removed))
        assertEquals("Bakery", journal[0].payee)
    }

    @Test
    fun `journal roundtrip preserves content after edit`() {
        val journal = sampleJournal().toMutableList()
        journal[0] = Transaction(
            LocalDate.of(2025, 1, 15), "Bakery",
            listOf(Posting("Expenses:Food", 6.0), Posting("Assets:Cash"))
        ).validated()
        val text = journal.joinToString("\n") { it.format() }
        val reparsed = LedgerParser.parse(text)
        assertEquals(2, reparsed.size)
        assertEquals(6.0, reparsed[0].postings[0].amount!!, 1e-9)
        assertTrue(reparsed[0].balanced())
    }
}
