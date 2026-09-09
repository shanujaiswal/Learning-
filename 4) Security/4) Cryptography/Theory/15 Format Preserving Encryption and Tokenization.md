# The Problem Standard Encryption Creates

--> AES-encrypted output (covered in the Symmetric Encryption file) is a block of essentially random-looking bytes, in ciphertext that's typically a fixed block size regardless of the input -- but a credit card number is expected to remain a 16-digit number, a date field expects a valid date format, a legacy database column has a fixed `CHAR(9)` width for a Social Security Number. Dropping normal AES ciphertext into these fields breaks application logic, database schemas, and validation rules that assume a specific format.

# Format-Preserving Encryption (FPE)

--> FPE encrypts data while preserving its original FORMAT -- a 16-digit card number encrypts to another value that is STILL a valid-looking 16-digit number, letting encrypted data flow through existing systems, validation logic, and database columns designed for the original format, without requiring schema or application changes.

```
Original card number:    4532 0151 1283 0366
FPE-encrypted (fake):     8817 4402 9951 7723    <-- still 16 digits, still passes Luhn-style format checks
```

--> Built on standard, well-vetted primitives underneath (typically AES used in specific constructions like FF1/FF3-1, standardized by NIST) -- FPE isn't a new, unvetted cryptographic primitive, just AES adapted with clever techniques to constrain its output to a specific format/alphabet.

# Tokenization -- A Related but Distinct Approach

--> Tokenization replaces sensitive data with a randomly generated, non-mathematically-related TOKEN, storing the real value in a secure, separate "token vault" -- unlike FPE, the token has NO cryptographic relationship to the original value at all; you can't "decrypt" a token without consulting the vault.

```
Real card number: 4532 0151 1283 0366
Token stored elsewhere: TOK_9f8a7b2c1d
                          (vault maps this token back to the real number, when authorized)
```

--> Tokenization vs FPE trade-off -- tokenization requires a centralized vault (a single, well-protected point managing the real mappings) but offers arguably stronger protection since tokens carry zero mathematical relationship to the original data (nothing to attack cryptanalytically); FPE avoids needing a vault/lookup at all, since the encrypted value is self-contained and reversible with the right key, but that reversibility is itself an attack surface if the key is compromised.

# Why the Payment Industry Relies on These

--> PCI-DSS (referenced in the GRC/Compliance file in the Cyber Security track) requires protecting cardholder data -- FPE and tokenization are the two standard techniques letting a company avoid ever storing/processing REAL card numbers in most of its systems, dramatically shrinking the scope of infrastructure that must meet PCI-DSS's strictest requirements (only the vault or the FPE key management system needs that level of scrutiny, not every downstream system that merely needs a card-number-shaped value to function).

# Practical Example -- Tokenizing in a Payment Flow

```python
def tokenize_card_number(card_number, vault):
    token = f"TOK_{secrets.token_hex(8)}"   # Cryptographically random, unrelated to the real value
    vault.store(token, card_number)          # Real number stored ONLY in the secure vault
    return token

def process_payment(token, vault, payment_processor):
    real_card_number = vault.retrieve(token)   # Only the vault-authorized service can reverse this
    return payment_processor.charge(real_card_number)
```

--> Correction note: the `tokenize_card_number` example above calls `secrets.token_hex(8)` but the snippet never imports the module -- to actually run this code, add `import secrets` at the top of the file/script before this function is defined.

--> Every other system in the application (order records, customer support tools, analytics) only ever sees and stores the TOKEN -- meaningfully reducing the number of places a real card number could ever be exposed or leaked from.

# Limitations to Keep in Mind

--> FPE with a small input space (e.g. encrypting a single-digit field, or a field with very few possible valid values) can be vulnerable to brute-force guessing of the entire possible input space, regardless of how strong the underlying cipher is -- format preservation sometimes means preserving a small space, and small spaces are inherently easier to exhaustively search.
