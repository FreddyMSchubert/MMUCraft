# MMU Resource Pack Generator

Generates the resource-pack side of the composable fake-item system.

## Emissive model elements

Set `light_emission` on each exported model element that should remain visible in darkness:
`0` (or absent) uses normal lighting, `1`–`14` sets an intermediate minimum, and `15` is fully
bright. Blockbench groups must export this property on their constituent elements.
Use `shade_direction_override` for fixed directional shading. It does not set emission.

The generator writes OptiFine `_e.png` overlays beside generated textures using the UVs
of elements with positive `light_emission`. Unselected pixels are transparent; selected
pixels retain the source color and alpha, with reduced RGB for intermediate emission.
Implicit, reversed, and rotated UVs and animation frames are supported. UV coordinates
use Minecraft's 0–16 space, regardless of the atlas resolution or `texture_size` metadata.

A source companion such as `model_e.png` or `texture_e.png` takes precedence and is copied
unchanged. Its own `.png.mcmeta` takes precedence over the base texture's animation metadata.
This also works for manually authored item and charm equipment overlays. The generated
pack includes `assets/minecraft/optifine/emissive.properties` with `suffix.emissive=_e`.
Source files are never written; source/output directory overlap is rejected.

OptiFine overlays operate on texture pixels: if ordinary and emissive faces share the same
UV region, both sample the emissive pixels. Use separate UV regions for exact separation.
Intermediate emission is an approximation in the overlay; shader-pack bloom and lighting
remain controlled by the shader pack. Emissive surfaces do not cast light onto nearby blocks.

The shop preview uses `light_emission` directly, never `_e` images or `shade_direction_override`. Its day/night
button appears for emissive models and preserves the model rotation. It uses a Bright-style
night lightmap approximation and retains each element's emission as a minimum brightness.
Block previews use static Trails & Tales and Chaos Cubed panoramas for day and night respectively.

Run generator regression tests with `npm test` in this directory.

Each item lives in its own leaf directory and must contain an `item.json` that follows the
new schema shape:

```json
{
  "title": "Example item",
  "id": "example-item",
  "modelType": "basic",
  "rarity": "common",
  "maxStackSize": 64,
  "tooltips": []
}
```

The generator uses the item `id` as the selector value written into the generated
`heart_of_the_sea` / `carved_pumpkin` item definitions. It also writes the
`command_block` selector for older stacks until the server migrates them.

Item definitions may include gameplay metadata such as `shopPurchasable`. The generator
validates that known metadata shape and uses `shopPurchasable.unlockWeight` for the
post-generation cosmetic weight report, but does not write it into resource-pack assets.

Item-producing recipes belong in `craftable.recipes` in the resulting item's `item.json`.
Each recipe has an `id` (its path under the `mainmod:recipe` namespace) and the usual
recipe fields. The recipe types and their required fields are defined in
`data/validation/schemas/item/components/craftable.schema.json`. For example:

```json
"craftable": {
  "recipes": [{
    "id": "glider",
    "type": "mainmod:fake_crafting_shaped",
    "gameplayToggle": "soaring",
    "pattern": ["LLL", "MMM", "S S"],
    "key": { "L": "minecraft:leather", "M": "minecraft:phantom_membrane", "S": "minecraft:breeze_rod" },
    "result": { "stack": "mainmod:charm-glider", "count": 1 }
  }]
}
```

`gameplayToggle` may be omitted or empty for recipes that are always available.
Crafting table toggles do not restrict committee members. The validator checks toggle
IDs against `data/drops.json`. Item drop labels are inferred from the earliest dated
toggle among `shopPurchasable`, `decoBlock`, and `craftable.recipes`; there is no root
`drop` field. The generator writes these recipes into the ignored
`mod/src/main/resources/data/mainmod/recipe/dont_edit_auto_generated` directory.
Edit `item.json`, not the generated recipe. Recipes that do not produce an item defined
by `item.json` remain directly in the mod recipe directory.

Other copied item, daily, and hopper data lives in the ignored
`mod/src/main/resources/data/mainmod/dont_edit_auto_generated` directory and is staged
from `data/data` during validation and builds.

## Supported item layouts

### 1. Basic 2D item

```json
{
  "title": "Soul",
  "id": "soul",
  "modelType": "basic",
  "rarity": "rare",
  "maxStackSize": 64,
  "tooltips": []
}
```

Required files:

- `item.json`
- `texture.png`
- optional `texture.png.mcmeta`

### 2. Basic 3D item

```json
{
  "title": "Astral Orb",
  "id": "astral-orb",
  "modelType": "basic-3d",
  "rarity": "epic",
  "maxStackSize": 1,
  "tooltips": []
}
```

Required files:

- `item.json`
- `model.json`
- `model.png`
- optional `model.png.mcmeta`

### 3. Cosmetic

```json
{
  "title": "Beret",
  "id": "cosmetic-beret",
  "modelType": "cosmetic",
  "rarity": "common",
  "maxStackSize": 1,
  "tooltips": [],
  "equippableCosmetic": {},
  "dyeable": {
    "tintColor": "#8A2BE2"
  }
}
```

Required files:

- `item.json`
- `model.json`
- `model.png`
- optional `model.png.mcmeta`

Notes:

- `equippableCosmetic` must be present for `modelType: "cosmetic"`.
- If `dyeable.tintColor` is present, the generated `carved_pumpkin` selector entry is emitted
  with a `minecraft:dye` tint and that hex colour becomes the default tint.

### 4. Charm

```json
{
  "title": "Candle of the Deep",
  "id": "charm-candle-of-the-deep",
  "modelType": "charm",
  "rarity": "rare",
  "maxStackSize": 1,
  "tooltips": [],
  "equippableCharm": {
    "equipmentSlot": "chest",
    "equippableAssetId": "candle_of_the_deep__charm"
  }
}
```

Required files:

- `item.json`
- `texture.png`
- optional `texture.png.mcmeta`
- `equippable.png`

Notes:

- `equippableCharm.equipmentSlot` decides whether generated equipment goes under
  `humanoid` or `humanoid_leggings`.
- `equippableCharm.equippableAssetId` must end with `__charm`.
