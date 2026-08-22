import { BadRequestException } from '@nestjs/common';
import { normalizeOptionalColor } from './player-color';
import { normalizeNullableInteger } from './player-statistics';

export const PROFILE_TEXT_LIMITS = {
	preferredName: 16,
	pronouns: 16,
	courseYear: 64,
	discordUsername: 40,
	bio: 280,
} as const;

export interface PlayerProfile {
	preferredName: string;
	pronouns: string;
	courseYear: string;
	discordUsername: string;
	base: {
		x: number | null;
		y: number | null;
		z: number | null;
	};
	bio: string;
	color: string;
	defaultColor: string;
	customColor: string | null;
	showDeathCounter: boolean;
	updatedAtUnixMs: number;
}

export interface PlayerProfileInput {
	preferredName?: string;
	pronouns?: string;
	courseYear?: string;
	discordUsername?: string;
	baseX?: number | null;
	baseY?: number | null;
	baseZ?: number | null;
	bio?: string;
	color?: string | null;
	showDeathCounter?: boolean;
}

export function normalizeProfileInput(
	input: PlayerProfileInput,
	currentShowDeathCounter: boolean,
): PlayerProfile {
	if (input.showDeathCounter !== undefined && typeof input.showDeathCounter !== 'boolean') {
		throw new BadRequestException('Show death counter must be true or false');
	}

	return {
		preferredName: profileText(
			input.preferredName,
			PROFILE_TEXT_LIMITS.preferredName,
			'Nickname',
		),
		pronouns: profileText(input.pronouns, PROFILE_TEXT_LIMITS.pronouns, 'Pronouns'),
		courseYear: profileText(input.courseYear, PROFILE_TEXT_LIMITS.courseYear, 'Course / Year'),
		discordUsername: profileText(
			input.discordUsername,
			PROFILE_TEXT_LIMITS.discordUsername,
			'Discord username',
		),
		base: {
			x: coordinate(input.baseX, 'Base X'),
			y: coordinate(input.baseY, 'Base Y'),
			z: coordinate(input.baseZ, 'Base Z'),
		},
		bio: profileText(input.bio, PROFILE_TEXT_LIMITS.bio, 'Bio'),
		color: '',
		defaultColor: '',
		customColor: normalizeOptionalColor(input.color),
		showDeathCounter: input.showDeathCounter ?? currentShowDeathCounter,
		updatedAtUnixMs: Date.now(),
	};
}

function coordinate(value: number | null | undefined, label: string): number | null {
	const coordinate = normalizeNullableInteger(value);
	if (coordinate === null) return null;
	if (Math.abs(coordinate) > 30_000_000) {
		throw new BadRequestException(`${label} is outside Minecraft world bounds.`);
	}
	return coordinate;
}

function profileText(value: string | undefined, maxLength: number, label: string): string {
	if (typeof value !== 'string') return '';
	const trimmed = value.trim();
	if (trimmed.length > maxLength) {
		throw new BadRequestException(`${label} must be ${maxLength} characters or fewer.`);
	}
	return trimmed;
}
