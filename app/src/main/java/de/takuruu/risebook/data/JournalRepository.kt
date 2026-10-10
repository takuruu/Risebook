package de.takuruu.risebook.data

import android.content.Context
import de.takuruu.risebook.ledger.Envelope
import de.takuruu.risebook.ledger.LedgerParser
import de.takuruu.risebook.ledger.Posting
import de.takuruu.risebook.ledger.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate

enum class ImportMode { Replace, Append }

/**
 * Stores the journal as a plain .ledger file in the app's private storage and
 * exposes parsed transactions as a state flow.
 */
class JournalRepository(context: Context) {
    val file: File = File(context.filesDir, "risebook.ledger")

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions

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

    fun accounts(): List<String> =
        _transactions.value.flatMap { txn -> txn.postings.map { it.account } }.distinct()

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
            effectiveAmounts(txn).forEach { (account, value) ->
                totals.merge(account, value, Double::plus)
            }
        }
        return totals
    }

    private fun effectiveAmounts(txn: Transaction): List<Pair<String, Double>> =
        txn.postings.map { p ->
            val remainder = -txn.postings.filter { it.amount != null }.sumOf { it.amount!! }
            p.account to (p.amount ?: remainder)
        }

    /**
     * Envelope model: assignment transactions book Budget:X against
     * Equity:Budgettopf; expenses are real transactions. Available per
     * envelope = assigned total - spent total (all time, so leftover
     * money naturally rolls over into the next month).
     */
    fun envelopes(): List<Envelope> {
        val now = LocalDate.now()
        val assignedTotal = mutableMapOf<String, Double>()
        val spentTotal = mutableMapOf<String, Double>()
        val assignedMonth = mutableMapOf<String, Double>()
        val spentMonth = mutableMapOf<String, Double>()

        _transactions.value.forEach { txn ->
            val hasBudgetPosting = txn.postings.any { it.account.startsWith("Budget:") }
            val hasEquityPosting = txn.postings.any { it.account == "Equity:Budgettopf" }
            val amounts = effectiveAmounts(txn)
            val isAssignment = hasBudgetPosting && hasEquityPosting
            val thisMonth = txn.date.year == now.year && txn.date.month == now.month

            amounts.forEach { (account, value) ->
                if (isAssignment && account.startsWith("Budget:")) {
                    assignedTotal.merge(account, value, Double::plus)
                    if (thisMonth) assignedMonth.merge(account, value, Double::plus)
                } else if (!isAssignment && account.startsWith("Expenses:")) {
                    val envelopeAccount = "Budget:" + account.removePrefix("Expenses:")
                    if (assignedTotal.containsKey(envelopeAccount) ||
                        spentTotal.containsKey(envelopeAccount) ||
                        assignedMonth.containsKey(envelopeAccount)
                    ) {
                        spentTotal.merge(envelopeAccount, value, Double::plus)
                        if (thisMonth) spentMonth.merge(envelopeAccount, value, Double::plus)
                    }
                }
            }
        }

        return (assignedTotal.keys + spentTotal.keys).distinct().sorted().map { account ->
            Envelope(
                account = account,
                assignedTotal = assignedTotal[account] ?: 0.0,
                spentTotal = spentTotal[account] ?: 0.0,
                assignedThisMonth = assignedMonth[account] ?: 0.0,
                spentThisMonth = spentMonth[account] ?: 0.0
            )
        }
    }

    /** Assign money to an envelope for the given month as a real transaction. */
    fun assignToEnvelope(envelopeAccount: String, amount: Double, month: LocalDate) {
        require(envelopeAccount.startsWith("Budget:")) { "Umschlag muss mit Budget: beginnen" }
        require(amount > 0) { "Betrag muss positiv sein" }
        append(
            Transaction(
                date = month,
                payee = "Budget-Zuweisung ${month.year}-${"%02d".format(month.monthValue)}",
                postings = listOf(
                    Posting(envelopeAccount, amount),
                    Posting("Equity:Budgettopf", -amount)
                )
            )
        )
    }

    /** Available to budget: money assigned this month but not yet in an envelope expense side. */
    fun availableToBudget(): Double =
        envelopes().sumOf { it.available } * -1.0 +
            envelopes().sumOf { it.assignedThisMonth - it.spentThisMonth }
}
