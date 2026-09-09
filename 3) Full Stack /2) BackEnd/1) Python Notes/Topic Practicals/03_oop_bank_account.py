"""
03_oop_bank_account.py

Maps to Theory chapter:
    09 OOP Concepts.md

Demonstrates:
    - Inheritance (BankAccount -> SavingsAccount)
    - Encapsulation via _protected and __private attributes
    - A classmethod alternate constructor
    - A staticmethod utility
    - @property for a computed value

No external dependencies. Run directly:
    python "03_oop_bank_account.py"
"""

from __future__ import annotations
from datetime import datetime


class InsufficientFundsError(Exception):
    """Raised when a withdrawal would overdraw the account."""


class BankAccount:
    """A simple bank account demonstrating encapsulation."""

    # Class attribute shared by all instances.
    bank_name: str = "WarpX National Bank"

    def __init__(self, owner: str, balance: float = 0.0) -> None:
        self.owner = owner                 # public attribute
        self._balance = balance            # protected: internal use / subclasses
        self.__pin = "0000"                # private: name-mangled, only this class touches it
        self._transaction_log: list[str] = []
        self._created_at = datetime.now()

    # -- encapsulated access to the private pin --------------------------
    def set_pin(self, old_pin: str, new_pin: str) -> None:
        if old_pin != self.__pin:
            raise PermissionError("Incorrect PIN.")
        self.__pin = new_pin

    def _log(self, message: str) -> None:
        """Protected helper: intended for use by this class and subclasses only."""
        self._transaction_log.append(message)

    # -- public operations -------------------------------------------------
    def deposit(self, amount: float) -> None:
        if amount <= 0:
            raise ValueError("Deposit amount must be positive.")
        self._balance += amount
        self._log(f"Deposited {amount:.2f}")

    def withdraw(self, amount: float) -> None:
        if amount <= 0:
            raise ValueError("Withdrawal amount must be positive.")
        if amount > self._balance:
            raise InsufficientFundsError(
                f"Cannot withdraw {amount:.2f}; balance is only {self._balance:.2f}"
            )
        self._balance -= amount
        self._log(f"Withdrew {amount:.2f}")

    @property
    def balance(self) -> float:
        """Computed read-only view of the balance (no direct external mutation)."""
        return round(self._balance, 2)

    @property
    def statement(self) -> str:
        """A computed property summarizing the account."""
        lines = "\n".join(f"  - {entry}" for entry in self._transaction_log) or "  (no transactions yet)"
        return f"Statement for {self.owner} ({self.bank_name}):\n{lines}\n  Balance: {self.balance:.2f}"

    # -- alternate constructor ---------------------------------------------
    @classmethod
    def from_dict(cls, data: dict) -> "BankAccount":
        """Alternate constructor: build an account from a dict, e.g. loaded from JSON."""
        return cls(owner=data["owner"], balance=data.get("balance", 0.0))

    # -- utility that doesn't need instance/class state ---------------------
    @staticmethod
    def is_valid_amount(amount: float) -> bool:
        """Utility check with no dependency on instance or class state."""
        return isinstance(amount, (int, float)) and amount > 0

    def __repr__(self) -> str:
        return f"{self.__class__.__name__}(owner={self.owner!r}, balance={self.balance})"


class SavingsAccount(BankAccount):
    """A savings account that accrues interest -- demonstrates inheritance."""

    def __init__(self, owner: str, balance: float = 0.0, interest_rate: float = 0.03) -> None:
        super().__init__(owner, balance)
        self.interest_rate = interest_rate

    def apply_interest(self) -> None:
        interest = self._balance * self.interest_rate
        self._balance += interest
        self._log(f"Applied interest: +{interest:.2f} at {self.interest_rate:.0%}")

    def withdraw(self, amount: float) -> None:
        """Override: savings accounts charge a small withdrawal fee."""
        fee = 1.5
        super().withdraw(amount + fee)
        self._log(f"Charged withdrawal fee: {fee:.2f}")


def main() -> None:
    print("=== Base BankAccount ===")
    acc = BankAccount("Vanisha", 1000.0)
    acc.deposit(250)
    acc.withdraw(100)
    print(acc)
    print(acc.statement)

    print("\n=== Encapsulation: private PIN ===")
    acc.set_pin("0000", "4321")
    try:
        acc.set_pin("wrong-old-pin", "9999")
    except PermissionError as exc:
        print("Caught expected error:", exc)

    print("\n=== Insufficient funds ===")
    try:
        acc.withdraw(999999)
    except InsufficientFundsError as exc:
        print("Caught expected error:", exc)

    print("\n=== classmethod alternate constructor ===")
    loaded = BankAccount.from_dict({"owner": "Ravi", "balance": 500})
    print(loaded)

    print("\n=== staticmethod utility ===")
    print("is_valid_amount(50):", BankAccount.is_valid_amount(50))
    print("is_valid_amount(-5):", BankAccount.is_valid_amount(-5))

    print("\n=== Inheritance: SavingsAccount ===")
    savings = SavingsAccount("Meera", 2000.0, interest_rate=0.05)
    savings.apply_interest()
    savings.withdraw(100)  # withdraws 100 + 1.5 fee
    print(savings)
    print(savings.statement)


if __name__ == "__main__":
    main()
