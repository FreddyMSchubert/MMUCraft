import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common'
import { asc, eq, gt, lte } from 'drizzle-orm'
import { countdowns, DatabaseService } from '../database/database.service'

const LONDON_TIME = new Intl.DateTimeFormat('en-GB', {
	timeZone: 'Europe/London',
	year: 'numeric',
	month: '2-digit',
	day: '2-digit',
	hour: '2-digit',
	minute: '2-digit',
	second: '2-digit',
	hourCycle: 'h23',
})

@Injectable()
export class CountdownsService {
	constructor(private readonly database: DatabaseService) { }

	list() {
		return { countdowns: this.activeRows().map(toCountdown) }
	}

	create(
		headingInput: unknown,
		targetInput: unknown,
		descriptionInput: unknown,
		headingColorInput: unknown,
		descriptionColorInput: unknown,
		backgroundColorInput: unknown,
		backgroundAlphaInput: unknown,
		backgroundImageUrlInput: unknown,
	) {
		const values = countdownValues(headingInput, targetInput, descriptionInput, headingColorInput, descriptionColorInput, backgroundColorInput, backgroundAlphaInput, backgroundImageUrlInput)
		const now = Date.now()
		if (values.target_at_unix_ms <= now) throw new BadRequestException('The countdown time must be in the future')

		let createdId = 0
		this.database.connection.transaction((tx) => {
			tx.delete(countdowns).where(lte(countdowns.visible_until_unix_ms, now)).run()
			const existing = tx.select().from(countdowns).orderBy(asc(countdowns.position)).all()
			if (existing.length >= 4) throw new BadRequestException('You can have at most 4 countdowns')
			createdId = Number(tx.insert(countdowns).values({
				...values,
				position: existing.length,
			}).run().lastInsertRowid)
		})

		return toCountdown(this.database.connection.select().from(countdowns).where(eq(countdowns.id, createdId)).get()!)
	}

	update(
		idInput: string,
		headingInput: unknown,
		targetInput: unknown,
		descriptionInput: unknown,
		headingColorInput: unknown,
		descriptionColorInput: unknown,
		backgroundColorInput: unknown,
		backgroundAlphaInput: unknown,
		backgroundImageUrlInput: unknown,
	) {
		const id = positiveId(idInput)
		const existing = this.database.connection.select().from(countdowns).where(eq(countdowns.id, id)).get()
		if (!existing) throw new NotFoundException('Countdown not found')
		const values = countdownValues(headingInput, targetInput, descriptionInput, headingColorInput, descriptionColorInput, backgroundColorInput, backgroundAlphaInput, backgroundImageUrlInput)
		if (values.target_at_unix_ms <= Date.now() && values.target_at_unix_ms !== existing.target_at_unix_ms) {
			throw new BadRequestException('The countdown time must be in the future')
		}
		this.database.connection.update(countdowns).set(values).where(eq(countdowns.id, id)).run()
		return toCountdown(this.database.connection.select().from(countdowns).where(eq(countdowns.id, id)).get()!)
	}

	remove(idInput: string) {
		const id = positiveId(idInput)
		this.database.connection.transaction((tx) => {
			if (tx.delete(countdowns).where(eq(countdowns.id, id)).run().changes !== 1) {
				throw new NotFoundException('Countdown not found')
			}
			tx.select().from(countdowns).orderBy(asc(countdowns.position)).all()
				.forEach((row, position) => tx.update(countdowns).set({ position }).where(eq(countdowns.id, row.id)).run())
		})
		return { ok: true }
	}

	move(idInput: string, directionInput: unknown) {
		const id = positiveId(idInput)
		if (directionInput !== 'up' && directionInput !== 'down') {
			throw new BadRequestException('Direction must be up or down')
		}

		this.database.connection.transaction((tx) => {
			const rows = tx.select().from(countdowns).orderBy(asc(countdowns.position)).all()
			const index = rows.findIndex((row) => row.id === id)
			if (index === -1) throw new NotFoundException('Countdown not found')
			const swapIndex = index + (directionInput === 'up' ? -1 : 1)
			const current = rows[index]!
			const swap = rows[swapIndex]
			if (!swap) return
			tx.update(countdowns).set({ position: swap.position }).where(eq(countdowns.id, id)).run()
			tx.update(countdowns).set({ position: current.position }).where(eq(countdowns.id, swap.id)).run()
		})
		return this.list()
	}

	private activeRows() {
		return this.database.connection.select().from(countdowns)
			.where(gt(countdowns.visible_until_unix_ms, Date.now()))
			.orderBy(asc(countdowns.position)).all()
	}
}

function toCountdown(row: typeof countdowns.$inferSelect) {
	return {
		id: row.id,
		heading: row.heading,
		description: row.description,
		headingColor: row.heading_color,
		descriptionColor: row.description_color,
		backgroundColor: row.background_color,
		backgroundAlpha: row.background_alpha,
		backgroundImageUrl: row.background_image_url,
		targetAtUnixMs: row.target_at_unix_ms,
		visibleUntilUnixMs: row.visible_until_unix_ms,
	}
}

function requiredText(input: unknown, label: string, maxLength: number) {
	if (typeof input !== 'string' || !input.trim()) throw new BadRequestException(`${label} is required`)
	const value = input.trim()
	if (value.length > maxLength) throw new BadRequestException(`${label} must be ${maxLength} characters or fewer`)
	return value
}

function countdownValues(
	headingInput: unknown,
	targetInput: unknown,
	descriptionInput: unknown,
	headingColorInput: unknown,
	descriptionColorInput: unknown,
	backgroundColorInput: unknown,
	backgroundAlphaInput: unknown,
	backgroundImageUrlInput: unknown,
) {
	const targetAtUnixMs = parseLondonDateTime(targetInput)
	return {
		heading: requiredText(headingInput, 'Heading', 80),
		description: requiredText(descriptionInput, 'Abstract', 500),
		heading_color: hexColor(headingColorInput, 'Heading color'),
		description_color: hexColor(descriptionColorInput, 'Abstract color'),
		background_color: hexColor(backgroundColorInput, 'Background color'),
		background_alpha: percentage(backgroundAlphaInput),
		background_image_url: optionalImageUrl(backgroundImageUrlInput),
		target_at_unix_ms: targetAtUnixMs,
		visible_until_unix_ms: Math.max(nextLondonMidnight(targetAtUnixMs), targetAtUnixMs + 5 * 60 * 60 * 1000),
	}
}

function positiveId(input: string) {
	const id = Number(input)
	if (!Number.isInteger(id) || id <= 0) throw new NotFoundException('Countdown not found')
	return id
}

function hexColor(input: unknown, label: string) {
	if (typeof input !== 'string' || !/^#[0-9a-f]{6}$/i.test(input)) throw new BadRequestException(`${label} must be a 6-digit hex color`)
	return input.toLowerCase()
}

function percentage(input: unknown) {
	if (typeof input !== 'number' || !Number.isInteger(input) || input < 0 || input > 100) {
		throw new BadRequestException('Background opacity must be a whole number from 0 to 100')
	}
	return input
}

function optionalImageUrl(input: unknown) {
	if (input === null || input === undefined || input === '') return null
	if (typeof input !== 'string' || input.length > 2_000) throw new BadRequestException('Background image URL is invalid')
	try {
		const url = new URL(input)
		if (url.protocol !== 'https:' || url.username || url.password) throw new Error()
		return url.toString()
	} catch {
		throw new BadRequestException('Background image URL must be a valid HTTPS URL')
	}
}

function parseLondonDateTime(input: unknown) {
	if (typeof input !== 'string') throw new BadRequestException('Enter a valid British date and time')
	const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(input)
	if (!match) throw new BadRequestException('Enter a valid British date and time')
	const localUnixMs = Date.UTC(+match[1]!, +match[2]! - 1, +match[3]!, +match[4]!, +match[5]!)
	let targetUnixMs = localUnixMs - londonOffsetAt(localUnixMs)
	targetUnixMs = localUnixMs - londonOffsetAt(targetUnixMs)
	if (formatLondon(targetUnixMs) !== input) throw new BadRequestException('That time does not exist in British time')
	return targetUnixMs
}

function londonOffsetAt(timestamp: number) {
	const parts = londonParts(timestamp)
	return Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second) - timestamp
}

function nextLondonMidnight(timestamp: number) {
	const { year, month, day } = londonParts(timestamp)
	const nextDay = new Date(Date.UTC(year, month - 1, day + 1))
	return parseLondonDateTime(`${nextDay.getUTCFullYear()}-${two(nextDay.getUTCMonth() + 1)}-${two(nextDay.getUTCDate())}T00:00`)
}

function formatLondon(timestamp: number) {
	const { year, month, day, hour, minute } = londonParts(timestamp)
	return `${year}-${two(month)}-${two(day)}T${two(hour)}:${two(minute)}`
}

function londonParts(timestamp: number) {
	const values = Object.fromEntries(LONDON_TIME.formatToParts(new Date(timestamp)).map((part) => [part.type, part.value]))
	return {
		year: Number(values.year),
		month: Number(values.month),
		day: Number(values.day),
		hour: Number(values.hour),
		minute: Number(values.minute),
		second: Number(values.second),
	}
}

function two(value: number) {
	return String(value).padStart(2, '0')
}
