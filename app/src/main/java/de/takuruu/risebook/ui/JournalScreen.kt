package de.takuruu.risebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.data.JournalRepository
import de.takuruu.risebook.ledger.Transaction

@Composable
fun JournalScreen(repository: JournalRepository, modifier: Modifier = Modifier) {
    val transactions by repository.transactions.collectAsState()

    if (transactions.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Noch keine Buchungen.\nTippe auf +, um deine erste\nBuchung zu erfassen.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(transactions.asReversed()) { txn ->
            TransactionCard(txn)
        }
    }
}

@Composable
fun TransactionCard(txn: Transaction) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${txn.date} ${txn.payee}", style = MaterialTheme.typography.titleMedium)
            txn.postings.forEach { p ->
                val amountText = p.amount?.let { "%.2f %s".format(it, p.commodity) } ?: "auto"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.account, style = MaterialTheme.typography.bodyMedium)
                    Text(amountText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
