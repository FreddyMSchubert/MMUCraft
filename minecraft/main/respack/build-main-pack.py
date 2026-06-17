import argparse
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
import urllib.error
import zipfile
import hashlib
import re
import uuid
import ssl

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
SERVER_PROPERTIES_TEMPLATE = HERE.parent / "server.properties"
GENERATED_SERVER_PROPERTIES = HERE.parent / "server.properties.generated"
PROJECT_SOUNDS = ROOT / "assets" / "sounds"
GENERAL_PACK_MAINMOD_SOUNDS = PACKS / "general-pack" / "assets" / "mainmod" / "sounds"
CACHE = HERE / ".cache"
PACK_INPUTS_FINGERPRINT = CACHE / "main-pack.inputs.sha256"
PACK_SHA1_CACHE = CACHE / "main-pack.sha1"
REMOTE_PACK_CACHE = CACHE / "remote-packs"
RESOURCE_PACK_ID_NAMESPACE = uuid.UUID("50b0ab3c-3a20-4d7f-ba6a-0a0720f7b50d")

FINGERPRINT_EXCLUDED_DIR_NAMES = {
	".git",
	"dist",
	"node_modules",
	"target",
	"__pycache__",
}
FINGERPRINT_EXCLUDED_PATHS = {
	GENERAL_PACK_MAINMOD_SOUNDS,
}

# Hashing Helpers

def sha1_file(path: Path) -> str:
	h = hashlib.sha1()
	with path.open("rb") as f:
		for chunk in iter(lambda: f.read(1024 * 1024), b""):
			h.update(chunk)
	return h.hexdigest()


def sha256_file(path: Path) -> str:
	h = hashlib.sha256()
	with path.open("rb") as f:
		for chunk in iter(lambda: f.read(1024 * 1024), b""):
			h.update(chunk)
	return h.hexdigest()


def set_property(text: str, key: str, value: str) -> str:
	line = f"{key}={value}"
	pattern = re.compile(rf"(?m)^{re.escape(key)}=.*$")
	if pattern.search(text):
		return pattern.sub(line, text, count=1)
	return text.rstrip("\n") + "\n" + line + "\n"


def write_server_properties(pack_sha1: str):
	text = SERVER_PROPERTIES_TEMPLATE.read_text(encoding="utf-8")
	text = set_property(text, "resource-pack-id", str(uuid.uuid5(RESOURCE_PACK_ID_NAMESPACE, pack_sha1)))
	text = set_property(text, "resource-pack-sha1", pack_sha1)
	GENERATED_SERVER_PROPERTIES.write_text(text, encoding="utf-8", newline="\n")


def write_pack_sha1_cache(pack_sha1: str):
	CACHE.mkdir(parents=True, exist_ok=True)
	PACK_SHA1_CACHE.write_text(pack_sha1 + "\n", encoding="utf-8", newline="\n")


def path_key(path: Path) -> str:
	resolved = path.resolve()
	try:
		return resolved.relative_to(ROOT).as_posix()
	except ValueError:
		return resolved.as_posix()


def is_fingerprint_excluded(path: Path) -> bool:
	resolved = path.resolve()
	if resolved.name in FINGERPRINT_EXCLUDED_DIR_NAMES:
		return True

	for excluded in FINGERPRINT_EXCLUDED_PATHS:
		excluded = excluded.resolve()
		if resolved == excluded:
			return True
		try:
			resolved.relative_to(excluded)
			return True
		except ValueError:
			pass

	return False


def iter_fingerprint_files(path: Path):
	if not path.exists():
		return

	if is_fingerprint_excluded(path):
		return

	if path.is_file():
		yield path
		return

	for child in sorted(path.iterdir(), key=lambda p: p.name):
		yield from iter_fingerprint_files(child)


def add_fingerprint_path(h, path: Path):
	h.update(b"path\0")
	h.update(path_key(path).encode("utf-8"))
	h.update(b"\0")

	if not path.exists():
		h.update(b"missing\0")
		return

	files = list(iter_fingerprint_files(path))
	if path.is_dir() and not files:
		h.update(b"empty-dir\0")

	for file in files:
		h.update(b"file\0")
		h.update(path_key(file).encode("utf-8"))
		h.update(b"\0")
		h.update(sha256_file(file).encode("ascii"))
		h.update(b"\0")


def is_remote_pack_entry(entry: str) -> bool:
	return entry.startswith(("http://", "https://"))


def local_pack_paths(config: dict):
	for entry in config["packs"]:
		if is_remote_pack_entry(entry):
			continue
		yield (HERE / entry).resolve()


def pack_input_fingerprint() -> str:
	config = json.loads(CONFIG.read_text(encoding="utf-8"))
	h = hashlib.sha256()

	for entry in config["packs"]:
		if is_remote_pack_entry(entry):
			h.update(b"remote-pack\0")
			h.update(entry.encode("utf-8"))
			h.update(b"\0")

	for path in [
		HERE / "build-main-pack.py",
		CONFIG,
		ITEMS,
		PROJECT_SOUNDS,
		GENERATOR / "package.json",
		GENERATOR / "package-lock.json",
		GENERATOR / "tsconfig.json",
		GENERATOR / "src",
		GENERATOR / "vanilla_armor_assets",
		MERGER / "pom.xml",
		MERGER / "src",
		*local_pack_paths(config),
	]:
		add_fingerprint_path(h, path)

	return h.hexdigest()


def cached_pack_fingerprint() -> str | None:
	if not PACK_INPUTS_FINGERPRINT.exists():
		return None
	return PACK_INPUTS_FINGERPRINT.read_text(encoding="utf-8").strip()


def write_pack_fingerprint(fingerprint: str):
	CACHE.mkdir(parents=True, exist_ok=True)
	PACK_INPUTS_FINGERPRINT.write_text(fingerprint + "\n", encoding="utf-8", newline="\n")


def pack_outputs_are_reusable(fingerprint: str) -> bool:
	return (
		FINAL_ZIP.exists()
		and cached_pack_fingerprint() == fingerprint
	)


def publish_pack_outputs(pack_sha1: str):
	if not FINAL_ZIP.exists():
		raise RuntimeError(f"Cannot publish missing resource pack archive: {FINAL_ZIP}")

	WEB_ZIP.parent.mkdir(parents=True, exist_ok=True)

	if WEB_ZIP.exists() and sha1_file(WEB_ZIP) == pack_sha1:
		print("==> Website zip already matches resource pack SHA1")
	else:
		print("==> Publishing zip for the website")
		shutil.copy2(FINAL_ZIP, WEB_ZIP)

	print(f"==> Computed resource-pack-sha1: {pack_sha1}")
	write_server_properties(pack_sha1)
	write_pack_sha1_cache(pack_sha1)

# General Stuff

def run(*cmd, cwd=None):
	cmd = [str(part) for part in cmd]

	if os.name == "nt":
		if cmd[0] == "npm":
			cmd[0] = "npm.cmd"
		elif cmd[0] == "mvn":
			cmd[0] = "mvn.cmd"

	proc = subprocess.Popen(
		cmd,
		cwd=cwd,
		stdout=subprocess.PIPE,
		stderr=subprocess.STDOUT,
		text=True,
		bufsize=1,
	)

	try:
		for line in proc.stdout:
			if "Copying File " in line or "Copying Directory " in line:
				continue
			print(line, end="")
	finally:
		proc.stdout.close()

	ret = proc.wait()
	if ret != 0:
		raise subprocess.CalledProcessError(ret, cmd)


def rm(path: Path):
	if path.is_dir():
		shutil.rmtree(path, ignore_errors=True)
	elif path.exists():
		path.unlink()


def remote_pack_cache_path(url: str) -> Path:
	return REMOTE_PACK_CACHE / f"{hashlib.sha256(url.encode('utf-8')).hexdigest()}.zip"


def is_ssl_cert_verification_error(error: urllib.error.URLError) -> bool:
	reason = getattr(error, "reason", error)
	while reason is not None:
		if isinstance(reason, ssl.SSLCertVerificationError):
			return True
		reason = getattr(reason, "__cause__", None)
	return False


def download_remote_pack(url: str, zip_path: Path):
	cache_path = remote_pack_cache_path(url)

	def download(context=None):
		with urllib.request.urlopen(url, context=context) as r, zip_path.open("wb") as f:
			shutil.copyfileobj(r, f)

	try:
		download()
	except urllib.error.HTTPError:
		raise
	except urllib.error.URLError as e:
		if cache_path.exists():
			print(f"==> Download failed; using cached remote pack: {cache_path}")
			shutil.copy2(cache_path, zip_path)
			return

		if not is_ssl_cert_verification_error(e):
			raise

		print("WARNING: TLS certificate verification failed; retrying configured pack URL without verification")
		download(ssl._create_unverified_context())

	REMOTE_PACK_CACHE.mkdir(parents=True, exist_ok=True)
	shutil.copy2(zip_path, cache_path)


def build_generated():
	print("==> Generating resource pack from item definitions")
	if not (GENERATOR / "node_modules").exists():
		run("npm", "ci", cwd=GENERATOR)

	rm(GENERATED)

	run(
		"npm",
		"run",
		"generate",
		"--",
		"--source",
		str(ITEMS),
		"--vanilla-armor",
		str(GENERATOR / "vanilla_armor_assets"),
		"--output",
		str(GENERATED),
		cwd=GENERATOR,
	)


def build_merger_jar():
	print("==> Building ResourcePackMerger")

	mvnw = MERGER / "mvnw"
	mvnw_cmd = MERGER / "mvnw.cmd"

	if os.name == "nt" and mvnw_cmd.exists():
		run(str(mvnw_cmd), "-q", "-DskipTests", "package", cwd=MERGER)
	elif mvnw.exists():
		if os.access(mvnw, os.X_OK):
			run(str(mvnw), "-q", "-DskipTests", "package", cwd=MERGER)
		else:
			run("sh", str(mvnw), "-q", "-DskipTests", "package", cwd=MERGER)
	else:
		mvn = shutil.which("mvn")
		if not mvn:
			raise SystemExit(
				"Could not build ResourcePackMerger: neither ./mvnw nor mvn is available."
			)
		run(mvn, "-q", "-DskipTests", "package", cwd=MERGER)

	jars = sorted(
		[
			p
			for p in (MERGER / "target").glob("*.jar")
			if not p.name.startswith("original-")
		],
		key=lambda p: p.stat().st_mtime,
		reverse=True,
	)
	if not jars:
		raise SystemExit("Could not find built merger jar")
	return jars[0]


def load_inputs(tmp: Path):
	inputs = [GENERATED]

	config = json.loads(CONFIG.read_text(encoding="utf-8"))
	for i, entry in enumerate(config["packs"]):
		if is_remote_pack_entry(entry):
			zip_path = tmp / f"pack-{i}.zip"
			out_dir = tmp / f"pack-{i}"
			print(f"==> Downloading {entry}")
			download_remote_pack(entry, zip_path)
			with zipfile.ZipFile(zip_path) as z:
				z.extractall(out_dir)
			inputs.append(out_dir)

		else:
			inputs.append((HERE / entry).resolve())

	return inputs

def sync_project_sounds_into_general_pack():
	print("==> Syncing project sounds into general-pack")

	if not PROJECT_SOUNDS.exists():
			print(" - no project sounds folder found at", PROJECT_SOUNDS)
			return

	GENERAL_PACK_MAINMOD_SOUNDS.mkdir(parents=True, exist_ok=True)

	expected = set()
	for src in PROJECT_SOUNDS.rglob("*"):
			if src.is_dir():
					continue

			rel = src.relative_to(PROJECT_SOUNDS)
			expected.add(rel)
			dst = GENERAL_PACK_MAINMOD_SOUNDS / rel
			dst.parent.mkdir(parents=True, exist_ok=True)
			if not dst.exists() or sha1_file(src) != sha1_file(dst):
					shutil.copy2(src, dst)

	for dst in sorted((p for p in GENERAL_PACK_MAINMOD_SOUNDS.rglob("*") if p.is_file()), reverse=True):
			rel = dst.relative_to(GENERAL_PACK_MAINMOD_SOUNDS)
			if rel not in expected:
					dst.unlink()

	for directory in sorted((p for p in GENERAL_PACK_MAINMOD_SOUNDS.rglob("*") if p.is_dir()), reverse=True):
			if not any(directory.iterdir()):
					directory.rmdir()


def main():
	parser = argparse.ArgumentParser()
	parser.add_argument("--force", action="store_true", help="Rebuild the resource pack even when inputs are unchanged.")
	args = parser.parse_args()

	fingerprint = pack_input_fingerprint()
	if not args.force and pack_outputs_are_reusable(fingerprint):
		print("==> Resource pack inputs unchanged; reusing existing archive")
		pack_sha1 = sha1_file(FINAL_ZIP)
		publish_pack_outputs(pack_sha1)
		print("Done:")
		print(" - canonical archive:", FINAL_ZIP)
		print(" - served archive:   ", WEB_ZIP)
		print(" - server settings:  ", GENERATED_SERVER_PROPERTIES)
		print(" - pack input hash:  ", PACK_INPUTS_FINGERPRINT)
		return

	build_generated()
	sync_project_sounds_into_general_pack()
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

	pack_sha1 = sha1_file(FINAL_ZIP)
	publish_pack_outputs(pack_sha1)
	write_pack_fingerprint(fingerprint)

	print("Done:")
	print(" - canonical archive:", FINAL_ZIP)
	print(" - served archive:   ", WEB_ZIP)
	print(" - server settings:  ", GENERATED_SERVER_PROPERTIES)
	print(" - pack input hash:  ", PACK_INPUTS_FINGERPRINT)


if __name__ == "__main__":
	main()
	
