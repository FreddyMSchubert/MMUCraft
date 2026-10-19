export const DUCKS = [
	{
		id: 1,
		itemId: 'duck-rubber',
		name: 'Rubber Duck',
		condition: 'Join the server.',
		manual: false,
	},
	{
		id: 2,
		itemId: 'duck-minequack',
		name: 'MineQuack',
		condition: 'Become a member.',
		manual: false,
	},
	{
		id: 3,
		itemId: 'duck-socials',
		name: 'Socials Duck',
		condition: 'Attend three in-person socials.',
		manual: true,
	},
	{
		id: 4,
		itemId: 'duck-committee-kill',
		name: 'Committee Kill Duck',
		condition: 'Kill a committee member.',
		manual: false,
	},
	{
		id: 5,
		itemId: 'duck-aristoquack',
		name: 'AristoQuack',
		condition:
			'Fill every inventory slot, hotbar slot, and offhand with full stacks of 1-dabloon coins.',
		manual: false,
	},
	{
		id: 6,
		itemId: 'duck-mythical-cosmetic',
		name: 'Mythical Cosmetic Duck',
		condition: 'Wear a mythical cosmetic.',
		manual: false,
	},
	{
		id: 7,
		itemId: 'duck-playtime',
		name: '100 Hours Duck',
		condition: 'Play on the server for 100 hours.',
		manual: false,
	},
	{
		id: 8,
		itemId: 'duck-hearts',
		name: '20 Hearts Duck',
		condition: 'Have 20 full red hearts at once.',
		manual: false,
	},
	{
		id: 9,
		itemId: 'duck-enderite',
		name: 'Enderite Duck',
		condition: 'Wear a full set of Enderite armour.',
		manual: false,
	},
	{
		id: 10,
		itemId: 'duck-joke',
		name: 'Joke Duck',
		condition: 'Get a new joke from your Discord suggestion added in-game.',
		manual: true,
	},
	{
		id: 11,
		itemId: 'duck-bugger',
		name: 'Bugger Duck',
		condition: 'Find and report a bug. Thanks!',
		manual: true,
	},
	{
		id: 12,
		itemId: 'duck-mythical-fish',
		name: 'Mythical Fish Duck',
		condition: 'Catch a mythical fish.',
		manual: false,
	},
	{
		id: 13,
		itemId: 'duck-every-duck',
		name: 'Every Duck Duck',
		condition: 'Find every other duck.',
		manual: false,
	},
] as const;

export type DuckId = (typeof DUCKS)[number]['id'];

export function duckById(id: number) {
	return DUCKS.find((duck) => duck.id === id);
}

export function duckRank(count: number) {
	if (count === 13) return 'Duckiversal Grandmaster';
	if (count === 12) return 'One Quack from Omnipotence';
	if (count >= 10) return 'High Chancellor of the Pond';
	if (count >= 7) return 'Supreme Quackonaut';
	if (count >= 4) return 'Duck Aficionado';
	if (count >= 1) return 'Puddle Apprentice';
	return 'Duckless Wanderer';
}
