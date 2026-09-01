# Dabloon symbol

This project uses **U+F0DAB** for the dabloon sign.

Do not paste the private-use character into source code or documentation. Most fonts do not contain it, so editors and Markdown renderers show a missing-glyph box. Use these ASCII-only representations instead:

| Context | Representation |
| --- | --- |
| Code point | `U+F0DAB` or `0xF0DAB` |
| Java | `Character.toString(0xF0DAB)` |
| TypeScript/JavaScript | `String.fromCodePoint(0xF0DAB)` or `\u{F0DAB}` |
| JSON/Minecraft command | `\udb83\uddab` |
| CSS `unicode-range` | `U+F0DAB` |

U+F0DAB was checked on 1 September 2026 against Icons 1.13.4 and every cached third-party pack used by `main-pack.config.json`; none maps it. Private-use code points have no global registry, so this cannot guarantee that an arbitrary future pack will never reuse it. If that happens, that one icon will depend on resource-pack priority.

## Minecraft artwork

The Minecraft source image is:

`minecraft/main/respack/packs/general-pack/assets/general-pack/textures/font/dabloon.png`

Its current format is:

- 5 pixels wide by 9 pixels high;
- PNG with transparency;
- a white glyph on a transparent background;
- hard pixel edges, with no antialiasing or partially transparent pixels.

The bitmap is intentionally not padded or scaled down. Its rows are:

```text
..#..
####.
#.#.#
#.#.#
#.#.#
#.#.#
#.#.#
####.
..#..
```

The ordinary Minecraft UI `D` is seven pixels high. Rows 2 through 8 contain the seven-pixel `D`; rows 1 and 9 contain only the extending line. Minecraft and the webfont generator both preserve that native pixel size, so the `D` body matches an ordinary `D` and only the line extends above and below it.

Transparent padding is not required. Minecraft calculates a bitmap glyph's width from its rightmost non-transparent column and supplies normal character spacing. The font definition renders this image with `height: 9` and `ascent: 8`, preserving all nine rows.

Minecraft permits other rectangular dimensions, up to 256×256 for an individual bitmap glyph. If the artwork dimensions change, review `height` and `ascent` in the font definition rather than padding the PNG just to make it square.

## Minecraft default-font integration

The provider is defined in:

`minecraft/main/respack/packs/general-pack/assets/minecraft/font/default.json`

It extends `minecraft:default`, so there is no custom `font` property to set on text components. Once the server resource pack is loaded, outputting U+F0DAB is enough.

Use the shared formatter in Java:

```java
Component amount = MoneyHelper.FormatDabloons(1250);
```

The implementation constructs the character from its numeric code point, so no private-use glyph needs to be copied into the source:

```java
public static final int DABLOON_CODEPOINT = 0xF0DAB;
private static final String DABLOON_SYMBOL = Character.toString(DABLOON_CODEPOINT);
```

For a manual in-game test, JSON uses the UTF-16 surrogate-pair escape:

```text
/tellraw @s {"text":"\udb83\uddab1,250","color":"gold"}
```

No `"font":"..."` field is needed. Java Edition does not automatically expand a shortcode such as `:dabloon:` in player chat; implementing chat shortcodes would be a separate server feature. Application code should call the formatter rather than make people copy the character.

After changing the PNG or font JSON, rebuild the normal pack:

```powershell
python minecraft\main\respack\build-main-pack.py --force
```

Players must load the rebuilt server pack. Without it, U+F0DAB correctly appears as a missing glyph.

## Website artwork

The website has three glyph variants because its existing fonts have different visual styles:

1. **Minecraft UI:** generated directly from the 5×9 Minecraft PNG. Each bitmap pixel maps to one native Minecraft UI pixel. The seven-row `D` stays the same size as the existing `D`; the two extra line pixels overhang it.
2. **Noto Sans:** built from the authored Noto-style SVG. Its base `D` was aligned exactly to the existing Noto Sans `D` bounds and metrics before the line was added. The overhanging line therefore does not shrink the `D`.
3. **JetBrains Mono:** built from the separately authored JetBrains-style SVG for Knowledge and every other JetBrains Mono context. It uses JetBrains Mono's 600-unit character cell and original `D` bounds, so it remains monospaced. The line is allowed to overhang without shrinking the `D`.

The generated one-glyph fonts are:

- `services/web/public/assets/fonts/dabloon-symbol/dabloon-symbol-minecraft.woff2`
- `services/web/public/assets/fonts/dabloon-symbol/dabloon-symbol-noto-sans.woff2`
- `services/web/public/assets/fonts/dabloon-symbol/dabloon-symbol-jetbrains-mono.woff2`

Each font maps only U+F0DAB. The matching symbol family can be placed before Minecraft UI, Noto Sans, or JetBrains Mono; every other code point then falls through to the existing font.

The site also serves regular and bold JetBrainsMono Nerd Font WOFF2 files from `services/web/public/assets/fonts/jetbrains-mono-nerd/`, so Knowledge no longer depends on fonts installed on the visitor's computer. These files are Nerd Fonts v3.5.1 and their `OFL.txt` is stored alongside them.

The SVG conversion was a one-time build step. Only the finished WOFF2 files are required at runtime; there is no permanent font generator or Python environment in the repository.

## Website usage

The initial rollout is intentionally limited to the shop flow. `.playPage` uses the Noto Sans symbol fallback, and shop prices and purchase prompts call the shared formatter. The Minecraft UI and JetBrains Mono symbol faces are built and registered but are not yet added to unrelated page font stacks.

Use the shared formatter:

```tsx
import { formatDabloons } from '@/lib/dabloons';

<span aria-label="1,250 dabloons">{formatDabloons(1250)}</span>
```

`services/web/src/lib/dabloons.ts` constructs U+F0DAB with `String.fromCodePoint`, so TypeScript source and documentation remain readable on machines that do not have the custom font installed.

## Verification

1. Load a shop price and purchase prompt in the web dashboard.
2. Rebuild the resource pack.
3. Run the escaped `/tellraw` command above or trigger the server balance message.
4. Confirm that ordinary letters and digits still use their original fonts in both applications.
