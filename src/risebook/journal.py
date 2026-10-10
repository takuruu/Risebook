"""Core journal, transaction and account models for Risebook."""

from __future__ import annotations

import datetime as _dt
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional

ACCOUNT_RE = re.compile(r"^[A-Z][A-Za-z0-9]*(:[A-Z][A-Za-z0-9]*)*$")
_AMOUNT_RE = re.compile(
    r"^(?P<sign>-?)(?P<value>\d+(?:\.\d+)?)(?:\s+(?P<commodity>[A-Za-z€$£].*))?$"
)


class JournalError(Exception):
    """Raised when a journal cannot be parsed or is invalid."""


def validate_account(name: str) -> None:
    if not ACCOUNT_RE.match(name):
        raise JournalError(
            f"invalid account name: {name!r} "
            "(expected e.g. 'Expenses:Groceries', each part capitalised)"
        )


@dataclass
class Posting:
    account: str
    amount: Optional[float] = None  # None => unbalanced remainder
    commodity: str = "€"
    comment: Optional[str] = None

    def format(self, indent: int = 4) -> str:
        pad = " " * indent
        if self.amount is None:
            return f"{pad}{self.account}"
        value = f"{self.amount:,.2f}".replace(",", "")
        line = f"{pad}{self.account:<40}  {value} {self.commodity}"
        if self.comment:
            line += f"  ; {self.comment}"
        return line.rstrip()


@dataclass
class Transaction:
    date: _dt.date
    payee: str
    postings: List[Posting] = field(default_factory=list)
    note: Optional[str] = None

    def validate(self) -> None:
        if not self.postings:
            raise JournalError("transaction has no postings")
        empty = [p for p in self.postings if p.amount is None]
        if len(empty) > 1:
            raise JournalError("more than one empty posting amount")
        if empty:
            balance = sum(p.amount for p in self.postings if p.amount is not None)
            if abs(balance) < 1e-9:
                raise JournalError(
                    "empty posting present although transaction already balances"
                )
        else:
            commodities = {p.commodity for p in self.postings}
            balance = sum(p.amount for p in self.postings)
            if commodities != {"€"} and len(commodities) > 1:
                raise JournalError("mixed commodities in one transaction")
            if abs(balance) > 1e-9:
                raise JournalError(f"transaction does not balance ({balance:+.2f})")

    def format(self) -> str:
        self.validate()
        lines = [f"{self.date.isoformat()} * {self.payee}"]
        if self.note:
            lines.append(f"    ; {self.note}")
        lines.extend(p.format() for p in self.postings)
        return "\n".join(lines)


class Journal:
    def __init__(self, path: Path) -> None:
        self.path = Path(path)
        self.transactions: List[Transaction] = []

    def append(self, txn: Transaction) -> None:
        txn.validate()
        self.transactions.append(txn)

    def append_to_file(self, txn: Transaction) -> None:
        """Validate and append a transaction to the journal file."""
        self.append(txn)
        text = txn.format()
        with self.path.open("a", encoding="utf-8") as fh:
            fh.write("\n" + text + "\n")

    def accounts(self) -> List[str]:
        names: Dict[str, None] = {}
        for txn in self.transactions:
            for p in txn.postings:
                validate_account(p.account)
                names[p.account] = None
        return list(names)


def parse_amount(text: str) -> tuple[float, str]:
    m = _AMOUNT_RE.match(text.strip())
    if not m:
        raise JournalError(f"cannot parse amount: {text!r}")
    value = float(m.group("value"))
    if m.group("sign") == "-":
        value = -value
    commodity = m.group("commodity") or "€"
    return value, commodity


def parse_journal(path: Path) -> Journal:
    """Parse a minimal ledger journal: date-star-payee headers and posting lines."""
    journal = Journal(path)
    current: Optional[Transaction] = None
    text = Path(path).read_text(encoding="utf-8")
    for lineno, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith(";") or line.startswith("#"):
            continue
        header = re.match(r"^(\d{4}-\d{2}-\d{2})\s+\*\s+(.*)$", line)
        if header:
            if current is not None:
                journal.append(current)
            try:
                date = _dt.date.fromisoformat(header.group(1))
            except ValueError as exc:
                raise JournalError(f"line {lineno}: invalid date") from exc
            current = Transaction(date=date, payee=header.group(2).strip())
            continue
        if current is None:
            raise JournalError(f"line {lineno}: posting outside transaction")
        posting = re.match(r"^([\s]+)([A-Za-z][^;]*?)(?:\s{2,}(-?[\d.]+)(?:\s+(.+))?)?$", raw)
        if not posting:
            raise JournalError(f"line {lineno}: cannot parse posting")
        account = posting.group(2).strip()
        validate_account(account)
        amount: Optional[float]
        commodity = "€"
        if posting.group(3):
            amount = float(posting.group(3))
            commodity = posting.group(4) or "€"
        else:
            amount = None
        current.postings.append(Posting(account=account, amount=amount, commodity=commodity))
    if current is not None:
        journal.append(current)
    return journal


def balance_report(journal: Journal) -> Dict[str, float]:
    totals: Dict[str, float] = {}
    for txn in journal.transactions:
        for p in txn.postings:
            if p.amount is None:
                balance = sum(q.amount for q in txn.postings if q.amount is not None)
                p.amount = -balance
            totals[p.account] = totals.get(p.account, 0.0) + p.amount
    return totals


def top_level(account: str) -> str:
    return account.split(":")[0]


def filter_accounts(totals: Dict[str, float], pattern: Optional[str]) -> Dict[str, float]:
    if not pattern:
        return dict(totals)
    return {
        acc: amt
        for acc, amt in totals.items()
        if re.search(pattern, acc, re.IGNORECASE)
    }
