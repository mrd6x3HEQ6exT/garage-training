#!/usr/bin/env python3
"""Inspector: turn a workbook into readable text, one file per sheet.

For each sheet you get: size, freeze panes, protection (and what is still allowed),
merged cells, hidden rows/columns, dropdowns (data validation), conditional formatting,
a "formula patterns" list (thousands of copied formulas collapse to a few lines),
and every cell with its formula, its last calculated value, and [UNLOCKED] where
typing is allowed on a protected sheet.

Usage:
  python3 xlsx_inspect.py FILE.xlsx [-o OUTDIR] [--sheets "Name 1" "Name 2"] [--no-cells]

Output goes to OUTDIR (default: ./xlsx_out/<file name>_inspect/). Runs locally only.
"""

import argparse
import re
import sys
from pathlib import Path

from common import compress_ranges, formula_text, is_array_formula, iter_cells, load, to_r1c1

PROTECTION_OPTIONS = ["selectLockedCells", "selectUnlockedCells", "formatCells", "formatColumns",
                      "formatRows", "insertColumns", "insertRows", "insertHyperlinks", "deleteColumns",
                      "deleteRows", "sort", "autoFilter", "pivotTables", "objects", "scenarios"]


def _color(rule):
    try:
        fill = rule.dxf.fill
        for c in (fill.fgColor, fill.bgColor):
            if c is not None and c.rgb and isinstance(c.rgb, str) and c.rgb not in ("00000000",):
                return c.rgb
    except AttributeError:
        pass
    return ""


def snapshot(ws, ws_values=None, with_cells=True):
    """Everything worth comparing about one sheet, as plain Python data."""
    p = ws.protection
    snap = {
        "title": ws.title,
        "state": ws.sheet_state,
        "dims": ws.dimensions,
        "freeze": ws.freeze_panes,
        "protected": bool(p.sheet),
        "allowed": sorted(o for o in PROTECTION_OPTIONS
                          if p.sheet and not getattr(p, o) and o not in ("selectLockedCells", "selectUnlockedCells")),
        "password": bool(p.password),
        "merged": sorted(str(m) for m in ws.merged_cells.ranges),
        "hidden_rows": sorted(r for r, d in ws.row_dimensions.items() if d.hidden),
        "hidden_cols": sorted(k for k, d in ws.column_dimensions.items() if d.hidden),
        "widths": {k: round(d.width, 2) for k, d in ws.column_dimensions.items() if d.width},
        "dv": sorted((str(d.sqref), d.type or "", d.operator or "", d.formula1 or "", d.formula2 or "",
                      (d.prompt or "")[:120], (d.error or "")[:120])
                     for d in ws.data_validations.dataValidation),
        "cf": sorted((str(cf.sqref), r.type or "", r.operator or "", " ; ".join(r.formula or []), _color(r))
                     for cf in ws.conditional_formatting for r in cf.rules),
        "cells": {},
        "patterns": {},
    }
    vals = ws_values
    for c in iter_cells(ws):
        v = c.value
        locked = True if c.protection is None else bool(c.protection.locked)
        f = formula_text(v)
        if f is None and v is None:
            if not locked:
                snap["cells"][c.coordinate] = {"kind": "blank", "content": None, "locked": False}
            continue
        entry = {"kind": "formula" if f else "value", "content": f if f else v, "locked": locked,
                 "array": is_array_formula(v)}
        if f:
            pat = to_r1c1(f, c.row, c.column)
            entry["pattern"] = pat
            snap["patterns"].setdefault(pat, []).append(c.coordinate)
            if vals is not None:
                entry["cached"] = vals[c.coordinate].value
        if with_cells or f:
            snap["cells"][c.coordinate] = entry
    return snap


def _write_sheet(snap, path, with_cells):
    lines = [f"SHEET {snap['title']}",
             f"state={snap['state']}  used range={snap['dims']}  freeze panes={snap['freeze']}",
             f"protected={snap['protected']}  password={'YES' if snap['password'] else 'no'}"
             + (f"  allowed while protected: {', '.join(snap['allowed']) or 'nothing extra'}" if snap["protected"] else ""),
             f"merged: {', '.join(snap['merged']) or 'none'}",
             f"hidden rows: {snap['hidden_rows'] or 'none'}",
             f"hidden columns: {snap['hidden_cols'] or 'none'}",
             "column widths: " + (", ".join(f"{k}={v}" for k, v in list(snap['widths'].items())[:40]) or "default"),
             "", f"DROPDOWNS / DATA VALIDATION ({len(snap['dv'])})"]
    for sq, typ, op, f1, f2, prompt, err in snap["dv"]:
        rule = f"{typ} {op} {f1}" + (f" .. {f2}" if f2 else "")
        lines.append(f"  {sq}: {rule.strip()}" + (f"  | prompt: {prompt}" if prompt else "")
                     + (f"  | error: {err}" if err else ""))
    lines += ["", f"CONDITIONAL FORMATTING ({len(snap['cf'])} rules)"]
    for sq, typ, op, formula, color in snap["cf"]:
        lines.append(f"  {sq}: {typ} {op} {formula}" + (f"  -> fill {color}" if color else ""))
    pats = sorted(snap["patterns"].items(), key=lambda kv: (-len(kv[1]), kv[1][0]))
    lines += ["", f"FORMULA PATTERNS ({len(pats)} distinct, {sum(len(v) for v in snap['patterns'].values())} formulas)"]
    for pat, coords in pats:
        first = coords[0]
        a1 = snap["cells"][first]["content"]
        where = compress_ranges(coords)
        if len(where) > 160:
            where = where[:157] + "..."
        lines.append(f"  x{len(coords):<5} {where}")
        lines.append(f"         e.g. {first}: {a1[:300]}")
    unlocked_empty = [c for c, e in snap["cells"].items() if e["kind"] == "blank"]
    lines += ["", f"EMPTY TYPING CELLS (unlocked, {len(unlocked_empty)}): {compress_ranges(unlocked_empty) or 'none'}"]
    if with_cells:
        lines += ["", "CELLS  (value or =formula  -> last calculated value)  [UNLOCKED] = can type here when protected"]
        for coord, e in snap["cells"].items():
            tag = " [UNLOCKED]" if not e["locked"] else ""
            if e["kind"] == "blank":
                continue
            content = str(e["content"]).replace("\n", " | ")
            if len(content) > 500:
                content = content[:497] + "..."
            cached = ""
            if e["kind"] == "formula" and "cached" in e:
                cached = f"  -> {e['cached']!r}" if e["cached"] is not None else "  -> (blank)"
            arr = " {array}" if e.get("array") else ""
            lines.append(f"{coord}{tag}:{arr} {content}{cached}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def inspect(file, outdir=None, sheets=None, with_cells=True):
    file = Path(file)
    outdir = Path(outdir) if outdir else Path("xlsx_out") / f"{file.stem}_inspect"
    outdir.mkdir(parents=True, exist_ok=True)
    wb = load(file)
    wbv = load(file, values=True)
    rows = []
    for i, ws in enumerate(wb.worksheets, 1):
        if sheets and ws.title not in sheets:
            continue
        snap = snapshot(ws, wbv[ws.title], with_cells)
        safe = re.sub(r"[^A-Za-z0-9._-]+", "_", ws.title)
        _write_sheet(snap, outdir / f"{i:02d}_{safe}.txt", with_cells)
        n_form = sum(len(v) for v in snap["patterns"].values())
        n_unl = sum(1 for e in snap["cells"].values() if not e["locked"])
        rows.append((ws.title, snap["state"], snap["dims"], "yes" if snap["protected"] else "no",
                     snap["freeze"] or "", n_form, len(snap["patterns"]), n_unl, len(snap["dv"]), len(snap["cf"])))
    summary = [f"# {file.name}", "",
               "| Sheet | State | Used range | Protected | Freeze | Formulas | Patterns | Unlocked cells | Dropdown rules | Cond. format rules |",
               "|---|---|---|---|---|---|---|---|---|---|"]
    summary += ["| " + " | ".join(str(x) for x in r) + " |" for r in rows]
    summary += ["", "## Defined names"]
    for name, dn in sorted(wb.defined_names.items()):
        summary.append(f"- `{name}` = `{dn.attr_text}`")
    for ws in wb.worksheets:
        for name, dn in sorted(ws.defined_names.items()):
            summary.append(f"- `{name}` (only on {ws.title}) = `{dn.attr_text}`")
    sec = wb.security
    summary += ["", "## Workbook protection",
                f"structure locked: {bool(sec and sec.lockStructure)}; password: "
                f"{'YES' if sec and (sec.workbookPassword or getattr(sec, 'workbookHashValue', None)) else 'no'}"]
    (outdir / "00_workbook.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    return outdir


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("file")
    ap.add_argument("-o", "--outdir")
    ap.add_argument("--sheets", nargs="+", help="only these sheet names")
    ap.add_argument("--no-cells", action="store_true", help="skip the cell-by-cell listing")
    args = ap.parse_args(argv)
    out = inspect(args.file, args.outdir, args.sheets, not args.no_cells)
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
