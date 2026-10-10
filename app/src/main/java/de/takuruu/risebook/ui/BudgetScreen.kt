package de.takuruu.risebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.data.JournalRepository
import de.takuruu.risebook.ledger.Envelope
import de.takuruu.risebook.ledger.LedgerParser
import java.time.LocalDate

@Composable
fun BudgetScreen(
    repository: JournalRepository,
    modifier: Modifier = Modifier
) {
    val transactions by repository.transactions.collectAsState()
    var showAssign by remember { mutableStateOf(false) }

    val envelopes = remember(transactions) { repository.envelopes() }
    val available = remember(transactions) { repository.availableToBudget() }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Verfügbar zum Zuweisen (dieser Monat)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "%.2f €".format(available),
                    style = MaterialTheme.typography.headlineSmall
                )
                TextButton(onClick = { showAssign = true }) {
                    Text("Geld in Umschlag zuweisen")
                }
            }
        }

        if (envelopes.isEmpty()) {
            Text(
                "Noch keine Umschläge.\nWeise Geld zu, z. B. Budget:Food 200 €,\num einen Umschlag zu starten.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(envelopes) { envelope ->
                    EnvelopeCard(envelope)
                }
            }
        }
    }

    if (showAssign) {
        AssignDialog(
            knownAccounts = repository.accounts(),
            onDismiss = { showAssign = false },
            onSave = { account, amount, date ->
                runCatching { repository.assignToEnvelope(account, amount, date) }
                showAssign = false
            }
        )
    }
}

@Composable
private fun EnvelopeCard(envelope: Envelope) {
    val ratio = if (envelope.assignedTotal > 0)
        envelope.spentTotal / envelope.assignedTotal else 0.0
    val available = envelope.available
    val color = when {
        available < 0 -> MaterialTheme.colorScheme.error
        ratio >= 1.0 -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(envelope.account, style = MaterialTheme.typography.titleMedium)
                Text(
                    "verfügbar: %.2f €".format(available),
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
            }
            Text(
                "zugewiesen: %.2f € | ausgegeben: %.2f €".format(
                    envelope.assignedTotal, envelope.spentTotal
                ) + if (envelope.assignedThisMonth > 0)
                    " | dieser Monat: %.2f € zugewiesen, %.2f € ausgegeben".format(
                        envelope.assignedThisMonth, envelope.spentThisMonth
                    ) else "",
                style = MaterialTheme.typography.bodySmall
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
private fun AssignDialog(
    knownAccounts: List<String>,
    onDismiss: () -> Unit,
    onSave: (account: String, amount: Double, date: LocalDate) -> Unit
) {
    var account by remember { mutableStateOf("Budget:Food") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Geld zuweisen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Umschlag (z. B. Budget:Food)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Betrag (z. B. 200)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Datum (Zuweisungsmonat)") },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Ändern") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = LedgerParser.parseAmount(amount)
                when {
                    !account.startsWith("Budget:") -> error = "Umschlag muss mit Budget: beginnen"
                    !LedgerParser.isValidAccount(account) ->
                        error = "Kontoname ungültig (Format: Budget:Food)"
                    parsed == null || parsed.first <= 0.0 -> error = "Betrag ungültig"
                    else -> onSave(account.trim(), parsed.first, date)
                }
            }) { Text("Zuweisen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    if (showDatePicker) {
        DatePickerField(
            initial = date,
            onConfirm = { date = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}
