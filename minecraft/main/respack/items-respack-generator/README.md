# MMU Resource Pack Generator

Generates the resource-pack side of the composable fake-item system.

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
`command_block` / `carved_pumpkin` item definitions.

Item definitions may include gameplay metadata such as `shopPurchasable`. The generator
validates that known metadata shape, then ignores it because it is not resource-pack data.

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
