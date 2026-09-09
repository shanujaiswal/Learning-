# The Naive String Matching Baseline

--> "Find pattern `p` inside text `t`" is common enough (search boxes, `grep`, DNA sequence scanning) that its naive solution's inefficiency is worth seeing explicitly before the faster algorithms below make sense.

```python
def naive_search(text, pattern):
    n, m = len(text), len(pattern)
    matches = []
    for i in range(n - m + 1):           # try every possible starting position
        if text[i:i + m] == pattern:      # O(m) comparison at each position
            matches.append(i)
    return matches                        # O(n*m) overall -- the part every algorithm below improves on
```

--> **Why it's slow** -- in the worst case (e.g. text `"aaaa...a"` searching for pattern `"aaa...b"`), nearly every one of the `n` starting positions requires comparing almost all `m` pattern characters before failing, giving `O(n*m)` -- every algorithm below exists specifically to avoid re-examining text characters that a smarter algorithm can prove don't need re-checking.

# KMP (Knuth-Morris-Pratt) Pattern Matching

--> KMP avoids re-scanning text by precomputing, for the PATTERN itself, how much of a partial match can be REUSED after a mismatch -- the key insight is that a partial match already reveals information about the pattern's own internal structure, and throwing that information away (as naive search does, restarting from scratch) is wasted work.
--> **The failure function (`lps` -- longest proper prefix that's also a suffix)** -- for each position in the pattern, `lps[i]` stores the length of the longest prefix of the pattern that is ALSO a suffix ending at position `i` -- this tells the algorithm exactly how far to fall back to on a mismatch, without ever re-reading a text character it already looked at.

```python
def build_lps(pattern):
    m = len(pattern)
    lps = [0] * m
    length = 0                     # length of the previous longest prefix-suffix
    i = 1
    while i < m:
        if pattern[i] == pattern[length]:
            length += 1
            lps[i] = length
            i += 1
        elif length != 0:
            length = lps[length - 1]     # fall back within the pattern -- no text re-reading involved
        else:
            lps[i] = 0
            i += 1
    return lps

def kmp_search(text, pattern):
    n, m = len(text), len(pattern)
    if m == 0:
        return []
    lps = build_lps(pattern)
    matches = []
    i = j = 0                       # i indexes text, j indexes pattern
    while i < n:
        if text[i] == pattern[j]:
            i += 1
            j += 1
            if j == m:
                matches.append(i - j)
                j = lps[j - 1]        # look for the next match, reusing partial-match info
        elif j != 0:
            j = lps[j - 1]            # mismatch -- fall back using the failure function, not from scratch
        else:
            i += 1
    return matches
```

--> **Why this achieves `O(n + m)`** -- `i` (the text pointer) never moves backward, ever -- every text character is examined a bounded number of times total, and the `O(m)` preprocessing of `lps` is paid once, up front -- a strict improvement over naive search's `O(n*m)`, with no asymptotic trade-off cost at all.

# Rabin-Karp -- Rolling Hash Pattern Matching

--> Rabin-Karp's approach is entirely different from KMP's -- instead of exploiting the pattern's internal structure, it computes a HASH of the pattern once, then slides a window across the text computing each window's hash INCREMENTALLY (a "rolling hash"), only falling back to an actual character comparison when hashes match (to rule out a hash collision).
--> **The rolling hash trick** -- given the hash of the window at position `i`, the hash of the window at position `i + 1` can be computed in `O(1)` by removing the contribution of the character that just left the window and adding the contribution of the new character entering it -- avoiding recomputing the whole window's hash from scratch every time.

```python
def rabin_karp_search(text, pattern, base=256, mod=10 ** 9 + 7):
    n, m = len(text), len(pattern)
    if m > n:
        return []

    pattern_hash = 0
    window_hash = 0
    h = 1                                # base^(m-1) % mod -- needed to remove the leading character later
    for i in range(m - 1):
        h = (h * base) % mod

    for i in range(m):
        pattern_hash = (pattern_hash * base + ord(pattern[i])) % mod
        window_hash = (window_hash * base + ord(text[i])) % mod

    matches = []
    for i in range(n - m + 1):
        if pattern_hash == window_hash:                # hash matches -- verify to rule out a collision
            if text[i:i + m] == pattern:
                matches.append(i)
        if i < n - m:
            # slide the window: drop text[i]'s contribution, add text[i+m]
            window_hash = ((window_hash - ord(text[i]) * h) * base + ord(text[i + m])) % mod
            window_hash %= mod
    return matches
```

--> **Why verification after a hash match is non-negotiable** -- two different substrings CAN produce the same hash (a collision) purely by chance -- skipping the direct character comparison would silently accept false positives, so Rabin-Karp's correctness depends on treating a hash match as only a CANDIDATE, not a confirmed match.
--> **KMP vs Rabin-Karp, practically** -- KMP has a clean worst-case `O(n + m)` with no collision risk at all; Rabin-Karp is average-case `O(n + m)` too but degrades toward `O(n*m)` if collisions are frequent (rare with a good hash/modulus), and its real strength is generalizing easily to MULTIPLE pattern search (hash all patterns once, check every window's hash against the whole set) and 2D pattern matching, which KMP doesn't extend to as naturally.

# Z-Algorithm

--> Computes, for every position `i` in a string, the length of the longest substring starting at `i` that matches a PREFIX of the string itself -- this single array (the "Z-array") answers a surprising number of string questions at once, including pattern matching.

```python
def z_array(s):
    n = len(s)
    z = [0] * n
    z[0] = n                      # by convention, the whole string matches its own prefix trivially
    l, r = 0, 0                    # [l, r] is the current rightmost "Z-box" (a known prefix-matching window)
    for i in range(1, n):
        if i < r:
            z[i] = min(r - i, z[i - l])     # reuse previously-computed info inside the current Z-box
        while i + z[i] < n and s[z[i]] == s[i + z[i]]:
            z[i] += 1
        if i + z[i] > r:
            l, r = i, i + z[i]
    return z

def z_search(text, pattern):
    combined = pattern + "$" + text        # "$" -- separator guaranteed not to appear in either string
    z = z_array(combined)
    m = len(pattern)
    matches = []
    for i in range(m + 1, len(combined)):
        if z[i] >= m:
            matches.append(i - m - 1)         # translate index back into the original text's coordinates
    return matches
```

--> **Why concatenating with a separator works** -- any position in `text` where the Z-value reaches at least `m` (the pattern's length) means that position's substring matches the FULL pattern prefix that's now sitting at the front of `combined` -- turning pattern search into a single Z-array computation over one combined string.
--> **Z-algorithm vs KMP** -- both achieve `O(n + m)`; KMP's failure function only looks at the PATTERN's self-similarity, while the Z-array is a more general tool that also directly answers "longest common prefix between the string and any of its own suffixes," which is reused below in building suffix arrays.

# Suffix Arrays (Conceptual)

--> A suffix array is the sorted order (as a list of starting indices) of ALL suffixes of a string -- e.g. for `"banana"`, the suffixes `"a", "ana", "anana", "banana", "na", "nana"` sorted lexicographically give the suffix array `[5, 3, 1, 0, 4, 2]`.
--> **Why this is powerful** -- once suffixes are sorted, any substring search becomes a BINARY SEARCH (Searching file) over the suffix array -- `O(m log n)` per query after an upfront build cost -- because any substring that exists in the text is necessarily a PREFIX of some suffix, and prefixes of sorted strings cluster together, making binary search valid.
--> **Building one efficiently** -- naively sorting all `n` suffixes directly costs `O(n^2 log n)` (each of the `n` suffixes can be up to length `n`, and there are `n` of them to compare); real implementations build it in `O(n log n)` using techniques that sort suffixes by progressively doubling comparison length each round, reusing previous rounds' rankings rather than re-comparing full suffixes from scratch every time -- conceptually similar in spirit to how the Z-algorithm above reuses previously-computed matching information rather than restarting.
--> **Canonical real-world use cases** -- full-text search indexes, bioinformatics genome alignment (searching for a short DNA sequence inside a massive genome repeatedly), and data compression algorithms (the Burrows-Wheeler transform, used in `bzip2`, is built directly on suffix array ordering).

# Manacher's Algorithm -- Longest Palindromic Substring in Linear Time

--> The naive approach to finding the longest palindromic substring checks every possible CENTER and expands outward while both sides still match -- `O(n^2)` in the worst case (e.g. all-identical characters, where every center expands almost the full string length).
--> Manacher's achieves `O(n)` by recognizing that palindrome-expansion information around one center can PARTIALLY predict the answer for nearby centers, avoiding a full re-expansion at every single position -- directly analogous to the Z-algorithm's and KMP's shared theme of reusing previously-discovered structure instead of restarting from scratch.

```python
def longest_palindrome_manacher(s):
    if not s:
        return ""
    # transform e.g. "abc" -> "^#a#b#c#$" to uniformly handle even and odd-length palindromes
    t = "#" + "#".join(s) + "#"
    n = len(t)
    p = [0] * n                # p[i] = radius of the palindrome centered at i, within t
    center, right = 0, 0        # rightmost currently-known palindrome's center and right boundary

    for i in range(n):
        if i < right:
            mirror = 2 * center - i
            p[i] = min(right - i, p[mirror])    # reuse the mirrored position's known radius as a starting point
        while i - p[i] - 1 >= 0 and i + p[i] + 1 < n and t[i - p[i] - 1] == t[i + p[i] + 1]:
            p[i] += 1
        if i + p[i] > right:
            center, right = i, i + p[i]

    max_len, center_index = max((length, i) for i, length in enumerate(p))
    start = (center_index - max_len) // 2      # map back from the transformed string to the original
    return s[start:start + max_len]
```

--> **The mirror trick, intuitively** -- if position `i` sits inside an already-known palindrome centered at `center`, then `i`'s palindrome radius is AT LEAST as large as its mirror position's radius on the other side of that center (up to the boundary), because the known palindrome guarantees symmetric characters already match -- this lower bound is what turns `O(n^2)` naive expansion into `O(n)` overall.
--> **Why the `#`-interleaved transform** -- it uniformly handles both odd-length palindromes (real center on a character) and even-length ones (real center between two characters) by ensuring every palindrome in the transformed string has a single, well-defined center character, avoiding writing two separate cases.

# String/Rolling Hashing as a General Technique

--> Beyond Rabin-Karp specifically, rolling hashes are a general-purpose technique any time a problem needs to compare many substrings for equality QUICKLY, without repeatedly re-comparing raw characters -- reducing an `O(m)` substring comparison to an `O(1)` hash comparison (plus a rare verification step, as in Rabin-Karp above).
--> **Common applications** -- detecting duplicate substrings, checking if one string is a rotation of another, computing the longest common substring between many strings via binary search on length + hash-set lookup, and plagiarism/duplicate-content detection systems that hash rolling windows of text for near-instant comparison across huge corpora.
--> **Practical guidance -- picking base and modulus** -- use a modulus that's a LARGE prime (to minimize collisions) and consider computing TWO independent hashes with different bases/moduli for anything security- or correctness-sensitive (a single hash function, however well-chosen, can still theoretically collide -- directly connecting to the Heaps and Hashing file's discussion of collisions being mathematically inevitable, not a bug).

# Deep Dive -- Matching the Algorithm to the Problem

```text
Need                                                              Algorithm
Single pattern, one search, worst-case guarantee needed             KMP
Multiple patterns searched against the same text, or 2D matching     Rabin-Karp (rolling hash)
Need info about EVERY prefix-matching-substring position at once      Z-algorithm
Many repeated substring queries against the same fixed text            Suffix array
Longest palindromic substring                                          Manacher's algorithm
General "compare many substrings fast" outside exact pattern search     Rolling hash as a technique
```

--> **The shared thread across every algorithm in this file** -- naive string algorithms are slow specifically because they THROW AWAY information after each failed attempt and start completely over; every algorithm here (KMP's failure function, Rabin-Karp's incremental hash, the Z-array's Z-box reuse, Manacher's mirror trick, suffix arrays' doubling construction) is a different way of proving that some of that information can be legitimately carried forward instead of recomputed, which is precisely the same underlying principle that makes memoization valuable in the Dynamic Programming file.
