package de.takuruu.risebook.data

import android.content.Context
import de.takuruu.risebook.ledger.LedgerParser
import de.takuruu.risebook.ledger.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class ImportMode { Replace, Append }

/**
 * Stores the journal as a plain .ledger file in the app's private storage and
 * exposes parsed transactions as a state flow.
 */
class JournalRepository(context: Context) {
    val file: File = File(context.filesDir, "risebook.ledger")

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

    fun accounts(): List<String> =
        _transactions.value.flatMap { txn -> txn.postings.map { it.account } }.distinct()

    init {
        reload()
    }

    fun reload() {
        _transactions.value = if (file.exists()) {
            runCatching { LedgerParser.parse(file.readText()) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    fun append(transaction: Transaction) {
        val text = if (file.exists() && file.readText().isNotEmpty()) "\n" else ""
        file.appendText(text + transaction.format())
        reload()
    }

    fun replaceAll(transactions: List<Transaction>) {
        file.writeText(transactions.joinToString("\n") { it.validated().format() })
        reload()
    }

    fun update(old: Transaction, new: Transaction) {
        val list = _transactions.value.toMutableList()
        val index = list.indexOf(old)
        require(index >= 0) { "transaction not found in journal" }
        list[index] = new
        replaceAll(list)
    }

    fun delete(transaction: Transaction) {
        replaceAll(_transactions.value - transaction)
    }

    fun import(text: String, mode: ImportMode): Int {
        val imported = LedgerParser.parse(text).map { it.validated() }
        if (imported.isEmpty()) throw IllegalArgumentException("Datei enthält keine Buchungen")
        replaceAll(
            when (mode) {
                ImportMode.Replace -> imported
                ImportMode.Append -> _transactions.value + imported
            }
        )
        return imported.size
    }

    fun balances(): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        _transactions.value.forEach { txn ->
            val explicit = txn.postings.filter { it.amount != null }
            val remainder = -explicit.sumOf { it.amount!! }
            txn.postings.forEach { p ->
                val value = p.amount ?: remainder
                totals.merge(p.account, value, Double::plus)
            }
        }
        return totals
    }
}
