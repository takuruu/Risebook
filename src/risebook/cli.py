"""Command line interface for Risebook."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Optional

from . import __version__
from . import interactive
from .journal import (
    Journal,
    JournalError,
    balance_report,
    filter_accounts,
    parse_journal,
    top_level,
)

DEFAULT_FILE = "risebook.ledger"


def _journal_path(args) -> Path:
    return Path(args.file) if args.file else Path(DEFAULT_FILE)


def _fmt(amount: float) -> str:
    return f"{amount:>12,.2f}".replace(",", "")


def cmd_balance(args) -> int:
    journal = parse_journal(_journal_path(args))
    totals = balance_report(journal)
    totals = filter_accounts(totals, args.account)
    if not totals:
        print("No matching accounts.")
        return 0
    if args.flat:
        for account, amount in sorted(totals.items()):
            print(f"{account:<44} {_fmt(amount)} €")
        return 0
    grouped: dict = {}
    for account, amount in sorted(totals.items()):
        grouped.setdefault(top_level(account), {})[account] = amount
    grand = 0.0
    for group, entries in sorted(grouped.items()):
        print(group)
        for account, amount in entries.items():
            print(f"    {account:<40} {_fmt(amount)} €")
        group_total = sum(entries.values())
        grand += group_total
        print(f"    {'-' * 40}")
        print(f"    {'Total ' + group:<40} {_fmt(group_total)} €")
    print(f"{'=' * 56}")
    print(f"{'Grand total':<44} {_fmt(grand)} €")
    return 0


def cmd_register(args) -> int:
    journal = parse_journal(_journal_path(args))
    for txn in journal.transactions:
        print(f"{txn.date.isoformat()} {txn.payee}")
        for p in txn.postings:
            if p.amount is None:
                p.amount = -sum(
                    q.amount for q in txn.postings if q.amount is not None
                )
            print(f"    {p.account:<40} {_fmt(p.amount)} €")
        print()
    return 0


def cmd_add(args) -> int:
    path = _journal_path(args)
    journal = parse_journal(path) if path.exists() else Journal(path)
    interactive.run_add(journal)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="risebook",
        description="Interactive plain text accounting with a ledger-style journal.",
    )
    parser.add_argument("--version", action="version", version=f"risebook {__version__}")
    parser.add_argument(
        "-f", "--file", default=None, help="journal file (default: risebook.ledger)"
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_add = sub.add_parser("add", help="interactively record a transaction")
    p_add.set_defaults(func=cmd_add)

    p_bal = sub.add_parser("balance", aliases=["bal"], help="show account balances")
    p_bal.add_argument("account", nargs="?", default=None, help="regex filter")
    p_bal.add_argument("--flat", action="store_true", help="flat list, no groups")
    p_bal.set_defaults(func=cmd_balance)

    p_reg = sub.add_parser("register", aliases=["reg"], help="show all transactions")
    p_reg.set_defaults(func=cmd_register)

    return parser


def main(argv: Optional[list] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except JournalError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    except FileNotFoundError as exc:
        print(f"error: journal not found: {exc.filename}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
