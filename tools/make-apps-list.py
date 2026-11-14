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
            "section": "core" if r["name"] in CORE else "extras",
        })
    apps.sort(key=lambda a: (a["section"] != "core", CORE.index(a["name"]) if a["section"] == "core" else 0, a["name"].lower()))
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
    print(f"{len(apps)} apps ({sum(a['section'] == 'core' for a in apps)} core) -> {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
