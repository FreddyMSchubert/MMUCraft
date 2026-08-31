import { expect, test } from '@playwright/test';

const longKnowledgePage = `
## Kettle

${'Kettles are decorative copper blocks with a detailed crafting recipe. '.repeat(30)}

## Fruit Bowl

${'Fruit bowls add another useful decorative option to a kitchen build. '.repeat(30)}

## Cookie Jar

${'Cookie jars can be placed and rotated like the other decorative blocks. '.repeat(30)}

## Firefly Jar

${'Firefly jars are a softly glowing decoration for paths and gardens. '.repeat(30)}

## Spoons Carpets

${'Spoons carpets come in several variants described in the sections below. '.repeat(20)}

### Beer

${'This is the first nested carpet variant in the knowledge page. '.repeat(20)}

### Grandiloquent Spoons Carpet

${'This is another nested carpet variant in the knowledge page. '.repeat(20)}
`;

test.beforeEach(async ({ page }) => {
	await page.route('**/api/auth/me', async (route) => {
		await route.fulfill({
			json: {
				user: {
					id: 2,
					minecraftUsername: 'PlaywrightMember',
					skinUrl: null,
					color: '#ffffff',
					isMember: true,
					isCommittee: false,
					isSuperAdmin: false,
				},
			},
		});
	});
	await page.route('**/api/players/online', async (route) => {
		await route.fulfill({ json: { players: [] } });
	});
	await page.route('**/api/countdowns', async (route) => {
		await route.fulfill({ json: { countdowns: [] } });
	});
	await page.route('**/api/knowledge', async (route) => {
		await route.fulfill({
			json: {
				contentVersion: 1,
				lastUnlockedKnowledgeId: 'decorative-blocks',
				unlockedKnowledgeIds: ['decorative-blocks'],
				readKnowledgeIds: ['decorative-blocks'],
				tree: [
					{
						type: 'folder',
						name: 'Items',
						children: [
							{
								type: 'page',
								id: 'decorative-blocks',
								path: '01-items/06-decorative-blocks.md',
								sidebarTitle: 'Decorative Blocks',
								unlockOrder: 4,
								chatMessage: 'Fixture knowledge page',
								unlockedByDefault: false,
								unlocked: true,
							},
						],
					},
				],
			},
		});
	});
	await page.route('**/knowledge/01-items/06-decorative-blocks.md?*', async (route) => {
		await route.fulfill({ contentType: 'text/markdown', body: longKnowledgePage });
	});
});

test('long knowledge pages get nested, scroll-aware section links on desktop', async ({ page }) => {
	await page.goto('/play/knowledge/decorative-blocks');
	await expect(
		page.getByRole('heading', { name: 'Decorative Blocks', exact: true }),
	).toBeVisible();

	const outline = page.getByRole('complementary', { name: 'On this page' });
	await expect(outline).toBeVisible();
	const kettleLink = outline.getByRole('link', { name: 'Kettle' });
	const beerLink = outline.getByRole('link', { name: 'Beer' });
	await expect(kettleLink).toHaveAttribute('href', '#knowledge-kettle');
	await expect(beerLink).toHaveAttribute('href', '#knowledge-beer');

	const kettleIndent = await kettleLink.evaluate((link) => getComputedStyle(link).paddingLeft);
	const beerIndent = await beerLink.evaluate((link) => getComputedStyle(link).paddingLeft);
	expect(Number.parseFloat(beerIndent)).toBeGreaterThan(Number.parseFloat(kettleIndent));

	await beerLink.click();
	await expect(page).toHaveURL(/\/play\/knowledge\/decorative-blocks#knowledge-beer$/);
	await expect(beerLink).toHaveClass(/\bactive\b/);
	await expect(page.locator('h3#knowledge-beer')).toBeInViewport();
	await expect(outline).toHaveCSS('position', 'sticky');
});

test('knowledge outlines stay out of the mobile layout', async ({ page }) => {
	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto('/play/knowledge/decorative-blocks');
	await expect(
		page.getByRole('heading', { name: 'Decorative Blocks', exact: true }),
	).toBeVisible();
	await expect(page.locator('.knowledgeOutline')).toBeHidden();
	await expect(page.locator('#knowledge-page-select')).toBeVisible();
});

test('short knowledge pages do not show a redundant outline', async ({ page }) => {
	await page.route('**/knowledge/01-items/06-decorative-blocks.md?*', async (route) => {
		await route.fulfill({
			contentType: 'text/markdown',
			body: '## One short section\n\nThere is not enough content to need an outline.',
		});
	});

	await page.goto('/play/knowledge/decorative-blocks');
	await expect(page.getByRole('heading', { name: 'One short section' })).toBeVisible();
	await expect(page.locator('.knowledgeOutline')).toBeHidden();
});
