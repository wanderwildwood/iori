#!/usr/bin/env python3
"""Writes app/src/iori/assets/iori_apps.json: the apps Home offers under "Your apps".

One entry per app in the master list (apps.tsv, named by the APPS_TSV environment variable), never
typed by hand:
its shown name, package, GitHub repo, the release file to download, the SHA-256 of the
certificate its releases are signed with (read from the release APK on this machine), and
which section it is listed in. Home installs nothing whose certificate differs from this.

    tools/make-apps-list.py            # write the list
    tools/make-apps-list.py --check    # also ask GitHub that every download link exists
"""
import csv, glob, json, os, subprocess, sys, urllib.request

HOME = os.path.expanduser("~")
TSV = os.environ.get("APPS_TSV") or sys.exit("set APPS_TSV to the apps.tsv master list")
RELEASES = f"{HOME}/Desktop/dev/apps/releases"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app/src/iori/assets/iori_apps.json")

# The core set: apps that share an account or feed another app (MAKEOVER-PLAN.md, "The package").
CORE = ["Handset", "Messaging", "Connections", "Datebook", "Memo", "Mailbox", "Pictures", "Lens",
        "Cabinet", "Passwords", "Time", "Glance", "Sky", "Music Box", "Feeds", "Dictionary", "Translate"]

# Every other app, under the heading it is listed with. The keys are IoriApps.SECTIONS; an app
# that is in apps.tsv and not here stops the script, so a new app is always given a place.
SECTIONS = {
    "outdoors": ["Atlas", "Topo", "Detour", "Field Kit", "Birding", "Mushroom Journal", "Star Chart",
                 "Whereabouts", "Mesh"],
    "road": ["Car Check", "Parked", "Speedometer"],
    "daily": ["Medicine", "Cycle", "Practice", "Recipes", "Wallet"],
    "music": ["Tuner", "Hum", "Songbook", "Engraver"],
    "reading": ["Audio Reader", "Shelf", "Typewriter", "Dream Log"],
    "games": ["Chess+", "Go", "Solitaire", "Handheld Games", "Tarot", "I Ching"],
    "tools": ["Abacus", "Level", "Remote", "Bitcoin", "Torrents"],
}
ORDER = ["core"] + list(SECTIONS)
SECTION_OF = {name: key for key, names in SECTIONS.items() for name in names}


def cert_of(apk):
    tools = sorted(glob.glob(f"{HOME}/Android/Sdk/build-tools/*"), key=lambda p: [int(x) if x.isdigit() else 0 for x in os.path.basename(p).split(".")])[-1]
    out = subprocess.run([f"{tools}/apksigner", "verify", "--print-certs", apk], capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        if "SHA-256 digest" in line:
            return line.split()[-1]
    raise SystemExit(f"no certificate in {apk}")


def main():
    rows = [r for r in csv.DictReader((l for l in open(TSV) if not l.startswith("#")), delimiter="\t")]
    names = {r["name"] for r in rows}
    missing = [c for c in CORE if c not in names]
    if missing:
        raise SystemExit(f"core apps not in apps.tsv: {missing}")
    unplaced = sorted(n for n in names if n not in CORE and n not in SECTION_OF)
    if unplaced:
        raise SystemExit(f"apps with no section (add them to SECTIONS): {unplaced}")
    gone = sorted(n for n in SECTION_OF if n not in names)
    if gone:
        raise SystemExit(f"SECTIONS names apps that are not in apps.tsv: {gone}")
    apps = []
    for r in rows:
        repo = r["repo"]
        apk = f"{RELEASES}/{repo}/{repo}.apk"
        if not os.path.exists(apk):
            raise SystemExit(f"no release APK for {repo}: {apk}")
        apps.append({
            "name": r["name"],
            "package": r["package"],
            "repo": r["github"],
            "file": f"{repo}.apk",
            "cert": cert_of(apk),
            "section": "core" if r["name"] in CORE else SECTION_OF[r["name"]],
        })
    apps.sort(key=lambda a: (ORDER.index(a["section"]), CORE.index(a["name"]) if a["section"] == "core" else 0, a["name"].lower()))
    if "--check" in sys.argv:
        bad = 0
        for a in apps:
            for f in (a["file"], a["file"] + ".sha256"):
                url = f"https://github.com/{a['repo']}/releases/latest/download/{f}"
                try:
                    urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=20)
                except Exception as e:
                    bad += 1
                    print(f"!! {url}: {e}")
        print(f"links checked: {2 * len(apps) - bad} ok, {bad} bad")
        if bad:
            raise SystemExit(1)
    with open(OUT, "w") as f:
        json.dump({"apps": apps}, f, indent=1)
        f.write("\n")
    print(f"{len(apps)} apps ({', '.join(f'{sum(a["section"] == k for a in apps)} {k}' for k in ORDER)}) -> {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
