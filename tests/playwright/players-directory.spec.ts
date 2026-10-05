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
	const makePlayer = (id: number, minecraftUsername: string, value: number) => ({
		...example,
		id,
		minecraftUsername,
		stats: {
			...example.stats,
			minecraft: {
				...example.stats.minecraft,
				stats: {
					...example.stats.minecraft.stats,
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
	const firstPage = {
		...body,
		players: [makePlayer(101, 'Alpha', 100), makePlayer(102, 'Bravo', 90)],
		page: 0,
		hasMore: true,
	};
	const middlePage = {
		...body,
		players: [makePlayer(106, 'miaalicexoxo', 0)],
		page: 1,
		hasMore: true,
	};
	const finalPage = {
		...body,
		players: [
			makePlayer(103, 'Charlie', 80),
			makePlayer(104, 'Delta', 80),
			{ ...makePlayer(105, 'Admin', 999), isRanked: false },
		],
		page: 2,
		hasMore: false,
	};
	let finishLoading = () => undefined;
	const delayedPage = new Promise<void>((resolve) => {
		finishLoading = resolve;
	});
	await page.route('**/api/players?*', async (route) => {
		const requestedPage = new URL(route.request().url()).searchParams.get('page');
		if (requestedPage === '2') {
			await delayedPage;
			await route.fulfill({ json: finalPage });
		} else if (requestedPage === '1') {
			await route.fulfill({ json: middlePage });
		} else {
			await route.fulfill({ json: firstPage });
		}
	});
	await page.context().addCookies([
		{
			name: 'mcstack_session',
			value: 'playwright-member',
			url: baseURL ?? 'http://127.0.0.1:3100',
		},
	]);
	const nextPageRequested = page.waitForRequest(
		(request) =>
			new URL(request.url()).pathname === '/api/players' &&
			new URL(request.url()).searchParams.get('page') === '2',
	);
	await page.goto('/play/players');
	await nextPageRequested;
	await expect(page.locator('.playersLoading')).toBeVisible();
	await expect(page.locator('.playersTable')).toHaveCount(0);

	finishLoading();
	const rows = page.locator('.playersTable tbody tr');
	await expect(rows).toHaveCount(6);
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
