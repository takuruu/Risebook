package de.takuruu.risebook.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.data.JournalRepository

@Composable
fun BalanceScreen(repository: JournalRepository, modifier: Modifier = Modifier) {
    val transactions by repository.transactions.collectAsState()
    var filter by remember { mutableStateOf("") }

    val balances = remember(transactions, filter) {
        val totals = repository.balances()
        if (filter.isBlank()) totals
        else totals.filterKeys { it.contains(filter, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Konto filtern") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )
        if (balances.isEmpty()) {
            Text(
                if (transactions.isEmpty()) "Noch keine Buchungen." else "Keine Treffer.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            val grouped = balances.entries.groupBy({ it.key.substringBefore(":") })
            LazyColumn {
                grouped.forEach { (top, entries) ->
                    item {
                        Text(
                            top,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                        )
                    }
                    items(entries.toList()) { (account, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                        ) {
                            Text(account, style = MaterialTheme.typography.bodyLarge)
                            Text("%.2f €".format(amount), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    item {
                        val total = entries.sumOf { it.value }
                        Text(
                            "Summe $top: %.2f €".format(total),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
