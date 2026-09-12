export const DEFAULT_LAUNCH_TIME = Date.parse('2026-09-29T19:00:00Z');

export function formatLaunchTimeLabel(timestamp: number) {
	return new Intl.DateTimeFormat('en-GB', {
		timeZone: 'Europe/London',
		dateStyle: 'long',
		timeStyle: 'short',
	}).format(timestamp);
}
