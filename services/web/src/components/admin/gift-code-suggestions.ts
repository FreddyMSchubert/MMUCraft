const CODE_ADJECTIVES = [
	'ancient',
	'blocky',
	'creeping',
	'enchanted',
	'ender',
	'golden',
	'hidden',
	'nether',
	'pixelated',
	'redstone',
	'shimmering',
	'square',
	'verdant',
	'cute',
	'creepy',
	'gorgeous',
	'pretty',
	'speedy',
	'rough',
	'angry',
	'anxious',
	'attacking',
];
const CODE_NOUNS = [
	'allay',
	'axolotl',
	'beacon',
	'bee',
	'creeper',
	'elytra',
	'fox',
	'golem',
	'minecart',
	'pickaxe',
	'shulker',
	'slime',
	'sniffer',
	'warden',
	'armadillo',
	'bat',
	'camel',
	'cat',
	'moobloom',
	'ghast',
	'ocelot',
	'parrot',
	'squid',
	'salmon',
	'horse',
	'villager',
	'turtle',
	'dolphin',
	'enderman',
	'alpaka',
	'panda',
	'pufferfish',
	'spider',
	'blaze',
	'creeper',
	'pillager',
	'vindicator',
	'witch',
	'silverfish',
	'dragon',
	'wither',
	'cobblestone',
	'pickaxe',
	'sword',
	'axe',
	'spear',
	'redstone',
	'diamond',
	'gold',
];
const CODE_JOINERS = ['-', '_', '.'];

export function makeGiftCodeSuggestion() {
	const adjective = CODE_ADJECTIVES[Math.floor(Math.random() * CODE_ADJECTIVES.length)];
	const noun = CODE_NOUNS[Math.floor(Math.random() * CODE_NOUNS.length)];
	const joiner = CODE_JOINERS[Math.floor(Math.random() * CODE_JOINERS.length)];
	return `${adjective}${joiner}${noun}`;
}

export function makeDifferentGiftCodeSuggestion(current: string) {
	let suggestion = makeGiftCodeSuggestion();
	while (suggestion === current) suggestion = makeGiftCodeSuggestion();
	return suggestion;
}
