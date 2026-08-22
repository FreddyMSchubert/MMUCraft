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
