package de.takuruu.risebook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.takuruu.risebook.ledger.LedgerParser
import de.takuruu.risebook.ledger.Posting
import de.takuruu.risebook.ledger.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    knownAccounts: List<String>,
    initial: Transaction? = null,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit
) {
    var payee by remember { mutableStateOf(initial?.payee ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var account1 by remember {
        mutableStateOf(initial?.postings?.firstOrNull()?.account ?: "")
    }
    var amount by remember {
        mutableStateOf(
            initial?.postings?.firstOrNull()?.amount?.let { "%.2f".format(it) } ?: ""
        )
    }
    var account2 by remember {
        mutableStateOf(initial?.postings?.getOrNull(1)?.account?.takeIf { acc ->
            initial?.postings?.getOrNull(1)?.amount == null && acc != "Assets:Cash"
        } ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Buchung" else "Buchung bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Datum") },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Ändern") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it },
                    label = { Text("Payee") },
                    modifier = Modifier.fillMaxWidth()
                )
                AccountField(
                    label = "Soll-Konto (z. B. Expenses:Groceries)",
                    value = account1,
                    knownAccounts = knownAccounts,
                    onValueChange = { account1 = it }
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Betrag (z. B. 12.50)") },
                    modifier = Modifier.fillMaxWidth()
                )
                AccountField(
                    label = "Haben-Konto (leer = Assets:Cash)",
                    value = account2,
                    knownAccounts = knownAccounts,
                    onValueChange = { account2 = it }
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
                        if (!LedgerParser.isValidAccount(account1) ||
                            !LedgerParser.isValidAccount(acc2)
                        ) {
                            error = "Kontoname ungültig (Format: Expenses:Groceries)"
                        } else {
                            onSave(
                                Transaction(
                                    date = date,
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

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountField(
    label: String,
    value: String,
    knownAccounts: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = knownAccounts.filter {
        it.contains(value, ignoreCase = true) && it != value
    }.take(6)

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account) },
                    onClick = { onValueChange(account); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
