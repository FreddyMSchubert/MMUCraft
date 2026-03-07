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
  assets/<namespace>/equipment/*.json

Rules:
- For every base armor texture (material) and every charm overlay, create a combined texture.
- Charm textures may be integer-upscaled versions of the base resolution
  (for example 128x64 instead of 64x32). In that case, the other texture
  is upscaled with nearest-neighbor before compositing.
- For leather:
  - Keep the base leather texture copied unchanged for every charm variant,
	except it may be upscaled to match the charm resolution.
  - Apply the charm overlay onto the leather *_overlay.png*.
  - Equipment model JSON uses two layers (base + overlay).
- For non-leather:
  - Bake the charm directly into the base texture (single layer).
  - Equipment model JSON uses one layer.

Naming:
- Output textures are named: <material>__<charm>.png
- Leather overlay variants are: leather_overlay__<charm>.png
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
	humanoid_layers: List[str]
	leggings_layers: List[str]


def _slug(name: str) -> str:
	name = name.lower()
	name = re.sub(r"[^a-z0-9_\-./]+", "_", name)
	name = re.sub(r"_+", "_", name).strip("_")
	return name


def _load_png(path: Path) -> Image.Image:
	im = Image.open(path)
	if im.mode != "RGBA":
		im = im.convert("RGBA")
	return im


def _integer_scale_factor(smaller: Tuple[int, int], larger: Tuple[int, int]) -> Optional[int]:
	sw, sh = smaller
	lw, lh = larger

	if sw <= 0 or sh <= 0:
		return None
	if lw % sw != 0 or lh % sh != 0:
		return None

	fx = lw // sw
	fy = lh // sh

	if fx != fy or fx < 1:
		return None
	return fx


def _common_pixel_art_size(*sizes: Tuple[int, int]) -> Tuple[int, int]:
	"""
	Return the largest size among `sizes` when every smaller size is an
	integer upscale factor of the largest one.
	"""
	if not sizes:
		raise ValueError("Need at least one size")

	target = sizes[0]
	for size in sizes[1:]:
		if size == target:
			continue

		if _integer_scale_factor(target, size) is not None:
			target = size
			continue

		if _integer_scale_factor(size, target) is not None:
			continue

		raise ValueError(
			f"Incompatible pixel-art sizes: {', '.join(f'{w}x{h}' for w, h in sizes)}"
		)

	return target


def _upscale_to_size(im: Image.Image, target_size: Tuple[int, int], *, label: str) -> Image.Image:
	if im.size == target_size:
		return im

	factor = _integer_scale_factor(im.size, target_size)
	if factor is None:
		raise ValueError(
			f"Cannot upscale {label} from {im.size} to {target_size}. "
			"Sizes must match exactly or by an integer multiple."
		)

	return im.resize(target_size, resample=Image.NEAREST)


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
	"""
	overlay = None
	base = None

	if "leather_overlay" in keys:
		overlay = "leather_overlay"
	else:
		for k in keys:
			if "leather" in k and "overlay" in k:
				overlay = k
				break

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
	if not is_leather:
		return {
			"layers": {
				"humanoid": [{"texture": t} for t in humanoid_layers],
				"humanoid_leggings": [{"texture": t} for t in leggings_layers],
			}
		}

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

	charm_imgs_h = {k: _load_png(p) for k, p in charms_h.items()}
	charm_imgs_l = {k: _load_png(p) for k, p in charms_l.items()}

	def save_png(img: Image.Image, path: Path) -> None:
		if path.exists() and not args.overwrite:
			raise SystemExit(f"Refusing to overwrite existing file: {path} (use --overwrite)")
		_ensure_dir(path.parent)
		img.save(path, format="PNG")

	models_to_write: Dict[Tuple[str, str], TextureSet] = {}

	for charm_stem in charms_h.keys():
		if charm_stem not in charms_l:
			continue

		ch = _slug(charm_stem)

		save_png(charm_imgs_h[charm_stem], out_tex_h / f"{ch}.png")
		save_png(charm_imgs_l[charm_stem], out_tex_l / f"{ch}.png")

		models_to_write[(ch, "charm")] = TextureSet(
			humanoid_layers=[f"{ns}:{ch}"],
			leggings_layers=[f"{ns}:{ch}"],
		)

	for material_stem, material_path in base_hum.items():
		mat = _slug(material_stem)

		if leather_h_overlay and material_stem == leather_h_overlay:
			continue

		if material_stem not in base_leg and not ("leather" in material_stem):
			continue

		base_img_h = _load_png(material_path)

		leg_stem = material_stem if material_stem in base_leg else None
		base_img_l = _load_png(base_leg[leg_stem]) if leg_stem else None

		is_leather = ("leather" in material_stem) or (leather_h_base and material_stem == leather_h_base)

		for charm_stem in charms_h.keys():
			if charm_stem not in charms_l:
				continue

			ch = _slug(charm_stem)
			overlay_h = charm_imgs_h[charm_stem]
			overlay_l = charm_imgs_l[charm_stem]

			texid_h = f"{ns}:{mat}__{ch}"
			texid_l = f"{ns}:{mat}__{ch}"

			if not is_leather:
				if base_img_l is None:
					continue

				target_h = _common_pixel_art_size(base_img_h.size, overlay_h.size)
				target_l = _common_pixel_art_size(base_img_l.size, overlay_l.size)

				scaled_base_h = _upscale_to_size(base_img_h, target_h, label=f"{material_stem} humanoid base")
				scaled_overlay_h = _upscale_to_size(overlay_h, target_h, label=f"{charm_stem} humanoid charm")

				scaled_base_l = _upscale_to_size(base_img_l, target_l, label=f"{material_stem} leggings base")
				scaled_overlay_l = _upscale_to_size(overlay_l, target_l, label=f"{charm_stem} leggings charm")

				out_img_h = _alpha_composite(scaled_base_h, scaled_overlay_h)
				out_img_l = _alpha_composite(scaled_base_l, scaled_overlay_l)

				save_png(out_img_h, out_tex_h / f"{mat}__{ch}.png")
				save_png(out_img_l, out_tex_l / f"{mat}__{ch}.png")

				models_to_write[(ch, mat)] = TextureSet(
					humanoid_layers=[texid_h],
					leggings_layers=[texid_l],
				)

			else:
				if not leather_h_base or not leather_h_overlay or not leather_l_base or not leather_l_overlay:
					raise SystemExit(
						"Leather handling requested, but could not detect leather base+overlay files.\n"
						"Make sure basic/humanoid has leather + leather_overlay (or similar),\n"
						"and basic/leggings has leather + leather_overlay (or similar)."
					)

				leather_mat = "leather"

				leather_base_h = _load_png(base_hum[leather_h_base])
				leather_base_l = _load_png(base_leg[leather_l_base])
				leather_ov_h = _load_png(base_hum[leather_h_overlay])
				leather_ov_l = _load_png(base_leg[leather_l_overlay])

				target_h = _common_pixel_art_size(
					leather_base_h.size,
					leather_ov_h.size,
					overlay_h.size,
				)
				target_l = _common_pixel_art_size(
					leather_base_l.size,
					leather_ov_l.size,
					overlay_l.size,
				)

				scaled_leather_base_h = _upscale_to_size(
					leather_base_h, target_h, label="leather humanoid base"
				)
				scaled_leather_base_l = _upscale_to_size(
					leather_base_l, target_l, label="leather leggings base"
				)
				scaled_leather_ov_h = _upscale_to_size(
					leather_ov_h, target_h, label="leather humanoid overlay"
				)
				scaled_leather_ov_l = _upscale_to_size(
					leather_ov_l, target_l, label="leather leggings overlay"
				)
				scaled_overlay_h = _upscale_to_size(
					overlay_h, target_h, label=f"{charm_stem} humanoid charm"
				)
				scaled_overlay_l = _upscale_to_size(
					overlay_l, target_l, label=f"{charm_stem} leggings charm"
				)

				save_png(scaled_leather_base_h, out_tex_h / f"{leather_mat}__{ch}.png")
				save_png(scaled_leather_base_l, out_tex_l / f"{leather_mat}__{ch}.png")

				out_leather_ov_h = _alpha_composite(scaled_leather_ov_h, scaled_overlay_h)
				out_leather_ov_l = _alpha_composite(scaled_leather_ov_l, scaled_overlay_l)

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
	first_key = next(iter(models_to_write.keys()))
	print(f"Example asset_id to use: {ns}:{first_key[0]}__{first_key[1]}")


if __name__ == "__main__":
	main()