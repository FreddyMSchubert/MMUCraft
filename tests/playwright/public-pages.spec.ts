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
