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
	'👿 Hate and prejudice, NSFW content, criminal behaviour and discussion are prohibited.',
	'🗯️ Discuss sensitive topics, including politics and religion, respectfully.',
	'☢️ General toxicity is prohibited.',
	'💥 Griefing and exploiting loopholes are prohibited.',
	'🤝 Cooperate with committee members at all times.',
	'🚫 No impersonation, scams, deliberate spam, or disruptive advertising.',
] as const;

const TEXTURE_BASE = `${ASSETS.minecraft.vanilla}/textures`;
export const AUTH_CODE_ITEMS = [
	{ name: 'Apple', image: `${TEXTURE_BASE}/item/apple.png` },
	{ name: 'Coal', image: `${TEXTURE_BASE}/item/coal.png` },
	{ name: 'Diamond', image: `${TEXTURE_BASE}/item/diamond.png` },
	{ name: 'Egg', image: `${TEXTURE_BASE}/item/egg.png` },
	{ name: 'Gold Ingot', image: `${TEXTURE_BASE}/item/gold_ingot.png` },
	{ name: 'Iron', image: `${TEXTURE_BASE}/item/raw_iron.png` },
	{ name: 'Lapis Lazuli', image: `${TEXTURE_BASE}/item/lapis_lazuli.png` },
	{ name: 'Pickaxe', image: `${TEXTURE_BASE}/item/iron_pickaxe.png` },
	{ name: 'Redstone', image: `${TEXTURE_BASE}/item/redstone.png` },
	{ name: 'Sword', image: `${TEXTURE_BASE}/item/wooden_sword.png` },
	{ name: 'Totem', image: `${TEXTURE_BASE}/item/totem_of_undying.png` },
	{ name: 'Wheat', image: `${TEXTURE_BASE}/item/wheat.png` },
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

export function verificationMessage(resent = false) {
	return `${resent ? 'We sent another' : 'We sent a'} three-item code to `;
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
