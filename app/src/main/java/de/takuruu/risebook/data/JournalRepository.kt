package de.takuruu.risebook.data

import android.content.Context
import de.takuruu.risebook.ledger.Budget
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

    private val _budgets = MutableStateFlow<List<Budget>>(emptyList())
    val budgets: StateFlow<List<Budget>> = _budgets

    fun accounts(): List<String> =
        _transactions.value.flatMap { txn -> txn.postings.map { it.account } }.distinct()

    fun reload() {
        val text = if (file.exists()) file.readText() else ""
        _transactions.value = runCatching { LedgerParser.parse(text) }.getOrDefault(emptyList())
        _budgets.value = LedgerParser.parseBudgets(text)
    }

    init {
        reload()
    }

    init {
        reload()
    }

    fun setBudget(account: String, amount: Double) {
        require(LedgerParser.isValidAccount(account)) { "Kontoname ungültig" }
        val existing = LedgerParser.parseBudgets(file.readText())
            .filter { it.account != account }
        val updated = existing + Budget(account, amount)
        rewriteBudgets(updated)
        reload()
    }

    fun removeBudget(account: String) {
        val updated = LedgerParser.parseBudgets(file.readText())
            .filter { it.account != account }
        rewriteBudgets(updated)
        reload()
    }

    private fun rewriteBudgets(budgets: List<Budget>) {
        val text = file.readText()
        val withoutBudgets = text.lineSequence()
            .filter { line ->
                val t = line.trim()
                !t.startsWith("~")
            }
            .joinToString("\n")
        val budgetBlock = budgets.joinToString("\n") {
            "~ monthly\n    ${it.account}          ${"%.2f".format(it.amount)} ${it.commodity}"
        }
        file.writeText(listOf(withoutBudgets.trim(), budgetBlock).filter { it.isNotBlank() }.joinToString("\n\n") + "\n")
    }

    /** Ausgaben des laufenden Monats pro Budget-Konto, Unterkonten inklusive. */
    fun monthlySpending(): Map<String, Double> {
        val now = java.time.LocalDate.now()
        val totals = mutableMapOf<String, Double>()
        _transactions.value.forEach { txn ->
            if (txn.date.year == now.year && txn.date.month == now.month) {
                txn.postings.forEach { p ->
                    if (p.account.startsWith("Expenses")) {
                        totals.merge(p.account, p.effectiveAmount, Double::plus)
                    }
                }
            }
        }
        return totals
    }

    /** Aggregierte Ist-Werte passend zu den Budget-Konten (inkl. Unterkonten). */
    fun budgetProgress(): List<Triple<Budget, Double, Double>> {
        val spending = monthlySpending()
        return _budgets.value.map { budget ->
            val actual = spending.entries
                .filter { (account, _) ->
                    account == budget.account || account.startsWith(budget.account + ":")
                }
                .sumOf { it.value }
            Triple(budget, actual, budget.amount)
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
