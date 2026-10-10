"""Tests for risebook.journal."""

import datetime as _dt
from pathlib import Path

import pytest

from risebook.journal import (
    Journal,
    JournalError,
    Posting,
    Transaction,
    balance_report,
    filter_accounts,
    parse_amount,
    parse_journal,
    validate_account,
)


def make_txn(**overrides) -> Transaction:
    defaults = dict(
        date=_dt.date(2025, 1, 1),
        payee="Grocery store",
        postings=[
            Posting("Expenses:Groceries", 50.0),
            Posting("Assets:Cash", -50.0),
        ],
    )
    defaults.update(overrides)
    return Transaction(**defaults)


class TestAccountValidation:
    def test_valid(self):
        validate_account("Expenses:Groceries")

    def test_invalid_lowercase(self):
        with pytest.raises(JournalError):
            validate_account("expenses:groceries")

    def test_invalid_empty_part(self):
        with pytest.raises(JournalError):
            validate_account("Expenses::Groceries")


class TestTransaction:
    def test_balanced(self):
        make_txn().validate()

    def test_unbalanced(self):
        txn = make_txn(
            postings=[Posting("Expenses:Groceries", 50.0), Posting("Assets:Cash", -40.0)]
        )
        with pytest.raises(JournalError):
            txn.validate()

    def test_empty_posting_balances(self):
        txn = make_txn(postings=[Posting("Expenses:Groceries", 50.0), Posting("Assets:Cash")])
        txn.validate()

    def test_two_empty_postings_rejected(self):
        txn = make_txn(postings=[Posting("A:B"), Posting("C:D")])
        with pytest.raises(JournalError):
            txn.validate()

    def test_no_postings_rejected(self):
        txn = make_txn(postings=[])
        with pytest.raises(JournalError):
            txn.validate()


class TestAmountParsing:
    def test_simple(self):
        assert parse_amount("12.50") == (12.50, "€")

    def test_negative(self):
        assert parse_amount("-3.00") == (-3.00, "€")

    def test_custom_commodity(self):
        value, commodity = parse_amount("10 USD")
        assert value == 10.0 and commodity == "USD"

    def test_invalid(self):
        with pytest.raises(JournalError):
            parse_amount("abc")


class TestJournalRoundtrip:
    def test_format_parse(self, tmp_path: Path):
        path = tmp_path / "test.ledger"
        journal = Journal(path)
        journal.append_to_file(make_txn())
        parsed = parse_journal(path)
        assert len(parsed.transactions) == 1
        txn = parsed.transactions[0]
        assert txn.payee == "Grocery store"
        assert txn.postings[0].account == "Expenses:Groceries"

    def test_parse_empty_posting(self, tmp_path: Path):
        path = tmp_path / "t.ledger"
        path.write_text(
            "2025-01-01 * Grocery store\n"
            "    Expenses:Groceries          50.00 €\n"
            "    Assets:Cash\n"
        )
        journal = parse_journal(path)
        assert journal.transactions[0].postings[1].amount is None


class TestReports:
    def test_balance_report(self, tmp_path: Path):
        path = tmp_path / "t.ledger"
        journal = Journal(path)
        journal.append(make_txn())
        journal.append(
            make_txn(
                date=_dt.date(2025, 1, 2),
                payee="Bus",
                postings=[Posting("Expenses:Transport", 3.0), Posting("Assets:Cash", -3.0)],
            )
        )
        totals = balance_report(journal)
        assert totals["Assets:Cash"] == -53.0
        assert totals["Expenses:Groceries"] == 50.0

    def test_filter(self):
        totals = {"Expenses:Groceries": 50.0, "Assets:Cash": -50.0}
        filtered = filter_accounts(totals, "grocer")
        assert list(filtered) == ["Expenses:Groceries"]
