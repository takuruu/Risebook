package de.takuruu.risebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.data.JournalRepository
import de.takuruu.risebook.ledger.Budget
import de.takuruu.risebook.ledger.LedgerParser

@Composable
fun BudgetScreen(
    repository: JournalRepository,
    modifier: Modifier = Modifier
) {
    val budgets by repository.budgets.collectAsState()
    var showEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (budgets.isEmpty()) {
            Text(
                "Noch keine Budgets.\nTippe auf +, um ein monatliches\nBudget zu erfassen.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            val progress = remember(budgets) { repository.budgetProgress() }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(progress) { (budget, actual, planned) ->
                    BudgetCard(
                        budget = budget,
                        actual = actual,
                        onEdit = { editing = budget },
                        onDelete = { repository.removeBudget(budget.account) }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { showEdit = true },
            modifier = Modifier.padding(16.dp).align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Budget hinzufügen")
        }
    }

    if (showEdit || editing != null) {
        BudgetDialog(
            initial = editing,
            knownAccounts = repository.accounts(),
            onDismiss = { showEdit = false; editing = null },
            onSave = { account, amount ->
                repository.setBudget(account, amount)
                showEdit = false; editing = null
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    actual: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ratio = if (budget.amount > 0) actual / budget.amount else 1.0
    val color = when {
        ratio >= 1.0 -> MaterialTheme.colorScheme.error
        ratio >= 0.8 -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(budget.account, style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen")
                    }
                }
            }
            Text(
                "%.2f € von %.2f € (%d%%)".format(actual, budget.amount, (ratio * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BudgetDialog(
    initial: Budget?,
    knownAccounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (account: String, amount: Double) -> Unit
) {
    var account by remember { mutableStateOf(initial?.account ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.let { "%.2f".format(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neues Budget" else "Budget bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Konto (z. B. Expenses:Groceries)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monatsbudget (z. B. 200)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (knownAccounts.isNotEmpty()) {
                    Text(
                        "Vorhandene Konten: " + knownAccounts.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = LedgerParser.parseAmount(amount)
                when {
                    account.isBlank() -> error = "Konto fehlt"
                    !LedgerParser.isValidAccount(account) ->
                        error = "Kontoname ungültig (Format: Expenses:Groceries)"
                    parsed == null || parsed.first <= 0.0 -> error = "Betrag ungültig"
                    else -> onSave(account.trim(), parsed.first)
                }
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
