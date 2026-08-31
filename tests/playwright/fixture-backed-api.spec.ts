import { expect, test, type APIRequestContext } from '@playwright/test';

const memberCookie = { Cookie: 'mcstack_session=playwright-member' };
const adminCookie = { Cookie: 'mcstack_session=playwright-admin' };

async function getJson(request: APIRequestContext, path: string, headers = memberCookie) {
	const response = await request.get(path, { headers });
	await expect(response, `${path} must return a successful response`).toBeOK();
	return response.json();
}

test('member endpoints serialize the seeded database', async ({ request }) => {
	await test.step('Resolve the member session', async () => {
		const body = await getJson(request, '/api/auth/me');
		expect(body.user).toMatchObject({
			id: 2,
			minecraftUsername: 'PlaywrightMember',
			isMember: true,
			isCommittee: false,
		});
	});

	await test.step('List players and joined profile data', async () => {
		const body = await getJson(request, '/api/players?page=0&player=PlaywrightMember');
		expect(body.currentUserId).toBe(2);
		expect(body.selectedPlayer).toMatchObject({
			id: 2,
			minecraftUsername: 'PlaywrightMember',
		});
	});

	await test.step('Read one player by identifier', async () => {
		const body = await getJson(request, '/api/players/2');
		expect(body.player).toMatchObject({ id: 2, minecraftUsername: 'PlaywrightMember' });
	});

	await test.step('List the member claim and all candidate players', async () => {
		const body = await getJson(request, '/api/claims');
		expect(body.claims).toEqual(
			expect.arrayContaining([
				expect.objectContaining({ id: 'playwright-claim', name: 'Fixture claim' }),
			]),
		);
		expect(body.candidates).toEqual(
			expect.arrayContaining([
				expect.objectContaining({ id: 1 }),
				expect.objectContaining({ id: 3, minecraftUsername: 'PlaywrightGuest' }),
			]),
		);
	});

	await test.step('Add and remove a non-member player from the claim', async () => {
		const addResponse = await request.post('/api/claims/playwright-claim/members', {
			headers: memberCookie,
			data: { userId: 3 },
		});
		await expect(addResponse).toBeOK();

		const body = await getJson(request, '/api/claims');
		expect(body.claims[0].members).toEqual(
			expect.arrayContaining([
				expect.objectContaining({ id: 3, minecraftUsername: 'PlaywrightGuest' }),
			]),
		);

		const removeResponse = await request.delete('/api/claims/playwright-claim/members/3', {
			headers: memberCookie,
		});
		await expect(removeResponse).toBeOK();
	});

	await test.step('Load the knowledge tree', async () => {
		const body = await getJson(request, '/api/knowledge');
		expect(body.tree).toBeInstanceOf(Array);
		expect(body.contentVersion).toEqual(expect.any(Number));
	});

	await test.step('Load the fixture-aware shop catalog', async () => {
		const body = await getJson(request, '/api/shop');
		expect(body.items).toBeInstanceOf(Array);
		expect(body.dealDate).toEqual(expect.any(String));
	});

	await test.step('Load the fishing compendium', async () => {
		const body = await getJson(request, '/api/fishing/compendium');
		expect(body.fish).toBeInstanceOf(Array);
		expect(body.fish.length).toBeGreaterThan(3);
	});
});

test('committee endpoints expose seeded administration data', async ({ request }) => {
	await test.step('Resolve committee privileges', async () => {
		const body = await getJson(request, '/api/auth/me', adminCookie);
		expect(body.user).toMatchObject({ id: 1, isCommittee: true, isSuperAdmin: true });
	});

	await test.step('List administrative players', async () => {
		const body = await getJson(request, '/api/admin/players', adminCookie);
		expect(body.players).toHaveLength(3);
	});

	await test.step('List administrative claims', async () => {
		const body = await getJson(request, '/api/admin/claims?offset=0&limit=10', adminCookie);
		expect(body.claims).toEqual(
			expect.arrayContaining([expect.objectContaining({ id: 'playwright-claim' })]),
		);
	});

	await test.step('List administrative countdowns', async () => {
		const body = await getJson(request, '/api/admin/countdowns', adminCookie);
		expect(body.countdowns[0]).toMatchObject({ id: 1, heading: 'Fixture countdown' });
	});

	await test.step('List gift codes and access controls', async () => {
		const giftCodes = await getJson(request, '/api/admin/gift-codes', adminCookie);
		const allowlist = await getJson(request, '/api/admin/email-whitelist', adminCookie);
		const bans = await getJson(request, '/api/admin/player-bans', adminCookie);
		expect(giftCodes.giftCodes).toEqual([]);
		expect(allowlist.entries).toEqual([]);
		expect(bans.bans).toEqual([]);
	});

	await test.step('List command history', async () => {
		const body = await getJson(request, '/api/admin/command-logs', adminCookie);
		expect(body.commands).toEqual([]);
		expect(body.hasMore).toBe(false);
	});
});
