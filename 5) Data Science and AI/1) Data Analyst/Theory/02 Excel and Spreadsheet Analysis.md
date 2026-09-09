# Why Excel Still Matters

--> Despite Python/SQL being more powerful for large-scale work, spreadsheets remain the most universally used analysis tool in business -- nearly every stakeholder an analyst reports to can open and understand a spreadsheet, making it the lowest-friction way to share and collaborate on smaller datasets.

# Core Formulas Every Analyst Needs

```
=SUM(A1:A100)                          -- Total of a range
=AVERAGE(A1:A100)                       -- Mean
=COUNTIF(A1:A100, ">100")               -- Count matching a condition
=SUMIF(A1:A100, "East", B1:B100)         -- Sum B where A matches a condition
=IF(A1>100, "High", "Low")               -- Conditional logic
```

# VLOOKUP, INDEX/MATCH and XLOOKUP -- Joining Data Within a Sheet

--> `VLOOKUP` -- looks up a value in one column and returns a corresponding value from another column -- spreadsheet's closest equivalent to a SQL `JOIN` (covered in depth in the Full Stack Database track), but limited to searching left-to-right and only the FIRST match.

```
=VLOOKUP(A2, ProductTable, 3, FALSE)
-- Look up A2's value in ProductTable, return the 3rd column, exact match only
```

--> `INDEX/MATCH` -- a more flexible combination achieving the same lookup goal without VLOOKUP's left-to-right restriction.
--> `XLOOKUP` (modern Excel/Sheets) -- a newer function combining the flexibility of INDEX/MATCH with VLOOKUP's simpler syntax, now generally the recommended default over both older approaches.

# Pivot Tables -- Summarizing Data Interactively

--> A Pivot Table lets you drag-and-drop fields to instantly summarize large datasets (sum/average/count by category) without writing any formula -- functionally similar to a SQL `GROUP BY` (covered in the SQL Joins/GROUP BY file), but interactive and requiring no code.
--> Typical use -- dropping "Region" into rows, "Month" into columns, and "Revenue" into values instantly produces a cross-tabulated summary table, which would otherwise require several `SUMIFS` formulas or a database query to replicate.

# Data Cleaning in Spreadsheets

--> `TRIM()` -- removes extra whitespace, a common cause of "why doesn't this VLOOKUP match" bugs.
--> Text-to-Columns -- splits a single column (like "First Last" names) into separate columns based on a delimiter.
--> Conditional formatting -- visually highlighting outliers, duplicates, or values matching a specific rule, useful for spotting data quality issues at a glance before deeper analysis.
--> Data Validation -- restricting what values can be entered into a cell (a dropdown list, a number range) -- prevents inconsistent data entry at the SOURCE, cheaper than cleaning it up after the fact.

# Where Spreadsheets Break Down

--> Performance -- spreadsheets slow down significantly beyond roughly hundreds of thousands of rows, well within range of what a SQL database or Pandas (covered in the Data Science folder) handles easily.
--> Reproducibility -- a chain of manual formulas/manipulations is hard to audit or repeat exactly, unlike a SQL query or a Python script which documents the exact steps taken.
--> Version control and collaboration -- multiple people editing the same spreadsheet risks silent overwrites and lost work, a problem Git (covered in the Full Stack GitHub notes) solves for code but spreadsheets historically haven't solved well (cloud versions like Google Sheets have improved this significantly).

# Excel/Sheets as a Prototyping Tool

--> Even analysts who primarily work in SQL/Python often reach for a spreadsheet first to quickly sanity-check a small sample of data or sketch out a calculation's logic before formalizing it in code -- a legitimate, common part of the real workflow, not just a beginner's tool.
