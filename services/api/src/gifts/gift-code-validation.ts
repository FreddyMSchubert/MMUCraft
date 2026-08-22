import { BadRequestException } from '@nestjs/common';

const GIFT_CODE_PATTERN = /^[A-Za-z0-9_.-]+$/;
const MAX_GIFT_CODE_LENGTH = 64;
const MAX_DABLOONS = 2_147_483_647;

export interface GiftCodeInput {
	code?: string;
	amountDabloons?: number;
	redemptionMode?: 'single' | 'per_user';
	membersOnly?: boolean;
	expiresAtUnixMs?: number | null;
}

export function normalizeGiftCode(value: string | undefined) {
	if (typeof value !== 'string') {
		throw new BadRequestException('Gift code is required');
	}

	const code = value.trim().toLowerCase();
	if (!code || code.length > MAX_GIFT_CODE_LENGTH || !GIFT_CODE_PATTERN.test(code)) {
		throw new BadRequestException(
			'Gift code must be 1-64 characters using only letters, numbers, -, _ or .',
		);
	}
	return code;
}

export function normalizeGiftCodeAmount(value: number | undefined) {
	if (
		typeof value !== 'number' ||
		!Number.isInteger(value) ||
		value <= 0 ||
		value > MAX_DABLOONS
	) {
		throw new BadRequestException(
			`Amount must be a whole number between 1 and ${MAX_DABLOONS}`,
		);
	}
	return value;
}

export function normalizeGiftCodeRedemptionMode(
	value: GiftCodeInput['redemptionMode'],
): 'single' | 'per_user' {
	if (value === 'single' || value === 'per_user') return value;
	throw new BadRequestException('Redemption mode must be single or per_user');
}

export function normalizeGiftCodeMembersOnly(value: boolean | undefined) {
	if (value === undefined || value === false) return false;
	if (value === true) return true;
	throw new BadRequestException('membersOnly must be a boolean');
}

export function normalizeGiftCodeExpiry(
	value: number | null | undefined,
	now: number,
): number | null {
	if (value === null || value === undefined) return null;
	if (!Number.isSafeInteger(value) || value <= now) {
		throw new BadRequestException('Expiry must be a valid date and time in the future');
	}
	return value;
}
