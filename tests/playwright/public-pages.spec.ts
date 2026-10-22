import { expect, test } from '@playwright/test';

test('landing page and the API proxy are available', async ({ page, request }) => {
	await test.step('Open the landing page', async () => {
		await page.goto('/');
		await expect(page.getByRole('main')).toBeVisible();
		await expect(page.getByRole('navigation', { name: 'Main links' })).toBeVisible();
	});

	await test.step('Proxy the API health check through Next.js', async () => {
		const response = await request.get('/api/health');
		await expect(response).toBeOK();
		expect(await response.json()).toEqual({ ok: true });
	});
});

test('protected endpoints reject anonymous requests', async ({ request }) => {
	await test.step('Reject an anonymous player-list request', async () => {
		const response = await request.get('/api/players');
		expect(response.status()).toBe(401);
	});

	await test.step('Reject an anonymous committee request', async () => {
		const response = await request.get('/api/admin/players');
		expect(response.status()).toBe(401);
	});
});

test('dashboard tabs fit by hiding older labels, including committee tabs', async ({ page }) => {
	await page.route('**/api/auth/me', async (route) => {
		await route.fulfill({
			json: {
				user: {
					id: 1,
					minecraftUsername: 'TestPlayer',
					skinUrl: null,
					color: '#ffffff',
					isMember: true,
					isCommittee: true,
					isSuperAdmin: false,
					customEmojis: [],
				},
			},
		});
	});
	await page.route('**/api/ducks/availability', async (route) => {
		await route.fulfill({ json: { enabled: true } });
	});
	await page.route('**/api/surprising-saturday', async (route) => {
		await route.fulfill({ json: [] });
	});
	await page.setViewportSize({ width: 1400, height: 900 });
	await page.goto('/play/knowledge');
	const nav = page.getByRole('navigation', { name: 'Dashboard sections' });
	await expect(nav.locator('[data-tab-id="ducks"]')).toBeVisible();
	await expect(nav.locator('[data-tab-id="admin"]')).toBeVisible();
	await expect(page.locator('.playPage .content')).toHaveCSS('width', '1100px');
	const wideCompactCount = await nav.locator('a.compact').count();
	const misc = nav.locator('[data-tab-id="misc"]');
	await page.setViewportSize({ width: 900, height: 900 });
	await expect(misc).toHaveClass(/compact/);

	await page.setViewportSize({ width: 760, height: 900 });
	await expect.poll(() => nav.locator('a.compact').count()).toBeGreaterThan(wideCompactCount);
	await expect(misc).toHaveAttribute('title', '⚙️ Misc');
	await expect(nav).toHaveCSS('flex-wrap', 'nowrap');
	await expect(nav).toHaveCSS('overflow-x', 'auto');

	await misc.click();
	await expect(misc).not.toHaveClass(/compact/);
	await expect(misc.locator('.dashboardTabLabel')).toBeVisible();
	await expect(misc).toHaveAttribute('aria-current', 'page');
	await expect
		.poll(() =>
			page.evaluate(() =>
				JSON.parse(localStorage.getItem('mmu-dashboard-recent-tabs-v1-1') ?? '[]'),
			),
		)
		.toEqual(['misc']);
	await page.setViewportSize({ width: 900, height: 900 });
	await page.goto('/play/knowledge');
	await expect(misc).not.toHaveClass(/compact/);

	await page.setViewportSize({ width: 701, height: 900 });
	await expect(nav).toHaveCSS('flex-wrap', 'nowrap');
	await page.setViewportSize({ width: 600, height: 900 });
	await page.getByRole('button', { name: 'Open menu' }).click();
	await expect(nav.locator('[data-tab-id="event"] .dashboardTabLabel')).toBeVisible();
	await expect(nav).toHaveCSS('flex-wrap', 'wrap');
	await nav.locator('[data-tab-id="event"]').click();
	await expect
		.poll(() =>
			page.evaluate(() =>
				JSON.parse(localStorage.getItem('mmu-dashboard-recent-tabs-v1-1') ?? '[]'),
			),
		)
		.toEqual(['misc']);
});

test('dyslexia mode applies across pages and survives a reload', async ({ page }) => {
	await page.route('**/api/auth/me', async (route) => {
		await route.fulfill({
			json: {
				user: {
					id: 1,
					minecraftUsername: 'TestPlayer',
					skinUrl: null,
					color: '#ffffff',
					isMember: true,
					isCommittee: false,
					isSuperAdmin: false,
					customEmojis: [],
				},
			},
		});
	});
	await page.route('**/api/shop', async (route) => {
		await route.fulfill({
			json: {
				isMember: true,
				drops: [],
				dealDate: '2026-10-05',
				shoppingSunday: false,
				availability: { knowledge: true, charms: true, cosmetics: true },
				items: [],
			},
		});
	});
	await page.goto('/play/misc/settings');
	const toggle = page.getByText('Dyslexia mode');
	await expect(toggle).toBeVisible();
	await expect(page.locator('html')).not.toHaveAttribute('data-dyslexia-mode');
	await expect(page.locator('.siteBackground')).not.toHaveCSS('background-image', 'none');

	await toggle.click();
	await expect(page.locator('html')).toHaveAttribute('data-dyslexia-mode', '');
	await expect(page.locator('.siteBackground')).toHaveCSS('background-image', 'none');
	await expect(page.locator('.dashboard')).toHaveCSS('font-family', /OpenDyslexic/);
	await page.goto('/play/shop');
	await expect(page.locator('html')).toHaveAttribute('data-dyslexia-mode', '');
	await page.reload();
	await expect(page.locator('html')).toHaveAttribute('data-dyslexia-mode', '');
	await page.goto('/play/misc/settings');
	await toggle.click();
	await expect(page.locator('html')).not.toHaveAttribute('data-dyslexia-mode');
	await expect(page.locator('.siteBackground')).not.toHaveCSS('background-image', 'none');
});
