#!/usr/bin/env python3
"""
Finds developer documentation pages that may be out of date.

Every page in manual/developer/ has two front matter keys:

    verified: b244ca1          the commit the page was last checked against the code
    covers:                    the code the page describes (files or folders, relative to the repository root)
      - lingollm/src/main/resources/migration

The script lists, for each page, the covered files that changed between `verified` and HEAD.

    python3 tools/check_dev_docs.py                  # report: added / deleted files by name, modified ones counted
    python3 tools/check_dev_docs.py -v               # also name the modified files
    python3 tools/check_dev_docs.py --check          # same report, exit code 1 when any page is behind (for CI)
    python3 tools/check_dev_docs.py --stamp database.md plugins/overview.md
                                                     # mark pages as verified at HEAD (after you reviewed them)
    python3 tools/check_dev_docs.py --stamp-all      # ... all pages

A page is only as good as its `covers` list: when a page starts describing other code, add the path there. Stamp pages
in a commit of their own after the review - the stamp is the commit that was checked, so it can't name the commit
that contains it. Uncommitted changes are not seen, commit first.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGES = ROOT / "manual" / "developer"
FRONT = re.compile(r"\A---\n(.*?)\n---\n", re.S)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def read_page(path):
    """Returns (text, verified, covers); verified is None when the page has no stamp."""
    text = path.read_text(encoding="utf-8")
    m = FRONT.match(text)
    if not m:
        return text, None, []
    verified = re.search(r"^verified:\s*(\S+)\s*$", m.group(1), re.M)
    covers = []
    block = re.search(r"^covers:\s*\n((?:[ \t]+-[^\n]*\n?)*)", m.group(1) + "\n", re.M)
    if block:
        covers = [line.strip()[1:].strip() for line in block.group(1).splitlines() if line.strip()]
    return text, verified.group(1) if verified else None, covers


def changes(verified, covers):
    """Added, deleted and modified files of the covered paths since `verified`."""
    out = git("diff", "--name-status", "--no-renames", f"{verified}..HEAD", "--", *covers)
    added, deleted, modified = [], [], []
    for line in out.splitlines():
        status, name = line.split("\t", 1)
        {"A": added, "D": deleted}.get(status[0], modified).append(name)
    return added, deleted, modified


def stamp(path, head):
    text = path.read_text(encoding="utf-8")
    m = FRONT.match(text)
    if re.search(r"^verified:.*$", m.group(1), re.M):
        front = re.sub(r"^verified:.*$", f"verified: {head}", m.group(1), flags=re.M)
    else:
        front = f"{m.group(1)}\nverified: {head}"
    path.write_text(f"---\n{front}\n---\n" + text[m.end():], encoding="utf-8")


def main(argv):
    verbose = "-v" in argv
    check = "--check" in argv
    head = git("rev-parse", "--short", "HEAD").strip()
    pages = sorted(PAGES.rglob("*.md"))

    if "--stamp-all" in argv or "--stamp" in argv:
        names = [a for a in argv if a.endswith(".md")]
        for page in pages:
            if "--stamp-all" in argv or str(page.relative_to(PAGES)) in names:
                stamp(page, head)
                print(f"stamped {page.relative_to(PAGES)} at {head}")
        return 0

    behind = 0
    for page in pages:
        name = str(page.relative_to(PAGES))
        _, verified, covers = read_page(page)
        if verified is None or not covers:
            print(f"{name}: no `verified` / `covers` in the front matter")
            behind += 1
            continue
        missing = [c for c in covers if not (ROOT / c).exists()]
        if missing:
            print(f"{name}: covers paths that don't exist: {', '.join(missing)}")
            behind += 1
        try:
            added, deleted, modified = changes(verified, covers)
        except subprocess.CalledProcessError:
            print(f"{name}: `verified: {verified}` is not a commit of this repository")
            behind += 1
            continue
        if not (added or deleted or modified):
            continue
        behind += 1
        print(f"{name}  (verified {verified}, {len(added)} added, {len(deleted)} deleted, {len(modified)} modified)")
        for f in added:
            print(f"    A {f}")
        for f in deleted:
            print(f"    D {f}")
        if verbose:
            for f in modified:
                print(f"    M {f}")

    if behind:
        print(f"\n{behind} page(s) to review. After reviewing: python3 tools/check_dev_docs.py --stamp <page.md>")
    else:
        print(f"all {len(pages)} pages are up to date with the code they cover (HEAD {head})")
    return 1 if (check and behind) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
