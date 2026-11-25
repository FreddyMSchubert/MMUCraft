import { expect, test } from '@playwright/test';

test('a player can compose a letter and later open a present', async ({ page }) => {
	test.setTimeout(60_000);
	await page.addInitScript(() => {
		const original = window.setInterval.bind(window);
		window.setInterval = ((handler: TimerHandler, timeout?: number, ...args: unknown[]) =>
			original(
				handler,
				timeout === 30_000 ? 3_000 : timeout,
				...args,
			)) as typeof window.setInterval;
	});
	let receiving = false;
	let draft: Record<string, unknown> | null = null;
	let opened = false;
	let claimed = false;
	let sent = false;
	const letter = {
		id: 'letter-1',
		from: 'Alice',
		to: 'Bob',
		title: 'A small surprise',
		body: 'Merry Christmas, Bob!',
		color: 'green',
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
		sent: sent ? [letter] : [],
		returned: [],
		pendingReturns: [],
		players: [{ id: 2, minecraftUsername: 'Bob', nickname: 'Bobby', color: '#ffffff' }],
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
				body: body?.body ?? '',
				color: body?.color ?? 'red',
				anonymous: body?.anonymous ?? false,
			};
			json = state();
		} else if (path === '/api/presents/draft/capture') {
			draft = { ...draft, itemName: 'Red present', itemCount: 4 };
			json = state();
		} else if (path === '/api/presents/draft/send') {
			sent = true;
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

	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto('/play/presents');
	await expect(
		page.getByRole('heading', { name: 'Send a little Christmas magic' }),
	).toBeVisible();
	await expect(page.getByRole('button', { name: 'Write a letter' })).toBeVisible();
	await expect(page.getByRole('heading', { name: 'Letters you sent' })).toHaveCount(0);
	await page.screenshot({ path: 'tests/test-results/presents-send-page.png', fullPage: true });
	await page.getByRole('button', { name: 'Write a letter' }).click();
	await expect(page.getByRole('button', { name: 'Open the letter to write' })).toBeVisible();
	await page.screenshot({ path: 'tests/test-results/presents-envelope-mobile.png' });
	await page.getByRole('button', { name: 'Open the letter to write' }).hover();
	await expect(page.getByRole('button', { name: 'Open the letter to write' })).toHaveCSS(
		'filter',
		'none',
	);
	await page.getByRole('button', { name: 'Send letter' }).click();
	await expect(page.locator('.presentComposeError')).toContainText(
		'Write a message or add a gift',
	);
	await page.getByRole('button', { name: 'Open the letter to write' }).click();
	await expect(page.locator('.presentEnvelopeView')).toBeVisible();
	await page.waitForTimeout(300);
	await page.screenshot({ path: 'tests/test-results/presents-writing-zoom.png' });
	await page.getByPlaceholder('Choose a recipient').fill('Bob');
	await page.getByRole('button', { name: 'Light blue' }).click();
	await expect(page.getByRole('button', { name: 'Next', exact: true })).toHaveCSS(
		'background-color',
		'rgb(90, 159, 186)',
	);
	await page.getByRole('button', { name: 'Next', exact: true }).click();
	await page.getByPlaceholder('Letter from you').fill('A small surprise');
	await page.getByPlaceholder('Write your letter here...').fill('Merry Christmas, Bob!');
	await page.waitForTimeout(650);
	await page.screenshot({
		path: 'tests/test-results/presents-message-mobile.png',
		fullPage: true,
	});
	await page.setViewportSize({ width: 320, height: 568 });
	await page.getByRole('button', { name: 'Finish writing' }).scrollIntoViewIfNeeded();
	await expect(page.getByRole('button', { name: 'Finish writing' })).toBeInViewport();
	await page.screenshot({ path: 'tests/test-results/presents-small-mobile.png' });
	await page.setViewportSize({ width: 390, height: 844 });
	await page.getByRole('button', { name: 'Next', exact: true }).click();
	await page.getByRole('button', { name: 'Add gift' }).click();
	await expect(page.getByText('Red present × 4')).toBeVisible();
	await page.screenshot({ path: 'tests/test-results/presents-compose.png', fullPage: true });
	await page.getByRole('button', { name: 'Finish writing' }).click();
	await page.waitForTimeout(350);
	await page.screenshot({ path: 'tests/test-results/presents-writing-close.png' });
	await expect(page.locator('.presentEnvelopeLetter')).toContainText('A small surprise');
	await page.getByRole('button', { name: 'Put down letter' }).click();
	await page.reload();
	await page.getByRole('button', { name: 'Write a letter' }).click();
	await expect(page.locator('.presentEnvelopeLetter')).toContainText('A small surprise');
	await expect(page.getByRole('button', { name: 'Put down letter' })).toHaveCSS(
		'background-color',
		'rgb(90, 159, 186)',
	);
	await expect(page.getByRole('button', { name: 'Send letter' })).toHaveCSS(
		'background-color',
		'rgb(90, 159, 186)',
	);
	await page.setViewportSize({ width: 1280, height: 800 });
	await page.screenshot({ path: 'tests/test-results/presents-envelope-desktop.png' });
	await page.setViewportSize({ width: 390, height: 844 });
	await page.getByRole('button', { name: 'Send letter' }).click();
	await expect(page.locator('.presentEnvelopeObject.isSending')).toBeVisible();
	await page.waitForTimeout(1350);
	await page.screenshot({ path: 'tests/test-results/presents-fold-one.png' });
	await page.waitForTimeout(1650);
	await page.screenshot({ path: 'tests/test-results/presents-fold-two.png' });
	await page.waitForTimeout(1800);
	await page.screenshot({ path: 'tests/test-results/presents-inserting.png' });
	await page.waitForTimeout(1800);
	await page.screenshot({ path: 'tests/test-results/presents-sealed.png' });
	await page.waitForTimeout(2400);
	await page.screenshot({ path: 'tests/test-results/presents-posting-mobile.png' });
	await page.waitForTimeout(1500);
	await page.screenshot({ path: 'tests/test-results/presents-flight.png' });
	await expect(page.locator('.presentComposeDialog')).not.toBeVisible({ timeout: 20000 });
	await expect(page.getByRole('heading', { name: 'Letters you sent' })).toHaveCount(0);

	receiving = true;
	await page.reload();
	await expect(page.getByRole('heading', { name: 'Christmas presents are here!' })).toBeVisible();
	await page.getByRole('button', { name: 'Open letter' }).click();
	await expect(page.getByText('Merry Christmas, Bob!')).toBeVisible();
	await expect(page.getByText('Red present × 4')).toBeVisible();
	await page.getByRole('button', { name: 'Receive gift' }).click();
	await expect(page.getByText('Gift received in Minecraft')).toBeVisible();
	await page.screenshot({ path: 'tests/test-results/presents-received.png', fullPage: true });
	await page.reload();
	await expect(page.getByRole('heading', { name: 'Christmas presents are here!' })).toBeVisible();
	await expect
		.poll(() => page.evaluate(() => document.documentElement.scrollWidth))
		.toBeLessThanOrEqual(390);
	await page.screenshot({ path: 'tests/test-results/presents-mobile.png', fullPage: true });
});
