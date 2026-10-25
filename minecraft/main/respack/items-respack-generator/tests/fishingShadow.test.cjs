const { test } = require('node:test');
const assert = require('node:assert/strict');
const { Buffer } = require('node:buffer');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const sharp = require('sharp');
const { generateResourcePack } = require('../dist/generation/resourcePackGenerator');

test('translucent fish pixels become an opaque black fishing shadow', async (t) => {
	const tempDir = await fs.realpath(os.tmpdir());
	const root = await fs.realpath(await fs.mkdtemp(path.join(tempDir, 'fishing-shadow-')));
	assert.equal(path.dirname(root).toLowerCase(), tempDir.toLowerCase());
	t.after(() => fs.rm(root, { recursive: true, force: true }));
	const sourceDirectory = path.join(root, 'source');
	await fs.mkdir(sourceDirectory);
	const texturePngPath = path.join(sourceDirectory, 'texture.png');
	await sharp(Buffer.from([255, 255, 255, 128, 50, 60, 70, 255, 0, 0, 0, 0]), {
		raw: { width: 3, height: 1, channels: 4 },
	})
		.png()
		.toFile(texturePngPath);
	const original = await fs.readFile(texturePngPath);
	const resourcePath = 'fish/nether/rare/ghast_wisp';
	const outputDir = path.join(root, 'pack');
	const summary = await generateResourcePack(
		[
			{
				type: 'basic',
				sourceDirectory,
				relativeDirectory: resourcePath,
				title: 'Ghast Wisp',
				id: 'fish-ghast_wisp',
				rarity: 'rare',
				maxStackSize: 64,
				tooltips: [],
				resourcePath,
				baseName: 'ghast_wisp',
				texturePngPath,
			},
		],
		{
			sourceDir: sourceDirectory,
			armorAssetsDir: path.dirname(require.resolve('../package.json')),
			outputDir,
			namespace: 'mmu_pack',
			packDescription: 'test',
		},
	);
	assert.equal(summary.skippedItems, 0);
	const itemDir = path.join(outputDir, 'assets/mmu_pack');
	assert.deepEqual(
		await fs.readFile(path.join(itemDir, 'textures/item', `${resourcePath}.png`)),
		original,
	);
	const shadowTexture = path.join(itemDir, 'textures/item/fishing_shadow', `${resourcePath}.png`);
	const { data } = await sharp(shadowTexture).raw().toBuffer({ resolveWithObject: true });
	assert.deepEqual([...data], [0, 0, 0, 255, 0, 0, 0, 255, 0, 0, 0, 0]);
	const definition = JSON.parse(
		await fs.readFile(path.join(outputDir, 'assets/minecraft/items/heart_of_the_sea.json')),
	);
	const wisp = definition.model.cases.find((entry) => entry.when === 'fish-ghast_wisp');
	assert.equal(wisp.model.cases[0].model.model, `mmu_pack:item/fishing_shadow/${resourcePath}`);
});
