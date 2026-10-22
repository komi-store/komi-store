#!/usr/bin/env python3
"""Collect the release corpus used by the asset-matching replay fixture.

Fetches the newest releases (tag, date, prerelease flag, asset names) of real
Android projects and writes them to

    core/domain/src/jvmTest/resources/asset-replay/corpus.tsv

which AssetMatchingReplayTest loads offline. One line per release:
`repo<TAB>tag<TAB>published<TAB>prerelease<TAB>asset<TAB>asset...`
Re-run this when the corpus is meant to move forward; the replay itself never
touches the network.

Auth: uses $GITHUB_TOKEN / $GH_TOKEN, or falls back to `gh auth token`.
"""
import json
import os
import subprocess
import sys
import time
import urllib.request

REPOS = [
    # The cases the maintainers called out in review.
    "wxxsfxyzm/InstallerX-Revived",
    "LibChecker/LibChecker",
    "T8RIN/ImageToolbox",
    "florisboard/florisboard",
    "Kunzisoft/KeePassDX",
    "thunderbird/thunderbird-android",
    # Real Android projects whose release assets exercise the name shapes.
    "2dust/v2rayNG",
    "bggRGjQaUbCoE/PiliPlus",
    "ImranR98/Obtainium",
    "Predidit/Kazumi",
    "ReVanced/revanced-manager",
    "rumboalla/apkupdater",
    "shadowsocks/shadowsocks-android",
    "TeamNewPipe/NewPipe",
    "yuliskov/SmartTube",
    "termux/termux-app",
    "JunkFood02/Seal",
    "MuntashirAkon/AppManager",
    "gkd-kit/gkd",
    "libre-tube/LibreTube",
    "RikkaApps/Shizuku",
    "Aefyr/SAI",
    "Droid-ify/client",
    "MatsuriDayo/NekoBoxForAndroid",
    "zhanghai/MaterialFiles",
    "mihonapp/mihon",
    "iamr0s/InstallerX",
    "Paving-Base/APK-Installer",
    "samolego/Canta",
    "vvb2060/PackageInstaller",
    "FBlackBox/BlackBox",
    "xiaojieonly/Ehviewer_CN_SXJ",
    "xiaye13579/BBLL",
    "open-ani/animeko",
    "pppscn/SmsForwarder",
    "topjohnwu/Magisk",
    "tiann/KernelSU",
    "LSPosed/LSPosed",
    "VegaBobo/DSU-Sideloader",
    "brunodev85/winlator",
    "signalapp/Signal-Android",
    "DrKLO/Telegram",
    "yairm210/Unciv",
    "komi-store/komi-store",
    "ReadYouApp/ReadYou",
    "godotengine/godot",
    "0x192/universal-android-debloater",
    "mrhaydendp/Fire-Tools",
]

MAX_RELEASES = 60

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEST = os.path.join(ROOT, "core", "domain", "src", "jvmTest", "resources", "asset-replay", "corpus.tsv")


def token():
    for name in ("GITHUB_TOKEN", "GH_TOKEN"):
        value = os.environ.get(name)
        if value:
            return value
    try:
        return subprocess.check_output(["gh", "auth", "token"], text=True).strip()
    except Exception:
        sys.exit("no GitHub token: set GITHUB_TOKEN, or log in with `gh auth login`")


def fetch(url, tok):
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {tok}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "komi-store-asset-replay-collector",
        },
    )
    with urllib.request.urlopen(req) as response:
        return json.load(response)


def main():
    tok = token()
    corpus = []
    for full_name in REPOS:
        url = f"https://api.github.com/repos/{full_name}/releases?per_page={MAX_RELEASES}"
        try:
            releases = fetch(url, tok)
        except Exception as exc:  # collection keeps going; the replay just sees fewer repos
            print(f"WARN {full_name}: {exc}", file=sys.stderr)
            continue
        entries = []
        for release in releases:
            if release.get("draft"):
                continue
            entries.append(
                {
                    "tag": release["tag_name"],
                    "published": release.get("published_at") or release.get("created_at") or "",
                    "prerelease": bool(release.get("prerelease")),
                    "assets": [asset["name"] for asset in release.get("assets", [])],
                }
            )
        corpus.append({"repo": full_name, "releases": entries})
        print(f"{full_name}: {len(entries)} releases")
        time.sleep(0.2)

    os.makedirs(os.path.dirname(DEST), exist_ok=True)
    with open(DEST, "w", encoding="utf-8") as out:
        for entry in corpus:
            for release in entry["releases"]:
                fields = [entry["repo"], release["tag"], release["published"], "1" if release["prerelease"] else "0"]
                fields.extend(release["assets"])
                out.write("\t".join(fields) + "\n")
    total = sum(len(entry["releases"]) for entry in corpus)
    print(f"wrote {DEST}: {len(corpus)} repos, {total} releases")


if __name__ == "__main__":
    main()
