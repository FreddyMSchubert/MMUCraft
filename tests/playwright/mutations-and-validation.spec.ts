import { expect, test } from '@playwright/test';

const memberCookie = { Cookie: 'mcstack_session=playwright-member' };
const adminCookie = { Cookie: 'mcstack_session=playwright-admin' };
const guestCookie = { Cookie: 'mcstack_session=playwright-guest' };

test('committee can configure the shared launch time', async ({ request }) => {
	const updatedTarget = '2030-01-02T12:34';
	const defaultTarget = '2026-09-29T20:00';

	const anonymous = await request.patch('/api/admin/launch', {
		data: { target: updatedTarget },
	});
	expect(anonymous.status()).toBe(401);

	const updated = await request.patch('/api/admin/launch', {
		headers: adminCookie,
		data: { target: updatedTarget },
	});
	await expect(updated).toBeOK();
	expect(await updated.json()).toEqual({
		launchAtUnixMs: Date.parse('2030-01-02T12:34:00Z'),
	});

	const publicSetting = await request.get('/api/launch');
	await expect(publicSetting).toBeOK();
	expect(await publicSetting.json()).toEqual({
		launchAtUnixMs: Date.parse('2030-01-02T12:34:00Z'),
	});

	const restored = await request.patch('/api/admin/launch', {
		headers: adminCookie,
		data: { target: defaultTarget },
	});
	await expect(restored).toBeOK();
});

test('committee can create, update, and remove a countdown in time order', async ({ request }) => {
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

	await test.step('A manual move cannot override time order', async () => {
		const response = await request.patch(`/api/admin/countdowns/${countdownId}/order`, {
			headers: adminCookie,
			data: { direction: 'up' },
		});
		await expect(response).toBeOK();
		const body = await response.json();
		expect(body.countdowns[0].id).toBe(1);
		expect(body.countdowns.at(-1).id).toBe(countdownId);
		const publicResponse = await request.get('/api/countdowns');
		await expect(publicResponse).toBeOK();
		expect((await publicResponse.json()).countdowns[0].id).toBe(1);
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

test('committee can edit another player’s claim without changing its owner', async ({
	request,
}) => {
	const url = '/api/admin/claims/playwright-claim';
	const denied = await request.patch(`${url}/appearance`, {
		headers: memberCookie,
		data: { name: 'Not allowed', color: null },
	});
	expect(denied.status()).toBe(403);
	const deniedRead = await request.get(url, { headers: memberCookie });
	expect(deniedRead.status()).toBe(403);
	const deniedMember = await request.post(`${url}/members`, {
		headers: memberCookie,
		data: { userId: 3 },
	});
	expect(deniedMember.status()).toBe(403);

	try {
		const detail = await request.get(url, { headers: adminCookie });
		await expect(detail).toBeOK();
		expect((await detail.json()).claim.members).toContainEqual(
			expect.objectContaining({ id: 2, isOwner: true }),
		);

		const appearance = await request.patch(`${url}/appearance`, {
			headers: adminCookie,
			data: { name: 'Committee edited', color: '#AABBCC' },
		});
		await expect(appearance).toBeOK();

		const ownerRejected = await request.post(`${url}/members`, {
			headers: adminCookie,
			data: { userId: 2 },
		});
		expect(ownerRejected.status()).toBe(400);

		const added = await request.post(`${url}/members`, {
			headers: adminCookie,
			data: { userId: 3 },
		});
		await expect(added).toBeOK();

		const updated = await request.get(url, { headers: adminCookie });
		await expect(updated).toBeOK();
		expect((await updated.json()).claim).toMatchObject({
			name: 'Committee edited',
			customColor: '#AABBCC',
			members: [
				expect.objectContaining({ id: 2, isOwner: true }),
				expect.objectContaining({ id: 3, isOwner: false }),
			],
		});

		const ownerRemoval = await request.delete(`${url}/members/2`, { headers: adminCookie });
		expect(ownerRemoval.status()).toBe(404);
	} finally {
		await request.delete(`${url}/members/3`, { headers: adminCookie });
		await request.patch(`${url}/appearance`, {
			headers: adminCookie,
			data: { name: 'Fixture claim', color: '#336699' },
		});
	}
});

test('a claim owner can transfer a claim and stay a member', async ({ page, request, baseURL }) => {
	const url = '/api/claims/playwright-claim';
	expect((await request.post(`${url}/transfer`, { data: { userId: 3 } })).status()).toBe(401);
	expect(
		(
			await request.post(`${url}/transfer`, { headers: guestCookie, data: { userId: 3 } })
		).status(),
	).toBe(404);
	expect(
		(
			await request.post(`${url}/transfer`, { headers: memberCookie, data: { userId: 2 } })
		).status(),
	).toBe(400);
	expect(
		(
			await request.post(`${url}/transfer`, {
				headers: memberCookie,
				data: { userId: 999999 },
			})
		).status(),
	).toBe(400);

	try {
		const added = await request.post(`${url}/members`, {
			headers: memberCookie,
			data: { userId: 3 },
		});
		if (added.status() !== 409) await expect(added).toBeOK();
		await page.context().addCookies([
			{
				name: 'mcstack_session',
				value: 'playwright-member',
				url: baseURL ?? 'http://127.0.0.1:3100',
			},
		]);
		await page.goto('/play/claims', { waitUntil: 'domcontentloaded' });
		const owned = page
			.locator('.claimsSection')
			.filter({ has: page.getByRole('heading', { name: 'My claims' }) });
		await owned.getByRole('button', { name: 'Edit' }).click();
		await owned.getByText('Transfer claim', { exact: true }).first().click();
		await expect(owned.locator('.claimTransferWarning')).toContainText('lose owner control');
		await owned.getByRole('textbox', { name: 'New claim owner' }).fill('PlaywrightGuest');
		await owned.getByRole('button', { name: 'Transfer claim' }).click();

		await expect(owned.locator('.claimCard')).toHaveCount(0);
		const transferred = await request.get('/api/claims', { headers: guestCookie });
		await expect(transferred).toBeOK();
		const recipientClaims = await transferred.json();
		expect(recipientClaims.claims).toContainEqual(
			expect.objectContaining({
				id: 'playwright-claim',
				members: [
					expect.objectContaining({ id: 3, isOwner: true }),
					expect.objectContaining({ id: 2, isOwner: false }),
				],
			}),
		);
		const previousOwnerClaims = await request.get('/api/claims', { headers: memberCookie });
		await expect(previousOwnerClaims).toBeOK();
		expect((await previousOwnerClaims.json()).memberClaims).toContainEqual(
			expect.objectContaining({ id: 'playwright-claim' }),
		);
		expect(
			(
				await request.post(`${url}/transfer`, {
					headers: memberCookie,
					data: { userId: 2 },
				})
			).status(),
		).toBe(404);
	} finally {
		const guestClaims = await request.get('/api/claims', { headers: guestCookie });
		if (
			(await guestClaims.json()).claims.some(
				(claim: { id: string }) => claim.id === 'playwright-claim',
			)
		) {
			await request.post(`${url}/transfer`, { headers: guestCookie, data: { userId: 2 } });
		}
		await request.delete(`${url}/members/3`, { headers: memberCookie });
	}
});

test('signup gate and committee permissions reject unauthorized access', async ({
	request,
}, testInfo) => {
	await test.step('Check the prelaunch signup gate', async () => {
		const prelaunch = await request.patch('/api/admin/launch', {
			headers: adminCookie,
			data: { target: '2100-01-02T12:00' },
		});
		await expect(prelaunch).toBeOK();

		try {
			const listed = await request.post('/api/auth/signup', {
				data: { email: `invited${testInfo.retry}@mmu.ac.uk` },
			});
			expect(listed.status()).toBe(503);
			expect(await listed.json()).toMatchObject({
				message: 'Verification email could not be sent',
			});

			const unlisted = await request.post('/api/auth/signup', {
				data: { email: 'unlisted@mmu.ac.uk' },
			});
			expect(unlisted.status()).toBe(403);
		} finally {
			const restored = await request.patch('/api/admin/launch', {
				headers: adminCookie,
				data: { target: '2026-09-29T20:00' },
			});
			await expect(restored).toBeOK();
		}
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
