#!/usr/bin/env python3
"""
Generate “artifact/armor” equipment assets for a Minecraft resource pack.

Inputs (relative to --in):
  basic/humanoid/*.png
  basic/leggings/*.png
  charms/humanoid/*.png
  charms/humanoid_leggings/*.png

Outputs (relative to --out):
  assets/<namespace>/textures/entity/equipment/humanoid/*.png
  assets/<namespace>/textures/entity/equipment/humanoid_leggings/*.png
  assets/<namespace>/models/equipment/*.json

Rules:
- For every base armor texture (material) and every charm overlay, create a combined texture.
- For leather:
  - Keep the base leather texture copied unchanged for every charm variant.
  - Apply the charm overlay onto the leather *_overlay.png* (per your instruction).
  - Equipment model JSON uses two layers (base + overlay).
- For non-leather:
  - Bake the charm directly into the base texture (single layer).
  - Equipment model JSON uses one layer.

Naming:
- Output textures are named: <material>__<charm>.png
- Leather overlay variants are: leather_overlay__<charm>.png (and leggings equivalents if present)
- Equipment model JSON: <charm>__<material>.json
  => asset_id to use in equippable is "<namespace>:<charm>__<material>"

Requires: Pillow  (pip install pillow)
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from PIL import Image


@dataclass(frozen=True)
class TextureSet:
	# texture ids (namespace:name) used in equipment JSON
	humanoid_layers: List[str]
	leggings_layers: List[str]


def _slug(name: str) -> str:
	# Minecraft-ish safe id: lowercase, digits, _-. and /
	name = name.lower()
	name = re.sub(r"[^a-z0-9_\-./]+", "_", name)
	name = re.sub(r"_+", "_", name).strip("_")
	return name


def _load_png(path: Path) -> Image.Image:
	im = Image.open(path)
	if im.mode != "RGBA":
		im = im.convert("RGBA")
	return im


def _alpha_composite(base: Image.Image, overlay: Image.Image) -> Image.Image:
	if base.size != overlay.size:
		raise ValueError(f"Size mismatch: base={base.size} overlay={overlay.size}")
	out = base.copy()
	out.alpha_composite(overlay)
	return out


def _ensure_dir(p: Path) -> None:
	p.mkdir(parents=True, exist_ok=True)


def _write_json(path: Path, data: dict) -> None:
	_ensure_dir(path.parent)
	path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _list_pngs(folder: Path) -> Dict[str, Path]:
	if not folder.exists():
		return {}
	out: Dict[str, Path] = {}
	for p in sorted(folder.glob("*.png")):
		out[p.stem] = p
	return out


def _detect_leather_keys(keys: List[str]) -> Tuple[Optional[str], Optional[str]]:
	"""
	Try to find leather base and leather overlay texture stems in a folder listing.
	Returns (base_stem, overlay_stem).
	Typical stems you might use:
	  leather, leather_overlay
	  leather_layer_1, leather_layer_1_overlay
	  etc.
	"""
	# Overlay candidates first
	overlay = None
	base = None

	# Prefer explicit "leather_overlay"
	if "leather_overlay" in keys:
		overlay = "leather_overlay"
	else:
		# any stem containing leather and overlay
		for k in keys:
			if "leather" in k and "overlay" in k:
				overlay = k
				break

	# Base: prefer "leather" or leather-ish without overlay
	if "leather" in keys:
		base = "leather"
	else:
		for k in keys:
			if "leather" in k and "overlay" not in k:
				base = k
				break

	return base, overlay


def build_equipment_model_json(
	namespace: str,
	humanoid_layers: List[str],
	leggings_layers: List[str],
	*,
	is_leather: bool = False,
	leather_color_when_undyed: int = -6265536,
) -> dict:
	"""
	Build an equipment model JSON.
	- Non-leather: single (or multi) texture layers as plain {"texture": "..."}
	- Leather: first layer is dyeable, second is overlay, and also include horse_body.
	"""
	if not is_leather:
		return {
			"layers": {
				"humanoid": [{"texture": t} for t in humanoid_layers],
				"humanoid_leggings": [{"texture": t} for t in leggings_layers],
			}
		}

	# Leather expects: base dyeable + overlay (and horse_body too)
	# We assume humanoid_layers/leggings_layers are [base, overlay]
	if len(humanoid_layers) != 2 or len(leggings_layers) != 2:
		raise ValueError("Leather models must provide exactly 2 layers: [base, overlay]")

	base_h, overlay_h = humanoid_layers
	base_l, overlay_l = leggings_layers

	def dyeable_layer(tex: str) -> dict:
		return {
			"dyeable": {"color_when_undyed": leather_color_when_undyed},
			"texture": tex,
		}

	def plain_layer(tex: str) -> dict:
		return {"texture": tex}

	return {
		"layers": {
			"horse_body": [
				dyeable_layer(base_h),
				plain_layer(overlay_h),
			],
			"humanoid": [
				dyeable_layer(base_h),
				plain_layer(overlay_h),
			],
			"humanoid_leggings": [
				dyeable_layer(base_l),
				plain_layer(overlay_l),
			],
		}
	}

def main() -> None:
	ap = argparse.ArgumentParser()
	ap.add_argument("--in", dest="inp", required=True, help="Input root folder")
	ap.add_argument("--out", dest="out", required=True, help="Output resource pack root")
	ap.add_argument("--namespace", default="yourpack", help="Namespace under assets/")
	ap.add_argument(
		"--overwrite",
		action="store_true",
		help="Allow overwriting output files",
	)
	args = ap.parse_args()

	inp = Path(args.inp).resolve()
	out = Path(args.out).resolve()
	ns = _slug(args.namespace)

	# Input folders
	basic_hum = inp / "basic" / "humanoid"
	basic_leg = inp / "basic" / "leggings"
	charm_hum = inp / "charms" / "humanoid"
	charm_leg = inp / "charms" / "humanoid_leggings"

	base_hum = _list_pngs(basic_hum)
	base_leg = _list_pngs(basic_leg)
	charms_h = _list_pngs(charm_hum)
	charms_l = _list_pngs(charm_leg)

	if not base_hum or not base_leg:
		raise SystemExit("Missing basic textures. Need basic/humanoid/*.png and basic/leggings/*.png")
	if not charms_h or not charms_l:
		raise SystemExit("Missing charm textures. Need charms/humanoid/*.png and charms/humanoid_leggings/*.png")

	# Output folders (new equipment texture locations)
	out_tex_h = out / "assets" / ns / "textures" / "entity" / "equipment" / "humanoid"
	out_tex_l = out / "assets" / ns / "textures" / "entity" / "equipment" / "humanoid_leggings"
	out_models = out / "assets" / ns / "equipment"

	_ensure_dir(out_tex_h)
	_ensure_dir(out_tex_l)
	_ensure_dir(out_models)

	base_h_keys = list(base_hum.keys())
	base_l_keys = list(base_leg.keys())

	leather_h_base, leather_h_overlay = _detect_leather_keys(base_h_keys)
	leather_l_base, leather_l_overlay = _detect_leather_keys(base_l_keys)

	# Preload charm images (enforce size match on use)
	charm_imgs_h = {k: _load_png(p) for k, p in charms_h.items()}
	charm_imgs_l = {k: _load_png(p) for k, p in charms_l.items()}

	# Helper to write PNG
	def save_png(img: Image.Image, path: Path) -> None:
		if path.exists() and not args.overwrite:
			raise SystemExit(f"Refusing to overwrite existing file: {path} (use --overwrite)")
		_ensure_dir(path.parent)
		img.save(path, format="PNG")

	# Track model definitions to write
	models_to_write: Dict[Tuple[str, str], TextureSet] = {}

	# Copies each charm overlay into the equipment texture folders as-is,
	# and creates an equipment model JSON that references only the charm layer.
	for charm_stem in charms_h.keys():
		if charm_stem not in charms_l:
			continue  # require leggings variant too

		ch = _slug(charm_stem)

		# Save charm textures exactly once, unchanged, into the equipment texture folders.
		# Names are just "<charm>.png" to keep them clean.
		save_png(charm_imgs_h[charm_stem], out_tex_h / f"{ch}.png")
		save_png(charm_imgs_l[charm_stem], out_tex_l / f"{ch}.png")

		# Create an equipment model entry for this charm-only asset.
		# asset_id will be: <namespace>:<charm>__charm
		models_to_write[(ch, "charm")] = TextureSet(
			humanoid_layers=[f"{ns}:{ch}"],
			leggings_layers=[f"{ns}:{ch}"],
		)

	# Generate per-material, per-charm textures
	for material_stem, material_path in base_hum.items():
		mat = _slug(material_stem)

		# Skip leather overlay stem during "material list" pass;
		# leather is handled using base+overlay pairing instead.
		if leather_h_overlay and material_stem == leather_h_overlay:
			continue

		# Must have a leggings base texture with the same material stem,
		# except leather where stems may differ.
		if material_stem not in base_leg and not ("leather" in material_stem):
			# If you want stricter behavior, change this to raise.
			continue

		base_img_h = _load_png(material_path)

		# Pick the matching leggings base stem.
		leg_stem = material_stem if material_stem in base_leg else None
		base_img_l = _load_png(base_leg[leg_stem]) if leg_stem else None

		is_leather = ("leather" in material_stem) or (leather_h_base and material_stem == leather_h_base)

		for charm_stem in charms_h.keys():
			ch = _slug(charm_stem)

			# Require matching charm overlay for leggings, too
			if charm_stem not in charms_l:
				continue

			overlay_h = charm_imgs_h[charm_stem]
			overlay_l = charm_imgs_l[charm_stem]

			# Output texture ids (namespace:name)
			texid_h = f"{ns}:{mat}__{ch}"
			texid_l = f"{ns}:{mat}__{ch}"

			if not is_leather:
				# Bake overlay into base for humanoid and leggings
				out_img_h = _alpha_composite(base_img_h, overlay_h)
				if base_img_l is None:
					continue
				out_img_l = _alpha_composite(base_img_l, overlay_l)

				save_png(out_img_h, out_tex_h / f"{mat}__{ch}.png")
				save_png(out_img_l, out_tex_l / f"{mat}__{ch}.png")

				models_to_write[(ch, mat)] = TextureSet(
					humanoid_layers=[texid_h],
					leggings_layers=[texid_l],
				)

			else:
				# Leather special:
				# - copy base leather unchanged per charm variant
				# - apply overlay onto leather overlay texture per charm variant
				# Determine leather stems in each folder
				if not leather_h_base or not leather_h_overlay or not leather_l_base or not leather_l_overlay:
					raise SystemExit(
						"Leather handling requested, but could not detect leather base+overlay files.\n"
						"Make sure basic/humanoid has leather + leather_overlay (or similar),\n"
						"and basic/leggings has leather + leather_overlay (or similar)."
					)

				# Use canonical leather stems for the output "material" name
				# (so you get asset_id like charm__leather)
				leather_mat = "leather"

				leather_base_h = _load_png(base_hum[leather_h_base])
				leather_base_l = _load_png(base_leg[leather_l_base])

				leather_ov_h = _load_png(base_hum[leather_h_overlay])
				leather_ov_l = _load_png(base_leg[leather_l_overlay])

				# Copy base
				save_png(leather_base_h, out_tex_h / f"{leather_mat}__{ch}.png")
				save_png(leather_base_l, out_tex_l / f"{leather_mat}__{ch}.png")

				# Compose overlay (your charm goes onto the leather overlay texture)
				out_leather_ov_h = _alpha_composite(leather_ov_h, overlay_h)
				out_leather_ov_l = _alpha_composite(leather_ov_l, overlay_l)

				save_png(out_leather_ov_h, out_tex_h / f"{leather_mat}_overlay__{ch}.png")
				save_png(out_leather_ov_l, out_tex_l / f"{leather_mat}_overlay__{ch}.png")

				models_to_write[(ch, leather_mat)] = TextureSet(
					humanoid_layers=[
						f"{ns}:{leather_mat}__{ch}",
						f"{ns}:{leather_mat}_overlay__{ch}",
					],
					leggings_layers=[
						f"{ns}:{leather_mat}__{ch}",
						f"{ns}:{leather_mat}_overlay__{ch}",
					],
				)

	# Write equipment model JSONs
	written = 0
	for (ch, mat), texset in sorted(models_to_write.items()):
		model_name = f"{ch}__{mat}"
		model_path = out_models / f"{model_name}.json"

		is_leather = (mat == "leather")
		data = build_equipment_model_json(
			namespace=ns,
			humanoid_layers=texset.humanoid_layers,
			leggings_layers=texset.leggings_layers,
			is_leather=is_leather,
			leather_color_when_undyed=-6265536,
		)
		if model_path.exists() and not args.overwrite:
			raise SystemExit(f"Refusing to overwrite existing file: {model_path} (use --overwrite)")
		_write_json(model_path, data)
		written += 1

	print(f"Done. Wrote {written} equipment model JSON files into {out_models}")
	print(f"Example asset_id to use: {ns}:{next(iter(models_to_write.keys()))[0]}__{next(iter(models_to_write.keys()))[1]}")


if __name__ == "__main__":
	main()
