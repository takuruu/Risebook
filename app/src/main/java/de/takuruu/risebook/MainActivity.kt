package de.takuruu.risebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.data.JournalRepository
import de.takuruu.risebook.ledger.LedgerParser
import de.takuruu.risebook.ledger.Posting
import de.takuruu.risebook.ledger.Transaction
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = JournalRepository(this)
        setContent {
            MaterialTheme {
                RisebookApp(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RisebookApp(repository: JournalRepository) {
    val transactions by repository.transactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Risebook") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Noch keine Buchungen.\nTippe auf +, um deine erste\nBuchung zu erfassen.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(transactions.asReversed()) { txn ->
                    TransactionCard(txn)
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onSave = { txn ->
                repository.append(txn)
                showAddDialog = false
            }
        )
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

@Composable
fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (Transaction) -> Unit) {
    var payee by remember { mutableStateOf("") }
    var account1 by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var account2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Buchung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    label = { Text("Payee") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = account1,
                    onValueChange = { account1 = it },
                    label = { Text("Soll-Konto (z. B. Expenses:Groceries)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Betrag (z. B. 12.50)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = account2,
                    onValueChange = { account2 = it },
                    label = { Text("Haben-Konto (leer = Assets:Cash)") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = LedgerParser.parseAmount(amount)
                when {
                    payee.isBlank() -> error = "Payee fehlt"
                    account1.isBlank() -> error = "Soll-Konto fehlt"
                    parsed == null -> error = "Betrag ungültig"
                    else -> {
                        val acc2 = account2.ifBlank { "Assets:Cash" }
                        if (!LedgerParser.isValidAccount(account1) || !LedgerParser.isValidAccount(acc2)) {
                            error = "Kontoname ungültig (Format: Expenses:Groceries)"
                        } else {
                            onSave(
                                Transaction(
                                    date = LocalDate.now(),
                                    payee = payee.trim(),
                                    postings = listOf(
                                        Posting(account1.trim(), parsed.first, parsed.second),
                                        Posting(acc2.trim())
                                    )
                                )
                            )
                        }
                    }
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}


