import { expect, test } from '@playwright/test';

const memberCookie = { Cookie: 'mcstack_session=playwright-member' };
const adminCookie = { Cookie: 'mcstack_session=playwright-admin' };

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
