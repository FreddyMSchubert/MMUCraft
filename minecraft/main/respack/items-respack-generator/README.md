# MMU Resource Pack Generator

Item schemas

### 1. Basic 2D item

```json
{
  "type": "basic",
  "custom_model_data": "soul"
}
```

Required files:

- `item.json`
- `texture.png`
- optional `texture.png.mcmeta`

### 2. Basic 3D item

```json
{
  "type": "basic-3d",
  "custom_model_data": "astral-orb"
}
```

Required files:

- `item.json`
- `model.json`
- `model.png`
- optional `model.png.mcmeta`

### 3. Hat

```json
{
  "type": "hat",
  "custom_model_data": "cosmetic-hat-beret",
  "isTinted": false
}
```

Required files:

- `item.json`
- `model.json`
- `model.png`
- optional `model.png.mcmeta`

### 4. Charm

```json
{
  "type": "charm",
  "custom_model_data": "cosmetic-charm-candle-of-the-deep",
  "isLeggings": false,
  "equippable_asset_id": "candle_of_the_deep__charm"
}
```

Required files:

- `item.json`
- `texture.png`
- optional `texture.png.mcmeta`
- `equippable.png`
