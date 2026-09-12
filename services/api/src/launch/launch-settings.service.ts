import { BadRequestException, Injectable } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DatabaseService, launchSettings } from '../database/database.service';

const DEFAULT_LAUNCH_AT_UNIX_MS = 1_790_708_400_000;
const LONDON_TIME = new Intl.DateTimeFormat('en-GB', {
	timeZone: 'Europe/London',
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
	hour: '2-digit',
	minute: '2-digit',
	second: '2-digit',
	hourCycle: 'h23',
});

@Injectable()
export class LaunchSettingsService {
	constructor(private readonly database: DatabaseService) {}

	get() {
		const row = this.database.connection
			.select()
			.from(launchSettings)
			.where(eq(launchSettings.id, 1))
			.get();
		return { launchAtUnixMs: row?.launch_at_unix_ms ?? DEFAULT_LAUNCH_AT_UNIX_MS };
	}

	hasLaunched(now = Date.now()) {
		return now >= this.get().launchAtUnixMs;
	}

	update(target: unknown) {
		const launchAtUnixMs = parseLondonDateTime(target);
		this.database.connection
			.insert(launchSettings)
			.values({ id: 1, launch_at_unix_ms: launchAtUnixMs })
			.onConflictDoUpdate({
				target: launchSettings.id,
				set: { launch_at_unix_ms: launchAtUnixMs },
			})
			.run();
		return this.get();
	}
}

function parseLondonDateTime(input: unknown) {
	if (typeof input !== 'string')
		throw new BadRequestException('Enter a valid British date and time');
	const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(input);
	if (!match) throw new BadRequestException('Enter a valid British date and time');
	const [, year, month, day, hour, minute] = match;
	if (!year || !month || !day || !hour || !minute)
		throw new BadRequestException('Enter a valid British date and time');
	const localUnixMs = Date.UTC(+year, +month - 1, +day, +hour, +minute);
	let targetUnixMs = localUnixMs - londonOffsetAt(localUnixMs);
	targetUnixMs = localUnixMs - londonOffsetAt(targetUnixMs);
	if (formatLondon(targetUnixMs) !== input)
		throw new BadRequestException('That time does not exist in British time');
	return targetUnixMs;
}

function londonOffsetAt(timestamp: number) {
	const parts = londonParts(timestamp);
	return (
		Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second) -
		timestamp
	);
}

function formatLondon(timestamp: number) {
	const { year, month, day, hour, minute } = londonParts(timestamp);
	return `${year}-${two(month)}-${two(day)}T${two(hour)}:${two(minute)}`;
}

function londonParts(timestamp: number) {
	const values = Object.fromEntries(
		LONDON_TIME.formatToParts(new Date(timestamp)).map((part) => [part.type, part.value]),
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
