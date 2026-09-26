"""Put the fixed Fabric dependencies in the event image build context."""

import hashlib
import json
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
MODS = (
    ("fabric-api", "0.161.0+26.3"),
    ("fabricproxy-lite", "v2.12.0"),
    ("simple-voice-chat", "fabric-2.6.24+26.3"),
    ("emotecraft", "y5qOKnGD"),  # 3.5.0-a.build.169 for Minecraft 26.3
    ("player-animation-library", "kemJXVHZ"),  # 1.2.7 for Minecraft 26.3
)


def download(project: str, version: str) -> None:
    print(f"Download {project} {version}", flush=True)
    url = f"https://api.modrinth.com/v2/project/{project}/version/{quote(version, safe='')}"
    with urlopen(Request(url, headers={"User-Agent": "MMUCraft-event-image/1"}), timeout=30) as response:
        release = json.load(response)
    file = next((item for item in release["files"] if item.get("primary")), release["files"][0])
    with urlopen(Request(file["url"], headers={"User-Agent": "MMUCraft-event-image/1"}), timeout=60) as response:
        contents = response.read()
    if hashlib.sha512(contents).hexdigest() != file["hashes"]["sha512"]:
        raise ValueError(f"Hash mismatch for {project} {version}")
    (ROOT / "mods").mkdir(exist_ok=True)
    (ROOT / "mods" / f"{project}-{version}.jar").write_bytes(contents)


if __name__ == "__main__":
    for project, version in MODS:
        download(project, version)
