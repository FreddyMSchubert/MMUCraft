export const DABLOON_CODEPOINT = 0xf0dab;
export const DABLOON_SYMBOL = String.fromCodePoint(DABLOON_CODEPOINT);

export function formatDabloons(value: number) {
	return `${formatDabloonNumber(value)} ${DABLOON_SYMBOL}`;
}

export function formatDabloonWord(value: number) {
	return `${formatDabloons(value)}${value === 1 ? 'abloon' : 'abloons'}`;
}

export function formatDabloonDelta(value: number) {
	const sign = value < 0 ? '−' : '+';
	return `(${sign}${formatDabloons(Math.abs(value))})`;
}

export function dabloonizeWords(value: string) {
	return value.replace(/\bdabloons?\b/gi, (word) => `${DABLOON_SYMBOL}${word.slice(1)}`);
}

function formatDabloonNumber(value: number) {
	return value.toLocaleString('en-US');
}
