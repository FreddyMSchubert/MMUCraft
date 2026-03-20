#!/usr/bin/env python3
from pathlib import Path
import json, os, shutil, subprocess, tempfile, urllib.request, zipfile
import json, urllib.parse, urllib.request, http.cookiejar, shutil, zipfile

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
GENERATOR = HERE / "items-respack-generator"
MERGER = HERE / "ResourcePackMerger"
ITEMS = HERE.parent / "data" / "data" / "items"
PACKS = HERE / "packs"
GENERATED = PACKS / "generated"
MERGED = PACKS / "main-pack"
FINAL_ZIP = PACKS / "main-pack.zip"
WEB_ZIP = ROOT / "services" / "web" / "public" / "packs" / "main.zip"
CONFIG = HERE / "main-pack.config.json"

# Vanilla Tweaks Stuff

VT_BASE = "https://vanillatweaks.net"
VT_HEADERS = {
	"User-Agent": "Mozilla/5.0",
	"Accept": "application/json, text/plain, */*",
	"Origin": VT_BASE,
	"Referer": f"{VT_BASE}/",
}

VT_OPENER = urllib.request.build_opener(
	urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
)

def vt_request(url, data=None, headers=None):
	req = urllib.request.Request(url, data=data, headers={**VT_HEADERS, **(headers or {})})
	with VT_OPENER.open(req) as r:
		return r.read(), r.headers

def resolve_vt_share_code(code: str) -> dict:
	body, _ = vt_request(f"{VT_BASE}/assets/server/sharecode.php?code={urllib.parse.quote(code)}")
	spec = json.loads(body.decode("utf-8"))
	if spec.get("result") not in (None, "ok"):
		raise RuntimeError(f"Vanilla Tweaks share code failed: {spec}")
	if spec.get("type") != "resourcepacks":
		raise RuntimeError(f"Share code {code} is not a resource pack share code")
	return spec

def build_vt_resourcepack_zip(code: str, zip_path):
	spec = resolve_vt_share_code(code)

	payload = urllib.parse.urlencode({
		"packs": json.dumps(spec["packs"], separators=(",", ":")),
		"version": spec["version"],
	}).encode("utf-8")

	body, _ = vt_request(
		f"{VT_BASE}/assets/server/zipresourcepacks.php",
		data=payload,
		headers={"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
	)
	result = json.loads(body.decode("utf-8"))
	link = result.get("link")
	if not link:
		raise RuntimeError(f"Vanilla Tweaks zip generation failed: {result}")

	if not link.startswith("/"):
		link = "/" + link

	data, _ = vt_request(f"{VT_BASE}{link}")
	with open(zip_path, "wb") as f:
		f.write(data)


## GENERAL LOGIC

def run(*cmd, cwd=None):
	cmd = list(cmd)
	if os.name == "nt":
		if cmd[0] == "npm":
			cmd[0] = "npm.cmd"
		elif cmd[0] == "mvn":
			cmd[0] = "mvn.cmd"
	subprocess.run(cmd, cwd=cwd, check=True)


def rm(path: Path):
	if path.is_dir():
		shutil.rmtree(path, ignore_errors=True)
	elif path.exists():
		path.unlink()


def build_generated():
	print("==> Generating resource pack from item definitions")
	if not (GENERATOR / "node_modules").exists():
		run("npm", "ci", cwd=GENERATOR)
	rm(GENERATED)
	run(
		"npm", "run", "generate", "--",
		"--source", str(ITEMS),
		"--vanilla-armor", str(GENERATOR / "vanilla_armor_assets"),
		"--output", str(GENERATED),
		cwd=GENERATOR,
	)


def build_merger_jar():
	print("==> Building ResourcePackMerger")
	if os.name == "nt" and (MERGER / "mvnw.cmd").exists():
		run(str(MERGER / "mvnw.cmd"), "-q", "-DskipTests", "package", cwd=MERGER)
	elif (MERGER / "mvnw").exists():
		run(str(MERGER / "mvnw"), "-q", "-DskipTests", "package", cwd=MERGER)
	else:
		run("mvn", "-q", "-DskipTests", "package", cwd=MERGER)

	jars = sorted(
		[p for p in (MERGER / "target").glob("*.jar") if not p.name.startswith("original-")],
		key=lambda p: p.stat().st_mtime,
		reverse=True,
	)
	if not jars:
		raise SystemExit("Could not find built merger jar")
	return jars[0]


def load_inputs(tmp: Path):
	inputs = [GENERATED]

	for i, entry in enumerate(json.loads(CONFIG.read_text(encoding="utf-8"))["packs"]):
		if entry.startswith("vt:"):
			code = entry.removeprefix("vt:")
			zip_path = tmp / f"vt-{i}.zip"
			out_dir = tmp / f"vt-{i}"
			print(f"==> Downloading Vanilla Tweaks share code {code}")
			build_vt_resourcepack_zip(code, zip_path)
			with zipfile.ZipFile(zip_path) as z:
				z.extractall(out_dir)
			inputs.append(out_dir)

		elif entry.startswith(("http://", "https://")):
			zip_path = tmp / f"pack-{i}.zip"
			out_dir = tmp / f"pack-{i}"
			print(f"==> Downloading {entry}")
			with urllib.request.urlopen(entry) as r, zip_path.open("wb") as f:
				shutil.copyfileobj(r, f)
			with zipfile.ZipFile(zip_path) as z:
				z.extractall(out_dir)
			inputs.append(out_dir)

		else:
			inputs.append((HERE / entry).resolve())

	return inputs


def main():
	build_generated()
	jar = build_merger_jar()

	rm(MERGED)
	rm(FINAL_ZIP)
	PACKS.mkdir(parents=True, exist_ok=True)
	WEB_ZIP.parent.mkdir(parents=True, exist_ok=True)

	with tempfile.TemporaryDirectory() as td:
		inputs = load_inputs(Path(td))
		print("==> Merging packs")
		for p in inputs:
			print(" -", p)
		run("java", "-jar", str(jar), *map(str, inputs), str(MERGED))

	print("==> Creating zip archive")
	shutil.make_archive(str(FINAL_ZIP.with_suffix("")), "zip", MERGED)
	shutil.copy2(FINAL_ZIP, WEB_ZIP)

	print("Done:")
	print(" - canonical archive:", FINAL_ZIP)
	print(" - served archive:   ", WEB_ZIP)


if __name__ == "__main__":
	main()