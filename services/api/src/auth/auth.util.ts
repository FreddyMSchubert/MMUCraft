import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { isIP } from 'node:net';

export const AUTH_CODE_ITEMS = [
	'Apple',
	'Coal',
	'Diamond',
	'Egg',
	'Gold Ingot',
	'Iron',
	'Lapis Lazuli',
	'Pickaxe',
	'Redstone',
	'Sword',
	'Totem',
	'Wheat',
] as const;

export function normalizeEmail(email: string): string {
	return email.trim().toLowerCase();
}

export function isAllowedEmail(email: string): boolean {
	const normalized = normalizeEmail(email);
	const parts = parseEmailParts(normalized);
	if (!parts) return false;

	return (
		parts.domain === 'mmu.ac.uk' ||
		(parts.domain === 'stu.mmu.ac.uk' && /^\d+$/.test(parts.local))
	);
}

export function isValidEmail(email: string): boolean {
	return parseEmailParts(normalizeEmail(email)) !== null;
}

function parseEmailParts(email: string): { local: string; domain: string } | null {
	const parts = email.split('@');
	if (parts.length !== 2) return null;

	const [local, domain] = parts;
	if (!local || !domain) return null;
	if (!/^[^\s@]+$/.test(local)) return null;
	const domainParts = domain.split('.');
	if (
		domainParts.length < 2 ||
		domainParts.some((part) => !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(part))
	)
		return null;

	return { local, domain };
}

export function createAuthCode(length = 3): string {
	return Array.from({ length }, () => AUTH_CODE_ITEMS[randomInt(AUTH_CODE_ITEMS.length)]).join(
		'|',
	);
}

export function displayAuthCode(code: string): string {
	return code.split('|').join(' → ');
}

export function createOpaqueToken(): string {
	return randomBytes(32).toString('base64url');
}

export function hashSecret(value: string): string {
	const secret = process.env.AUTH_CODE_SECRET ?? 'dev-change-me';
	return createHash('sha256').update(`${secret}:${value}`).digest('hex');
}

export function safeSecretEquals(rawValue: string, storedHash: string): boolean {
	const actualHash = hashSecret(rawValue);
	const actual = Buffer.from(actualHash);
	const expected = Buffer.from(storedHash);

	if (actual.length !== expected.length) return false;
	return timingSafeEqual(actual, expected);
}

export function normalizeIpBucket(input: string): string {
	const ip = input.trim().split('%')[0] ?? '';
	const mappedIpv4 = /^::ffff:(.+)$/i.exec(ip)?.[1];
	if (mappedIpv4 && isIP(mappedIpv4) === 4) return mappedIpv4;
	if (isIP(ip) !== 6) return ip || 'unknown';

	const [left = '', right = ''] = ip.toLowerCase().split('::');
	const leftParts = left ? left.split(':') : [];
	const rightParts = right ? right.split(':') : [];
	const parts = ip.includes('::')
		? [
				...leftParts,
				...Array<string>(8 - leftParts.length - rightParts.length).fill('0'),
				...rightParts,
			]
		: leftParts;

	return `${parts
		.slice(0, 4)
		.map((part) => part.padStart(4, '0'))
		.join(':')}::/64`;
}

export function isValidMinecraftUsername(username: string): boolean {
	return /^[A-Za-z0-9_]{3,16}$/.test(username);
}
