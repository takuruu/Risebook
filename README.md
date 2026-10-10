# Risebook

Interactive plain text accounting for Android: record transactions on the go
into a ledger-style journal file.

Risebook keeps your books in a simple, human-readable `.ledger` file — no
database, no account, no lock-in. It gives you a guided entry form for date,
payee and postings, balances the transaction for you, and stores it in classic
ledger format.

## Features

- **Interactive entry**: form for payee, debit account, amount and credit account
- **Auto-balancing**: the credit posting is filled in automatically
- **Transaction list**: your journal, newest first
- **Validation**: rejects unbalanced transactions and invalid account names
- **Plain text**: the journal is a plain `.ledger` file, compatible in spirit
  with ledger/hledger

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Min SDK 26 (Android 8.0), Target SDK 34
- Journal stored locally as `risebook.ledger` in app-internal storage

## Build

Requires JDK 17 and the Android SDK.

```bash
./gradlew assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/`.

## Tests

```bash
./gradlew testDebugUnitTest
```

## Journal format

```ledger
2025-01-15 * Bakery
    Expenses:Food                             4.50 €
    Assets:Cash
```

- Header: `YYYY-MM-DD * Payee`
- Postings: indented `Account:Name` with optional amount; one posting may omit
  the amount (auto-balanced)
- Comments start with `;`

## Roadmap

- [ ] Balance report view
- [ ] Account autocomplete from existing journal
- [ ] Export/share journal file (SAF)
- [ ] CSV import

## License

MIT — see [LICENSE](LICENSE).
