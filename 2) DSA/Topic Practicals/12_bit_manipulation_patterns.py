"""
12_bit_manipulation_patterns.py

Implements and tests:
    1. Core bit tricks -- power-of-two check, Brian Kernighan's set-bit counter, single-number via XOR
    2. get/set/clear/toggle bit helpers
    3. Bitmask subset generation, cross-checked against the backtracking version from chapter 4

Covers Theory chapter:
    2) DSA/Theory/12 Bit Manipulation and Problem-Solving Patterns.md

Run:  python 12_bit_manipulation_patterns.py
"""


def print_section(title: str) -> None:
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)


# ---------------------------------------------------------------------------
# 1) Core bit tricks
# ---------------------------------------------------------------------------

def is_power_of_two(n):
    return n > 0 and (n & (n - 1)) == 0


def count_set_bits(n):
    count = 0
    while n:
        n &= (n - 1)
        count += 1
    return count


def find_single_number(nums):
    result = 0
    for num in nums:
        result ^= num
    return result


def demo_core_bit_tricks() -> None:
    print_section("1) Power-of-two check, set-bit counting, and single-number via XOR")

    for n in [1, 2, 3, 4, 16, 18, 1024]:
        print(f"  is_power_of_two({n:5d}) = {is_power_of_two(n)}")
    assert is_power_of_two(1024) is True
    assert is_power_of_two(18) is False

    for n in [0, 7, 255, 1024]:
        print(f"  count_set_bits({n:5d}) = {count_set_bits(n)}  (binary: {bin(n)})")
    assert count_set_bits(7) == 3        # 111
    assert count_set_bits(255) == 8      # 11111111
    assert count_set_bits(1024) == 1     # 10000000000

    nums = [4, 1, 2, 1, 2]
    single = find_single_number(nums)
    print(f"\n  nums={nums} (every value paired except one) -> unpaired value = {single}")
    assert single == 4
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 2) get/set/clear/toggle bit helpers
# ---------------------------------------------------------------------------

def get_bit(n, i):
    return (n >> i) & 1


def set_bit(n, i):
    return n | (1 << i)


def clear_bit(n, i):
    return n & ~(1 << i)


def toggle_bit(n, i):
    return n ^ (1 << i)


def demo_bit_helpers() -> None:
    print_section("2) get/set/clear/toggle bit helpers")
    n = 0b1010   # 10
    print(f"Starting value: {bin(n)} ({n})")

    print(f"  get_bit(n, 1)    = {get_bit(n, 1)}   (bit 1 is currently set)")
    assert get_bit(n, 1) == 1
    assert get_bit(n, 0) == 0

    after_set = set_bit(n, 0)
    print(f"  set_bit(n, 0)    = {bin(after_set)} ({after_set})")
    assert after_set == 0b1011

    after_clear = clear_bit(n, 1)
    print(f"  clear_bit(n, 1)  = {bin(after_clear)} ({after_clear})")
    assert after_clear == 0b1000

    after_toggle = toggle_bit(n, 3)
    print(f"  toggle_bit(n, 3) = {bin(after_toggle)} ({after_toggle})")
    assert after_toggle == 0b0010
    print("Assertions passed.")


# ---------------------------------------------------------------------------
# 3) Bitmask subset generation, cross-checked against backtracking
# ---------------------------------------------------------------------------

def subsets_via_bitmask(items):
    n = len(items)
    result = []
    for mask in range(1 << n):
        subset = [items[i] for i in range(n) if mask & (1 << i)]
        result.append(subset)
    return result


def subsets_via_backtracking(items):
    results = []

    def backtrack(start, current):
        results.append(current[:])
        for i in range(start, len(items)):
            current.append(items[i])
            backtrack(i + 1, current)
            current.pop()

    backtrack(0, [])
    return results


def demo_bitmask_subsets() -> None:
    print_section("3) Bitmask subset generation vs backtracking -- same result, different technique")
    items = ["x", "y", "z"]

    bitmask_result = subsets_via_bitmask(items)
    backtracking_result = subsets_via_backtracking(items)

    print(f"Items: {items}")
    print(f"Bitmask-generated subsets ({len(bitmask_result)}):     {bitmask_result}")
    print(f"Backtracking-generated subsets ({len(backtracking_result)}): {backtracking_result}")

    # Compare as sets-of-frozensets since the two techniques may produce subsets in a different order
    normalize = lambda subsets: sorted(sorted(s) for s in subsets)
    assert normalize(bitmask_result) == normalize(backtracking_result)
    assert len(bitmask_result) == 2 ** len(items)
    print("Assertion passed -- both techniques produce the exact same set of subsets, "
          "confirming they're two different implementations of the identical result.")


if __name__ == "__main__":
    demo_core_bit_tricks()
    demo_bit_helpers()
    demo_bitmask_subsets()
    print("\nAll Bit Manipulation demos completed.")
