import { formatManchesterDateTime } from './date-time';

export const DEFAULT_LAUNCH_TIME = Date.parse('2026-09-29T19:00:00Z');

export function formatLaunchTimeLabel(timestamp: number) {
	return formatManchesterDateTime(timestamp, {
		dateStyle: 'long',
		timeStyle: 'short',
	});
}
