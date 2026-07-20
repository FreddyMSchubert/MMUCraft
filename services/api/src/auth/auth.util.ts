import { createHash, randomBytes, randomInt, timingSafeEqual } from 'node:crypto'

const MMU_EMAIL_DOMAINS = ['stu.mmu.ac.uk', 'mmu.ac.uk']

export function normalizeEmail(email: string): string {
	return email.trim().toLowerCase()
}

export function isAllowedEmail(email: string): boolean {
	const normalized = normalizeEmail(email)
	const parts = parseEmailParts(normalized)
	if (!parts) return false

	return MMU_EMAIL_DOMAINS.includes(parts.domain)
}

export function isValidEmail(email: string): boolean {
	return parseEmailParts(normalizeEmail(email)) !== null
}

function parseEmailParts(email: string): { local: string; domain: string } | null {
	const parts = email.split('@')
	if (parts.length !== 2) return null

	const [local, domain] = parts
	if (!local || !domain) return null
	if (!/^[^\s@]+$/.test(local)) return null
	const domainParts = domain.split('.')
	if (domainParts.length < 2 || domainParts.some((part) => !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(part))) return null

	return { local, domain }
}

export function createNumericCode(length = 6): string {
	const min = 10 ** (length - 1)
	const max = 10 ** length
	return String(randomInt(min, max))
}

export function createMinecraftCode(): string {
	return randomBytes(4).toString('hex').toUpperCase()
}

export function createOpaqueToken(): string {
	return randomBytes(32).toString('base64url')
}

export function hashSecret(value: string): string {
	const secret = process.env.AUTH_CODE_SECRET ?? 'dev-change-me'
	return createHash('sha256').update(`${secret}:${value}`).digest('hex')
}

export function safeSecretEquals(rawValue: string, storedHash: string): boolean {
	const actualHash = hashSecret(rawValue)
	const actual = Buffer.from(actualHash)
	const expected = Buffer.from(storedHash)

	if (actual.length !== expected.length) return false
	return timingSafeEqual(actual, expected)
}

export function isAuthRequestActive(request: {
	active_code: string | null
	completed_at_unix_ms: number | null
	expires_at_unix_ms: number
}, now = Date.now()): boolean {
	return request.active_code !== null
		&& request.completed_at_unix_ms === null
		&& request.expires_at_unix_ms > now
}

export function isValidMinecraftUsername(username: string): boolean {
	return /^[A-Za-z0-9_]{3,16}$/.test(username)
}
