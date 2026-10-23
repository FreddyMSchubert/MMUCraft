export const DUCKS = [
	{
		id: 1,
		itemId: 'duck-rubber',
		condition: 'Join the server.',
		announcementReason: 'joining the server',
		manual: false,
	},
	{
		id: 2,
		itemId: 'duck-minequack',
		condition: 'Become a member.',
		announcementReason: 'becoming a member',
		manual: false,
	},
	{
		id: 3,
		itemId: 'duck-socials',
		condition: 'Attend five in-person socials.',
		announcementReason: 'attending five socials',
		manual: true,
	},
	{
		id: 4,
		itemId: 'duck-committee-kill',
		condition: 'Kill every committee member at least once.',
		announcementReason: 'taking down every committee member',
		manual: false,
	},
	{
		id: 5,
		itemId: 'duck-aristoquack',
		condition:
			'Fill every inventory slot, hotbar slot, and offhand with full stacks of 1-dabloon coins.',
		announcementReason: 'being rich AF',
		manual: false,
	},
	{
		id: 6,
		itemId: 'duck-mythical-cosmetic',
		condition: 'Unlock every cosmetic and charm in the shop.',
		announcementReason: 'unlocking the entire shop',
		manual: false,
	},
	{
		id: 7,
		itemId: 'duck-playtime',
		condition: 'Play on the server for 168 hours.',
		announcementReason: 'playing for a full week',
		manual: false,
	},
	{
		id: 8,
		itemId: 'duck-hearts',
		condition:
			'Have 20 full red hearts, full hunger and saturation, and Regeneration I from a beacon at once.',
		announcementReason: 'maxing out hearts, hunger, saturation and regeneration',
		manual: false,
	},
	{
		id: 9,
		itemId: 'duck-enderite',
		condition:
			'Wear a full set of Enderite armour with every compatible enchantment at its maximum Enderite level, excluding curses.',
		announcementReason: 'wearing fully max-enchanted Enderite armour',
		manual: false,
	},
	{
		id: 10,
		itemId: 'duck-joke',
		condition:
			'Post a genuinely good and new joke into the Discord suggestions channel, resulting in your joke being added in-game.',
		announcementReason: 'getting a joke into the game',
		manual: true,
	},
	{
		id: 11,
		itemId: 'duck-bugger',
		condition: 'Find and report a bug. Thanks!',
		announcementReason: 'finding and reporting a bug',
		manual: true,
	},
	{
		id: 12,
		itemId: 'duck-mythical-fish',
		condition: 'Catch every fish.',
		announcementReason: 'catching every fish',
		manual: false,
	},
	{
		id: 13,
		itemId: 'duck-every-duck',
		condition: 'Find every other duck.',
		announcementReason: 'finding every other duck',
		manual: false,
	},
] as const;

export type DuckId = (typeof DUCKS)[number]['id'];

export function duckById(id: number) {
	return DUCKS.find((duck) => duck.id === id);
}

export function duckRank(count: number) {
	const ranks = [
		'Duckless Wanderer',
		'Puddle Apprentice',
		'Duck Aficionado',
		'Knight of the Golden Bill',
		'Keeper of the Sacred Quack',
		'Baron of the Breadcrumbs',
		'The Feathered Menace',
		'Supreme Quackonaut',
		'Lord of the Lilypads',
		'Grand Admiral of the Bathtub',
		'High Chancellor of the Pond',
		'Emperor of All Things Buoyant',
		'One Quack from Omnipotence',
		'Duckiversal Grandmaster',
	];
	return ranks[Math.min(Math.max(count, 0), ranks.length - 1)] ?? 'Duckless Wanderer';
}
