# new item defs

all required types are in root, everything option is a subobject

some fields are incompatible (https://chatgpt.com/c/69bbb78d-befc-838d-b3bc-b5e266c6d82b)
- torsoExtremetiesEquippable & dyeable

new shape:

```json
{
	"title": "1 Dabloon",
	"id": "coin-1",
	"modelType": "basic",
	"rarity": "common",
	"maxStackSize": 64,
	"tooltips": [
		"Might turn into a million and we all rich"
	],
	"charm": {
		"charmId": null
	},
	"consumable": {
		"isDrink": null,
		"consumeSeconds": null,
		"canAlwaysEat": null,
		"hungerBars": null,
		"saturationBars": null,
		"directHearts": null,
		"useRemainderItem": null,
		"effects": null
	},
	"dyeable": {
		"tintColor": null
	},
	"equippableCharm": {
		"equippableAssetId": null,
		"equipmentSlot": null
	},
	"equippableCosmetic": {}
}
```