# Particle emission for cosmetics and deco blocks

Add `particleEmission` to an item definition that has `equippableCosmetic`, `decoBlock`, or both. The server emits particles while the cosmetic is in the head slot or the item is displayed in an item frame, including our deco block frames. An inventory item, dropped item, or held item does not emit.

```json
"particleEmission": {
  "particles": [
    {
      "particle": "minecraft:smoke",
      "from": [7.8, 13, 7.8],
      "to": [8.2, 14, 8.2],
      "minTicks": 14,
      "maxTicks": 30
    }
  ]
}
```

Each entry has its own timer. After an emission, the next delay is chosen uniformly between `minTicks` and `maxTicks`, inclusive. The next position is chosen uniformly on each axis between `from` and `to`. Equal endpoints give an exact point. Coordinates use the model's 0–16 units around `[8, 8, 8]`; values outside 0–16 can put particles beyond the model. The current server-side positioning follows the wearer's facing and the frame's mounting face, with nominal scale 1.5 for worn models and 2 for placed models. Worn coordinates are centred a quarter block above the wearer's eye position. This does not follow client-only head pitch or each model's custom display transform exactly, so check the result in game.

Any namespaced particle ID is accepted by the schema. Simple vanilla or registered particle types work by ID. For `minecraft:dust`, `color` (for example `"#FF00AA"`) and `scale` (greater than 0 and at most 4) are optional. `minecraft:note` gets a random note colour and rises using its vanilla behavior. For other particle types that need extra data, an optional `arguments` string is appended directly to the ID and parsed using Minecraft's particle command parser; for example, use the syntax that follows the particle ID in a working `/particle` command. Invalid or unsupported particle data is skipped at runtime. `arguments` cannot be combined with `color` or `scale`.

The item schema validates field types, namespaced IDs, finite coordinate ranges, and allowed combinations. The data validator also checks that `minTicks` does not exceed `maxTicks` and `from` does not exceed `to` on any axis. Emission is limited to loaded frames within 32 blocks of a player.
