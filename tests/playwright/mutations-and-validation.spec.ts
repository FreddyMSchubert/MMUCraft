import { expect, test } from '@playwright/test';

const memberCookie = { Cookie: 'mcstack_session=playwright-member' };
const adminCookie = { Cookie: 'mcstack_session=playwright-admin' };

test('committee can create, move, update, and remove a countdown', async ({ request }) => {
	let countdownId = 0;
	const target = '2100-01-02T12:00';

	await test.step('Create a complete countdown', async () => {
		const response = await request.post('/api/admin/countdowns', {
			headers: adminCookie,
			data: {
				heading: 'Playwright countdown',
				target,
				description: 'Created by the endpoint test',
				headingColor: '#ffffff',
				descriptionColor: '#dddddd',
				backgroundColor: '#112233',
				backgroundAlpha: 75,
				backgroundImageUrl: null,
			},
		});
		await expect(response).toBeOK();
		const body = await response.json();
		countdownId = body.id;
		expect(body).toMatchObject({ heading: 'Playwright countdown', backgroundAlpha: 75 });
	});

	await test.step('Move the new countdown above the fixture', async () => {
		const response = await request.patch(`/api/admin/countdowns/${countdownId}/order`, {
			headers: adminCookie,
			data: { direction: 'up' },
		});
		await expect(response).toBeOK();
		const body = await response.json();
		expect(body.countdowns[0].id).toBe(countdownId);
	});

	await test.step('Update the new countdown', async () => {
		const response = await request.patch(`/api/admin/countdowns/${countdownId}`, {
			headers: adminCookie,
			data: {
				heading: 'Updated Playwright countdown',
				target,
				description: 'Updated by the endpoint test',
				headingColor: '#ffffff',
				descriptionColor: '#dddddd',
				backgroundColor: '#445566',
				backgroundAlpha: 60,
				backgroundImageUrl: 'https://example.com/background.png',
			},
		});
		await expect(response).toBeOK();
		expect(await response.json()).toMatchObject({
			heading: 'Updated Playwright countdown',
			backgroundColor: '#445566',
		});
	});

	await test.step('Remove the new countdown', async () => {
		const response = await request.delete(`/api/admin/countdowns/${countdownId}`, {
			headers: adminCookie,
		});
		await expect(response).toBeOK();
		expect(await response.json()).toEqual({ ok: true });
	});
});

test('trust boundaries reject invalid input and insufficient privilege', async ({ request }) => {
	await test.step('Reject a signup outside the configured allowlist', async () => {
		const response = await request.post('/api/auth/signup', {
			data: { email: 'attacker@example.com' },
		});
		expect(response.status()).toBe(403);
	});

	await test.step('Reject an unknown sign-in account', async () => {
		const response = await request.post('/api/auth/signin', {
			data: { email: 'missing@mmu.ac.uk' },
		});
		expect(response.status()).toBe(401);
	});

	await test.step('Reject invalid authentication flow identifiers', async () => {
		const email = await request.post('/api/auth/verify-email', {
			data: { flowId: 'missing', code: 'wrong' },
		});
		const minecraft = await request.post('/api/auth/verify-minecraft', {
			data: { flowId: 'missing', code: 'wrong' },
		});
		expect(email.status()).toBe(400);
		expect(minecraft.status()).toBe(400);
	});

	await test.step('Reject malformed player and claim identifiers', async () => {
		const player = await request.get('/api/players/not-a-number', { headers: memberCookie });
		const claim = await request.patch('/api/claims/missing/appearance', {
			headers: memberCookie,
			data: { name: '', color: 'red' },
		});
		expect(player.status()).toBe(404);
		expect(claim.status()).toBe(404);
	});

	await test.step('Reject invalid knowledge, shop, and gift input', async () => {
		const knowledge = await request.post('/api/knowledge/read', {
			headers: memberCookie,
			data: { knowledgeId: 'missing-page' },
		});
		const shop = await request.post('/api/shop/purchase', {
			headers: memberCookie,
			data: {},
		});
		const gift = await request.post('/api/gift-codes/redeem', {
			headers: memberCookie,
			data: {},
		});
		expect(knowledge.status()).toBe(400);
		expect(shop.status()).toBe(400);
		expect(gift.status()).toBe(400);
	});

	await test.step('Reject committee endpoints for a regular member', async () => {
		const committee = await request.get('/api/admin/players', { headers: memberCookie });
		const superAdmin = await request.patch('/api/admin/players/3/committee', {
			headers: memberCookie,
			data: { isCommittee: true },
		});
		expect(committee.status()).toBe(403);
		expect(superAdmin.status()).toBe(403);
	});
});
