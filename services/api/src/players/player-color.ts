import { BadRequestException } from '@nestjs/common'

const COLOR_PATTERN = /^#[0-9a-f]{6}$/i

export function normalizeOptionalColor(value: unknown, label = 'Color'): string | null {
	if (value === null) return null
	if (typeof value !== 'string' || !COLOR_PATTERN.test(value)) {
		throw new BadRequestException(`${label} must be a full hex color such as #FFD166.`)
	}
	return value.toUpperCase()
}

export function effectivePlayerColor(minecraftUuid: string | null, customColor?: string | null): string {
	if (customColor && COLOR_PATTERN.test(customColor)) return customColor.toUpperCase()
	const compact = minecraftUuid?.replaceAll('-', '') ?? ''
	if (!/^[0-9a-f]{32}$/i.test(compact)) return '#E6E6E6'

	const most = BigInt(`0x${compact.slice(0, 16)}`)
	const least = BigInt(`0x${compact.slice(16)}`)
	const hash = Number(BigInt.asIntN(32, (most >> 32n) ^ most ^ (least >> 32n) ^ least))
	return setBrightness(hash & 0xFFFFFF, 0.9)
}

export function playerAvatarUrl(minecraftUuid: string | null): string | null {
	const uuid = minecraftUuid?.replaceAll('-', '').toLowerCase() ?? ''
	if (!/^[0-9a-f]{32}$/.test(uuid)) return null
	return `/api/players/avatar/${uuid}.png`
}

function setBrightness(rgb: number, brightness: number) {
	const red = rgb >> 16 & 0xFF
	const green = rgb >> 8 & 0xFF
	const blue = rgb & 0xFF
	const max = Math.max(red, green, blue)
	const delta = max - Math.min(red, green, blue)
	const saturation = max === 0 ? 0 : delta / max
	let hue = 0

	if (saturation !== 0) {
		const redDistance = (max - red) / delta
		const greenDistance = (max - green) / delta
		const blueDistance = (max - blue) / delta
		hue = (red === max ? blueDistance - greenDistance
			: green === max ? 2 + redDistance - blueDistance
				: 4 + greenDistance - redDistance) / 6
		if (hue < 0) hue++
	}

	if (saturation === 0) return hex(Math.round(brightness * 255), Math.round(brightness * 255), Math.round(brightness * 255))
	const sector = (hue - Math.floor(hue)) * 6
	const fraction = sector - Math.floor(sector)
	const low = brightness * (1 - saturation)
	const falling = brightness * (1 - saturation * fraction)
	const rising = brightness * (1 - saturation * (1 - fraction))
	const channels = [
		[brightness, rising, low], [falling, brightness, low], [low, brightness, rising],
		[low, falling, brightness], [rising, low, brightness], [brightness, low, falling],
	][Math.floor(sector)] ?? [brightness, brightness, brightness]
	return hex(...channels.map((channel) => Math.round(channel * 255)) as [number, number, number])
}

function hex(red: number, green: number, blue: number) {
	return `#${[red, green, blue].map((channel) => channel.toString(16).padStart(2, '0')).join('').toUpperCase()}`
}
