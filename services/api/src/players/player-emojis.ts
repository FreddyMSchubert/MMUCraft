import { BadRequestException } from '@nestjs/common';

export interface PlayerEmoji {
	emoji: string;
	explanation: string;
	showInName: boolean;
}

const MAX_EMOJIS = 8;
const MAX_EMOJI_LENGTH = 16;
const MAX_EXPLANATION_LENGTH = 80;

export function customPlayerEmojis(customJson?: string | null): PlayerEmoji[] {
	if (customJson !== null && customJson !== undefined) {
		try {
			const parsed: unknown = JSON.parse(customJson);
			if (Array.isArray(parsed)) return parsed as PlayerEmoji[];
		} catch {
			// Ignore invalid stored data.
		}
	}
	return [];
}

export function normalizeCustomEmojis(input: unknown): string | null {
	if (input === null) return null;
	if (!Array.isArray(input) || input.length > MAX_EMOJIS) {
		throw new BadRequestException(`Emojis must be a list of at most ${MAX_EMOJIS} entries.`);
	}

	const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' });
	const emojis = input.map((entry, index) => {
		if (!entry || typeof entry !== 'object') throw invalidEntry(index);
		const { emoji, explanation, showInName } = entry as Record<string, unknown>;
		if (
			typeof emoji !== 'string' ||
			typeof explanation !== 'string' ||
			typeof showInName !== 'boolean'
		) {
			throw invalidEntry(index);
		}
		const symbol = emoji.trim();
		const text = explanation.trim();
		const isSingleGrapheme = [...segmenter.segment(symbol)].length === 1;
		const looksLikeEmoji = /\p{Extended_Pictographic}|\p{Regional_Indicator}|\u20e3/u.test(
			symbol,
		);
		if (
			!symbol ||
			symbol.length > MAX_EMOJI_LENGTH ||
			!isSingleGrapheme ||
			!looksLikeEmoji ||
			!text ||
			text.length > MAX_EXPLANATION_LENGTH ||
			hasControlCharacter(text)
		) {
			throw invalidEntry(index);
		}
		return { emoji: symbol, explanation: text, showInName };
	});
	return JSON.stringify(emojis);
}

function hasControlCharacter(value: string) {
	for (const character of value) {
		const codePoint = character.codePointAt(0) ?? 0;
		if (codePoint < 32 || codePoint === 127) return true;
	}
	return false;
}

function invalidEntry(index: number) {
	return new BadRequestException(
		`Emoji ${index + 1} must contain one emoji, a 1-${MAX_EXPLANATION_LENGTH} character explanation and a name visibility setting.`,
	);
}
