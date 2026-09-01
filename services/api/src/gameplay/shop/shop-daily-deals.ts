import type { CatalogItem } from './shop-item-catalog.service';

const BRITISH_MONTH_AND_DAY = new Intl.DateTimeFormat('en-GB', {
	timeZone: 'Europe/London',
	month: '2-digit',
	day: '2-digit',
});

const DAILY_DEAL_MESSAGES = [
	'What a steal!',
	'Limited time offer',
	'While stocks last',
	'Best seller',
	'New arrival',
	'Must-have',
	'Amazing deal',
	'Nothing beats a {item}',
	"Today's treasure",
	'A proper bargain',
	"Blink and you'll miss it",
	'Worth every dabloon',
	'A deal this good is rare',
	'The price is right',
	'Too good to leave behind',
	'Limited-time deal',
	"Treat yo' self",
	'Shut up and take my Dabloons!',
	'Anything from the trolley, dears?',
	"We'll take the lot.",
	"Buy somethin', will ya!",
	'I am never going to financially recover from this.',
	"Let's go shopping!",
	'Capitalism, baby!',
	"It's our best-seller.",
	'A fool and her Dabloons soon part.',
] as const;

export function currentShopDealDate(): string {
	return new Date().toISOString().slice(0, 10);
}

export function isShoppingSunday(dealDate: string): boolean {
	return new Date(`${dealDate}T00:00:00Z`).getUTCDay() === 0;
}

export function dailyDealItemIds(items: CatalogItem[], dealDate: string): Set<string> {
	const dealCount = isShoppingSunday(dealDate) ? 16 : 8;
	return new Set(
		[...items]
			.sort(
				(left, right) =>
					deterministicRank(`${dealDate}:${left.id}`) -
					deterministicRank(`${dealDate}:${right.id}`),
			)
			.slice(0, Math.min(dealCount, items.length))
			.map((item) => item.id),
	);
}

export function dailyDealDiscountPercent(itemId: string, dealDate: string): number {
	const sunday = isShoppingSunday(dealDate);
	const minimum = sunday ? 20 : 10;
	const maximum = sunday ? 50 : 30;
	return (
		minimum + (deterministicRank(`${dealDate}:discount:${itemId}`) % (maximum - minimum + 1))
	);
}

export function dailyDealMessage(item: CatalogItem, dealDate: string): string {
	const index = deterministicRank(`${dealDate}:message:${item.id}`) % DAILY_DEAL_MESSAGES.length;
	return (DAILY_DEAL_MESSAGES[index] ?? DAILY_DEAL_MESSAGES[0]).replaceAll('{item}', item.title);
}

export function discountedShopPrice(price: number, discountPercent: number): number {
	return Math.max(1, Math.floor(price * (1 - discountPercent / 100)));
}

export function isBritishAnniversary(createdAtUnixMs: number, nowUnixMs: number): boolean {
	return (
		BRITISH_MONTH_AND_DAY.format(createdAtUnixMs) === BRITISH_MONTH_AND_DAY.format(nowUnixMs)
	);
}

export function shopDiscountPercent(
	itemId: string,
	signupAnniversary: boolean,
	dailyDiscountPercent: number,
): number {
	return itemId === 'charm-wallet' && signupAnniversary ? 42 : dailyDiscountPercent;
}

function deterministicRank(value: string): number {
	let hash = 2166136261;
	for (let index = 0; index < value.length; index++) {
		hash ^= value.charCodeAt(index);
		hash = Math.imul(hash, 16777619);
	}
	return hash >>> 0;
}
