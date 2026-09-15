export const MANCHESTER_TIME_ZONE = 'Europe/London';

const MANCHESTER_PARTS = new Intl.DateTimeFormat('en-GB', {
	timeZone: MANCHESTER_TIME_ZONE,
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
	hour: '2-digit',
	minute: '2-digit',
	second: '2-digit',
	hourCycle: 'h23',
});

export function formatManchesterDateTime(
	timestamp: number,
	options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' },
) {
	return new Intl.DateTimeFormat('en-GB', {
		...options,
		timeZone: MANCHESTER_TIME_ZONE,
	}).format(new Date(timestamp));
}

export function formatManchesterInput(timestamp: number) {
	const values = manchesterParts(timestamp);
	return `${values.year}-${two(values.month)}-${two(values.day)}T${two(values.hour)}:${two(values.minute)}`;
}

export function parseManchesterInput(value: string) {
	const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
	if (!match) return Number.NaN;

	const requested = {
		year: Number(match[1]),
		month: Number(match[2]),
		day: Number(match[3]),
		hour: Number(match[4]),
		minute: Number(match[5]),
		second: Number(match[6] || 0),
	};
	const wallClockUnixMs = Date.UTC(
		requested.year,
		requested.month - 1,
		requested.day,
		requested.hour,
		requested.minute,
		requested.second,
	);
	let timestamp = wallClockUnixMs;
	for (let pass = 0; pass < 3; pass += 1) {
		const adjusted = wallClockUnixMs - manchesterOffsetAt(timestamp);
		if (adjusted === timestamp) break;
		timestamp = adjusted;
	}

	const actual = manchesterParts(timestamp);
	return Object.entries(requested).every(
		([part, expected]) => actual[part as keyof typeof actual] === expected,
	)
		? timestamp
		: Number.NaN;
}

function manchesterOffsetAt(timestamp: number) {
	const parts = manchesterParts(timestamp);
	return (
		Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second) -
		timestamp
	);
}

function manchesterParts(timestamp: number) {
	const values = Object.fromEntries(
		MANCHESTER_PARTS.formatToParts(new Date(timestamp)).map((part) => [part.type, part.value]),
	);
	return {
		year: Number(values.year),
		month: Number(values.month),
		day: Number(values.day),
		hour: Number(values.hour),
		minute: Number(values.minute),
		second: Number(values.second),
	};
}

function two(value: number) {
	return String(value).padStart(2, '0');
}
