# Risebook

Interactive plain text accounting: record transactions from your terminal into a
ledger-style journal file.

Risebook keeps your books in a simple, human-readable `.ledger` file — no
database, no lock-in. It prompts you for date, payee and postings, balances the
transaction for you, and appends it to the journal in classic ledger format.

## Features

- **Interactive `add`**: guided entry for date, payee, note and postings
- **Auto-balancing**: leave one posting amount empty and Risebook fills it in
- **`balance`**: grouped account balances with totals and regex account filter
- **`register`**: full listing of all recorded transactions
- **Validation**: rejects unbalanced transactions and invalid account names
- **Plain text**: the journal is a plain `.ledger` file, compatible in spirit
  with ledger/hledger

## Installation

Requires Python 3.9+.

```bash
pip install .
```

## Usage

Record a transaction interactively (creates `risebook.ledger` if missing):

```console
$ risebook add
Journal: risebook.ledger
Date [YYYY-MM-DD, empty=2025-01-15]:
Payee: Bakery
Note (optional): Lunch
Posting account (empty=done): Expenses:Food
Amount (e.g. 12.50 or -3.00, empty=auto-balance): 4.50
Posting 2 account (empty=done): Assets:Cash
Amount (e.g. 12.50 or -3.00, empty=auto-balance):
Recorded:
2025-01-15 * Bakery
    ; Lunch
    Expenses:Food                             4.50 €
    Assets:Cash
```

Show balances:

```console
$ risebook balance
$ risebook balance --flat
$ risebook balance Grocer      # regex filter
```

Show all transactions:

```console
$ risebook register
```

Use a different journal file:

```console
$ risebook -f mybooks.ledger add
```

## Journal format

```ledger
2025-01-15 * Bakery
    ; Lunch
    Expenses:Food                             4.50 €
    Assets:Cash
```

- Header: `YYYY-MM-DD * Payee`
- Postings: indented `Account:Name` with optional amount; one posting may omit
  the amount (auto-balanced)
- Comments start with `;`

## Development

```bash
pip install -e . pytest
python -m pytest tests/
```

## License

MIT — see [LICENSE](LICENSE).
