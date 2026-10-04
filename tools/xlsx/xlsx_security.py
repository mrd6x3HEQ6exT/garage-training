#!/usr/bin/env python3
"""Security check for .xlsx files, run locally before a workbook is handed over.

Checks the file against these limits:
  - plain .xlsx, no macros (VBA or Excel 4.0 macro sheets), no ActiveX, no embedded objects
  - no add-ins or custom ribbons
  - no external data: links to other workbooks, data connections, Power Query, data model,
    DDE, RTD, WEBSERVICE / IMAGE / STOCKHISTORY / CUBE* / Python-in-Excel formulas
  - sheet and workbook protection without a password (use --allow-passwords to relax)

Usage:
  python3 xlsx_security.py FILE.xlsx [FILE2.xlsx ...] [--json] [--allow-passwords]

Exit code 1 when any check FAILs. WARN = look at it; INFO = for your awareness.
"""

import argparse
import json
import re
import sys
import zipfile
from pathlib import PurePosixPath
from xml.etree import ElementTree as ET

NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

KNOWN_PREFIXES = (
    "[Content_Types].xml", "_rels/", "docProps/", "xl/workbook.xml", "xl/_rels/", "xl/worksheets/",
    "xl/theme/", "xl/styles.xml", "xl/sharedStrings.xml", "xl/calcChain.xml", "xl/printerSettings/",
    "xl/drawings/", "xl/media/", "xl/comments", "xl/threadedComments/", "xl/persons/", "xl/tables/",
    "xl/charts/", "xl/metadata.xml", "xl/richData/", "xl/featurePropertyBag/", "customXml/",
    "xl/pivotTables/", "xl/pivotCache/", "xl/slicers/", "xl/slicerCaches/", "xl/timelines/",
    "xl/timelineCaches/", "xl/ctrlProps/", "xl/externalLinks/", "xl/connections.xml",
    "xl/queryTables/", "xl/model/", "xl/webextensions/", "xl/activeX/", "xl/embeddings/",
    "xl/macrosheets/", "xl/dialogsheets/", "xl/chartsheets/", "_xmlsignatures/", "customUI/",
    "xl/vbaProject.bin", "xl/vbaData.xml", "xl/vbaProjectSignature.bin", "xl/volatileDependencies.xml",
    "xl/customProperty", "xl/revisions/", "xl/worksheets/_rels/",
)

FAIL_FUNCS = {
    "WEBSERVICE": "fetches data from the web",
    "RTD": "real-time data server (external program)",
    "CALL": "calls code in a DLL",
    "REGISTER": "registers DLL code",
    "REGISTER.ID": "registers DLL code",
    "IMAGE": "downloads an image from the web",
    "STOCKHISTORY": "downloads data from the web",
    "PY": "Python in Excel runs in the cloud",
    "CUBEVALUE": "external OLAP data", "CUBEMEMBER": "external OLAP data", "CUBESET": "external OLAP data",
    "CUBESETCOUNT": "external OLAP data", "CUBERANKEDMEMBER": "external OLAP data",
    "CUBEMEMBERPROPERTY": "external OLAP data", "CUBEKPIMEMBER": "external OLAP data",
}
WARN_FUNCS = {"FILTERXML": "parses XML (usually paired with WEBSERVICE)"}

_STRING = re.compile(r'"(?:[^"]|"")*"')
_FUNC = re.compile(r"((?:_xlfn\.|_xlws\.|_xludf\.)?[A-Za-z][A-Za-z0-9.]*)\s*\(")


class Report:
    def __init__(self, path):
        self.path = path
        self.items = []

    def add(self, status, check, detail):
        self.items.append({"status": status, "check": check, "detail": detail})

    @property
    def failed(self):
        return any(i["status"] == "FAIL" for i in self.items)


def _xml(zf, name):
    try:
        return ET.fromstring(zf.read(name))
    except (KeyError, ET.ParseError):
        return None


def _sheet_map(zf):
    """Map worksheet part path -> (sheet name, state)."""
    wb = _xml(zf, "xl/workbook.xml")
    rels = _xml(zf, "xl/_rels/workbook.xml.rels")
    if wb is None or rels is None:
        return {}
    targets = {}
    for r in rels.findall(f"{{{NS_PKG_REL}}}Relationship"):
        t = r.get("Target", "")
        t = t.lstrip("/") if t.startswith("/") else "xl/" + t
        targets[r.get("Id")] = str(PurePosixPath(t))
    out = {}
    for s in wb.iter(f"{{{NS_MAIN}}}sheet"):
        rid = s.get(f"{{{NS_REL}}}id")
        if rid in targets:
            out[targets[rid]] = (s.get("name"), s.get("state", "visible"))
    return out


def _formula_texts(zf, names):
    """Yield (where, formula text) for every formula-bearing element in the package."""
    tags = re.compile(r"<(?:\w+:)?(f|formula|formula1|formula2|definedName)\b[^>]*>(.*?)</(?:\w+:)?\1>", re.S)
    for n in names:
        if not n.endswith(".xml"):
            continue
        if not (n.startswith("xl/worksheets/") or n == "xl/workbook.xml"):
            continue
        text = zf.read(n).decode("utf-8", "replace")
        for m in tags.finditer(text):
            body = (m.group(2).replace("&quot;", '"').replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'"))
            yield n, body


def check_file(path, allow_passwords=False):
    rep = Report(path)
    low = path.lower()
    if not low.endswith(".xlsx"):
        rep.add("FAIL", "File type", f"not a plain .xlsx ({PurePosixPath(path).suffix or 'no extension'})")
    try:
        zf = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        rep.add("FAIL", "File type", "not a valid .xlsx package (could be an old .xls or a renamed file)")
        return rep
    names = zf.namelist()

    ct = zf.read("[Content_Types].xml").decode("utf-8", "replace") if "[Content_Types].xml" in names else ""
    if "macroEnabled" in ct:
        rep.add("FAIL", "Macros", "workbook content type is macro-enabled")

    def present(pred):
        return [n for n in names if pred(n)]

    groups = [
        ("Macros (VBA)", "FAIL", present(lambda n: re.search(r"vba(Project|Data|ProjectSignature)", n, re.I))),
        ("Macros (Excel 4.0 macro sheets)", "FAIL", present(lambda n: n.startswith(("xl/macrosheets/", "xl/dialogsheets/")))),
        ("ActiveX controls", "FAIL", present(lambda n: n.startswith("xl/activeX/"))),
        ("Embedded objects (OLE)", "FAIL", present(lambda n: n.startswith("xl/embeddings/"))),
        ("Links to other workbooks", "FAIL", present(lambda n: n.startswith("xl/externalLinks/"))),
        ("Data connections", "FAIL", present(lambda n: n == "xl/connections.xml" or n.startswith("xl/queryTables/"))),
        ("Data model (Power Pivot)", "FAIL", present(lambda n: n.startswith("xl/model/"))),
        ("Office add-ins", "FAIL", present(lambda n: n.startswith("xl/webextensions/"))),
        ("Custom ribbon (customUI)", "FAIL", present(lambda n: n.lower().startswith("customui/"))),
        ("Legacy form controls", "WARN", present(lambda n: n.startswith("xl/ctrlProps/"))),
    ]
    for label, status, hits in groups:
        if hits:
            rep.add(status, label, f"{len(hits)} part(s): " + ", ".join(hits[:5]))
        else:
            rep.add("PASS", label, "none")

    mashup = [n for n in names if n.startswith("customXml/item") and n.endswith(".xml")
              and b"DataMashup" in zf.read(n)]
    rep.add("FAIL" if mashup else "PASS", "Power Query", ", ".join(mashup) if mashup else "none")

    ext_pivot = [n for n in names if n.startswith("xl/pivotCache/pivotCacheDefinition")
                 and re.search(rb'<cacheSource[^>]*type="external"', zf.read(n))]
    if ext_pivot:
        rep.add("FAIL", "Pivot tables with external source", ", ".join(ext_pivot))

    # External relationships (hyperlinks to web/files, linked objects)
    ext_links, ext_other = [], []
    for n in names:
        if not n.endswith(".rels"):
            continue
        root = _xml(zf, n)
        if root is None:
            continue
        for r in root.findall(f"{{{NS_PKG_REL}}}Relationship"):
            if r.get("TargetMode") == "External":
                kind = r.get("Type", "").rsplit("/", 1)[-1]
                (ext_links if kind == "hyperlink" else ext_other).append(f"{kind}: {r.get('Target')}")
    rep.add("FAIL" if ext_other else "PASS", "External references (non-hyperlink)",
            "; ".join(ext_other[:10]) if ext_other else "none")
    if ext_links:
        rep.add("WARN", "Hyperlinks leaving the workbook", f"{len(ext_links)}: " + "; ".join(ext_links[:10]))

    # Formulas: external refs, DDE, risky functions, inventory of newer functions
    ext_refs, dde, risky, warn_funcs, web_links = [], [], {}, {}, []
    newer = {}
    internal_links = 0
    for where, body in _formula_texts(zf, names):
        bare = _STRING.sub('""', body)
        if re.search(r"\[\d+\]|\[[^\]]+\.xl\w*\]", bare):
            ext_refs.append(f"{where}: {body[:80]}")
        if re.search(r"[A-Za-z0-9_.]+\|", bare):
            dde.append(f"{where}: {body[:80]}")
        for fn in _FUNC.findall(bare):
            name = fn.upper()
            base = re.sub(r"^_(XLFN|XLWS|XLUDF)\.", "", name)
            if name.startswith(("_XLFN.", "_XLWS.")):
                newer[base] = newer.get(base, 0) + 1
            if base in FAIL_FUNCS:
                risky.setdefault(base, []).append(where)
            elif base in WARN_FUNCS:
                warn_funcs.setdefault(base, []).append(where)
            if base == "HYPERLINK":
                strings = _STRING.findall(body)
                if any(re.match(r'"(https?:|file:|mailto:|\\\\)', s, re.I) for s in strings):
                    web_links.append(f"{where}: {body[:80]}")
                else:
                    internal_links += 1
    rep.add("FAIL" if ext_refs else "PASS", "Formulas pointing at other workbooks",
            "; ".join(ext_refs[:5]) if ext_refs else "none")
    rep.add("FAIL" if dde else "PASS", "DDE formulas", "; ".join(dde[:5]) if dde else "none")
    if risky:
        for fn, wheres in risky.items():
            rep.add("FAIL", f"Function {fn}", f"{FAIL_FUNCS[fn]} - used {len(wheres)}x")
    else:
        rep.add("PASS", "Web / external-program functions", "none")
    for fn, wheres in warn_funcs.items():
        rep.add("WARN", f"Function {fn}", f"{WARN_FUNCS[fn]} - used {len(wheres)}x")
    if web_links:
        rep.add("WARN", "HYPERLINK formulas to web/files", f"{len(web_links)}: " + "; ".join(web_links[:5]))
    if internal_links:
        rep.add("INFO", "HYPERLINK formulas inside the workbook", f"{internal_links} (jump links - fine)")
    if newer:
        rep.add("INFO", "Newer Excel functions used",
                ", ".join(f"{k} x{v}" for k, v in sorted(newer.items())))

    wbx = _xml(zf, "xl/workbook.xml")
    if wbx is not None:
        autos = [d.get("name") for d in wbx.iter(f"{{{NS_MAIN}}}definedName")
                 if (d.get("name") or "").lower().removeprefix("_xlnm.")
                 in ("auto_open", "auto_close", "auto_activate")]
        if autos:
            rep.add("FAIL", "Auto-run macro names", ", ".join(autos))

    # Protection and passwords
    sheets = _sheet_map(zf)
    pw_sheets, protected, unprotected, very_hidden, hidden = [], [], [], [], []
    for part, (sname, state) in sheets.items():
        if state == "veryHidden":
            very_hidden.append(sname)
        elif state == "hidden":
            hidden.append(sname)
        if part not in names:
            continue
        txt = zf.read(part)
        m = re.search(rb"<sheetProtection\b[^>]*>", txt)
        if m:
            tag = m.group(0)
            if re.search(rb'\bsheet="(1|true)"', tag):
                protected.append(sname)
            else:
                unprotected.append(sname)
            if re.search(rb'\b(password|hashValue)="', tag):
                pw_sheets.append(sname)
        else:
            unprotected.append(sname)
    wb_pw = False
    if wbx is not None:
        wp = wbx.find(f"{{{NS_MAIN}}}workbookProtection")
        if wp is not None and any(k in wp.attrib for k in ("workbookPassword", "workbookHashValue",
                                                             "revisionsPassword", "revisionsHashValue")):
            wb_pw = True
    pw_status = "WARN" if allow_passwords else "FAIL"
    if pw_sheets or wb_pw:
        rep.add(pw_status, "Protection passwords",
                ("workbook structure; " if wb_pw else "") + ", ".join(pw_sheets))
    else:
        rep.add("PASS", "Protection passwords", "no passwords")
    rep.add("INFO", "Sheet protection", f"{len(protected)} protected, {len(unprotected)} not protected"
            + (f" (not protected: {', '.join(unprotected[:8])})" if unprotected else ""))
    if very_hidden:
        rep.add("WARN", "Very hidden sheets", "can't be unhidden from Excel's menus: " + ", ".join(very_hidden))
    if hidden:
        rep.add("INFO", "Hidden sheets", ", ".join(hidden))

    if any(n.startswith("xl/featurePropertyBag/") for n in names):
        rep.add("INFO", "Cell checkboxes", "present (Insert > Checkbox - allowed)")
    if any(n.startswith("_xmlsignatures/") for n in names):
        rep.add("INFO", "Digital signature", "present")

    # Personal information that travels with the file
    people = []
    core = _xml(zf, "docProps/core.xml")
    if core is not None:
        for el in core:
            tag = el.tag.split("}")[-1]
            if tag in ("creator", "lastModifiedBy") and (el.text or "").strip():
                people.append(f"{tag}={el.text.strip()}")
    app = _xml(zf, "docProps/app.xml")
    if app is not None:
        for el in app:
            tag = el.tag.split("}")[-1]
            if tag in ("Company", "Manager") and (el.text or "").strip():
                people.append(f"{tag}={el.text.strip()}")
    authors = set()
    for n in names:
        if re.match(r"xl/comments\d*\.xml$", n):
            root = _xml(zf, n)
            if root is not None:
                authors |= {a.text for a in root.iter(f"{{{NS_MAIN}}}author") if a.text}
        if n.startswith("xl/persons/"):
            authors |= set(re.findall(r'displayName="([^"]+)"', zf.read(n).decode("utf-8", "replace")))
    if authors:
        people.append("comment authors=" + ", ".join(sorted(authors)))
    rep.add("INFO", "Names stored in the file", "; ".join(people) if people else "none")

    if "docMetadata/LabelInfo.xml" in names:
        rep.add("INFO", "Sensitivity label", "present (docMetadata/LabelInfo.xml) - a rebuilt file "
                "may not carry it; re-apply the label in Excel if your site requires one")
    trash = [n for n in names if n.startswith("[trash]/")]
    if trash:
        rep.add("WARN", "Leftover data from earlier saves", f"{len(trash)} [trash] part(s) - "
                "old content Excel kept in the package; a clean re-save drops it")
    unknown = [n for n in names if not n.startswith(KNOWN_PREFIXES + ("docMetadata/", "[trash]/"))]
    if unknown:
        rep.add("WARN", "Unrecognised parts", "review: " + ", ".join(unknown[:10]))
    return rep


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+")
    ap.add_argument("--json", action="store_true", help="machine-readable output")
    ap.add_argument("--allow-passwords", action="store_true", help="treat protection passwords as WARN, not FAIL")
    args = ap.parse_args(argv)

    reports = [check_file(f, args.allow_passwords) for f in args.files]
    if args.json:
        print(json.dumps([{"file": r.path, "failed": r.failed, "checks": r.items} for r in reports], indent=2))
    else:
        for r in reports:
            verdict = "FAIL" if r.failed else "PASS"
            print(f"\n=== {r.path}  ->  {verdict}")
            for i in r.items:
                print(f"  [{i['status']:4}] {i['check']}: {i['detail']}")
    return 1 if any(r.failed for r in reports) else 0


if __name__ == "__main__":
    sys.exit(main())
