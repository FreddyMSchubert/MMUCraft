import { expect, test } from '@playwright/test';

test('public pages and the API proxy are available', async ({ page, request }) => {
	await test.step('Open the landing page', async () => {
		await page.goto('/');
		await expect(page.getByRole('main')).toBeVisible();
		await expect(page.getByRole('navigation', { name: 'Main links' })).toBeVisible();
	});

	await test.step('Open the daily Wordle', async () => {
		await page.goto('/wordle');
		await expect(
			page.getByRole('heading', { name: 'MMU Minecraft Society Wordle' }),
		).toBeVisible();
		await expect(page.getByLabel('Daily word puzzle')).toBeVisible();
	});

	await test.step('Open the launch page', async () => {
		await page.goto('/countdown');
		await expect(
			page.getByRole('heading', { name: /We're live!|Server launch/ }),
		).toBeVisible();
	});

	await test.step('Proxy the API health check through Next.js', async () => {
		const response = await request.get('/api/health');
		await expect(response).toBeOK();
		expect(await response.json()).toEqual({ ok: true });
	});

	await test.step('Return the configured public launch time', async () => {
		const response = await request.get('/api/launch');
		await expect(response).toBeOK();
		expect(await response.json()).toEqual({ launchAtUnixMs: 1_790_708_400_000 });
	});

	await test.step('Return public countdown data from the fixture', async () => {
		const response = await request.get('/api/countdowns');
		await expect(response).toBeOK();
		expect(await response.json()).toMatchObject({
			countdowns: [{ heading: 'Fixture countdown' }],
		});
	});
});

test('protected endpoints reject anonymous requests', async ({ request }) => {
	await test.step('Report no current user without a cookie', async () => {
		const response = await request.get('/api/auth/me');
		await expect(response).toBeOK();
		expect(await response.json()).toEqual({ user: null });
	});

	await test.step('Reject an anonymous player-list request', async () => {
		const response = await request.get('/api/players');
		expect(response.status()).toBe(401);
	});

	await test.step('Reject an anonymous committee request', async () => {
		const response = await request.get('/api/admin/players');
		expect(response.status()).toBe(401);
	});
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
	await page.route('**/api/fishing/compendium', async (route) => {
		await route.fulfill({
			json: { currentUserId: 1, selectedUserId: 1, players: [], fish: [] },
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
	await expect(page.locator('.dashboard')).toHaveCSS('background-color', 'rgb(255, 255, 255)');
	await expect(page.locator('.dashboard')).toHaveCSS('color', 'rgb(0, 0, 0)');
	await expect(page.locator('.dashboard')).toHaveCSS('border-color', 'rgb(0, 0, 0)');
	await expect(page.locator('.dashboard')).toHaveCSS('font-family', /OpenDyslexic/);
	await page.goto('/play/shop');
	const tags = page.getByRole('group', { name: 'Tags filter' });
	const allTags = tags.getByRole('button', { name: 'All' });
	const dyeable = tags.getByRole('button', { name: 'Dyeable' });
	await expect(allTags).toHaveCSS('background-color', 'rgb(0, 0, 0)');
	await dyeable.click();
	await expect(dyeable).toHaveCSS('background-color', 'rgb(0, 0, 0)');
	await expect(dyeable).toHaveCSS('color', 'rgb(255, 255, 255)');
	await expect(allTags).toHaveCSS('background-color', 'rgb(255, 255, 255)');
	await expect(page.locator('.emissiveSparks').first()).toHaveCSS('display', 'none');
	await page.goto('/play/fishing');
	await expect(page.getByRole('heading', { name: 'Fish Compendium' })).toBeVisible();
	await expect(page.locator('.fishFireworks')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');

	await page.goto('/wordle');
	await expect(page.locator('html')).toHaveAttribute('data-dyslexia-mode', '');
	await expect(page.locator('.fireworksStage')).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
	await page.reload();
	await expect(page.locator('html')).toHaveAttribute('data-dyslexia-mode', '');
	await page.goto('/');
	await expect(page.locator('.panoramaFace').first()).toHaveCSS('background-image', 'none');
	await expect(page.locator('.minecraftButton').first()).toHaveCSS('border-image-source', 'none');

	await page.goto('/play/misc/settings');
	await toggle.click();
	await expect(page.locator('html')).not.toHaveAttribute('data-dyslexia-mode');
	await expect(page.locator('.siteBackground')).not.toHaveCSS('background-image', 'none');
});
