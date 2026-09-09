# Recap -- Blockchain Fundamentals From the Steganography File

--> The Steganography, Blockchain and Post-Quantum Cryptography file introduced blockchain's basic structure (Merkle trees, hash-linked blocks). This file goes deep specifically on the SECURITY dimension -- how blockchains achieve trustless consensus, and the extensive, costly history of smart contract vulnerabilities, a genuinely distinct and important sub-field within applied cryptography and security.

# How a Blockchain Achieves Trust Without a Central Authority

--> A blockchain is fundamentally a distributed ledger -- a record of transactions replicated across many independent computers ("nodes"), with no single central authority controlling it -- the central cryptographic and game-theoretic challenge is getting all these independent, mutually-distrusting nodes to agree on ONE shared, tamper-resistant history.

## Proof of Work -- Security Through Computational Cost

--> Bitcoin's original consensus mechanism -- nodes ("miners") compete to solve a computationally expensive puzzle (finding a hash value below a target threshold, directly connecting to the hash function properties covered in the Hashing and Password Storage file) -- whoever solves it first gets to add the next block and receives a reward.

```
Mining (conceptually): find a "nonce" value such that
    SHA256(block_data + nonce) < target_difficulty

There's no shortcut to find this nonce faster than brute-force trying many values --
this is precisely the hash function's "avalanche effect" and unpredictability property
(covered in the Hashing file) being put to direct cryptoeconomic use.
```

--> **Why this secures the network** -- rewriting past transaction history would require REDOING the computational work for that block AND every block after it, faster than the entire rest of the network is currently adding new blocks -- as long as no single attacker controls more than half the network's total computing power (a "51% attack"), rewriting confirmed history is computationally infeasible, not merely difficult.
--> **The genuine cost** -- Proof of Work's security model consumes enormous real-world electricity, since security literally comes from the sheer scale of wasted computation required to attack the network -- a well-known, significant criticism directly motivating the shift toward Proof of Stake.

## Proof of Stake -- Security Through Economic Skin in the Game

--> Rather than competing via computational work, validators "stake" (lock up) a significant amount of the blockchain's own cryptocurrency as collateral -- the right to propose/validate the next block is awarded based on stake size, and a validator caught acting maliciously (validating fraudulent transactions) has their staked funds partially or fully destroyed ("slashed").
--> **Why this secures the network** -- an attacker attempting to corrupt the ledger would need to acquire and stake an enormous amount of the cryptocurrency itself, and if caught, would lose that entire stake -- security comes from aligning economic self-interest with honest behavior, rather than from computational cost, dramatically reducing energy consumption compared to Proof of Work (this is precisely the change Ethereum made in its well-known 2022 "Merge" transition).

# Smart Contracts -- Programmable, Immutable Agreements

--> A smart contract is code deployed directly ONTO a blockchain (most commonly on Ethereum, using the Solidity language) that executes automatically and exactly as written when triggered, with NO ability for anyone (including the contract's own creator) to alter it after deployment -- this immutability is the entire point (a genuinely trustless, tamper-proof agreement), but it also means any BUG in the deployed code is permanent and can potentially be exploited forever, with no equivalent to a normal application's ability to simply patch and redeploy.

# The DAO Hack -- The Foundational Case Study

--> In 2016, "The DAO" (a smart-contract-based investment fund) was exploited for roughly $60 million worth of cryptocurrency due to a "reentrancy" vulnerability -- one of the most consequential security incidents in blockchain history, significant enough that it caused Ethereum itself to controversially "hard fork" (create a new version of its blockchain history) specifically to reverse the theft, a decision that remains debated precisely because it violated blockchain's core immutability promise.

## Reentrancy -- The Vulnerability Pattern Behind the DAO Hack

--> Occurs when a contract calls an EXTERNAL contract/address (e.g. to send funds) BEFORE updating its own internal state to reflect that the funds were already sent -- a malicious external contract can exploit this ordering by calling BACK into the original function again, DURING that external call, before the original function has recorded that the withdrawal already happened.

```solidity
// Vulnerable pattern (simplified)
function withdraw(uint amount) public {
    require(balances[msg.sender] >= amount);
    (bool success, ) = msg.sender.call{value: amount}("");   // Sends funds FIRST
    require(success);
    balances[msg.sender] -= amount;                            // Updates balance AFTER -- too late!
}
```

--> If `msg.sender` is a malicious contract, its fallback function (automatically triggered upon receiving funds) can call `withdraw()` AGAIN before the first call's `balances[msg.sender] -= amount` line has executed -- since the balance hasn't been decremented yet, the check `balances[msg.sender] >= amount` still passes, letting the attacker drain funds repeatedly in a single transaction, well beyond their actual legitimate balance.

## The Fix -- Checks-Effects-Interactions Pattern

```solidity
// Fixed -- update internal state BEFORE making any external call
function withdraw(uint amount) public {
    require(balances[msg.sender] >= amount);   // CHECKS
    balances[msg.sender] -= amount;              // EFFECTS -- update state first
    (bool success, ) = msg.sender.call{value: amount}("");   // INTERACTIONS -- external call LAST
    require(success);
}
```

--> This "Checks-Effects-Interactions" ordering is now considered a fundamental, mandatory best practice for any smart contract making external calls -- directly analogous in spirit to the parameterized-query discipline covered in the Injection Attacks file: a specific, well-understood pattern that structurally prevents an entire class of vulnerability, rather than trying to catch every individual malicious input case.

# Integer Overflow/Underflow -- A Historically Common Smart Contract Bug

--> Older Solidity versions (before built-in overflow protection was added by default) allowed arithmetic to silently "wrap around" -- subtracting 1 from a value of 0 in an unsigned integer would wrap to the MAXIMUM possible value instead of correctly erroring or going negative, directly connecting to the numeric edge-case bugs covered conceptually in the Buffer Overflow and Memory Safety file, just manifesting in the very different context of on-chain financial logic here.

```solidity
uint8 balance = 0;
balance = balance - 1;   // Pre-fix Solidity: wraps to 255, NOT a negative number or an error
                            // An attacker could exploit this to give themselves an enormous "balance"
```

# Front-Running and MEV (Maximal Extractable Value)

--> Blockchain transactions sit briefly in a public "mempool" before being confirmed -- because this is publicly visible, other network participants (or the actual block-producing miner/validator) can see a pending transaction and insert their OWN transaction ahead of it to profit from the resulting price movement (e.g. seeing a large pending trade and buying first, then selling immediately after the large trade executes and moves the price) -- a form of exploitation entirely unique to blockchain's transparent, publicly-ordered transaction model, without a direct equivalent in traditional web application security.

# Smart Contract Auditing -- The Defensive Discipline

--> Given that deployed contract code is immutable and often controls genuinely significant real financial value, formal security AUDITS before deployment are considered essential, not optional -- specialized firms manually review contract code line-by-line specifically hunting for reentrancy, overflow, and access-control bugs, combined with automated static analysis tools (Slither, Mythril) that scan for known vulnerable patterns automatically, directly paralleling the SAST tooling covered in the DevSecOps and CI-CD Security file, just applied to the specific, higher-stakes context of immutable, financially-consequential blockchain code.

# Why This Sits in the Cryptography Track

--> While smart contract exploitation techniques (reentrancy, overflow) are really application-security bugs rather than cryptographic weaknesses per se, the blockchain infrastructure they run on -- hash functions, digital signatures (covered in the Asymmetric Encryption file), Merkle trees -- is fundamentally built from the cryptographic primitives covered throughout this entire track, making blockchain security a genuine, natural extension of applied cryptography rather than an entirely separate discipline.
