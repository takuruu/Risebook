"""Tests for the interactive flow, using a scripted stdin."""

import io

from risebook.interactive import collect_transaction
from risebook.journal import Journal


def run_script(lines):
    return collect_transaction(Journal("/dev/null"), stdin=io.StringIO("\n".join(lines) + "\n"))


def test_simple_two_posting_flow():
    txn = run_script(
        [
            "2025-01-15",      # date
            "Bakery",          # payee
            "",                # note (optional)
            "Expenses:Food",   # posting 1 account
            "4.50",            # posting 1 amount
            "Assets:Cash",     # posting 2 account
            "",                # posting 2 amount -> auto-balance
        ]
    )
    assert txn.payee == "Bakery"
    assert txn.postings[1].amount is None
    txn.validate()


def test_auto_stop_when_balanced():
    txn = run_script(
        [
            "2025-01-15",
            "Bakery",
            "",
            "Expenses:Food",
            "4.50",
            "Assets:Cash",
            "-4.50",
            # balanced -> should stop asking without further input
        ]
    )
    assert len(txn.postings) == 2
    txn.validate()


def test_invalid_account_retry():
    txn = run_script(
        [
            "2025-01-15",
            "Bakery",
            "",
            "bad account",     # invalid -> retry
            "Expenses:Food",
            "4.50",
            "Assets:Cash",
            "",
        ]
    )
    assert txn.postings[0].account == "Expenses:Food"
