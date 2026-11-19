#!/usr/bin/env python3
"""Lists what of upstream's Home hides without touching it, and what upstream changed there.

Three kinds of thing merge cleanly however upstream changes them, because Home never edits
upstream's lines; this script is the check the merge procedure runs for them (IORI.md,
"Shadowed upstream code and resources"):

  1. Strings. app/src/iori/res is added to the debug and release resources, which outrank
     main's, so a string (or plurals, string-array) of the same name there replaces upstream's
     English one. Checked: every override's placeholders (%s, %1$d ...) match upstream's;
     an override whose name upstream no longer has (renamed or dropped since the base: Home's
     wording is then orphaned and upstream's new one shows).
  2. Whole resource files. A file in app/src/iori/res at the same path as one in
     app/src/main/res replaces it outright (xml/file_paths.xml, the launcher icon). Checked:
     for res/xml files, each element of upstream's is still in Home's copy (a path upstream
     adds to file_paths.xml must be carried over, or FileProvider fails on it at run time).
  3. Upstream code Home goes around: functions that a hook returns from before they run, or
     never calls. Listed in IORI.md's table `| File | Function | Home's own |`. Checked: each
     function is still there.

With --to <new upstream ref>, it also lists, between the base (IORI.md's "Upstream base", or
--from) and that ref: overridden strings upstream reworded, renamed or dropped; shadowed files
upstream changed; shadowed functions upstream changed (with the git diff to read). Each one is
carried over by hand into Home's copy (IoriFirstRun, IoriDialogs, the iori strings ...).

    tools/iori-shadows.py                 # the tree as it is; exit 1 on a problem
    tools/iori-shadows.py --to v0.7       # before or during a merge of v0.7
    tools/iori-shadows.py --list          # also print every overridden name

Only the default locale (values/) is overridden; other locales are upstream's as they are.
"""
import os, re, subprocess, sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                      check=True, cwd=os.path.dirname(os.path.abspath(__file__))).stdout.strip()
IORI_RES = "app/src/iori/res"
MAIN_RES = "app/src/main/res"
KINDS = ("string", "plurals", "string-array")
FMT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def git(*args, check=True):
    r = subprocess.run(["git", *args], capture_output=True, text=True, cwd=ROOT)
    if check and r.returncode:
        sys.exit(f"git {' '.join(args)}: {r.stderr.strip()}")
    return r.stdout if r.returncode == 0 else None


def show(ref, path):
    """A file's text at ref, or in the working tree when ref is None; None if it is not there."""
    if ref is None:
        p = os.path.join(ROOT, path)
        return open(p, encoding="utf-8").read() if os.path.isfile(p) else None
    return git("show", f"{ref}:{path}", check=False)


def ls(ref, path):
    if ref is None:
        d = os.path.join(ROOT, path)
        out = []
        for base, _, names in os.walk(d):
            out += [os.path.relpath(os.path.join(base, n), ROOT) for n in names]
        return sorted(out)
    return sorted((git("ls-tree", "-r", "--name-only", ref, "--", path, check=False) or "").splitlines())


def text_of(el):
    return "".join(el.itertext()).strip()


def resources(ref, folder):
    """{name: (kind, [texts], file)} from folder/values/*.xml."""
    res = {}
    for path in ls(ref, f"{folder}/values"):
        if not path.endswith(".xml") or "/values/" not in path:
            continue
        src = show(ref, path)
        if not src:
            continue
        try:
            root = ET.fromstring(src)
        except ET.ParseError as e:
            sys.exit(f"{path}: {e}")
        for el in root:
            if el.tag in KINDS and el.get("name"):
                texts = [text_of(el)] if el.tag == "string" else [text_of(i) for i in el.findall("item")]
                res[el.get("name")] = (el.tag, texts, path)
    return res


def fmts(texts):
    return sorted(set(m for t in texts for m in FMT.findall(t.replace("%%", ""))))


def ledger():
    text = open(os.path.join(ROOT, "IORI.md"), encoding="utf-8").read()
    base = re.search(r"Upstream base: `([^`]+)`", text)
    funcs = []
    on = False
    for line in text.splitlines():
        if not line.startswith("|"):
            on = False
            continue
        c = [x.strip() for x in line.strip().strip("|").split("|")]
        if c[:2] == ["File", "Function"]:
            on = True
            continue
        if on and not set(line) <= set("|-: "):
            f, fn = re.match(r"`([^`]+)`", c[0]), re.match(r"`([^`]+)`", c[1])
            if f and fn:
                funcs.append((f.group(1), fn.group(1)))
    return (base.group(1) if base else None), funcs


def function_range(src, name):
    """(first, last) 1-based lines of `fun name(...)` and its body, by brace matching."""
    if src is None:
        return None
    m = re.search(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?" + re.escape(name) + r"\s*\(", src)
    if not m:
        return None
    i, depth = m.end() - 1, 0
    while i < len(src):  # the parameter list
        ch = src[i]
        depth += ch == "("
        depth -= ch == ")"
        i += 1
        if depth == 0:
            break
    while i < len(src) and src[i] not in "{=":
        i += 1
    if i < len(src) and src[i] == "{":
        depth, in_str = 0, None
        while i < len(src):
            ch = src[i]
            if in_str:
                if ch == "\\":
                    i += 1
                elif ch == in_str:
                    in_str = None
            elif ch in "\"'":
                in_str = ch
            elif ch == "/" and src.startswith("//", i):
                i = src.find("\n", i)
                if i < 0:
                    break
                continue
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
    else:
        i = src.find("\n", i)
    first = src.count("\n", 0, m.start()) + 1
    return first, src.count("\n", 0, max(i, 0)) + 1


def changed_old_lines(old, new, path):
    """Old-side line numbers touched by git diff old..new on path."""
    out = git("diff", "-U0", "--no-color", old, new, "--", path, check=False) or ""
    touched = set()
    for l in out.splitlines():
        m = re.match(r"@@ -(\d+)(?:,(\d+))? ", l)
        if m:
            a, n = int(m.group(1)), int(m.group(2) or 1)
            touched |= set(range(a, a + max(n, 1)))
    return touched


def canon(el):
    return (el.tag, tuple(sorted(el.attrib.items())))


def main():
    args = sys.argv[1:]
    base, funcs = ledger()
    if "--from" in args:
        base = args[args.index("--from") + 1]
    new = args[args.index("--to") + 1] if "--to" in args else None
    if not base:
        sys.exit("no upstream base: give --from or write 'Upstream base: `vX.Y`' in IORI.md")
    problems, review = [], []

    # 1. strings
    home = resources(None, IORI_RES)
    main_now = resources(None, MAIN_RES)
    main_base = resources(base, MAIN_RES)
    overrides = sorted(n for n in home if n in main_now or n in main_base)
    per_file = defaultdict(int)
    for n in overrides:
        per_file[home[n][2]] += 1
        if n not in main_now:
            problems.append(f"string {n}: Home overrides it ({home[n][2]}) but upstream's strings no longer have it")
            continue
        if home[n][0] != main_now[n][0]:
            problems.append(f"string {n}: Home's is a {home[n][0]}, upstream's a {main_now[n][0]}")
        if fmts(home[n][1]) != fmts(main_now[n][1]):
            problems.append(f"string {n}: placeholders {fmts(home[n][1])} in Home's, {fmts(main_now[n][1])} in upstream's")
    print(f"strings: Home overrides {len(overrides)} of upstream's English strings: " +
          ", ".join(f"{os.path.basename(f)} {c}" for f, c in sorted(per_file.items())))
    if "--list" in args:
        for n in overrides:
            print(f"  {n}  ({os.path.basename(home[n][2])})")
    if new:
        main_new = resources(new, MAIN_RES)
        if not main_new:
            sys.exit(f"no {MAIN_RES}/values at {new}")
        for n in overrides:
            if n in main_base and n not in main_new:
                review.append(f"string {n}: upstream dropped or renamed it between {base} and {new}")
            elif n in main_base and main_base[n][1] != main_new[n][1]:
                review.append(f"string {n}: upstream reworded it: {main_base[n][1]!r} -> {main_new[n][1]!r}")
                if fmts(main_base[n][1]) != fmts(main_new[n][1]):
                    review.append(f"string {n}: and its placeholders changed: {fmts(main_new[n][1])}")

    # 2. whole files
    shadows = []
    for path in ls(None, IORI_RES):
        rel = os.path.relpath(path, IORI_RES)
        if rel.split(os.sep)[0].startswith("values"):
            continue
        up = f"{MAIN_RES}/{rel}"
        if show(None, up) is not None or show(base, up) is not None:
            shadows.append((path, up))
    print("files Home's copy stands in for: " + ", ".join(up for _, up in shadows))
    for path, up in shadows:
        cur = show(None, up)
        if cur is None:
            problems.append(f"{up}: Home's {path} stands in for it, but upstream's is gone")
            continue
        if up.startswith(f"{MAIN_RES}/xml/"):
            theirs = {canon(e) for e in ET.fromstring(cur).iter()}
            ours = {canon(e) for e in ET.fromstring(show(None, path)).iter()}
            for tag, attrs in sorted(theirs - ours):
                problems.append(f"{path}: lacks upstream's <{tag} {' '.join(f'{k}={v!r}' for k, v in attrs)}>")
        if new and show(base, up) != show(new, up):
            review.append(f"{up}: upstream changed it between {base} and {new}; carry it into {path}"
                          f" (git diff {base} {new} -- {up})")

    # 3. code Home goes around
    for path, fn in funcs:
        if function_range(show(None, path), fn) is None:
            problems.append(f"{path}: no `fun {fn}` any more; update IORI.md's shadowed-code table")
        if new:
            rng = function_range(show(base, path), fn)
            if rng is None:
                review.append(f"{path}: `fun {fn}` not found at {base}")
                continue
            if changed_old_lines(base, new, path) & set(range(rng[0], rng[1] + 1)):
                review.append(f"{path}: upstream changed `{fn}` (lines {rng[0]}-{rng[1]} at {base});"
                              f" read git diff {base} {new} -- {path} and port it into Home's own")
    print(f"code Home goes around: {len(funcs)} functions" + (f"; compared {base}..{new}" if new else ""))

    for x in review:
        print("  review: " + x)
    if problems:
        print(f"{len(problems)} problem(s):")
        for x in problems:
            print("  " + x)
        return 1
    print("no problems" + (f"; {len(review)} change(s) to review" if new else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
