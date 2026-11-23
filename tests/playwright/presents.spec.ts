import { expect, test } from '@playwright/test';

test('a player can compose a letter and later open a present', async ({ page }) => {
	let receiving = false;
	let draft: Record<string, unknown> | null = null;
	let opened = false;
	let claimed = false;
	const letter = {
		id: 'letter-1',
		from: 'Alice',
		to: 'Bob',
		title: 'A small surprise',
		anonymous: false,
		itemName: 'Red present',
		itemCount: 4,
		status: 'sent',
		opened: false,
		sentAt: Date.UTC(2026, 11, 1),
		claimedAt: null,
		adminReason: null,
	};
	const state = () => ({
		visible: true,
		clock: {
			now: Date.UTC(2026, 11, receiving ? 25 : 1, receiving ? 9 : 12),
			simulatedAt: null,
			sending: !receiving,
			receiving,
		},
		draft,
		incoming: receiving
			? [
					{
						...letter,
						itemName: opened ? letter.itemName : null,
						itemCount: opened ? 4 : null,
						opened,
						status: claimed ? 'claimed' : 'sent',
					},
				]
			: [],
		sent: [],
		returned: [],
		pendingReturns: [],
		remainingToday: 3,
		players: [{ id: 2, minecraftUsername: 'Bob', color: '#ffffff' }],
	});
	await page.route('**/api/**', async (route) => {
		const url = new URL(route.request().url());
		const path = url.pathname;
		const body = route.request().postData()
			? (route.request().postDataJSON() as Record<string, unknown>)
			: null;
		let json: unknown = [];
		if (path === '/api/auth/me')
			json = {
				user: {
					id: 1,
					minecraftUsername: 'Alice',
					skinUrl: null,
					color: '#ffffff',
					isMember: true,
					canBecomeMember: false,
					isCommittee: false,
					isSuperAdmin: false,
					customEmojis: [],
				},
			};
		else if (path === '/api/presents/draft' && route.request().method() === 'PATCH') {
			draft = {
				...letter,
				id: 'draft-1',
				status: 'draft',
				itemName: draft?.itemName ?? null,
				itemCount: draft?.itemCount ?? null,
				to: body?.recipient ?? null,
				title: body?.title ?? '',
				anonymous: body?.anonymous ?? false,
			};
			json = state();
		} else if (path === '/api/presents/draft/capture') {
			draft = { ...draft, itemName: 'Red present', itemCount: 4 };
			json = state();
		} else if (path === '/api/presents/draft/send') {
			draft = null;
			json = state();
		} else if (path === '/api/presents/preview')
			json = { itemName: 'Red present', itemCount: 4 };
		else if (path === '/api/presents/letter-1/open') {
			opened = true;
			json = { firstOpen: true, present: state().incoming[0] };
		} else if (path === '/api/presents/letter-1/claim') {
			claimed = true;
			json = state();
		} else if (path === '/api/presents') json = state();
		else if (path === '/api/velocity/capacity') json = { servers: [] };
		else if (path === '/api/velocity/online-players') json = { players: [] };
		else if (path === '/api/countdowns') json = { countdowns: [] };
		else if (path === '/api/ducks/availability') json = { enabled: false };
		else if (path === '/api/big-boss/availability') json = { enabled: false };
		await route.fulfill({ json });
	});

	await page.goto('/play/presents');
	await expect(
		page.getByRole('heading', { name: 'Send a little Christmas magic' }),
	).toBeVisible();
	await page.getByPlaceholder('Choose a player').fill('Bob');
	await page.getByPlaceholder('Letter from you').fill('A small surprise');
	await page.getByRole('button', { name: 'Insert full stack' }).click();
	await expect(page.getByText('Red present × 4 is inside the letter.')).toBeVisible();
	await page.screenshot({ path: 'tests/test-results/presents-compose.png', fullPage: true });
	await page.getByRole('button', { name: 'Close letter' }).click();
	await page.getByRole('button', { name: 'Stamp and send' }).click();
	await expect(page.getByText('Your letter is on its way.')).toBeVisible();

	receiving = true;
	await page.reload();
	await expect(page.getByRole('heading', { name: 'Christmas presents are here!' })).toBeVisible();
	await page.getByRole('button', { name: 'Open letter' }).click();
	await expect(page.getByText('Red present × 4')).toBeVisible();
	await page.getByRole('button', { name: 'Receive gift' }).click();
	await expect(page.getByText('Received in Minecraft')).toBeVisible();
	await page.screenshot({ path: 'tests/test-results/presents-received.png', fullPage: true });
	await page.setViewportSize({ width: 390, height: 844 });
	await page.reload();
	await expect(page.getByRole('heading', { name: 'Christmas presents are here!' })).toBeVisible();
	await expect
		.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
		.toBeLessThanOrEqual(390);
	await page.screenshot({ path: 'tests/test-results/presents-mobile.png', fullPage: true });
});
