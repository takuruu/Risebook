package de.takuruu.risebook

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import de.takuruu.risebook.data.ImportMode
import de.takuruu.risebook.data.JournalRepository
import de.takuruu.risebook.ledger.Transaction
import de.takuruu.risebook.ui.AddTransactionDialog
import de.takuruu.risebook.ui.BalanceScreen
import de.takuruu.risebook.ui.JournalScreen
import kotlinx.coroutines.launch
import java.io.File

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

private enum class Screen(val title: String) {
    Journal("Journal"),
    Balance("Balance")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RisebookApp(repository: JournalRepository) {
    var screen by remember { mutableStateOf(Screen.Journal) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var importMode by remember { mutableStateOf(ImportMode.Replace) }
    val context = LocalContext.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (text == null) {
                showMessage("Datei konnte nicht gelesen werden")
            } else {
                runCatching { repository.import(text, importMode) }.fold(
                    onSuccess = { count ->
                        showMessage(
                            if (importMode == ImportMode.Replace)
                                "Journal ersetzt: $count Buchungen"
                            else
                                "$count Buchungen angehängt"
                        )
                    },
                    onFailure = { showMessage("Import fehlgeschlagen: ${it.message}") }
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Risebook") },
                actions = {
                    IconButton(onClick = { shareJournal(context, repository.file) }) {
                        Icon(Icons.Default.Share, contentDescription = "Journal teilen/exportieren")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ledger importieren (ersetzen)") },
                            onClick = {
                                menuOpen = false
                                importMode = ImportMode.Replace
                                importLauncher.launch(arrayOf("text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ledger importieren (anhängen)") },
                            onClick = {
                                menuOpen = false
                                importMode = ImportMode.Append
                                importLauncher.launch(arrayOf("text/*"))
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = {
                            Icon(
                                if (s == Screen.Journal) Icons.Default.List else Icons.Default.Add,
                                contentDescription = null
                            )
                        },
                        label = { Text(s.title) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (screen == Screen.Journal) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Buchung hinzufügen")
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (screen) {
            Screen.Journal -> JournalScreen(
                repository = repository,
                modifier = modifier,
                onEdit = { editing = it },
                onDelete = { txn ->
                    repository.delete(txn)
                    showMessage("Buchung gelöscht")
                }
            )
            Screen.Balance -> BalanceScreen(repository, modifier)
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            knownAccounts = repository.accounts(),
            onDismiss = { showAddDialog = false },
            onSave = { txn ->
                repository.append(txn)
                showAddDialog = false
            }
        )
    }

    editing?.let { txn ->
        AddTransactionDialog(
            knownAccounts = repository.accounts(),
            initial = txn,
            onDismiss = { editing = null },
            onSave = { new ->
                runCatching { repository.update(txn, new) }
                    .onFailure { showMessage("Speichern fehlgeschlagen: ${it.message}") }
                editing = null
            }
        )
    }
}

private fun shareJournal(context: android.content.Context, file: File) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "Kein Journal vorhanden", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Journal exportieren"))
}
