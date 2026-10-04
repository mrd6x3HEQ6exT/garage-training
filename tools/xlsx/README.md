# xlsx tools

Small command-line tools for checking Excel workbooks. Everything runs on the local machine;
nothing is uploaded. The code holds no workbook data: each tool reads whatever file you point
it at.

**Keep spreadsheets and tool output out of git.** Reports, rule files (`*.json`) and pictures
contain the workbook's data. The `.gitignore` files block `*.xlsx` and similar files and
`xlsx_out/`, but run the tools from a working folder outside the repository anyway.

## Setup

```
apt-get install -y --no-install-recommends libreoffice-calc poppler-utils fonts-crosextra-carlito
pip install openpyxl
```

`libreoffice-calc` is required. The bare `libreoffice-core` package cannot open spreadsheets, and
recalculation then hangs until it times out. `fonts-crosextra-carlito` matches Calibri's widths,
so pictures line up like Excel.

Run the tools with `python3 /path/to/tools/xlsx/<tool>.py ...`. Each one prints its options with `--help`.

## The tools

| Tool | What it answers |
|---|---|
| `xlsx_security.py FILE...` | Does the file stay inside the security limits? Checks for macros (VBA and Excel 4.0), ActiveX, embedded objects, add-ins, custom ribbons, links to other workbooks, data connections, Power Query, data model, DDE/RTD/WEBSERVICE/IMAGE-type formulas and protection passwords. Also lists names stored in the file and any sensitivity label. Exits 1 on any FAIL. |
| `xlsx_inspect.py FILE` | What is in this workbook? Writes one text file per sheet: protection and what's still allowed, freeze panes, dropdowns, conditional formatting, formula patterns (copied formulas collapse to one line), and every cell with its formula and last value. |
| `xlsx_compare.py OLD NEW` | What changed between two versions? Covers sheets, protection, dropdowns, conditional formats, locked cells, widths and cells, plus formula-pattern changes. Use `--values` to compare calculated results too. |
| `xlsx_preview.py FILE` | What does it look like? Draws one PNG per sheet with LibreOffice. `--sheets` and `--range A1:Z40` draw just a block. |
| `rules_test.py spec/diff/run FILE` | Do the GO/NG and percentage formulas follow the workbook's own rules? See below. |

### rules_test.py

The rules are read from the workbook, never typed into the script:

- **`rows` layout:** a hidden `Rules` sheet listing each step's tab, section, step number, type (S/NS), max, critical flag, part (Booth/Report), minimum group and switch. It also has a minimum-group table and a GO-floor table.
  - A `Column` header means steps run across, one row per student.
  - A `Row` header means steps run down, one column per student; that layout is read through its "Result", "Final %", "Student" labels.
- **`v9` layout:** students in columns and steps in rows, with `GO / NG:` in A2. Rules come from the overall GO/NG formula. The red step numbers and every pass/fail row below the steps are read as extra "views" and compared against it.

Commands:

- **`spec FILE`** prints the rules found, with notes. Notes cover missing dropdowns, divisors that don't match the points, and views that disagree.
- **`diff A B`** compares the rules of two workbooks, or of saved `spec -o` JSON files.
- **`run FILE`** builds test students from the rules and types them into a throwaway copy, then recalculates it in LibreOffice. It then checks every result cell against the rules: GO/NG/INC/CHECK, Final %, Booth/Report % and Booth/Report GO.
  - The test students cover: perfect, blank, one missing step, each critical step missed, each S step under the minimum, each section exactly at and one under its minimum, invalid values, the lowest passing score, switches off, and random mixes.
  - Exits 1 when anything is wrong. Use `--keep DIR` to keep the filled-in copies.

A new layout needs a small adapter class in `rules_test.py`, like `V9Layout` and `RowsLayout`. It has to say where each student's step cells and result cells are.

## Self-tests

```
cd tools/xlsx && python3 -m unittest discover -s tests -v
```

The tests build small made-up workbooks with planted bugs and check that each tool finds them.
