#!/usr/bin/env python3
"""Checks Home's hooks in upstream's files against the hook ledger in IORI.md.

A hook is a line in one of upstream's files (a file that exists in the upstream base) that
carries an `iori:` marker in a comment. A block of changed lines is held between a line with
`iori: begin` and a line with `iori: end`; both count as markers. Lines that continue a marker's
own comment (more `//` lines under a `// iori:` line, the rest of an XML comment that opens
with `iori:`) count as marked too.

The ledger in IORI.md ("## Hook ledger") has:

  - `Upstream base: `vX.Y`` : the upstream tag Home is built on;
  - `**N** marker lines`    : how many marker lines there are in all;
  - a table `| File | Lines | Purpose |`: each row a file and its marker lines, written
    `12`, `12, 40` or `12-30` (a begin/end block: 12 and 30 are the markers). Rows are in
    file order, then line order;
  - a table `| File | Unmarked lines | What |`: upstream files changed on purpose with no
    markers (the carried commits, whole files replaced), and how many changed lines each has.

What it checks:

  1. every marker is in the ledger at its line, and every ledger line has a marker;
  2. the stated total and the row order;
  3. every line added to an upstream file since the base (`git diff <base>`, the working tree)
     is marked, inside a block, or in a file of the carried table, and each carried file still
     has the number of unmarked lines the table says. A hook half lost in a merge, or an
     unmarked edit, shows here even when the marker count is right.

    tools/iori-hooks.py               # check; exit 1 on any difference
    tools/iori-hooks.py --list        # print every marker line (file:line: text)
    tools/iori-hooks.py --base v0.7   # check against another base (after a merge, before the
                                      # ledger's base line is changed)

Blank lines are not counted. *.md files and files that are not in the base are not upstream's
hooks and are not looked at. Run from anywhere inside the repository.
"""
import os, re, subprocess, sys
from collections import defaultdict

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                      check=True, cwd=os.path.dirname(os.path.abspath(__file__))).stdout.strip()
LEDGER = os.path.join(ROOT, "IORI.md")
MARK = "iori:"


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True, cwd=ROOT).stdout


def cells(row):
    return [c.strip() for c in row.strip().strip("|").split("|")]


def read_ledger():
    text = open(LEDGER, encoding="utf-8").read()
    m = re.search(r"^## Hook ledger\n(.*?)(?=^## )", text, re.S | re.M)
    if not m:
        sys.exit("IORI.md has no '## Hook ledger' section")
    sec = m.group(1)
    base = re.search(r"Upstream base: `([^`]+)`", sec)
    total = re.search(r"\*\*(\d+)\*\* marker lines", sec)
    hooks, carried = [], {}
    table = None
    for line in sec.splitlines():
        if not line.startswith("|"):
            table = None
            continue
        c = cells(line)
        if c[:2] == ["File", "Lines"]:
            table = "hooks"; continue
        if c[:2] == ["File", "Unmarked lines"]:
            table = "carried"; continue
        if table is None or set(line) <= set("|-: "):
            continue
        path = re.match(r"`([^`]+)`", c[0])
        if not path:
            continue
        if table == "hooks":
            hooks.append((path.group(1), c[1]))
        else:
            carried[path.group(1)] = int(c[1])
    return (base.group(1) if base else None), (int(total.group(1)) if total else None), hooks, carried


def parse_lines(spec):
    """'12, 40-55' -> ([12, 40, 55], [(40, 55)])"""
    lines, blocks = [], []
    for tok in spec.split(","):
        tok = tok.strip()
        if not tok:
            continue
        if "-" in tok:
            a, b = (int(x) for x in tok.split("-"))
            lines += [a, b]
            blocks.append((a, b))
        else:
            lines.append(int(tok))
    return lines, blocks


def upstream_files(base):
    in_base = set(git("ls-tree", "-r", "--name-only", base).splitlines())
    tracked = set(git("ls-files").splitlines())
    return sorted(p for p in in_base & tracked
                  if not p.endswith(".md") and os.path.isfile(os.path.join(ROOT, p)))


def read(path):
    try:
        return open(os.path.join(ROOT, path), encoding="utf-8").read().split("\n")
    except UnicodeDecodeError:
        return []


def covered(lines):
    """Line numbers (1-based) that are marked: markers, block insides, comment continuations."""
    cov, open_block, xml_comment, slash_chain = set(), None, False, False
    for i, l in enumerate(lines, 1):
        s = l.strip()
        if MARK in l:
            cov.add(i)
            if re.search(r"iori: begin\b", l):
                open_block = i
            if re.search(r"iori: end\b", l):
                open_block = None
            after = l[l.index(MARK):]
            xml_comment = "<!--" in l and "-->" not in after
            slash_chain = s.startswith("//")
            continue
        if open_block is not None:
            cov.add(i); continue
        if xml_comment:
            cov.add(i)
            if "-->" in l:
                xml_comment = False
            continue
        if slash_chain and s.startswith("//"):
            cov.add(i); continue
        slash_chain = False
    return cov


def added_lines(base, files):
    """{file: [new-side line numbers of added lines]} for upstream files changed since base."""
    out = git("diff", "-U0", "--no-color", "--no-ext-diff", "--diff-filter=M", base, "--", *files)
    res, path, n = defaultdict(list), None, 0
    for l in out.splitlines():
        if l.startswith("+++ "):
            path = l[6:] if l.startswith("+++ b/") else None
        elif l.startswith("@@"):
            n = int(re.match(r"@@ -\S+ \+(\d+)", l).group(1))
        elif l.startswith("+") and path:
            if l[1:].strip():
                res[path].append(n)
            n += 1
    return res


def main():
    args = sys.argv[1:]
    base, total, hooks, carried = read_ledger()
    if "--base" in args:
        base = args[args.index("--base") + 1]
    if not base:
        sys.exit("no upstream base: give --base or write 'Upstream base: `vX.Y`' in the ledger")
    files = upstream_files(base)
    markers = {}
    for p in files:
        lines = read(p)
        found = {i: l.strip() for i, l in enumerate(lines, 1) if MARK in l}
        if found:
            markers[p] = found
    if "--list" in args:
        for p in sorted(markers):
            for i, t in sorted(markers[p].items()):
                print(f"{p}:{i}: {t[:160]}")
        print(f"{sum(len(v) for v in markers.values())} marker lines in {len(markers)} files")
        return 0

    problems = []
    want, blocks, prev = defaultdict(set), defaultdict(list), None
    for path, spec in hooks:
        try:
            nums, bl = parse_lines(spec)
        except ValueError:
            problems.append(f"ledger: cannot read the lines of {path}: {spec!r}")
            continue
        if nums != sorted(nums):
            problems.append(f"ledger: lines not in order in the row {path}: {spec}")
        key = (path, nums[0] if nums else 0)
        if prev and key < prev:
            problems.append(f"ledger: row {path} ({spec}) is out of order after {prev[0]} ({prev[1]})")
        prev = key
        if path not in files:
            problems.append(f"ledger: {path} is not an upstream file at {base} (or is gone)")
        dup = want[path] & set(nums)
        if dup:
            problems.append(f"ledger: {path} lists {sorted(dup)} twice")
        want[path] |= set(nums)
        blocks[path] += bl
    ledger_count = sum(len(v) for v in want.values())
    real_count = sum(len(v) for v in markers.values())

    for p in sorted(set(want) | set(markers)):
        have = set(markers.get(p, {}))
        lost = sorted(want[p] - have)
        new = sorted(have - want[p])
        if lost:
            problems.append(f"{p}: the ledger has {lost}, with no marker there")
        for i in new:
            problems.append(f"{p}:{i}: marker not in the ledger: {markers[p][i][:120]}")
        text = markers.get(p, {})
        for a, b in blocks[p]:
            if a in text and not re.search(r"iori: begin\b", text[a]):
                problems.append(f"{p}:{a}: the ledger has a block from here, but the line has no 'iori: begin'")
            if b in text and not re.search(r"iori: end\b", text[b]):
                problems.append(f"{p}:{b}: the ledger has a block ending here, but the line has no 'iori: end'")

    if total is None:
        problems.append("ledger: no '**N** marker lines' total")
    elif total != real_count or total != ledger_count:
        problems.append(f"total: the ledger says {total}; its rows give {ledger_count}; the tree has {real_count}")

    changed = added_lines(base, files)
    for p, found in sorted(markers.items()):
        opened = None
        for i, t in sorted(found.items()):
            if re.search(r"iori: begin\b", t):
                if opened:
                    problems.append(f"{p}:{i}: 'iori: begin' inside the block opened at {opened}")
                opened = i
            elif re.search(r"iori: end\b", t):
                if not opened:
                    problems.append(f"{p}:{i}: 'iori: end' with no 'iori: begin' above it")
                elif not any(opened < n < i for n in changed.get(p, [])):
                    problems.append(f"{p}:{opened}-{i}: nothing in this block differs from {base} any more")
                opened = None
        if opened:
            problems.append(f"{p}:{opened}: 'iori: begin' with no 'iori: end' below it")
    for p in sorted(set(changed) | set(carried)):
        lines = read(p) if p in files else []
        cov = covered(lines)
        loose = [i for i in changed.get(p, []) if i not in cov]
        if p in carried:
            if len(loose) != carried[p]:
                problems.append(f"{p}: {len(loose)} unmarked changed lines; the carried table says {carried[p]}"
                                f" (look at: git diff {base} -- {p})")
        elif loose:
            shown = ", ".join(map(str, loose[:30])) + (" ..." if len(loose) > 30 else "")
            problems.append(f"{p}: changed since {base} with no marker at {shown}")

    print(f"base {base}: {real_count} marker lines in {len(markers)} upstream files; "
          f"ledger {ledger_count} in {len(hooks)} rows; {len(carried)} carried files")
    if problems:
        print(f"{len(problems)} problem(s):")
        for x in problems:
            print("  " + x)
        return 1
    print("ledger matches the tree")
    return 0


if __name__ == "__main__":
    sys.exit(main())
