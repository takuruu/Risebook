"""Interactive transaction entry for Risebook."""

from __future__ import annotations

import datetime as _dt
import sys
from typing import List, Optional

from .journal import (
    Journal,
    JournalError,
    Posting,
    Transaction,
    parse_amount,
    validate_account,
)

PROMPT = "> "


def _input(prompt: str, stdin=None) -> str:
    stream = stdin if stdin is not None else sys.stdin
    line = stream.readline()
    if not line:
        raise EOFError
    return line.rstrip("\n")


def _ask(prompt: str, *, optional: bool = False, stdin=None) -> Optional[str]:
    value = _input(prompt + PROMPT, stdin).strip()
    if value or optional:
        return value or None
    return None


def _ask_date(stdin=None) -> _dt.date:
    today = _dt.date.today().isoformat()
    raw = _input(f"Date [YYYY-MM-DD, empty={today}]: ".format() + "", stdin)
    raw = raw.strip() if isinstance(raw, str) else raw
    while True:
        if raw == "":
            return _dt.date.today()
        try:
            return _dt.date.fromisoformat(raw)
        except ValueError:
            print("Invalid date, try again.")
            raw = _input("Date [YYYY-MM-DD]: ", stdin).strip()


def _ask_amount(stdin=None):
    while True:
        raw = _input(
            "Amount (e.g. 12.50 or -3.00, empty=auto-balance): ", stdin
        ).strip()
        if raw == "":
            return None, "€"
        try:
            return parse_amount(raw)
        except JournalError as exc:
            print(f"  {exc}")


def _ask_posting(index: int, accounts: List[str], stdin=None) -> Optional[Posting]:
    label = "Posting" if index == 0 else f"Posting {index + 1}"
    while True:
        try:
            raw = _input(f"{label} account (empty=done): ", stdin).strip()
        except EOFError:
            return None
        if not raw:
            return None
        try:
            validate_account(raw)
        except JournalError as exc:
            print(f"  {exc}")
            continue
        if accounts and raw not in accounts:
            hint = ", ".join(accounts[:8])
            print(f"  new account (known: {hint})")
        amount, commodity = _ask_amount(stdin)
        return Posting(account=raw, amount=amount, commodity=commodity)


def collect_transaction(journal: Journal, stdin=None) -> Transaction:
    accounts = journal.accounts() if journal.transactions else []
    date = _ask_date(stdin)
    payee = _input("Payee: ", stdin).strip()
    note = _input("Note (optional): ", stdin).strip()
    postings: List[Posting] = []
    while len(postings) < 10:
        posting = _ask_posting(len(postings), accounts, stdin=stdin)
        if posting is None:
            break
        postings.append(posting)
        filled = [p for p in postings if p.amount is not None]
        if len(filled) >= 2 and abs(sum(p.amount for p in filled)) < 1e-9:
            break
    txn = Transaction(date=date, payee=payee, postings=postings, note=note or None)
    txn.validate()
    return txn


def run_add(journal: Journal, stdin=None) -> None:
    print(f"Journal: {journal.path}")
    txn = collect_transaction(journal, stdin=stdin)
    journal.append_to_file(txn)
    print(f"Recorded:\n{txn.format()}")
