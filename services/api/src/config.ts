export interface AppConfig {
	port: number
	host: string
	logLevel: 'fatal' | 'error' | 'warn' | 'info' | 'debug' | 'trace' | 'silent'
	databasePath: string
	publicWebBaseUrl: string
	publicApiBaseUrl: string
	registrationSessionTtlMs: number
	webSessionTtlMs: number
	sessionCookieName: string
	sessionCookieSecure: boolean
	discordClientId: string | null
	discordClientSecret: string | null
	discordRedirectUri: string | null
}

const LOG_LEVELS = new Set<AppConfig['logLevel']>([
	'fatal',
	'error',
	'warn',
	'info',
	'debug',
	'trace',
	'silent',
])

function toNumber(value: string | undefined, fallback: number): number {
	if (!value) return fallback
	const parsed = Number(value)
	return Number.isFinite(parsed) ? parsed : fallback
}

function toBoolean(value: string | undefined, fallback: boolean): boolean {
	if (!value) return fallback
	return ['1', 'true', 'yes', 'on'].includes(value.toLowerCase())
}

function trimTrailingSlash(value: string): string {
	return value.endsWith('/') ? value.slice(0, -1) : value
}

function nullIfBlank(value: string | undefined): string | null {
	const trimmed = value?.trim()
	return trimmed ? trimmed : null
}

export function buildConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
	const logLevel = env.LOG_LEVEL as AppConfig['logLevel'] | undefined

	return {
		port: toNumber(env.PORT, 8080),
		host: env.HOST || '0.0.0.0',
		logLevel: logLevel && LOG_LEVELS.has(logLevel) ? logLevel : 'info',
		databasePath: env.DATABASE_PATH || '/data/main.sqlite',
		publicWebBaseUrl: trimTrailingSlash(env.PUBLIC_WEB_BASE_URL || 'http://127.0.0.1:3000'),
		publicApiBaseUrl: trimTrailingSlash(env.PUBLIC_API_BASE_URL || 'http://127.0.0.1:8080'),
		registrationSessionTtlMs: toNumber(env.REGISTRATION_SESSION_TTL_MS, 30 * 60 * 1000),
		webSessionTtlMs: toNumber(env.WEB_SESSION_TTL_MS, 30 * 24 * 60 * 60 * 1000),
		sessionCookieName: env.SESSION_COOKIE_NAME || 'mmu_session',
		sessionCookieSecure: toBoolean(env.SESSION_COOKIE_SECURE, false),
		discordClientId: nullIfBlank(env.DISCORD_CLIENT_ID),
		discordClientSecret: nullIfBlank(env.DISCORD_CLIENT_SECRET),
		discordRedirectUri:
			nullIfBlank(env.DISCORD_REDIRECT_URI) ||
			`${trimTrailingSlash(env.PUBLIC_API_BASE_URL || 'http://127.0.0.1:8080')}/api/auth/discord/callback`,
	}
}
