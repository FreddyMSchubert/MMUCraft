function normalizeBaseUrl(value: string): string {
	const url = new URL(value)
	return url.toString().replace(/\/$/, '')
}

export function getApiBaseUrl(): string {
	return normalizeBaseUrl(process.env.API_BASE_URL ?? 'http://api:8080')
}

export function getPublicApiBaseUrl(): string {
	return normalizeBaseUrl(process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://127.0.0.1:8080')
}

export function getSessionCookieName(): string {
	return process.env.SESSION_COOKIE_NAME ?? 'mmu_session'
}
