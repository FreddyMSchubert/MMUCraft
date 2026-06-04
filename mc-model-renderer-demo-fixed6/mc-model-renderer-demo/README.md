# Minecraft Model Renderer Demo

A Three.js demo for rendering explicit Java-style Minecraft model JSON on the web.

## What this build does

- Uses one texture for the whole model.
- Supports tintindex-based color multiplication.
- Supports square textures at 16/32/64+ resolutions with correct Minecraft-style UV scaling.
- Supports vertical strip animation for non-square textures.
- Supports zero-thickness planes and display transforms.
- Auto-rotates by default.

## Run locally

```bash
python3 -m http.server 4173
```

Then open `http://localhost:4173`.

## Notes

- Upload one JSON model and one texture image.
- If the texture is taller than it is wide, it is treated as a vertical animation strip of square frames.
- The hue slider controls tintindex `0` and defaults to pure red.
