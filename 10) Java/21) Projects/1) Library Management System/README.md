# Library Management System (Java Console App)

A complete, runnable, multi-class console application that manages a small
library's book catalog, members, and checkout/return workflow, with
persistence to a plain text file. This is the Java-domain capstone project:
it deliberately ties together OOP, Collections, Generics, Exceptions,
Streams, and File I/O in one working program rather than isolated snippets.

No external dependencies, no build tool required — plain `javac`/`java`,
single default package, JDK 11+ compatible.

## Real-world scenario

You are staffing the front desk of a small community library. Over the
course of a shift you need to: add newly-acquired books to the catalog,
sign up new members, help a patron search for a book by title/author/genre,
check a book out to a member (respecting copy availability and each
member's borrowing limit), process a return, and at the end of the day
save everything to disk so it's there again tomorrow. `LibraryManagementSystemApp`
is that front-desk terminal — a single menu loop you'd sit at all shift.

## Features

- **Catalog management** — add books with ISBN, title, author, genre, and
  copy count; duplicate ISBNs are rejected.
- **Member management** — register members with an id, name, and optional
  email; duplicate ids are rejected.
- **Search** — case-insensitive keyword search across title, author, and
  genre, powered by the Stream API.
- **Checkout / return** — enforces two real business rules: a book must
  have an available copy, and a member may not exceed
  `Member.MAX_BOOKS_ALLOWED` (3) books out at once. Every checkout creates
  a `Loan` record with a 14-day due date; every return closes the matching
  open loan.
- **Overdue tracking** — lists every loan currently out and past its due
  date, as of "today".
- **Reports** — total counts, top-5 most-borrowed books of all time, and a
  breakdown of currently-checked-out books by genre — all computed with
  Streams (`groupingBy`, `counting`, sorting by value).
- **Persistence** — save/load the entire library state (books, members,
  loan history, including who currently has what) to a single pipe-delimited
  text file using `java.nio.file` + `java.io`, with `try-with-resources`
  everywhere a file handle is opened. The app auto-loads any existing save
  file on startup, and seeds a few sample records only if nothing was
  loaded, so the menu is never empty on a first run.
- **Meaningful validation & exceptions** — every business-rule violation
  (book not found, book unavailable, member not found, borrowing limit
  exceeded, duplicate entry) raises a specific checked exception with a
  clear message; the console menu catches each one and reports it without
  crashing.

## Architecture

```
Person (abstract)                     <-- OOP: base class, encapsulation
  └── Member                          <-- OOP: inheritance, Collections (Set<String> of borrowed ISBNs)

Book                                  <-- OOP: encapsulation, Comparable<Book>
Loan                                  <-- model: one checkout transaction (checkout/due/return dates)

LibraryException (checked)            <-- Exceptions: custom exception hierarchy
  ├── BookNotFoundException
  ├── BookNotAvailableException
  ├── MemberNotFoundException
  ├── BorrowingLimitExceededException
  └── DuplicateEntryException

Library                               <-- service layer: Collections (Map/List), Generics, Streams
  - catalog: Map<String, Book>
  - members: Map<String, Member>
  - loans:   List<Loan>
  - addBook / registerMember / checkout / returnBook
  - searchBooks / availableBooksSorted / overdueLoans / mostBorrowedBooks / checkoutCountByGenre
  - ReadOnlyView<T>  (generic, read-only Iterable wrapper)

FileStorage                           <-- File I/O: java.nio.file.Files, BufferedReader/Writer, try-with-resources
  - save(Library)  -> library_data.txt
  - load(Library)

LibraryManagementSystemApp            <-- main(): Scanner-driven console menu, wires everything together
```

Dependency direction is one-way: the console app depends on `Library` and
`FileStorage`; `Library` depends on the model classes (`Book`, `Member`,
`Loan`) and the exception hierarchy; nothing depends back on the console
layer. `Library`'s internal maps/lists are never handed out directly —
callers get an unmodifiable `ReadOnlyView<T>` or a fresh `List` produced by
a stream pipeline, so external code cannot corrupt service-layer state.

## Which Java concepts this demonstrates

| Theory sub-domain | Where it shows up |
|---|---|
| **OOP** | `Person` is an abstract base class extended by `Member` (inheritance + polymorphism via `getRole()`); every model class encapsulates its fields behind validated getters/setters; `Book implements Comparable<Book>` for natural sort order; `equals`/`hashCode` implemented correctly on `Book` (by ISBN) and `Person` (by id) so they behave correctly as `Map` keys / in `Set`s. |
| **Collections** | `LinkedHashMap<String, Book>` and `LinkedHashMap<String, Member>` for the catalog/member registries (predictable insertion-order iteration); `ArrayList<Loan>` for loan history; `LinkedHashSet<String>` on `Member` for currently-borrowed ISBNs (no duplicates, insertion order preserved). |
| **Generics** | `Library.ReadOnlyView<T>` is a generic, reusable read-only wrapper; `Library.sortedCopyOf` is a generic method (`<T extends Comparable<T>>`) usable for any comparable collection, not just books. |
| **Exceptions** | A custom checked-exception hierarchy rooted at `LibraryException`, with five specific subclasses for distinct business failures; `FileStorage` translates low-level `IOException`/parse errors into `LibraryException` so callers only ever handle one exception family; the console menu catches each exception type with a specific, user-facing message instead of a stack trace. |
| **Streams** | `Library.searchBooks`, `availableBooksSorted`, `currentlyOnLoan`, `overdueLoans`, `loanHistory`, `checkoutCountByGenre` (`Collectors.groupingBy` + `counting`), and `mostBorrowedBooks` (grouping, sorting by value, `limit`) are all implemented as stream pipelines rather than manual loops. |
| **File I/O** | `FileStorage` uses `java.nio.file.Path`/`Files.newBufferedReader`/`newBufferedWriter` wrapped in `try-with-resources`, reading/writing a simple sectioned, pipe-delimited text format (`#BOOKS` / `#MEMBERS` / `#LOANS`). |

## Compile & run

Requires JDK 11 or newer. From inside this folder:

```
javac *.java
java LibraryManagementSystemApp
```

(If `javac`/`java` aren't on your `PATH`, invoke them with a full path,
e.g. `"C:\Program Files\Java\jdk-11\bin\javac.exe" *.java`.)

On exit, or whenever you choose option 10, the full library state is saved
to `library_data.txt` in the same folder; option 11 (or simply relaunching
the app) reloads it. Delete `library_data.txt` to start over with a fresh
seeded sample catalog.

## Sample usage walkthrough

```
=== Library Management System ===
Seeded sample catalog and members for this session.

------------------------------------------
 1. Add a book
 2. Register a member
 3. Search books
 4. Checkout a book
 5. Return a book
 6. List all books
 7. List all members
 8. List overdue loans
 9. Reports (popular books / genre breakdown)
10. Save library data to file
11. Load library data from file
 0. Exit
------------------------------------------
Choose an option: 3

-- Search Books --
Enter a keyword (matches title/author/genre): Clean
Found 1 book(s):
  [978-0132350884] "Clean Code" by Robert C. Martin (Software Engineering) - 2/2 available

Choose an option: 4

-- Checkout a Book --
ISBN of the book: 978-0132350884
Member ID: M001
Checked out successfully: Loan[isbn=978-0132350884, member=M001, out=2026-08-31, OUT (due 2026-09-14)]

Choose an option: 9

-- Reports --
Total books in catalog : 3
Total registered members: 2
Currently on loan       : 1

Top 5 most-borrowed books of all time:
  Clean Code                               borrowed 1 time(s)

Books currently checked out, by genre:
  Software Engineering 1

Choose an option: 0
Goodbye!
```

This exact walkthrough (plus checkout-limit, book-unavailable,
member-not-found, and duplicate-entry error paths) was run against a
compiled build of this project on 2026-08-31 using JDK 11.0.19, confirming
the whole pipeline — search, checkout, validation failures, reports,
return, save, and reload — works end to end.

## Things to try changing

- **Add a `Librarian` subclass of `Person`** with its own `getRole()`, and
  a "staff-only" menu action (e.g. force-return any book) to see the
  abstract base class extend cleanly to a second concrete type.
- **Add fines**: extend `Loan` with a per-day-overdue fine calculation and
  a new report that sums outstanding fines per member using Streams.
- **Swap the persistence format**: change `FileStorage` to write JSON
  instead of pipe-delimited text (still using only `java.io`/`java.nio`,
  hand-rolled — no external JSON library) and confirm save/load still
  round-trips correctly.
- **Add a reservation queue**: when `checkout` throws
  `BookNotAvailableException`, offer to add the member to a
  `Queue<String>` (member ids waiting for that ISBN) on the `Book`, and
  notify the next member in line when a copy is returned.
- **Raise `Member.MAX_BOOKS_ALLOWED`** or make it per-member instead of a
  shared constant, and watch `BorrowingLimitExceededException` behavior
  change accordingly.
