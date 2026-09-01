export const DABLOON_CODEPOINT = 0xf0dab;
export const DABLOON_SYMBOL = String.fromCodePoint(DABLOON_CODEPOINT);

export function formatDabloons(value: number) {
	return `${DABLOON_SYMBOL}${value.toLocaleString('en-US')}`;
}
