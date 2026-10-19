import { expect, test } from '@playwright/test';

test('player places use the final sorted list and tied positions', async ({
	page,
	request,
	baseURL,
}) => {
	const response = await request.get('/api/players?page=0', {
		headers: { Cookie: 'mcstack_session=playwright-member' },
	});
	await expect(response).toBeOK();
	const body = await response.json();
	const example = body.players.find((player: { isRanked: boolean }) => player.isRanked);
	expect(example).toBeTruthy();

	const statKey = 'minecraft.custom.minecraft:play_time';
	const deathsKey = 'minecraft.custom.minecraft:deaths';
	const totemsKey = 'minecraft.used.minecraft:totem_of_undying';
	const makePlayer = (id: number, minecraftUsername: string, value: number) => ({
		...example,
		id,
		minecraftUsername,
		streakDuration: Math.floor(value / 10),
		stats: {
			...example.stats,
			minecraft: {
				...example.stats.minecraft,
				stats: {
					...example.stats.minecraft.stats,
					[deathsKey]: {
						key: deathsKey,
						category: 'custom',
						id: 'minecraft:deaths',
						label: 'Deaths',
						value: Math.floor(value / 10),
						updatedAtUnixMs: 0,
					},
					[totemsKey]: {
						key: totemsKey,
						category: 'used',
						id: 'minecraft:totem_of_undying',
						label: 'Totems Used',
						value: 1,
						updatedAtUnixMs: 0,
					},
					[statKey]: {
						key: statKey,
						category: 'custom',
						id: 'minecraft:play_time',
						label: 'Play Time',
						value,
						updatedAtUnixMs: 0,
					},
				},
			},
		},
	});
	const directory = {
		...body,
		players: [
			makePlayer(101, 'Alpha', 100),
			makePlayer(102, 'Bravo', 90),
			makePlayer(106, 'miaalicexoxo', 0),
			makePlayer(103, 'Charlie', 80),
			makePlayer(104, 'Delta', 80),
			{ ...makePlayer(105, 'Admin', 999), isRanked: false },
		],
		page: 0,
		hasMore: false,
	};
	let finishLoading = () => undefined;
	const delayedDirectory = new Promise<void>((resolve) => {
		finishLoading = resolve;
	});
	let directoryRequests = 0;
	await page.route('**/api/players?*', async (route) => {
		directoryRequests++;
		await delayedDirectory;
		await route.fulfill({ json: directory });
	});
	await page.context().addCookies([
		{
			name: 'mcstack_session',
			value: 'playwright-member',
			url: baseURL ?? 'http://127.0.0.1:3100',
		},
	]);
	const directoryRequested = page.waitForRequest(
		(request) =>
			new URL(request.url()).pathname === '/api/players' &&
			new URL(request.url()).searchParams.get('all') === '1',
	);
	await page.goto('/play/players');
	await directoryRequested;
	await expect(page.locator('.playersLoading')).toBeVisible();
	await expect(page.locator('.playersTable')).toHaveCount(0);

	finishLoading();
	const rows = page.locator('.playersTable tbody tr');
	await expect(rows).toHaveCount(6);
	expect(directoryRequests).toBe(1);
	await expect(page.getByRole('combobox', { name: 'Column 4' })).toHaveValue(
		'minecraft.deathsAndTotemPops',
	);
	await expect(rows.locator('td:nth-child(6)')).toHaveText(['11', '10', '9', '9', '1', '100']);
	await page.getByRole('combobox', { name: 'Column 4' }).selectOption('dailies.streakDuration');
	await expect(rows.locator('td:nth-child(6)')).toHaveText([
		'10 days',
		'9 days',
		'8 days',
		'8 days',
		'0 days',
		'99 days',
	]);
	await expect(rows.locator('td:first-child')).toHaveText(['1', '2', '4', '4', '5', '-']);
	await page.getByRole('button', { name: 'Sort by Play Time' }).click();
	await expect(rows.locator('td:first-child')).toHaveText(['1', '3', '3', '4', '5', '-']);
	await page.getByRole('textbox', { name: 'Search players' }).fill('miaalicexoxo');
	await expect(rows).toHaveCount(1);
	await expect(rows).toContainText('miaalicexoxo');
	await page.getByRole('textbox', { name: 'Search players' }).fill('Delta');
	await expect(rows).toHaveCount(1);
	await expect(rows.locator('td:first-child')).toHaveText(['3']);
});

test('a player profile loads without fetching the rest of the directory', async ({
	page,
	request,
	baseURL,
}) => {
	const response = await request.get('/api/players?player=PlaywrightMember&selectedOnly=1', {
		headers: { Cookie: 'mcstack_session=playwright-member' },
	});
	await expect(response).toBeOK();
	const body = await response.json();
	expect(body.players).toEqual([]);
	expect(body.selectedPlayer).toMatchObject({ minecraftUsername: 'PlaywrightMember' });
	const requests: string[] = [];
	await page.route('**/api/players?*', async (route) => {
		const query = new URL(route.request().url()).searchParams;
		requests.push(query.toString());
		await route.fulfill({ json: body });
	});
	await page.context().addCookies([
		{
			name: 'mcstack_session',
			value: 'playwright-member',
			url: baseURL ?? 'http://127.0.0.1:3100',
		},
	]);
	await page.goto('/play/players/PlaywrightMember');
	await expect(page.locator('.playerProfilePanel')).toContainText('PlaywrightMember');
	expect(requests).toEqual(['player=PlaywrightMember&selectedOnly=1']);
});
