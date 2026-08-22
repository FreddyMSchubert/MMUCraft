export function apiMessage(body: unknown, fallback: string): string {
	if (!body || typeof body !== 'object' || !('message' in body)) return fallback;
	const { message } = body;
	if (typeof message === 'string') return message;
	if (Array.isArray(message) && message.every((item) => typeof item === 'string')) {
		return message.join(', ');
	}
	return fallback;
}
