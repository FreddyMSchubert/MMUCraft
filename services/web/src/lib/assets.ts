const MINECRAFT_ASSETS =
	'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.3/assets';

export const ASSETS = {
	minecraft: {
		root: MINECRAFT_ASSETS,
		vanilla: `${MINECRAFT_ASSETS}/minecraft`,
	},
} as const;
