import {
	formatManchesterDateTime,
	formatManchesterInput,
	parseManchesterInput,
} from '@/lib/date-time';

export { parseManchesterInput };

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
	return formatManchesterDateTime(expiresAtUnixMs);
}

export function formatDateTime(timestamp: number) {
	return formatManchesterDateTime(timestamp);
}

export function formatPreciseDateTime(timestamp: number) {
	return formatManchesterDateTime(timestamp, {
		year: 'numeric',
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
		second: '2-digit',
	});
}

export function formatLondonDateTime(timestamp: number) {
	return formatManchesterDateTime(timestamp);
}

export function formatLondonInput(timestamp: number) {
	return formatManchesterInput(timestamp);
}
