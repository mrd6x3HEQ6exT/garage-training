#!/usr/bin/env python3
"""Before/after compare: list exactly what changed between two versions of a workbook.

Reports, per sheet: added/removed sheets, visibility, protection and what is allowed,
freeze panes, merged cells, hidden rows/columns, column widths, dropdown (validation)
rules, conditional-formatting rules, locked/unlocked changes, and every changed cell.
Formula changes are also summarised as "patterns" so one edited formula copied down
150 rows shows as one change, not 150.

Usage:
  python3 xlsx_compare.py OLD.xlsx NEW.xlsx [-o report.md] [--max-examples 20] [--values]
         [--fail-on-diff]

--values  also compares last calculated values of formula cells.
Runs locally only.
"""

import argparse
import sys
from pathlib import Path

from common import compress_ranges, load
from xlsx_inspect import snapshot


def _fmt(v, n=160):
    s = repr(v) if not isinstance(v, str) else v
    s = s.replace("\n", " | ")
    return s if len(s) <= n else s[: n - 3] + "..."


def _set_diff(old, new):
    o, n = set(old), set(new)
    return sorted(n - o), sorted(o - n)


def compare_sheet(a, b, max_examples, values):
    out = []
    for key, label in (("state", "Visibility"), ("dims", "Used range"), ("freeze", "Freeze panes"),
                       ("protected", "Protected"), ("password", "Protection password")):
        if a[key] != b[key]:
            out.append(f"- {label}: `{a[key]}` -> `{b[key]}`")
    if a["allowed"] != b["allowed"]:
        out.append(f"- Allowed while protected: {a['allowed']} -> {b['allowed']}")
    for key, label in (("merged", "Merged cells"), ("hidden_rows", "Hidden rows"), ("hidden_cols", "Hidden columns")):
        added, removed = _set_diff(a[key], b[key])
        if added:
            out.append(f"- {label} added: {', '.join(map(str, added[:40]))}")
        if removed:
            out.append(f"- {label} removed: {', '.join(map(str, removed[:40]))}")
    wchg = sorted(k for k in set(a["widths"]) | set(b["widths"]) if a["widths"].get(k) != b["widths"].get(k))
    if wchg:
        out.append(f"- Column widths changed ({len(wchg)}): "
                   + ", ".join(f"{k} {a['widths'].get(k)}->{b['widths'].get(k)}" for k in wchg[:20]))
    for key, label in (("dv", "Dropdown/validation rule"), ("cf", "Conditional-format rule")):
        added, removed = _set_diff(a[key], b[key])
        for r in removed[:max_examples]:
            out.append(f"- {label} removed: `{r[0]}` {' '.join(str(x) for x in r[1:] if x)}")
        for r in added[:max_examples]:
            out.append(f"- {label} added: `{r[0]}` {' '.join(str(x) for x in r[1:] if x)}")
        extra = max(0, len(added) - max_examples) + max(0, len(removed) - max_examples)
        if extra:
            out.append(f"- ... {extra} more {label.lower()} changes")

    ca, cb = a["cells"], b["cells"]
    changed, added, removed, lock_changed, value_changed = [], [], [], [], []
    for coord in sorted(set(ca) | set(cb), key=lambda c: (int("".join(ch for ch in c if ch.isdigit())), len(c), c)):
        ea, eb = ca.get(coord), cb.get(coord)
        if ea is None or ea["kind"] == "blank":
            if eb is not None and eb["kind"] != "blank":
                added.append((coord, eb))
            if ea is not None and eb is not None and ea["locked"] != eb["locked"]:
                lock_changed.append((coord, ea["locked"], eb["locked"]))
            if ea is not None and eb is None:
                lock_changed.append((coord, False, True))
            if ea is None and eb is not None and eb["kind"] == "blank":
                lock_changed.append((coord, True, False))
            continue
        if eb is None or eb["kind"] == "blank":
            removed.append((coord, ea))
            if eb is not None and ea["locked"] != eb["locked"]:
                lock_changed.append((coord, ea["locked"], eb["locked"]))
            continue
        if ea["content"] != eb["content"] or ea["kind"] != eb["kind"] or ea.get("array") != eb.get("array"):
            changed.append((coord, ea, eb))
        if ea["locked"] != eb["locked"]:
            lock_changed.append((coord, ea["locked"], eb["locked"]))
        if values and ea["kind"] == "formula" == eb["kind"] and "cached" in ea and "cached" in eb \
                and ea["cached"] != eb["cached"]:
            value_changed.append((coord, ea["cached"], eb["cached"]))

    # formula patterns: what logic appeared or disappeared
    pa, pb = a["patterns"], b["patterns"]
    new_p = [p for p in pb if p not in pa]
    gone_p = [p for p in pa if p not in pb]
    if new_p or gone_p:
        out.append(f"- Formula patterns: {len(new_p)} new, {len(gone_p)} gone "
                   f"(a pattern = one formula however many cells it is copied to)")
        for p in sorted(new_p, key=lambda p: -len(pb[p]))[:max_examples]:
            first = pb[p][0]
            out.append(f"  - NEW x{len(pb[p])} at {compress_ranges(pb[p])[:120]}: `{_fmt(b['cells'][first]['content'])}`")
        for p in sorted(gone_p, key=lambda p: -len(pa[p]))[:max_examples]:
            first = pa[p][0]
            out.append(f"  - GONE x{len(pa[p])} at {compress_ranges(pa[p])[:120]}: `{_fmt(a['cells'][first]['content'])}`")

    def listing(title, items, render):
        if not items:
            return
        out.append(f"- {title} ({len(items)})" + (f": {compress_ranges([i[0] for i in items])[:200]}" if len(items) > 1 else ""))
        for it in items[:max_examples]:
            out.append("  - " + render(it))
        if len(items) > max_examples:
            out.append(f"  - ... {len(items) - max_examples} more")

    def arr(e):
        return "{array} " if e.get("array") else ""

    listing("Cells changed", changed,
            lambda t: f"{t[0]}: {arr(t[1])}`{_fmt(t[1]['content'])}` -> {arr(t[2])}`{_fmt(t[2]['content'])}`")
    listing("Cells filled", added, lambda t: f"{t[0]}: `{_fmt(t[1]['content'])}`")
    listing("Cells emptied", removed, lambda t: f"{t[0]}: was `{_fmt(t[1]['content'])}`")
    listing("Lock changed", lock_changed,
            lambda t: f"{t[0]}: {'locked' if t[1] else 'unlocked'} -> {'locked' if t[2] else 'unlocked'}")
    listing("Calculated values changed", value_changed, lambda t: f"{t[0]}: {_fmt(t[1])} -> {_fmt(t[2])}")
    counts = (len(changed), len(added), len(removed), len(lock_changed), len(value_changed))
    return out, counts


def compare(old, new, max_examples=20, values=False):
    wa, wb = load(old), load(new)
    va = load(old, values=True) if values else None
    vb = load(new, values=True) if values else None
    lines = [f"# Compare", f"- old: `{old}`", f"- new: `{new}`", ""]
    na, nb = wa.sheetnames, wb.sheetnames
    added = [s for s in nb if s not in na]
    removed = [s for s in na if s not in nb]
    common = [s for s in na if s in nb]
    any_diff = bool(added or removed)
    lines.append("## Workbook")
    if added:
        lines.append(f"- Sheets added: {', '.join(added)}")
    if removed:
        lines.append(f"- Sheets removed: {', '.join(removed)}")
    if [s for s in na if s in common] != [s for s in nb if s in common]:
        lines.append(f"- Sheet order changed: {[s for s in nb if s in common]}")
        any_diff = True
    names_a = {k: v.attr_text for k, v in wa.defined_names.items()}
    names_b = {k: v.attr_text for k, v in wb.defined_names.items()}
    for k in sorted(set(names_a) | set(names_b)):
        if names_a.get(k) != names_b.get(k):
            lines.append(f"- Defined name `{k}`: `{names_a.get(k)}` -> `{names_b.get(k)}`")
            any_diff = True
    la = bool(wa.security and wa.security.lockStructure)
    lb = bool(wb.security and wb.security.lockStructure)
    if la != lb:
        lines.append(f"- Workbook structure lock: {la} -> {lb}")
        any_diff = True
    if lines[-1] == "## Workbook":
        lines.append("- no workbook-level changes")

    table = ["", "## Summary by sheet", "",
             "| Sheet | Cells changed | Filled | Emptied | Lock changed | Values changed | Other |",
             "|---|---|---|---|---|---|---|"]
    details = ["", "## Details"]
    for s in common:
        sa = snapshot(wa[s], va[s] if va else None)
        sb = snapshot(wb[s], vb[s] if vb else None)
        out, counts = compare_sheet(sa, sb, max_examples, values)
        other = sum(1 for l in out if l.startswith("- ") and not l.startswith(
            ("- Cells", "- Lock changed", "- Calculated values")))
        if out:
            any_diff = True
            table.append(f"| {s} | " + " | ".join(str(c) for c in counts) + f" | {other} |")
            details += ["", f"### {s}"] + out
    if len(table) == 5:
        table.append("| (no sheet differences) | | | | | | |")
    return "\n".join(lines + table + details) + "\n", any_diff


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("-o", "--output", help="write the report to this .md file")
    ap.add_argument("--max-examples", type=int, default=20)
    ap.add_argument("--values", action="store_true", help="also compare last calculated values")
    ap.add_argument("--fail-on-diff", action="store_true", help="exit 1 when anything differs")
    args = ap.parse_args(argv)
    report, diff = compare(args.old, args.new, args.max_examples, args.values)
    if args.output:
        Path(args.output).write_text(report, encoding="utf-8")
        print(f"Wrote {args.output} ({'differences found' if diff else 'identical'})")
    else:
        print(report)
    return 1 if (diff and args.fail_on_diff) else 0


if __name__ == "__main__":
    sys.exit(main())
