package de.takuruu.risebook.ledger

import java.time.LocalDate

data class Posting(
    val account: String,
    val amount: Double? = null, // null => auto-balanced remainder
    val commodity: String = "€"
) {
    val effectiveAmount: Double get() = amount ?: 0.0
}

data class Transaction(
    val date: LocalDate,
    val payee: String,
    val postings: List<Posting>,
    val note: String? = null
) {
    fun balanced(): Boolean {
        val explicit = postings.filter { it.amount != null }
        if (explicit.size == postings.size - 1) return true
        val sum = explicit.sumOf { it.amount!! }
        return explicit.size == postings.size && kotlin.math.abs(sum) < 1e-9
    }

    fun validated(): Transaction {
        require(postings.isNotEmpty()) { "transaction has no postings" }
        require(postings.count { it.amount == null } <= 1) {
            "more than one posting without amount"
        }
        if (!balanced()) throw IllegalArgumentException("transaction does not balance")
        return this
    }

    fun format(): String = buildString {
        appendLine("${date} * $payee")
        note?.let { appendLine("    ; $it") }
        postings.forEach { p ->
            if (p.amount == null) {
                appendLine("    ${p.account}")
            } else {
                appendLine("    ${p.account.padEnd(44)} ${"%.2f".format(p.amount)} ${p.commodity}")
            }
        }
    }
}

object LedgerParser {
    private val headerRegex = Regex("""^(\d{4}-\d{2}-\d{2})\s+\*\s+(.*)$""")
    private val postingRegex = Regex(
        """^\s+([A-Za-z][\w:]*)(?:\s{2,}(-?\d+(?:\.\d+)?)(?:\s+(\S+))?)?\s*(?:;.*)?$"""
    )
    private val accountRegex = Regex("""^[A-Z]\w*(:[A-Z]\w*)*$""")

    fun isValidAccount(name: String): Boolean = accountRegex.matches(name)

    fun parseAmount(text: String): Pair<Double, String>? {
        val m = Regex("""^(-?\d+(?:\.\d+)?)(?:\s+(\S+))?$""").matchEntire(text.trim())
            ?: return null
        val value = m.groupValues[1].toDouble()
        val commodity = m.groups[3]?.value ?: "€"
        return value to commodity
    }

    fun parse(text: String): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        var date: LocalDate? = null
        var payee: String? = null
        var note: String? = null
        var postings = mutableListOf<Posting>()

        fun flush() {
            if (date != null && payee != null) {
                transactions += Transaction(date!!, payee!!, postings.toList(), note)
            }
            date = null; payee = null; note = null; postings = mutableListOf()
        }

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";") -> {}
                headerRegex.matchEntire(trimmed)?.let { m ->
                    flush()
                    date = LocalDate.parse(m.groupValues[1])
                    payee = m.groupValues[2].trim()
                } != null -> {}
                line.trimStart().startsWith(";") -> { note = line.trim().removePrefix(";").trim() }
                postingRegex.matchEntire(line)?.let { m ->
                    val account = m.groupValues[1]
                    val amountText = m.groups[2]?.value
                    val commodity = m.groups[3]?.value ?: "€"
                    postings += Posting(account, amountText?.toDouble(), commodity)
                } != null -> {}
                else -> throw IllegalArgumentException("cannot parse line: $line")
            }
        }
        flush()
        return transactions
    }
}
