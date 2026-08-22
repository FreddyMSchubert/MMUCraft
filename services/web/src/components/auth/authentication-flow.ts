import { ASSETS } from '@/lib/assets';

export type AuthenticationStep =
	| 'email'
	| 'email-code'
	| 'minecraft-username'
	| 'minecraft-code'
	| 'rules'
	| 'done'
	| 'signin'
	| 'signin-code';

interface ApiErrorResponse {
	message?: string | string[];
	retryAfterSeconds?: number;
}

export class ApiRequestError extends Error {
	constructor(
		message: string,
		readonly retryAfterSeconds?: number,
	) {
		super(message);
	}
}

export const SERVER_RULES = [
	'👿 Hate and prejudice, NSFW content, criminal behaviour and discussion is prohibited',
	'🗯️ Politics, religion, and your mother should be discussed respectfully.',
	'☢️ General toxicity is prohibited.',
	'💥 Griefing & exploiting loopholes is prohibited.',
	'‼️ Instructions from committee members are to be followed.',
	'🤡 Fun is to be had, this is an order.',
] as const;

const TEXTURE_BASE = `${ASSETS.minecraft.vanilla}/textures`;
export const AUTH_CODE_ITEMS = [
	{ name: 'Apple', image: `${TEXTURE_BASE}/item/apple.png` },
	{ name: 'Axe', image: `${TEXTURE_BASE}/item/golden_axe.png` },
	{ name: 'Beetroot', image: `${TEXTURE_BASE}/item/beetroot.png` },
	{ name: 'Coal', image: `${TEXTURE_BASE}/item/coal.png` },
	{ name: 'Copper', image: `${TEXTURE_BASE}/item/raw_copper.png` },
	{ name: 'Creeper', image: `${TEXTURE_BASE}/entity/creeper/creeper.png`, head: true },
	{ name: 'Diamond', image: `${TEXTURE_BASE}/item/diamond.png` },
	{ name: 'Egg', image: `${TEXTURE_BASE}/item/egg.png` },
	{ name: 'Emerald', image: `${TEXTURE_BASE}/item/emerald.png` },
	{ name: 'Fish', image: `${TEXTURE_BASE}/item/tropical_fish.png` },
	{ name: 'Flint and Steel', image: `${TEXTURE_BASE}/item/flint_and_steel.png` },
	{ name: 'Flower', image: `${TEXTURE_BASE}/block/red_tulip.png` },
	{ name: 'Gold Ingot', image: `${TEXTURE_BASE}/item/gold_ingot.png` },
	{ name: 'Iron', image: `${TEXTURE_BASE}/item/raw_iron.png` },
	{ name: 'Lapis Lazuli', image: `${TEXTURE_BASE}/item/lapis_lazuli.png` },
	{ name: 'Lava Bucket', image: `${TEXTURE_BASE}/item/lava_bucket.png` },
	{ name: 'Lily Pad', image: `${TEXTURE_BASE}/block/lily_pad.png` },
	{ name: 'Melon Slice', image: `${TEXTURE_BASE}/item/melon_slice.png` },
	{ name: 'Mushroom', image: `${TEXTURE_BASE}/block/red_mushroom.png` },
	{ name: 'Music Disk', image: `${TEXTURE_BASE}/item/music_disc_cat.png` },
	{ name: 'Netherite', image: `${TEXTURE_BASE}/item/netherite_scrap.png` },
	{ name: 'Pickaxe', image: `${TEXTURE_BASE}/item/iron_pickaxe.png` },
	{ name: 'Potato', image: `${TEXTURE_BASE}/item/potato.png` },
	{ name: 'Potion', image: `${TEXTURE_BASE}/item/potion.png` },
	{ name: 'Quartz', image: `${TEXTURE_BASE}/item/quartz.png` },
	{ name: 'Redstone', image: `${TEXTURE_BASE}/item/redstone.png` },
	{ name: 'Shovel', image: `${TEXTURE_BASE}/item/copper_shovel.png` },
	{ name: 'Slimeball', image: `${TEXTURE_BASE}/item/slime_ball.png` },
	{ name: 'Spear', image: `${TEXTURE_BASE}/item/diamond_spear.png` },
	{ name: 'Sword', image: `${TEXTURE_BASE}/item/wooden_sword.png` },
	{ name: 'Totem', image: `${TEXTURE_BASE}/item/totem_of_undying.png` },
	{ name: 'Trident', image: `${TEXTURE_BASE}/item/trident.png` },
	{ name: 'Wheat', image: `${TEXTURE_BASE}/item/wheat.png` },
	{ name: 'Zombie', image: `${TEXTURE_BASE}/entity/zombie/zombie.png`, head: true },
] as const;

export const AUTH_CODE_LENGTH = 3;
export const RESEND_DELAY_MS = 60_000;
export const SIGNUP_PROGRESS: Partial<Record<AuthenticationStep, number>> = {
	email: 1,
	'email-code': 2,
	'minecraft-username': 3,
	'minecraft-code': 4,
	rules: 5,
	done: 5,
};

export function emptyAuthCode() {
	return Array<string>(AUTH_CODE_LENGTH).fill('');
}

export async function postJson<T>(url: string, body: object): Promise<T> {
	const response = await fetch(url, {
		method: 'POST',
		headers: { 'content-type': 'application/json' },
		body: JSON.stringify(body),
	});
	const data = await response.json().catch(() => null);
	if (!response.ok) {
		const error = data as ApiErrorResponse | null;
		const message = Array.isArray(error?.message)
			? error.message.join(', ')
			: (error?.message ?? 'Request failed');
		throw new ApiRequestError(message, error?.retryAfterSeconds);
	}
	return data as T;
}

export function verificationMessage(email: string, resent = false) {
	return resent
		? `We sent another three-item code to ${email}. It expires in 10 minutes.`
		: `We sent a three-item code to ${email}. It expires in 10 minutes.`;
}

export function formatCountdown(totalSeconds: number) {
	if (totalSeconds <= 60) return `${totalSeconds}s`;
	const hours = Math.floor(totalSeconds / 3600);
	const minutes = Math.floor((totalSeconds % 3600) / 60);
	const seconds = totalSeconds % 60;
	return [
		hours ? `${hours}h` : '',
		minutes ? `${minutes}m` : '',
		!hours && seconds ? `${seconds}s` : '',
	]
		.filter(Boolean)
		.join(' ');
}
