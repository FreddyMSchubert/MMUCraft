export function apiMessage(body: unknown, fallback: string) {
	if (!body || typeof body !== 'object' || !('message' in body)) return fallback;
	const message = (body as { message?: unknown }).message;
	return Array.isArray(message)
		? message.join(', ')
		: typeof message === 'string'
			? message
			: fallback;
}

export function apiBody<T extends object>(body: unknown): T {
	if (!body || typeof body !== 'object')
		throw new Error('The server returned an invalid response.');
	return body as T;
}

export async function fetchAdmin<T extends object>(path: string, fallback: string): Promise<T> {
	const response = await fetch(path, { cache: 'no-store' });
	const body = await response.json().catch(() => null);
	if (!response.ok) throw new Error(apiMessage(body, fallback));
	return apiBody<T>(body);
}

export function errorMessage(error: unknown, fallback: string) {
	return error instanceof Error ? error.message : fallback;
}

export function formatExpiry(expiresAtUnixMs: number) {
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(expiresAtUnixMs));
}

export function formatDateTime(timestamp: number) {
	return new Intl.DateTimeFormat(undefined, {
		dateStyle: 'medium',
		timeStyle: 'short',
	}).format(new Date(timestamp));
}

export function formatLondonDateTime(timestamp: number) {
	return new Intl.DateTimeFormat('en-GB', {
		dateStyle: 'medium',
		timeStyle: 'short',
		timeZone: 'Europe/London',
	}).format(new Date(timestamp));
}

export function formatLondonInput(timestamp: number) {
	const values = Object.fromEntries(
		new Intl.DateTimeFormat('en-GB', {
			timeZone: 'Europe/London',
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
			hourCycle: 'h23',
		})
			.formatToParts(new Date(timestamp))
			.map((part) => [part.type, part.value]),
	);
	return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}`;
}
